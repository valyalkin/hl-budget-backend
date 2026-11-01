-- Income: income categories, income entries and income import receipts
-- (specs/009-income-tracking/data-model.md, research R-001, R-002).
--
-- Income has its own tables, shaped like categories, spendings and spending_imports. An income
-- references its income category and its account through composite keys that include user_id, so
-- it can only point at its own user's rows, and never at a spending category (FR-013). Accounts
-- are shared with spendings: an account used by income cannot be deleted either (FR-052).

-- When Salary and Investments were created for this user (research R-002). NULL means not yet:
-- every user from before this feature, and new users until their first budgeting call.
ALTER TABLE users ADD COLUMN income_categories_seeded_at timestamptz NULL;

CREATE TABLE income_categories (
  id         uuid        NOT NULL DEFAULT gen_random_uuid(),
  user_id    uuid        NOT NULL,
  name       varchar(50) NOT NULL,
  is_default boolean     NOT NULL DEFAULT false,
  -- No default: the app always assigns one (fixed for the defaults, CategoryPalette.next otherwise).
  color      varchar(7)  NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  version    bigint      NOT NULL DEFAULT 0,
  CONSTRAINT pk_income_categories PRIMARY KEY (id),
  CONSTRAINT uq_income_categories_id_user UNIQUE (id, user_id),
  CONSTRAINT fk_income_categories_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT ck_income_categories_name CHECK (length(name) BETWEEN 1 AND 50 AND name = btrim(name)),
  CONSTRAINT ck_income_categories_color CHECK (color ~ '^#[0-9a-f]{6}$')
);

-- Unique per user ignoring case, defaults included, and independent of categories (FR-022).
CREATE UNIQUE INDEX ux_income_categories_user_lower_name ON income_categories (user_id, lower(name));

CREATE TABLE incomes (
  id                      uuid          NOT NULL DEFAULT gen_random_uuid(),
  user_id                 uuid          NOT NULL,
  income_date             date          NOT NULL,
  source                  varchar(255)  NOT NULL,
  amount                  numeric(10,2) NOT NULL,
  category_id             uuid          NOT NULL,
  account_id              uuid          NOT NULL,
  idempotency_key         varchar(255)  NULL,
  idempotency_fingerprint char(64)      NULL,
  created_at              timestamptz   NOT NULL DEFAULT now(),
  updated_at              timestamptz   NOT NULL DEFAULT now(),
  version                 bigint        NOT NULL DEFAULT 0,
  CONSTRAINT pk_incomes PRIMARY KEY (id),
  CONSTRAINT fk_incomes_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT fk_incomes_category FOREIGN KEY (category_id, user_id)
    REFERENCES income_categories (id, user_id) ON DELETE NO ACTION,
  CONSTRAINT fk_incomes_account FOREIGN KEY (account_id, user_id)
    REFERENCES accounts (id, user_id) ON DELETE NO ACTION,
  CONSTRAINT ck_incomes_amount CHECK (amount > 0),
  CONSTRAINT ck_incomes_source CHECK (length(source) BETWEEN 1 AND 255 AND source = btrim(source)),
  CONSTRAINT ck_incomes_idempotency_pair CHECK ((idempotency_key IS NULL) = (idempotency_fingerprint IS NULL))
);

CREATE UNIQUE INDEX ux_incomes_user_idempotency_key ON incomes (user_id, idempotency_key)
  WHERE idempotency_key IS NOT NULL;
-- The list order, the month totals and the sources (mirrors ix_spendings_user_date).
CREATE INDEX ix_incomes_user_date ON incomes (user_id, income_date DESC, created_at DESC, id);
-- Filters, counts and the in-use checks on delete.
CREATE INDEX ix_incomes_user_category ON incomes (user_id, category_id);
CREATE INDEX ix_incomes_user_account ON incomes (user_id, account_id);

-- The receipt of each income CSV import. Stores nothing from the imported file.
CREATE TABLE income_imports (
  id                      uuid          NOT NULL DEFAULT gen_random_uuid(),
  user_id                 uuid          NOT NULL,
  idempotency_key         varchar(255)  NULL,
  idempotency_fingerprint char(64)      NULL,
  income_count            integer       NOT NULL,
  first_date              date          NOT NULL,
  last_date               date          NOT NULL,
  total                   numeric(14,2) NOT NULL,
  created_at              timestamptz   NOT NULL DEFAULT now(),
  CONSTRAINT pk_income_imports PRIMARY KEY (id),
  CONSTRAINT fk_income_imports_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT ck_income_imports_count CHECK (income_count BETWEEN 1 AND 1000),
  CONSTRAINT ck_income_imports_dates CHECK (first_date <= last_date),
  CONSTRAINT ck_income_imports_total CHECK (total > 0),
  CONSTRAINT ck_income_imports_idempotency_pair
    CHECK ((idempotency_key IS NULL) = (idempotency_fingerprint IS NULL))
);

CREATE UNIQUE INDEX ux_income_imports_user_key ON income_imports (user_id, idempotency_key)
  WHERE idempotency_key IS NOT NULL;

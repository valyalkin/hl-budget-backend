-- Accounts, categories and spendings (specs/002-spending-tracking/data-model.md).
--
-- Every row belongs to one user. Spendings reference their account and category through
-- composite keys that include user_id, so a spending can only point at its own user's rows
-- (FR-031). NO ACTION refuses deleting a referenced account or category like RESTRICT would, but
-- is checked at statement end, so a future user delete can still cascade in one statement.

CREATE TABLE accounts (
  id         uuid        NOT NULL DEFAULT gen_random_uuid(),
  user_id    uuid        NOT NULL,
  name       varchar(50) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  version    bigint      NOT NULL DEFAULT 0,
  CONSTRAINT pk_accounts PRIMARY KEY (id),
  CONSTRAINT uq_accounts_id_user UNIQUE (id, user_id),
  CONSTRAINT fk_accounts_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT ck_accounts_name CHECK (length(name) BETWEEN 1 AND 50 AND name = btrim(name))
);

-- Names are unique per user ignoring case (FR-022).
CREATE UNIQUE INDEX ux_accounts_user_lower_name ON accounts (user_id, lower(name));

CREATE TABLE categories (
  id         uuid        NOT NULL DEFAULT gen_random_uuid(),
  user_id    uuid        NOT NULL,
  name       varchar(50) NOT NULL,
  is_default boolean     NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  version    bigint      NOT NULL DEFAULT 0,
  CONSTRAINT pk_categories PRIMARY KEY (id),
  CONSTRAINT uq_categories_id_user UNIQUE (id, user_id),
  CONSTRAINT fk_categories_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT ck_categories_name CHECK (length(name) BETWEEN 1 AND 50 AND name = btrim(name))
);

-- Names are unique per user ignoring case, defaults included (FR-013).
CREATE UNIQUE INDEX ux_categories_user_lower_name ON categories (user_id, lower(name));

CREATE TABLE spendings (
  id                      uuid          NOT NULL DEFAULT gen_random_uuid(),
  user_id                 uuid          NOT NULL,
  spending_date           date          NOT NULL,
  merchant                varchar(255)  NOT NULL,
  amount                  numeric(10,2) NOT NULL,
  category_id             uuid          NOT NULL,
  account_id              uuid          NOT NULL,
  idempotency_key         varchar(255)  NULL,
  idempotency_fingerprint char(64)      NULL,
  created_at              timestamptz   NOT NULL DEFAULT now(),
  updated_at              timestamptz   NOT NULL DEFAULT now(),
  version                 bigint        NOT NULL DEFAULT 0,
  CONSTRAINT pk_spendings PRIMARY KEY (id),
  CONSTRAINT fk_spendings_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT fk_spendings_category FOREIGN KEY (category_id, user_id)
    REFERENCES categories (id, user_id) ON DELETE NO ACTION,
  CONSTRAINT fk_spendings_account FOREIGN KEY (account_id, user_id)
    REFERENCES accounts (id, user_id) ON DELETE NO ACTION,
  -- Amounts are exact, positive and at most 99,999,999.99 (FR-032; the scale enforces the cap).
  CONSTRAINT ck_spendings_amount CHECK (amount > 0),
  CONSTRAINT ck_spendings_merchant CHECK (length(merchant) BETWEEN 1 AND 255 AND merchant = btrim(merchant)),
  -- A key always comes with the fingerprint of the request that used it (research R-007).
  CONSTRAINT ck_spendings_idempotency_pair CHECK ((idempotency_key IS NULL) = (idempotency_fingerprint IS NULL))
);

CREATE UNIQUE INDEX ux_spendings_user_idempotency_key ON spendings (user_id, idempotency_key)
  WHERE idempotency_key IS NOT NULL;
-- The default list order: newest date first, then newest created, then id (research R-010).
CREATE INDEX ix_spendings_user_date ON spendings (user_id, spending_date DESC, created_at DESC, id);
-- Filters, spending counts and the in-use check on delete (research R-005, R-011).
CREATE INDEX ix_spendings_user_category ON spendings (user_id, category_id);
CREATE INDEX ix_spendings_user_account ON spendings (user_id, account_id);

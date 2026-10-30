-- The receipt of each CSV import (specs/004-csv-import/data-model.md, research R-006).
--
-- One row per successful import, so a retried import is recognised by its Idempotency-Key and
-- 201 Created can point at a readable resource. It stores nothing from the imported file: no
-- file name, no row contents, and no link to the spendings, which stay ordinary spendings.

CREATE TABLE spending_imports (
  id                      uuid          NOT NULL DEFAULT gen_random_uuid(),
  user_id                 uuid          NOT NULL,
  idempotency_key         varchar(255)  NULL,
  idempotency_fingerprint char(64)      NULL,
  spending_count          integer       NOT NULL,
  first_date              date          NOT NULL,
  last_date               date          NOT NULL,
  -- 14 digits hold 1,000 spendings at the per-spending maximum of 99,999,999.99.
  total                   numeric(14,2) NOT NULL,
  created_at              timestamptz   NOT NULL DEFAULT now(),
  CONSTRAINT pk_spending_imports PRIMARY KEY (id),
  CONSTRAINT fk_spending_imports_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
  CONSTRAINT ck_spending_imports_count CHECK (spending_count BETWEEN 1 AND 1000),
  CONSTRAINT ck_spending_imports_dates CHECK (first_date <= last_date),
  CONSTRAINT ck_spending_imports_total CHECK (total > 0),
  -- A key always comes with the fingerprint of the request that used it.
  CONSTRAINT ck_spending_imports_idempotency_pair
    CHECK ((idempotency_key IS NULL) = (idempotency_fingerprint IS NULL))
);

CREATE UNIQUE INDEX ux_spending_imports_user_key ON spending_imports (user_id, idempotency_key)
  WHERE idempotency_key IS NOT NULL;

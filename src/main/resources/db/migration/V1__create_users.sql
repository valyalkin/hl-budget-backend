-- A person who has signed in at least once, keyed by the verified Auth0 subject (data-model.md).
CREATE TABLE users (
  id            uuid          NOT NULL DEFAULT gen_random_uuid(),
  auth0_subject varchar(255)  NOT NULL,
  display_name  varchar(255)  NULL,
  email         varchar(320)  NULL,
  avatar_url    varchar(2048) NULL,
  first_seen_at timestamptz   NOT NULL DEFAULT now(),
  last_seen_at  timestamptz   NOT NULL DEFAULT now(),
  version       bigint        NOT NULL DEFAULT 0,
  CONSTRAINT pk_users PRIMARY KEY (id),
  -- One row per subject (FR-014); also the conflict target of the profile upsert.
  CONSTRAINT uq_users_auth0_subject UNIQUE (auth0_subject),
  CONSTRAINT ck_users_auth0_subject_not_blank CHECK (length(trim(auth0_subject)) > 0)
);

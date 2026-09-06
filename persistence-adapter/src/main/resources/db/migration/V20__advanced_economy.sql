-- Advanced economy features layered onto the native ledger (V2): shared joint
-- banks, player loans with credit scores, and anti-dupe physical banknotes.
--
-- Same portability contract as V1/V2: plain CREATE TABLE in the subset SQLite
-- (the default), MySQL/MariaDB and PostgreSQL all accept, no engine-specific
-- syntax. UUIDs are the canonical 36-character text; timestamps are epoch
-- milliseconds in a BIGINT; every money column is DECIMAL(20,4), the same width
-- wallet_balances.amount (V2) uses, never a float. Tables carry the established
-- economy_ context prefix (cf. economy_owners/economy_pay_preferences/
-- economy_worth_overrides) and the player-owned rows foreign-key back to
-- economy_owners exactly as wallet_balances does, so an advanced-economy write
-- for an offline target cannot violate the owner identity. jOOQ's DDLDatabase
-- parses this file alongside V1-V19 at build time, so the generated tables match
-- the runtime schema.

-- A shared joint bank: a named pooled balance in one currency with a creator and
-- a member roster. The balance is DB-backed (the economy hard invariant) so it
-- survives a world rollback like a wallet balance does.
CREATE TABLE economy_shared_banks (
    id           VARCHAR(36)    NOT NULL,
    name         VARCHAR(64)    NOT NULL,
    balance      DECIMAL(20, 4) NOT NULL,
    currency     VARCHAR(32)    NOT NULL,
    creator_uuid VARCHAR(36)    NOT NULL,
    created_at   BIGINT         NOT NULL,
    CONSTRAINT pk_economy_shared_banks PRIMARY KEY (id)
);

-- The bank roster: one row per (bank, player) with the player's role. Cascades
-- conceptually with its bank. The repository clears and rewrites the roster on
-- save and deletes it on bank delete.
CREATE TABLE economy_bank_members (
    bank_id     VARCHAR(36)    NOT NULL,
    player_uuid VARCHAR(36)    NOT NULL,
    role        VARCHAR(16)    NOT NULL,
    CONSTRAINT pk_economy_bank_members PRIMARY KEY (bank_id, player_uuid),
    CONSTRAINT fk_economy_bank_members_bank FOREIGN KEY (bank_id)
        REFERENCES economy_shared_banks (id)
);

-- An outstanding player loan: principal and remaining (the amount column) are
-- money, the interest rate is a fixed-point fraction, and the installment schedule
-- is tracked by a remaining count and the next-payment instant. The debtor
-- foreign-keys to economy_owners so a loan cannot reference a never-materialised
-- owner. The principal is stored alongside the shrinking remaining balance so a
-- dashboard shows the original loan size after a restart, not the residual.
-- interest_rate is DECIMAL(12,8): LoanPolicy.interestFor derives the rate as a
-- fraction with up to eight fractional digits (a 6-dp score fraction times the
-- configured span), so a narrower scale would truncate it and the rate shown after
-- a reload would disagree with the rate the loan actually charges.
CREATE TABLE economy_loans (
    id                     VARCHAR(36)    NOT NULL,
    player_uuid            VARCHAR(36)    NOT NULL,
    principal              DECIMAL(20, 4) NOT NULL,
    amount                 DECIMAL(20, 4) NOT NULL,
    currency               VARCHAR(32)    NOT NULL,
    interest_rate          DECIMAL(12, 8) NOT NULL,
    remaining_installments INT            NOT NULL,
    installment_amount     DECIMAL(20, 4) NOT NULL,
    taken_at               BIGINT         NOT NULL,
    next_payment_at        BIGINT         NOT NULL,
    CONSTRAINT pk_economy_loans PRIMARY KEY (id),
    CONSTRAINT fk_economy_loans_player FOREIGN KEY (player_uuid)
        REFERENCES economy_owners (owner)
);

-- A player's credit score (0-1000), driving loan eligibility. One row per player,
-- foreign-keyed to economy_owners.
CREATE TABLE economy_credit_scores (
    player_uuid  VARCHAR(36) NOT NULL,
    score        INT         NOT NULL DEFAULT 100,
    last_updated BIGINT      NOT NULL,
    CONSTRAINT pk_economy_credit_scores PRIMARY KEY (player_uuid),
    CONSTRAINT fk_economy_credit_scores_player FOREIGN KEY (player_uuid)
        REFERENCES economy_owners (owner)
);

-- The anti-dupe registry of live physical banknotes (checks): a banknote is only
-- redeemable while its token row exists, and redeem deletes the row atomically so
-- a duplicated item cannot be cashed twice.
CREATE TABLE economy_active_banknotes (
    token      VARCHAR(36)    NOT NULL,
    amount     DECIMAL(20, 4) NOT NULL,
    currency   VARCHAR(32)    NOT NULL,
    created_at BIGINT         NOT NULL,
    CONSTRAINT pk_economy_active_banknotes PRIMARY KEY (token)
);

CREATE INDEX idx_economy_loans_player ON economy_loans (player_uuid);
CREATE INDEX idx_economy_bank_members_player ON economy_bank_members (player_uuid);

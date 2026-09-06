-- Schema for the economy bounded context, the multi-currency native ledger.
--
-- Same portability contract as V1: the DDL stays in the subset SQLite (the
-- default), MySQL/MariaDB and PostgreSQL all accept. UUIDs are the canonical
-- 36-character text; timestamps are epoch milliseconds in a BIGINT; every fact a
-- query needs (owner, currency, amount, type, reason, the two transfer sides) is
-- a first-class column. Money is DECIMAL(20,4), never a float, never an opaque
-- JSON map of {currency: amount}, so /baltop can rank and the guarded debit can
-- compare at the database. jOOQ's DDLDatabase parses this file alongside V1 at
-- build time, so the generated classes always match the runtime schema.
--
-- Balances are DB-backed and survive world rollbacks (the hard economy
-- invariant): they never live in PDC and never in an in-memory authority.

-- Owner identity, materialised by ensureUserExists() before any offline-target
-- write so a /pay or admin give to a never-joined player cannot violate a foreign
-- key. One row per owner; the per-currency balance rows reference it.
CREATE TABLE economy_owners (
    owner       VARCHAR(36)  NOT NULL,
    name        VARCHAR(16)  NOT NULL,
    created_at  BIGINT       NOT NULL,
    CONSTRAINT pk_economy_owners PRIMARY KEY (owner)
);

-- One balance per (owner, currency): the Wallet aggregate is a map from Currency
-- to Money, so the table is keyed the same way. A wallet only has rows for the
-- currencies it has actually held, the rows stay sparse. The guarded debit
-- (UPDATE ... WHERE owner = ? AND currency = ? AND amount >= ?) compares against
-- the amount column, so it must be a real DECIMAL, never text or float.
CREATE TABLE wallet_balances (
    owner     VARCHAR(36)    NOT NULL,
    currency  VARCHAR(32)    NOT NULL,
    amount    DECIMAL(20, 4) NOT NULL,
    CONSTRAINT pk_wallet_balances PRIMARY KEY (owner, currency),
    CONSTRAINT fk_wallet_balances_owner FOREIGN KEY (owner)
        REFERENCES economy_owners (owner)
);

-- Ranks /baltop descending by balance within one currency, bounded by LIMIT. The
-- leading currency column lets the index serve a single-currency leaderboard; the
-- descending amount column lets the engine read the ranking straight off the
-- index without a sort.
CREATE INDEX idx_wallet_balances_baltop ON wallet_balances (currency, amount DESC);

-- Append-only transaction history (the ledger). Every applied or refused money
-- move is one row: a transfer carries both sides, a single-sided credit/debit
-- leaves the absent side null. type and reason are stored as their stable enum
-- names (EconomyReason) so an audit review can GROUP BY them. This table is only
-- ever inserted into and read back, never updated, so the batch-INSERT flush
-- path (append-only telemetry, not the debounced settle) owns it.
CREATE TABLE transactions (
    id          BIGINT         NOT NULL,
    ts          BIGINT         NOT NULL,
    from_owner  VARCHAR(36),
    to_owner    VARCHAR(36),
    currency    VARCHAR(32)    NOT NULL,
    amount      DECIMAL(20, 4) NOT NULL,
    type        VARCHAR(32)    NOT NULL,
    reason      VARCHAR(32)    NOT NULL,
    CONSTRAINT pk_transactions PRIMARY KEY (id)
);

CREATE INDEX idx_transactions_from ON transactions (from_owner, ts);
CREATE INDEX idx_transactions_to ON transactions (to_owner, ts);
CREATE INDEX idx_transactions_currency ON transactions (currency, ts);

-- Cross-server trade escrow: the durable, crash-safe hold of each side's staked goods while a trade between two
-- players on different backends completes its two-phase commit. Same-server trades are transient in-memory sessions
-- and never touch this table; a row exists only for a live cross-server trade and is deleted once the trade settles
-- or is refunded. Both participants own one row each, keyed by (trade_id, owner_uuid), so either backend and a
-- rejoining player reconcile the trade from these rows: the bus signal is only a poke to re-read them.
--
-- item_data is the base64 of the owner's serialised item stacks (TEXT, the same idiom V45/V68 use for a base64
-- payload the application treats as opaque); money is the base64-free "currency=amount" encoding of the staked
-- amounts per currency; both are empty when nothing of that kind was staked. Money is DECIMAL-scale text, amounts a
-- DECIMAL-derived string, and created_at an epoch-millis BIGINT, matching V1-V74. phase drives the commit:
-- HELD (refundable) -> COMMITTED (the atomic point of no return, both rows flip together) -> CLAIMED (delivered).
CREATE TABLE trade_escrow (
    trade_id            VARCHAR(36)  NOT NULL,
    owner_uuid          VARCHAR(36)  NOT NULL,
    owner_name          VARCHAR(64)  NOT NULL,
    owner_server        VARCHAR(64)  NOT NULL,
    counterparty_uuid   VARCHAR(36)  NOT NULL,
    counterparty_name   VARCHAR(64)  NOT NULL,
    counterparty_server VARCHAR(64)  NOT NULL,
    item_data           TEXT,
    item_count          INTEGER      NOT NULL DEFAULT 0,
    money               TEXT,
    phase               VARCHAR(16)  NOT NULL,
    created_at          BIGINT       NOT NULL,
    CONSTRAINT pk_trade_escrow PRIMARY KEY (trade_id, owner_uuid)
);

CREATE INDEX idx_trade_escrow_owner ON trade_escrow (owner_uuid);

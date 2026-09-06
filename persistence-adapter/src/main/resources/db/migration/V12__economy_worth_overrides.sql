-- Adds the writable per-item worth-override store behind /setworth. Item worth was
-- config-only and read-only: EconomyConfig.worthTable() parsed the `worth.items`
-- list once and /worth, /sell and /sellall read it through an immutable copy, so an
-- operator could not price an item without editing economy.conf by hand. A row here
-- is a price set in-game, keyed by the canonical lowercase material id, merged with
-- the config table behind the combined worth source (store first, config fallback)
-- so a freshly set price is what /worth reports and /sell credits against.
--
-- price is DECIMAL(20,4). Identical to wallet_balances.amount and
-- transactions.amount (V2): worth is money, never a float, so /sell can compute a
-- stack value the same width a balance is stored at. defined_by is the actor uuid
-- (NULL for a console actor); updated_at is epoch milliseconds in a BIGINT,
-- mirroring every other instant in the schema. The override is relational, so a set
-- price survives a world rollback like a balance does (the economy hard invariant),
-- never PDC.
--
-- Same portability contract as V1-V11: a plain CREATE TABLE in the subset SQLite
-- (the default, single-node servers), MySQL/MariaDB and PostgreSQL all accept, with
-- no dialect-specific clause. jOOQ's DDLDatabase parses this file alongside V1-V11 at
-- build time, so the generated EconomyWorthOverrides table and record appear.

CREATE TABLE economy_worth_overrides (
    material    VARCHAR(64)    NOT NULL,
    price       DECIMAL(20, 4) NOT NULL,
    defined_by  VARCHAR(36),
    updated_at  BIGINT         NOT NULL,
    CONSTRAINT pk_economy_worth_overrides PRIMARY KEY (material)
);

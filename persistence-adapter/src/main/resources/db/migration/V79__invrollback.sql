-- Schema for the invrollback bounded context. Inventory snapshots captured at key
-- moments (death, logout) so staff can restore a lost inventory from a GUI. Like the
-- economy and vaults ledgers, a snapshot is DB-backed and survives a world rollback
-- (never PDC): the authority is the row in this table, not the live ItemStack[] on the
-- player.
--
-- Same portability contract as V1-V78: the DDL stays in the subset SQLite (the default,
-- single-node servers), MySQL/MariaDB and PostgreSQL all accept. The primary key is a
-- client-minted UUID in canonical 36-character text, NOT a database autoincrement/identity
-- column: AUTOINCREMENT (SQLite), AUTO_INCREMENT (MySQL) and SERIAL / GENERATED AS IDENTITY
-- (PostgreSQL) are each spelled differently and none is in the portable subset, so, exactly
-- as every other table in this schema does. The application mints the id
-- (UUID.randomUUID) and the column is a plain VARCHAR(36). A UUID is unique before the row
-- is written, so a capture needs no read-back. jOOQ's DDLDatabase parses this file at build
-- time, so the generated InvSnapshots record always matches the runtime schema.
--
-- Queryable-vs-opaque split (architecture persistence invariant, 01-architecture §"No opaque
-- JSON-blob columns"): the facts a query, the retention sweep or the restore GUI needs, the
-- id, the owner, the capture cause and the capture instant: are first-class, indexable
-- columns. ONLY the serialized ItemStack[] inventory is intrinsically opaque payload, so it
-- is the one column allowed to serialize. It is stored as the base64 text of the adapter's
-- serialized item bytes (the same idiom V6/V29/V45/V75 use), which is byte-identical across
-- SQLite, MySQL/MariaDB and PostgreSQL and keeps the schema free of dialect-specific binary
-- types (PostgreSQL has no BLOB, it uses bytea, and the SQLite/MySQL BLOB shapes differ).
-- The payload is opaque to SQL by design (never filtered, ordered or partially updated) so
-- text encoding costs nothing a query could otherwise have used. It is nullable so a snapshot
-- of an empty inventory needs no blob.

-- One row per snapshot. `owner` is the player's canonical 36-char UUID text; `cause` is the
-- SnapshotCause constant name (DEATH / LOGOUT / RESTORE); `created_at` is the capture instant
-- as epoch milliseconds in a BIGINT so there is no dialect-specific datetime handling;
-- `contents` is the base64-encoded serialized inventory (main + armor + offhand, and the ender
-- chest when configured), the opaque payload, nullable for an empty capture.
CREATE TABLE inv_snapshots (
    id          VARCHAR(36)  NOT NULL,
    owner       VARCHAR(36)  NOT NULL,
    cause       VARCHAR(16)  NOT NULL,
    created_at  BIGINT       NOT NULL,
    contents    TEXT,
    CONSTRAINT pk_inv_snapshots PRIMARY KEY (id)
);

-- Serves the per-owner snapshot listing (the restore GUI reads a player's snapshots newest
-- first, ORDER BY created_at DESC) and both retention sweeps: deleteBeyondCount orders an
-- owner's rows by recency to keep the newest N, and deleteOlderThan filters on created_at. The
-- leading owner column scopes the scan to one player and the trailing created_at backs the
-- recency ordering.
CREATE INDEX idx_inv_snapshots_owner ON inv_snapshots (owner, created_at);

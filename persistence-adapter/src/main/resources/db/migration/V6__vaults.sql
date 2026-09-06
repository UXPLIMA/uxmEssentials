-- Schema for the vaults bounded context, DB-backed player item storage. A vault
-- is a numbered, per-owner chest the player opens with /vault <n>; its contents
-- are persisted to the database, never to PDC, so they survive a world rollback
-- (the same hard invariant the economy ledger holds): the authority is the row in
-- this table, not the live ItemStack[] in a GUI.
--
-- Same portability contract as V1-V5: the DDL stays in the subset SQLite (the
-- default, single-node servers), MySQL/MariaDB and PostgreSQL all accept. UUIDs
-- are the canonical 36-character text; the vault number is a plain INT; the row
-- count is a SMALLINT; instants are epoch milliseconds in a BIGINT so there is no
-- dialect-specific datetime handling. jOOQ's DDLDatabase parses this file
-- alongside V1-V5 at build time, so the generated classes always match the
-- runtime schema.
--
-- Queryable-vs-opaque split (architecture persistence invariant, 01-architecture
-- §"No opaque JSON-blob columns"): the facts a query, a quota check or an audit
-- line needs, the owner, the vault index, the size in rows and the last-touched
-- instant: are first-class, indexable columns. ONLY the ItemStack[] contents are
-- intrinsically opaque payload (not a queryable key), so they are the one column
-- allowed to serialize. They are stored as Bukkit's serialized item bytes encoded
-- to base64 in a TEXT column rather than a binary BLOB: BLOB is not portable
-- (PostgreSQL has no BLOB type, it uses bytea, and the SQLite/MySQL BLOB shapes
-- differ), whereas a base64 TEXT column is byte-identical across SQLite,
-- MySQL/MariaDB and PostgreSQL and keeps the column shape uniform with the rest of
-- the schema, which uses no binary types. The payload is opaque to SQL by design;
-- it is never filtered, ordered or partially updated. A save rewrites the whole
-- cell, so text encoding costs nothing a query could otherwise have used.

-- One row per (owner, idx): the Vault aggregate is keyed by its owner and its
-- index, so the table is keyed the same way. `idx` is the vault number the player
-- types (/vault <n>); `size` is the height in rows the .size.<rows> quota node
-- granted when the vault was last opened, so a shrunk quota never silently drops
-- the stored rows. `last_touched` is the epoch-millis of the last save, surfaced
-- by the admin /vault <player> audit and usable for housekeeping. `contents` is
-- the base64-encoded serialized ItemStack[], the opaque payload, nullable so a
-- freshly allocated empty vault needs no blob.
CREATE TABLE vaults (
    owner         VARCHAR(36)  NOT NULL,
    idx           INT          NOT NULL,
    size          SMALLINT     NOT NULL,
    last_touched  BIGINT       NOT NULL,
    contents      TEXT,
    CONSTRAINT pk_vaults PRIMARY KEY (owner, idx)
);

-- Serves the per-owner vault listing (the /vault index menu reads every vault a
-- player owns, ORDER BY idx) and the admin /vault <player> lookup. The leading
-- owner column scopes the scan to one player; the composite primary key already
-- covers (owner, idx) point reads, so this index backs the owner-only range read.
CREATE INDEX idx_vaults_owner ON vaults (owner);

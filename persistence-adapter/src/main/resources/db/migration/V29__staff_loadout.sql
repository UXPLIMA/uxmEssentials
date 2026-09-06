-- Schema for the staff bounded context: the DB-backed pre-staff-mode loadout. When
-- a player enters staff mode their real inventory, armour, off-hand and active potion
-- effects are captured and committed to this table BEFORE the inventory is swapped for
-- the gadget hotbar; on exit the row is read back, the real loadout restored, and only
-- then the row deleted. Persisting the loadout (never PDC) is what makes staff mode
-- item-loss-safe: a crash mid-mode leaves the real loadout recoverable from this row
-- rather than lost, and it survives a restart or world rollback (the same hard invariant
-- the economy ledger and the vaults context hold).
--
-- Same portability contract as V1-V28: the DDL stays in the subset SQLite (the default,
-- single-node servers), MySQL/MariaDB and PostgreSQL all accept. The player UUID is the
-- canonical 36-character text; the held slot, level and look-progress are plain numeric
-- columns; the flight flag is a SMALLINT (0/1) rather than a BOOLEAN so there is no
-- dialect-specific boolean handling (matching the read flag in V4 and the public flag in
-- V14); entered_at is the capture instant in epoch milliseconds in a BIGINT so there is
-- no dialect-specific datetime handling. jOOQ's DDLDatabase parses this file alongside
-- V1-V28 at build time, so the generated STAFF_LOADOUT class always matches the runtime
-- schema.
--
-- Queryable-vs-opaque split (architecture persistence invariant, 01-architecture
-- §"No opaque JSON-blob columns"): the scalar facts of the captured state, the held
-- hotbar slot, the experience level and progress, the game-mode name, the flight flag and
-- the capture instant: are first-class columns. The four item/effect regions are
-- intrinsically opaque payload (the adapter's serialized item/effect bytes, never filtered,
-- ordered or partially updated. A save rewrites the whole cell), so they are stored as
-- those bytes encoded to base64 in TEXT columns, exactly as the vaults context stores its
-- ItemStack[] (V6). base64 TEXT is byte-identical across SQLite, MySQL/MariaDB and
-- PostgreSQL, whereas BLOB is not portable (PostgreSQL has no BLOB type, it uses bytea, 
-- and the SQLite/MySQL BLOB shapes differ).

-- One row per owner: the loadout is keyed by the player who owns it, so the table is keyed
-- the same way (a single-row-per-owner table, upsert on the player key). inventory, armor,
-- offhand and potion_effects are the base64-encoded serialized regions, the opaque
-- payload; held_slot/exp_level/exp_progress/game_mode/flying are the captured scalars;
-- entered_at is the epoch-millis of the capture.
CREATE TABLE staff_loadout (
    player         VARCHAR(36)  NOT NULL,
    inventory      TEXT         NOT NULL,
    armor          TEXT         NOT NULL,
    offhand        TEXT         NOT NULL,
    potion_effects TEXT         NOT NULL,
    held_slot      INT          NOT NULL,
    exp_level      INT          NOT NULL,
    exp_progress   REAL         NOT NULL,
    game_mode      VARCHAR(16)  NOT NULL,
    flying         SMALLINT     NOT NULL,
    entered_at     BIGINT       NOT NULL,
    CONSTRAINT pk_staff_loadout PRIMARY KEY (player)
);

-- Re-key staff_loadout from (player) to (player, server_id). The pre-staff-mode loadout is
-- inherently per-server state: the inventory to restore is the one the player had on THIS
-- backend. Keyed by player alone, two backends sharing one DB clobber each other's captured
-- loadout. Entering staff mode on server B overwrites server A's saved row, and an exit then
-- restores the wrong items. Adding the origin server to the key gives every backend its own
-- independent row, so a save/load/delete on B never touches A's loadout. server_id is the same
-- backend identity stamped into the cross-server bus (network.server-id); a single-server
-- install keeps its lone default id and behaves exactly as before, just correctly keyed.
--
-- The table-rebuild pattern is used here (as in V22) because SQLite cannot ALTER an existing
-- primary key, and the portable DDL subset must work on SQLite (the default), MySQL/MariaDB
-- and PostgreSQL alike. server_id is VARCHAR(64) NOT NULL, matching the canonical text id;
-- the new primary key is the composite (player, server_id). Every other column keeps its
-- V29-V31 shape unchanged (the four base64 TEXT regions, the captured scalars, the SMALLINT
-- 0/1 flags and the BIGINT capture instant), so the row mapping is otherwise untouched.
--
-- Existing rows backfill to 'server-1', the default network.server-id a single-server install
-- runs with (NetworkConfig.DEFAULT_SERVER_ID). A pre-V62 row is only ever an un-restored loadout
-- from an interrupted exit / crash mid-mode, and dropping it would lose the player's real items
-- (the very thing the DB-backed loadout protects). Backfilling to the default id therefore
-- preserves that crash-recovery row for the common single-server case, where the runtime
-- server_id is 'server-1', so the join-recovery path still finds and restores it. A multi-server
-- install was already clobbering these rows before this fix, so no correct state is lost there.

CREATE TABLE staff_loadout_new (
    player          VARCHAR(36)  NOT NULL,
    server_id       VARCHAR(64)  NOT NULL,
    inventory       TEXT         NOT NULL,
    armor           TEXT         NOT NULL,
    offhand         TEXT         NOT NULL,
    potion_effects  TEXT         NOT NULL,
    held_slot       INT          NOT NULL,
    exp_level       INT          NOT NULL,
    exp_progress    REAL         NOT NULL,
    game_mode       VARCHAR(16)  NOT NULL,
    flying          SMALLINT     NOT NULL,
    vanished_before SMALLINT     NOT NULL DEFAULT 0,
    allow_flight    SMALLINT     NOT NULL DEFAULT 0,
    entered_at      BIGINT       NOT NULL,
    CONSTRAINT pk_staff_loadout_new PRIMARY KEY (player, server_id)
);

INSERT INTO staff_loadout_new (player, server_id, inventory, armor, offhand, potion_effects,
                               held_slot, exp_level, exp_progress, game_mode, flying,
                               vanished_before, allow_flight, entered_at)
SELECT player,
       'server-1',
       inventory,
       armor,
       offhand,
       potion_effects,
       held_slot,
       exp_level,
       exp_progress,
       game_mode,
       flying,
       vanished_before,
       allow_flight,
       entered_at
FROM staff_loadout;

DROP TABLE staff_loadout;
ALTER TABLE staff_loadout_new RENAME TO staff_loadout;

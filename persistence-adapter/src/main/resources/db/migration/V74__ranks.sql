-- Schema for the ranks bounded context: the DB-backed rank pointer. Rankup does
-- not read a permission plugin's group; the plugin tracks the player's current
-- rank itself, so this row is the authority. It is persisted to the database,
-- never to PDC, so it survives a world rollback (the same hard invariant the
-- economy ledger holds): the truth of who is on which rank is the row here, not
-- any live LuckPerms group a rankup action may also have set.
--
-- Same portability contract as V1-V73: the DDL stays in the subset SQLite (the
-- default, single-node servers), MySQL/MariaDB and PostgreSQL all accept. The
-- player uuid is the canonical 36-character text; the rank id is the operator's
-- ranks.conf section name; the prestige level is a plain INT defaulting to 0; the
-- last-write instant is epoch milliseconds in a BIGINT so there is no
-- dialect-specific datetime handling. jOOQ's DDLDatabase parses this file
-- alongside V1-V73 at build time, so the generated classes always match the
-- runtime schema.

-- One row per player: the rank pointer is keyed by the player, so the table is
-- keyed the same way. `rank_id` is the id of the rank the player is currently on
-- (an operator-authored ranks.conf section name), left as raw text so persistence
-- stays decoupled from whatever the live ladder holds. `prestige` is how many
-- times the player has reset the ladder from the top, defaulting to 0 for a player
-- who has never prestiged. `updated_at` is the epoch-millis of the last write,
-- usable for auditing and housekeeping.
CREATE TABLE player_ranks (
    uuid        VARCHAR(36)  NOT NULL,
    rank_id     VARCHAR(64)  NOT NULL,
    prestige    INT          NOT NULL DEFAULT 0,
    updated_at  BIGINT       NOT NULL,
    CONSTRAINT pk_player_ranks PRIMARY KEY (uuid)
);

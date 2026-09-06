-- The global announcer settings the /announce editor's settings screen owns. announcer.conf still ships the file
-- defaults (default-interval-seconds, min-players); this single-row table holds an operator's in-game override of
-- those two globals, set from the GUI rather than by editing the file. When a value here is non-negative it wins
-- over the file default; a negative sentinel means "unset, defer to the file". The announcer's merge step reads
-- this row each tick, so a change in the settings screen takes effect on the next rotation with no reload.
--
-- One sentinel row, keyed by a SMALLINT id of 0, the same single-row shape V33's moderation_lockdown uses: the
-- read and the update always address the same row, and it is seeded here so a read on a fresh database always finds
-- it (everything unset, defer to the file) without an upsert. The two overrides are BIGINT epoch-free counts:
-- interval_seconds is the cadence override, min_online_players the gate override; -1 in either is the unset
-- sentinel (a real interval is at least one second and a real gate is at least zero, so -1 can never collide with a
-- legitimate value). updated_at is a write-only BIGINT epoch-millis audit stamp the read path ignores, matching the
-- worth-override store (V63) and V65.
--
-- Same portability contract as V1-V65: the DDL stays in the subset SQLite, MySQL/MariaDB and PostgreSQL all accept,
-- with no dialect-specific clause. jOOQ's DDLDatabase parses this file at build time, so the generated
-- CommunicationAnnouncerSettings table matches the runtime schema.

CREATE TABLE communication_announcer_settings (
    id                  SMALLINT NOT NULL PRIMARY KEY,
    interval_seconds    BIGINT   NOT NULL DEFAULT -1,
    min_online_players  BIGINT   NOT NULL DEFAULT -1,
    updated_at          BIGINT   NOT NULL DEFAULT 0
);

INSERT INTO communication_announcer_settings (id, interval_seconds, min_online_players, updated_at) VALUES (0, -1, -1, 0);

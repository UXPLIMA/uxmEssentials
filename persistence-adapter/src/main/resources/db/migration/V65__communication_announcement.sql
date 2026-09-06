-- The DB-backed announcement set the /announce editor owns. The rotating announcer was config-only
-- (announcer.conf, read into AnnouncerConfig at wiring time), with no surface a GUI could write to. This table
-- holds the announcements an operator creates, edits, enables/disables, and deletes in-game; the announcer rotates
-- over the config set PLUS the enabled rows here, so the file-managed set stays backward compatible while the
-- editor-managed set is durable and survives a world rollback. One row per announcement id.
--
-- The lines are newline-joined into a single TEXT cell (an announcement's lines are always delivered together and
-- never indexed individually, so a child table would buy nothing), and the channels are a comma-separated list of
-- BroadcastChannel names. The condition is the raw operator-written display-condition string the editor pulls the
-- world:/permission: parts out of and recomposes; an empty string is unconditional. The sound is nullable (no
-- sound when null), and interval_seconds is nullable (rotate on the default cadence when null, else run on an
-- independent loop at that cadence).
--
-- enabled is an INTEGER flag (1 = on, 0 = off) rather than a BOOLEAN: SQLite (the default backend) has no native
-- BOOLEAN type and stores it as an integer affinity anyway, while a portable INTEGER reads back as the same shape
-- on SQLite, MySQL/MariaDB and PostgreSQL alike, every other flag in this schema (the V30/V31 staff flags, V62)
-- follows the same convention. updated_at is a write-only BIGINT epoch-millis audit stamp the read path ignores,
-- matching the worth-override store (V12/V63); like every timestamp here it is a BIGINT rather than a SQL
-- TIMESTAMP for the same cross-backend reason V2 documents.
--
-- Same portability contract as V1-V64: the DDL stays in the subset SQLite, MySQL/MariaDB and PostgreSQL all
-- accept, with no dialect-specific clause. jOOQ's DDLDatabase parses this file at build time, so the generated
-- CommunicationAnnouncement table matches the runtime schema.

CREATE TABLE communication_announcement (
    id                VARCHAR(64)   NOT NULL,
    lines             TEXT          NOT NULL,
    channels          VARCHAR(255)  NOT NULL,
    enabled           INTEGER       NOT NULL DEFAULT 1,
    display_condition VARCHAR(1024) NOT NULL DEFAULT '',
    sound             VARCHAR(255),
    interval_seconds  BIGINT,
    updated_at        BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_communication_announcement PRIMARY KEY (id)
);

-- The editor list reads every row ordered by id, and the announcer's merge reads only the enabled rows; the
-- leading enabled column lets the index serve the enabled filter, with id second so the ordered scan reads
-- straight off it.
CREATE INDEX idx_communication_announcement_enabled_id ON communication_announcement (enabled, id);

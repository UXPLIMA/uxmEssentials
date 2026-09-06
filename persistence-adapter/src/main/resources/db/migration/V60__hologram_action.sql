-- Adds the ordered click-action chain to a hologram, the same richer mechanism the NPC action chain (V41) added:
-- a clickable hologram (the V54 single click_command kept and still runs first) may now carry a list of typed
-- actions, each with a click trigger (LEFT/RIGHT/ANY) and a type (run-console, run-player, message, action-bar,
-- title, sound, connect). It mirrors the common hologram "actions" surface.
--
-- The actions live in a SEPARATE child table keyed (hologram_name, ordinal), NOT an opaque JSON blob: an action
-- is a first-class row that can be selected, ordered and edited in place, exactly like a V13 line or a V57 page
-- line. ordinal is the 0-based run order; click_trigger and type are the enum names; value is the raw operator
-- payload the runner interprets per type. The relationship is enforced by the application: it rewrites the
-- whole action set on every save and deletes these rows alongside the name row in one transaction, so there is
-- no ON DELETE CASCADE clause SQLite gates behind a pragma, matching hologram_lines (V13) and hologram_pages
-- (V57).
--
-- The click-trigger column is named click_trigger, not trigger: TRIGGER is a reserved keyword in MySQL/MariaDB
-- (and a non-portable column name there without backticks), so the prefixed name keeps the plain unquoted DDL
-- accepted on every backend, in line with the portability contract below, and matches V41's npc_action.
--
-- Same portability contract as V13/V41/V57: a plain CREATE TABLE / CREATE INDEX in the subset SQLite (the
-- default), MySQL/MariaDB and PostgreSQL all accept, with no dialect-specific clause. jOOQ's DDLDatabase parses
-- this file alongside V1-V59 at build time, so the generated HologramAction table and record appear with no
-- extra configuration.

CREATE TABLE hologram_action (
    hologram_name  VARCHAR(64)  NOT NULL,
    ordinal        INT          NOT NULL,
    click_trigger  VARCHAR(16)  NOT NULL,
    type           VARCHAR(32)  NOT NULL,
    value          VARCHAR(512) NOT NULL,
    CONSTRAINT pk_hologram_action PRIMARY KEY (hologram_name, ordinal)
);

CREATE INDEX idx_hologram_action_hologram ON hologram_action (hologram_name);

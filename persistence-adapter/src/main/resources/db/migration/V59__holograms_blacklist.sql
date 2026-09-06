-- Adds the per-hologram viewer blacklist: players a hologram is hidden from regardless of its visibility mode,
-- so an ALL hologram everyone else sees can still be hidden from named players. It is the inverse of the V44
-- manual-viewer set (which lists who a MANUAL hologram is shown to); this lists who any hologram is hidden from.
-- An empty blacklist, every existing hologram, leaves visibility exactly as before, so there is no migration.
--
-- The blacklisted players live in a SEPARATE child table keyed (hologram_name, player_uuid), NOT an opaque JSON
-- blob: the architecture persistence invariant is that every queryable fact is a first-class column, so a
-- blacklisted player is a row that can be selected, added and removed in place. player_uuid is the viewer's uuid
-- as canonical 36-char text, identical on every backend. The foreign-key-like relationship is enforced by the
-- application (it deletes the blacklist rows alongside the name row), kept dialect-portable by avoiding an ON
-- DELETE CASCADE clause SQLite gates behind a pragma. The hologram delete removes the blacklist and the row in
-- one transaction, mirroring hologram_lines (V13) and hologram_manual_viewer (V44).
--
-- Same portability contract as V1-V58: a plain CREATE TABLE / CREATE INDEX in the subset SQLite (the default),
-- MySQL/MariaDB and PostgreSQL all accept, with no dialect-specific clause. jOOQ's DDLDatabase parses this file
-- at build time, so the generated HologramBlacklist table and record appear with no extra configuration.

CREATE TABLE hologram_blacklist (
    hologram_name  VARCHAR(64)  NOT NULL,
    player_uuid    VARCHAR(36)  NOT NULL,
    CONSTRAINT pk_hologram_blacklist PRIMARY KEY (hologram_name, player_uuid)
);

CREATE INDEX idx_hologram_blacklist_hologram ON hologram_blacklist (hologram_name);

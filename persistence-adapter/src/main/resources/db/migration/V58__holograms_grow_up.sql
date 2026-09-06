-- Adds the grow-up flag: a hologram whose lines grow upward from its anchor (the anchor is the bottom of the
-- text) rather than the default downward (the anchor is the top). Stored as a SMALLINT 0/1 like the other
-- boolean display columns (text_shadow, see_through); NULL, every pre-V58 row, reads back as 0 (grow down),
-- so an existing hologram keeps its current downward layout with no data migration.
--
-- Same portability contract as the earlier holograms columns: a plain ALTER TABLE ADD COLUMN the subset SQLite
-- (the default), MySQL/MariaDB and PostgreSQL all accept, parsed by jOOQ's DDLDatabase at build time so the
-- generated Holograms record gains the column with no extra configuration.

ALTER TABLE holograms ADD COLUMN grow_up SMALLINT;

-- Adds text-display appearance and a per-hologram refresh interval to the V13 hologram store.
-- Every column is nullable with no DEFAULT clause so the ALTER stays portable across SQLite, MySQL/MariaDB
-- and PostgreSQL (SQLite gates a few DEFAULT forms behind pragmas); an absent value reads back as NULL and
-- the mapper resolves that to Appearance.defaults() / a static (0-tick) hologram, so existing rows keep their
-- current look with no data migration.
--
-- Types mirror the V13 / V32 conventions: VARCHAR for the billboard enum name, INT for packed/integer values,
-- SMALLINT 0/1 for the boolean, REAL for the float multipliers (the same shapes V13 used for yaw/pitch).
--
--   billboard         the facing mode enum name (CENTER / FIXED / VERTICAL / HORIZONTAL)
--   background_argb    the text-panel background as a packed ARGB int (NULL = vanilla translucent panel)
--   text_shadow        0/1. Whether the text casts a drop shadow
--   brightness_block   the block-light override 0-15 (NULL = world light)
--   brightness_sky     the sky-light override 0-15 (NULL = world light)
--   scale              the uniform scale factor (NULL = 1.0)
--   line_width         the max line width in pixels before wrapping (NULL = 200)
--   view_range         the view-distance multiplier (NULL = 1.0)
--   refresh_interval_ticks  how often the live entity re-renders, 0/NULL = static (rendered once)

ALTER TABLE holograms ADD COLUMN billboard VARCHAR(16);
ALTER TABLE holograms ADD COLUMN background_argb INT;
ALTER TABLE holograms ADD COLUMN text_shadow SMALLINT;
ALTER TABLE holograms ADD COLUMN brightness_block INT;
ALTER TABLE holograms ADD COLUMN brightness_sky INT;
ALTER TABLE holograms ADD COLUMN scale REAL;
ALTER TABLE holograms ADD COLUMN line_width INT;
ALTER TABLE holograms ADD COLUMN view_range REAL;
ALTER TABLE holograms ADD COLUMN refresh_interval_ticks INT;

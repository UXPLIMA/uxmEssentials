-- Adds the remaining text-display properties to the V13 hologram store: text alignment,
-- see-through, a per-axis scale and translation offset (extending the V35 uniform `scale` column), and the
-- display drop-shadow radius/strength (distinct from the V35 text_shadow flag, which is the per-glyph text
-- shadow). Every column is nullable with no DEFAULT clause so the ALTER stays portable across SQLite,
-- MySQL/MariaDB and PostgreSQL (SQLite gates a few DEFAULT forms behind pragmas); an absent value reads back
-- as NULL and the mapper resolves that to the matching Appearance default, so existing rows keep their current
-- look with no data migration.
--
-- Types mirror the V35 conventions: VARCHAR for the alignment enum name, SMALLINT 0/1 for the boolean, REAL
-- for the float scale/translation/shadow values (the same shape V35 used for the uniform scale).
--
--   text_alignment    the text line alignment enum name (CENTER / LEFT / RIGHT); NULL = CENTER
--   see_through       0/1. Whether the text renders through blocks; NULL = false
--   scale_y           the spatial scale on the Y axis; NULL = the uniform `scale` (V35)
--   scale_z           the spatial scale on the Z axis; NULL = the uniform `scale` (V35)
--   translation_x     the display-space X offset from the anchor; NULL = 0
--   translation_y     the display-space Y offset from the anchor; NULL = 0
--   translation_z     the display-space Z offset from the anchor; NULL = 0
--   shadow_radius     the display drop-shadow radius on the ground; NULL = the vanilla shadow
--   shadow_strength   the display drop-shadow strength; NULL = the vanilla shadow

ALTER TABLE holograms ADD COLUMN text_alignment VARCHAR(16);
ALTER TABLE holograms ADD COLUMN see_through SMALLINT;
ALTER TABLE holograms ADD COLUMN scale_y REAL;
ALTER TABLE holograms ADD COLUMN scale_z REAL;
ALTER TABLE holograms ADD COLUMN translation_x REAL;
ALTER TABLE holograms ADD COLUMN translation_y REAL;
ALTER TABLE holograms ADD COLUMN translation_z REAL;
ALTER TABLE holograms ADD COLUMN shadow_radius REAL;
ALTER TABLE holograms ADD COLUMN shadow_strength REAL;

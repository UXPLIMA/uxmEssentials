-- Adds a stored display rotation (yaw + pitch in degrees) to the V13 hologram store. This is the
-- explicit spin of the display itself, the /hologram rotate transition, kept separate from
-- the row's existing yaw/pitch (V13), which orient the hologram's anchor location, not the display
-- transform. A stored rotation is only visually meaningful with a FIXED billboard; the operator sets the
-- billboard separately, and the renderer applies this rotation regardless.
--
-- Unlike the V35-V37 nullable columns, these carry a NOT NULL DEFAULT 0 in the subset every backend
-- accepts: SQLite gates only a handful of DEFAULT forms (CURRENT_TIME, expressions in parentheses) behind
-- a pragma (a plain numeric literal default is portable across SQLite, MySQL/MariaDB and PostgreSQL) so
-- an existing row reads back as Rotation.NONE with no data migration. REAL matches the V13 yaw/pitch shape.
--
--   rotation_yaw    the display spin about the vertical axis, in degrees (0 = unrotated)
--   rotation_pitch  the display spin about the horizontal axis, in degrees (0 = unrotated)

ALTER TABLE holograms ADD COLUMN rotation_yaw REAL NOT NULL DEFAULT 0;
ALTER TABLE holograms ADD COLUMN rotation_pitch REAL NOT NULL DEFAULT 0;

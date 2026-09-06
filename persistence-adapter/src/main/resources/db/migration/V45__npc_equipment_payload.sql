-- Widens NPC equipment storage so a slot can hold a full serialized item (a named/enchanted/custom-model
-- item), not just a material name. V40 added six equip_<slot> VARCHAR(64) columns, each holding a bare Bukkit
-- material NAME (e.g. DIAMOND_HELMET). A serialized + base64 ItemStack is far longer than 64 chars, so those
-- columns cannot hold one on the backends that enforce VARCHAR length (MySQL/MariaDB, PostgreSQL).
--
-- We cannot widen the V40 columns in place: SQLite (the default backend) supports only RENAME / ADD / DROP in
-- ALTER TABLE: it has no ALTER COLUMN ... TYPE, and the three engines spell a type change differently
-- (MySQL MODIFY, PostgreSQL ALTER COLUMN ... TYPE), so no single portable ALTER COLUMN statement exists. The
-- portable move, and the shape the vaults (V6) and staff (V29) contexts already use for a base64 ItemStack
-- payload, is a plain ALTER TABLE ... ADD COLUMN ... TEXT. The same statement form V40 itself used, accepted
-- unchanged on SQLite, MySQL/MariaDB and PostgreSQL.
--
-- So this adds six new TEXT columns alongside the V40 ones. Going forward the mapper writes a slot's token to
-- the new equip_<slot>_b64 column and reads the new column first, falling back to the old VARCHAR column when
-- the new one is NULL. An existing NPC keeps its gear: its material name still lives in the V40 column and is
-- read by the fallback, so old NPCs render exactly as before while new ones can carry full items. The new token
-- is itself self-describing (a b64: prefix marks a serialized item, a bare name is a legacy material) so the
-- two shapes never collide.
--
-- Same portability contract as V1-V44: plain ALTER TABLE ... ADD COLUMN statements in the subset all three
-- backends accept. jOOQ's DDLDatabase parses this file alongside V1-V44 at build time, so the generated Npc
-- record gains the new TEXT columns (still mapped to String) with no extra configuration.

ALTER TABLE npc ADD COLUMN equip_mainhand_b64 TEXT;
ALTER TABLE npc ADD COLUMN equip_offhand_b64  TEXT;
ALTER TABLE npc ADD COLUMN equip_head_b64     TEXT;
ALTER TABLE npc ADD COLUMN equip_chest_b64    TEXT;
ALTER TABLE npc ADD COLUMN equip_legs_b64     TEXT;
ALTER TABLE npc ADD COLUMN equip_feet_b64     TEXT;

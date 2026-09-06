-- Records which player owns an NPC, so a per-player creation quota (the uxmessentials.npc.limit.<n> node) and a
-- per-owner view can be enforced. When set, owner_uuid is the creating player's UUID; an absent (NULL) value is
-- the default: a server/console-created NPC with no owner, which no per-player quota counts against. Existing
-- rows read back as NULL (owned by no one), so no NPC is retroactively attributed to a player.
--
-- The column is nullable with no DEFAULT clause so the ALTER stays portable across SQLite, MySQL/MariaDB and
-- PostgreSQL (SQLite gates a few DEFAULT forms behind pragmas), matching the V50 linked_npc_name column. The type
-- is a VARCHAR(36) holding the canonical 36-character UUID text, the same shape the world uuid is stored in.
--
--   owner_uuid   the creating player's UUID; NULL = no owner (server/console-created)

ALTER TABLE npc ADD COLUMN owner_uuid VARCHAR(36);

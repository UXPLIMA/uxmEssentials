-- Server lockdown flag. A single row that holds whether the server currently
-- refuses every login except holders of the lockdown-bypass permission. It is a
-- runtime toggle (an operator flips it with `/lockdown`), not a config value, so
-- it must survive a restart the way every other moderation fact does: a server
-- locked down before a crash stays locked down on the next boot until an operator
-- lifts it.
--
-- Same portability contract as V5 and the staff flags in V29-V31: the flag is a
-- SMALLINT (0/1) rather than a BOOLEAN so there is no dialect-specific boolean
-- handling, and the single sentinel row is keyed by a SMALLINT id of 0 so the read
-- and the update always address the same row. The row is seeded here so a read on a
-- fresh database always finds it (not-locked, the safe default) without an upsert.
-- jOOQ's DDLDatabase parses this file alongside V1-V32 at build time, so the
-- generated MODERATION_LOCKDOWN class always matches the runtime schema.
CREATE TABLE moderation_lockdown (
    id      SMALLINT NOT NULL PRIMARY KEY,
    enabled SMALLINT NOT NULL DEFAULT 0
);

INSERT INTO moderation_lockdown (id, enabled) VALUES (0, 0);

-- The per-player main-home choice behind /setmainhome, which home plain /home (no
-- name) teleports to. Until now plain /home always went to the first-created home;
-- this lets a player pick any of theirs as the destination. A row here is one stored
-- name keyed by the owner, queryable as a first-class column rather than a JSON blob,
-- so it survives a relog or a world rollback (it is never a transient PDC stamp).
--
-- One row per player: the absence of a row means the owner has never run
-- /setmainhome and plain /home falls back to the first-created home. The stored name
-- mirrors the homes.name column width (V1 `homes`) so the two never disagree on the
-- maximum length; a name that no longer matches a live home (after /delhome or
-- /renamehome) is tolerated by the resolver, which drops it and falls back the same
-- way, so no eager cleanup is needed here.
--
-- Same portability contract as V1-V8: a plain CREATE TABLE in the subset SQLite (the
-- default, single-node servers), MySQL/MariaDB and PostgreSQL all accept, with no
-- dialect-specific clause. jOOQ's DDLDatabase parses this file alongside V1-V8 at
-- build time, so the generated HomeMain table and record appear.

CREATE TABLE home_main (
    owner  VARCHAR(36) NOT NULL,
    name   VARCHAR(64) NOT NULL,
    CONSTRAINT pk_home_main PRIMARY KEY (owner)
);

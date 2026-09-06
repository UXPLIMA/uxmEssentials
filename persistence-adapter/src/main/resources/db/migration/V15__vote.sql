-- Adds the durable store behind the vote context: the global vote-party counter and the per-player
-- offline reward queue. Both must survive a restart. An accumulated party count must not reset on a
-- crash, and a vote that arrives while the voter is offline must still pay out on their next join, so
-- neither is PDC-backed (PDC would not survive a world rollback).
--
-- vote_party is a single-row global counter: id is always 1 and count is the votes accumulated towards
-- the next party. A single-row table (rather than a key/value blob) keeps the count a first-class,
-- queryable column, the architecture persistence invariant, that the application upserts on id = 1.
--
-- vote_queue holds the reward command batches owed to offline voters, keyed (player, idx): player is the
-- voter's canonical 36-char UUID text, idx the 0-based insertion order so a drain replays the commands in
-- the order they were queued, command the raw reward command (still carrying the {player} placeholder),
-- and queued_at epoch milliseconds in a BIGINT. The rows are a first-class child set, not an opaque JSON
-- blob: a drain selects then deletes a player's rows in one transaction so each batch pays out exactly
-- once. The foreign-key-like relationship is enforced by the application, kept dialect-portable by
-- avoiding an ON DELETE CASCADE clause SQLite gates behind a pragma.
--
-- Same portability contract as V1-V14: a plain CREATE TABLE / CREATE INDEX in the subset SQLite (the
-- default), MySQL/MariaDB and PostgreSQL all accept, with no dialect-specific clause. Count and idx are
-- INT, the instant is a BIGINT, the identifiers VARCHAR. jOOQ's DDLDatabase parses this file alongside
-- V1-V14 at build time, so the generated VoteParty and VoteQueue tables and records appear with no extra
-- configuration.

CREATE TABLE vote_party (
    id     INT NOT NULL,
    count  INT NOT NULL,
    CONSTRAINT pk_vote_party PRIMARY KEY (id)
);

CREATE TABLE vote_queue (
    player     VARCHAR(36)  NOT NULL,
    idx        INT          NOT NULL,
    command    VARCHAR(512) NOT NULL,
    queued_at  BIGINT       NOT NULL,
    CONSTRAINT pk_vote_queue PRIMARY KEY (player, idx)
);

CREATE INDEX idx_vote_queue_player ON vote_queue (player);

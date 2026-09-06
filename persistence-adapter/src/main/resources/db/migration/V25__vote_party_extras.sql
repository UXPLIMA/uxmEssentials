-- Adds participant tracking, period key, and threshold override to the vote party context.
--
-- vote_party_participants holds the UUID of every player who voted during the current party
-- window. The primary key on player ensures idempotent marks (calling markPartyParticipant
-- twice for the same player in one window is a no-op). The table is cleared after each party
-- fires so the next window starts empty.
--
-- period_key on vote_party is the calendar window boundary used by the automatic reset
-- schedule (epoch-day for DAILY, year*100+week for WEEKLY, 0 for NONE). 0 is the sentinel
-- value that means "no period key persisted yet" and triggers a first-run flush.
--
-- threshold_override on vote_party is a positive integer set by an escalation command or an
-- operator; 0 means the base threshold from config applies. Both columns are added to the
-- existing single-row (id = 1) vote_party row and default to 0, so existing rows are
-- populated without a data migration and inserts that omit these columns still succeed.
--
-- Portability contract: plain CREATE TABLE / ALTER TABLE in the subset accepted by SQLite
-- (default), MySQL/MariaDB, and PostgreSQL. No dialect-specific clause; jOOQ's DDLDatabase
-- parses this file alongside V1 to V24 at build time.

CREATE TABLE vote_party_participants (
    player VARCHAR(36) NOT NULL,
    CONSTRAINT pk_vote_party_participants PRIMARY KEY (player)
);

ALTER TABLE vote_party ADD COLUMN period_key         BIGINT  NOT NULL DEFAULT 0;
ALTER TABLE vote_party ADD COLUMN threshold_override INTEGER NOT NULL DEFAULT 0;

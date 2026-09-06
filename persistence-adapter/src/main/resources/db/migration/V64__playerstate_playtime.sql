-- Per-day playtime ledger for the playerstate /playtime breakdown. One row per
-- (player, calendar day) holds that day's active (non-AFK) and AFK seconds; the
-- periodic sampler adds the sample interval to today's row for each online player,
-- classified by their live AFK state. Storing per-day rows means the today /
-- last-7-days / last-30-days / all-time buckets the command renders fall out as
-- range SUMs at read time. There is no rollover job and no aggregate row to keep
-- consistent.
--
-- Same portability contract as V1-V63: the DDL stays in the subset SQLite (the
-- default), MySQL/MariaDB and PostgreSQL all accept, with no dialect-specific
-- clause. The UUID is the canonical 36-character text used everywhere else.
--
-- The day is the ISO-8601 calendar date (yyyy-MM-dd) stored as a fixed-width
-- VARCHAR rather than a SQL DATE. SQLite has no native DATE type (it stores dates
-- as text affinity), while MySQL and PostgreSQL do, so a DATE column would be read
-- back as three different shapes across the three backends and jOOQ's DDLDatabase
-- would generate a dialect-dependent binding. A yyyy-MM-dd string sorts
-- lexicographically in the same order it sorts chronologically, so the BETWEEN
-- range scan the SUM queries use is correct on every backend, and the binding is a
-- plain String on all three. Every other date/timestamp in this schema is already
-- a BIGINT or text for the same reason (see V2); this follows that convention.
--
-- The seconds columns are BIGINT DEFAULT 0 so a brand-new (player, day) row that is
-- inserted before its first delta is applied still reads back as zero rather than
-- null, and a single player-day can accumulate well past the INT range over a long
-- session history. jOOQ's DDLDatabase parses this file at build time, so the
-- generated PlayerstatePlaytime table matches the runtime schema.

CREATE TABLE playerstate_playtime (
    uuid            VARCHAR(36)  NOT NULL,
    day             VARCHAR(10)  NOT NULL,
    active_seconds  BIGINT       NOT NULL DEFAULT 0,
    afk_seconds     BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_playerstate_playtime PRIMARY KEY (uuid, day)
);

-- The summary queries scan a player's rows over a small date range (today, the last
-- 7 days, the last 30 days, all-time), so the leading uuid column lets the index
-- serve every one of them, with day second so the range predicate reads straight
-- off the index.
CREATE INDEX idx_playerstate_playtime_uuid_day ON playerstate_playtime (uuid, day);

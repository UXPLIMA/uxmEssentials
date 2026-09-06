-- Adds consecutive-day voting streak tracking to vote_totals. current_streak is the
-- player's active run of voting days, best_streak the longest run ever reached, and
-- streak_day_key the epoch-day of the most recent vote that touched the streak, so a
-- second vote on the same day leaves the run unchanged while the first vote of the next
-- day extends it. These live alongside the calendar buckets but advance independently of
-- the daily/weekly/monthly windows.
--
-- Portability contract: plain ALTER TABLE ADD COLUMN with a constant default, in the
-- subset accepted by SQLite (default), MySQL/MariaDB, and PostgreSQL. jOOQ's DDLDatabase
-- parses this file alongside V1-V26 at build time.

ALTER TABLE vote_totals ADD COLUMN current_streak BIGINT NOT NULL DEFAULT 0;
ALTER TABLE vote_totals ADD COLUMN best_streak    BIGINT NOT NULL DEFAULT 0;
ALTER TABLE vote_totals ADD COLUMN streak_day_key BIGINT NOT NULL DEFAULT 0;

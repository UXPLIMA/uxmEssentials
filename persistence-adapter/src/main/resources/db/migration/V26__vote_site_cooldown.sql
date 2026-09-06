-- Adds per-site vote cooldown tracking so the cooldown check can answer "when did
-- this player last vote on site X?" without scanning the full tally.
--
-- vote_site_cooldown is one row per (player, site_name): player is the voter's
-- canonical 36-char UUID text, site_name the vote-site identifier normalised to
-- lowercase on write (so a case-insensitive lookup is a plain equality scan with
-- no lower() on the stored value), and last_vote_at epoch milliseconds in a BIGINT
--, the same convention used by every other timestamp column in this schema
-- (vote_queue.queued_at, discord_link_pending.expires_at, moderation timestamps).
--
-- The (player, site_name) primary key doubles as the unique constraint, so the
-- upsert in recordLastVoteAtSite can use ON CONFLICT ... DO UPDATE without a
-- separate index.
--
-- Portability contract: plain CREATE TABLE in the subset accepted by SQLite
-- (default), MySQL/MariaDB, and PostgreSQL. No dialect-specific clause; jOOQ's
-- DDLDatabase parses this file alongside V1-V25 at build time.

CREATE TABLE vote_site_cooldown (
    player       VARCHAR(36)  NOT NULL,
    site_name    VARCHAR(64)  NOT NULL,
    last_vote_at BIGINT       NOT NULL,
    CONSTRAINT pk_vote_site_cooldown PRIMARY KEY (player, site_name)
);

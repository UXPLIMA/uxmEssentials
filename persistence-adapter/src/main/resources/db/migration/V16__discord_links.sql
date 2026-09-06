-- Adds the durable store behind account linking (/discordlink in game, /link in Discord): the one-time
-- pending codes and the confirmed UUID<->Discord bindings. A confirmed link is account identity, not a
-- transient flag, it must survive a restart and a world rollback, so neither table is PDC-backed (PDC
-- would not survive a rollback), exactly as the economy/vault invariant requires for identity-bearing data.
--
-- discord_link_pending holds at most one outstanding code per player: player is the voter's canonical
-- 36-char UUID text and the primary key (a fresh /discordlink upserts the same row, replacing the old
-- code), code is the one-time token the /link slash command looks the row up by. Unique across rows so a
-- redemption resolves to exactly one player, and expires_at is the epoch-millis instant after which the
-- code is rejected and the stale row swept. The code column is sized for the 6-8 char tokens with room to
-- spare.
--
-- discord_links holds the confirmed bindings: player the bound account (primary key, one binding per
-- player) and discord_id the bound Discord snowflake as text. Unique across rows so a single Discord
-- account binds to at most one player, the guard the confirmation enforces. With linked_at the epoch-millis
-- instant the binding was confirmed. Discord snowflakes are 17-20 digit integers; they are carried as text
-- (never arithmetic) so a VARCHAR is the portable, exact shape on every backend.
--
-- Same portability contract as V1-V15: a plain CREATE TABLE / CREATE INDEX in the subset SQLite (the
-- default), MySQL/MariaDB and PostgreSQL all accept, with no dialect-specific clause: the instants are
-- BIGINT, the identifiers VARCHAR. jOOQ's DDLDatabase parses this file alongside V1-V15 at build time, so
-- the generated DiscordLinkPending and DiscordLinks tables and records appear with no extra configuration.

CREATE TABLE discord_link_pending (
    player      VARCHAR(36) NOT NULL,
    code        VARCHAR(16) NOT NULL,
    expires_at  BIGINT      NOT NULL,
    CONSTRAINT pk_discord_link_pending PRIMARY KEY (player)
);

CREATE UNIQUE INDEX idx_discord_link_pending_code ON discord_link_pending (code);

CREATE TABLE discord_links (
    player      VARCHAR(36) NOT NULL,
    discord_id  VARCHAR(32) NOT NULL,
    linked_at   BIGINT      NOT NULL,
    CONSTRAINT pk_discord_links PRIMARY KEY (player)
);

CREATE UNIQUE INDEX idx_discord_links_discord ON discord_links (discord_id);

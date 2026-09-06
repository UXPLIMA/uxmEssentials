-- Schema for the moderation bounded context. The DB-backed sanction state that
-- survives restart (the hard moderation invariant): a mute, a jail sentence, a
-- tempban, the warning history and the last-seen/IP record never live in PDC or
-- an in-memory authority, so an offline `/jail`/`/banip`, a ban-on-login check at
-- `PlayerLoginEvent`, and the alt-detection IP lookup are all real DB reads that
-- a fresh server boot re-applies.
--
-- Same portability contract as V1-V4: the DDL stays in the subset SQLite (the
-- default, single-node servers), MySQL/MariaDB and PostgreSQL all accept. UUIDs
-- are the canonical 36-character text; instants are epoch milliseconds in a
-- BIGINT so there is no dialect-specific datetime handling; flags are a SMALLINT
-- (0/1) so there is no dialect-specific BOOLEAN handling. Every fact a query or
-- an audit line needs (target, actor, until, reason, jail name, the online
-- remaining millis, the last IP) is a first-class, indexable column: there are
-- no opaque JSON blobs (architecture persistence invariant). jOOQ's DDLDatabase
-- parses this file alongside V1-V4 at build time, so the generated classes always
-- match the runtime schema.

-- One row per actively muted target. `until` is the expiry in epoch millis; a
-- null `until` is a permanent mute (`/mute` without a duration), a non-null one a
-- `/tempmute`. `muted_by`/`muted_by_name` keep the issuer's UUID (null for a
-- console/system actor) and the name issued under so the audit and `/unmute`
-- failure messages render an issuer even after a rename. The presence of a row is
-- the mute: the real MutePolicy the messaging context consumes reads this table,
-- and `/unmute` deletes the row. `created_at` orders the most-recent sanction.
CREATE TABLE moderation_mutes (
    target          VARCHAR(36)  NOT NULL,
    until           BIGINT,
    reason          VARCHAR(256),
    muted_by        VARCHAR(36),
    muted_by_name   VARCHAR(16)  NOT NULL,
    created_at      BIGINT       NOT NULL,
    CONSTRAINT pk_moderation_mutes PRIMARY KEY (target)
);

-- Sweeps expired mutes (DELETE ... WHERE until IS NOT NULL AND until < ?). The
-- leading `until` column lets the expiry sweep read only the timed mutes off the
-- index; permanent mutes (null `until`) sort out and are never swept.
CREATE INDEX idx_moderation_mutes_until ON moderation_mutes (until);

-- One row per actively jailed target. `jail` is the named jail location the
-- player is held in (resolved by the moderation config, not stored here). The
-- countdown is online-only (docs/02-concurrency.md, docs/09-deployment.md): a
-- timed sentence accrues online time only, so `remaining_millis` holds the time
-- left to serve and is decremented only while the player is connected in the jail
-- world, then frozen on quit and resumed on rejoin. A null `remaining_millis` is
-- a permanent jail (`/jail` without a duration). `until` is kept null for an
-- online-only sentence and is only a wall-clock expiry when the operator opts a
-- jail into wall-clock countdown via `jail-countdown` in `moderation.conf`; the
-- two columns are mutually exclusive per sentence. `jailed_by`/`jailed_by_name`
-- mirror the mute issuer columns. The presence of a row is the jail: the teleport
-- context's jail gate reads it to block `/home`/`/tpa`, and `/unjail`, the only
-- release for a target who never logs back in, deletes the row.
CREATE TABLE moderation_jails (
    target            VARCHAR(36)  NOT NULL,
    jail              VARCHAR(64)  NOT NULL,
    until             BIGINT,
    remaining_millis  BIGINT,
    reason            VARCHAR(256),
    jailed_by         VARCHAR(36),
    jailed_by_name    VARCHAR(16)  NOT NULL,
    created_at        BIGINT       NOT NULL,
    CONSTRAINT pk_moderation_jails PRIMARY KEY (target)
);

-- One row per actively tempbanned target. `until` is the ban expiry in epoch
-- millis (non-null. A permanent ban is `/banip`/an external ban-list, not a
-- tempban). The ban-on-login listener at `PlayerLoginEvent` priority HIGHEST
-- reads this table by target UUID and kicks before player data loads when `until`
-- is still in the future; the sweep removes rows whose `until` has passed.
-- `banned_by`/`banned_by_name` mirror the mute issuer columns.
CREATE TABLE moderation_tempbans (
    target          VARCHAR(36)  NOT NULL,
    until           BIGINT       NOT NULL,
    reason          VARCHAR(256),
    banned_by       VARCHAR(36),
    banned_by_name  VARCHAR(16)  NOT NULL,
    created_at      BIGINT       NOT NULL,
    CONSTRAINT pk_moderation_tempbans PRIMARY KEY (target)
);

-- Serves the login check (target lookup) and bounds the expiry sweep
-- (DELETE ... WHERE until < ?). The leading `until` lets the engine read the
-- soon-to-expire bans straight off the index.
CREATE INDEX idx_moderation_tempbans_until ON moderation_tempbans (until);

-- Append-only warning history, one row per `/warn`. `id` is the synthetic key;
-- `target` is the warned player, `warned_by`/`warned_by_name` the issuer (null
-- UUID for a console actor), `reason` the free text, `ts` the issue time in epoch
-- millis. This table is only inserted into and read back (the `/warns <player>`
-- review lists newest-first), never updated, so warning history is preserved in
-- full and never overwritten by a later warning.
CREATE TABLE moderation_warns (
    id             BIGINT       NOT NULL,
    target         VARCHAR(36)  NOT NULL,
    reason         VARCHAR(256),
    warned_by      VARCHAR(36),
    warned_by_name VARCHAR(16)  NOT NULL,
    ts             BIGINT       NOT NULL,
    CONSTRAINT pk_moderation_warns PRIMARY KEY (id)
);

-- Serves `/warns <player>` newest-first (ORDER BY ts DESC) per target. The
-- leading `target` column scopes the index to one player's history; the
-- descending `ts` lets the engine read the ordering off the index without a sort.
CREATE INDEX idx_moderation_warns_target ON moderation_warns (target, ts DESC);

-- IP ban-list: one row per banned address. `ip` is the banned inet literal (the
-- key); `until` is null for a permanent `/banip` and a non-null epoch-millis
-- expiry for a timed one. The login listener resolves the connecting IP against
-- this table (alongside the per-UUID tempban) before data load. `banned_by`/
-- `banned_by_name` mirror the mute issuer columns; `target` records the UUID the
-- ban was applied against, if any (`/banip <player>` resolves one, `/banip <ip>`
-- leaves it null), so the audit `event=player_banip target=<uuid>` is replayable.
CREATE TABLE moderation_ip_bans (
    ip             VARCHAR(45)  NOT NULL,
    until          BIGINT,
    reason         VARCHAR(256),
    target         VARCHAR(36),
    banned_by      VARCHAR(36),
    banned_by_name VARCHAR(16)  NOT NULL,
    created_at     BIGINT       NOT NULL,
    CONSTRAINT pk_moderation_ip_bans PRIMARY KEY (ip)
);

-- The last-seen / last-IP record: one row per player, upserted on join/quit.
-- `name` is the most recent name (for the `/seen` rename-aware render); `last_ip`
-- is the most recent connecting address (an indexed column, never a JSON blob) 
-- so `/seenip` and the alt-detection check (other UUIDs sharing this IP) are real
-- DB lookups; `first_seen`/`last_seen` are the first-join and last-activity
-- instants in epoch millis that `/seen` reports.
CREATE TABLE moderation_seen (
    uuid        VARCHAR(36)  NOT NULL,
    name        VARCHAR(16)  NOT NULL,
    last_ip     VARCHAR(45),
    first_seen  BIGINT       NOT NULL,
    last_seen   BIGINT       NOT NULL,
    CONSTRAINT pk_moderation_seen PRIMARY KEY (uuid)
);

-- Serves the alt-detection lookup at login (SELECT uuid ... WHERE last_ip = ?):
-- the connecting player's IP is matched against every other UUID's stored IP, so
-- the address must be a first-class indexed column. Also serves `/seenip`.
CREATE INDEX idx_moderation_seen_ip ON moderation_seen (last_ip);

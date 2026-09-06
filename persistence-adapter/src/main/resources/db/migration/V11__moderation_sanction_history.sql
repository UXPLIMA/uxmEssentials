-- Append-only sanction history. One row per ban/unban/mute/unmute applied to a
-- target, so /banhistory and /mutehistory render a player's full disciplinary
-- record (not just the sanctions still in effect). The active-state tables (V5
-- moderation_mutes/moderation_tempbans/moderation_ip_bans) hold only the current
-- state and are cleared on a lift; this table is never updated and never deleted
-- from at runtime, so a lifted ban still shows in the history alongside the lift
-- that ended it. Same hard moderation invariant as V5: the record survives a
-- restart and a world rollback, never PDC.
--
-- Same portability contract as V1-V10: a plain CREATE TABLE in the subset SQLite
-- (the default, single-node servers), MySQL/MariaDB and PostgreSQL all accept,
-- with no dialect-specific clause. `id` is the synthetic key (max(id)+1 on insert,
-- the V5 moderation_warns pattern); `action` is the kind stored as text
-- (BAN/UNBAN/MUTE/UNMUTE), distinguishing the family a /banhistory or /mutehistory
-- read scopes to. `target` is the affected player's UUID as the canonical 36-char
-- text (the nil UUID for an /banip <ip> with no resolved player); `reason` the free
-- text; `actor`/`actor_name` the issuer (null UUID for a console actor, the name
-- always present) mirroring the V5 *_by columns; `ip` the banned address for an
-- IP-scoped row (null for a UUID-scoped one); `expires_at` the wall-clock expiry of
-- a timed sanction in epoch millis (null for a permanent sanction or a lift); `ts`
-- the apply instant in epoch millis. No opaque JSON, every fact a history line
-- needs is a first-class column. jOOQ's DDLDatabase parses this file alongside
-- V1-V10 at build time, so the generated ModerationSanctionHistory table and record
-- appear.

CREATE TABLE moderation_sanction_history (
    id          BIGINT       NOT NULL,
    action      VARCHAR(16)  NOT NULL,
    target      VARCHAR(36)  NOT NULL,
    reason      VARCHAR(256),
    actor       VARCHAR(36),
    actor_name  VARCHAR(16)  NOT NULL,
    ip          VARCHAR(45),
    expires_at  BIGINT,
    ts          BIGINT       NOT NULL,
    CONSTRAINT pk_moderation_sanction_history PRIMARY KEY (id)
);

-- Serves /banhistory and /mutehistory newest-first per target (… WHERE target = ?
-- AND action IN (…) ORDER BY ts DESC). The leading `target` scopes the index to one
-- player's history; the descending `ts` lets the engine read the ordering off the
-- index without a sort.
CREATE INDEX idx_moderation_sanction_history_target ON moderation_sanction_history (target, ts DESC);

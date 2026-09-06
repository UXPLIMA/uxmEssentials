-- Schema for the security bounded context's IP/alt guard (Phase 4). Every join records which account connected from
-- which address so the guard can spot accounts that share an IP (alts) and, optionally, cap how many distinct
-- accounts one address may carry. Like the two-factor and device-trust rows this is DB-backed and never PDC: an
-- alt link a restart or a world rollback forgot would silently hide an alt from staff.
--
-- Same portability contract as V1-V77: the DDL stays in the subset SQLite (the default), MySQL/MariaDB and
-- PostgreSQL all accept. The player uuid is the canonical 36-character text; `ip_token` is a one-way digest of the
-- connecting address (the raw IP is NEVER stored. The adapter hashes it before it reaches the DB), so one account
-- carries one row per distinct address and one address carries one row per account. `last_seen` is the epoch-millis
-- the link was last observed, in a BIGINT so there is no dialect-specific datetime handling. jOOQ's DDLDatabase
-- parses this file alongside V1-V77 at build time, so the generated classes always match the runtime schema.
CREATE TABLE security_ip (
    uuid       VARCHAR(36) NOT NULL,
    ip_token   VARCHAR(64) NOT NULL,
    last_seen  BIGINT      NOT NULL,
    CONSTRAINT pk_security_ip PRIMARY KEY (uuid, ip_token)
);

-- The alt lookup and the per-IP account cap both query by ip_token, so index it for the same-address fan-out.
CREATE INDEX idx_security_ip_token ON security_ip (ip_token);

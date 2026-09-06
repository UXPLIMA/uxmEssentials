-- Schema for the security bounded context's device-trust store (Phase 2). When an enrolled player proves their
-- second factor on join, their device is optionally remembered so the next join from the same device skips the
-- keypad until the trust lapses. Like the two-factor row this is DB-backed and never PDC: a trust a restart or a
-- world rollback forgot would send the player back to the keypad, defeating the point.
--
-- Same portability contract as V1-V76: the DDL stays in the subset SQLite (the default), MySQL/MariaDB and
-- PostgreSQL all accept. The player uuid is the canonical 36-character text; `ip_hash` is a one-way digest of the
-- connecting address (the raw IP is never stored. The adapter hashes it before it reaches the DB), so a player may
-- carry several trusted devices, one row each. `until` is the epoch-millis the trust expires at, in a BIGINT so there
-- is no dialect-specific datetime handling. jOOQ's DDLDatabase parses this file alongside V1-V76 at build time, so the
-- generated classes always match the runtime schema.
CREATE TABLE security_trusted (
    uuid     VARCHAR(36) NOT NULL,
    ip_hash  VARCHAR(64) NOT NULL,
    until    BIGINT      NOT NULL,
    CONSTRAINT pk_security_trusted PRIMARY KEY (uuid, ip_hash)
);

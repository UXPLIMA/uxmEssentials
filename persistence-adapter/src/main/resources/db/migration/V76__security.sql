-- Schema for the security bounded context, the durable two-factor store. A
-- second factor that a world rollback could wipe would be no protection at all,
-- so this row is DB-backed and never PDC (the same hard invariant the economy
-- ledger and the rank pointer hold): the truth of who has 2FA and what it is
-- lives here.
--
-- Same portability contract as V1-V75: the DDL stays in the subset SQLite (the
-- default, single-node servers), MySQL/MariaDB and PostgreSQL all accept. The
-- player uuid is the canonical 36-character text and the primary key; the two
-- factor columns are nullable because a player may hold either factor alone or
-- both, and enrolling one must not require the other. The last-write instant is
-- epoch milliseconds in a BIGINT so there is no dialect-specific datetime
-- handling. jOOQ's DDLDatabase parses this file alongside V1-V75 at build time,
-- so the generated classes always match the runtime schema.

-- One row per player. `pin_hash` is the PIN's salted one-way digest, serialised
-- as `algorithm:salt:hash` (the same PBKDF2 hasher the player-warp password uses)
--: the PIN plaintext is never stored. `totp_secret_enc` is the TOTP shared
-- secret encrypted with AES-GCM under a server key-file, because TOTP
-- verification needs the secret back and so it cannot be a one-way hash; it is
-- never stored or logged in plaintext. Both are NULL until the matching factor is
-- enrolled. `enrolled_at` is the epoch-millis of the first enrolment.
CREATE TABLE security_2fa (
    uuid             VARCHAR(36)   NOT NULL,
    pin_hash         VARCHAR(255),
    totp_secret_enc  VARCHAR(255),
    enrolled_at      BIGINT        NOT NULL,
    CONSTRAINT pk_security_2fa PRIMARY KEY (uuid)
);

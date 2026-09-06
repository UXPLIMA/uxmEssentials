-- Schema for the messaging bounded context, the persistent mailbox and the
-- persistent ignore list. Private messages themselves are transient/real-time
-- and never touch this schema; only the DB-backed facts live here.
--
-- Same portability contract as V1/V2/V3: the DDL stays in the subset SQLite (the
-- default, single-node servers), MySQL/MariaDB and PostgreSQL all accept. UUIDs
-- are the canonical 36-character text; timestamps are epoch milliseconds in a
-- BIGINT so there is no dialect-specific datetime handling; the read flag is a
-- SMALLINT (0/1) so there is no dialect-specific BOOLEAN handling and `read`
-- a reserved word on some backends, is never used as an identifier. Every fact
-- a query needs (recipient, sender, body, sent_at, read state, ignore scope) is
-- a first-class column. There are no opaque JSON blobs (architecture
-- persistence invariant). jOOQ's DDLDatabase parses this file alongside V1-V3 at
-- build time, so the generated classes always match the runtime schema.
--
-- Mail is DB-backed and survives restart (the hard messaging invariant): an
-- offline recipient still receives mail and reads it on next join. Mail is
-- text-only: there are no item attachments (out of scope).

-- One row per delivered mail. The recipient is always a UUID; the sender keeps
-- both its UUID and the name it sent under, so a console/system sender (null
-- uuid) still renders a name and a since-renamed sender still shows the name at
-- send time. is_read is the read flag; sent_at drives mail expiry and the
-- newest-first listing.
CREATE TABLE mail (
    id           BIGINT       NOT NULL,
    recipient    VARCHAR(36)  NOT NULL,
    sender       VARCHAR(36),
    sender_name  VARCHAR(16)  NOT NULL,
    body         VARCHAR(256) NOT NULL,
    sent_at      BIGINT       NOT NULL,
    is_read      SMALLINT     NOT NULL,
    CONSTRAINT pk_mail PRIMARY KEY (id)
);

-- Serves the per-recipient mailbox read newest-first and bounds the expiry sweep
-- (DELETE ... WHERE sent_at < ?). The leading recipient column lets the index
-- serve one mailbox; the descending sent_at column lets the engine read the
-- ordering straight off the index without a sort.
CREATE INDEX idx_mail_recipient ON mail (recipient, sent_at DESC);

-- One row per (owner, ignored): the ignore list is persistent and ignore-aware
-- /msg resolution and the async chat-mute path both read it. scope keeps the
-- stable enum name (e.g. the kind of traffic ignored) as a first-class column so
-- a future per-scope query needs no migration. The composite primary key makes
-- a duplicate /ignore idempotent at the database.
CREATE TABLE ignores (
    owner    VARCHAR(36) NOT NULL,
    ignored  VARCHAR(36) NOT NULL,
    scope    VARCHAR(32) NOT NULL,
    CONSTRAINT pk_ignores PRIMARY KEY (owner, ignored)
);

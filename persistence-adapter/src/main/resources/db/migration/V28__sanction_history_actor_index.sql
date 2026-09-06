-- Index the sanction history by actor so /staffhistory can answer "every action
-- this staff member issued, newest-first" off an index instead of a full-table
-- scan. V11 already indexes (target, ts DESC) for the per-target reads
-- (/banhistory, /mutehistory, /history); this is its mirror on the issuer side.
-- The leading `actor` scopes the index to one staff member's actions; the read
-- still orders by ts DESC, but the actor scope is the selective predicate here so
-- a plain (actor) index is enough. Console actions and the auto-escalation system
-- actor both record under the nil UUID (0,0), not null, so they ARE indexed: they
-- are just unreachable via /staffhistory, which resolves a real played-before
-- account and no account maps to (0,0). So this index serves real-staff lookups
-- while console/system actions stay out of any human staffer's audit by design.
--
-- Portability contract: a plain CREATE INDEX in the subset SQLite (default),
-- MySQL/MariaDB, and PostgreSQL all accept, with no dialect-specific clause
-- the same contract as V1-V27. jOOQ's DDLDatabase parses this file alongside the
-- earlier migrations at build time.

CREATE INDEX idx_moderation_sanction_history_actor ON moderation_sanction_history (actor);

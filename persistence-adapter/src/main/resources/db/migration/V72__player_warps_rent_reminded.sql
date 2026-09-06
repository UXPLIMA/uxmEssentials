-- Rent-reminder dedup counter. The rent sweep mails a warp's owner a "rent due soon" heads-up once per configured
-- window (24/12/6/1h ahead); this column records the highest window already mailed for the current paid term so a
-- second sweep in the same window sends no duplicate, and the settle pass resets it to 0 once rent is paid. It is a
-- persistence-only counter, never a fact on the PlayerWarp aggregate, so no domain field maps to it; the reminder
-- projection reads it and a guarded UPDATE bumps it.
--
-- Additive, portable DDL only: a plain ADD COLUMN with a constant default, no dialect clause and no ON DELETE
-- CASCADE, so it parses identically on SQLite (default), MySQL/MariaDB, and PostgreSQL, and through the jOOQ
-- DDLDatabase the code generator parses at build time.
ALTER TABLE player_warps ADD COLUMN rent_reminded_stage INT NOT NULL DEFAULT 0;

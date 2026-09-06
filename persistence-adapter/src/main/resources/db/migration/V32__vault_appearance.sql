-- Per-vault display name + icon (a Material name), both optional. Nullable, no default: absence means
-- "use the configured default name/icon". VARCHAR keeps the add portable across SQLite/MySQL/Postgres.
ALTER TABLE vaults ADD COLUMN display_name VARCHAR(256);
ALTER TABLE vaults ADD COLUMN icon VARCHAR(128);

-- Browse read-model support. The old browse GUI read the whole player_warps table into memory and paginated in
-- Java; this task replaces that with a single filter + sort + LIMIT/OFFSET query, and these are the columns and
-- indexes it needs that the V70 rebuild does not already carry.
--
-- random_sort is a persistence-only ordering column, never a fact on the aggregate. A shuffled browse cannot be
-- paged with ORDER BY RANDOM(). The order would change between page reads and the same warp could appear on two
-- pages or none, so each warp is stamped an application-random long on insert and RANDOM browse orders by it.
-- A scheduled reshuffle rewrites the column so the order is not frozen forever.
--
-- The three indexes cover the browse's filter and sort shapes V70 lacks: the composite category/server filter
-- axis, the favourite-count sort, and the random sort. V70 already indexes (status, access),
-- (status, rating_score), (status, visit_count), and (status, created_at), so those sorts are not repeated here.
--
-- Additive, portable DDL only: a plain ADD COLUMN with a constant default and plain CREATE INDEX, no dialect
-- clause and no ON DELETE CASCADE, so it parses identically on SQLite (default), MySQL/MariaDB, and PostgreSQL,
-- and through the jOOQ DDLDatabase the code generator parses at build time.
ALTER TABLE player_warps ADD COLUMN random_sort BIGINT NOT NULL DEFAULT 0;
CREATE INDEX idx_player_warps_browse ON player_warps (status, access, category_id, server_id);
CREATE INDEX idx_player_warps_status_favourites ON player_warps (status, favourite_count);
CREATE INDEX idx_player_warps_random ON player_warps (status, random_sort);

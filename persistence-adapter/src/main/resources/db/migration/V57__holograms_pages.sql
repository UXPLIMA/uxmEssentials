-- Adds multi-page holograms (the "pages" feature): a hologram may carry an
-- ordered set of pages, and a viewer sees one page at a time and clicks to cycle to the next. Page 0 stays the
-- hologram's existing line set in hologram_lines (V13). Untouched, so every existing hologram is simply a
-- one-page hologram with no data migration; the extra pages 1..n live here, one row per (hologram, page_index,
-- idx), so a page line stays a first-class, queryable, in-place-editable row exactly like a V13 line, never an
-- opaque JSON blob.
--
-- page_index is the 1-based extra-page number (page 0 is hologram_lines); idx is the 0-based render order
-- within the page; text is the raw MiniMessage source. Same portability contract as V13: a plain CREATE TABLE /
-- CREATE INDEX the subset SQLite (the default), MySQL/MariaDB and PostgreSQL all accept, with no dialect-specific
-- clause, and no ON DELETE CASCADE (the application rewrites the whole page set on every save and deletes the
-- rows alongside the name row in one transaction). jOOQ's DDLDatabase parses this alongside V1-V56 at build
-- time, so the generated HologramPages table and record appear with no extra configuration.

CREATE TABLE hologram_pages (
    hologram    VARCHAR(64)  NOT NULL,
    page_index  INT          NOT NULL,
    idx         INT          NOT NULL,
    text        VARCHAR(512) NOT NULL,
    CONSTRAINT pk_hologram_pages PRIMARY KEY (hologram, page_index, idx)
);

CREATE INDEX idx_hologram_pages_hologram ON hologram_pages (hologram);

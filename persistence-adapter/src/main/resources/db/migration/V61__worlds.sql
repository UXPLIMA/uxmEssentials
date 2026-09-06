-- V61__worlds.sql, world-management metadata (sub-project A).
-- Portable subset (SQLite default / MySQL / PostgreSQL): UUID as VARCHAR(36), instant as BIGINT
-- epoch-ms, boolean as INT 0/1. World files stay on disk; this is the managed metadata only.
CREATE TABLE world (
    name                VARCHAR(64)  NOT NULL,
    uid                 VARCHAR(36),
    environment         VARCHAR(16)  NOT NULL,
    world_type          VARCHAR(16)  NOT NULL,
    seed                BIGINT,
    generator_ref       VARCHAR(255),
    dimension           VARCHAR(128),
    generate_structures INT          NOT NULL,
    alias               VARCHAR(64),
    auto_load           INT          NOT NULL,
    adopted             INT          NOT NULL,
    created_at          BIGINT       NOT NULL,
    created_by          VARCHAR(36),
    CONSTRAINT pk_world PRIMARY KEY (name)
);

-- Open-ended per-world property + gamerule store. Created now (schema future-proof); written by
-- sub-project B. Referential cleanup on world delete is performed in application code.
CREATE TABLE world_setting (
    world_name    VARCHAR(64)  NOT NULL,
    setting_key   VARCHAR(64)  NOT NULL,
    setting_value VARCHAR(512) NOT NULL,
    CONSTRAINT pk_world_setting PRIMARY KEY (world_name, setting_key)
);

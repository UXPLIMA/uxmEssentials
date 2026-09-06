-- Rebuild player_warps around a stable surrogate id so a rename never orphans a side row (the old
-- (owner,name) key orphaned ratings on every rename/delete). The legacy tables are kept intact under a
-- _v1_legacy name for the one-shot Java data migration (T6) to copy across. This file is schema only,
-- it moves no data. id is an application-assigned BIGINT (max(id)+1, the V5/V11/V69 idiom), so the schema
-- needs no dialect-specific auto-increment; every value is a typed column (no JSON blob); money is
-- DECIMAL(20,4) and every instant a BIGINT of epoch milliseconds, matching V1-V69. The side tables carry a
-- plain indexed warp_id with no ON DELETE CASCADE (SQLite gates that behind a pragma), the repository
-- deletes a warp's side rows in the same transaction as the parent.
--
-- Two identifiers on the new table deliberately break from the V14 names they otherwise mirror: the primary
-- key is named pk_player_warps_v2 and the owner index idx_player_warps_by_owner. Renaming (not dropping) the
-- old table leaves V14's pk_player_warps constraint and idx_player_warps_owner index alive under the
-- _v1_legacy table, and both constraint and index names are global (per schema, not per table) in
-- PostgreSQL, SQLite, and the H2 engine jOOQ's DDL parser simulates against. Reusing either V14 name would
-- collide there. A distinct name on the new table sidesteps the clash on every backend (MySQL, which scopes
-- them per table, never saw a clash either way) without a non-portable DROP of the legacy identifiers.

ALTER TABLE player_warps RENAME TO player_warps_v1_legacy;
ALTER TABLE player_warp_ratings RENAME TO player_warp_ratings_v1_legacy;

CREATE TABLE player_warps (
    id                  BIGINT         NOT NULL,
    name                VARCHAR(32)    NOT NULL,
    display_name        VARCHAR(128),
    owner               VARCHAR(36)    NOT NULL,
    owner_name          VARCHAR(32),
    server_id           VARCHAR(64),
    world               VARCHAR(36)    NOT NULL,
    world_name          VARCHAR(128)   NOT NULL,
    x                   DOUBLE PRECISION NOT NULL,
    y                   DOUBLE PRECISION NOT NULL,
    z                   DOUBLE PRECISION NOT NULL,
    yaw                 REAL           NOT NULL,
    pitch               REAL           NOT NULL,
    category_id         VARCHAR(64),
    description         VARCHAR(512),
    icon                VARCHAR(256),
    access              VARCHAR(16)    NOT NULL,
    password_algorithm  VARCHAR(32),
    password_salt       VARCHAR(64),
    password_hash       VARCHAR(128),
    status              VARCHAR(16)    NOT NULL,
    price_amount        DECIMAL(20, 4) NOT NULL DEFAULT 0,
    price_currency      VARCHAR(32)    NOT NULL DEFAULT 'default',
    earned_amount       DECIMAL(20, 4) NOT NULL DEFAULT 0,
    earned_currency     VARCHAR(32)    NOT NULL DEFAULT 'default',
    rating_sum          BIGINT         NOT NULL DEFAULT 0,
    rating_count        INT            NOT NULL DEFAULT 0,
    rating_average      DOUBLE PRECISION NOT NULL DEFAULT 0,
    rating_score        DOUBLE PRECISION NOT NULL DEFAULT 0,
    visit_count         BIGINT         NOT NULL DEFAULT 0,
    unique_visitors     INT            NOT NULL DEFAULT 0,
    favourite_count     INT            NOT NULL DEFAULT 0,
    sponsored_until     BIGINT,
    sponsor_slot        INT,
    rent_paid_until     BIGINT,
    rent_suspended_at   BIGINT,
    rent_archive_after  BIGINT,
    warmup_seconds      DOUBLE PRECISION,
    cooldown_seconds    DOUBLE PRECISION,
    departure_sound     VARCHAR(128),
    arrival_sound       VARCHAR(128),
    departure_particle  VARCHAR(128),
    arrival_particle    VARCHAR(128),
    created_at          BIGINT         NOT NULL,
    updated_at          BIGINT         NOT NULL,
    CONSTRAINT pk_player_warps_v2 PRIMARY KEY (id),
    CONSTRAINT uq_player_warps_name UNIQUE (name)
);

CREATE INDEX idx_player_warps_by_owner ON player_warps (owner);
CREATE INDEX idx_player_warps_status_access ON player_warps (status, access);
CREATE INDEX idx_player_warps_status_score ON player_warps (status, rating_score);
CREATE INDEX idx_player_warps_status_visits ON player_warps (status, visit_count);
CREATE INDEX idx_player_warps_status_created ON player_warps (status, created_at);
CREATE INDEX idx_player_warps_sponsored ON player_warps (sponsored_until);
CREATE INDEX idx_player_warps_rent ON player_warps (rent_paid_until);

CREATE TABLE player_warp_ratings (
    warp_id     BIGINT      NOT NULL,
    player_uuid VARCHAR(36) NOT NULL,
    stars       INT         NOT NULL,
    rated_at    BIGINT      NOT NULL,
    CONSTRAINT pk_player_warp_ratings PRIMARY KEY (warp_id, player_uuid)
);
CREATE INDEX idx_player_warp_ratings_warp ON player_warp_ratings (warp_id);

CREATE TABLE player_warp_visits (
    warp_id      BIGINT      NOT NULL,
    visitor_uuid VARCHAR(36) NOT NULL,
    first_at     BIGINT      NOT NULL,
    last_at      BIGINT      NOT NULL,
    visit_count  BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_player_warp_visits PRIMARY KEY (warp_id, visitor_uuid)
);
CREATE INDEX idx_player_warp_visits_warp ON player_warp_visits (warp_id);

CREATE TABLE player_warp_bans (
    warp_id      BIGINT      NOT NULL,
    player_uuid  VARCHAR(36) NOT NULL,
    banned_until BIGINT,
    reason       VARCHAR(256),
    banned_by    VARCHAR(36),
    banned_at    BIGINT      NOT NULL,
    CONSTRAINT pk_player_warp_bans PRIMARY KEY (warp_id, player_uuid)
);
CREATE INDEX idx_player_warp_bans_warp ON player_warp_bans (warp_id);

CREATE TABLE player_warp_whitelist (
    warp_id     BIGINT      NOT NULL,
    player_uuid VARCHAR(36) NOT NULL,
    added_at    BIGINT      NOT NULL,
    CONSTRAINT pk_player_warp_whitelist PRIMARY KEY (warp_id, player_uuid)
);
CREATE INDEX idx_player_warp_whitelist_warp ON player_warp_whitelist (warp_id);

CREATE TABLE player_warp_members (
    warp_id     BIGINT      NOT NULL,
    player_uuid VARCHAR(36) NOT NULL,
    role        VARCHAR(16) NOT NULL,
    added_at    BIGINT      NOT NULL,
    CONSTRAINT pk_player_warp_members PRIMARY KEY (warp_id, player_uuid)
);
CREATE INDEX idx_player_warp_members_warp ON player_warp_members (warp_id);

CREATE TABLE player_warp_favourites (
    player_uuid VARCHAR(36) NOT NULL,
    warp_id     BIGINT      NOT NULL,
    added_at    BIGINT      NOT NULL,
    CONSTRAINT pk_player_warp_favourites PRIMARY KEY (player_uuid, warp_id)
);
CREATE INDEX idx_player_warp_favourites_warp ON player_warp_favourites (warp_id);

CREATE TABLE player_warp_payments (
    warp_id      BIGINT         NOT NULL,
    player_uuid  VARCHAR(36)    NOT NULL,
    amount       DECIMAL(20, 4) NOT NULL,
    currency     VARCHAR(32)    NOT NULL,
    paid_at      BIGINT         NOT NULL,
    CONSTRAINT pk_player_warp_payments PRIMARY KEY (warp_id, player_uuid, paid_at)
);
CREATE INDEX idx_player_warp_payments_warp ON player_warp_payments (warp_id);

CREATE TABLE player_warp_rating_rewards (
    subject_uuid VARCHAR(36) NOT NULL,
    warp_id      BIGINT      NOT NULL,
    reward_id    VARCHAR(64) NOT NULL,
    kind         VARCHAR(16) NOT NULL,
    awarded_at   BIGINT      NOT NULL,
    CONSTRAINT pk_player_warp_rating_rewards PRIMARY KEY (subject_uuid, warp_id, reward_id)
);
CREATE INDEX idx_player_warp_rating_rewards_warp ON player_warp_rating_rewards (warp_id);

CREATE TABLE player_warp_pending_teleports (
    player_uuid   VARCHAR(36)    NOT NULL,
    warp_id       BIGINT         NOT NULL,
    target_server VARCHAR(64)    NOT NULL,
    origin_server VARCHAR(64)    NOT NULL,
    requested_at  BIGINT         NOT NULL,
    paid_amount   DECIMAL(20, 4),
    paid_currency VARCHAR(32),
    CONSTRAINT pk_player_warp_pending_teleports PRIMARY KEY (player_uuid)
);
CREATE INDEX idx_player_warp_pending_teleports_warp ON player_warp_pending_teleports (warp_id);

-- Every IP each player has connected from (not just the latest the V5 moderation_seen row keeps). Powers
-- alt detection across all historical addresses (not only the current one) and the optional STRICT
-- address-strictness that fans a UUID ban out to a target's known IPs. This broadens IP retention: an
-- operator who must keep that surface small caps it with the moderation config (and the censor option that
-- masks addresses in /alts and /seen output): see modules/moderation/config.conf.
--
-- Same portability contract as V5 and V33: VARCHAR(36) UUIDs, instants in epoch-millis BIGINTs, no
-- dialect-specific datetime handling. The address is VARCHAR(45), the max IPv6 literal length, matching
-- the moderation_seen.last_ip and moderation_ip_bans.ip columns. One row per (player, address): the join
-- capture upserts it, bumping last_seen on a repeat connection from the same address rather than inserting a
-- duplicate, so a player's history is the set of distinct addresses they have ever used. jOOQ's DDLDatabase
-- parses this file alongside V1-V33 at build time, so the generated MODERATION_IP_HISTORY class always
-- matches the runtime schema.
CREATE TABLE moderation_ip_history (
    uuid        VARCHAR(36)  NOT NULL,
    ip          VARCHAR(45)  NOT NULL,
    first_seen  BIGINT       NOT NULL,
    last_seen   BIGINT       NOT NULL,
    CONSTRAINT pk_moderation_ip_history PRIMARY KEY (uuid, ip)
);

-- Serves the broadened alt-detection lookup (other UUIDs whose history contains one of a target's known
-- addresses) and the STRICT IP-ban fan-out, both of which match by address rather than by player.
CREATE INDEX idx_moderation_ip_history_ip ON moderation_ip_history (ip);

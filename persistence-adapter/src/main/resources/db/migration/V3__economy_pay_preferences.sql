-- The /paytoggle accept-pay flag. A queryable key-value row on the player's
-- economy state, not a JSON blob, so it survives relog and can be read without
-- materialising a wallet (docs/11-economy-integration.md §9.1).
--
-- Same portability contract as V1/V2: the boolean is stored as a SMALLINT (0/1)
-- so there is no dialect-specific BOOLEAN handling; SQLite (the default),
-- MySQL/MariaDB and PostgreSQL all accept it. One row per player; the absence of
-- a row means the player has never run /paytoggle and takes the configured
-- economy.pay.toggle-default.

CREATE TABLE economy_pay_preferences (
    owner        VARCHAR(36) NOT NULL,
    accepts_pay  SMALLINT    NOT NULL,
    CONSTRAINT pk_economy_pay_preferences PRIMARY KEY (owner)
);

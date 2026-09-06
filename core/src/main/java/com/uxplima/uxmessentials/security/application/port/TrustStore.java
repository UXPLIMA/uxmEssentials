package com.uxplima.uxmessentials.security.application.port;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbound port for the durable record of which devices a player has already verified from, so a returning player on a
 * trusted device is not made to prove their second factor on every single join. Like the two-factor store it is
 * DB-backed and survives a restart or a world rollback (never PDC): a trust that a reboot forgot would send a player
 * back to the keypad every day, and a trust a rollback forgot would be worse.
 *
 * <p>A device is identified by a <b>salted-or-hashed IP token</b>, never the raw address. The adapter hashes the
 * connecting IP before it reaches this port, so the store holds no personally identifying network data. A trust
 * carries an expiry instant; {@link #isTrusted} answers only for a token whose trust has not lapsed, and re-trusting
 * the same {@code (player, ipHash)} simply slides the expiry forward.
 */
public interface TrustStore {

    /** Whether {@code playerId} has an unexpired trust for {@code ipHash} as of {@code now}. */
    boolean isTrusted(UUID playerId, String ipHash, Instant now);

    /** Record (or renew) a trust for {@code playerId} from {@code ipHash}, valid until {@code until}. */
    void trust(UUID playerId, String ipHash, Instant until);

    /**
     * Forget every device trust {@code playerId} holds, so their next join is not skipped by the trusted-device
     * bypass and they must prove their second factor again. Used by the admin force-re-verification flow; a no-op
     * for a player who holds no trust.
     */
    void revoke(UUID playerId);
}

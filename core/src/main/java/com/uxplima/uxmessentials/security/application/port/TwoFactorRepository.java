package com.uxplima.uxmessentials.security.application.port;

import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.security.domain.TwoFactorSecret;

/**
 * Outbound port for the durable, per-player two-factor store. It is DB-backed and survives a world rollback, never
 * PDC. A second factor a world reset could wipe would be no protection at all (the same hard invariant the economy
 * ledger holds). One row per player carries at most one PIN and one TOTP secret; the two enrol independently.
 *
 * <p>The port draws the crypto boundary deliberately. The <b>PIN is one-way hashed</b> at rest, so it is written
 * through {@link #setPin} and checked through {@link #verifyPin} (which re-hashes the candidate), the plaintext
 * never leaves the adapter and the hash never leaves the store. The <b>TOTP secret is encrypted, not hashed</b>,
 * because verification needs the shared secret back: {@link #enableTotp} encrypts it and {@link #find} returns it
 * decrypted. The application holds neither key nor cipher; it only names this contract.
 */
public interface TwoFactorRepository {

    /** The player's stored registration, or empty when they hold no factor. */
    Optional<TwoFactorRegistration> find(UUID playerId);

    /** Encrypt and persist {@code secret} as the player's TOTP factor, stamping the enrolment instant if new. */
    void enableTotp(UUID playerId, TwoFactorSecret secret);

    /** Hash and persist {@code plaintextPin} as the player's PIN factor, stamping the enrolment instant if new. */
    void setPin(UUID playerId, String plaintextPin);

    /** Re-hash {@code candidate} and compare in constant time against the stored PIN; false when no PIN is set. */
    boolean verifyPin(UUID playerId, String candidate);

    /**
     * Drop only the player's TOTP secret, leaving any PIN they hold intact. The two factors are independent, so
     * removing one must never touch the other; a player left holding no factor at all is no longer enrolled and
     * {@link #find} reports them as such.
     */
    void clearTotp(UUID playerId);

    /** Drop only the player's PIN hash, leaving any TOTP secret they hold intact. The mirror of {@link #clearTotp}. */
    void clearPin(UUID playerId);

    /**
     * Remember {@code step} as the last authenticator time-step accepted for this player, so the code that covered
     * it cannot be presented a second time. Only ever moves forward: a step at or below the stored one is ignored.
     */
    void recordTotpStep(UUID playerId, long step);

    /** Remove the player's registration entirely (both factors), leaving no row behind. */
    void delete(UUID playerId);
}

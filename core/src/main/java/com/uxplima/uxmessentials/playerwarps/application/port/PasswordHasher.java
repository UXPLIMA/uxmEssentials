package com.uxplima.uxmessentials.playerwarps.application.port;

import com.uxplima.uxmessentials.playerwarps.domain.PasswordHash;

/**
 * Outbound port the player-warps context uses to protect a warp password. The application never touches a
 * crypto primitive directly; it asks this port to turn a plaintext into a stored {@link PasswordHash} and, on a
 * later teleport attempt, to check a candidate against that stored digest.
 *
 * <p>Implementations must be:
 *
 * <ul>
 *   <li><b>salted</b>. A fresh random salt per {@link #hash(String)} call, so two warps with the same password
 *       produce different digests and a precomputed table is useless;
 *   <li><b>one-way</b>. The stored {@link PasswordHash} yields no path back to the plaintext, which is why only
 *       the digest is ever persisted;
 *   <li><b>constant-time on verify</b>. {@link #verify(String, PasswordHash)} compares digests in a way whose
 *       timing does not depend on how many leading bytes match, so an attacker cannot recover the password byte
 *       by byte from response timing.
 * </ul>
 */
public interface PasswordHasher {

    /**
     * Derive a stored {@link PasswordHash} from a plaintext password using a fresh random salt.
     *
     * @param plaintext the raw password the warp owner chose; never {@code null}
     * @return the salted, one-way digest to persist
     */
    PasswordHash hash(String plaintext);

    /**
     * Check a candidate plaintext against a stored digest in constant time.
     *
     * @param plaintext the candidate password a visitor supplied; never {@code null}
     * @param against the previously stored digest to check it against; never {@code null}
     * @return {@code true} if the candidate reproduces the stored digest
     */
    boolean verify(String plaintext, PasswordHash against);
}

package com.uxplima.uxmessentials.playerwarps.application.port;

import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;

/**
 * Outbound port that owns a warp password's whole at-rest lifecycle: set it, clear it, or check a candidate
 * against it. The port takes a plaintext, never a {@link com.uxplima.uxmessentials.playerwarps.domain.PasswordHash
 * PasswordHash}, the application and the domain never hold a digest, so the hashing lives behind this one seam and
 * exactly one place knows how a password is stored. Kept separate from {@code PlayerWarpRepository} on purpose: the
 * repository never touches the password columns, so a routine warp save (a visibility flip, a rename) can never wipe
 * a set password.
 *
 * <p>This store answers match-or-not only. Attempt <b>rate-limiting is not here</b>. The ordered access gate
 * (P4-T3) throttles password attempts through the {@code Cooldowns} port and calls {@link #matches} for the
 * PASSWORD access step.
 */
public interface PlayerWarpPasswordStore {

    /** Hash {@code plaintext} and store it as {@code warp}'s password, overwriting any password already set. */
    void set(PlayerWarpId warp, String plaintext);

    /** Remove {@code warp}'s password: the three password columns become {@code NULL}. A no-op when none is set. */
    void clear(PlayerWarpId warp);

    /**
     * Check a candidate password against {@code warp}'s stored one.
     *
     * @return {@code true} when {@code warp} has a password and {@code plaintext} verifies against it;
     *     {@code false} when the warp has no password set (or does not exist)
     */
    boolean matches(PlayerWarpId warp, String plaintext);
}

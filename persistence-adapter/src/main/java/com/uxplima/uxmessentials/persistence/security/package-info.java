/**
 * The security context's outbound persistence adapter: the jOOQ {@code TwoFactorRepository} over the
 * {@code security_2fa} table, plus the two crypto primitives that guard the two factors at rest, the PBKDF2 PIN
 * hasher (reused from the player-warps password path) and the AES-GCM cipher that keeps the TOTP shared secret
 * recoverable-but-encrypted under a server key-file. No plaintext PIN and no plaintext secret is ever written or
 * logged.
 */
@NullMarked
package com.uxplima.uxmessentials.persistence.security;

import org.jspecify.annotations.NullMarked;

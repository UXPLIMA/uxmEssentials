/**
 * The security context's pure domain: the two-factor value objects and the RFC 6238 TOTP verification maths.
 *
 * <p>Nothing here touches Bukkit, Paper, Adventure, SLF4J or jOOQ. The only platform types are {@code
 * java.security} / {@code javax.crypto} primitives ({@link javax.crypto.Mac}, {@link java.security.SecureRandom}),
 * which the domain is allowed to use so the one-time-password maths and the shared-secret generation stay a pure,
 * deterministically testable function of a secret and a clock. The two secrets are handled with care: {@link
 * com.uxplima.uxmessentials.security.domain.TwoFactorSecret} redacts itself in {@code toString} so a shared secret
 * never leaks into a log line.
 */
@NullMarked
package com.uxplima.uxmessentials.security.domain;

import org.jspecify.annotations.NullMarked;

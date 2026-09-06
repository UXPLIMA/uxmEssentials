package com.uxplima.uxmessentials.shared.application.port;

import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * Outbound seam over WorldGuard's custom region flags, exposing whether a covering region vetoes an action at a
 * location. The playerwarps context reads this without naming WorldGuard: the adapter registers the flag at load and
 * evaluates its state through reflection, so a WorldGuard-less server carries none of its classes and this seam simply
 * reports "nothing denied".
 *
 * <p><b>{@code set-pwarp} is a veto, off by default.</b> The flag registers as a {@code StateFlag} defaulting to ALLOW,
 * so "not set" reads as allowed and only an explicit DENY on a covering region blocks. {@link #deniesFlag} therefore
 * answers {@code true} <em>only</em> when a region covering {@code where} sets the named flag to DENY, an operator
 * adds the flag as DENY to forbid warp creation in a region, and playerwarps treats
 * {@code deniesFlag("set-pwarp", loc) == true} as "creation forbidden here". A location no region touches, and every
 * location on a server without WorldGuard, answers {@code false}.
 *
 * <p>The seam is fail-open: a lookup it cannot evaluate reports {@code false} rather than blocking, because wrongly
 * refusing a legitimate warp is worse than missing a rare veto.
 */
public interface WorldGuardFlags {

    /** Whether WorldGuard is present and its flag query API is reachable; {@code false} degrades every query to allow. */
    boolean supported();

    /** Whether a region covering {@code where} sets the flag named {@code flagName} to DENY. */
    boolean deniesFlag(String flagName, Position where);
}

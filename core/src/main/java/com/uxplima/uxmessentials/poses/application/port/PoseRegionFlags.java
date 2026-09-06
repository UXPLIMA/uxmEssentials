package com.uxplima.uxmessentials.poses.application.port;

import com.uxplima.uxmessentials.poses.domain.PoseType;
import com.uxplima.uxmessentials.shared.domain.Position;

/**
 * Outbound seam over a region plugin's per-pose flags (WorldGuard's {@code sit} / {@code playersit} / {@code pose} /
 * {@code crawl} custom flags). The core {@link com.uxplima.uxmessentials.poses.application.ClaimAwareRegionGate gate}
 * consults it without naming WorldGuard: the adapter maps each {@link PoseType} to its flag and reads the region
 * state through reflection, so a WorldGuard-less server carries none of its classes and this seam simply reports
 * "nothing denied".
 *
 * <p>Only an <em>explicit</em> deny matters. A {@code StateFlag} defaults to ALLOW, so a location no region touches
 *, and every location on a server without WorldGuard, answers {@code false} here; the flag has to be set DENY on a
 * covering region for a pose to be turned away. The seam is fail-open: a lookup it cannot evaluate reports
 * {@code false} rather than blocking a harmless sit.
 */
public interface PoseRegionFlags {

    /** Whether a region flag at {@code where} explicitly forbids holding {@code type} there. */
    boolean deniesPose(Position where, PoseType type);
}

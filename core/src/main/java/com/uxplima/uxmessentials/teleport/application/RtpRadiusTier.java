package com.uxplima.uxmessentials.teleport.application;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaFamily;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.teleport.domain.SafeSearchArea;

/**
 * Resolves a player's random-teleport search radius from the numbered {@code uxmessentials.rtp.radius.<n>} tier
 * through the shared {@link Permissions} quota reducer, using the same "highest wins" ({@link
 * com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaReduction#MAX MAX}) direction every quota
 * family uses. The resolved value becomes the effective outer radius of a per-player live search area; a player
 * with no matching tier node falls back to the area's own configured maximum (the {@code rtp.max-radius} default),
 * and the world border still clamps the served location on top ({@link SafeSearchArea#maxRadius()}). The reduction
 * lives entirely in the one shared reducer, so RTP radii combine across permission groups exactly like home limits.
 *
 * <p>This applies to the per-player live search. The {@code /rtp biome} path, which builds a fresh area per
 * request. The plain {@code /rtp} and {@code /rtp <world>} paths serve from the shared, world-wide pre-warmed pool
 * (O(1), not per-player), so they carry no per-player radius clamp by construction.
 */
public final class RtpRadiusTier {

    /** The quota family for the radius tier: {@code uxmessentials.rtp.radius.<n>}, reduced by the maximum. */
    public static final QuotaFamily FAMILY = QuotaFamily.quota("uxmessentials.rtp.radius");

    private final Permissions permissions;

    public RtpRadiusTier(Permissions permissions) {
        this.permissions = Objects.requireNonNull(permissions, "permissions");
    }

    /**
     * Bound {@code base}'s configured outer radius to {@code who}'s resolved {@code rtp.radius} tier. The tier's
     * config default is {@code base}'s own configured maximum, so a player who holds no tier node searches the full
     * configured radius; a player who holds a higher tier searches further, still capped by the world border on serve.
     */
    public SafeSearchArea clamp(PlayerRef who, SafeSearchArea base) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(base, "base");
        long configDefault = Math.round(base.configuredMaxRadius());
        long resolved =
                permissions.resolveQuota(who, FAMILY, null, configDefault).orElse(configDefault);
        return base.withConfiguredMaxRadius((double) resolved);
    }
}

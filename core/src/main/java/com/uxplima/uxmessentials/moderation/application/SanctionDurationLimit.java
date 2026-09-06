package com.uxplima.uxmessentials.moderation.application;

import java.time.Duration;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaFamily;
import com.uxplima.uxmessentials.shared.application.port.Permissions.QuotaResult;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Caps how long a sanction an actor issues may last, by the actor's permission tier. The cap is the highest
 * {@code uxmessentials.moderation.<kind>.maxduration.<seconds>} node the actor holds (more is better, so the
 * {@code MAX}-direction reducer of the shared {@link Permissions} quota family); the {@code -1} sentinel node
 * means "unlimited". An actor holding no such node is unlimited by default, so behaviour is unchanged until an
 * operator sets caps.
 *
 * <p>{@link #cap(PlayerRef, String, Duration)} reduces a requested span to the tier's ceiling: a request
 * within the cap passes through unchanged, a request over the cap (including a permanent ban, modelled as the
 * far-future {@link Ban#PERMANENT_SPAN}) comes back clamped to the cap. The use case compares the returned
 * span against what it requested to decide whether to tell the actor the duration was capped. A {@code kind}
 * is the lowercase family segment, {@code "ban"} or {@code "mute"}.
 */
public final class SanctionDurationLimit {

    private static final String NODE_PREFIX = "uxmessentials.moderation.";
    private static final String NODE_SUFFIX = ".maxduration";

    /**
     * The "no node held" fallback handed to the reducer as its config default. It must be negative (so the
     * {@code capSeconds < 0} branch below reads it as unlimited) but <em>not</em> the {@code -1} unlimited
     * sentinel: the MAX reducer short-circuits to unlimited the instant it accepts {@code -1}, and the config
     * default is seeded before any held node is folded in. Seeding {@code -1} would latch unlimited and a real
     * {@code maxduration.<seconds>} node could never cap anything. {@code Long.MIN_VALUE} loses every {@code MAX}
     * fold to a real node, so a held cap wins, while a player with no node still resolves to this negative
     * fallback and is treated as unlimited.
     */
    private static final long NO_NODE_FALLBACK = Long.MIN_VALUE;

    private final Permissions permissions;

    public SanctionDurationLimit(Permissions permissions) {
        this.permissions = Objects.requireNonNull(permissions, "permissions");
    }

    /**
     * The effective span for {@code actor} issuing a {@code kind} sanction of {@code requested}: {@code
     * requested} itself when it is within the actor's tier cap (or the actor is unlimited), the cap otherwise.
     */
    public Duration cap(PlayerRef actor, String kind, Duration requested) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(requested, "requested");
        QuotaFamily family = QuotaFamily.quota(NODE_PREFIX + kind + NODE_SUFFIX);
        QuotaResult resolved = permissions.resolveQuota(actor, family, null, NO_NODE_FALLBACK);
        if (resolved.isUnlimited()) {
            return requested;
        }
        long capSeconds = resolved.orElse(NO_NODE_FALLBACK);
        if (capSeconds < 0) {
            // No node held (the negative fallback) or an explicit negative tier: treat as unlimited, no cap.
            return requested;
        }
        Duration cap = Duration.ofSeconds(capSeconds);
        return requested.compareTo(cap) > 0 ? cap : requested;
    }
}

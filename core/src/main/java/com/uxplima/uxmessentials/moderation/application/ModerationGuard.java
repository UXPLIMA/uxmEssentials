package com.uxplima.uxmessentials.moderation.application;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * The target-side immunity check every sanction consults: a target holding the exempt node
 * ({@code uxmessentials.moderation.exempt}) cannot be muted, jailed, tempbanned, kicked, warned, IP-banned or
 * frozen by lower staff (docs/permissions.md). Centralised here so every use case applies the exact same
 * gate and the node name lives in one place.
 *
 * <p>The check is intentionally a plain node lookup through the kernel {@link Permissions} port: there is no
 * staff-rank ladder in scope (warn-escalation ladders are explicitly out of charter), so "lower staff" means
 * "anyone; the exempt holder is simply immune". A future rank model would refine this one method.
 */
public final class ModerationGuard {

    /** The target-side immunity node. */
    public static final String EXEMPT_NODE = "uxmessentials.moderation.exempt";

    private final Permissions permissions;

    public ModerationGuard(Permissions permissions) {
        this.permissions = Objects.requireNonNull(permissions, "permissions");
    }

    /** True when {@code target} is immune to sanctions (holds the exempt node). */
    public boolean isExempt(PlayerRef target) {
        Objects.requireNonNull(target, "target");
        return permissions.has(target, EXEMPT_NODE);
    }
}

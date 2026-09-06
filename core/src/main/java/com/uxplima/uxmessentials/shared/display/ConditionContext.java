package com.uxplima.uxmessentials.shared.display;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Everything a {@link DisplayCondition} needs to evaluate, gathered at the boundary so the condition model
 * itself stays a pure function: mirroring vote's {@code RewardContext} seam. The condition decides
 * <em>what</em> to ask; this context supplies the runtime answers the adapter knows and the domain does not:
 * the viewer's permission check, the world they are in, their gamemode, and a placeholder bridge.
 *
 * <p>{@code hasPermission.test(node)} is the viewer's permission check. {@code worldName} and {@code gamemode}
 * are matched case-insensitively by the relevant condition kinds. {@code resolvePlaceholders} expands
 * {@code %papi%}-style tokens in a comparison's operands: the bukkit adapter backs it with the real
 * PlaceholderAPI bridge, and a test backs it with a map-backed fake. A placeholder with no expansion is left
 * to the bridge's own convention (typically returned unchanged), which {@code COMPARE} then treats as a plain
 * string.
 *
 * @param hasPermission tests whether the viewer holds a permission node
 * @param worldName the viewer's current world name
 * @param gamemode the viewer's current gamemode name
 * @param resolvePlaceholders expands {@code %papi%}-style tokens in a string
 */
public record ConditionContext(
        Predicate<String> hasPermission,
        String worldName,
        String gamemode,
        Function<String, String> resolvePlaceholders) {

    public ConditionContext {
        Objects.requireNonNull(hasPermission, "hasPermission");
        Objects.requireNonNull(worldName, "worldName");
        Objects.requireNonNull(gamemode, "gamemode");
        Objects.requireNonNull(resolvePlaceholders, "resolvePlaceholders");
    }
}

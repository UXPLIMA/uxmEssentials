package com.uxplima.uxmessentials.communication.application;

import java.util.Objects;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.communication.domain.JoinGroupPolicies;
import com.uxplima.uxmessentials.communication.domain.MessagePolicy;
import com.uxplima.uxmessentials.communication.domain.PlaceholderBindings;
import org.jspecify.annotations.Nullable;

/**
 * Resolves the join message for a connecting player through the shared {@link ResolveConnectionMessage} engine.
 * Two operator inputs steer the selection, both read fresh each call from their supplier so a
 * {@code /uxmess reload communication} takes effect on the next join with no re-wiring:
 *
 * <ul>
 *   <li>the per-group {@link JoinGroupPolicies}. The joiner's primary permission group picks a {@link MessagePolicy}
 *       (its own override when authored, else the default join policy), so a rank can be greeted differently;</li>
 *   <li>the optional first-join welcome {@link MessagePolicy}, broadcast instead of the ordinary join line the
 *       first time the server has ever seen a player, and only when the operator authored a welcome template.</li>
 * </ul>
 *
 * <p>First-join is a plain boolean input to selection here (the adapter derives it from
 * {@code Player#hasPlayedBefore()}); the group is a nullable string the adapter reads from the permission plugin.
 * The standard join tokens, {@code {player}} (display name), {@code {count}} (online count after join),
 * {@code {world}}, are bound by the adapter from the live join event and passed in; this use case never touches a
 * Bukkit type, and the returned {@link ResolvedMessage} carries operator content for the adapter to render.
 */
public final class ResolveJoinMessage {

    static final String CHANNEL = "join";
    static final String FIRST_JOIN_CHANNEL = "first-join";

    private final ResolveConnectionMessage engine;
    private final Supplier<JoinGroupPolicies> groupPolicies;
    private final Supplier<MessagePolicy> firstJoinPolicy;

    public ResolveJoinMessage(
            ResolveConnectionMessage engine,
            Supplier<JoinGroupPolicies> groupPolicies,
            Supplier<MessagePolicy> firstJoinPolicy) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.groupPolicies = Objects.requireNonNull(groupPolicies, "groupPolicies");
        this.firstJoinPolicy = Objects.requireNonNull(firstJoinPolicy, "firstJoinPolicy");
    }

    /**
     * The resolved join message for a player of {@code primaryGroup} whose join is the server's first sighting of
     * them when {@code firstJoin} is true. A first join with an authored welcome template renders that welcome
     * instead of the ordinary join line; every other join (and a first join with no welcome authored) falls through
     * to the group's policy.
     */
    public ResolvedMessage resolve(boolean firstJoin, @Nullable String primaryGroup, PlaceholderBindings bindings) {
        Objects.requireNonNull(bindings, "bindings");
        if (firstJoin) {
            MessagePolicy welcome = firstJoinPolicy.get();
            if (welcome.rendersTemplate()) {
                return engine.resolve(FIRST_JOIN_CHANNEL, welcome, bindings);
            }
        }
        return engine.resolve(CHANNEL, groupPolicies.get().policyFor(primaryGroup), bindings);
    }
}

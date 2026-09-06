package com.uxplima.uxmessentials.communication.application;

import java.util.Objects;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.communication.domain.DeathCause;
import com.uxplima.uxmessentials.communication.domain.DeathCausePolicies;
import com.uxplima.uxmessentials.communication.domain.MessagePolicy;
import com.uxplima.uxmessentials.communication.domain.PlaceholderBindings;

/**
 * Resolves the death message for a player by applying the death channel's per-cause {@link DeathCausePolicies} and
 * the event's placeholders through the shared {@link ResolveConnectionMessage} engine. The table is read fresh each
 * call from the supplied holder, so a reload that swaps the death policies takes effect on the next death. The
 * {@link DeathCause} the adapter classified the death as picks the {@link MessagePolicy}, a cause with its own
 * template renders it, every other cause falls through to the default policy.
 *
 * <p>The standard death tokens. {@code {player}} (the victim's display name), {@code {killer}} (the killer's name
 * when a player dealt the blow), {@code {killer_weapon}} (the weapon that killer held), {@code {cause}}, are bound
 * by the adapter from the live death event, which already holds the vanilla death cause; this use case never
 * touches a Bukkit type. The returned {@link ResolvedMessage} carries operator content for the adapter to render
 * through MiniMessage.
 */
public final class ResolveDeathMessage {

    static final String CHANNEL = "death";

    private final ResolveConnectionMessage engine;
    private final Supplier<DeathCausePolicies> policies;

    public ResolveDeathMessage(ResolveConnectionMessage engine, Supplier<DeathCausePolicies> policies) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.policies = Objects.requireNonNull(policies, "policies");
    }

    /** The resolved death message for a death of {@code cause} described by {@code bindings}. */
    public ResolvedMessage resolve(DeathCause cause, PlaceholderBindings bindings) {
        Objects.requireNonNull(cause, "cause");
        Objects.requireNonNull(bindings, "bindings");
        return engine.resolve(CHANNEL, policies.get().policyFor(cause), bindings);
    }
}

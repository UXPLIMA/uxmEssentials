package com.uxplima.uxmessentials.communication.application;

import java.util.Objects;
import java.util.function.Supplier;

import com.uxplima.uxmessentials.communication.domain.MessagePolicy;
import com.uxplima.uxmessentials.communication.domain.PlaceholderBindings;

/**
 * Resolves the quit message for a disconnecting player by applying the quit channel's {@link MessagePolicy} and
 * the event's placeholders through the shared {@link ResolveConnectionMessage} engine. The policy is read fresh
 * each call from the supplied holder, so a reload that swaps the quit policy takes effect on the next quit.
 *
 * <p>The standard quit tokens ({@code {player}} (display name), {@code {count}} (online count after quit)) are
 * bound by the adapter from the live quit event. This use case never touches a Bukkit type; the returned
 * {@link ResolvedMessage} carries operator content for the adapter to render through MiniMessage.
 */
public final class ResolveQuitMessage {

    static final String CHANNEL = "quit";

    private final ResolveConnectionMessage engine;
    private final Supplier<MessagePolicy> policy;

    public ResolveQuitMessage(ResolveConnectionMessage engine, Supplier<MessagePolicy> policy) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.policy = Objects.requireNonNull(policy, "policy");
    }

    /** The resolved quit message for the event described by {@code bindings}. */
    public ResolvedMessage resolve(PlaceholderBindings bindings) {
        Objects.requireNonNull(bindings, "bindings");
        return engine.resolve(CHANNEL, policy.get(), bindings);
    }
}

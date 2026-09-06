package com.uxplima.uxmessentials.communication.application;

import java.util.Objects;

import com.uxplima.uxmessentials.communication.application.port.RandomSource;
import com.uxplima.uxmessentials.communication.application.port.SequenceCounter;
import com.uxplima.uxmessentials.communication.domain.MessagePolicy;
import com.uxplima.uxmessentials.communication.domain.Ordering;
import com.uxplima.uxmessentials.communication.domain.PlaceholderBindings;

/**
 * The shared engine the {@code ResolveJoinMessage} / {@code ResolveQuitMessage} / {@code ResolveDeathMessage} use
 * cases delegate to: given a channel's {@link MessagePolicy} and the {@link PlaceholderBindings} for the event, it
 * picks a template index, selects the raw template, substitutes placeholders, and returns the {@link ResolvedMessage}
 * the adapter renders. The three per-channel use cases differ only in which policy they hold and which channel name
 * they key the sequence counter by, so the resolution rule lives here once.
 *
 * <p>Index selection honours the policy's {@link Ordering}: a sequential channel reads the next monotonically
 * advancing index from the {@link SequenceCounter} keyed by the channel name, a random channel draws a bounded
 * index from the {@link RandomSource}. The policy then wraps the index into range. This engine touches no Bukkit
 * type and renders no MiniMessage: it returns the substituted raw string for the adapter to parse.
 */
public final class ResolveConnectionMessage {

    private final SequenceCounter counters;
    private final RandomSource random;

    public ResolveConnectionMessage(SequenceCounter counters, RandomSource random) {
        this.counters = Objects.requireNonNull(counters, "counters");
        this.random = Objects.requireNonNull(random, "random");
    }

    /**
     * Resolve {@code channel}'s {@code policy} against {@code bindings}. A {@code CUSTOM} policy selects and
     * substitutes a template; a {@code DISABLE} policy suppresses and clears vanilla; a {@code DEFAULT} policy
     * keeps the vanilla line.
     */
    public ResolvedMessage resolve(String channel, MessagePolicy policy, PlaceholderBindings bindings) {
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(bindings, "bindings");
        if (policy.suppressesVanilla() && !policy.rendersTemplate()) {
            return ResolvedMessage.suppress();
        }
        if (!policy.rendersTemplate()) {
            return ResolvedMessage.keepVanilla();
        }
        return policy.select(selectionIndex(channel, policy))
                .map(bindings::apply)
                .map(ResolvedMessage::render)
                .orElseGet(ResolvedMessage::keepVanilla);
    }

    private int selectionIndex(String channel, MessagePolicy policy) {
        return policy.ordering() == Ordering.SEQUENTIAL
                ? counters.next(channel)
                : random.nextBounded(Math.max(1, policy.templateCount()));
    }
}

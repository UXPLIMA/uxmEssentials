package com.uxplima.uxmessentials.servertweaks.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * What a Velocity proxy running SignedVelocity has decided should happen to one of a player's signed chat messages or
 * commands, carried back to this backend so the backend applies the same outcome to its own event. Modelled as a
 * sealed set of three outcomes so a caller must handle each explicitly:
 *
 * <ul>
 *   <li>{@link Allow}: deliver the message/command unchanged.
 *   <li>{@link Cancel}: drop it; the proxy has already vetoed it.
 *   <li>{@link Modify}: replace its content with the proxy-supplied text.
 * </ul>
 *
 * <p>Keeping the outcome consistent on both sides of the proxy hop is the whole point of SignedVelocity: a signed
 * message vetoed or rewritten at the proxy must be vetoed or rewritten identically at the backend, or the client's
 * signed-chat chain desynchronises. Pure Java, the Bukkit event mutation that acts on the directive lives in the
 * adapter.
 */
public sealed interface SignedChatDirective
        permits SignedChatDirective.Allow, SignedChatDirective.Cancel, SignedChatDirective.Modify {

    /** Deliver the message or command unchanged. */
    static SignedChatDirective allow() {
        return Allow.INSTANCE;
    }

    /** Drop the message or command entirely. */
    static SignedChatDirective cancel() {
        return Cancel.INSTANCE;
    }

    /** Replace the message or command content with {@code message}. */
    static SignedChatDirective modify(String message) {
        return new Modify(message);
    }

    /** Whether this directive vetoes the message or command. */
    default boolean cancelled() {
        return this instanceof Cancel;
    }

    /** The replacement content when this is a {@link Modify}, otherwise empty. */
    default Optional<String> modifiedMessage() {
        return this instanceof Modify modify ? Optional.of(modify.message()) : Optional.empty();
    }

    /** Deliver unchanged. */
    record Allow() implements SignedChatDirective {
        private static final Allow INSTANCE = new Allow();
    }

    /** Veto: drop the message or command. */
    record Cancel() implements SignedChatDirective {
        private static final Cancel INSTANCE = new Cancel();
    }

    /**
     * Rewrite: replace the content with the proxy-supplied text.
     *
     * @param message the replacement content (never null)
     */
    record Modify(String message) implements SignedChatDirective {
        public Modify {
            Objects.requireNonNull(message, "message");
        }
    }
}

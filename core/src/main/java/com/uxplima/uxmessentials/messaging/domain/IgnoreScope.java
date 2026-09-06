package com.uxplima.uxmessentials.messaging.domain;

import org.jspecify.annotations.Nullable;

/**
 * What kind of traffic an ignore entry suppresses. {@code /ignore <player>} creates a {@link #ALL} entry
 * the default and only verb-reachable scope, which blocks both private messages and the would-be chat the
 * charter does not ship; the finer scopes exist so the persistent {@code scope} column never needs a
 * migration to model a narrower ignore a future surface might add (a mail-only mute, say).
 *
 * <p>The stable {@code name()} is what the {@code ignores.scope} column stores, so the enum constant is the
 * source of truth for the persisted value and the two never drift. A scope read back from a row the running
 * code no longer recognises is treated as {@link #ALL} by {@link #fromStored(String)} so an ignore is never
 * silently dropped on a downgrade.
 */
public enum IgnoreScope {

    /** Block every channel from the ignored player: private messages and mail alike. The verb default. */
    ALL,

    /** Block only private messages ({@code /msg}, {@code /reply}); mail still reaches the owner. */
    MESSAGES,

    /** Block only mail ({@code /mail send}); private messages still reach the owner. */
    MAIL;

    /** Resolve a stored scope name back to a constant, defaulting to {@link #ALL} for an unknown value. */
    public static IgnoreScope fromStored(@Nullable String stored) {
        if (stored == null) {
            return ALL;
        }
        for (IgnoreScope scope : values()) {
            if (scope.name().equals(stored)) {
                return scope;
            }
        }
        return ALL;
    }

    /** True when this scope suppresses a private message ({@code /msg} / {@code /reply}). */
    public boolean blocksMessages() {
        return this == ALL || this == MESSAGES;
    }

    /** True when this scope suppresses mail ({@code /mail send}). */
    public boolean blocksMail() {
        return this == ALL || this == MAIL;
    }
}

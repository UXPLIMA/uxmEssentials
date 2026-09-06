package com.uxplima.uxmessentials.vanish.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The vanish context's user-visible message keys. Each constant maps 1:1 to a kebab-case catalog key in
 * {@code messages_<lang>.conf} ({@code VANISH_ON} ↔ {@code vanish.on}); the constant is the compile-time handle, the
 * catalog holds the text. There are no inline player-facing literals in the context. Every message resolves through
 * one of these. Phase 1 owns the {@code /vanish} on/off confirmations; Phase 2 adds the {@code /vanish <player>} actor
 * feedback and the {@code /vanish list} output; Phase 4 adds the persistent action-bar indicator. The fake join/quit
 * lines are operator MiniMessage config content (see {@code VanishConfig}), not catalog keys.
 */
public enum VanishMessageKey implements MessageKey {

    // /vanish: the on/off confirmation shown to the toggling player.
    VANISH_ON("vanish.on"),
    VANISH_OFF("vanish.off"),

    // /vanish <player>: the confirmation shown to the actor who vanished/revealed another player.
    VANISH_OTHER_ON("vanish.other-on"),
    VANISH_OTHER_OFF("vanish.other-off"),

    // /vanish list: the roster of hidden players the caller may see, and the nobody-hidden line.
    VANISH_LIST("vanish.list"),
    VANISH_LIST_EMPTY("vanish.list-empty"),

    // /vanish pickup: the confirmation shown when a player flips whether they pick up items while vanished.
    VANISH_PICKUP_ON("vanish.pickup-on"),
    VANISH_PICKUP_OFF("vanish.pickup-off"),

    // The persistent action-bar indicator refreshed to a vanished player while they are hidden.
    VANISH_ACTIONBAR("vanish.actionbar");

    private final String key;

    VanishMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}

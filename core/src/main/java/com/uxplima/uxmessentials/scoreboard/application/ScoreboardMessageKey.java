package com.uxplima.uxmessentials.scoreboard.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The scoreboard context's user-visible message keys. Each constant maps 1:1 to a kebab-case catalog key in
 * {@code messages_<lang>.conf} ({@code SCOREBOARD_SHOWN} ↔ {@code scoreboard.shown}); the constant is the
 * compile-time handle, the catalog holds the text. These are the <em>only</em> localised strings in the context
 * the {@code /scoreboard} toggle confirmations and the players-only rejection. The sidebar title and sidebar lines
 * are operator-authored MiniMessage content under {@code modules/scoreboard/config.conf}, rendered as written and
 * never parity-checked.
 *
 * <p>Per the i18n contract, a disabled module still ships its keys so the catalog stays whole and the locale-parity
 * guard sees the full {@code en} key set.
 */
public enum ScoreboardMessageKey implements MessageKey {
    SCOREBOARD_SHOWN("scoreboard.shown"),
    SCOREBOARD_HIDDEN("scoreboard.hidden"),
    SCOREBOARD_PLAYERS_ONLY("scoreboard.players-only"),

    // /scoreboard gui. The per-player display settings panel
    GUI_TITLE("scoreboard.gui.title"),
    GUI_VALUE_LORE("scoreboard.gui.value-lore"),
    GUI_BACK("scoreboard.gui.back"),
    GUI_VISIBILITY("scoreboard.gui.visibility"),
    GUI_VALUE_SHOWN("scoreboard.gui.value-shown"),
    GUI_VALUE_HIDDEN("scoreboard.gui.value-hidden");

    private final String key;

    ScoreboardMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}

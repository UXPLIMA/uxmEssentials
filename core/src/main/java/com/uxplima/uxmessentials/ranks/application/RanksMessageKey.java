package com.uxplima.uxmessentials.ranks.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The ranks context's user-visible message keys. Each constant maps 1:1 to a kebab-case catalog key in
 * {@code messages_<lang>.conf} ({@code RANKS_RANKUP_SUCCESS} ↔ {@code ranks.rankup-success}); the constant is
 * the compile-time handle, the catalog holds the text. There are no inline player-facing literals in the
 * context: every message resolves through one of these.
 *
 * <p>Per the i18n contract a disabled module still ships its keys so the catalog stays whole and the
 * locale-parity guard sees the full {@code en} key set. This is the Phase-1 seed: the current-rank read line
 * and the rankup / prestige feedback the behaviour phases (P2, P3) resolve through. Later phases add their own
 * keys here as their verbs land. Ladder, requirement and action text is operator content in {@code ranks.conf},
 * not a plugin string, so it never appears here.
 */
public enum RanksMessageKey implements MessageKey {

    // Current standing: the line shown when a player reads their own rank and prestige.
    RANKS_CURRENT("ranks.current"),

    // Rankup outcomes: success, and the three refusals: already at the top, requirements unmet, cost unaffordable.
    RANKS_RANKUP_SUCCESS("ranks.rankup-success"),
    RANKS_RANKUP_MAX("ranks.rankup-max"),
    RANKS_RANKUP_REQUIREMENTS("ranks.rankup-requirements"),
    RANKS_RANKUP_COST("ranks.rankup-cost"),

    // Admin setrank outcomes, the confirmation of a direct set, and the refusal when the named rank is not a rung.
    RANKS_SETRANK_SUCCESS("ranks.setrank-success"),
    RANKS_SETRANK_UNKNOWN_RANK("ranks.setrank-unknown-rank"),

    // Prestige outcomes. Success, and the refusals: not yet at the top rank, at the prestige cap, requirements
    // unmet, or cost unaffordable.
    RANKS_PRESTIGE_SUCCESS("ranks.prestige-success"),
    RANKS_PRESTIGE_NOT_MAX_RANK("ranks.prestige-not-max-rank"),
    RANKS_PRESTIGE_MAX("ranks.prestige-max"),
    RANKS_PRESTIGE_REQUIREMENTS("ranks.prestige-requirements"),
    RANKS_PRESTIGE_COST("ranks.prestige-cost"),

    // The /ranks ladder panel. The window title, the current-standing and next-rank display items, the rank-up
    // button, the close button, and the marker shown for the next rank when the player is already at the top.
    RANKS_GUI_TITLE("ranks.gui-title"),
    RANKS_GUI_CURRENT_NAME("ranks.gui-current-name"),
    RANKS_GUI_CURRENT_LORE("ranks.gui-current-lore"),
    RANKS_GUI_NEXT_NAME("ranks.gui-next-name"),
    RANKS_GUI_NEXT_LORE("ranks.gui-next-lore"),
    RANKS_GUI_RANKUP_NAME("ranks.gui-rankup-name"),
    RANKS_GUI_RANKUP_LORE("ranks.gui-rankup-lore"),
    RANKS_GUI_CLOSE("ranks.gui-close"),
    RANKS_GUI_MAX("ranks.gui-max");

    private final String key;

    RanksMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}

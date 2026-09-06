package com.uxplima.uxmessentials.survival.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The survival context's user-visible message keys. Each constant maps 1:1 to a kebab-case catalog key in
 * {@code messages_<lang>.conf} ({@code SURVIVAL_TREEFELLER_ENABLED} ↔ {@code survival.treefeller-enabled}); the
 * constant is the compile-time handle, the catalog holds the text. There are no inline player-facing literals in the
 * context: the {@code /treefeller} and {@code /veinminer} toggles resolve their on/off notices through these.
 *
 * <p>Per the i18n contract a disabled module still ships its keys so the catalog stays whole and the locale-parity
 * guard sees the full {@code en} key set. Later phases add their own keys here as their mechanics land.
 */
public enum SurvivalMessageKey implements MessageKey {

    // /treefeller: the per-player toggle's on and off notices.
    SURVIVAL_TREEFELLER_ENABLED("survival.treefeller-enabled"),
    SURVIVAL_TREEFELLER_DISABLED("survival.treefeller-disabled"),

    // /veinminer: the per-player toggle's on and off notices.
    SURVIVAL_VEINMINER_ENABLED("survival.veinminer-enabled"),
    SURVIVAL_VEINMINER_DISABLED("survival.veinminer-disabled"),

    // /farmprotect: the per-player toggle's on and off notices.
    SURVIVAL_FARMPROTECT_ENABLED("survival.farmprotect-enabled"),
    SURVIVAL_FARMPROTECT_DISABLED("survival.farmprotect-disabled"),

    // /autopickup: the per-player toggle's on and off notices.
    SURVIVAL_AUTOPICKUP_ENABLED("survival.autopickup-enabled"),
    SURVIVAL_AUTOPICKUP_DISABLED("survival.autopickup-disabled"),

    // /autosmelt: the per-player toggle's on and off notices.
    SURVIVAL_AUTOSMELT_ENABLED("survival.autosmelt-enabled"),
    SURVIVAL_AUTOSMELT_DISABLED("survival.autosmelt-disabled"),

    // /autosell: the per-player toggle's on and off notices.
    SURVIVAL_AUTOSELL_ENABLED("survival.autosell-enabled"),
    SURVIVAL_AUTOSELL_DISABLED("survival.autosell-disabled"),

    // The auto-sell receipt: what a mined drop fetched. The line is built from one entry per sold material
    // ({amount} of that material, {item} its client-localized name), joined by the separator, and slotted into the
    // chat or action-bar form as {items} beside the {amount} of money the sale credited.
    SURVIVAL_AUTOSELL_SOLD("survival.autosell-sold"),
    SURVIVAL_AUTOSELL_SOLD_BAR("survival.autosell-sold-bar"),
    SURVIVAL_AUTOSELL_SOLD_ENTRY("survival.autosell-sold-entry"),
    SURVIVAL_AUTOSELL_SOLD_SEPARATOR("survival.autosell-sold-separator"),

    // /autotool: the per-player toggle's on and off notices.
    SURVIVAL_AUTOTOOL_ENABLED("survival.autotool-enabled"),
    SURVIVAL_AUTOTOOL_DISABLED("survival.autotool-disabled"),

    // /survival: the personal settings panel. The title, back button, the shared value-lore line (carrying the
    // {value} placeholder) and its on/off words, then one label per toggleable mechanic drawn as a row.
    SURVIVAL_GUI_TITLE("survival.gui.title"),
    SURVIVAL_GUI_BACK("survival.gui.back"),
    SURVIVAL_GUI_VALUE_LORE("survival.gui.value-lore"),
    SURVIVAL_GUI_VALUE_ON("survival.gui.value-on"),
    SURVIVAL_GUI_VALUE_OFF("survival.gui.value-off"),
    SURVIVAL_GUI_TREEFELLER("survival.gui.treefeller"),
    SURVIVAL_GUI_VEINMINER("survival.gui.veinminer"),
    SURVIVAL_GUI_FARMPROTECT("survival.gui.farmprotect"),
    SURVIVAL_GUI_AUTOPICKUP("survival.gui.autopickup"),
    SURVIVAL_GUI_AUTOSMELT("survival.gui.autosmelt"),
    SURVIVAL_GUI_AUTOSELL("survival.gui.autosell"),
    SURVIVAL_GUI_AUTOTOOL("survival.gui.autotool");

    private final String key;

    SurvivalMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}

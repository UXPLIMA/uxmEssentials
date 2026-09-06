package com.uxplima.uxmessentials.villagers.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The villagers context's user-visible message keys. Each constant maps 1:1 to a kebab-case catalog key in
 * {@code messages_<lang>.conf} ({@code VILLAGERS_TRADES_DISABLED} ↔ {@code villagers.trades-disabled}); the constant
 * is the compile-time handle, the catalog holds the text. There are no inline player-facing literals in the context
 * every message resolves through one of these.
 *
 * <p>Phase 1 seeded the one refusal a player sees when they right-click a villager whose trading is turned off
 * (globally or per-villager); Phase 2 adds the trade-manager surface, the "look at a villager" hint, the manager
 * GUI title, and the two button labels (the disable toggle, in its two states, and the per-row remove control) plus
 * the usage help. Phase 3 adds the protection toggle's two confirmations and its no-target hint, and the name the
 * villager-in-a-bucket item carries. Phase 4 adds the follow toggle's two confirmations and its no-target hint
 * (leashing is silent, like vanilla leashing). Later phases add their own keys here as their verbs land.
 */
public enum VillagersMessageKey implements MessageKey {

    // Refusal. The villager's trading is disabled (by the global switch or a per-villager flag), so the trade GUI
    // does not open.
    VILLAGERS_TRADES_DISABLED("villagers.trades-disabled"),

    // The bare /villager root: a usage line pointing at the manager/protect/follow verbs, and the line shown when the
    // module is on but every villager sub-feature is off so there is nothing to do.
    VILLAGERS_USAGE("villagers.usage"),
    VILLAGERS_NONE_ENABLED("villagers.none-enabled"),

    // Trade manager: /villager manager could not find a villager the player is looking at (or within reach).
    VILLAGERS_MANAGER_NO_TARGET("villagers.manager.no-target"),

    // Trade manager: the GUI window title (carries the villager's {name}).
    VILLAGERS_MANAGER_TITLE("villagers.manager.title"),

    // Trade manager: the disable-toggle button when the villager IS trading (click disables it).
    VILLAGERS_MANAGER_TRADING_ENABLED("villagers.manager.trading-enabled"),

    // Trade manager: the disable-toggle button when the villager is NOT trading (click re-enables it).
    VILLAGERS_MANAGER_TRADING_DISABLED("villagers.manager.trading-disabled"),

    // Trade manager, the per-recipe remove button label.
    VILLAGERS_MANAGER_REMOVE("villagers.manager.remove"),

    // Trade manager: the info/help item explaining how to edit, add and remove trades.
    VILLAGERS_MANAGER_HELP("villagers.manager.help"),

    // Protection: /villager protect could not find a villager the player is looking at (or within reach).
    VILLAGERS_PROTECT_NO_TARGET("villagers.protect.no-target"),

    // Protection: the villager the player looked at is now protected (the mark was turned on).
    VILLAGERS_PROTECT_ENABLED("villagers.protect.enabled"),

    // Protection: the villager the player looked at is no longer protected (the mark was turned off).
    VILLAGERS_PROTECT_DISABLED("villagers.protect.disabled"),

    // Villager-in-a-bucket, the display name of the item a picked-up villager becomes.
    VILLAGERS_BUCKET_NAME("villagers.bucket.name"),

    // Follow: /villager follow could not find a villager the player is looking at (or within reach).
    VILLAGERS_FOLLOW_NO_TARGET("villagers.follow.no-target"),

    // Follow: the villager the player looked at is now following them.
    VILLAGERS_FOLLOW_STARTED("villagers.follow.started"),

    // Follow: the villager the player looked at has stopped following them.
    VILLAGERS_FOLLOW_STOPPED("villagers.follow.stopped");

    private final String key;

    VillagersMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}

package com.uxplima.uxmessentials.presence.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The presence context's user-visible message keys. Each constant maps 1:1 to a kebab-case catalog key in
 * {@code messages_<lang>.conf} ({@code AFK_NOW} ↔ {@code afk.now}); the constant is the compile-time handle,
 * the catalog holds the text. There are no inline player-facing literals anywhere in the context, every
 * message resolves through one of these.
 *
 * <p>Per the i18n contract, a disabled module still ships its keys so the catalog stays whole and the
 * locale-parity guard sees the full {@code en} key set.
 */
public enum PresenceMessageKey implements MessageKey {

    // /afk. Manual and auto AFK transitions
    AFK_NOW("afk.now"),
    AFK_NOW_REASON("afk.now-reason"),
    AFK_RETURNED("afk.returned"),
    AFK_BROADCAST_AWAY("afk.broadcast-away"),
    AFK_BROADCAST_AWAY_REASON("afk.broadcast-away-reason"),
    AFK_BROADCAST_BACK("afk.broadcast-back"),

    // anti-AFK: shown once per AFK session when item pickups are blocked while away
    AFK_PICKUP_BLOCKED("afk.pickup-blocked"),

    // /list, the vanish-aware online roster
    LIST_PLAYERS("list.players"),

    // /list, the online-roster browse menu (bare /list when its gui flag is on)
    LIST_GUI_TITLE("list.gui.title"),
    LIST_GUI_PREV("list.gui.prev"),
    LIST_GUI_NEXT("list.gui.next"),
    LIST_GUI_ENTRY_NAME("list.gui.entry-name"),
    LIST_GUI_ENTRY_LORE("list.gui.entry-lore"),
    LIST_GUI_STATUS_ONLINE("list.gui.status-online"),
    LIST_GUI_EMPTY_TITLE("list.gui.empty-title"),

    // /realname. Resolve a visible player's display name to their account name
    REALNAME_RESULT("presence.realname-result"),
    REALNAME_NOT_FOUND("presence.realname-not-found"),

    // /nick, set or clear a display name (own, or another player's when gated)
    NICK_SET("presence.nick-set"),
    NICK_CLEARED("presence.nick-cleared"),
    NICK_SET_OTHER("presence.nick-set-other"),
    NICK_INVALID("presence.nick-invalid"),
    NICK_TARGET_UNKNOWN("presence.nick-target-unknown"),

    // /whois. Staff identity/status summary for one visible online player
    WHOIS_RESULT("presence.whois-result"),
    WHOIS_NOT_FOUND("presence.whois-not-found"),

    // /gc. Server health read-out (TPS, uptime, memory, loaded chunks and entities)
    GC_RESULT("presence.gc-result"),

    // /staff. The vanish-aware online staff roster (holders of uxmessentials.staff.member)
    STAFF_LIST("presence.staff-list"),
    STAFF_EMPTY("presence.staff-empty"),

    // /presencesettings. The per-player presence settings panel
    GUI_SETTINGS_TITLE("presence.gui.settings.title"),
    GUI_SETTINGS_VALUE_LORE("presence.gui.settings.value-lore"),
    GUI_SETTINGS_BACK("presence.gui.settings.back"),
    GUI_SETTINGS_AFK("presence.gui.settings.afk"),
    GUI_SETTINGS_VANISH("presence.gui.settings.vanish"),
    GUI_SETTINGS_VALUE_ON("presence.gui.settings.value-on"),
    GUI_SETTINGS_VALUE_OFF("presence.gui.settings.value-off");

    private final String key;

    PresenceMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}

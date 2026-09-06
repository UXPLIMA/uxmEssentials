package com.uxplima.uxmessentials.discordlink.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The discord-link context's user-visible message keys. Each constant maps 1:1 to a kebab-case catalog key in
 * {@code messages_<lang>.conf} ({@code DISCORD_LINK_CODE} ↔ {@code discordlink.code}); the constant is the
 * compile-time handle, the catalog holds the text. There are no inline player-facing literals in the context
 * every message resolves through one of these.
 *
 * <p>These render only the in-game side ({@code /discordlink}, {@code /discordunlink}). The text the bridge
 * replies with inside Discord is not a player-locale catalog key. It is sent on JDA's own thread to a Discord
 * user with no Minecraft locale, so the slash handler carries its own short literals, not these keys.
 *
 * <p>Per the i18n contract a disabled module still ships its keys so the catalog stays whole and the
 * locale-parity guard sees the full {@code en} key set.
 */
public enum DiscordlinkMessageKey implements MessageKey {

    // /discordlink. Issuing and reporting a code
    DISCORD_LINK_CODE("discordlink.code"),
    DISCORD_LINK_HOWTO("discordlink.howto"),
    DISCORD_LINK_ALREADY("discordlink.already"),

    // /discordlink status
    DISCORD_LINK_STATUS_LINKED("discordlink.status.linked"),
    DISCORD_LINK_STATUS_UNLINKED("discordlink.status.unlinked"),

    // /discordunlink
    DISCORD_LINK_UNLINKED("discordlink.unlinked"),
    DISCORD_LINK_NOT_LINKED("discordlink.not-linked"),

    // console rejection
    DISCORD_LINK_PLAYERS_ONLY("discordlink.players-only"),

    // the Discord bridge is not installed/connected, so a link code would have nothing to redeem it
    DISCORD_NOT_CONFIGURED("discordlink.not-configured"),

    // /discordlink gui. The per-player link-status panel
    GUI_TITLE("discordlink.gui.title"),
    GUI_VALUE_LORE("discordlink.gui.value-lore"),
    GUI_ACTION_HINT("discordlink.gui.action-hint"),
    GUI_BACK("discordlink.gui.back"),
    GUI_STATUS("discordlink.gui.status"),
    GUI_STATUS_LINKED("discordlink.gui.status-linked"),
    GUI_STATUS_UNLINKED("discordlink.gui.status-unlinked"),
    GUI_LINK("discordlink.gui.link"),
    GUI_LINK_HINT("discordlink.gui.link-hint"),
    GUI_UNLINK("discordlink.gui.unlink"),
    GUI_UNLINK_HINT("discordlink.gui.unlink-hint"),
    GUI_UNLINK_CONFIRM("discordlink.gui.unlink-confirm");

    private final String key;

    DiscordlinkMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}

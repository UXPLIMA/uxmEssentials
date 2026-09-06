package com.uxplima.uxmessentials.communication.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The communication context's user-visible message keys: the plugin's <em>own</em> strings only. Each constant
 * maps 1:1 to a kebab-case catalog key in {@code messages_<lang>.conf} ({@code BROADCAST_TOGGLE_ON} ↔
 * {@code communication.broadcast-toggle.on}); the constant is the compile-time handle, the catalog holds the
 * text. These resolve through both locale catalogs and are parity-checked.
 *
 * <p>Deliberately <em>not</em> here: the operator-authored join/quit/death templates, the announcer lines, and
 * the {@code /rules} / {@code /motd} body. That is operator content in the module's content siblings, rendered
 * through MiniMessage, and never a {@code MessageKey}, so the locale-parity guard never sees it. Only the
 * plugin's own confirmations and errors live here.
 *
 * <p>Per the i18n contract, a disabled module still ships its keys so the catalog stays whole and the
 * locale-parity guard sees the full {@code en} key set.
 */
public enum CommunicationMessageKey implements MessageKey {

    // /broadcasttoggle: the per-player announcer subscription toggle confirmations.
    BROADCAST_TOGGLE_ON("communication.broadcast-toggle.on"),
    BROADCAST_TOGGLE_OFF("communication.broadcast-toggle.off"),

    // /rules /motd /info: a requested info page that is not configured.
    INFO_PAGE_MISSING("communication.info-page-missing"),

    // /rules /motd /info [page]: the paginated header/footer chrome drawn around a multi-page info body. The page
    // body itself is operator content, never a MessageKey; only this framing is the plugin's own string.
    INFO_PAGE_HEADER("communication.info-page.header"),
    INFO_PAGE_FOOTER("communication.info-page.footer"),

    // /me: the third-person action broadcast line; the typed action is a placeholder, never re-parsed.
    ME("communication.me"),

    // /clearchat: the cleared notice the flushed players see, and the actor confirmation.
    CLEARCHAT_CLEARED("communication.clearchat.cleared"),
    CLEARCHAT_BY("communication.clearchat.by"),

    // /togglechat: the global chat lock toggle confirmations and the blocked notice a muted speaker sees.
    CHAT_LOCK_ON("communication.chat-lock.on"),
    CHAT_LOCK_OFF("communication.chat-lock.off"),
    CHAT_LOCKED("communication.chat-lock.locked"),

    // /uxmess reload communication, /announce reload: the announcer config was reloaded.
    ANNOUNCER_RELOADED("communication.announcer-reloaded"),

    // /announce list, the header above the announcement list, one entry per announcement, and the empty notice.
    ANNOUNCE_LIST_HEADER("communication.announce.list-header"),
    ANNOUNCE_LIST_ENTRY("communication.announce.list-entry"),
    ANNOUNCE_LIST_EMPTY("communication.announce.list-empty"),

    // /announce preview <id>: the requested announcement id is not configured.
    ANNOUNCE_PREVIEW_UNKNOWN("communication.announce.preview-unknown"),

    // /communication gui, the admin panel chrome (title, value lore, back) shared by every panel button.
    GUI_PANEL_TITLE("communication.gui.panel.title"),
    GUI_PANEL_VALUE_LORE("communication.gui.panel.value-lore"),
    GUI_PANEL_BACK("communication.gui.panel.back"),
    GUI_PANEL_ACTION_HINT("communication.gui.panel.action-hint"),
    // The chat-lock toggle button and its two value states.
    GUI_CHAT_LOCK("communication.gui.chat-lock"),
    GUI_VALUE_LOCKED("communication.gui.value-locked"),
    GUI_VALUE_UNLOCKED("communication.gui.value-unlocked"),
    // The clearchat action button (confirm-gated) and its confirm-menu title.
    GUI_CLEARCHAT("communication.gui.clearchat"),
    GUI_CLEARCHAT_CONFIRM("communication.gui.clearchat-confirm"),
    // The broadcast action button and the anvil prompt that captures the message.
    GUI_BROADCAST("communication.gui.broadcast"),
    GUI_BROADCAST_PROMPT("communication.gui.broadcast-prompt"),
    // The announcer-list button on the panel and the read-only list view it opens.
    GUI_ANNOUNCER("communication.gui.announcer"),
    GUI_ANNOUNCER_TITLE("communication.gui.announcer.title"),
    GUI_ANNOUNCER_ENTRY_NAME("communication.gui.announcer.entry-name"),
    GUI_ANNOUNCER_ENTRY_LORE("communication.gui.announcer.entry-lore"),
    GUI_ANNOUNCER_PREV("communication.gui.announcer.prev"),
    GUI_ANNOUNCER_NEXT("communication.gui.announcer.next"),

    // /announce (and /announce editor): the DB-backed announcement editor. The list screen of store
    // announcements with a create button, the create id prompt, and the chat confirmations the editor sends.
    ANNOUNCE_EDITOR_LIST_TITLE("communication.announce.editor.list-title"),
    ANNOUNCE_EDITOR_LIST_PREV("communication.announce.editor.list-prev"),
    ANNOUNCE_EDITOR_LIST_NEXT("communication.announce.editor.list-next"),
    ANNOUNCE_EDITOR_LIST_CREATE("communication.announce.editor.list-create"),
    ANNOUNCE_EDITOR_LIST_CREATE_PROMPT("communication.announce.editor.list-create-prompt"),
    ANNOUNCE_EDITOR_ENTRY_NAME("communication.announce.editor.entry-name"),
    ANNOUNCE_EDITOR_ENTRY_LORE("communication.announce.editor.entry-lore"),
    ANNOUNCE_EDITOR_CREATED("communication.announce.editor.created"),
    ANNOUNCE_EDITOR_CREATE_EXISTS("communication.announce.editor.create-exists"),

    // The per-announcement editor: the title, the shared value lore each property's value renders into, the back
    // button, the delete button and its confirm title, and the on/off and none words a value lore shows.
    ANNOUNCE_EDITOR_TITLE("communication.announce.editor.title"),
    ANNOUNCE_EDITOR_VALUE_LORE("communication.announce.editor.value-lore"),
    ANNOUNCE_EDITOR_BACK("communication.announce.editor.back"),
    ANNOUNCE_EDITOR_DELETE("communication.announce.editor.delete"),
    ANNOUNCE_EDITOR_DELETE_CONFIRM("communication.announce.editor.delete-confirm"),
    ANNOUNCE_EDITOR_VALUE_ON("communication.announce.editor.value-on"),
    ANNOUNCE_EDITOR_VALUE_OFF("communication.announce.editor.value-off"),
    ANNOUNCE_EDITOR_VALUE_NONE("communication.announce.editor.value-none"),

    // The editable properties: message (the lines, edited as a list), enabled, the per-channel toggles, and the
    // world and permission targets that compose into the stored display condition.
    ANNOUNCE_EDITOR_PROP_MESSAGE("communication.announce.editor.prop-message"),
    ANNOUNCE_EDITOR_MESSAGE_TITLE("communication.announce.editor.message-title"),
    ANNOUNCE_EDITOR_MESSAGE_ENTRY_NAME("communication.announce.editor.message-entry-name"),
    ANNOUNCE_EDITOR_MESSAGE_ENTRY_HINTS("communication.announce.editor.message-entry-hints"),
    ANNOUNCE_EDITOR_MESSAGE_ADD("communication.announce.editor.message-add"),
    ANNOUNCE_EDITOR_MESSAGE_ADD_PROMPT("communication.announce.editor.message-add-prompt"),
    ANNOUNCE_EDITOR_MESSAGE_EDIT_PROMPT("communication.announce.editor.message-edit-prompt"),
    ANNOUNCE_EDITOR_MESSAGE_REMOVE_CONFIRM("communication.announce.editor.message-remove-confirm"),
    ANNOUNCE_EDITOR_MESSAGE_BACK("communication.announce.editor.message-back"),
    ANNOUNCE_EDITOR_PROP_ENABLED("communication.announce.editor.prop-enabled"),
    ANNOUNCE_EDITOR_PROP_CHANNEL_CHAT("communication.announce.editor.prop-channel-chat"),
    ANNOUNCE_EDITOR_PROP_CHANNEL_ACTION_BAR("communication.announce.editor.prop-channel-action-bar"),
    ANNOUNCE_EDITOR_PROP_CHANNEL_TITLE("communication.announce.editor.prop-channel-title"),
    ANNOUNCE_EDITOR_PROP_CHANNEL_SUBTITLE("communication.announce.editor.prop-channel-subtitle"),
    ANNOUNCE_EDITOR_PROP_CHANNEL_BOSS_BAR("communication.announce.editor.prop-channel-boss-bar"),
    ANNOUNCE_EDITOR_PROP_WORLD("communication.announce.editor.prop-world"),
    ANNOUNCE_EDITOR_PROP_WORLD_PROMPT("communication.announce.editor.prop-world-prompt"),
    ANNOUNCE_EDITOR_PROP_PERMISSION("communication.announce.editor.prop-permission"),
    ANNOUNCE_EDITOR_PROP_PERMISSION_PROMPT("communication.announce.editor.prop-permission-prompt"),

    // /announce settings: the global announcer settings screen reached from the list GUI's last slot. The list
    // button that opens it, the screen title and back button, the value lore each setting renders into, the word
    // shown for a setting deferring to the file default, and the two settings (default interval, min-players) with
    // their anvil prompts.
    ANNOUNCE_SETTINGS_BUTTON("communication.announce.settings.button"),
    ANNOUNCE_SETTINGS_TITLE("communication.announce.settings.title"),
    ANNOUNCE_SETTINGS_BACK("communication.announce.settings.back"),
    ANNOUNCE_SETTINGS_VALUE_LORE("communication.announce.settings.value-lore"),
    ANNOUNCE_SETTINGS_VALUE_DEFAULT("communication.announce.settings.value-default"),
    ANNOUNCE_SETTINGS_PROP_INTERVAL("communication.announce.settings.prop-interval"),
    ANNOUNCE_SETTINGS_PROP_INTERVAL_PROMPT("communication.announce.settings.prop-interval-prompt"),
    ANNOUNCE_SETTINGS_PROP_MIN_PLAYERS("communication.announce.settings.prop-min-players"),
    ANNOUNCE_SETTINGS_PROP_MIN_PLAYERS_PROMPT("communication.announce.settings.prop-min-players-prompt");

    private final String key;

    CommunicationMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}

package com.uxplima.uxmessentials.vaults.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The vaults context's user-visible message keys. Each constant maps 1:1 to a kebab-case catalog key in
 * {@code messages_<lang>.conf} ({@code VAULT_OPENED} ↔ {@code vaults.opened}); the constant is the
 * compile-time handle, the catalog holds the text. There are no inline player-facing literals anywhere in the
 * context: every message resolves through one of these.
 *
 * <p>Per the i18n contract, a disabled module still ships its keys so the catalog stays whole and the
 * locale-parity guard sees the full {@code en} key set.
 */
public enum VaultsMessageKey implements MessageKey {

    // GUI window titles (rendered into a Component for the inventory view)
    VAULT_TITLE("vaults.title"),
    VAULT_ADMIN_TITLE("vaults.admin.title"),

    // /vault, /vault <n>
    VAULT_OPENED("vaults.opened"),
    VAULT_LIST_HEADER("vaults.list.header"),
    VAULT_LIST_ENTRY("vaults.list.entry"),
    VAULT_NONE_OWNED("vaults.none-owned"),
    VAULT_AMOUNT_EXCEEDED("vaults.amount-exceeded"),

    // /vault info
    VAULT_INFO_HEADER("vaults.info.header"),
    VAULT_INFO_LINE("vaults.info.line"),

    // /vault <player> [n] (admin)
    VAULT_ADMIN_OPENED("vaults.admin.opened"),
    VAULT_ADMIN_UNKNOWN_TARGET("vaults.admin.unknown-target"),

    // /vault delete <n>, /vault delete <player> <n>
    VAULT_DELETED("vaults.deleted"),
    VAULT_DELETE_UNKNOWN("vaults.delete-unknown"),
    VAULT_CANNOT_AFFORD("vaults.cannot-afford"),

    // /vault rename <n> [name], /vault icon <n> [material]
    VAULT_RENAMED("vaults.renamed"),
    VAULT_NAME_CLEARED("vaults.name-cleared"),
    VAULT_ICON_SET("vaults.icon-set"),
    VAULT_RENAME_UNKNOWN("vaults.rename-unknown"),
    VAULT_NAME_TOO_LONG("vaults.name-too-long"),
    VAULT_UNKNOWN_MATERIAL("vaults.unknown-material"),
    VAULT_ICON_NOT_ALLOWED("vaults.icon-not-allowed"),
    VAULT_ICON_NO_HELD_ITEM("vaults.icon-no-held-item"),

    // item blacklist (items refused on save and returned to the player)
    VAULT_ITEM_BLOCKED("vaults.item-blocked"),

    // overflow rescue (items in slots beyond a shrunken size quota, returned to the player on open)
    VAULT_OVERFLOW_RETURNED("vaults.overflow-returned"),

    // vault-selector GUI (/vault with several owned): one icon per index, owned vs locked
    VAULT_SELECTOR_TITLE("vaults.selector.title"),
    VAULT_SELECTOR_ENTRY_NAME("vaults.selector.entry.name"),
    VAULT_SELECTOR_NAMED_ENTRY("vaults.selector.named-entry"),
    VAULT_SELECTOR_ENTRY_LORE("vaults.selector.entry.lore"),
    VAULT_SELECTOR_LOCKED_NAME("vaults.selector.locked.name"),
    VAULT_SELECTOR_LOCKED_LORE("vaults.selector.locked.lore"),
    VAULT_SELECTOR_LOCKED_CLICK("vaults.selector.locked.click"),
    VAULT_SELECTOR_PREV("vaults.selector.prev"),
    VAULT_SELECTOR_NEXT("vaults.selector.next"),

    // shared
    VAULT_ALREADY_OPEN("vaults.already-open"),
    VAULT_SAVED("vaults.saved"),
    VAULT_PLAYERS_ONLY("vaults.players-only");

    private final String key;

    VaultsMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}

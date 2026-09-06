package com.uxplima.uxmessentials.kits.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The kits context's user-visible message keys. Each constant maps 1:1 to a kebab-case catalog key in
 * {@code messages_<lang>.conf} ({@code KIT_CLAIMED} ↔ {@code kit.claimed}); the constant is the compile-time
 * handle, the catalog holds the text. There are no inline player-facing literals anywhere in the context
 * every message resolves through one of these.
 *
 * <p>Per the i18n contract, a disabled module still ships its keys so the catalog stays whole and the
 * locale-parity guard sees the full {@code en} key set.
 */
public enum KitsMessageKey implements MessageKey {

    // claim feedback
    KIT_CLAIMED("kit.claimed"),

    // listing
    KIT_LIST_HEADER("kit.list.header"),
    KIT_LIST_ENTRY("kit.list.entry"),
    KIT_LIST_EMPTY("kit.list.empty"),

    // browse menu (/kit list)
    KIT_MENU_TITLE("kit.menu.title"),
    KIT_MENU_ENTRY_NAME("kit.menu.entry.name"),
    KIT_MENU_LORE_COOLDOWN("kit.menu.lore.cooldown"),
    KIT_MENU_LORE_ONETIME("kit.menu.lore.one-time"),
    KIT_MENU_LORE_COST("kit.menu.lore.cost"),
    KIT_MENU_LORE_CLAIMABLE("kit.menu.lore.claimable"),
    KIT_MENU_UNAVAILABLE_NAME("kit.menu.unavailable.name"),
    KIT_MENU_LORE_UNAVAILABLE("kit.menu.lore.unavailable"),
    KIT_MENU_OUT_OF_STOCK_NAME("kit.menu.out-of-stock.name"),
    KIT_MENU_LORE_OUT_OF_STOCK("kit.menu.lore.out-of-stock"),
    KIT_MENU_PREVIEW_HINT("kit.menu.preview-hint"),
    KIT_MENU_PREVIEW_DENIED("kit.menu.preview-denied"),
    KIT_MENU_PREV("kit.menu.prev"),
    KIT_MENU_NEXT("kit.menu.next"),
    KIT_MENU_BACK("kit.menu.back"),
    KIT_MENU_CATEGORY_NAME("kit.menu.category.name"),
    KIT_MENU_CATEGORY_LORE("kit.menu.category.lore"),

    // preview (/kit show)
    KIT_PREVIEW_HEADER("kit.preview.header"),
    KIT_PREVIEW_ENTRY("kit.preview.entry"),
    KIT_PREVIEW_GUI_TITLE("kit.preview.gui-title"),

    // authoring (/kit create /kit del /kit editor)
    KIT_CREATED("kit.created"),
    KIT_DELETED("kit.deleted"),
    KIT_EDIT_OPENED("kit.edit.opened"),
    KIT_EDITOR_GUI_TITLE("kit.editor.gui-title"),
    KIT_EDITOR_SAVED("kit.editor.saved"),

    // reset (/kit reset)
    KIT_RESET("kit.reset"),
    KIT_RESET_ALL("kit.reset-all"),

    // failures
    KIT_NOT_FOUND("kit.not-found"),
    KIT_NONE("kit.none"),
    KIT_ALREADY_EXISTS("kit.already-exists"),
    KIT_NO_PERMISSION("kit.no-permission"),
    KIT_ON_COOLDOWN("kit.on-cooldown"),
    KIT_ALREADY_CLAIMED("kit.already-claimed"),
    KIT_REQUIREMENTS_NOT_MET("kit.requirements-not-met"),
    KIT_CANNOT_AFFORD("kit.cannot-afford"),
    KIT_INVENTORY_FULL("kit.inventory-full"),
    KIT_UNAVAILABLE("kit.unavailable"),
    KIT_OUT_OF_STOCK("kit.out-of-stock"),
    KIT_CLAIM_CANCELLED("kit.claim-cancelled"),

    // requirement status-symbol lore (browse menu / preview icon)
    KIT_REQUIREMENT_MET("kit.requirement.met"),
    KIT_REQUIREMENT_UNMET("kit.requirement.unmet"),

    // kit manager GUI (/kit editor)
    KIT_EDITOR_MANAGER_TITLE("kit.editor.manager-title"),
    KIT_EDITOR_CREATE_BUTTON_NAME("kit.editor.create-button.name"),
    KIT_EDITOR_CREATE_BUTTON_LORE("kit.editor.create-button.lore"),
    KIT_EDITOR_CLOSE_BUTTON_NAME("kit.editor.close-button.name"),
    KIT_EDITOR_KIT_LORE_LORE("kit.editor.kit-lore.lore"),

    // kit settings GUI
    KIT_EDITOR_SETTINGS_TITLE("kit.editor.settings.title"),
    KIT_EDITOR_SETTINGS_EDIT_ITEMS_NAME("kit.editor.settings.edit-items.name"),
    KIT_EDITOR_SETTINGS_EDIT_ITEMS_LORE("kit.editor.settings.edit-items.lore"),
    KIT_EDITOR_SETTINGS_PERMISSION_NAME("kit.editor.settings.permission.name"),
    KIT_EDITOR_SETTINGS_PERMISSION_LORE("kit.editor.settings.permission.lore"),
    KIT_EDITOR_SETTINGS_ONETIME_NAME("kit.editor.settings.onetime.name"),
    KIT_EDITOR_SETTINGS_ONETIME_LORE("kit.editor.settings.onetime.lore"),
    KIT_EDITOR_SETTINGS_COOLDOWN_NAME("kit.editor.settings.cooldown.name"),
    KIT_EDITOR_SETTINGS_COOLDOWN_LORE("kit.editor.settings.cooldown.lore"),
    KIT_EDITOR_SETTINGS_COST_NAME("kit.editor.settings.cost.name"),
    KIT_EDITOR_SETTINGS_COST_LORE("kit.editor.settings.cost.lore"),
    KIT_EDITOR_SETTINGS_DISPLAY_NAME_NAME("kit.editor.settings.display-name.name"),
    KIT_EDITOR_SETTINGS_DISPLAY_NAME_LORE("kit.editor.settings.display-name.lore"),
    KIT_EDITOR_SETTINGS_DISPLAY_MATERIAL_NAME("kit.editor.settings.display-material.name"),
    KIT_EDITOR_SETTINGS_DISPLAY_MATERIAL_LORE("kit.editor.settings.display-material.lore"),
    KIT_EDITOR_SETTINGS_DISPLAY_LORE_NAME("kit.editor.settings.display-lore.name"),
    KIT_EDITOR_SETTINGS_DISPLAY_LORE_LORE("kit.editor.settings.display-lore.lore"),
    KIT_EDITOR_SETTINGS_COMMANDS_NAME("kit.editor.settings.commands.name"),
    KIT_EDITOR_SETTINGS_COMMANDS_LORE("kit.editor.settings.commands.lore"),
    KIT_EDITOR_SETTINGS_DELETE_NAME("kit.editor.settings.delete.name"),
    KIT_EDITOR_SETTINGS_DELETE_LORE("kit.editor.settings.delete.lore"),
    KIT_EDITOR_SETTINGS_BACK_BUTTON("kit.editor.settings.back-button"),
    KIT_EDITOR_SETTINGS_FIRSTJOIN_NAME("kit.editor.settings.firstjoin.name"),
    KIT_EDITOR_SETTINGS_FIRSTJOIN_LORE("kit.editor.settings.firstjoin.lore"),
    KIT_EDITOR_SETTINGS_AUTOEQUIP_NAME("kit.editor.settings.autoequip.name"),
    KIT_EDITOR_SETTINGS_AUTOEQUIP_LORE("kit.editor.settings.autoequip.lore"),

    // prompts & editor errors
    KIT_EDITOR_PROMPT_CREATE("kit.editor.prompt.create"),
    KIT_EDITOR_PROMPT_CANCELLED("kit.editor.prompt.cancelled"),
    KIT_EDITOR_ERROR_INVALID_NAME("kit.editor.error.invalid-name"),
    KIT_EDITOR_PROMPT_COOLDOWN("kit.editor.prompt.cooldown"),
    KIT_EDITOR_ERROR_NEGATIVE_COOLDOWN("kit.editor.error.negative-cooldown"),
    KIT_EDITOR_ERROR_INVALID_NUMBER("kit.editor.error.invalid-number"),
    KIT_EDITOR_PROMPT_COST("kit.editor.prompt.cost"),
    KIT_EDITOR_ERROR_NEGATIVE_COST("kit.editor.error.negative-cost"),
    KIT_EDITOR_PROMPT_DISPLAY_NAME("kit.editor.prompt.display-name"),
    KIT_EDITOR_ERROR_EMPTY_HAND("kit.editor.error.empty-hand"),
    KIT_EDITOR_PROMPT_DISPLAY_LORE("kit.editor.prompt.display-lore"),
    KIT_EDITOR_PROMPT_COMMANDS("kit.editor.prompt.commands"),

    // Category Manager & Settings GUI
    KIT_EDITOR_CATEGORY_MANAGER_TITLE("kit.editor.category.manager-title"),
    KIT_EDITOR_CATEGORY_CREATE_BUTTON_NAME("kit.editor.category.create-button.name"),
    KIT_EDITOR_CATEGORY_CREATE_BUTTON_LORE("kit.editor.category.create-button.lore"),
    KIT_EDITOR_CATEGORY_LORE_EDIT_HINT("kit.editor.category.lore.edit-hint"),
    KIT_EDITOR_CATEGORY_SETTINGS_TITLE("kit.editor.category.settings.title"),
    KIT_EDITOR_CATEGORY_SETTINGS_DISPLAY_NAME_NAME("kit.editor.category.settings.display-name.name"),
    KIT_EDITOR_CATEGORY_SETTINGS_DISPLAY_NAME_LORE("kit.editor.category.settings.display-name.lore"),
    KIT_EDITOR_CATEGORY_SETTINGS_DISPLAY_NAME_PROMPT("kit.editor.category.settings.display-name.prompt"),
    KIT_EDITOR_CATEGORY_SETTINGS_DISPLAY_MATERIAL_NAME("kit.editor.category.settings.display-material.name"),
    KIT_EDITOR_CATEGORY_SETTINGS_DISPLAY_MATERIAL_LORE("kit.editor.category.settings.display-material.lore"),
    KIT_EDITOR_CATEGORY_SETTINGS_DISPLAY_LORE_NAME("kit.editor.category.settings.display-lore.name"),
    KIT_EDITOR_CATEGORY_SETTINGS_DISPLAY_LORE_LORE("kit.editor.category.settings.display-lore.lore"),
    KIT_EDITOR_CATEGORY_SETTINGS_DISPLAY_LORE_PROMPT("kit.editor.category.settings.display-lore.prompt"),
    KIT_EDITOR_CATEGORY_SETTINGS_SLOT_NAME("kit.editor.category.settings.slot.name"),
    KIT_EDITOR_CATEGORY_SETTINGS_SLOT_LORE("kit.editor.category.settings.slot.lore"),
    KIT_EDITOR_CATEGORY_SETTINGS_SLOT_PROMPT("kit.editor.category.settings.slot.prompt"),
    KIT_EDITOR_CATEGORY_SETTINGS_PARENT_NAME("kit.editor.category.settings.parent.name"),
    KIT_EDITOR_CATEGORY_SETTINGS_PARENT_LORE("kit.editor.category.settings.parent.lore"),
    KIT_EDITOR_CATEGORY_SETTINGS_DELETE_NAME("kit.editor.category.settings.delete.name"),
    KIT_EDITOR_CATEGORY_SETTINGS_DELETE_LORE("kit.editor.category.settings.delete.lore"),
    KIT_EDITOR_CATEGORY_PROMPT_CREATE("kit.editor.category.prompt.create"),
    KIT_EDITOR_CATEGORY_SELECTOR_TITLE("kit.editor.category.selector.title"),
    KIT_EDITOR_CATEGORY_SELECTOR_NONE_NAME("kit.editor.category.selector.none.name"),
    KIT_EDITOR_CATEGORY_SELECTOR_NONE_LORE("kit.editor.category.selector.none.lore"),
    KIT_EDITOR_CATEGORY_PARENT_SELECTOR_TITLE("kit.editor.category.parent-selector.title"),
    KIT_EDITOR_SETTINGS_CATEGORY_NAME("kit.editor.settings.category.name"),
    KIT_EDITOR_SETTINGS_CATEGORY_LORE("kit.editor.settings.category.lore"),

    // category icon lore (shared across manager + selectors)
    KIT_EDITOR_CATEGORY_ICON_ID("kit.editor.category.icon.id"),
    KIT_EDITOR_CATEGORY_ICON_SLOT("kit.editor.category.icon.slot"),
    KIT_EDITOR_CATEGORY_ICON_PARENT("kit.editor.category.icon.parent"),
    KIT_EDITOR_CATEGORY_SELECTOR_SELECT_HINT("kit.editor.category.selector.select-hint"),
    KIT_EDITOR_CATEGORY_PARENT_SELECTOR_SELECT_HINT("kit.editor.category.parent-selector.select-hint"),

    // category settings current-value lore

    // manager hints
    KIT_EDITOR_MANAGE_CATEGORIES_LORE("kit.editor.manage-categories.lore"),

    // values
    KIT_EDITOR_VALUE_YES("kit.editor.value.yes"),
    KIT_EDITOR_VALUE_NO("kit.editor.value.no"),
    KIT_EDITOR_VALUE_REQUIRED("kit.editor.value.required"),
    KIT_EDITOR_VALUE_NONE("kit.editor.value.none"),
    KIT_EDITOR_VALUE_FREE("kit.editor.value.free");

    private final String key;

    KitsMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}

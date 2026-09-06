package com.uxplima.uxmessentials.warps.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The warps context's user-visible message keys. Each constant maps 1:1 to a kebab-case catalog key in
 * {@code messages_<lang>.conf} ({@code WARP_SET} ↔ {@code warp.set}); the constant is the compile-time
 * handle, the catalog holds the text. There are no inline player-facing literals anywhere in the
 * context: every message resolves through one of these.
 *
 * <p>Per the i18n contract, a disabled module still ships its keys so the catalog stays whole and the
 * locale-parity guard sees the full {@code en} key set.
 */
public enum WarpsMessageKey implements MessageKey {

    // set / move / delete feedback
    WARP_SET("warp.set"),
    WARP_MOVED("warp.moved"),
    WARP_DELETED("warp.deleted"),

    // teleport
    WARP_TELEPORTING("warp.teleporting"),
    WARP_SENT("warp.sent"),

    // listing
    WARP_LIST_HEADER("warp.list.header"),
    WARP_LIST_ENTRY("warp.list.entry"),
    WARP_LIST_EMPTY("warp.list.empty"),

    // browse menu (/warps)
    WARP_MENU_TITLE("warp.menu.title"),
    WARP_MENU_ENTRY_NAME("warp.menu.entry.name"),
    WARP_MENU_LORE_COST("warp.menu.lore.cost"),
    WARP_MENU_LORE_PERMISSION("warp.menu.lore.permission"),
    WARP_MENU_LORE_LOCATION("warp.menu.lore.location"),
    WARP_MENU_LORE_USABLE("warp.menu.lore.usable"),
    WARP_MENU_PREV("warp.menu.prev"),
    WARP_MENU_NEXT("warp.menu.next"),

    // info
    WARP_INFO_HEADER("warp.info.header"),
    WARP_INFO_OWNER("warp.info.owner"),
    WARP_INFO_CREATED("warp.info.created"),
    WARP_INFO_COST("warp.info.cost"),
    WARP_INFO_PERMISSION("warp.info.permission"),

    // failures
    WARP_NOT_FOUND("warp.not-found"),
    WARP_NONE("warp.none"),
    WARP_NAME_TAKEN("warp.name-taken"),
    WARP_NO_PERMISSION("warp.no-permission"),
    WARP_CANNOT_AFFORD("warp.cannot-afford"),
    WARP_UNSAFE("warp.unsafe"),
    WARP_LOCKED("warp.locked"),
    WARP_WRONG_PASSWORD("warp.wrong-password"),
    WARP_PASSWORD_SET("warp.password-set"),
    WARP_PASSWORD_CLEARED("warp.password-cleared"),
    WARP_LOCK_TOGGLED("warp.lock-toggled"),
    WARP_WORLD_BLACKLISTED("warp.world-blacklisted"),
    WARP_RATED("warp.rated"),
    WARP_RATING("warp.rating"),
    WARP_RATING_INVALID("warp.rating-invalid"),

    // editor
    WARP_EDITOR_TITLE("warp.editor.title"),
    WARP_EDITOR_LOCK_NAME("warp.editor.lock.name"),
    WARP_EDITOR_LOCK_LORE("warp.editor.lock.lore"),
    WARP_EDITOR_PASSWORD_NAME("warp.editor.password.name"),
    WARP_EDITOR_PASSWORD_LORE("warp.editor.password.lore"),
    WARP_EDITOR_PASSWORD_PROMPT("warp.editor.password.prompt"),
    WARP_EDITOR_WELCOME_NAME("warp.editor.welcome.name"),
    WARP_EDITOR_WELCOME_LORE("warp.editor.welcome.lore"),
    WARP_EDITOR_WELCOME_PROMPT("warp.editor.welcome.prompt"),
    WARP_EDITOR_SOUNDS_NAME("warp.editor.sounds.name"),
    WARP_EDITOR_SOUNDS_LORE("warp.editor.sounds.lore"),
    WARP_EDITOR_SOUND_DEPARTURE_PROMPT("warp.editor.sound.departure.prompt"),
    WARP_EDITOR_SOUND_ARRIVAL_PROMPT("warp.editor.sound.arrival.prompt"),
    WARP_EDITOR_PARTICLES_NAME("warp.editor.particles.name"),
    WARP_EDITOR_PARTICLES_LORE("warp.editor.particles.lore"),
    WARP_EDITOR_PARTICLE_DEPARTURE_PROMPT("warp.editor.particle.departure.prompt"),
    WARP_EDITOR_PARTICLE_ARRIVAL_PROMPT("warp.editor.particle.arrival.prompt"),
    WARP_EDITOR_WARMUP_NAME("warp.editor.warmup.name"),
    WARP_EDITOR_WARMUP_LORE("warp.editor.warmup.lore"),
    WARP_EDITOR_WARMUP_PROMPT("warp.editor.warmup.prompt"),
    WARP_EDITOR_COOLDOWN_NAME("warp.editor.cooldown.name"),
    WARP_EDITOR_COOLDOWN_LORE("warp.editor.cooldown.lore"),
    WARP_EDITOR_COOLDOWN_PROMPT("warp.editor.cooldown.prompt"),
    WARP_EDITOR_ICON_NAME("warp.editor.icon.name"),
    WARP_EDITOR_ICON_LORE("warp.editor.icon.lore"),
    WARP_EDITOR_ICON_ERROR_HAND("warp.editor.icon.error-hand"),
    WARP_EDITOR_TELEPORT_NAME("warp.editor.teleport.name"),
    WARP_EDITOR_TELEPORT_LORE("warp.editor.teleport.lore"),
    WARP_EDITOR_CLOSE("warp.editor.close"),
    WARP_EDITOR_INVALID_NUMBER("warp.editor.invalid-number"),
    WARP_EDITOR_PROMPT_CANCELLED("warp.editor.prompt.cancelled"),
    WARP_EDITOR_WELCOME_LORE_ENTRY("warp.editor.welcome.lore.entry"),

    // editor display values
    WARP_EDITOR_VALUE_NONE("warp.editor.value.none"),
    WARP_EDITOR_VALUE_LOCKED("warp.editor.value.locked"),
    WARP_EDITOR_VALUE_UNLOCKED("warp.editor.value.unlocked"),

    // sound selector GUI
    WARP_EDITOR_SOUND_SELECTOR_TITLE_DEPARTURE("warp.editor.sound-selector.title.departure"),
    WARP_EDITOR_SOUND_SELECTOR_TITLE_ARRIVAL("warp.editor.sound-selector.title.arrival"),
    WARP_EDITOR_SOUND_SELECTOR_ENTRY_NAME("warp.editor.sound-selector.entry.name"),
    WARP_EDITOR_SOUND_SELECTOR_ENTRY_LORE("warp.editor.sound-selector.entry.lore"),
    WARP_EDITOR_SOUND_SELECTOR_CUSTOM_NAME("warp.editor.sound-selector.custom.name"),
    WARP_EDITOR_SOUND_SELECTOR_CUSTOM_LORE("warp.editor.sound-selector.custom.lore"),
    WARP_EDITOR_SOUND_SELECTOR_REMOVE_NAME("warp.editor.sound-selector.remove.name"),
    WARP_EDITOR_SOUND_SELECTOR_REMOVE_LORE("warp.editor.sound-selector.remove.lore"),

    // particle selector GUI
    WARP_EDITOR_PARTICLE_SELECTOR_TITLE_DEPARTURE("warp.editor.particle-selector.title.departure"),
    WARP_EDITOR_PARTICLE_SELECTOR_TITLE_ARRIVAL("warp.editor.particle-selector.title.arrival"),
    WARP_EDITOR_PARTICLE_SELECTOR_ENTRY_NAME("warp.editor.particle-selector.entry.name"),
    WARP_EDITOR_PARTICLE_SELECTOR_ENTRY_LORE("warp.editor.particle-selector.entry.lore"),
    WARP_EDITOR_PARTICLE_SELECTOR_CUSTOM_NAME("warp.editor.particle-selector.custom.name"),
    WARP_EDITOR_PARTICLE_SELECTOR_CUSTOM_LORE("warp.editor.particle-selector.custom.lore"),
    WARP_EDITOR_PARTICLE_SELECTOR_REMOVE_NAME("warp.editor.particle-selector.remove.name"),
    WARP_EDITOR_PARTICLE_SELECTOR_REMOVE_LORE("warp.editor.particle-selector.remove.lore"),

    // shared selector back button
    WARP_EDITOR_SELECTOR_BACK("warp.editor.selector.back"),

    // welcome messages GUI
    WARP_EDITOR_WELCOME_MANAGER_TITLE("warp.editor.welcome-manager.title"),
    WARP_EDITOR_WELCOME_MANAGER_ENTRY_NAME("warp.editor.welcome-manager.entry.name"),
    WARP_EDITOR_WELCOME_MANAGER_ENTRY_LORE("warp.editor.welcome-manager.entry.lore"),
    WARP_EDITOR_WELCOME_MANAGER_ADD_NAME("warp.editor.welcome-manager.add.name"),
    WARP_EDITOR_WELCOME_MANAGER_ADD_LORE("warp.editor.welcome-manager.add.lore"),
    WARP_EDITOR_WELCOME_MANAGER_CLEAR_NAME("warp.editor.welcome-manager.clear.name"),
    WARP_EDITOR_WELCOME_MANAGER_CLEAR_LORE("warp.editor.welcome-manager.clear.lore"),

    // signs
    WARP_SIGN_NO_PERMISSION_CREATE("warp.sign.no-permission-create"),
    WARP_SIGN_NO_PERMISSION_USE("warp.sign.no-permission-use"),
    WARP_SIGN_EMPTY_NAME("warp.sign.empty-name"),
    WARP_SIGN_CREATED("warp.sign.created"),

    // manager GUI (the kit-editor-style /warp edit manager)
    WARP_MANAGER_TITLE("warp.manager.title"),
    WARP_MANAGER_CREATE_BUTTON_NAME("warp.manager.create-button.name"),
    WARP_MANAGER_CREATE_BUTTON_LORE("warp.manager.create-button.lore"),
    WARP_MANAGER_CLOSE_BUTTON_NAME("warp.manager.close-button.name"),
    WARP_MANAGER_ENTRY_LORE("warp.manager.entry.lore"),
    WARP_MANAGER_MANAGE_CATEGORIES_NAME("warp.manager.manage-categories.name"),
    WARP_MANAGER_MANAGE_CATEGORIES_LORE("warp.manager.manage-categories.lore"),
    WARP_MANAGER_CREATE_PROMPT("warp.manager.create-prompt"),
    WARP_MANAGER_ERROR_INVALID_NAME("warp.manager.error.invalid-name"),

    // browse-menu category grouping
    WARP_MENU_BACK("warp.menu.back"),
    WARP_MENU_CATEGORY_NAME("warp.menu.category.name"),
    WARP_MENU_CATEGORY_LORE("warp.menu.category.lore"),

    // category administration GUIs
    WARP_EDITOR_CATEGORY_MANAGER_TITLE("warp.editor.category.manager-title"),
    WARP_EDITOR_CATEGORY_CREATE_BUTTON_NAME("warp.editor.category.create-button.name"),
    WARP_EDITOR_CATEGORY_CREATE_BUTTON_LORE("warp.editor.category.create-button.lore"),
    WARP_EDITOR_CATEGORY_LORE_EDIT_HINT("warp.editor.category.lore.edit-hint"),
    WARP_EDITOR_CATEGORY_ICON_ID("warp.editor.category.icon.id"),
    WARP_EDITOR_CATEGORY_ICON_SLOT("warp.editor.category.icon.slot"),
    WARP_EDITOR_CATEGORY_ICON_PARENT("warp.editor.category.icon.parent"),
    WARP_EDITOR_CATEGORY_PROMPT_CREATE("warp.editor.category.prompt.create"),
    WARP_EDITOR_CATEGORY_SETTINGS_TITLE("warp.editor.category.settings.title"),
    WARP_EDITOR_CATEGORY_SETTINGS_DISPLAY_NAME_NAME("warp.editor.category.settings.display-name.name"),
    WARP_EDITOR_CATEGORY_SETTINGS_DISPLAY_NAME_LORE("warp.editor.category.settings.display-name.lore"),
    WARP_EDITOR_CATEGORY_SETTINGS_DISPLAY_NAME_PROMPT("warp.editor.category.settings.display-name.prompt"),
    WARP_EDITOR_CATEGORY_SETTINGS_DISPLAY_MATERIAL_NAME("warp.editor.category.settings.display-material.name"),
    WARP_EDITOR_CATEGORY_SETTINGS_DISPLAY_MATERIAL_LORE("warp.editor.category.settings.display-material.lore"),
    WARP_EDITOR_CATEGORY_SETTINGS_DISPLAY_LORE_NAME("warp.editor.category.settings.display-lore.name"),
    WARP_EDITOR_CATEGORY_SETTINGS_DISPLAY_LORE_LORE("warp.editor.category.settings.display-lore.lore"),
    WARP_EDITOR_CATEGORY_SETTINGS_DISPLAY_LORE_PROMPT("warp.editor.category.settings.display-lore.prompt"),
    WARP_EDITOR_CATEGORY_SETTINGS_SLOT_NAME("warp.editor.category.settings.slot.name"),
    WARP_EDITOR_CATEGORY_SETTINGS_SLOT_LORE("warp.editor.category.settings.slot.lore"),
    WARP_EDITOR_CATEGORY_SETTINGS_SLOT_PROMPT("warp.editor.category.settings.slot.prompt"),
    WARP_EDITOR_CATEGORY_SETTINGS_PARENT_NAME("warp.editor.category.settings.parent.name"),
    WARP_EDITOR_CATEGORY_SETTINGS_PARENT_LORE("warp.editor.category.settings.parent.lore"),
    WARP_EDITOR_CATEGORY_SETTINGS_DELETE_NAME("warp.editor.category.settings.delete.name"),
    WARP_EDITOR_CATEGORY_SETTINGS_DELETE_LORE("warp.editor.category.settings.delete.lore"),
    WARP_EDITOR_CATEGORY_SELECTOR_TITLE("warp.editor.category.selector.title"),
    WARP_EDITOR_CATEGORY_SELECTOR_NONE_NAME("warp.editor.category.selector.none.name"),
    WARP_EDITOR_CATEGORY_SELECTOR_NONE_LORE("warp.editor.category.selector.none.lore"),
    WARP_EDITOR_CATEGORY_SELECTOR_SELECT_HINT("warp.editor.category.selector.select-hint"),
    WARP_EDITOR_CATEGORY_PARENT_SELECTOR_TITLE("warp.editor.category.parent-selector.title"),
    WARP_EDITOR_CATEGORY_PARENT_SELECTOR_SELECT_HINT("warp.editor.category.parent-selector.select-hint"),

    // warp-editor category assignment row
    WARP_EDITOR_CATEGORY_NAME("warp.editor.category.name"),
    WARP_EDITOR_CATEGORY_LORE("warp.editor.category.lore");

    private final String key;

    WarpsMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}

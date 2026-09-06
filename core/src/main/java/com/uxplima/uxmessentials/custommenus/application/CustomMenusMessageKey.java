package com.uxplima.uxmessentials.custommenus.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The custommenus context's user-visible message keys for the {@code /menu} command. Each constant maps 1:1 to a
 * kebab-case catalog key in {@code messages_<lang>.conf} ({@code MENU_NOT_FOUND} ↔ {@code menu.not-found}); the
 * constant is the compile-time handle, the catalog holds the text. There are no inline player-facing literals in
 * the context. Every line the command shows resolves through one of these (the players-only rejection a console
 * meets reuses the shared {@code command.players-only} key).
 *
 * <p>Per the i18n contract a disabled module still ships its keys, so the catalog stays whole and the
 * locale-parity guard sees the full {@code en} key set whether or not custommenus is enabled.
 */
public enum CustomMenusMessageKey implements MessageKey {

    /** Reply when {@code /menu open <name>} names a menu no loaded spec is registered under. */
    MENU_NOT_FOUND("menu.not-found"),

    /** Header line for {@code /menu list}. */
    MENU_LIST_HEADER("menu.list.header"),

    /** One registered menu name in the {@code /menu list} output ({@code {name}}). */
    MENU_LIST_ENTRY("menu.list.entry"),

    /** Reply for {@code /menu list} when no operator menus are registered. */
    MENU_LIST_EMPTY("menu.list.empty"),

    /** Reply for {@code /menu last} when the player has no remembered menu to reopen (or it is no longer loaded). */
    MENU_NO_LAST("menu.no-last"),

    /** Reply for {@code /menu reload} reporting how many specs loaded and how many were skipped. */
    MENU_RELOADED("menu.reloaded"),

    /** Reply for {@code /menu reload <menu>} reporting the single re-loaded menu's loaded / skipped outcome. */
    MENU_RELOADED_ONE("menu.reloaded-one"),

    /** Confirmation for {@code /menu execute <player> <action>} that {@code {action}} ran for {@code {name}}. */
    MENU_EXECUTED("menu.executed"),

    /** Header line for {@code /menu dump <menu>}: the menu's title, row count and item count. */
    MENU_DUMP_HEADER("menu.dump-header"),

    /** One item line in the {@code /menu dump <menu>} output: id, slots, material and action count. */
    MENU_DUMP_ITEM("menu.dump-item"),

    /** Compact one-line metadata summary for {@code /menu meta <menu>}: rows, item count and the menu's flags. */
    MENU_META("menu.meta"),

    /** Reply when a console (or other non-player) invokes a menu open command whose block forbids the console. */
    MENU_CONSOLE_DENIED("menu.console-denied"),

    /** Confirmation to the sender of {@code /menu open <name> <target>} that the menu opened for {@code {player}}. */
    MENU_OPENED_FOR("menu.opened-for"),

    /** Reply for {@code /menu convert <deluxemenus|zmenu> <path>} reporting the converted / skipped / warning counts. */
    MENU_CONVERTED("menu.converted"),

    /** Reply for {@code /menu convert <deluxemenus|zmenu> <path>} when the given {@code {path}} held no menu YAML. */
    MENU_CONVERT_FAILED("menu.convert-failed"),

    /** Confirmation for {@code /menu save <menu>} that {@code {name}} was written back to its file and reloaded. */
    MENU_SAVED("menu.saved"),

    /** Reply for {@code /menu save <menu>} refused because the spec named the unregistered ids {@code {missing}}. */
    MENU_SAVE_INVALID("menu.save-invalid"),

    /** Reply for {@code /menu save <menu>} when {@code {name}}'s file could not be written. */
    MENU_SAVE_FAILED("menu.save-failed"),

    /** Title of the {@code /menu editor} menu picker. */
    MENU_EDITOR_TITLE("menu.editor.title"),

    /** Title of the {@code /menu editor} picker when no custom menus exist yet. */
    MENU_EDITOR_EMPTY_TITLE("menu.editor.empty-title"),

    /** Name of the picker's create-a-new-menu button. */
    MENU_EDITOR_CREATE("menu.editor.create"),

    /** Prompt shown when the create button asks for the new menu's name. */
    MENU_EDITOR_CREATE_PROMPT("menu.editor.create-prompt"),

    /** Display name of one menu row in the picker ({@code {name}}). */
    MENU_EDITOR_ENTRY_NAME("menu.editor.entry.name"),

    /** Lore of one menu row in the picker: its title, row count and item count ({@code {title}{rows}{items}}). */
    MENU_EDITOR_ENTRY_LORE("menu.editor.entry.lore"),

    /** Title of the per-menu overview panel ({@code {name}{rows}{items}}). */
    MENU_EDITOR_OVERVIEW_TITLE("menu.editor.overview.title"),

    /** The overview panel's value-lore wrapper for each button's hint ({@code {value}}). */
    MENU_EDITOR_OVERVIEW_VALUE_LORE("menu.editor.overview.value-lore"),

    /** Name of the overview panel's back button. */
    MENU_EDITOR_OVERVIEW_BACK("menu.editor.overview.back"),

    /** Name of the overview panel's save button. */
    MENU_EDITOR_SAVE("menu.editor.save"),

    /** Hint lore of the overview panel's save button. */
    MENU_EDITOR_SAVE_HINT("menu.editor.save-hint"),

    /** Name of the overview panel's duplicate button. */
    MENU_EDITOR_DUPLICATE("menu.editor.duplicate"),

    /** Hint lore of the overview panel's duplicate button. */
    MENU_EDITOR_DUPLICATE_HINT("menu.editor.duplicate-hint"),

    /** Prompt shown when the duplicate button asks for the copy's name ({@code {name}}). */
    MENU_EDITOR_DUPLICATE_PROMPT("menu.editor.duplicate-prompt"),

    /** Name of the overview panel's rename button. */
    MENU_EDITOR_RENAME("menu.editor.rename"),

    /** Hint lore of the overview panel's rename button. */
    MENU_EDITOR_RENAME_HINT("menu.editor.rename-hint"),

    /** Prompt shown when the rename button asks for the new name ({@code {name}}). */
    MENU_EDITOR_RENAME_PROMPT("menu.editor.rename-prompt"),

    /** Name of the overview panel's button that opens the slot-grid canvas. */
    MENU_EDITOR_GRID("menu.editor.grid"),

    /** Hint lore of the overview panel's slot-grid button. */
    MENU_EDITOR_GRID_HINT("menu.editor.grid-hint"),

    /** Title of the slot-grid canvas ({@code {name}{rows}}). */
    MENU_GRID_TITLE("menu.editor.grid.title"),

    /** Name of an empty cell's placeholder on the grid canvas. */
    MENU_GRID_EMPTY("menu.editor.grid.empty"),

    /** Lore of an empty cell's placeholder, the "click to add an item" hint, kept off the display name. */
    MENU_GRID_EMPTY_LORE("menu.editor.grid.empty-lore"),

    /** Name of the grid canvas's back-to-overview control button. */
    MENU_GRID_BACK("menu.editor.grid.back"),

    /** Name of the grid canvas's save control button. */
    MENU_GRID_SAVE("menu.editor.grid.save"),

    /** Feedback that a default item was added at {@code {slot}} on the grid. */
    MENU_GRID_ADDED("menu.editor.grid.added"),

    /** Feedback that the item at {@code {slot}} was picked up, awaiting a target slot. */
    MENU_GRID_SELECTED("menu.editor.grid.selected"),

    /** Feedback that the picked-up item moved from {@code {from}} to {@code {to}}. */
    MENU_GRID_MOVED("menu.editor.grid.moved"),

    /** Feedback that the item at {@code {slot}} was cleared from the grid. */
    MENU_GRID_CLEARED("menu.editor.grid.cleared"),

    /** Title of the confirm window the grid's shift-click-to-clear opens ({@code {slot}}). */
    MENU_GRID_CLEAR_CONFIRM("menu.editor.grid.clear-confirm"),

    /** Title of the per-item property editor opened from a grid cell ({@code {id}{slot}}). */
    MENU_ITEM_EDITOR_TITLE("menu.item-editor.title"),

    /** The item editor's value-lore wrapper around each property's current value ({@code {value}}). */
    MENU_ITEM_EDITOR_VALUE_LORE("menu.item-editor.value-lore"),

    /** Name of the item editor's back-to-grid button. */
    MENU_ITEM_EDITOR_BACK("menu.item-editor.back"),

    /** Label of the item editor's material field. */
    MENU_ITEM_EDITOR_MATERIAL("menu.item-editor.material"),

    /** Anvil prompt shown when the material field asks for a material token. */
    MENU_ITEM_EDITOR_MATERIAL_PROMPT("menu.item-editor.material-prompt"),

    /** Label of the item editor's capture-from-hand button. */
    MENU_ITEM_EDITOR_CAPTURE("menu.item-editor.capture"),

    /** Hint lore of the capture-from-hand button. */
    MENU_ITEM_EDITOR_CAPTURE_HINT("menu.item-editor.capture-hint"),

    /** Feedback that the held item was captured into the material field. */
    MENU_ITEM_EDITOR_CAPTURED("menu.item-editor.captured"),

    /** Feedback that the capture button was clicked with an empty hand. */
    MENU_ITEM_EDITOR_CAPTURE_EMPTY("menu.item-editor.capture-empty"),

    /** Label of the item editor's name field. */
    MENU_ITEM_EDITOR_NAME("menu.item-editor.name"),

    /** Anvil prompt shown when the name field asks for a display name. */
    MENU_ITEM_EDITOR_NAME_PROMPT("menu.item-editor.name-prompt"),

    /** Label of the item editor's lore-lines list field. */
    MENU_ITEM_EDITOR_LORE("menu.item-editor.lore"),

    /** Title of the lore-lines sub-menu. */
    MENU_ITEM_EDITOR_LORE_TITLE("menu.item-editor.lore.title"),

    /** Per-line button name in the lore sub-menu ({@code {entry}}). */
    MENU_ITEM_EDITOR_LORE_ENTRY_NAME("menu.item-editor.lore.entry-name"),

    /** Per-line action-hint lore in the lore sub-menu. */
    MENU_ITEM_EDITOR_LORE_ENTRY_HINTS("menu.item-editor.lore.entry-hints"),

    /** Name of the lore sub-menu's add button. */
    MENU_ITEM_EDITOR_LORE_ADD("menu.item-editor.lore.add"),

    /** Anvil prompt shown when adding a lore line. */
    MENU_ITEM_EDITOR_LORE_ADD_PROMPT("menu.item-editor.lore.add-prompt"),

    /** Anvil prompt shown when editing a lore line ({@code {entry}}). */
    MENU_ITEM_EDITOR_LORE_EDIT_PROMPT("menu.item-editor.lore.edit-prompt"),

    /** Confirm title shown before removing a lore line. */
    MENU_ITEM_EDITOR_LORE_REMOVE_CONFIRM("menu.item-editor.lore.remove-confirm"),

    /** Name of the lore sub-menu's back button. */
    MENU_ITEM_EDITOR_LORE_BACK("menu.item-editor.lore.back"),

    /** Label of the item editor's slot-assignment field. */
    MENU_ITEM_EDITOR_SLOTS("menu.item-editor.slots"),

    /** Anvil prompt shown when the slot field asks for slot tokens (e.g. {@code 0-2,8}). */
    MENU_ITEM_EDITOR_SLOTS_PROMPT("menu.item-editor.slots-prompt"),

    /** Label of the item editor's stack-amount field. */
    MENU_ITEM_EDITOR_AMOUNT("menu.item-editor.amount"),

    /** Label of the item editor's priority field. */
    MENU_ITEM_EDITOR_PRIORITY("menu.item-editor.priority"),

    /** Label of the item editor's custom-model-data field. */
    MENU_ITEM_EDITOR_MODEL_DATA("menu.item-editor.model-data"),

    /** Label of the item editor's glow toggle. */
    MENU_ITEM_EDITOR_GLOW("menu.item-editor.glow"),

    /** Label of the item editor's lore-mode selector. */
    MENU_ITEM_EDITOR_LORE_MODE("menu.item-editor.lore-mode"),

    /** Title of the lore-mode selector sub-menu. */
    MENU_ITEM_EDITOR_SELECT_LORE_MODE("menu.item-editor.select-lore-mode"),

    /** Label of the item editor's pagination-type selector. */
    MENU_ITEM_EDITOR_TYPE("menu.item-editor.type"),

    /** Title of the pagination-type selector sub-menu. */
    MENU_ITEM_EDITOR_SELECT_TYPE("menu.item-editor.select-type"),

    /** The on state of a toggle in the item editor. */
    MENU_ITEM_EDITOR_VALUE_ON("menu.item-editor.value-on"),

    /** The off state of a toggle in the item editor. */
    MENU_ITEM_EDITOR_VALUE_OFF("menu.item-editor.value-off"),

    /** Label of the hide-enchantments flag toggle. */
    MENU_ITEM_EDITOR_FLAG_HIDE_ENCHANTS("menu.item-editor.flag.hide-enchants"),

    /** Label of the hide-attributes flag toggle. */
    MENU_ITEM_EDITOR_FLAG_HIDE_ATTRIBUTES("menu.item-editor.flag.hide-attributes"),

    /** Label of the hide-unbreakable flag toggle. */
    MENU_ITEM_EDITOR_FLAG_HIDE_UNBREAKABLE("menu.item-editor.flag.hide-unbreakable"),

    /**
     * Label of the toggle that decides whether the client may write its own tooltip lines under the item's lore.
     * It replaced a HIDE_ADDITIONAL_TOOLTIP flag toggle: that flag is deprecated on Paper 26.2 and never covered
     * the components the client has gained since (dyed colour, equippable, trim, firework, tool, and the rest).
     */
    MENU_ITEM_EDITOR_HIDE_VANILLA_TOOLTIP("menu.item-editor.hide-vanilla-tooltip"),

    /** Label of the hide-dye flag toggle. */
    MENU_ITEM_EDITOR_FLAG_HIDE_DYE("menu.item-editor.flag.hide-dye"),

    /** Label of the hide-armor-trim flag toggle. */
    MENU_ITEM_EDITOR_FLAG_HIDE_ARMOR_TRIM("menu.item-editor.flag.hide-armor-trim"),

    /** Label of the hide-can-destroy flag toggle. */
    MENU_ITEM_EDITOR_FLAG_HIDE_DESTROYS("menu.item-editor.flag.hide-destroys"),

    /** Label of the hide-can-place-on flag toggle. */
    MENU_ITEM_EDITOR_FLAG_HIDE_PLACED_ON("menu.item-editor.flag.hide-placed-on"),

    /** Label of the item editor's click-actions row (opens the per-gesture action editor). */
    MENU_ACTION_EDITOR_CLICK_ACTIONS("menu.action-editor.click-actions"),

    /** Label of the item editor's requirements row (opens the view-requirement editor). */
    MENU_ACTION_EDITOR_REQUIREMENTS("menu.action-editor.requirements"),

    /** Title of the gesture-list picker the click-actions row opens. */
    MENU_ACTION_EDITOR_GESTURE_TITLE("menu.action-editor.gesture-title"),

    /** Name of one gesture button in the gesture-list picker ({@code {gesture}{count}}). */
    MENU_ACTION_EDITOR_GESTURE("menu.action-editor.gesture"),

    /** Name of a back button in the action/requirement pickers. */
    MENU_ACTION_EDITOR_BACK("menu.action-editor.back"),

    /** Title of a gesture's action ref-list ({@code {gesture}}). */
    MENU_ACTION_EDITOR_ACTIONS_TITLE("menu.action-editor.actions-title"),

    /** Name of one ref button in an action/condition/deny list ({@code {ref}}, shown as {@code id:value}). */
    MENU_ACTION_EDITOR_REF_NAME("menu.action-editor.ref-name"),

    /** Per-ref action-hint lore in a ref-list (move / edit / remove gestures). */
    MENU_ACTION_EDITOR_REF_HINTS("menu.action-editor.ref-hints"),

    /** Name of a ref-list's add button. */
    MENU_ACTION_EDITOR_ADD("menu.action-editor.add"),

    /** Confirm title shown before removing a ref from a list. */
    MENU_ACTION_EDITOR_REMOVE_CONFIRM("menu.action-editor.remove-confirm"),

    /** Title of the id picker (choose a registered action / condition id). */
    MENU_ACTION_EDITOR_PICK_TITLE("menu.action-editor.pick-title"),

    /** Name of one id button in the id picker ({@code {id}}). */
    MENU_ACTION_EDITOR_ID_NAME("menu.action-editor.id-name"),

    /** Anvil prompt shown when a ref asks for its value/argument ({@code {id}}). */
    MENU_ACTION_EDITOR_ARG_PROMPT("menu.action-editor.arg-prompt"),

    /** Title of the per-item view-requirement editor ({@code {id}}). */
    MENU_ACTION_EDITOR_REQ_TITLE("menu.action-editor.req-title"),

    /** Name of the requirement editor's back-to-item button. */
    MENU_ACTION_EDITOR_REQ_BACK("menu.action-editor.req-back"),

    /** Label of the requirement editor's conditions row (opens the condition ref-list). */
    MENU_ACTION_EDITOR_CONDITIONS("menu.action-editor.conditions"),

    /** Title of the view condition ref-list. */
    MENU_ACTION_EDITOR_CONDITIONS_TITLE("menu.action-editor.conditions-title"),

    /** Label of the requirement editor's minimum stepper. */
    MENU_ACTION_EDITOR_MINIMUM("menu.action-editor.minimum"),

    /** Label of the requirement editor's deny-actions row (opens the deny ref-list). */
    MENU_ACTION_EDITOR_DENY("menu.action-editor.deny"),

    /** Title of the view deny-action ref-list. */
    MENU_ACTION_EDITOR_DENY_TITLE("menu.action-editor.deny-title"),

    /** Name of the overview panel's delete button. */
    MENU_EDITOR_DELETE("menu.editor.delete"),

    /** Title of the confirm window the overview's delete button opens ({@code {name}}). */
    MENU_EDITOR_DELETE_CONFIRM("menu.editor.delete-confirm"),

    /** Confirmation that a blank menu named {@code {name}} was created. */
    MENU_EDITOR_CREATED("menu.editor.created"),

    /** Confirmation that {@code {from}} was duplicated to {@code {to}}. */
    MENU_EDITOR_DUPLICATED("menu.editor.duplicated"),

    /** Confirmation that {@code {from}} was renamed to {@code {to}}. */
    MENU_EDITOR_RENAMED("menu.editor.renamed"),

    /** Confirmation that the menu {@code {name}} was deleted. */
    MENU_EDITOR_DELETED("menu.editor.deleted"),

    /** Reply when {@code {name}} is not a safe menu file name. */
    MENU_EDITOR_NAME_INVALID("menu.editor.name-invalid"),

    /** Reply when {@code {name}} is a reserved (non-menu) config name. */
    MENU_EDITOR_NAME_RESERVED("menu.editor.reserved"),

    /** Reply when {@code {name}} already belongs to a menu. */
    MENU_EDITOR_NAME_TAKEN("menu.editor.name-taken"),

    /** Name of the overview panel's button that opens the menu-property editor. */
    MENU_EDITOR_PROPERTIES("menu.editor.properties"),

    /** Hint lore of the overview panel's menu-property button. */
    MENU_EDITOR_PROPERTIES_HINT("menu.editor.properties-hint"),

    /** Title of the menu-property editor ({@code {name}}). */
    MENU_PROPERTIES_TITLE("menu.properties.title"),

    /** The menu-property editor's value-lore wrapper around each property's current value ({@code {value}}). */
    MENU_PROPERTIES_VALUE_LORE("menu.properties.value-lore"),

    /** Name of the menu-property editor's back button. */
    MENU_PROPERTIES_BACK("menu.properties.back"),

    /** The on state of a toggle in the menu-property editor. */
    MENU_PROPERTIES_VALUE_ON("menu.properties.value-on"),

    /** The off state of a toggle in the menu-property editor. */
    MENU_PROPERTIES_VALUE_OFF("menu.properties.value-off"),

    /** Label of the menu-property editor's title field. */
    MENU_PROPERTIES_TITLE_FIELD("menu.properties.title-field"),

    /** Anvil prompt shown when the title field asks for a new menu title. */
    MENU_PROPERTIES_TITLE_PROMPT("menu.properties.title-prompt"),

    /** Label of the menu-property editor's row-count field. */
    MENU_PROPERTIES_ROWS("menu.properties.rows"),

    /** Label of the menu-property editor's inventory-type selector. */
    MENU_PROPERTIES_INVENTORY_TYPE("menu.properties.inventory-type"),

    /** Title of the inventory-type selector sub-menu. */
    MENU_PROPERTIES_SELECT_INVENTORY_TYPE("menu.properties.select-inventory-type"),

    /** Label of the menu-property editor's click-cooldown field (milliseconds). */
    MENU_PROPERTIES_CLICK_COOLDOWN("menu.properties.click-cooldown"),

    /** Label of the menu-property editor's chest-only toggle. */
    MENU_PROPERTIES_CHEST_ONLY("menu.properties.chest-only"),

    /** Label of the menu-property editor's bottom-inventory toggle. */
    MENU_PROPERTIES_BOTTOM_INVENTORY("menu.properties.bottom-inventory"),

    /** Label of the menu-property editor's open-requirement row (opens the condition ref-list). */
    MENU_PROPERTIES_OPEN_REQUIREMENT("menu.properties.open-requirement"),

    /** Title of the open-requirement condition ref-list. */
    MENU_PROPERTIES_OPEN_REQUIREMENT_TITLE("menu.properties.open-requirement-title"),

    /** Label of the menu-property editor's open-actions row (opens the action ref-list). */
    MENU_PROPERTIES_OPEN_ACTIONS("menu.properties.open-actions"),

    /** Title of the open-actions ref-list. */
    MENU_PROPERTIES_OPEN_ACTIONS_TITLE("menu.properties.open-actions-title"),

    /** Label of the menu-property editor's close-actions row (opens the action ref-list). */
    MENU_PROPERTIES_CLOSE_ACTIONS("menu.properties.close-actions"),

    /** Title of the close-actions ref-list. */
    MENU_PROPERTIES_CLOSE_ACTIONS_TITLE("menu.properties.close-actions-title"),

    /** Label of the menu-property editor's refresh-enabled toggle. */
    MENU_PROPERTIES_REFRESH("menu.properties.refresh"),

    /** Label of the menu-property editor's refresh-interval field (ticks). */
    MENU_PROPERTIES_REFRESH_INTERVAL("menu.properties.refresh-interval"),

    /** Label of the menu-property editor's open-command row (opens the command-block sub-editor). */
    MENU_PROPERTIES_OPEN_COMMAND("menu.properties.open-command"),

    /** Label of the menu-property editor's slot-grid button. */
    MENU_PROPERTIES_GRID("menu.properties.grid"),

    /** Hint lore of the menu-property editor's slot-grid button. */
    MENU_PROPERTIES_GRID_HINT("menu.properties.grid-hint"),

    /** Label of the menu-property editor's save button. */
    MENU_PROPERTIES_SAVE("menu.properties.save"),

    /** Hint lore of the menu-property editor's save button. */
    MENU_PROPERTIES_SAVE_HINT("menu.properties.save-hint"),

    /** Name of the menu-property editor's delete button. */
    MENU_PROPERTIES_DELETE("menu.properties.delete"),

    /** Title of the confirm window the menu-property editor's delete button opens ({@code {name}}). */
    MENU_PROPERTIES_DELETE_CONFIRM("menu.properties.delete-confirm"),

    /** Title of the open-command sub-editor ({@code {name}}). */
    MENU_COMMAND_TITLE("menu.command.title"),

    /** Name of the open-command sub-editor's back button. */
    MENU_COMMAND_BACK("menu.command.back"),

    /** Label of the command sub-editor's enabled toggle (adds a command block, or clears it). */
    MENU_COMMAND_ENABLED("menu.command.enabled"),

    /** Label of the command sub-editor's command-name field. */
    MENU_COMMAND_NAME("menu.command.name"),

    /** Anvil prompt shown when the command-name field asks for a command word. */
    MENU_COMMAND_NAME_PROMPT("menu.command.name-prompt"),

    /** Label of the command sub-editor's aliases list field. */
    MENU_COMMAND_ALIASES("menu.command.aliases"),

    /** Title of the command aliases sub-menu. */
    MENU_COMMAND_ALIASES_TITLE("menu.command.aliases.title"),

    /** Per-alias button name in the aliases sub-menu ({@code {entry}}). */
    MENU_COMMAND_ALIASES_ENTRY_NAME("menu.command.aliases.entry-name"),

    /** Per-alias action-hint lore in the aliases sub-menu. */
    MENU_COMMAND_ALIASES_ENTRY_HINTS("menu.command.aliases.entry-hints"),

    /** Name of the aliases sub-menu's add button. */
    MENU_COMMAND_ALIASES_ADD("menu.command.aliases.add"),

    /** Anvil prompt shown when adding an alias. */
    MENU_COMMAND_ALIASES_ADD_PROMPT("menu.command.aliases.add-prompt"),

    /** Anvil prompt shown when editing an alias ({@code {entry}}). */
    MENU_COMMAND_ALIASES_EDIT_PROMPT("menu.command.aliases.edit-prompt"),

    /** Confirm title shown before removing an alias. */
    MENU_COMMAND_ALIASES_REMOVE_CONFIRM("menu.command.aliases.remove-confirm"),

    /** Name of the aliases sub-menu's back button. */
    MENU_COMMAND_ALIASES_BACK("menu.command.aliases.back"),

    /** Label of the command sub-editor's permission field. */
    MENU_COMMAND_PERMISSION("menu.command.permission"),

    /** Anvil prompt shown when the permission field asks for a node. */
    MENU_COMMAND_PERMISSION_PROMPT("menu.command.permission-prompt"),

    /** Label of the command sub-editor's deny-message field. */
    MENU_COMMAND_DENY_MESSAGE("menu.command.deny-message"),

    /** Anvil prompt shown when the deny-message field asks for a line. */
    MENU_COMMAND_DENY_MESSAGE_PROMPT("menu.command.deny-message-prompt"),

    /** Label of the command sub-editor's console-allowed toggle. */
    MENU_COMMAND_CONSOLE("menu.command.console"),

    /** Label of the command sub-editor's usage field. */
    MENU_COMMAND_USAGE("menu.command.usage"),

    /** Anvil prompt shown when the usage field asks for a usage line. */
    MENU_COMMAND_USAGE_PROMPT("menu.command.usage-prompt"),

    /** Name of the grid canvas's live-preview control button. */
    MENU_GRID_PREVIEW("menu.editor.grid.preview"),

    /** Reply when a viewer opens a menu another operator already has open in the editor ({@code {player}}). */
    MENU_EDITOR_LOCKED("menu.editor.locked"),

    /** Confirmation that the held item was captured into {@code {slot}}, the {@code /menu captureitem} command. */
    MENU_CAPTURE_CAPTURED("menu.capture.captured"),

    /** Reply for {@code /menu captureitem} when the sender is not holding an item. */
    MENU_CAPTURE_EMPTY_HAND("menu.capture.empty-hand"),

    /** Reply for {@code /menu captureitem} when the sender has no menu open in the slot-grid editor. */
    MENU_CAPTURE_NO_SESSION("menu.capture.no-session"),

    /** Reply for {@code /menu captureitem} when {@code {slot}} is past the menu's {@code {max}} slots. */
    MENU_CAPTURE_BAD_SLOT("menu.capture.bad-slot");

    private final String key;

    CustomMenusMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}

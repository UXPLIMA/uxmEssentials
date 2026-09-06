package com.uxplima.uxmessentials.itemworld.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The itemworld context's user-visible message keys. Each constant maps 1:1 to a kebab-case catalog key in
 * {@code messages_<lang>.conf} ({@code GIVE_GIVEN} ↔ {@code itemworld.give.given}); the constant is the
 * compile-time handle, the catalog holds the text. There are no inline player-facing literals anywhere in the
 * context: every message resolves through one of these.
 *
 * <p>The keys cover the full ~40-verb surface grouped by sub-feature group (docs/10-feature-modules.md
 * §15.10): item utils, virtual workstations, cleanup, powertool, mob/entity, time/weather (and its aliases),
 * and admin-fun, plus the shared validation failures the adapter boundary surfaces before a domain call
 * (unknown item, amount over cap, bad enchant level, bad time/weather, unknown mob). Per the i18n contract, a
 * disabled module still ships its keys so the catalog stays whole and the locale-parity guard sees the full
 * {@code en} key set.
 */
public enum ItemworldMessageKey implements MessageKey {

    // Item utils, /give, /i
    GIVE_GIVEN("itemworld.give.given"),
    GIVE_RECEIVED("itemworld.give.received"),
    // /giveall
    GIVEALL_DONE("itemworld.giveall.done"),
    // /item
    ITEM_GIVEN("itemworld.item.given"),
    // /itemname
    ITEMNAME_SET("itemworld.itemname.set"),
    ITEMNAME_CLEARED("itemworld.itemname.cleared"),
    // /itemlore
    ITEMLORE_SET("itemworld.itemlore.set"),
    ITEMLORE_ADDED("itemworld.itemlore.added"),
    ITEMLORE_CLEARED("itemworld.itemlore.cleared"),
    // /itemedit, the held-item editor (name + lore)
    ITEMEDIT_NAME_SET("itemworld.itemedit.name-set"),
    ITEMEDIT_NAME_RESET("itemworld.itemedit.name-reset"),
    ITEMEDIT_LORE_ADDED("itemworld.itemedit.lore-added"),
    ITEMEDIT_LORE_SET("itemworld.itemedit.lore-set"),
    ITEMEDIT_LORE_INSERTED("itemworld.itemedit.lore-inserted"),
    ITEMEDIT_LORE_REMOVED("itemworld.itemedit.lore-removed"),
    ITEMEDIT_LORE_CLEARED("itemworld.itemedit.lore-cleared"),
    ITEMEDIT_LORE_OUT_OF_RANGE("itemworld.itemedit.lore-out-of-range"),
    // /itemedit. Advanced editor (enchant, flags, attributes, durability, custom model data)
    ITEMEDIT_ENCHANTED("itemworld.itemedit.enchanted"),
    ITEMEDIT_ENCHANT_REMOVED("itemworld.itemedit.enchant-removed"),
    ITEMEDIT_ENCHANT_UNKNOWN("itemworld.itemedit.enchant-unknown"),
    ITEMEDIT_ENCHANT_CLAMPED("itemworld.itemedit.enchant-clamped"),
    ITEMEDIT_ENCHANT_NOT_PRESENT("itemworld.itemedit.enchant-not-present"),
    ITEMEDIT_FLAG_TOGGLED("itemworld.itemedit.flag-toggled"),
    ITEMEDIT_FLAG_UNKNOWN("itemworld.itemedit.flag-unknown"),
    ITEMEDIT_ATTRIBUTE_ADDED("itemworld.itemedit.attribute-added"),
    ITEMEDIT_ATTRIBUTE_REMOVED("itemworld.itemedit.attribute-removed"),
    ITEMEDIT_ATTRIBUTE_UNKNOWN("itemworld.itemedit.attribute-unknown"),
    ITEMEDIT_ATTRIBUTE_BAD_SLOT("itemworld.itemedit.attribute-bad-slot"),
    ITEMEDIT_DURABILITY_SET("itemworld.itemedit.durability-set"),
    ITEMEDIT_DURABILITY_NOT_DAMAGEABLE("itemworld.itemedit.durability-not-damageable"),
    ITEMEDIT_DURABILITY_OUT_OF_RANGE("itemworld.itemedit.durability-out-of-range"),
    ITEMEDIT_REPAIRED("itemworld.itemedit.repaired"),
    ITEMEDIT_REPAIR_NOTHING("itemworld.itemedit.repair-nothing"),
    ITEMEDIT_UNBREAKABLE_SET("itemworld.itemedit.unbreakable-set"),
    ITEMEDIT_MODEL_SET("itemworld.itemedit.model-set"),
    ITEMEDIT_MODEL_CLEARED("itemworld.itemedit.model-cleared"),
    // /itemedit: the click-driven GUI editor (bare /itemedit): the panel chrome (title + one label/hint per button),
    // the input prompts, the enchant/flag picker chrome, and its own graceful-exit / bad-input feedback. An applied
    // edit reuses the ITEMEDIT_* keys above, so a click reports exactly what the matching subcommand does.
    ITEMEDIT_GUI_TITLE("itemworld.gui.itemedit.title"),
    ITEMEDIT_GUI_RENAME_NAME("itemworld.gui.itemedit.rename.name"),
    ITEMEDIT_GUI_RENAME_HINT("itemworld.gui.itemedit.rename.hint"),
    ITEMEDIT_GUI_LORE_NAME("itemworld.gui.itemedit.lore.name"),
    ITEMEDIT_GUI_LORE_HINT("itemworld.gui.itemedit.lore.hint"),
    ITEMEDIT_GUI_ENCHANT_NAME("itemworld.gui.itemedit.enchant.name"),
    ITEMEDIT_GUI_ENCHANT_HINT("itemworld.gui.itemedit.enchant.hint"),
    ITEMEDIT_GUI_FLAGS_NAME("itemworld.gui.itemedit.flags.name"),
    ITEMEDIT_GUI_FLAGS_HINT("itemworld.gui.itemedit.flags.hint"),
    ITEMEDIT_GUI_UNBREAKABLE_NAME("itemworld.gui.itemedit.unbreakable.name"),
    ITEMEDIT_GUI_UNBREAKABLE_HINT("itemworld.gui.itemedit.unbreakable.hint"),
    ITEMEDIT_GUI_MODEL_NAME("itemworld.gui.itemedit.model.name"),
    ITEMEDIT_GUI_MODEL_HINT("itemworld.gui.itemedit.model.hint"),
    ITEMEDIT_GUI_DURABILITY_NAME("itemworld.gui.itemedit.durability.name"),
    ITEMEDIT_GUI_DURABILITY_HINT("itemworld.gui.itemedit.durability.hint"),
    ITEMEDIT_GUI_CLOSE("itemworld.gui.itemedit.close"),
    ITEMEDIT_GUI_RENAME_PROMPT("itemworld.gui.itemedit.rename-prompt"),
    ITEMEDIT_GUI_LORE_ADD_PROMPT("itemworld.gui.itemedit.lore-add-prompt"),
    ITEMEDIT_GUI_LORE_REMOVE_PROMPT("itemworld.gui.itemedit.lore-remove-prompt"),
    ITEMEDIT_GUI_ENCHANT_LEVEL_PROMPT("itemworld.gui.itemedit.enchant-level-prompt"),
    ITEMEDIT_GUI_MODEL_PROMPT("itemworld.gui.itemedit.model-prompt"),
    ITEMEDIT_GUI_DURABILITY_PROMPT("itemworld.gui.itemedit.durability-prompt"),
    ITEMEDIT_GUI_ENCHANT_ADD_TITLE("itemworld.gui.itemedit.enchant-add-title"),
    ITEMEDIT_GUI_ENCHANT_ADD_ENTRY("itemworld.gui.itemedit.enchant-add-entry"),
    ITEMEDIT_GUI_ENCHANT_REMOVE_TITLE("itemworld.gui.itemedit.enchant-remove-title"),
    ITEMEDIT_GUI_ENCHANT_REMOVE_ENTRY("itemworld.gui.itemedit.enchant-remove-entry"),
    ITEMEDIT_GUI_FLAGS_TITLE("itemworld.gui.itemedit.flags-title"),
    ITEMEDIT_GUI_FLAG_ENTRY("itemworld.gui.itemedit.flag-entry"),
    ITEMEDIT_GUI_FLAG_STATE("itemworld.gui.itemedit.flag-state"),
    ITEMEDIT_GUI_SELECTOR_BACK("itemworld.gui.itemedit.selector-back"),
    ITEMEDIT_GUI_VALUE_ON("itemworld.gui.itemedit.value-on"),
    ITEMEDIT_GUI_VALUE_OFF("itemworld.gui.itemedit.value-off"),
    ITEMEDIT_GUI_ITEM_CHANGED("itemworld.gui.itemedit.item-changed"),
    ITEMEDIT_GUI_INVALID_NUMBER("itemworld.gui.itemedit.invalid-number"),
    ITEMEDIT_GUI_NO_ENCHANTS("itemworld.gui.itemedit.no-enchants"),
    // /itemflag
    ITEMFLAG_TOGGLED("itemworld.itemflag.toggled"),
    ITEMFLAG_UNKNOWN("itemworld.itemflag.unknown"),
    // /skull
    SKULL_GIVEN("itemworld.skull.given"),
    // /firework
    FIREWORK_STYLED("itemworld.firework.styled"),
    FIREWORK_POWER_SET("itemworld.firework.power-set"),
    FIREWORK_CLEARED("itemworld.firework.cleared"),
    NOT_A_FIREWORK("itemworld.firework.not-a-firework"),
    // /potion
    POTION_EFFECT_ADDED("itemworld.potion.effect-added"),
    POTION_UNKNOWN_EFFECT("itemworld.potion.unknown-effect"),
    NOT_A_POTION("itemworld.potion.not-a-potion"),
    // /book
    BOOK_UNLOCKED("itemworld.book.unlocked"),
    NOT_A_WRITTEN_BOOK("itemworld.book.not-a-written-book"),
    // /more
    MORE_FILLED("itemworld.more.filled"),
    MORE_ALREADY_FULL("itemworld.more.already-full"),
    // /itemamount
    ITEMAMOUNT_SET("itemworld.itemamount.set"),
    // /itemdamage
    ITEMDAMAGE_SET("itemworld.itemdamage.set"),
    ITEMDAMAGE_NOT_DAMAGEABLE("itemworld.itemdamage.not-damageable"),
    ITEMDAMAGE_OUT_OF_RANGE("itemworld.itemdamage.out-of-range"),
    // /repair, /repairall
    REPAIR_DONE("itemworld.repair.done"),
    REPAIR_NOTHING("itemworld.repair.nothing"),
    REPAIRALL_DONE("itemworld.repairall.done"),
    // /enchant
    ENCHANT_APPLIED("itemworld.enchant.applied"),
    ENCHANT_UNKNOWN("itemworld.enchant.unknown"),
    ENCHANT_LEVEL_CLAMPED("itemworld.enchant.level-clamped"),
    // /hat
    HAT_WORN("itemworld.hat.worn"),
    HAT_NO_ITEM("itemworld.hat.no-item"),
    // /itemdb
    ITEMDB_REPORT("itemworld.itemdb.report"),
    // /showitem
    SHOWITEM_BROADCAST("itemworld.showitem.broadcast"),
    SHOWITEM_SHOWN("itemworld.showitem.shown"),
    // /recipe
    RECIPE_SHAPED("itemworld.recipe.shaped"),
    RECIPE_SHAPELESS("itemworld.recipe.shapeless"),
    RECIPE_NONE("itemworld.recipe.none"),
    // /recipe, the crafting-grid browse menu (bare /recipe when its gui flag is on)
    RECIPE_GUI_TITLE("itemworld.recipe.gui.title"),
    RECIPE_GUI_RESULT_NAME("itemworld.recipe.gui.result-name"),
    RECIPE_GUI_RESULT_LORE("itemworld.recipe.gui.result-lore"),
    RECIPE_GUI_INGREDIENT_LORE("itemworld.recipe.gui.ingredient-lore"),
    RECIPE_GUI_EMPTY_NAME("itemworld.recipe.gui.empty-name"),
    RECIPE_GUI_NONE_TITLE("itemworld.recipe.gui.none-title"),
    // /unbreakable
    UNBREAKABLE_SET("itemworld.unbreakable.set"),
    // /disenchant
    DISENCHANT_ONE("itemworld.disenchant.one"),
    DISENCHANT_ALL("itemworld.disenchant.all"),
    DISENCHANT_NONE("itemworld.disenchant.none"),
    DISENCHANT_NOT_PRESENT("itemworld.disenchant.not-present"),
    // /itemmodel
    ITEMMODEL_SET("itemworld.itemmodel.set"),
    ITEMMODEL_CLEARED("itemworld.itemmodel.cleared"),
    // /editsign
    EDITSIGN_OPENED("itemworld.editsign.opened"),
    EDITSIGN_NOT_A_SIGN("itemworld.editsign.not-a-sign"),
    EDITSIGN_NO_ACCESS("itemworld.editsign.no-access"),
    // /iteminfo
    ITEMINFO_HEADER("itemworld.iteminfo.header"),
    ITEMINFO_LINE("itemworld.iteminfo.line"),

    // Virtual workstations. One opened line, parameterised by station name; the others form names the target
    WORKSTATION_OPENED("itemworld.workstation.opened"),
    WORKSTATION_OPENED_FOR("itemworld.workstation.opened-for"),

    // Shulkers, open a shulker box from the inventory. The view's title (no chat line: the GUI is the feedback).
    SHULKER_TITLE("itemworld.shulker.title"),

    // Cleanup, /disposal
    DISPOSAL_OPENED("itemworld.disposal.opened"),
    DISPOSAL_TITLE("itemworld.disposal.title"),
    // /condense
    CONDENSE_DONE("itemworld.condense.done"),
    CONDENSE_NOTHING("itemworld.condense.nothing"),

    // Powertool, /powertool
    POWERTOOL_BOUND("itemworld.powertool.bound"),
    POWERTOOL_CLEARED("itemworld.powertool.cleared"),
    POWERTOOL_NO_ITEM("itemworld.powertool.no-item"),
    // /powertooltoggle
    POWERTOOL_ENABLED("itemworld.powertool.enabled"),
    POWERTOOL_DISABLED("itemworld.powertool.disabled"),
    // /powertoollist
    POWERTOOL_LIST_HEADER("itemworld.powertool.list-header"),
    POWERTOOL_LIST_ENTRY("itemworld.powertool.list-entry"),
    POWERTOOL_LIST_EMPTY("itemworld.powertool.list-empty"),

    // Mob & entity, /spawnmob
    SPAWNMOB_SPAWNED("itemworld.spawnmob.spawned"),
    SPAWNMOB_UNKNOWN("itemworld.spawnmob.unknown"),
    // /spawner
    SPAWNER_SET("itemworld.spawner.set"),
    SPAWNER_NOT_A_SPAWNER("itemworld.spawner.not-a-spawner"),
    // /kill
    KILL_DONE("itemworld.kill.done"),
    // /butcher
    BUTCHER_DONE("itemworld.butcher.done"),
    // /killall
    KILLALL_DONE("itemworld.killall.done"),
    // /remove
    REMOVE_DONE("itemworld.remove.done"),
    // /entitycount
    ENTITYCOUNT_HEADER("itemworld.entitycount.header"),
    ENTITYCOUNT_ENTRY("itemworld.entitycount.entry"),
    ENTITYCOUNT_NONE("itemworld.entitycount.none"),
    // /entitycount, the tally browse menu (bare /entitycount when its gui flag is on)
    ENTITYCOUNT_GUI_TITLE("itemworld.entitycount.gui.title"),
    ENTITYCOUNT_GUI_PREV("itemworld.entitycount.gui.prev"),
    ENTITYCOUNT_GUI_NEXT("itemworld.entitycount.gui.next"),
    ENTITYCOUNT_GUI_ENTRY_NAME("itemworld.entitycount.gui.entry-name"),
    ENTITYCOUNT_GUI_ENTRY_LORE("itemworld.entitycount.gui.entry-lore"),
    ENTITYCOUNT_GUI_EMPTY_TITLE("itemworld.entitycount.gui.empty-title"),
    // /unlimited
    UNLIMITED_ENABLED("itemworld.unlimited.enabled"),
    UNLIMITED_DISABLED("itemworld.unlimited.disabled"),

    // Time & weather, /time, /day, /night
    TIME_SET("itemworld.time.set"),
    // /weather, /sun, /rain, /thunder
    WEATHER_SET("itemworld.weather.set"),

    // Admin-fun, /lightning. The self/look form has no target player, so it gets its own wording.
    LIGHTNING_STRUCK("itemworld.lightning.struck"),
    LIGHTNING_STRUCK_SELF("itemworld.lightning.struck-self"),
    // /nuke: same split: the self/look form names no target.
    NUKE_DONE("itemworld.nuke.done"),
    NUKE_DONE_SELF("itemworld.nuke.done-self"),
    // /fireball
    FIREBALL_LAUNCHED("itemworld.fireball.launched"),
    // /kittycannon
    KITTYCANNON_FIRED("itemworld.kittycannon.fired"),
    // /antioch
    ANTIOCH_THROWN("itemworld.antioch.thrown"),
    // /beezooka
    BEEZOOKA_FIRED("itemworld.beezooka.fired"),
    // /enderclear
    ENDERCLEAR_DONE("itemworld.enderclear.done"),
    ENDERCLEAR_DONE_OTHER("itemworld.enderclear.done-other"),
    // /copyinv
    COPYINV_DONE("itemworld.copyinv.done"),
    // /endercopy
    ENDERCOPY_DONE("itemworld.endercopy.done"),
    // /break
    BREAK_DONE("itemworld.break.done"),
    BREAK_NO_TARGET("itemworld.break.no-target"),
    // /tree
    TREE_SPAWNED("itemworld.tree.spawned"),
    TREE_FAILED("itemworld.tree.failed"),
    TREE_NO_TARGET("itemworld.tree.no-target"),
    TREE_UNKNOWN_TYPE("itemworld.tree.unknown-type"),

    // Utilities hub, /itemworld gui. The launcher chrome (title, button lore, back) plus one label per button.
    GUI_HUB_TITLE("itemworld.gui.hub.title"),
    GUI_HUB_VALUE_LORE("itemworld.gui.hub.value-lore"),
    GUI_HUB_BACK("itemworld.gui.hub.back"),
    GUI_HUB_WORKSTATION("itemworld.gui.hub.workstation"),
    GUI_HUB_TIME_DAY("itemworld.gui.hub.time-day"),
    GUI_HUB_TIME_NIGHT("itemworld.gui.hub.time-night"),
    GUI_HUB_WEATHER_CLEAR("itemworld.gui.hub.weather-clear"),
    GUI_HUB_WEATHER_RAIN("itemworld.gui.hub.weather-rain"),
    GUI_HUB_CLEAR_DROPS("itemworld.gui.hub.clear-drops"),
    GUI_HUB_CLEAR_MOBS("itemworld.gui.hub.clear-mobs"),

    // Shared validation failures surfaced at the adapter boundary before a domain call
    UNKNOWN_ITEM("itemworld.unknown-item"),
    AMOUNT_OUT_OF_RANGE("itemworld.amount-out-of-range"),
    BAD_TIME("itemworld.bad-time"),
    BAD_WEATHER("itemworld.bad-weather"),
    UNKNOWN_TARGET("itemworld.unknown-target"),
    NO_ITEM_IN_HAND("itemworld.no-item-in-hand"),
    COMMAND_DISABLED("itemworld.command-disabled");

    private final String key;

    ItemworldMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}

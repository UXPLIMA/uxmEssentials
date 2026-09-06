package com.uxplima.uxmessentials.itemworld.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import com.uxplima.uxmessentials.itemworld.domain.SubFeatureGroup;
import com.uxplima.uxmessentials.shared.application.module.BrigadierCommand;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;

/**
 * The itemworld context's full command surface (docs/10-feature-modules.md §15.10, docs/permissions.md
 * §Itemworld) as platform-neutral {@link CommandSpec}s, the authoritative {@code literal → permission node →
 * sub-feature group} table the inbound adapter realises into Brigadier nodes on the next {@code COMMANDS}
 * fire. Collected here so the command/permission/group pairing is one greppable table that matches {@code
 * paper-plugin.yml} and the permissions reference, and so the module can resolve a command's owning group for
 * the per-sub-feature-group + per-command disable gate ({@link ItemworldConfig#commandEnabled}).
 *
 * <p>Every verb in the ~40-verb surface is present and none is dropped. The {@code /repair}, {@code
 * /repairall}, {@code /hat} and {@code /more} verbs overlap the playerstate context by name but are owned
 * here (playerstate deferred them, §15.6); they are registered here and the two modules do not
 * double-register. Abusable verbs (bulk {@code /give}, {@code /spawnmob}, {@code /spawner}, the entity-purge
 * family, the admin-fun verbs) are audit-logged in their use cases; all inputs are validated at the adapter
 * boundary before a domain call.
 */
final class ItemworldCommandSurface {

    private ItemworldCommandSurface() {}

    private static final List<Verb> VERBS = buildVerbs();

    /** Every itemworld command as a {@link CommandSpec}, in surface order grouped by sub-feature group. */
    static List<CommandSpec> all() {
        List<CommandSpec> specs = new ArrayList<>(VERBS.size());
        for (Verb verb : VERBS) {
            specs.add(verb.toSpec());
        }
        return List.copyOf(specs);
    }

    /** The owning sub-feature group of a command literal, for the per-group + per-command disable gate. */
    static Optional<SubFeatureGroup> groupOf(String literal) {
        Objects.requireNonNull(literal, "literal");
        for (Verb verb : VERBS) {
            if (verb.literal().equals(literal)) {
                return Optional.of(verb.group());
            }
        }
        return Optional.empty();
    }

    /** The {@code literal → owning group} map, exposed for the module's disable-gate wiring and tests. */
    static Map<String, SubFeatureGroup> groupsByLiteral() {
        Map<String, SubFeatureGroup> map = new java.util.LinkedHashMap<>();
        for (Verb verb : VERBS) {
            map.put(verb.literal(), verb.group());
        }
        return Map.copyOf(map);
    }

    private static List<Verb> buildVerbs() {
        List<Verb> verbs = new ArrayList<>();
        // Item utils. /give (/i is the adapter alias of /give), /item, cosmetics, /more, repair, /enchant, /hat,
        // /itemdb.
        verbs.add(new Verb("give", "uxmessentials.give.use", SubFeatureGroup.ITEM_UTILS, "Give an item to a player"));
        verbs.add(new Verb(
                "giveall",
                "uxmessentials.giveall.use",
                SubFeatureGroup.ITEM_UTILS,
                "Give an item to every online player"));
        verbs.add(new Verb("item", "uxmessentials.item.use", SubFeatureGroup.ITEM_UTILS, "Give yourself an item"));
        verbs.add(
                new Verb("itemname", "uxmessentials.itemname.use", SubFeatureGroup.ITEM_UTILS, "Rename the held item"));
        verbs.add(
                new Verb("itemlore", "uxmessentials.itemlore.use", SubFeatureGroup.ITEM_UTILS, "Edit held-item lore"));
        verbs.add(new Verb(
                "itemflag", "uxmessentials.itemflag.use", SubFeatureGroup.ITEM_UTILS, "Toggle an item meta flag"));
        verbs.add(new Verb("skull", "uxmessentials.skull.use", SubFeatureGroup.ITEM_UTILS, "Get a player-head skull"));
        verbs.add(new Verb(
                "firework",
                "uxmessentials.firework.use",
                SubFeatureGroup.ITEM_UTILS,
                "Style or power the held firework rocket"));
        verbs.add(new Verb(
                "potion",
                "uxmessentials.potion.use",
                SubFeatureGroup.ITEM_UTILS,
                "Add a potion effect to the held potion"));
        verbs.add(new Verb(
                "book", "uxmessentials.book.use", SubFeatureGroup.ITEM_UTILS, "Unlock a written book for editing"));
        verbs.add(new Verb("more", "uxmessentials.more.use", SubFeatureGroup.ITEM_UTILS, "Fill the held stack to max"));
        verbs.add(new Verb(
                "itemamount",
                "uxmessentials.itemamount.use",
                SubFeatureGroup.ITEM_UTILS,
                "Set the held stack's amount"));
        verbs.add(new Verb(
                "itemdamage",
                "uxmessentials.itemdamage.use",
                SubFeatureGroup.ITEM_UTILS,
                "Set the held item's durability damage"));
        verbs.add(new Verb(
                "repair", "uxmessentials.repair.itemworld", SubFeatureGroup.ITEM_UTILS, "Repair the held item"));
        verbs.add(new Verb(
                "repairall",
                "uxmessentials.repair.itemworld",
                SubFeatureGroup.ITEM_UTILS,
                "Repair the whole inventory"));
        verbs.add(
                new Verb("enchant", "uxmessentials.enchant.use", SubFeatureGroup.ITEM_UTILS, "Enchant the held item"));
        verbs.add(new Verb("hat", "uxmessentials.hat.use", SubFeatureGroup.ITEM_UTILS, "Wear the held item as a hat"));
        verbs.add(new Verb("itemdb", "uxmessentials.itemdb.use", SubFeatureGroup.ITEM_UTILS, "Look up an item's id"));
        verbs.add(new Verb(
                "showitem",
                "uxmessentials.showitem.use",
                SubFeatureGroup.ITEM_UTILS,
                "Broadcast the held item to chat"));
        verbs.add(new Verb(
                "recipe", "uxmessentials.recipe.use", SubFeatureGroup.ITEM_UTILS, "Show an item's crafting recipe"));
        verbs.add(new Verb(
                "unbreakable",
                "uxmessentials.unbreakable.use",
                SubFeatureGroup.ITEM_UTILS,
                "Toggle the held item's unbreakable flag"));
        verbs.add(new Verb(
                "disenchant",
                "uxmessentials.disenchant.use",
                SubFeatureGroup.ITEM_UTILS,
                "Remove enchantments from the held item"));
        verbs.add(new Verb(
                "itemmodel",
                "uxmessentials.itemmodel.use",
                SubFeatureGroup.ITEM_UTILS,
                "Set the held item's custom model data"));
        verbs.add(new Verb(
                "editsign",
                "uxmessentials.editsign.use",
                SubFeatureGroup.ITEM_UTILS,
                "Edit the sign you are looking at"));
        verbs.add(new Verb(
                "iteminfo",
                "uxmessentials.iteminfo.use",
                SubFeatureGroup.ITEM_UTILS,
                "Inspect the item you are holding"));
        verbs.add(new Verb(
                "copyinv",
                "uxmessentials.copyinv.use",
                SubFeatureGroup.ITEM_UTILS,
                "Copy a player's inventory into yours"));
        verbs.add(new Verb(
                "endercopy",
                "uxmessentials.endercopy.use",
                SubFeatureGroup.ITEM_UTILS,
                "Copy a player's ender chest into yours"));
        // Virtual workstations.
        verbs.add(new Verb(
                "anvil", "uxmessentials.workstation.anvil", SubFeatureGroup.WORKSTATIONS, "Open a virtual anvil"));
        verbs.add(new Verb(
                "workbench",
                "uxmessentials.workstation.workbench",
                SubFeatureGroup.WORKSTATIONS,
                "Open a virtual crafting table"));
        verbs.add(new Verb(
                "enderchest",
                "uxmessentials.workstation.enderchest",
                SubFeatureGroup.WORKSTATIONS,
                "Open your ender chest"));
        verbs.add(new Verb(
                "grindstone",
                "uxmessentials.workstation.grindstone",
                SubFeatureGroup.WORKSTATIONS,
                "Open a virtual grindstone"));
        verbs.add(new Verb(
                "cartography",
                "uxmessentials.workstation.cartography",
                SubFeatureGroup.WORKSTATIONS,
                "Open a virtual cartography table"));
        verbs.add(new Verb(
                "loom", "uxmessentials.workstation.loom", SubFeatureGroup.WORKSTATIONS, "Open a virtual loom"));
        verbs.add(new Verb(
                "smithingtable",
                "uxmessentials.workstation.smithingtable",
                SubFeatureGroup.WORKSTATIONS,
                "Open a virtual smithing table"));
        verbs.add(new Verb(
                "stonecutter",
                "uxmessentials.workstation.stonecutter",
                SubFeatureGroup.WORKSTATIONS,
                "Open a virtual stonecutter"));
        verbs.add(new Verb(
                "furnace",
                "uxmessentials.workstation.furnace",
                SubFeatureGroup.WORKSTATIONS,
                "Open a virtual furnace"));
        // Cleanup.
        verbs.add(new Verb("disposal", "uxmessentials.disposal.use", SubFeatureGroup.CLEANUP, "Open a throwaway GUI"));
        verbs.add(new Verb(
                "condense", "uxmessentials.condense.use", SubFeatureGroup.CLEANUP, "Recipe-stack inventory items"));
        verbs.add(new Verb(
                "enderclear", "uxmessentials.enderclear.use", SubFeatureGroup.CLEANUP, "Clear an ender chest"));
        // Powertool.
        verbs.add(new Verb(
                "powertool",
                "uxmessentials.powertool.use",
                SubFeatureGroup.POWERTOOL,
                "Bind a command to the held item"));
        verbs.add(new Verb(
                "powertooltoggle",
                "uxmessentials.powertool.toggle",
                SubFeatureGroup.POWERTOOL,
                "Toggle your powertool bindings"));
        verbs.add(new Verb(
                "powertoollist",
                "uxmessentials.powertool.use",
                SubFeatureGroup.POWERTOOL,
                "List the held item's powertool bindings"));
        // Mob & entity.
        verbs.add(new Verb("spawnmob", "uxmessentials.spawnmob.use", SubFeatureGroup.MOB_ENTITY, "Spawn mobs"));
        verbs.add(new Verb(
                "spawner", "uxmessentials.spawner.use", SubFeatureGroup.MOB_ENTITY, "Set a spawner's mob type"));
        verbs.add(new Verb("kill", "uxmessentials.kill.use", SubFeatureGroup.MOB_ENTITY, "Kill a target"));
        verbs.add(new Verb("butcher", "uxmessentials.butcher.use", SubFeatureGroup.MOB_ENTITY, "Purge nearby mobs"));
        verbs.add(new Verb(
                "killall", "uxmessentials.killall.use", SubFeatureGroup.MOB_ENTITY, "Purge entities world-wide"));
        verbs.add(
                new Verb("remove", "uxmessentials.remove.use", SubFeatureGroup.MOB_ENTITY, "Remove entities by type"));
        verbs.add(new Verb(
                "entitycount",
                "uxmessentials.entitycount.use",
                SubFeatureGroup.MOB_ENTITY,
                "Count nearby entities by type"));
        verbs.add(new Verb(
                "unlimited",
                "uxmessentials.unlimited.use",
                SubFeatureGroup.MOB_ENTITY,
                "Toggle unlimited block placement"));
        // Time & weather (core verbs plus the alias verbs in the same group).
        verbs.add(new Verb("time", "uxmessentials.time.use", SubFeatureGroup.TIME_WEATHER, "Set or add world time"));
        verbs.add(new Verb("weather", "uxmessentials.weather.use", SubFeatureGroup.TIME_WEATHER, "Set the weather"));
        verbs.add(new Verb("day", "uxmessentials.time.alias", SubFeatureGroup.TIME_WEATHER, "Set the time to day"));
        verbs.add(new Verb("night", "uxmessentials.time.alias", SubFeatureGroup.TIME_WEATHER, "Set the time to night"));
        verbs.add(new Verb("sun", "uxmessentials.weather.alias", SubFeatureGroup.TIME_WEATHER, "Clear the weather"));
        verbs.add(new Verb("rain", "uxmessentials.weather.alias", SubFeatureGroup.TIME_WEATHER, "Start rain"));
        verbs.add(new Verb(
                "thunder", "uxmessentials.weather.alias", SubFeatureGroup.TIME_WEATHER, "Start a thunderstorm"));
        // Admin-fun.
        verbs.add(new Verb("lightning", "uxmessentials.lightning.use", SubFeatureGroup.ADMIN_FUN, "Strike lightning"));
        verbs.add(new Verb("fireball", "uxmessentials.fireball.use", SubFeatureGroup.ADMIN_FUN, "Launch a fireball"));
        verbs.add(new Verb(
                "kittycannon", "uxmessentials.kittycannon.use", SubFeatureGroup.ADMIN_FUN, "Launch an exploding cat"));
        verbs.add(new Verb(
                "antioch", "uxmessentials.antioch.use", SubFeatureGroup.ADMIN_FUN, "Throw a primed TNT grenade"));
        verbs.add(new Verb("beezooka", "uxmessentials.beezooka.use", SubFeatureGroup.ADMIN_FUN, "Launch an angry bee"));
        verbs.add(new Verb(
                "break", "uxmessentials.break.use", SubFeatureGroup.ADMIN_FUN, "Break the block you are looking at"));
        verbs.add(new Verb(
                "tree", "uxmessentials.tree.use", SubFeatureGroup.ADMIN_FUN, "Generate a tree where you are looking"));
        verbs.add(new Verb(
                "bigtree",
                "uxmessentials.tree.use",
                SubFeatureGroup.ADMIN_FUN,
                "Generate a large tree where you are looking"));
        verbs.add(new Verb("nuke", "uxmessentials.nuke.use", SubFeatureGroup.ADMIN_FUN, "Rain lightning over an area"));
        return List.copyOf(verbs);
    }

    /** One itemworld command: literal, permission node, owning sub-feature group, and help text. */
    private record Verb(String literal, String permission, SubFeatureGroup group, String description) {
        Verb {
            Objects.requireNonNull(literal, "literal");
            Objects.requireNonNull(permission, "permission");
            Objects.requireNonNull(group, "group");
            Objects.requireNonNull(description, "description");
        }

        CommandSpec toSpec() {
            Function<ModuleContext, BrigadierCommand> factory = ctx -> new ItemworldCommand(literal, description);
            return new CommandSpec(literal, permission, factory);
        }
    }

    /** The kernel-side description of one itemworld command, literal and help text, no Brigadier type. */
    private record ItemworldCommand(String literal, String description) implements BrigadierCommand {}
}

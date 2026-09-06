package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.TreeType;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.itemworld.domain.SubFeatureGroup;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Shared collaborators every itemworld group-A Brigadier command holds: the constructed
 * {@link ItemworldServices} (the kernel ports, the audit logger, the live config view), plus the boundary
 * helpers each command reuses. Concrete command classes extend this so each stays focused on building its node
 * and mapping its arguments to one validated mutation.
 *
 * <p>Two gates run before any mutation. First {@link #enabled} consults the live {@code itemworld.conf} view:
 * a command is available only when its {@link SubFeatureGroup sub-feature group} is enabled and the command
 * itself is not per-command disabled (docs/10-feature-modules.md §15.10). A disabled command answers with
 * {@link ItemworldMessageKey#COMMAND_DISABLED} and does nothing else. Then the held-item verbs resolve the
 * player's main hand through {@link #heldItem}, replying {@link ItemworldMessageKey#NO_ITEM_IN_HAND} on an
 * empty hand. Every reply is a {@link MessageKey} rendered in the viewer's locale. There are no inline
 * player-facing literals.
 */
@NullMarked
abstract class ItemworldCommandSupport {

    final ItemworldServices services;
    private final String literal;
    private final SubFeatureGroup group;
    private final String description;

    ItemworldCommandSupport(ItemworldServices services, String literal, SubFeatureGroup group, String description) {
        this.services = Objects.requireNonNull(services, "services");
        this.literal = Objects.requireNonNull(literal, "literal");
        this.group = Objects.requireNonNull(group, "group");
        this.description = Objects.requireNonNull(description, "description");
    }

    /** The command literal, for the per-command disable gate and the registration. */
    final String literal() {
        return literal;
    }

    /** The short help text shown in the command listing. */
    final String describe() {
        return description;
    }

    /**
     * Whether this command may run now: its sub-feature group must be enabled and the command itself not
     * per-command disabled. On a disabled command the sender is told {@link ItemworldMessageKey#COMMAND_DISABLED}
     * and the caller must not proceed.
     */
    final boolean enabled(CommandContext<CommandSourceStack> ctx) {
        if (services.config().commandEnabled(group, literal)) {
            return true;
        }
        reply(ctx, ItemworldMessageKey.COMMAND_DISABLED, Map.of("command", literal));
        return false;
    }

    /** The invoking player, or {@code null} (after a players-only reply) for a console/command-block source. */
    final @Nullable Player player(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (sender instanceof Player player) {
            return player;
        }
        reply(ctx, SharedMessageKey.COMMAND_PLAYERS_ONLY, Map.of());
        return null;
    }

    /**
     * The player's main-hand item, or empty (after a {@link ItemworldMessageKey#NO_ITEM_IN_HAND} reply) when
     * the hand is empty. The held-item verbs ({@code /more}, {@code /repair}, {@code /enchant}, {@code /hat},
     * {@code /itemname}, …) all resolve their target through this so the empty-hand path is consistent.
     */
    final Optional<ItemStack> heldItem(CommandContext<CommandSourceStack> ctx, Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            reply(ctx, ItemworldMessageKey.NO_ITEM_IN_HAND, Map.of());
            return Optional.empty();
        }
        return Optional.of(hand);
    }

    /** A {@link PlayerRef} for the live player, for audit attribution and locale resolution. */
    static PlayerRef ref(Player player) {
        return BukkitRefs.toRef(player);
    }

    /** The command actor: a live player ref, or the stable system ref used by console automation. */
    final PlayerRef actor(CommandContext<CommandSourceStack> ctx) {
        return viewer(ctx);
    }

    /**
     * Whether {@code player} may act on the given {@code type} for this command, using the common per-type
     * gating: after the base {@code uxmessentials.<cmd>.use} node passes, the resolved registry type ({@code zombie},
     * {@code diamond_sword}, {@code sharpness}) must also be permitted under
     * {@code uxmessentials.itemworld.<cmd>.<type>}. The wildcard parent ({@code …spawnmob.*} / {@code …give.*} /
     * {@code …enchant.*}) and every per-type node default {@code true} in {@code paper-plugin.yml}, so a base-perm
     * holder is unaffected until an operator negates a specific type, non-breaking, opt-out restriction. On a denied
     * type the sender is told {@link SharedMessageKey#COMMAND_NO_PERMISSION} and the caller must not proceed.
     *
     * @param command the command segment of the node ({@code spawnmob}, {@code give}, {@code enchant})
     * @param type the resolved registry key path, already namespace-stripped and lower-cased by the caller
     */
    final boolean allowsType(CommandContext<CommandSourceStack> ctx, Player player, String command, String type) {
        return allowsType(ctx, (CommandSender) player, command, type);
    }

    /** Console-capable variant of the common per-type permission gate. */
    final boolean allowsType(
            CommandContext<CommandSourceStack> ctx, CommandSender sender, String command, String type) {
        String node = "uxmessentials.itemworld." + command + "." + type.toLowerCase(Locale.ROOT);
        boolean allowed = sender instanceof Player player
                ? services.kernel().permissions().has(ref(player), node)
                : sender.hasPermission(node);
        if (allowed) {
            return true;
        }
        reply(ctx, SharedMessageKey.COMMAND_NO_PERMISSION);
        return false;
    }

    /** Send {@code key} to the command sender, rendered in their locale, with no placeholders. */
    final void reply(CommandContext<CommandSourceStack> ctx, MessageKey key) {
        reply(ctx, key, Map.of());
    }

    /** Send {@code key} to the command sender, rendered in their locale, with {@code placeholders} substituted. */
    final void reply(CommandContext<CommandSourceStack> ctx, MessageKey key, Map<String, String> placeholders) {
        PlayerRef viewer = viewer(ctx);
        services.kernel()
                .messageSink()
                .deliver(viewer, services.kernel().messages().resolve(viewer, key, placeholders));
    }

    /** Send the command usage format to the sender. */
    final int usage(CommandContext<CommandSourceStack> ctx, String command, String usage, String description) {
        reply(
                ctx,
                SharedMessageKey.COMMAND_USAGE,
                Map.of(
                        "command", command,
                        "usage", usage,
                        "description", description));
        return 0;
    }

    private PlayerRef viewer(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        return sender instanceof Player player ? BukkitRefs.toRef(player) : PlayerRef.system(sender.getName());
    }

    /**
     * An {@code item} string argument that completes against the obtainable item materials in the live
     * registry, matching the {@code material.isItem()} gate the resolver applies, so a suggestion is never an
     * unobtainable block-only id. The value still parses through {@code ItemQuery} at execution, so an
     * arbitrary typed id keeps working.
     */
    static RequiredArgumentBuilder<CommandSourceStack, String> itemArgument() {
        return Commands.argument("item", StringArgumentType.word())
                .suggests(keyedSuggestions(() -> Registry.MATERIAL, Material::isItem));
    }

    /** A {@code type} string argument that completes against the spawnable entity types in the live registry. */
    static RequiredArgumentBuilder<CommandSourceStack, String> mobTypeArgument() {
        return Commands.argument("type", StringArgumentType.word())
                .suggests(keyedSuggestions(
                        () -> Registry.ENTITY_TYPE,
                        type -> type != EntityType.UNKNOWN && type != EntityType.PLAYER && type.isSpawnable()));
    }

    /**
     * A {@code flag} string argument that completes against the {@link ItemFlag} enum values, lower-cased. The
     * resolver upper-cases the token before {@code ItemFlag.valueOf}, so the lower-cased suggestion parses; the
     * value still resolves at execution, so a typed-but-unsuggested token keeps working.
     */
    static RequiredArgumentBuilder<CommandSourceStack, String> itemFlagArgument() {
        return Commands.argument("flag", StringArgumentType.word()).suggests(enumNames(ItemFlag.values()));
    }

    /**
     * A {@code type} string argument that completes against the {@link TreeType} enum values, lower-cased. The
     * tree resolvers lower-case and strip underscores before matching the enum names, so a lower-cased suggestion
     * parses; the friendly aliases ({@code jungle}, {@code oak}) the resolvers add are not enumerated here because
     * the canonical names cover the discoverable set, and the value still resolves at execution.
     */
    static RequiredArgumentBuilder<CommandSourceStack, String> treeTypeArgument() {
        return Commands.argument("type", StringArgumentType.word()).suggests(enumNames(TreeType.values()));
    }

    /**
     * An {@code attribute} string argument that completes against the attributes in the live registry. The value
     * still parses through {@link com.uxplima.uxmessentials.itemworld.domain.AttributeSpec} at execution, so a
     * typed-but-unsuggested id keeps working: the registry only drives the type-ahead.
     */
    static RequiredArgumentBuilder<CommandSourceStack, String> attributeArgument() {
        return Commands.argument("attribute", StringArgumentType.word())
                .suggests(keyedSuggestions(
                        () -> RegistryAccess.registryAccess().getRegistry(RegistryKey.ATTRIBUTE), attribute -> true));
    }

    /** A {@code slot} string argument that completes against the equipment-slot-group tokens the resolver accepts. */
    static RequiredArgumentBuilder<CommandSourceStack, String> slotGroupArgument() {
        return Commands.argument("slot", StringArgumentType.word())
                .suggests(CommandSuggestions.fromStrings(() -> SLOT_GROUP_TOKENS));
    }

    private static final List<String> SLOT_GROUP_TOKENS =
            List.of("any", "mainhand", "offhand", "hand", "feet", "legs", "chest", "head", "armor", "body");

    /**
     * Build a suggestion provider over an enum's values: each value is offered by its lower-cased name,
     * prefix-filtered by the partial token. Used for the small Bukkit enums ({@link ItemFlag}, {@link TreeType})
     * that are not registry {@link Keyed} types: the value set is fixed and read once per keystroke.
     */
    private static SuggestionProvider<CommandSourceStack> enumNames(Enum<?>[] values) {
        return (ctx, builder) -> {
            String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
            for (Enum<?> value : values) {
                String name = value.name().toLowerCase(Locale.ROOT);
                if (prefix.isEmpty() || name.startsWith(prefix)) {
                    builder.suggest(name);
                }
            }
            return builder.buildFuture();
        };
    }

    /** An {@code effect} string argument that completes against the potion effect types in the live registry. */
    static RequiredArgumentBuilder<CommandSourceStack, String> effectArgument() {
        return Commands.argument("effect", StringArgumentType.word())
                .suggests(keyedSuggestions(() -> Registry.EFFECT, effect -> true));
    }

    /**
     * An {@code enchant} string argument that completes against the enchantments in the live registry. The
     * value still parses through {@link com.uxplima.uxmessentials.itemworld.domain.EnchantSpec} at execution, so
     * a typed-but-unsuggested id keeps working: the registry only drives the type-ahead.
     */
    static RequiredArgumentBuilder<CommandSourceStack, String> enchantArgument() {
        // Enchantments live in the data-driven RegistryAccess (the legacy Registry.ENCHANTMENT is deprecated), the
        // same registry BukkitItemResolver#enchantment resolves the typed value against.
        return Commands.argument("enchant", StringArgumentType.word())
                .suggests(keyedSuggestions(
                        () -> RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT), enchant -> true));
    }

    /**
     * Build a suggestion provider over a Bukkit registry: each entry passing {@code accept} is offered by its
     * key, prefix-filtered by the partial token. A {@code minecraft:} entry is offered by its bare path (the
     * form the commands default the namespace for); any other namespace keeps its full {@code namespace:path}.
     * The registry is read each keystroke on the tick thread: a non-blocking in-memory lookup.
     */
    private static <T extends Keyed> SuggestionProvider<CommandSourceStack> keyedSuggestions(
            Supplier<? extends Iterable<T>> registry, Predicate<? super T> accept) {
        return (ctx, builder) -> {
            String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
            for (T entry : registry.get()) {
                if (!accept.test(entry)) {
                    continue;
                }
                String suggestion = suggestionFor(entry);
                if (prefix.isEmpty() || suggestion.startsWith(prefix)) {
                    builder.suggest(suggestion);
                }
            }
            return builder.buildFuture();
        };
    }

    private static String suggestionFor(Keyed entry) {
        var key = entry.getKey();
        return "minecraft".equals(key.getNamespace()) ? key.getKey() : key.toString();
    }
}

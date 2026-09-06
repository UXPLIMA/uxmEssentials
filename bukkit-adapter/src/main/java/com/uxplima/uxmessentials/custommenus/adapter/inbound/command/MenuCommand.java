package com.uxplima.uxmessentials.custommenus.adapter.inbound.command;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ObjIntConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;

import net.kyori.adventure.text.minimessage.MiniMessage;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.custommenus.adapter.CustomMenuLoader;
import com.uxplima.uxmessentials.custommenus.adapter.convert.DeluxeMenusConvertService;
import com.uxplima.uxmessentials.custommenus.adapter.convert.GuiPlusConvertService;
import com.uxplima.uxmessentials.custommenus.adapter.convert.OguiConvertService;
import com.uxplima.uxmessentials.custommenus.adapter.convert.ZMenuConvertService;
import com.uxplima.uxmessentials.custommenus.adapter.spec.MenuSpecPersistence;
import com.uxplima.uxmessentials.custommenus.application.CustomMenusMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuItemSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Ref;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * {@code /menu}. The operator surface over the loaded custom menus. {@code /menu open <name>} opens a registered
 * spec for the clicking player (gated by {@code uxmessentials.menu.use}); {@code /menu open <name> <target>} opens
 * it for another player (gated by {@code uxmessentials.menu.open.others}, so an operator can push a menu to someone
 * and the console can too); {@code /menu list} prints the loaded menu names; {@code /menu last} reopens the last
 * custom menu the player had open (with its page and typed arguments); {@code /menu reload} re-runs the loader and
 * reports the loaded/skipped counts, {@code /menu reload <menu>} re-loads just that one file, and
 * {@code /menu convert <deluxemenus|zmenu|ogui|guiplus> <path>} converts a DeluxeMenus, zMenu, OGUI or GUIPlus menu
 * YAML (or a directory of them) into {@code menus/*.conf}. Three admin diagnostics round out the surface:
 * {@code /menu execute <player> <action>} runs one menu action for a target, {@code /menu dump <menu>} prints a
 * loaded menu's title, rows and per-item breakdown, and {@code /menu meta <menu>} prints a compact one-line
 * metadata summary (all gated by {@code uxmessentials.menu.admin}). The set of
 * registered names is
 * supplied by the wiring rather than read off the engine, so the same list backs the {@code <name>} tab-completion,
 * the not-found guard, {@code /menu list}, and the still-registered check {@code /menu last} makes before reopening.
 */
@NullMarked
public final class MenuCommand implements CommandRegistration {

    private static final String USE = "uxmessentials.menu.use";
    private static final String ADMIN = "uxmessentials.menu.admin";
    private static final String EDITOR = "uxmessentials.menu.editor";
    private static final String OPEN_OTHERS = "uxmessentials.menu.open.others";

    private final Menus menus;
    private final Supplier<List<String>> menuNames;
    private final Supplier<CustomMenuLoader.LoadResult> reload;
    private final Function<String, CustomMenuLoader.SingleLoad> reloadOne;
    private final DeluxeMenusConvertService deluxeMenusConvert;
    private final ZMenuConvertService zMenuConvert;
    private final OguiConvertService oguiConvert;
    private final GuiPlusConvertService guiPlusConvert;
    private final MenuSpecPersistence persistence;
    private final Function<String, Optional<OpenCommandSpec>> openCommandFor;
    private final Consumer<Player> openEditor;
    private final ObjIntConsumer<Player> captureItem;
    private final Path menusDir;
    private final Scheduler scheduler;
    private final CommandFeedback feedback;

    public MenuCommand(
            Menus menus,
            Supplier<List<String>> menuNames,
            Supplier<CustomMenuLoader.LoadResult> reload,
            Function<String, CustomMenuLoader.SingleLoad> reloadOne,
            DeluxeMenusConvertService deluxeMenusConvert,
            ZMenuConvertService zMenuConvert,
            OguiConvertService oguiConvert,
            GuiPlusConvertService guiPlusConvert,
            MenuSpecPersistence persistence,
            Function<String, Optional<OpenCommandSpec>> openCommandFor,
            Consumer<Player> openEditor,
            ObjIntConsumer<Player> captureItem,
            Path menusDir,
            Scheduler scheduler,
            Messages messages) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.menuNames = Objects.requireNonNull(menuNames, "menuNames");
        this.reload = Objects.requireNonNull(reload, "reload");
        this.reloadOne = Objects.requireNonNull(reloadOne, "reloadOne");
        this.deluxeMenusConvert = Objects.requireNonNull(deluxeMenusConvert, "deluxeMenusConvert");
        this.zMenuConvert = Objects.requireNonNull(zMenuConvert, "zMenuConvert");
        this.oguiConvert = Objects.requireNonNull(oguiConvert, "oguiConvert");
        this.guiPlusConvert = Objects.requireNonNull(guiPlusConvert, "guiPlusConvert");
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.openCommandFor = Objects.requireNonNull(openCommandFor, "openCommandFor");
        this.openEditor = Objects.requireNonNull(openEditor, "openEditor");
        this.captureItem = Objects.requireNonNull(captureItem, "captureItem");
        this.menusDir = Objects.requireNonNull(menusDir, "menusDir");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.feedback = new CommandFeedback(Objects.requireNonNull(messages, "messages"));
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("menu")
                .requires(src -> src.getSender().hasPermission(USE))
                .then(Commands.literal("open")
                        .then(nameArgument()
                                .executes(this::open)
                                .then(targetArgument()
                                        .requires(src -> src.getSender().hasPermission(OPEN_OTHERS))
                                        .executes(this::openForOther))))
                .then(Commands.literal("list").executes(this::list))
                .then(Commands.literal("last").executes(this::last))
                .then(Commands.literal("reload")
                        .requires(src -> src.getSender().hasPermission(ADMIN))
                        .executes(this::reload)
                        .then(menuArgument().executes(this::reloadOne)))
                .then(Commands.literal("execute")
                        .requires(src -> src.getSender().hasPermission(ADMIN))
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .then(Commands.argument("action", StringArgumentType.greedyString())
                                        .executes(this::executeAction))))
                .then(Commands.literal("dump")
                        .requires(src -> src.getSender().hasPermission(ADMIN))
                        .then(menuArgument().executes(this::dump)))
                .then(Commands.literal("meta")
                        .requires(src -> src.getSender().hasPermission(ADMIN))
                        .then(menuArgument().executes(this::meta)))
                .then(Commands.literal("save")
                        .requires(src -> src.getSender().hasPermission(ADMIN))
                        .then(menuArgument().executes(this::save)))
                .then(Commands.literal("editor")
                        .requires(src -> src.getSender().hasPermission(EDITOR))
                        .executes(this::editor))
                .then(Commands.literal("captureitem")
                        .requires(src -> src.getSender().hasPermission(EDITOR))
                        .then(Commands.argument("slot", IntegerArgumentType.integer(0))
                                .executes(this::captureItem)))
                .then(Commands.literal("convert")
                        .requires(src -> src.getSender().hasPermission(ADMIN))
                        .then(Commands.literal("deluxemenus")
                                .then(Commands.argument("path", StringArgumentType.greedyString())
                                        .executes(this::convertDeluxeMenus)))
                        .then(Commands.literal("zmenu")
                                .then(Commands.argument("path", StringArgumentType.greedyString())
                                        .executes(this::convertZMenu)))
                        .then(Commands.literal("ogui")
                                .then(Commands.argument("path", StringArgumentType.greedyString())
                                        .executes(this::convertOgui)))
                        .then(Commands.literal("guiplus")
                                .then(Commands.argument("path", StringArgumentType.greedyString())
                                        .executes(this::convertGuiPlus))))
                .build();
    }

    @Override
    public String description() {
        return "Open, list, reload, execute, dump, save, edit, capture into, and convert operator custom menus.";
    }

    /** The {@code <name>} argument, completed from the currently registered menu names. */
    private RequiredArgumentBuilder<CommandSourceStack, String> nameArgument() {
        return Commands.argument("name", StringArgumentType.word()).suggests(nameSuggestions());
    }

    /**
     * The {@code <menu>} word argument the admin diagnostics ({@code reload}/{@code dump}/{@code meta}) take, completed
     * from the same registered-name suggestions the {@code <name>} argument uses. It carries a distinct argument id so
     * it can sit on the same {@code /menu} node without colliding with the open branch's {@code <name>}.
     */
    private RequiredArgumentBuilder<CommandSourceStack, String> menuArgument() {
        return Commands.argument("menu", StringArgumentType.word()).suggests(nameSuggestions());
    }

    private SuggestionProvider<CommandSourceStack> nameSuggestions() {
        return (ctx, builder) -> {
            String prefix = builder.getRemainingLowerCase();
            for (String name : menuNames.get()) {
                if (name.toLowerCase(java.util.Locale.ROOT).startsWith(prefix)) {
                    builder.suggest(name);
                }
            }
            return builder.buildFuture();
        };
    }

    private int open(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            feedback.send(sender, SharedMessageKey.COMMAND_PLAYERS_ONLY);
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "name");
        if (!menuNames.get().contains(name)) {
            feedback.send(player, CustomMenusMessageKey.MENU_NOT_FOUND, Map.of("name", name));
            return 0;
        }
        menus.open(BukkitRefs.toRef(player), name, null);
        return Command.SINGLE_SUCCESS;
    }

    /** The {@code <target>} player argument, present only for a sender holding {@code uxmessentials.menu.open.others}. */
    private RequiredArgumentBuilder<CommandSourceStack, PlayerSelectorArgumentResolver> targetArgument() {
        return Commands.argument("target", ArgumentTypes.player());
    }

    /**
     * Open a loaded menu for another player. The menu name is checked first, so an unknown name draws the same
     * not-found line the self-open does before any target work. The target is resolved from the single-player
     * selector; a selector that matches nobody draws the shared unknown-player line rather than opening an empty
     * window. The sender may be a player or the console. This never opens for the sender, only for the resolved
     * target, so the console can legitimately push a menu to a real player. The open is attributed to the sender as
     * the executor so the opened menu can show {@code %executor%} distinct from {@code %player%} (the target): a player
     * sender becomes that executor, and a console sender, which carries no {@link PlayerRef}, falls back to the
     * target, so {@code %executor%} still resolves to a real name rather than an empty token. The menu itself opens on
     * the target's own region thread through the {@link Menus} facade, so cross-region opens stay Folia-safe.
     */
    private int openForOther(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = ctx.getSource().getSender();
        String name = StringArgumentType.getString(ctx, "name");
        if (!menuNames.get().contains(name)) {
            feedback.send(sender, CustomMenusMessageKey.MENU_NOT_FOUND, Map.of("name", name));
            return 0;
        }
        Player target = resolveTarget(ctx);
        if (target == null) {
            feedback.send(sender, SharedMessageKey.COMMAND_UNKNOWN_PLAYER);
            return 0;
        }
        PlayerRef executor = sender instanceof Player p ? BukkitRefs.toRef(p) : BukkitRefs.toRef(target);
        menus.open(BukkitRefs.toRef(target), name, null, 0, Map.of(), executor);
        feedback.send(sender, CustomMenusMessageKey.MENU_OPENED_FOR, Map.of("player", target.getName()));
        return Command.SINGLE_SUCCESS;
    }

    /** Resolve the single target the {@code <target>} selector names, or null when it matched none (or more than one). */
    private static @Nullable Player resolveTarget(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        PlayerSelectorArgumentResolver resolver = ctx.getArgument("target", PlayerSelectorArgumentResolver.class);
        List<Player> resolved = resolver.resolve(ctx.getSource());
        return resolved.size() == 1 ? resolved.get(0) : null;
    }

    /**
     * Resolve the single online player the {@code <player>} selector of {@code /menu execute} names, or null when it
     * matched none: an offline name, or a selector that matched nothing or more than one. Unlike {@link #resolveTarget}
     * this swallows the Brigadier parse error a no-match name raises, so the caller answers with the shared
     * unknown-player line rather than surfacing a raw parse failure.
     */
    private static @Nullable Player resolvePlayer(CommandContext<CommandSourceStack> ctx) {
        try {
            PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
            List<Player> resolved = resolver.resolve(ctx.getSource());
            return resolved.size() == 1 ? resolved.get(0) : null;
        } catch (CommandSyntaxException unmatched) {
            return null;
        }
    }

    private int list(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        List<String> names = menuNames.get();
        if (names.isEmpty()) {
            feedback.send(sender, CustomMenusMessageKey.MENU_LIST_EMPTY);
            return Command.SINGLE_SUCCESS;
        }
        feedback.send(sender, CustomMenusMessageKey.MENU_LIST_HEADER, Map.of("count", String.valueOf(names.size())));
        for (String name : names) {
            feedback.send(sender, CustomMenusMessageKey.MENU_LIST_ENTRY, Map.of("name", name));
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Reopen the last custom menu the player had open, with the page and typed arguments it was shown with. Only a
     * player has a remembered open, so the console is turned away with the shared players-only line. The reopen is
     * delegated to {@link Menus#reopenLast}, which replays the recorded open without re-recording it (so repeated
     * {@code /menu last} never grows the back history) and returns {@code false} when there is nothing to reopen: a
     * player who never opened a menu, or whose remembered menu is no longer a registered spec (a since-deleted or
     * renamed one). That yields the no-last line rather than the loud unknown-spec failure a blind reopen would raise.
     * The reopen lands on the player's own region thread through the facade, so it stays Folia-safe.
     */
    private int last(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            feedback.send(sender, SharedMessageKey.COMMAND_PLAYERS_ONLY);
            return 0;
        }
        if (!menus.reopenLast(BukkitRefs.toRef(player))) {
            feedback.send(player, CustomMenusMessageKey.MENU_NO_LAST);
            return 0;
        }
        return Command.SINGLE_SUCCESS;
    }

    private int reload(CommandContext<CommandSourceStack> ctx) {
        CustomMenuLoader.LoadResult result = reload.get();
        feedback.send(
                ctx.getSource().getSender(),
                CustomMenusMessageKey.MENU_RELOADED,
                Map.of(
                        "loaded",
                        String.valueOf(result.loaded()),
                        "skipped",
                        String.valueOf(result.skipped().size())));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Re-load just the single {@code menus/<menu>.conf} spec, the per-menu {@code /menu reload <menu>}. The seam
     * re-parses that one file and re-registers its spec, leaving every other loaded menu in place; an absent file
     * replies not-found, otherwise the one file's loaded / skipped outcome is reported (a re-registered menu reads
     * {@code 1 loaded, 0 skipped}, a spec that failed to parse or named an unknown id reads {@code 0 loaded, 1
     * skipped}). Bare {@code /menu reload} still reloads every menu: this is the argument-bearing branch only.
     */
    private int reloadOne(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String name = StringArgumentType.getString(ctx, "menu");
        CustomMenuLoader.SingleLoad result = reloadOne.apply(name);
        if (!result.found()) {
            feedback.send(sender, CustomMenusMessageKey.MENU_NOT_FOUND, Map.of("name", name));
            return 0;
        }
        feedback.send(
                sender,
                CustomMenusMessageKey.MENU_RELOADED_ONE,
                Map.of(
                        "name", name,
                        "loaded", String.valueOf(result.loaded()),
                        "skipped", String.valueOf(result.skipped())));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Run one menu action for a target, {@code /menu execute <player> <action>}, the admin "fire a menu action on
     * someone" tool. The target is resolved from the single-player selector; an offline or no-match name draws the
     * shared unknown-player line rather than running anything. The {@code <action>} greedy string is parsed into a
     * {@link Ref} and dispatched through {@link Menus#execute}, which runs it on the target's own entity thread with
     * the target as the action's viewer, so a {@code %player%} in the action resolves to them. The action runs
     * standalone (no menu need be open or registered), and the sender is told which action ran for whom.
     */
    private int executeAction(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Player target = resolvePlayer(ctx);
        if (target == null) {
            feedback.send(sender, SharedMessageKey.COMMAND_UNKNOWN_PLAYER);
            return 0;
        }
        String actionString = StringArgumentType.getString(ctx, "action").strip();
        if (actionString.isEmpty()) {
            // A greedy string can capture an empty remainder on a trailing space; there is no action to run.
            feedback.send(sender, SharedMessageKey.COMMAND_UNKNOWN_PLAYER);
            return 0;
        }
        menus.execute(BukkitRefs.toRef(target), Ref.parse(actionString));
        feedback.send(
                sender, CustomMenusMessageKey.MENU_EXECUTED, Map.of("name", target.getName(), "action", actionString));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Print an operator diagnostic of a loaded menu, {@code /menu dump <menu>}. An unregistered name replies
     * not-found; otherwise a header line (title, row count, item count) is followed by one line per item
     * {@code id @ slots, material (N actions)}, so an operator can see a menu's shape without opening it. Every line
     * resolves through a {@link CustomMenusMessageKey}, so the dump carries no inline literal.
     */
    private int dump(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String name = StringArgumentType.getString(ctx, "menu");
        Optional<MenuSpec> found = menus.registeredSpec(name);
        if (found.isEmpty()) {
            feedback.send(sender, CustomMenusMessageKey.MENU_NOT_FOUND, Map.of("name", name));
            return 0;
        }
        MenuSpec spec = found.get();
        feedback.send(
                sender,
                CustomMenusMessageKey.MENU_DUMP_HEADER,
                Map.of(
                        "name", name,
                        "title", plainTitle(spec.title()),
                        "rows", String.valueOf(spec.rows()),
                        "items", String.valueOf(spec.items().size())));
        spec.items()
                .forEach((id, item) ->
                        feedback.send(sender, CustomMenusMessageKey.MENU_DUMP_ITEM, dumpItemPlaceholders(id, item)));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Print a compact one-line metadata summary of a loaded menu, {@code /menu meta <menu>}: its title, row count,
     * item count, and the four routing flags (has a list source, declares a {@code bedrock {}} block, is chest-only,
     * paints the bottom inventory). An unregistered name replies not-found. One reply line, resolved through a
     * {@link CustomMenusMessageKey}.
     */
    private int meta(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String name = StringArgumentType.getString(ctx, "menu");
        Optional<MenuSpec> found = menus.registeredSpec(name);
        if (found.isEmpty()) {
            feedback.send(sender, CustomMenusMessageKey.MENU_NOT_FOUND, Map.of("name", name));
            return 0;
        }
        feedback.send(sender, CustomMenusMessageKey.MENU_META, metaPlaceholders(name, found.get()));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Re-serialize a loaded menu back to its {@code menus/<menu>.conf} file and reload it, {@code /menu save <menu>},
     * the end-to-end proof that the editor's write path round-trips. An unregistered name replies not-found; otherwise
     * the registered spec (and any {@code command {}} block it declared) is validated and written off the tick thread
     * through the {@link Scheduler} port, and the outcome is bridged back to the global region to report it and, on a
     * clean save, trigger the single-file reload. A spec whose refs no longer resolve is refused with the offending
     * ids and nothing is written, so a save can never leave a menu the loader would only skip.
     */
    private int save(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String name = StringArgumentType.getString(ctx, "menu");
        Optional<MenuSpec> found = menus.registeredSpec(name);
        if (found.isEmpty()) {
            feedback.send(sender, CustomMenusMessageKey.MENU_NOT_FOUND, Map.of("name", name));
            return 0;
        }
        MenuSpec spec = found.get();
        OpenCommandSpec command = openCommandFor.apply(name).orElse(null);
        scheduler.async(() -> {
            MenuSpecPersistence.SaveResult result = persistence.save(menusDir, name, spec, command);
            scheduler.onGlobal(() -> reportSave(sender, name, result));
        });
        return Command.SINGLE_SUCCESS;
    }

    /** Report a save outcome to {@code sender}; on a clean write, reload the single file so the engine picks it up. */
    private void reportSave(CommandSender sender, String name, MenuSpecPersistence.SaveResult result) {
        switch (result.status()) {
            case SAVED -> {
                var unused = reloadOne.apply(name);
                feedback.send(sender, CustomMenusMessageKey.MENU_SAVED, Map.of("name", name));
            }
            case INVALID_REFS ->
                feedback.send(
                        sender,
                        CustomMenusMessageKey.MENU_SAVE_INVALID,
                        Map.of("name", name, "missing", String.join(", ", result.missingRefs())));
            case IO_ERROR -> feedback.send(sender, CustomMenusMessageKey.MENU_SAVE_FAILED, Map.of("name", name));
        }
    }

    /**
     * Open the in-game menu editor for the sender: {@code /menu editor}, gated by {@code uxmessentials.menu.editor}.
     * The editor is a GUI, so only a player can open it; a console (or other non-player) meets the shared players-only
     * line. The picker itself opens on the player's own entity thread through the engine, so the open stays Folia-safe.
     */
    private int editor(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            feedback.send(sender, SharedMessageKey.COMMAND_PLAYERS_ONLY);
            return 0;
        }
        openEditor.accept(player);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Capture the sender's main-hand item into {@code <slot>} of the menu they are editing, {@code /menu captureitem
     * <slot>}, gated by {@code uxmessentials.menu.editor}. A held item is captured with all its NBT (mirroring
     * {@code /hologram action … give hand}) and written into the slot of the viewer's active grid session. Only a
     * player has a hand and an edit session, so the console meets the shared players-only line; the capture hook itself
     * answers an empty hand, a slot past the menu, or no active session with the matching editor line and changes
     * nothing. The hook re-opens the grid on the viewer's own entity thread, so the redraw stays Folia-safe.
     */
    private int captureItem(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            feedback.send(sender, SharedMessageKey.COMMAND_PLAYERS_ONLY);
            return 0;
        }
        captureItem.accept(player, IntegerArgumentType.getInteger(ctx, "slot"));
        return Command.SINGLE_SUCCESS;
    }

    /** The per-item dump line's placeholders: the item id, its slot list, its raw material token, and its action count. */
    private static Map<String, String> dumpItemPlaceholders(String id, MenuItemSpec item) {
        return Map.of(
                "id", id,
                "slots", slotsText(item),
                "material", item.material(),
                "actions", String.valueOf(actionCount(item)));
    }

    /** The one-line meta summary's placeholders: title, rows, item count and the four routing flags. */
    private static Map<String, String> metaPlaceholders(String name, MenuSpec spec) {
        return Map.of(
                "name", name,
                "title", plainTitle(spec.title()),
                "rows", String.valueOf(spec.rows()),
                "items", String.valueOf(spec.items().size()),
                "has-list", String.valueOf(hasList(spec)),
                "has-bedrock", String.valueOf(spec.bedrock().isPresent()),
                "chest-only", String.valueOf(spec.chestOnly()),
                "bottom-inventory", String.valueOf(spec.bottomInventory()));
    }

    /** The item's occupied slots as a comma-joined list ({@code 0,1,2}); empty when the item declares no slots. */
    private static String slotsText(MenuItemSpec item) {
        return item.slots().slots().stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    /** How many action refs the item binds across every click gesture: the total bound, without double-counting {@code ANY}. */
    private static int actionCount(MenuItemSpec item) {
        return item.click().actions().values().stream().mapToInt(List::size).sum();
    }

    /** Whether any of the menu's items draws from a list source, the {@code has-list} meta flag. */
    private static boolean hasList(MenuSpec spec) {
        return spec.items().values().stream().anyMatch(item -> item.list().isPresent());
    }

    /**
     * The menu's raw title token reduced to plain text for a diagnostic line: MiniMessage tags are stripped so a title
     * like {@code <title>Shop</title>} reads {@code Shop}, and, because the stripped value is folded back into a
     * MiniMessage template: a title cannot inject markup into the reply. A {@code @key} reference or a {@code %token%}
     * is left verbatim, which is the honest thing to show an operator inspecting the spec.
     */
    private static String plainTitle(String title) {
        return MiniMessage.miniMessage().stripTags(title);
    }

    /**
     * Convert a DeluxeMenus menu YAML (or a directory of them) at {@code <path>} into {@code menus/*.conf}. The path is
     * absolute or relative to the menus directory; a path that matches no DeluxeMenus YAML replies not-found, otherwise
     * the converted / skipped / warning counts are reported. Deliberately does not reload. An operator reviews the
     * emitted files, then runs {@code /menu reload}, and the per-file conversion never crashes the command.
     */
    private int convertDeluxeMenus(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String path = StringArgumentType.getString(ctx, "path");
        var report = deluxeMenusConvert.convert(path);
        if (!report.found()) {
            feedback.send(sender, CustomMenusMessageKey.MENU_CONVERT_FAILED, Map.of("path", path));
            return 0;
        }
        reportConverted(sender, report.converted(), report.skipped(), report.warnings());
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Convert a zMenu inventory YAML (or a directory of them) at {@code <path>} into {@code menus/*.conf}, exactly as
     * the DeluxeMenus branch does. Same path resolution, same not-found reply, same converted / skipped / warning
     * report, same deliberate no-reload. The per-file conversion never crashes the command.
     */
    private int convertZMenu(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String path = StringArgumentType.getString(ctx, "path");
        var report = zMenuConvert.convert(path);
        if (!report.found()) {
            feedback.send(sender, CustomMenusMessageKey.MENU_CONVERT_FAILED, Map.of("path", path));
            return 0;
        }
        reportConverted(sender, report.converted(), report.skipped(), report.warnings());
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Convert an OGUI GUI YAML (or a directory of them) at {@code <path>} into {@code menus/*.conf}, exactly as the
     * DeluxeMenus and zMenu branches do. Same path resolution, same not-found reply, same converted / skipped /
     * warning report, same deliberate no-reload. The per-file conversion never crashes the command.
     */
    private int convertOgui(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String path = StringArgumentType.getString(ctx, "path");
        var report = oguiConvert.convert(path);
        if (!report.found()) {
            feedback.send(sender, CustomMenusMessageKey.MENU_CONVERT_FAILED, Map.of("path", path));
            return 0;
        }
        reportConverted(sender, report.converted(), report.skipped(), report.warnings());
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Convert a GUIPlus GUI YAML (or a directory of them) at {@code <path>} into {@code menus/*.conf}, exactly as the
     * DeluxeMenus, zMenu and OGUI branches do. Same path resolution, same not-found reply, same converted / skipped /
     * warning report, same deliberate no-reload. The per-file conversion never crashes the command.
     */
    private int convertGuiPlus(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String path = StringArgumentType.getString(ctx, "path");
        var report = guiPlusConvert.convert(path);
        if (!report.found()) {
            feedback.send(sender, CustomMenusMessageKey.MENU_CONVERT_FAILED, Map.of("path", path));
            return 0;
        }
        reportConverted(sender, report.converted(), report.skipped(), report.warnings());
        return Command.SINGLE_SUCCESS;
    }

    /** Report the converted / skipped / warning counts a convert run tallied: shared by all converter branches. */
    private void reportConverted(CommandSender sender, int converted, int skipped, int warnings) {
        feedback.send(
                sender,
                CustomMenusMessageKey.MENU_CONVERTED,
                Map.of(
                        "converted", String.valueOf(converted),
                        "skipped", String.valueOf(skipped),
                        "warnings", String.valueOf(warnings)));
    }
}

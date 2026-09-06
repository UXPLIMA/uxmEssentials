package com.uxplima.uxmessentials.warps.adapter.inbound.command;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.ListDisplayMode;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.warps.adapter.WarpServices;
import com.uxplima.uxmessentials.warps.application.WarpsMessageKey;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /warp}: the single entry point for everything a player does with server warps, structured as a
 * Brigadier subcommand tree (the same idiom {@code /pwarp} uses).
 *
 * <ul>
 *   <li>{@code /warp <name> [player]} teleports to a server warp. With no trailing player the sender warps
 *       themselves; with one, and {@code uxmessentials.warp.others}, staff send that player instead. Gated by
 *       {@code uxmessentials.warp.use}.
 *   <li>{@code /warp list} opens the warps browse menu (or the chat list when the operator selects
 *       {@code chat} mode, and always for a console). Gated by {@code uxmessentials.warp.list}.
 *   <li>{@code /warp create <name>} creates a server-wide warp at the operator's position or re-anchors one
 *       of the same name; {@code /warp set <name>} stays as a hidden alias of the same action so existing
 *       muscle-memory keeps working. Gated by {@code uxmessentials.warp.set}.
 *   <li>{@code /warp del <name>} removes a warp. Gated by {@code uxmessentials.warp.delete}.
 *   <li>{@code /warp info <name>} shows a warp's owner, creation time and cost. Gated by
 *       {@code uxmessentials.warp.info}.
 *   <li>{@code /warp move <name>} re-anchors an existing warp to the operator's current position. Gated by
 *       {@code uxmessentials.warp.move}.
 *   <li>{@code /warp lock|password|rate|rating|editor <name>} keep their prior per-warp management behaviour.
 * </ul>
 *
 * <p>The positional {@code <name>} could in principle collide with a subcommand literal (a warp literally
 * named {@code set}), but Brigadier matches literal children before the {@code name} argument, so the
 * literals always win. The same disambiguation {@code /pwarp} relies on, with no reserved-name guard on
 * either side. Per-subcommand permissions are enforced by Brigadier {@code .requires(...)} on each node, so
 * only {@code warp} is a command-catalog id; the subcommands are literals inside the tree.
 */
@NullMarked
public final class WarpCommand extends WarpCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.warp.use";
    private static final String OTHERS_PERMISSION = "uxmessentials.warp.others";
    private static final String LOCK_PERMISSION = "uxmessentials.warp.lock";
    private static final String PASSWORD_PERMISSION = "uxmessentials.warp.password";
    private static final String EDIT_PERMISSION = "uxmessentials.warp.edit";
    private static final String LIST_PERMISSION = "uxmessentials.warp.list";
    private static final String SET_PERMISSION = "uxmessentials.warp.set";
    private static final String DELETE_PERMISSION = "uxmessentials.warp.delete";
    private static final String INFO_PERMISSION = "uxmessentials.warp.info";
    private static final String MOVE_PERMISSION = "uxmessentials.warp.move";

    private final Supplier<ListDisplayMode> displayMode;

    public WarpCommand(WarpServices services, Messages messages, Supplier<ListDisplayMode> displayMode) {
        super(services, messages);
        this.displayMode = Objects.requireNonNull(displayMode, "displayMode");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("warp")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.literal("list")
                        .requires(src -> src.getSender().hasPermission(LIST_PERMISSION))
                        .executes(this::runList))
                .then(Commands.literal("create")
                        .requires(src -> src.getSender().hasPermission(SET_PERMISSION))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(this::runSet)
                                .then(Commands.literal("at").then(explicitPositionArguments(this::runSetAt)))))
                .then(explicitPositionNode("createat", SET_PERMISSION, this::runSetAt))
                // Hidden alias of `create` so existing `/warp set <name>` muscle-memory and docs keep working.
                .then(Commands.literal("set")
                        .requires(src -> src.getSender().hasPermission(SET_PERMISSION))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .executes(this::runSet)))
                .then(Commands.literal("del")
                        .requires(src -> src.getSender().hasPermission(DELETE_PERMISSION))
                        .then(warpNameArgument().executes(this::runDelete)))
                .then(Commands.literal("info")
                        .requires(src -> src.getSender().hasPermission(INFO_PERMISSION))
                        .then(warpNameArgument().executes(this::runInfo)))
                .then(Commands.literal("move")
                        .requires(src -> src.getSender().hasPermission(MOVE_PERMISSION))
                        .then(warpNameArgument()
                                .executes(this::runMove)
                                .then(Commands.literal("at").then(explicitPositionArguments(this::runMoveAt)))))
                .then(explicitPositionNode("moveat", MOVE_PERMISSION, this::runMoveAt))
                .then(Commands.literal("lock")
                        .requires(src -> src.getSender().hasPermission(LOCK_PERMISSION))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(CommandSuggestions.forPlayer(this::usableWarpNames))
                                .executes(this::toggleLock)))
                .then(Commands.literal("password")
                        .requires(src -> src.getSender().hasPermission(PASSWORD_PERMISSION))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(CommandSuggestions.forPlayer(this::usableWarpNames))
                                .executes(this::setPasswordClear)
                                .then(Commands.argument("password", StringArgumentType.word())
                                        .executes(this::setPassword))))
                .then(Commands.literal("rate")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(CommandSuggestions.forPlayer(this::usableWarpNames))
                                .then(Commands.argument(
                                                "rating",
                                                com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(1.0, 5.0))
                                        .executes(this::rateWarp))))
                .then(Commands.literal("rating")
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(CommandSuggestions.forPlayer(this::usableWarpNames))
                                .executes(this::getWarpRating)))
                .then(Commands.literal("editor")
                        .requires(src -> src.getSender().hasPermission(EDIT_PERMISSION))
                        .executes(this::openWarpManager)
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(CommandSuggestions.forPlayer(this::usableWarpNames))
                                .executes(this::openWarpEditor)))
                .then(warpNameArgument()
                        .executes(this::run)
                        .then(Commands.argument("arg", StringArgumentType.word())
                                .suggests(CommandSuggestions.onlinePlayers())
                                .executes(this::runWithArg)))
                .build();
    }

    @Override
    public String description() {
        return "Teleport to a server warp; list, create, delete, move, lock, edit or inspect warps.";
    }

    /**
     * Bare {@code /warp} opens the warps browse menu: the same view {@code /warp list} opens. Installed on
     * the root only when the command's catalog {@code gui} flag is on; with it off the bare root falls back
     * to the usage line.
     */
    @Override
    public Optional<Command<CommandSourceStack>> guiRoot() {
        return Optional.of(this::runList);
    }

    /** Bare {@code /warp editor} opens the admin warp manager GUI, the kit-editor-style manager. */
    private int openWarpManager(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        if (services.managerView() != null) {
            services.managerView().open(sender, ref(sender));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int openWarpEditor(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        String name = ctx.getArgument("name", String.class);
        if (!services.repository().exists(WarpName.of(name))) {
            feedback.send(sender, WarpsMessageKey.WARP_NOT_FOUND, Map.of("warp", name));
            return 0;
        }
        if (services.editorView() != null) {
            services.editorView().open(sender, ref(sender), name, null);
        }
        return Command.SINGLE_SUCCESS;
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.useWarp().use(ref(sender), WarpName.of(ctx.getArgument("name", String.class)));
        return Command.SINGLE_SUCCESS;
    }

    private int runWithArg(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        String warpName = ctx.getArgument("name", String.class);
        String arg = ctx.getArgument("arg", String.class);

        if (sender.hasPermission(OTHERS_PERMISSION)) {
            Optional<PlayerRef> target = services.players().findOnlineByName(arg);
            if (target.isPresent()) {
                services.useWarp().useFor(ref(sender), target.get(), WarpName.of(warpName));
                return Command.SINGLE_SUCCESS;
            }
        }

        services.useWarp().use(ref(sender), WarpName.of(warpName), arg);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * {@code /warp list}: open the warps browse menu. The same {@code PaginatedGui} bare {@code /warps} used
     * to open. A console has no inventory and the operator-selectable {@code chat} display mode both fall back
     * to the clickable chat list, sharing the same {@link com.uxplima.uxmessentials.warps.application.ListWarps}
     * filter so the two presentations never disagree. The mode is read live so a reload takes effect at once.
     */
    private int runList(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player viewer)) {
            // A console has no inventory to open a menu in; show the chat list instead.
            return runChatList(ctx);
        }
        if (displayMode.get() == ListDisplayMode.CHAT) {
            // Operator chose command/chat-only output: list in chat without opening an inventory.
            return runChatList(ctx);
        }
        PlayerRef viewerRef = ref(viewer);
        List<Warp> warps = services.listWarps().available(viewerRef);
        services.warpMenu().open(viewer, viewerRef, warps);
        return Command.SINGLE_SUCCESS;
    }

    private int runChatList(CommandContext<CommandSourceStack> ctx) {
        services.listWarps().list(actor(ctx));
        return Command.SINGLE_SUCCESS;
    }

    /** Backs both {@code /warp create <name>} and its hidden {@code /warp set <name>} alias. */
    private int runSet(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        WarpName name = WarpName.of(ctx.getArgument("name", String.class));
        services.setWarp().set(ref(sender), name, position(sender));
        return Command.SINGLE_SUCCESS;
    }

    private int runDelete(CommandContext<CommandSourceStack> ctx) {
        services.delWarp().delete(actor(ctx), WarpName.of(ctx.getArgument("name", String.class)));
        return Command.SINGLE_SUCCESS;
    }

    private int runInfo(CommandContext<CommandSourceStack> ctx) {
        services.warpInfo().show(actor(ctx), WarpName.of(ctx.getArgument("name", String.class)));
        return Command.SINGLE_SUCCESS;
    }

    private int runMove(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        WarpName name = WarpName.of(ctx.getArgument("name", String.class));
        services.moveWarp().move(ref(sender), name, position(sender));
        return Command.SINGLE_SUCCESS;
    }

    private int toggleLock(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String warpName = ctx.getArgument("name", String.class);
        Optional<Warp> opt = services.repository().find(WarpName.of(warpName));
        if (opt.isEmpty()) {
            services.warpInfo().show(actor(ctx), WarpName.of(warpName));
            return 0;
        }
        Warp warp = opt.get();
        Warp updated = warp.withLocked(!warp.isLocked());
        services.repository().save(updated);

        feedback.send(
                sender,
                WarpsMessageKey.WARP_LOCK_TOGGLED,
                Map.of("warp", warpName, "state", Boolean.toString(updated.isLocked())));
        return Command.SINGLE_SUCCESS;
    }

    private int setPasswordClear(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String warpName = ctx.getArgument("name", String.class);
        Optional<Warp> opt = services.repository().find(WarpName.of(warpName));
        if (opt.isEmpty()) {
            services.warpInfo().show(actor(ctx), WarpName.of(warpName));
            return 0;
        }
        Warp warp = opt.get();
        Warp updated = warp.withPassword(Optional.empty());
        services.repository().save(updated);

        feedback.send(sender, WarpsMessageKey.WARP_PASSWORD_CLEARED, Map.of("warp", warpName));
        return Command.SINGLE_SUCCESS;
    }

    private int setPassword(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String warpName = ctx.getArgument("name", String.class);
        String password = ctx.getArgument("password", String.class);
        Optional<Warp> opt = services.repository().find(WarpName.of(warpName));
        if (opt.isEmpty()) {
            services.warpInfo().show(actor(ctx), WarpName.of(warpName));
            return 0;
        }
        Warp warp = opt.get();
        Warp updated = warp.withPassword(Optional.of(password));
        services.repository().save(updated);

        feedback.send(sender, WarpsMessageKey.WARP_PASSWORD_SET, Map.of("warp", warpName, "password", password));
        return Command.SINGLE_SUCCESS;
    }

    private int rateWarp(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        String warpName = ctx.getArgument("name", String.class);
        double rating = ctx.getArgument("rating", Double.class);

        // The existence check is served from the in-memory set, but the rating write is not cached: it touches
        // the database, so run it off the tick thread and bridge the confirmation back to the player's region.
        if (!services.repository().exists(WarpName.of(warpName))) {
            feedback.send(sender, WarpsMessageKey.WARP_NOT_FOUND, Map.of("warp", warpName));
            return 0;
        }
        PlayerRef who = ref(sender);
        services.scheduler().async(() -> {
            services.repository().rate(WarpName.of(warpName), who.uuid(), rating);
            onPlayer(
                    who,
                    () -> feedback.send(
                            sender,
                            WarpsMessageKey.WARP_RATED,
                            Map.of("warp", warpName, "rating", Double.toString(rating))));
        });
        return Command.SINGLE_SUCCESS;
    }

    private int getWarpRating(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String warpName = ctx.getArgument("name", String.class);

        // The existence check is in-memory; the average rating is an aggregate query against the database, so
        // run it off the tick thread and bridge the result back to the player's region thread.
        if (!services.repository().exists(WarpName.of(warpName))) {
            feedback.send(sender, WarpsMessageKey.WARP_NOT_FOUND, Map.of("warp", warpName));
            return 0;
        }
        PlayerRef who = actor(ctx);
        services.scheduler().async(() -> {
            double avg = services.repository().averageRating(WarpName.of(warpName));
            onPlayer(
                    who,
                    () -> feedback.send(
                            sender, WarpsMessageKey.WARP_RATING, Map.of("warp", warpName, "rating", oneDecimal(avg))));
        });
        return Command.SINGLE_SUCCESS;
    }

    private static String oneDecimal(double value) {
        return java.math.BigDecimal.valueOf(value)
                .setScale(1, java.math.RoundingMode.HALF_UP)
                .toPlainString();
    }

    private LiteralArgumentBuilder<CommandSourceStack> explicitPositionNode(
            String literal, String permission, Command<CommandSourceStack> action) {
        return Commands.literal(literal)
                .requires(src -> src.getSender().hasPermission(permission))
                .then(Commands.argument("name", StringArgumentType.word()).then(explicitPositionArguments(action)));
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> explicitPositionArguments(
            Command<CommandSourceStack> action) {
        return Commands.argument("world", StringArgumentType.word())
                .suggests(CommandSuggestions.loadedWorlds())
                .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                        .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                        .executes(action))));
    }

    private int runSetAt(CommandContext<CommandSourceStack> ctx) {
        Position at = explicitPosition(ctx);
        if (at == null) {
            return 0;
        }
        services.setWarp().set(actor(ctx), WarpName.of(ctx.getArgument("name", String.class)), at);
        return Command.SINGLE_SUCCESS;
    }

    private int runMoveAt(CommandContext<CommandSourceStack> ctx) {
        Position at = explicitPosition(ctx);
        if (at == null) {
            return 0;
        }
        services.moveWarp().move(actor(ctx), WarpName.of(ctx.getArgument("name", String.class)), at);
        return Command.SINGLE_SUCCESS;
    }

    private @org.jspecify.annotations.Nullable Position explicitPosition(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        World world = sender.getServer().getWorld(ctx.getArgument("world", String.class));
        double x = ctx.getArgument("x", Double.class);
        double y = ctx.getArgument("y", Double.class);
        double z = ctx.getArgument("z", Double.class);
        if (world == null) {
            feedback.send(
                    sender,
                    com.uxplima.uxmessentials.shared.application.message.SharedMessageKey.COMMAND_UNKNOWN_WORLD,
                    Map.of("world", ctx.getArgument("world", String.class)));
            return null;
        }
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            feedback.send(
                    sender,
                    com.uxplima.uxmessentials.shared.application.message.SharedMessageKey.COMMAND_INVALID_POSITION);
            return null;
        }
        return Position.of(BukkitRefs.toRef(world), x, y, z);
    }
}

package com.uxplima.uxmessentials.npc.adapter.inbound.command;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.npc.adapter.NpcServices;
import com.uxplima.uxmessentials.npc.adapter.inbound.gui.NpcListMenu;
import com.uxplima.uxmessentials.npc.adapter.outbound.BukkitNpcSkins;
import com.uxplima.uxmessentials.npc.application.NearbyNpcs;
import com.uxplima.uxmessentials.npc.application.NpcMessageKey;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.npc.domain.NpcSkin;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /npc <create|delete|list|movehere|command|lookatplayer|skin|type|equip|glow|pose|scale|action|data>}:
 * the single operator command for NPCs. The root keeps the lifecycle subcommands. {@code create} (reads the
 * operator's position and defaults the skin to their own Bukkit profile, or skinless when unavailable),
 * {@code delete}, {@code list}, {@code movehere} (reads the operator's position), {@code command} (the bound
 * click command), and {@code lookatplayer} (whether the NPC turns to face nearby players), and attaches the skin
 * (name/player/url/texture), appearance (type/equip/glow/pose/scale), data (set/clear/list) and action
 * (add/list/remove/clear) sub-handlers under the one {@code /npc} literal, each contributing argument nodes
 * rather than a new literal. The base {@code uxmessentials.npc.admin} node guards the whole command, and each verb
 * additionally runs behind the capability node its work belongs to ({@code uxmessentials.npc.create}, {@code .delete},
 * {@code .move}, {@code .appearance}, {@code .action}, {@code .view}, {@code .edit}), every one of which defaults to
 * allowed so an existing grant is unchanged until an operator negates one.
 */
@NullMarked
public final class NpcCommand extends NpcCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.npc.admin";
    private static final String GUI_PERMISSION = "uxmessentials.npc.gui";

    private static final String CREATE = "uxmessentials.npc.create";
    private static final String DELETE = "uxmessentials.npc.delete";
    private static final String MOVE = "uxmessentials.npc.move";
    private static final String APPEARANCE = "uxmessentials.npc.appearance";
    private static final String ACTION = "uxmessentials.npc.action";
    private static final String VIEW = "uxmessentials.npc.view";
    private static final String EDIT = "uxmessentials.npc.edit";

    /**
     * Which capability each verb belongs to, so the many verbs of {@code /npc} are grantable in the shapes an
     * operator actually thinks in rather than one at a time. A verb not named here counts as {@link #EDIT}, which
     * keeps a newly added verb gated rather than open.
     */
    private static final Map<String, String> CAPABILITIES = Map.ofEntries(
            Map.entry("create", CREATE),
            Map.entry("createat", CREATE),
            Map.entry("copy", CREATE),
            Map.entry("delete", DELETE),
            Map.entry("list", VIEW),
            Map.entry("info", VIEW),
            Map.entry("nearby", VIEW),
            Map.entry("help", VIEW),
            Map.entry("movehere", MOVE),
            Map.entry("move", MOVE),
            Map.entry("moveto", MOVE),
            Map.entry("teleport", MOVE),
            Map.entry("center", MOVE),
            Map.entry("fix", MOVE),
            Map.entry("command", ACTION),
            Map.entry("action", ACTION),
            Map.entry("skin", APPEARANCE),
            Map.entry("skinslim", APPEARANCE),
            Map.entry("type", APPEARANCE),
            Map.entry("equip", APPEARANCE),
            Map.entry("glow", APPEARANCE),
            Map.entry("pose", APPEARANCE),
            Map.entry("scale", APPEARANCE),
            Map.entry("displayname", APPEARANCE));

    private final NpcSkinCommands skinCommands;
    private final NpcAppearanceCommands appearanceCommands;
    private final NpcDataCommands dataCommands;
    private final NpcActionCommands actionCommands;
    private final NpcStateCommands stateCommands;
    private final NpcListMenu listView;

    public NpcCommand(
            NpcServices services,
            Supplier<? extends Collection<String>> npcNames,
            NpcSkinByName skinByName,
            Messages messages,
            NpcListMenu listView) {
        super(services, npcNames, messages);
        this.skinCommands = new NpcSkinCommands(services, npcNames, skinByName, messages);
        this.appearanceCommands = new NpcAppearanceCommands(services, npcNames, messages);
        this.dataCommands = new NpcDataCommands(services, npcNames, messages);
        this.actionCommands = new NpcActionCommands(services, npcNames, messages);
        this.stateCommands = new NpcStateCommands(services, npcNames, messages);
        this.listView = java.util.Objects.requireNonNull(listView, "listView");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("npc")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::openGui);
        List<LiteralArgumentBuilder<CommandSourceStack>> verbs = new ArrayList<>(List.of(
                Commands.literal("create")
                        .executes(ctx -> usage(ctx, "npc create", "<name> [type]", "Create a new NPC"))
                        .then(nameArgument()
                                .executes(this::create)
                                .then(Commands.literal("at").then(createAtArguments()))
                                .then(Commands.argument("type", StringArgumentType.word())
                                        .executes(this::createTyped))),
                createAtNode(),
                name("delete", this::delete),
                Commands.literal("list")
                        .executes(this::list)
                        .then(Commands.argument("type", StringArgumentType.word())
                                .executes(this::listFiltered)),
                Commands.literal("help").executes(this::help),
                Commands.literal("nearby")
                        .executes(ctx -> nearby(ctx, NearbyNpcs.DEFAULT_RADIUS))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, NearbyNpcs.MAX_RADIUS))
                                .executes(ctx -> nearby(ctx, ctx.getArgument("radius", Integer.class)))),
                name("movehere", this::move),
                moveNode(),
                name("info", this::info),
                name("teleport", this::teleport),
                Commands.literal("copy")
                        .executes(ctx -> usage(ctx, "npc copy", "<name> <target>", "Copy NPC to new name"))
                        .then(nameArgument()
                                .executes(ctx -> usage(ctx, "npc copy", "<name> <target>", "Copy NPC to new name"))
                                .then(Commands.argument("target", StringArgumentType.string())
                                        .executes(this::copy))),
                name("center", this::center),
                name("fix", this::fix),
                greedy("command", this::command),
                bool("lookatplayer", this::lookAtPlayer),
                skinCommands.node(),
                dataCommands.node(),
                actionCommands.node()));
        verbs.addAll(appearanceCommands.nodes());
        verbs.addAll(stateCommands.nodes());
        for (LiteralArgumentBuilder<CommandSourceStack> verb : verbs) {
            root.then(verb.requires(capability(verb.getLiteral())));
        }
        return root.build();
    }

    /**
     * The gate one verb runs behind: the base node, then the capability node its verb belongs to. Every capability
     * node defaults to allowed, so an existing {@code uxmessentials.npc.admin} grant keeps the whole command and an
     * operator narrows it by negating one capability: builder staff who may move and re-dress an NPC but never
     * delete one hold the base node with {@code uxmessentials.npc.admin.delete} negated.
     */
    private static Predicate<CommandSourceStack> capability(String verb) {
        String node = CAPABILITIES.getOrDefault(verb, EDIT);
        return src ->
                src.getSender().hasPermission(PERMISSION) && src.getSender().hasPermission(node);
    }

    @Override
    public String description() {
        return "Create and manage fake-player NPCs.";
    }

    /**
     * {@code /npc} with no arguments: open the management GUI for a player who holds the GUI node, else print the
     * {@code /npc help} text so a console or an operator without the GUI node still gets a useful reply. The GUI
     * node is checked through the live sender's permissions, the same gate the {@code /uxmess gui} hub entry uses.
     */
    private int openGui(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getSender() instanceof Player sender)) {
            feedback.send(ctx.getSource().getSender(), NpcMessageKey.NPC_HELP, java.util.Map.of());
            return Command.SINGLE_SUCCESS;
        }
        if (sender.hasPermission(GUI_PERMISSION)) {
            listView.open(sender, ref(sender));
        } else {
            feedback.send(sender, NpcMessageKey.NPC_HELP, java.util.Map.of());
        }
        return Command.SINGLE_SUCCESS;
    }

    private int create(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        NpcSkin skin = BukkitNpcSkins.of(sender).orElse(null);
        services.create().create(ref(sender), nameArg(ctx), position(sender), skin);
        return Command.SINGLE_SUCCESS;
    }

    private int createTyped(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        String word = ctx.getArgument("type", String.class);
        EntityType type = parseRenderableType(word);
        if (type == null) {
            feedback.send(sender, NpcMessageKey.NPC_INVALID_ENTITY_TYPE, java.util.Map.of("type", word));
            return 0;
        }
        NpcSkin skin = BukkitNpcSkins.of(sender).orElse(null);
        services.create().create(ref(sender), nameArg(ctx), position(sender), skin, type.name());
        return Command.SINGLE_SUCCESS;
    }

    private int delete(CommandContext<CommandSourceStack> ctx) {
        services.delete().delete(actor(ctx), nameArg(ctx));
        return Command.SINGLE_SUCCESS;
    }

    private int list(CommandContext<CommandSourceStack> ctx) {
        services.list().list(actor(ctx));
        return Command.SINGLE_SUCCESS;
    }

    private int listFiltered(CommandContext<CommandSourceStack> ctx) {
        services.list().list(actor(ctx), ctx.getArgument("type", String.class));
        return Command.SINGLE_SUCCESS;
    }

    private int help(CommandContext<CommandSourceStack> ctx) {
        feedback.send(ctx.getSource().getSender(), NpcMessageKey.NPC_HELP, java.util.Map.of());
        return Command.SINGLE_SUCCESS;
    }

    private int nearby(CommandContext<CommandSourceStack> ctx, int radius) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.nearby().nearby(ref(sender), position(sender), radius);
        return Command.SINGLE_SUCCESS;
    }

    private int move(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.move().move(ref(sender), nameArg(ctx), position(sender));
        return Command.SINGLE_SUCCESS;
    }

    private int info(CommandContext<CommandSourceStack> ctx) {
        services.info().describe(actor(ctx), nameArg(ctx));
        return Command.SINGLE_SUCCESS;
    }

    private int teleport(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.teleport().teleport(ref(sender), nameArg(ctx));
        return Command.SINGLE_SUCCESS;
    }

    private int copy(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        NpcName target = NpcName.of(ctx.getArgument("target", String.class));
        services.copy().copy(ref(sender), nameArg(ctx), target, position(sender));
        return Command.SINGLE_SUCCESS;
    }

    private int center(CommandContext<CommandSourceStack> ctx) {
        services.center().center(actor(ctx), nameArg(ctx));
        return Command.SINGLE_SUCCESS;
    }

    private int fix(CommandContext<CommandSourceStack> ctx) {
        services.fix().fix(actor(ctx), nameArg(ctx));
        return Command.SINGLE_SUCCESS;
    }

    private int command(CommandContext<CommandSourceStack> ctx) {
        services.command().setCommand(actor(ctx), nameArg(ctx), value(ctx));
        return Command.SINGLE_SUCCESS;
    }

    private int lookAtPlayer(CommandContext<CommandSourceStack> ctx) {
        services.look().setLookAtPlayer(actor(ctx), nameArg(ctx), ctx.getArgument("value", Boolean.class));
        return Command.SINGLE_SUCCESS;
    }

    /** Explicit-location create form for console/build automation. */
    private LiteralArgumentBuilder<CommandSourceStack> createAtNode() {
        return Commands.literal("createat")
                .executes(ctx -> usage(
                        ctx,
                        "npc createat",
                        "<name> <world> <x> <y> <z> [yaw pitch [type]]",
                        "Create an NPC at an explicit location"))
                .then(Commands.argument("name", StringArgumentType.string()).then(createAtArguments()));
    }

    private LiteralArgumentBuilder<CommandSourceStack> moveNode() {
        return Commands.literal("move")
                .executes(ctx -> usage(ctx, "npc move", "<name> [at <world> <x> <y> <z>]", "Move an NPC"))
                .then(nameArgument()
                        .executes(this::move)
                        .then(Commands.literal("at").then(explicitPositionArguments(this::moveAt, false))));
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> createAtArguments() {
        return explicitPositionArguments(ctx -> createAt(ctx, 0f, 0f, null), true);
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> explicitPositionArguments(
            Command<CommandSourceStack> at, boolean rotationAndType) {
        RequiredArgumentBuilder<CommandSourceStack, Double> z =
                Commands.argument("z", DoubleArgumentType.doubleArg()).executes(at);
        if (rotationAndType) {
            z.then(Commands.argument("yaw", DoubleArgumentType.doubleArg())
                    .then(Commands.argument("pitch", DoubleArgumentType.doubleArg())
                            .executes(ctx -> createAt(ctx, floatArg(ctx, "yaw"), floatArg(ctx, "pitch"), null))
                            .then(Commands.argument("type", StringArgumentType.word())
                                    .executes(ctx -> createAt(
                                            ctx,
                                            floatArg(ctx, "yaw"),
                                            floatArg(ctx, "pitch"),
                                            ctx.getArgument("type", String.class))))));
        }
        return Commands.argument("world", StringArgumentType.word())
                .suggests(com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions.loadedWorlds())
                .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                        .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                .then(z)));
    }

    private int moveAt(CommandContext<CommandSourceStack> ctx) {
        Position at = explicitPosition(ctx, 0f, 0f);
        if (at == null) {
            return 0;
        }
        services.move().move(actor(ctx), nameArg(ctx), at);
        return Command.SINGLE_SUCCESS;
    }

    private int createAt(
            CommandContext<CommandSourceStack> ctx,
            float yaw,
            float pitch,
            @org.jspecify.annotations.Nullable String typeWord) {
        Position at = explicitPosition(ctx, yaw, pitch);
        if (at == null) {
            return 0;
        }
        org.bukkit.command.CommandSender sender = ctx.getSource().getSender();
        EntityType type = typeWord == null ? null : parseRenderableType(typeWord);
        if (typeWord != null && type == null) {
            feedback.send(sender, NpcMessageKey.NPC_INVALID_ENTITY_TYPE, Map.of("type", typeWord));
            return 0;
        }
        NpcSkin skin =
                sender instanceof Player player ? BukkitNpcSkins.of(player).orElse(null) : null;
        services.create().create(actor(ctx), nameArg(ctx), at, skin, type == null ? null : type.name());
        return Command.SINGLE_SUCCESS;
    }

    private @org.jspecify.annotations.Nullable Position explicitPosition(
            CommandContext<CommandSourceStack> ctx, float yaw, float pitch) {
        org.bukkit.command.CommandSender sender = ctx.getSource().getSender();
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
        if (!Double.isFinite(x)
                || !Double.isFinite(y)
                || !Double.isFinite(z)
                || !Float.isFinite(yaw)
                || !Float.isFinite(pitch)) {
            feedback.send(sender, NpcMessageKey.NPC_INVALID_COORDS, Map.of("coords", explicitCoords(ctx)));
            return null;
        }
        return new Position(BukkitRefs.toRef(world), x, y, z, yaw, pitch);
    }

    private static float floatArg(CommandContext<CommandSourceStack> ctx, String name) {
        return (float) (double) ctx.getArgument(name, Double.class);
    }

    private static String explicitCoords(CommandContext<CommandSourceStack> ctx) {
        return ctx.getArgument("world", String.class) + " " + ctx.getArgument("x", Double.class) + " "
                + ctx.getArgument("y", Double.class) + " " + ctx.getArgument("z", Double.class);
    }
}

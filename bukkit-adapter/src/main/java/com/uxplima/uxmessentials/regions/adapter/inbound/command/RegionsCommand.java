package com.uxplima.uxmessentials.regions.adapter.inbound.command;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.regions.adapter.inbound.gui.RegionFlagEditorView;
import com.uxplima.uxmessentials.regions.adapter.inbound.gui.RegionListView;
import com.uxplima.uxmessentials.regions.adapter.inbound.gui.RegionRosterView;
import com.uxplima.uxmessentials.regions.application.RegionsMessageKey;
import com.uxplima.uxmessentials.regions.application.port.RegionService;
import com.uxplima.uxmessentials.regions.domain.RegionMemberChange;
import com.uxplima.uxmessentials.regions.domain.RegionRef;
import com.uxplima.uxmessentials.regions.domain.RegionServiceException;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * {@code /regions}, the WorldGuard region management surface. The base command and each subcommand degrade to a
 * "WorldGuard not installed" line when the bound {@link RegionService} is the no-op fallback (WorldGuard absent), so
 * the whole surface stays inert on a server without WorldGuard.
 *
 * <ul>
 *   <li>{@code /regions [world]} ({@code uxmessentials.regions.list}), open the region list for a world.
 *   <li>{@code /regions pos1|pos2} / {@code /regions create <id>} ({@code uxmessentials.regions.create}), mark the
 *       two corners of a manual selection and define a cuboid region from the WorldEdit selection or those corners.
 *   <li>{@code /regions flags <id>} ({@code uxmessentials.regions.flags}), open the flag editor for a region.
 *   <li>{@code /regions members <id>} and {@code /regions addmember|addowner <id> <player>}
 *       ({@code uxmessentials.regions.members}), open the roster editor and add a player to a region's members or
 *       owners (the target resolved offline-safe by name).
 *   <li>{@code /regions priority <id> <value>} ({@code uxmessentials.regions.admin}): set a region's priority.
 * </ul>
 *
 * <p>Every WorldGuard read and every mutation runs on the global region thread through the injected {@link Scheduler}
 * (never a viewer's region thread); the command validates its inputs and hops off the tick thread, and the region set
 * / roster stays out of the command body.
 */
@NullMarked
public final class RegionsCommand implements CommandRegistration {

    private static final String LIST_PERMISSION = "uxmessentials.regions.list";
    private static final String CREATE_PERMISSION = "uxmessentials.regions.create";
    private static final String FLAGS_PERMISSION = "uxmessentials.regions.flags";
    private static final String MEMBERS_PERMISSION = "uxmessentials.regions.members";
    private static final String ADMIN_PERMISSION = "uxmessentials.regions.admin";

    private final RegionService service;
    private final RegionListView listView;
    private final RegionFlagEditorView flagEditor;
    private final RegionRosterView rosterView;
    private final RegionSelection selection;
    private final PlayerLookup playerLookup;
    private final Scheduler scheduler;
    private final Server server;
    private final CommandFeedback feedback;

    public RegionsCommand(
            RegionService service,
            RegionListView listView,
            RegionFlagEditorView flagEditor,
            RegionRosterView rosterView,
            RegionSelection selection,
            PlayerLookup playerLookup,
            Scheduler scheduler,
            Server server,
            Messages messages) {
        this.service = Objects.requireNonNull(service, "service");
        this.listView = Objects.requireNonNull(listView, "listView");
        this.flagEditor = Objects.requireNonNull(flagEditor, "flagEditor");
        this.rosterView = Objects.requireNonNull(rosterView, "rosterView");
        this.selection = Objects.requireNonNull(selection, "selection");
        this.playerLookup = Objects.requireNonNull(playerLookup, "playerLookup");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.server = Objects.requireNonNull(server, "server");
        this.feedback = new CommandFeedback(Objects.requireNonNull(messages, "messages"));
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("regions")
                .requires(p(LIST_PERMISSION))
                .executes(this::openCurrentWorld)
                .then(Commands.literal("pos1")
                        .requires(p(CREATE_PERMISSION))
                        .executes(ctx -> markCorner(ctx, RegionSelection.Corner.FIRST)))
                .then(Commands.literal("pos2")
                        .requires(p(CREATE_PERMISSION))
                        .executes(ctx -> markCorner(ctx, RegionSelection.Corner.SECOND)))
                .then(Commands.literal("create")
                        .requires(p(CREATE_PERMISSION))
                        .then(Commands.argument("id", StringArgumentType.word()).executes(this::create)))
                .then(Commands.literal("createat")
                        .requires(p(CREATE_PERMISSION))
                        .then(Commands.argument("id", StringArgumentType.word()).then(createAtArguments())))
                .then(Commands.literal("flags")
                        .requires(p(FLAGS_PERMISSION))
                        .then(Commands.argument("id", StringArgumentType.word()).executes(this::openFlags)))
                .then(Commands.literal("members")
                        .requires(p(MEMBERS_PERMISSION))
                        .then(Commands.argument("id", StringArgumentType.word()).executes(this::openMembers)))
                .then(Commands.literal("addmember")
                        .requires(p(MEMBERS_PERMISSION))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .executes(ctx -> add(ctx, RegionMemberChange.Role.MEMBER))
                                        .then(worldArgument()
                                                .executes(ctx -> add(ctx, RegionMemberChange.Role.MEMBER))))))
                .then(Commands.literal("addowner")
                        .requires(p(MEMBERS_PERMISSION))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.argument("player", StringArgumentType.word())
                                        .executes(ctx -> add(ctx, RegionMemberChange.Role.OWNER))
                                        .then(worldArgument()
                                                .executes(ctx -> add(ctx, RegionMemberChange.Role.OWNER))))))
                .then(Commands.literal("priority")
                        .requires(p(ADMIN_PERMISSION))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.argument("value", IntegerArgumentType.integer())
                                        .executes(this::setPriority)
                                        .then(worldArgument().executes(this::setPriority)))))
                .then(worldArgument().executes(this::openNamedWorld))
                .build();
    }

    @Override
    public String description() {
        return "Manage WorldGuard regions: list, create, edit flags, members and priority.";
    }

    private int openCurrentWorld(CommandContext<CommandSourceStack> ctx) {
        Player staff = playerOrReject(ctx);
        if (staff == null) {
            return 0;
        }
        return openList(staff, staff.getWorld());
    }

    private int openNamedWorld(CommandContext<CommandSourceStack> ctx) {
        Player staff = playerOrReject(ctx);
        if (staff == null) {
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "world");
        World world = server.getWorld(name);
        if (world == null) {
            feedback.send(staff, RegionsMessageKey.REGIONS_UNKNOWN_WORLD, Map.of("world", name));
            return Command.SINGLE_SUCCESS;
        }
        return openList(staff, world);
    }

    /**
     * Open the region browser for the world {@code staff} is standing in, the same screen a bare {@code /regions}
     * opens. The {@code /uxmess gui} hub entry routes here so both surfaces share one WorldGuard-present gate.
     */
    public void openBrowser(Player staff) {
        Objects.requireNonNull(staff, "staff");
        openList(staff, staff.getWorld());
    }

    /** Gate on WorldGuard being present, then hand the world to the list view. */
    private int openList(Player staff, World world) {
        if (!service.available()) {
            feedback.send(staff, RegionsMessageKey.REGIONS_NO_WORLDGUARD);
            return Command.SINGLE_SUCCESS;
        }
        listView.open(BukkitRefs.toRef(staff), BukkitRefs.toRef(world));
        return Command.SINGLE_SUCCESS;
    }

    private int markCorner(CommandContext<CommandSourceStack> ctx, RegionSelection.Corner corner) {
        Player staff = playerOrReject(ctx);
        if (staff == null) {
            return 0;
        }
        selection.mark(staff, corner);
        Location at = Objects.requireNonNull(staff.getLocation(), "location");
        feedback.send(
                staff,
                RegionsMessageKey.REGIONS_POS_SET,
                Map.of(
                        "corner", Integer.toString(corner == RegionSelection.Corner.FIRST ? 1 : 2),
                        "x", Integer.toString(at.getBlockX()),
                        "y", Integer.toString(at.getBlockY()),
                        "z", Integer.toString(at.getBlockZ())));
        return Command.SINGLE_SUCCESS;
    }

    private int create(CommandContext<CommandSourceStack> ctx) {
        Player staff = playerOrReject(ctx);
        if (staff == null) {
            return 0;
        }
        if (!service.available()) {
            feedback.send(staff, RegionsMessageKey.REGIONS_NO_WORLDGUARD);
            return Command.SINGLE_SUCCESS;
        }
        Optional<RegionBounds> bounds = selection.boundsFor(staff);
        if (bounds.isEmpty()) {
            feedback.send(staff, RegionsMessageKey.REGIONS_CREATE_NO_SELECTION);
            return Command.SINGLE_SUCCESS;
        }
        String id = StringArgumentType.getString(ctx, "id").toLowerCase(Locale.ROOT);
        WorldRef world = BukkitRefs.toRef(staff.getWorld());
        PlayerRef viewer = BukkitRefs.toRef(staff);
        scheduler.onGlobal(() -> createOnGlobal(staff, viewer, world, id, bounds.get()));
        return Command.SINGLE_SUCCESS;
    }

    private com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> createAtArguments() {
        return worldArgument()
                .then(Commands.argument("x1", DoubleArgumentType.doubleArg())
                        .then(Commands.argument("y1", DoubleArgumentType.doubleArg())
                                .then(Commands.argument("z1", DoubleArgumentType.doubleArg())
                                        .then(Commands.argument("x2", DoubleArgumentType.doubleArg())
                                                .then(Commands.argument("y2", DoubleArgumentType.doubleArg())
                                                        .then(Commands.argument("z2", DoubleArgumentType.doubleArg())
                                                                .executes(this::createAt)))))));
    }

    private int createAt(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!service.available()) {
            feedback.send(sender, RegionsMessageKey.REGIONS_NO_WORLDGUARD);
            return Command.SINGLE_SUCCESS;
        }
        World world = commandWorld(ctx, sender);
        if (world == null) {
            return 0;
        }
        double x1 = DoubleArgumentType.getDouble(ctx, "x1");
        double y1 = DoubleArgumentType.getDouble(ctx, "y1");
        double z1 = DoubleArgumentType.getDouble(ctx, "z1");
        double x2 = DoubleArgumentType.getDouble(ctx, "x2");
        double y2 = DoubleArgumentType.getDouble(ctx, "y2");
        double z2 = DoubleArgumentType.getDouble(ctx, "z2");
        if (!Double.isFinite(x1)
                || !Double.isFinite(y1)
                || !Double.isFinite(z1)
                || !Double.isFinite(x2)
                || !Double.isFinite(y2)
                || !Double.isFinite(z2)) {
            feedback.send(sender, SharedMessageKey.COMMAND_INVALID_POSITION);
            return 0;
        }
        String id = StringArgumentType.getString(ctx, "id").toLowerCase(Locale.ROOT);
        WorldRef worldRef = BukkitRefs.toRef(world);
        RegionBounds bounds = RegionBounds.of(Position.of(worldRef, x1, y1, z1), Position.of(worldRef, x2, y2, z2));
        PlayerRef viewer = CommandFeedback.refOf(sender);
        scheduler.onGlobal(() -> createOnGlobal(sender, viewer, worldRef, id, bounds));
        return Command.SINGLE_SUCCESS;
    }

    /** On the global region thread: reject a duplicate id, else create the region and report the outcome. */
    private void createOnGlobal(CommandSender staff, PlayerRef viewer, WorldRef world, String id, RegionBounds bounds) {
        if (service.region(world, id).isPresent()) {
            scheduler.onEntity(
                    viewer, () -> feedback.send(staff, RegionsMessageKey.REGIONS_CREATE_DUPLICATE, Map.of("id", id)));
            return;
        }
        try {
            service.create(world, id, bounds.min(), bounds.max());
            scheduler.onEntity(
                    viewer, () -> feedback.send(staff, RegionsMessageKey.REGIONS_CREATE_DONE, Map.of("id", id)));
        } catch (RegionServiceException failure) {
            scheduler.onEntity(
                    viewer, () -> feedback.send(staff, RegionsMessageKey.REGIONS_CREATE_FAILED, Map.of("id", id)));
        }
    }

    private int openFlags(CommandContext<CommandSourceStack> ctx) {
        Player staff = playerOrReject(ctx);
        if (staff == null) {
            return 0;
        }
        if (!service.available()) {
            feedback.send(staff, RegionsMessageKey.REGIONS_NO_WORLDGUARD);
            return Command.SINGLE_SUCCESS;
        }
        String id = StringArgumentType.getString(ctx, "id").toLowerCase(Locale.ROOT);
        WorldRef world = BukkitRefs.toRef(staff.getWorld());
        scheduler.onGlobal(() -> openFlagsOnGlobal(staff, world, id));
        return Command.SINGLE_SUCCESS;
    }

    /** On the global region thread: resolve the region, then either open its flag editor or refuse an unknown id. */
    private void openFlagsOnGlobal(Player staff, WorldRef world, String id) {
        PlayerRef ref = BukkitRefs.toRef(staff);
        Optional<RegionRef> region = service.region(world, id);
        if (region.isEmpty()) {
            scheduler.onEntity(
                    ref, () -> feedback.send(staff, RegionsMessageKey.REGIONS_UNKNOWN_REGION, Map.of("id", id)));
            return;
        }
        flagEditor.open(ref, region.get());
    }

    private int openMembers(CommandContext<CommandSourceStack> ctx) {
        Player staff = playerOrReject(ctx);
        if (staff == null) {
            return 0;
        }
        if (!service.available()) {
            feedback.send(staff, RegionsMessageKey.REGIONS_NO_WORLDGUARD);
            return Command.SINGLE_SUCCESS;
        }
        String id = StringArgumentType.getString(ctx, "id").toLowerCase(Locale.ROOT);
        WorldRef world = BukkitRefs.toRef(staff.getWorld());
        scheduler.onGlobal(() -> openMembersOnGlobal(staff, world, id));
        return Command.SINGLE_SUCCESS;
    }

    /** On the global region thread: resolve the region, then open its roster editor or refuse an unknown id. */
    private void openMembersOnGlobal(Player staff, WorldRef world, String id) {
        PlayerRef ref = BukkitRefs.toRef(staff);
        Optional<RegionRef> region = service.region(world, id);
        if (region.isEmpty()) {
            scheduler.onEntity(
                    ref, () -> feedback.send(staff, RegionsMessageKey.REGIONS_UNKNOWN_REGION, Map.of("id", id)));
            return;
        }
        rosterView.open(ref, region.get());
    }

    private int add(CommandContext<CommandSourceStack> ctx, RegionMemberChange.Role role) {
        CommandSender staff = ctx.getSource().getSender();
        if (!service.available()) {
            feedback.send(staff, RegionsMessageKey.REGIONS_NO_WORLDGUARD);
            return Command.SINGLE_SUCCESS;
        }
        World resolvedWorld = commandWorld(ctx, staff);
        if (resolvedWorld == null) {
            return 0;
        }
        String id = StringArgumentType.getString(ctx, "id").toLowerCase(Locale.ROOT);
        String targetName = StringArgumentType.getString(ctx, "player");
        WorldRef world = BukkitRefs.toRef(resolvedWorld);
        PlayerRef viewer = CommandFeedback.refOf(staff);
        // Offline-safe name resolution can hit the profile cache, so it runs off the tick thread before the write.
        scheduler.async(() -> resolveAndAdd(staff, viewer, world, id, targetName, role));
        return Command.SINGLE_SUCCESS;
    }

    /** Off-thread: resolve the target by name; if found, apply the add on the region thread, else refuse. */
    private void resolveAndAdd(
            CommandSender staff,
            PlayerRef viewer,
            WorldRef world,
            String id,
            String targetName,
            RegionMemberChange.Role role) {
        Optional<PlayerRef> target = playerLookup.findByName(targetName);
        if (target.isEmpty()) {
            scheduler.onEntity(
                    viewer,
                    () -> feedback.send(
                            staff, RegionsMessageKey.REGIONS_MEMBERS_UNKNOWN_PLAYER, Map.of("name", targetName)));
            return;
        }
        scheduler.onGlobal(() -> applyAddOnGlobal(staff, viewer, world, id, target.get(), role));
    }

    /** On the global region thread: reject an unknown region, else add the resolved player and report the outcome. */
    private void applyAddOnGlobal(
            CommandSender staff,
            PlayerRef viewer,
            WorldRef world,
            String id,
            PlayerRef target,
            RegionMemberChange.Role role) {
        Optional<RegionRef> region = service.region(world, id);
        if (region.isEmpty()) {
            scheduler.onEntity(
                    viewer, () -> feedback.send(staff, RegionsMessageKey.REGIONS_UNKNOWN_REGION, Map.of("id", id)));
            return;
        }
        Map<String, String> placeholders = Map.of("name", target.name(), "id", id);
        try {
            service.applyMemberChange(
                    new RegionMemberChange(region.get(), target.uuid(), role, RegionMemberChange.Action.ADD));
        } catch (RegionServiceException failure) {
            scheduler.onEntity(
                    viewer, () -> feedback.send(staff, RegionsMessageKey.REGIONS_MEMBERS_FAILED, Map.of("id", id)));
            return;
        }
        RegionsMessageKey done = role == RegionMemberChange.Role.OWNER
                ? RegionsMessageKey.REGIONS_MEMBERS_ADDED_OWNER
                : RegionsMessageKey.REGIONS_MEMBERS_ADDED_MEMBER;
        scheduler.onEntity(viewer, () -> feedback.send(staff, done, placeholders));
    }

    private int setPriority(CommandContext<CommandSourceStack> ctx) {
        CommandSender staff = ctx.getSource().getSender();
        if (!service.available()) {
            feedback.send(staff, RegionsMessageKey.REGIONS_NO_WORLDGUARD);
            return Command.SINGLE_SUCCESS;
        }
        World resolvedWorld = commandWorld(ctx, staff);
        if (resolvedWorld == null) {
            return 0;
        }
        String id = StringArgumentType.getString(ctx, "id").toLowerCase(Locale.ROOT);
        int value = IntegerArgumentType.getInteger(ctx, "value");
        WorldRef world = BukkitRefs.toRef(resolvedWorld);
        PlayerRef viewer = CommandFeedback.refOf(staff);
        scheduler.onGlobal(() -> setPriorityOnGlobal(staff, viewer, world, id, value));
        return Command.SINGLE_SUCCESS;
    }

    /** On the global region thread: reject an unknown region, else set the priority and report the outcome. */
    private void setPriorityOnGlobal(CommandSender staff, PlayerRef viewer, WorldRef world, String id, int value) {
        Optional<RegionRef> region = service.region(world, id);
        if (region.isEmpty()) {
            scheduler.onEntity(
                    viewer, () -> feedback.send(staff, RegionsMessageKey.REGIONS_UNKNOWN_REGION, Map.of("id", id)));
            return;
        }
        Map<String, String> placeholders = Map.of("id", id, "priority", Integer.toString(value));
        try {
            service.setPriority(region.get(), value);
        } catch (RegionServiceException failure) {
            scheduler.onEntity(
                    viewer, () -> feedback.send(staff, RegionsMessageKey.REGIONS_PRIORITY_FAILED, Map.of("id", id)));
            return;
        }
        scheduler.onEntity(viewer, () -> feedback.send(staff, RegionsMessageKey.REGIONS_PRIORITY_SET, placeholders));
    }

    private com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> worldArgument() {
        return Commands.argument("world", StringArgumentType.word()).suggests(CommandSuggestions.loadedWorlds());
    }

    /** Explicit world wins; otherwise a live player's current world supplies the context. */
    private @Nullable World commandWorld(CommandContext<CommandSourceStack> ctx, CommandSender sender) {
        try {
            String name = StringArgumentType.getString(ctx, "world");
            World world = server.getWorld(name);
            if (world == null) {
                feedback.send(sender, RegionsMessageKey.REGIONS_UNKNOWN_WORLD, Map.of("world", name));
            }
            return world;
        } catch (IllegalArgumentException absent) {
            if (sender instanceof Player player) {
                return player.getWorld();
            }
            feedback.send(sender, SharedMessageKey.COMMAND_PLAYERS_ONLY);
            return null;
        }
    }

    private @Nullable Player playerOrReject(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (sender instanceof Player player) {
            return player;
        }
        feedback.send(sender, SharedMessageKey.COMMAND_PLAYERS_ONLY);
        return null;
    }

    private static Predicate<CommandSourceStack> p(String permission) {
        return src -> src.getSender().hasPermission(permission);
    }
}

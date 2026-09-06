package com.uxplima.uxmessentials.regions.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import com.uxplima.uxmessentials.regions.adapter.inbound.gui.RegionFlagEditorView;
import com.uxplima.uxmessentials.regions.adapter.inbound.gui.RegionListView;
import com.uxplima.uxmessentials.regions.adapter.inbound.gui.RegionRosterView;
import com.uxplima.uxmessentials.regions.adapter.outbound.NoWorldGuardRegionService;
import com.uxplima.uxmessentials.regions.application.port.RegionService;
import com.uxplima.uxmessentials.regions.domain.FlagDescriptor;
import com.uxplima.uxmessentials.regions.domain.FlagValue;
import com.uxplima.uxmessentials.regions.domain.RegionMemberChange;
import com.uxplima.uxmessentials.regions.domain.RegionRef;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.shared.menu.TestMenuEngine;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of {@code /regions}: the list is gated by {@code .list} and degrades on the no-op service; the
 * {@code create} and {@code flags} subcommands are gated by their own permissions; {@code /regions create} defines a
 * cuboid from the two corners marked with {@code pos1}/{@code pos2} (WorldEdit is not on the classpath, so the marked
 * corners are the selection), rejecting a missing selection and a duplicate id. WorldGuard is exercised only through
 * the {@link RegionService} port and a fake.
 */
class RegionsCommandTest {

    private static final String LIST = "uxmessentials.regions.list";
    private static final String CREATE = "uxmessentials.regions.create";
    private static final String FLAGS = "uxmessentials.regions.flags";
    private static final String MEMBERS = "uxmessentials.regions.members";
    private static final String ADMIN = "uxmessentials.regions.admin";

    private ServerMock server;
    private Plugin plugin;
    private Scheduler scheduler;
    private Messages messages;
    private GuiText guiText;
    private Menus menus;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        scheduler = new SyncScheduler();
        messages = keyEcho();
        guiText = new GuiText(messages);
        TestMenuEngine engine = TestMenuEngine.create(messages, scheduler);
        engine.installListener(plugin);
        menus = engine.menus();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theCommandIsGatedByTheListPermission() {
        RegionsCommand command = command(new NoWorldGuardRegionService());
        PlayerMock permitted = server.addPlayer("Permitted");
        permitted.addAttachment(plugin, LIST, true);
        PlayerMock denied = server.addPlayer("Denied");

        assertThat(command.build().getRequirement().test(CommandSourceStackMock.from(permitted)))
                .isTrue();
        assertThat(command.build().getRequirement().test(CommandSourceStackMock.from(denied)))
                .isFalse();
    }

    @Test
    void theCreateAndFlagsSubcommandsAreGatedByTheirOwnPermissions() {
        RegionsCommand command = command(new FakeRegionService(true));
        PlayerMock creator = server.addPlayer("Creator");
        creator.addAttachment(plugin, CREATE, true);
        PlayerMock flagger = server.addPlayer("Flagger");
        flagger.addAttachment(plugin, FLAGS, true);
        PlayerMock bare = server.addPlayer("Bare");

        CommandNode<CommandSourceStack> create = command.build().getChild("create");
        CommandNode<CommandSourceStack> flags = command.build().getChild("flags");

        assertThat(create.getRequirement().test(CommandSourceStackMock.from(creator)))
                .isTrue();
        assertThat(create.getRequirement().test(CommandSourceStackMock.from(bare)))
                .isFalse();
        assertThat(flags.getRequirement().test(CommandSourceStackMock.from(flagger)))
                .isTrue();
        assertThat(flags.getRequirement().test(CommandSourceStackMock.from(bare)))
                .isFalse();
    }

    @Test
    void withoutWorldGuardItRepliesNotInstalledAndOpensNoWindow() {
        RegionsCommand command = command(new NoWorldGuardRegionService());
        PlayerMock staff = permittedPlayer("Staff", LIST);

        dispatch(command, CommandSourceStackMock.from(staff), "regions");

        assertThat(staff.nextMessage()).contains("regions.no-worldguard");
        assertThat(menuHolderOpen(staff)).isFalse();
    }

    /** Whether {@code player} currently has an engine menu open: null-safe (no window means a null top inventory). */
    private static boolean menuHolderOpen(PlayerMock player) {
        Inventory top = player.getOpenInventory().getTopInventory();
        return top != null && top.getHolder() instanceof MenuHolder;
    }

    @Test
    void withWorldGuardAnUnknownWorldIsRefused() {
        RegionsCommand command = command(new FakeRegionService(true));
        PlayerMock staff = permittedPlayer("Staff", LIST);

        dispatch(command, CommandSourceStackMock.from(staff), "regions ghostworld");

        assertThat(staff.nextMessage()).contains("regions.unknown-world");
    }

    @Test
    void withWorldGuardTheCurrentWorldOpensTheList() {
        FakeRegionService service = new FakeRegionService(true);
        PlayerMock staff = permittedPlayer("Staff", LIST);
        service.add(new RegionRef(worldOf(staff), "spawn"));
        RegionsCommand command = command(service);

        dispatch(command, CommandSourceStackMock.from(staff), "regions");

        assertThat(staff.getOpenInventory().getTopInventory().getHolder()).isInstanceOf(MenuHolder.class);
    }

    @Test
    void createDefinesACuboidFromTheTwoMarkedCorners() {
        FakeRegionService service = new FakeRegionService(true);
        RegionsCommand command = command(service);
        PlayerMock staff = permittedPlayer("Staff", LIST, CREATE);

        staff.setLocation(new Location(staff.getWorld(), 10, 64, 10));
        dispatch(command, CommandSourceStackMock.from(staff), "regions pos1");
        staff.setLocation(new Location(staff.getWorld(), 20, 70, 30));
        dispatch(command, CommandSourceStackMock.from(staff), "regions pos2");
        dispatch(command, CommandSourceStackMock.from(staff), "regions create arena");

        FakeRegionService.Created created = Objects.requireNonNull(service.lastCreate());
        assertThat(created.id()).isEqualTo("arena");
        assertThat(created.world()).isEqualTo(worldOf(staff));
        assertThat(blockCoords(created.min())).containsExactly(10, 64, 10);
        assertThat(blockCoords(created.max())).containsExactly(20, 70, 30);
    }

    @Test
    void consoleCanCreateARegionFromExplicitWorldAndCorners() {
        FakeRegionService service = new FakeRegionService(true);
        RegionsCommand command = command(service);
        server.addSimpleWorld("world");

        dispatch(
                command,
                CommandSourceStackMock.from(server.getConsoleSender()),
                "regions createat arena world 20 70 30 10 64 10");

        FakeRegionService.Created created = Objects.requireNonNull(service.lastCreate());
        assertThat(created.id()).isEqualTo("arena");
        assertThat(created.world().name()).isEqualTo("world");
        assertThat(blockCoords(created.min())).containsExactly(10, 64, 10);
        assertThat(blockCoords(created.max())).containsExactly(20, 70, 30);
    }

    @Test
    void createWithoutASelectionIsRejected() {
        FakeRegionService service = new FakeRegionService(true);
        RegionsCommand command = command(service);
        PlayerMock staff = permittedPlayer("Staff", LIST, CREATE);

        dispatch(command, CommandSourceStackMock.from(staff), "regions create arena");

        assertThat(staff.nextMessage()).contains("regions.create.no-selection");
        assertThat(service.lastCreate()).isNull();
    }

    @Test
    void createRejectsADuplicateId() {
        FakeRegionService service = new FakeRegionService(true);
        PlayerMock staff = permittedPlayer("Staff", LIST, CREATE);
        service.add(new RegionRef(worldOf(staff), "arena"));
        RegionsCommand command = command(service);

        staff.setLocation(new Location(staff.getWorld(), 1, 64, 1));
        dispatch(command, CommandSourceStackMock.from(staff), "regions pos1");
        staff.setLocation(new Location(staff.getWorld(), 9, 70, 9));
        dispatch(command, CommandSourceStackMock.from(staff), "regions pos2");
        drain(staff);
        dispatch(command, CommandSourceStackMock.from(staff), "regions create arena");

        assertThat(staff.nextMessage()).contains("regions.create.duplicate");
        assertThat(service.lastCreate()).isNull();
    }

    @Test
    void flagsOnAnUnknownRegionIsRefused() {
        RegionsCommand command = command(new FakeRegionService(true));
        PlayerMock staff = permittedPlayer("Staff", LIST, FLAGS);

        dispatch(command, CommandSourceStackMock.from(staff), "regions flags ghost");

        assertThat(staff.nextMessage()).contains("regions.unknown-region");
    }

    @Test
    void flagsOnAKnownRegionOpensTheFlagEditor() {
        FakeRegionService service = new FakeRegionService(true);
        PlayerMock staff = permittedPlayer("Staff", LIST, FLAGS);
        service.add(new RegionRef(worldOf(staff), "spawn"));
        RegionsCommand command = command(service);

        dispatch(command, CommandSourceStackMock.from(staff), "regions flags spawn");

        assertThat(staff.getOpenInventory().getTopInventory().getHolder()).isInstanceOf(MenuHolder.class);
    }

    @Test
    void theMembersAndPrioritySubcommandsAreGatedByTheirOwnPermissions() {
        RegionsCommand command = command(new FakeRegionService(true));
        PlayerMock roster = server.addPlayer("Roster");
        roster.addAttachment(plugin, MEMBERS, true);
        PlayerMock admin = server.addPlayer("Admin");
        admin.addAttachment(plugin, ADMIN, true);
        PlayerMock bare = server.addPlayer("Bare");

        CommandNode<CommandSourceStack> members = command.build().getChild("members");
        CommandNode<CommandSourceStack> priority = command.build().getChild("priority");

        assertThat(members.getRequirement().test(CommandSourceStackMock.from(roster)))
                .isTrue();
        assertThat(members.getRequirement().test(CommandSourceStackMock.from(bare)))
                .isFalse();
        assertThat(priority.getRequirement().test(CommandSourceStackMock.from(admin)))
                .isTrue();
        assertThat(priority.getRequirement().test(CommandSourceStackMock.from(bare)))
                .isFalse();
    }

    @Test
    void addmemberResolvesTheTargetOfflineAndAddsThemThroughThePort() {
        FakeRegionService service = new FakeRegionService(true);
        FakePlayerLookup lookup = new FakePlayerLookup();
        UUID target = UUID.randomUUID();
        lookup.register("Notch", target);
        PlayerMock staff = permittedPlayer("Staff", LIST, MEMBERS);
        service.add(new RegionRef(worldOf(staff), "spawn"));
        RegionsCommand command = command(service, lookup);

        dispatch(command, CommandSourceStackMock.from(staff), "regions addmember spawn Notch");

        assertThat(service.rosterChanges())
                .containsExactly(new RegionMemberChange(
                        new RegionRef(worldOf(staff), "spawn"),
                        target,
                        RegionMemberChange.Role.MEMBER,
                        RegionMemberChange.Action.ADD));
    }

    @Test
    void addownerAddsTheResolvedPlayerAsAnOwner() {
        FakeRegionService service = new FakeRegionService(true);
        FakePlayerLookup lookup = new FakePlayerLookup();
        UUID target = UUID.randomUUID();
        lookup.register("Jeb", target);
        PlayerMock staff = permittedPlayer("Staff", LIST, MEMBERS);
        service.add(new RegionRef(worldOf(staff), "shop"));
        RegionsCommand command = command(service, lookup);

        dispatch(command, CommandSourceStackMock.from(staff), "regions addowner shop Jeb");

        assertThat(service.rosterChanges())
                .containsExactly(new RegionMemberChange(
                        new RegionRef(worldOf(staff), "shop"),
                        target,
                        RegionMemberChange.Role.OWNER,
                        RegionMemberChange.Action.ADD));
    }

    @Test
    void addmemberWithAnUnknownPlayerIsRefused() {
        FakeRegionService service = new FakeRegionService(true);
        PlayerMock staff = permittedPlayer("Staff", LIST, MEMBERS);
        service.add(new RegionRef(worldOf(staff), "spawn"));
        RegionsCommand command = command(service, new FakePlayerLookup());

        dispatch(command, CommandSourceStackMock.from(staff), "regions addmember spawn Ghost");

        assertThat(staff.nextMessage()).contains("regions.members.unknown-player");
        assertThat(service.rosterChanges()).isEmpty();
    }

    @Test
    void prioritySetsTheRegionPriorityThroughThePort() {
        FakeRegionService service = new FakeRegionService(true);
        PlayerMock staff = permittedPlayer("Staff", LIST, ADMIN);
        service.add(new RegionRef(worldOf(staff), "spawn"));
        RegionsCommand command = command(service);

        dispatch(command, CommandSourceStackMock.from(staff), "regions priority spawn 7");

        assertThat(service.lastPriority()).isEqualTo(7);
        assertThat(staff.nextMessage()).contains("regions.priority.set");
    }

    @Test
    void membersOnAnUnknownRegionIsRefused() {
        RegionsCommand command = command(new FakeRegionService(true));
        PlayerMock staff = permittedPlayer("Staff", LIST, MEMBERS);

        dispatch(command, CommandSourceStackMock.from(staff), "regions members ghost");

        assertThat(staff.nextMessage()).contains("regions.unknown-region");
    }

    private RegionsCommand command(RegionService service) {
        return command(service, new FakePlayerLookup());
    }

    private RegionsCommand command(RegionService service, PlayerLookup playerLookup) {
        RegionRosterView rosterView = new RegionRosterView(
                menus,
                guiText,
                scheduler,
                messages,
                noopSink(),
                service,
                playerLookup,
                EntityListLayout.paginatedDefault(Material.PLAYER_HEAD));
        RegionFlagEditorView flagEditor = new RegionFlagEditorView(
                menus,
                guiText,
                scheduler,
                messages,
                noopSink(),
                service,
                (player, viewer, request, onSubmit, onCancel) -> {},
                List.of("pvp", "build"),
                EntityListLayout.paginatedDefault(Material.GRAY_DYE),
                (clicker, region) -> {});
        RegionListView listView = new RegionListView(
                menus,
                guiText,
                scheduler,
                messages,
                noopSink(),
                service,
                EntityListLayout.paginatedDefault(Material.PAPER),
                (Player clicker, RegionRef region) ->
                        flagEditor.open(new PlayerRef(clicker.getUniqueId(), clicker.getName()), region));
        return new RegionsCommand(
                service,
                listView,
                flagEditor,
                rosterView,
                new WorldEditRegionSelection(server),
                playerLookup,
                scheduler,
                server,
                messages);
    }

    private static WorldRef worldOf(PlayerMock player) {
        return new WorldRef(player.getWorld().getUID(), player.getWorld().getName());
    }

    private static List<Integer> blockCoords(Position position) {
        return List.of(position.blockX(), position.blockY(), position.blockZ());
    }

    private PlayerMock permittedPlayer(String name, String... permissions) {
        PlayerMock player = server.addPlayer(name);
        for (String permission : permissions) {
            player.addAttachment(plugin, permission, true);
        }
        return player;
    }

    private static void drain(PlayerMock player) {
        while (player.nextMessage() != null) {
            // discard the queued messages so a later assertion reads only what follows
        }
    }

    private static void dispatch(RegionsCommand command, CommandSourceStack source, String input) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(command.build());
        try {
            dispatcher.execute(input, source);
        } catch (CommandSyntaxException syntax) {
            throw new IllegalStateException("command dispatch failed: " + input, syntax);
        }
    }

    private static Messages keyEcho() {
        return (viewer, key, placeholders) -> key.key();
    }

    private static MessageSink noopSink() {
        return (viewer, renderedText) -> {};
    }

    /** An in-memory {@link RegionService} whose reads answer for every world, recording create/roster/priority calls. */
    private static final class FakeRegionService implements RegionService {
        private final boolean available;
        private final List<RegionRef> regions = new ArrayList<>();
        private final List<RegionMemberChange> rosterChanges = new ArrayList<>();
        private @Nullable Created lastCreate;
        private @Nullable Integer lastPriority;

        FakeRegionService(boolean available) {
            this.available = available;
        }

        void add(RegionRef region) {
            regions.add(region);
        }

        @Nullable Created lastCreate() {
            return lastCreate;
        }

        List<RegionMemberChange> rosterChanges() {
            return rosterChanges;
        }

        @Nullable Integer lastPriority() {
            return lastPriority;
        }

        @Override
        public boolean available() {
            return available;
        }

        @Override
        public List<RegionRef> regionsIn(WorldRef world) {
            return List.copyOf(regions);
        }

        @Override
        public Optional<RegionRef> region(WorldRef world, String id) {
            return regions.stream()
                    .filter(r -> r.world().equals(world) && r.id().equals(id))
                    .findFirst();
        }

        @Override
        public List<RegionRef> regionsAt(Position position) {
            return List.of();
        }

        @Override
        public List<FlagValue> flags(RegionRef region) {
            return List.of();
        }

        @Override
        public List<FlagDescriptor> flagDescriptors(RegionRef region) {
            return List.of();
        }

        @Override
        public List<String> members(RegionRef region) {
            return List.of();
        }

        @Override
        public List<String> owners(RegionRef region) {
            return List.of();
        }

        @Override
        public int priority(RegionRef region) {
            return 0;
        }

        @Override
        public RegionRef create(WorldRef world, String id, Position min, Position max) {
            this.lastCreate = new Created(world, id, min, max);
            RegionRef ref = new RegionRef(world, id);
            regions.add(ref);
            return ref;
        }

        @Override
        public void setFlag(RegionRef region, FlagValue flag) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void applyMemberChange(RegionMemberChange change) {
            rosterChanges.add(change);
        }

        @Override
        public void setPriority(RegionRef region, int priority) {
            this.lastPriority = priority;
        }

        /** A recorded {@code create} call. */
        record Created(WorldRef world, String id, Position min, Position max) {}
    }

    /** A {@link PlayerLookup} that resolves a fixed set of names to stable uuids, else empty (an unknown player). */
    private static final class FakePlayerLookup implements PlayerLookup {
        private final java.util.Map<String, UUID> byName = new java.util.HashMap<>();

        void register(String name, UUID uuid) {
            byName.put(name, uuid);
        }

        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return findByName(name);
        }

        @Override
        public Optional<PlayerRef> findByName(String name) {
            UUID uuid = byName.get(name);
            return uuid == null ? Optional.empty() : Optional.of(new PlayerRef(uuid, name));
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            return byName.entrySet().stream()
                    .filter(entry -> entry.getValue().equals(uuid))
                    .findFirst()
                    .map(entry -> new PlayerRef(uuid, entry.getKey()));
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return false;
        }
    }

    /** Runs every scheduler hop inline. */
    private static final class SyncScheduler implements Scheduler {
        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }
}

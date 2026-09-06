package com.uxplima.uxmessentials.warps.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.plugin.Plugin;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.CommandDispatcher;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.shared.menu.TestMenuEngine;
import com.uxplima.uxmessentials.warps.adapter.WarpServices;
import com.uxplima.uxmessentials.warps.adapter.inbound.command.WarpCommand;
import com.uxplima.uxmessentials.warps.adapter.inbound.gui.WarpBrowseMenu;
import com.uxplima.uxmessentials.warps.application.DelWarp;
import com.uxplima.uxmessentials.warps.application.ListWarps;
import com.uxplima.uxmessentials.warps.application.MoveWarp;
import com.uxplima.uxmessentials.warps.application.SetWarp;
import com.uxplima.uxmessentials.warps.application.UseWarp;
import com.uxplima.uxmessentials.warps.application.WarpAccess;
import com.uxplima.uxmessentials.warps.application.WarpInfo;
import com.uxplima.uxmessentials.warps.application.WarpsMessageKey;
import com.uxplima.uxmessentials.warps.application.port.WarpEconomy;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.application.port.WarpTeleporter;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the warps command's {@code /warp list} paths and per-subcommand permission gating. In
 * {@code chat} display mode {@code /warp list} prints the clickable chat list, asserted by the {@code WARP_LIST}
 * keys it produces and the absence of any open inventory; the GUI display mode's slot-for-slot rendering lives in
 * {@code WarpBrowseGoldenTest}, so this class no longer opens an inventory. Every subcommand is reachable only with
 * its own permission, and {@code /warp create} and {@code /warp set} are aliases that both create a warp. The
 * scheduler is a synchronous double so the entity-bound work runs inline.
 */
class WarpsMenuPathTest {

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private WarpServices services;
    private RecordingSink sink;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        player.setOp(true);
        sink = new RecordingSink();
        services = services();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void warpListInChatModeListsInChatAndOpensNoInventory() {
        CommandDispatcher<CommandSourceStack> dispatcher =
                registerCommand(com.uxplima.uxmessentials.shared.adapter.inbound.command.ListDisplayMode.CHAT);

        execute(dispatcher, "warp list");

        assertThat(sink.keys).contains(WarpsMessageKey.WARP_LIST_HEADER);
        // Chat mode opens no inventory at all, so the player has no top inventory to hold a menu.
        assertThat(player.getOpenInventory().getTopInventory()).isNull();
    }

    @Test
    void rootRequiresTheUsePermission() {
        var command = new WarpCommand(
                services,
                new KeyMessages(),
                () -> com.uxplima.uxmessentials.shared.adapter.inbound.command.ListDisplayMode.GUI);
        assertThat(command.build().getRequirement().test(sourceFor("uxmessentials.warp.use")))
                .isTrue();
    }

    @Test
    void listSubcommandRequiresTheListPermission() {
        assertCanUse("list", "uxmessentials.warp.list", "uxmessentials.warp.use");
    }

    @Test
    void setSubcommandRequiresTheSetPermission() {
        assertCanUse("set", "uxmessentials.warp.set", "uxmessentials.warp.use");
    }

    @Test
    void createSubcommandRequiresTheSetPermission() {
        assertCanUse("create", "uxmessentials.warp.set", "uxmessentials.warp.use");
    }

    @Test
    void createAndSetAreAliasesThatBothCreateAWarp() {
        CommandDispatcher<CommandSourceStack> dispatcher =
                registerCommand(com.uxplima.uxmessentials.shared.adapter.inbound.command.ListDisplayMode.GUI);

        execute(dispatcher, "warp create viacreate");
        execute(dispatcher, "warp set viaset");

        // Both literals run the same SetWarp use case, which emits WARP_SET when it creates a new warp.
        assertThat(java.util.Collections.frequency(sink.keys, WarpsMessageKey.WARP_SET))
                .as("both /warp create and /warp set should create a warp")
                .isEqualTo(2);
    }

    @Test
    void delSubcommandRequiresTheDeletePermission() {
        assertCanUse("del", "uxmessentials.warp.delete", "uxmessentials.warp.use");
    }

    @Test
    void infoSubcommandRequiresTheInfoPermission() {
        assertCanUse("info", "uxmessentials.warp.info", "uxmessentials.warp.use");
    }

    @Test
    void moveSubcommandRequiresTheMovePermission() {
        assertCanUse("move", "uxmessentials.warp.move", "uxmessentials.warp.use");
    }

    /**
     * Asserts the {@code subcommand} literal under {@code /warp} is reachable only with {@code grantsAccess}
     * and not with {@code deniedNode} alone. Proving Brigadier {@code .requires(...)} gates the subcommand by
     * its own permission rather than the root's.
     */
    private void assertCanUse(String subcommand, String grantsAccess, String deniedNode) {
        var root = new WarpCommand(
                        services,
                        new KeyMessages(),
                        () -> com.uxplima.uxmessentials.shared.adapter.inbound.command.ListDisplayMode.GUI)
                .build();
        var node = root.getChild(subcommand);
        assertThat(node).as("subcommand '%s' exists under /warp", subcommand).isNotNull();
        assertThat(node.canUse(sourceFor(grantsAccess)))
                .as("'%s' should be usable with %s", subcommand, grantsAccess)
                .isTrue();
        assertThat(node.canUse(sourceFor(deniedNode)))
                .as("'%s' should not be usable with only %s", subcommand, deniedNode)
                .isFalse();
    }

    /** A command source for a fresh player holding exactly {@code node} (and no op). */
    private CommandSourceStack sourceFor(String node) {
        PlayerMock holder = server.addPlayer();
        holder.addAttachment(plugin, node, true);
        return CommandSourceStackMock.from(holder);
    }

    private CommandDispatcher<CommandSourceStack> registerCommand(
            com.uxplima.uxmessentials.shared.adapter.inbound.command.ListDisplayMode mode) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(new WarpCommand(services, new KeyMessages(), () -> mode).build());
        return dispatcher;
    }

    private void execute(CommandDispatcher<CommandSourceStack> dispatcher, String input) {
        try {
            dispatcher.execute(input, CommandSourceStackMock.from(player));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new AssertionError("command did not parse: " + input, e);
        }
    }

    private WarpServices services() {
        Messages messages = new KeyMessages();
        Permissions permissions = new AllowAllPermissions();
        Notifier notifier = new Notifier(messages, sink);
        WarpRepository repository = new FakeRepository();
        WarpTeleporter teleporter = new RecordingTeleporter();
        WarpAccess access = new WarpAccess(permissions, Optional.<WarpEconomy>empty());
        Clock clock = Clock.systemUTC();
        UseWarp useWarp =
                new UseWarp(repository, access, teleporter, notifier, pos -> true, permissions, new SyncScheduler());
        // The browse menu is a WarpServices collaborator these command-path tests never open, so it stands up over a
        // bare test engine façade with no spec registered.
        WarpBrowseMenu warpMenu = new WarpBrowseMenu(
                TestMenuEngine.create(messages, new SyncScheduler()).menus(),
                new SyncScheduler(),
                useWarp,
                messages,
                new StubWarpCategoryRepository());
        return new WarpServices(
                useWarp,
                new SetWarp(repository, notifier, new NoEvents(), clock, List.of()),
                new DelWarp(repository, notifier, new NoEvents()),
                new ListWarps(repository, permissions, notifier),
                new WarpInfo(repository, notifier),
                new MoveWarp(repository, notifier),
                warpMenu,
                new NoPlayerLookup(),
                repository,
                null,
                null,
                new SyncScheduler());
    }

    /** Three free, ungated, owner-attributed warps. */
    private static final class FakeRepository implements WarpRepository {
        private final List<Warp> warps = warps();

        private static List<Warp> warps() {
            WorldRef world = new WorldRef(UUID.randomUUID(), "world");
            PlayerRef owner = new PlayerRef(UUID.randomUUID(), "Owner");
            Instant now = Instant.now();
            return List.of(
                    Warp.create(WarpName.of("spawn"), Position.of(world, 0, 64, 0), owner, now),
                    Warp.create(WarpName.of("shop"), Position.of(world, 10, 64, 10), owner, now),
                    Warp.create(WarpName.of("pvp"), Position.of(world, 20, 64, 20), owner, now));
        }

        @Override
        public Optional<Warp> find(WarpName name) {
            return warps.stream().filter(warp -> warp.name().equals(name)).findFirst();
        }

        @Override
        public List<Warp> all() {
            return warps;
        }

        @Override
        public boolean exists(WarpName name) {
            return find(name).isPresent();
        }

        @Override
        public void save(Warp warp) {}

        @Override
        public void delete(WarpName name) {}

        @Override
        public void rate(WarpName name, java.util.UUID player, double rating) {}

        @Override
        public double averageRating(WarpName name) {
            return 0.0;
        }
    }

    /** Records every hop so the harness mirrors the click test, though this path never teleports. */
    private static final class RecordingTeleporter implements WarpTeleporter {
        private final List<Warp> hops = new ArrayList<>();

        @Override
        public void teleportTo(PlayerRef who, Warp warp) {
            hops.add(warp);
        }
    }

    /** Records each delivered key so a path's outcome is asserted by the message it produced. */
    private static final class RecordingSink implements MessageSink {
        private final List<MessageKey> keys = new ArrayList<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            // renderedText is the key() string (see KeyMessages); the key list is what tests assert on
        }
    }

    private final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            sink.keys.add(key);
            return key.key();
        }
    }

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

    private static final class AllowAllPermissions implements Permissions {
        @Override
        public boolean has(PlayerRef who, String node) {
            return true;
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
            return QuotaResult.limited(configDefault);
        }
    }

    private static final class NoPlayerLookup implements PlayerLookup {
        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return Optional.empty();
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            return Optional.empty();
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return false;
        }
    }

    private static final class NoEvents
            implements com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher {
        @Override
        public void publish(com.uxplima.uxmessentials.shared.domain.DomainEvent event) {}
    }
}

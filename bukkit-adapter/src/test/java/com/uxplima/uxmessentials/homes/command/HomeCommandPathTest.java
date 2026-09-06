package com.uxplima.uxmessentials.homes.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.CommandDispatcher;
import com.uxplima.uxmessentials.homes.adapter.HomeServices;
import com.uxplima.uxmessentials.homes.adapter.inbound.command.HomeCommand;
import com.uxplima.uxmessentials.homes.adapter.inbound.gui.HomeListLayout;
import com.uxplima.uxmessentials.homes.adapter.inbound.gui.HomeListMenu;
import com.uxplima.uxmessentials.homes.adapter.outbound.SafeLocationGuard;
import com.uxplima.uxmessentials.homes.adapter.outbound.api.HomeApiWrites;
import com.uxplima.uxmessentials.homes.application.CreateHomeAtSlot;
import com.uxplima.uxmessentials.homes.application.DeleteHome;
import com.uxplima.uxmessentials.homes.application.HomeAdmin;
import com.uxplima.uxmessentials.homes.application.HomeCharge;
import com.uxplima.uxmessentials.homes.application.HomeChargeSettings;
import com.uxplima.uxmessentials.homes.application.HomeQuota;
import com.uxplima.uxmessentials.homes.application.InviteToHome;
import com.uxplima.uxmessentials.homes.application.ListHomes;
import com.uxplima.uxmessentials.homes.application.RelocateHome;
import com.uxplima.uxmessentials.homes.application.RenameHome;
import com.uxplima.uxmessentials.homes.application.UninviteFromHome;
import com.uxplima.uxmessentials.homes.application.VisitHome;
import com.uxplima.uxmessentials.homes.application.port.HomeInviteRepository;
import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.application.port.HomeTeleporter;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeSet;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.application.claim.AlwaysAllowClaimService;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.DomainGate;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmlib.gui.Guis;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the consolidated homes Brigadier surface: the single {@code /home} command with the
 * no-arg invocation opening the slot grid and {@code visit}, {@code invite}, {@code uninvite} and the
 * {@code admin} subtree as subcommands. {@code /home} opens the grid for the sender; {@code /home admin
 * <player> del|tp|list|set|clear|info} dispatches to the {@link HomeAdmin} use case against the target's set by
 * slot; {@code /home visit|invite|uninvite <player> [slot]} drive the respective use cases. The grid open is
 * asserted by the menu the sender ends up viewing; the verbs by their effect on the fake repository, invite
 * repository, and the recording teleporter.
 */
class HomeCommandPathTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");

    private static final Logger NOOP_LOG = new Logger() {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    };

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerMock target;
    private FakeHomeRepository repository;
    private FakeHomeInviteRepository invites;
    private RecordingTeleporter teleporter;
    private HomeServices services;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        player.setOp(true);
        target = server.addPlayer("Bob");
        repository = new FakeHomeRepository();
        invites = new FakeHomeInviteRepository();
        teleporter = new RecordingTeleporter();
        services = services();
        Guis.install(plugin);
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall();
        MockBukkit.unmock();
    }

    @Test
    void homeOpensTheSlotGrid() {
        CommandDispatcher<CommandSourceStack> dispatcher = register();

        execute(dispatcher, "home");

        Inventory open = player.getOpenInventory().getTopInventory();
        assertThat(open.getHolder()).isInstanceOf(MenuHolder.class);
    }

    @Test
    void rootRequiresTheUsePermission() {
        HomeCommand command = new HomeCommand(services, new KeyMessages(), new SyncScheduler());
        assertThat(command.build().getRequirement().test(sourceFor("uxmessentials.home.use")))
                .isTrue();
    }

    @Test
    void adminSubtreeRequiresTheAdminPermissionNotTheUsePermission() {
        // A player who can open their own grid but lacks home.admin cannot reach the admin subtree.
        assertCanUse("admin", "uxmessentials.home.admin", "uxmessentials.home.use");
    }

    @Test
    void visitSubcommandRequiresTheVisitPermission() {
        assertCanUse("visit", "uxmessentials.home.visit", "uxmessentials.home.use");
    }

    @Test
    void inviteAndUninviteSubcommandsRequireTheInvitePermission() {
        assertCanUse("invite", "uxmessentials.home.invite", "uxmessentials.home.use");
        assertCanUse("uninvite", "uxmessentials.home.invite", "uxmessentials.home.use");
    }

    /**
     * Asserts the {@code subcommand} literal under {@code /home} is reachable only with {@code grantsAccess}
     * and not with {@code deniedNode} alone. Proving Brigadier {@code .requires(...)} gates the subcommand by
     * its own permission rather than the root's.
     */
    private void assertCanUse(String subcommand, String grantsAccess, String deniedNode) {
        var root = new HomeCommand(services, new KeyMessages(), new SyncScheduler()).build();
        var node = root.getChild(subcommand);
        assertThat(node).as("subcommand '%s' exists under /home", subcommand).isNotNull();
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

    @Test
    void homeAdminDeleteRemovesTheTargetSlot() {
        repository.put(home(targetRef(), 0));
        CommandDispatcher<CommandSourceStack> dispatcher = register();

        execute(dispatcher, "home admin Bob del 1"); // 1-based display number maps to slot index 0

        assertThat(repository.findSlot(targetRef(), HomeSlot.of(0))).isEmpty();
    }

    @Test
    void homeAdminTeleportDelegatesToTheTeleporter() {
        repository.put(home(targetRef(), 0));
        CommandDispatcher<CommandSourceStack> dispatcher = register();

        execute(dispatcher, "home admin Bob tp 1");

        assertThat(teleporter.hops).isEqualTo(1);
    }

    @Test
    void homeAdminSetCreatesAHomeForTheTarget() {
        CommandDispatcher<CommandSourceStack> dispatcher = register();

        execute(dispatcher, "home admin Bob set 1");

        assertThat(repository.findSlot(targetRef(), HomeSlot.of(0))).isPresent();
    }

    @Test
    void homeAdminSetDefaultSlotUsesNextFreeIndex() {
        repository.put(home(targetRef(), 0));
        repository.put(home(targetRef(), 1));
        CommandDispatcher<CommandSourceStack> dispatcher = register();

        // No slot arg: default slot should be max+1 = 2 (index 2).
        execute(dispatcher, "home admin Bob set");

        assertThat(repository.findSlot(targetRef(), HomeSlot.of(2))).isPresent();
    }

    @Test
    void homeAdminClearRemovesAllTargetHomes() {
        repository.put(home(targetRef(), 0));
        repository.put(home(targetRef(), 1));
        CommandDispatcher<CommandSourceStack> dispatcher = register();

        execute(dispatcher, "home admin Bob clear");

        assertThat(repository.count(targetRef())).isEqualTo(0);
    }

    @Test
    void homeAdminInfoDoesNotThrowWhenHomeExists() {
        repository.put(home(targetRef(), 0));
        CommandDispatcher<CommandSourceStack> dispatcher = register();

        // Info resolves and sends a message; we assert no exception is thrown.
        execute(dispatcher, "home admin Bob info 1");
    }

    @Test
    void homeAdminOfflineTargetResolutionFallsThroughFindByName() {
        // Bob is in the fake lookup as an "offline" player returned only by findByName.
        // We rebuild services with a lookup that returns Bob offline-only.
        FakeOfflinePlayerLookup offlineLookup = new FakeOfflinePlayerLookup(targetRef());
        services = servicesWithLookup(offlineLookup);
        repository.put(home(targetRef(), 0));
        CommandDispatcher<CommandSourceStack> dispatcher = register();

        // del works even though Bob is "offline" (not returned by findOnlineByName).
        execute(dispatcher, "home admin Bob del 1");

        assertThat(repository.findSlot(targetRef(), HomeSlot.of(0))).isEmpty();
    }

    @Test
    void visitTeleportsToAPublicHomeOfTheTarget() {
        // Bob's slot 0 home is public, so Alice may visit it.
        repository.put(home(targetRef(), 0).withVisibility(true, Instant.EPOCH));
        CommandDispatcher<CommandSourceStack> dispatcher = register();

        execute(dispatcher, "home visit Bob"); // default slot 0

        assertThat(teleporter.hops).isEqualTo(1);
    }

    @Test
    void visitWithExplicitSlotResolvesTheZeroBasedSlot() {
        repository.put(home(targetRef(), 1).withVisibility(true, Instant.EPOCH));
        CommandDispatcher<CommandSourceStack> dispatcher = register();

        execute(dispatcher, "home visit Bob 2"); // 1-based display 2 maps to slot index 1

        assertThat(teleporter.hops).isEqualTo(1);
    }

    @Test
    void visitResolvesAnOfflineOwner() {
        // Bob resolves only via findByName (offline), and his public home is reachable.
        FakeOfflinePlayerLookup offlineLookup = new FakeOfflinePlayerLookup(targetRef());
        services = servicesWithLookup(offlineLookup);
        repository.put(home(targetRef(), 0).withVisibility(true, Instant.EPOCH));
        CommandDispatcher<CommandSourceStack> dispatcher = register();

        execute(dispatcher, "home visit Bob");

        assertThat(teleporter.hops).isEqualTo(1);
    }

    @Test
    void inviteAddsTheTargetToTheSendersInviteList() {
        repository.put(home(senderRef(), 0));
        CommandDispatcher<CommandSourceStack> dispatcher = register();

        execute(dispatcher, "home invite Bob"); // default slot 0

        assertThat(invites.invites(senderRef(), HomeSlot.of(0))).contains(target.getUniqueId());
    }

    @Test
    void uninviteRemovesTheTargetFromTheSendersInviteList() {
        repository.put(home(senderRef(), 0));
        invites.addInvite(senderRef(), HomeSlot.of(0), target.getUniqueId());
        CommandDispatcher<CommandSourceStack> dispatcher = register();

        execute(dispatcher, "home uninvite Bob");

        assertThat(invites.invites(senderRef(), HomeSlot.of(0))).doesNotContain(target.getUniqueId());
    }

    private PlayerRef senderRef() {
        return new PlayerRef(player.getUniqueId(), player.getName());
    }

    private PlayerRef targetRef() {
        return new PlayerRef(target.getUniqueId(), target.getName());
    }

    private CommandDispatcher<CommandSourceStack> register() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        Messages messages = new KeyMessages();
        dispatcher.getRoot().addChild(new HomeCommand(services, messages, new SyncScheduler()).build());
        return dispatcher;
    }

    private void execute(CommandDispatcher<CommandSourceStack> dispatcher, String input) {
        try {
            dispatcher.execute(input, CommandSourceStackMock.from(player));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new AssertionError("command did not parse: " + input, e);
        }
    }

    private Home home(PlayerRef owner, int slot) {
        return Home.create(owner, HomeSlot.of(slot), Position.of(WORLD, slot, 64, slot), Instant.EPOCH);
    }

    private HomeServices services() {
        return servicesWithLookup(new ServerPlayerLookup());
    }

    /** A map-backed slot repository keyed by (owner, slot). */
    private static final class FakeHomeRepository implements HomeRepository {
        private final Map<UUID, Map<Integer, Home>> byOwner = new ConcurrentHashMap<>();

        void put(Home home) {
            owned(home.owner()).put(home.slot().index(), home);
        }

        @Override
        public HomeSet load(PlayerRef owner) {
            return HomeSet.of(owner, new ArrayList<>(owned(owner).values()));
        }

        @Override
        public int count(PlayerRef owner) {
            return owned(owner).size();
        }

        @Override
        public Optional<Home> findSlot(PlayerRef owner, HomeSlot slot) {
            return Optional.ofNullable(owned(owner).get(slot.index()));
        }

        @Override
        public void save(Home home) {
            put(home);
        }

        @Override
        public void deleteSlot(PlayerRef owner, HomeSlot slot) {
            owned(owner).remove(slot.index());
        }

        @Override
        public void deleteAll(PlayerRef owner) {
            owned(owner).clear();
        }

        private Map<Integer, Home> owned(PlayerRef owner) {
            return byOwner.computeIfAbsent(owner.uuid(), u -> new java.util.TreeMap<>());
        }
    }

    /** A map-backed invite repository keyed by (owner, slot). */
    private static final class FakeHomeInviteRepository implements HomeInviteRepository {
        private final Map<String, Set<UUID>> bySlot = new ConcurrentHashMap<>();

        private static String key(PlayerRef owner, HomeSlot slot) {
            return owner.uuid() + ":" + slot.index();
        }

        @Override
        public Set<UUID> invites(PlayerRef owner, HomeSlot slot) {
            return Set.copyOf(bySlot.getOrDefault(key(owner, slot), Set.of()));
        }

        @Override
        public void addInvite(PlayerRef owner, HomeSlot slot, UUID invited) {
            bySlot.computeIfAbsent(key(owner, slot), k -> ConcurrentHashMap.newKeySet())
                    .add(invited);
        }

        @Override
        public void removeInvite(PlayerRef owner, HomeSlot slot, UUID invited) {
            bySlot.computeIfAbsent(key(owner, slot), k -> ConcurrentHashMap.newKeySet())
                    .remove(invited);
        }

        @Override
        public void removeAll(PlayerRef owner, HomeSlot slot) {
            bySlot.remove(key(owner, slot));
        }

        @Override
        public void removeAllForOwner(PlayerRef owner) {
            bySlot.keySet().removeIf(k -> k.startsWith(owner.uuid() + ":"));
        }
    }

    private static final class RecordingTeleporter implements HomeTeleporter {
        int hops;

        @Override
        public void teleportTo(PlayerRef who, Home home) {
            hops++;
        }
    }

    /** Resolves online players by name through the live mock server. */
    private final class ServerPlayerLookup implements PlayerLookup {
        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return Optional.ofNullable(server.getPlayerExact(name))
                    .map(p -> new PlayerRef(p.getUniqueId(), p.getName()));
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            return Optional.ofNullable(server.getPlayer(uuid)).map(p -> new PlayerRef(p.getUniqueId(), p.getName()));
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return server.getPlayer(uuid) != null;
        }
    }

    private static final class NoSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class NoEvents implements DomainEventPublisher {
        @Override
        public void publish(DomainEvent event) {}
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

    private HomeServices servicesWithLookup(PlayerLookup lookup) {
        Messages messages = new KeyMessages();
        Notifier notifier = new Notifier(messages, new NoSink());
        DomainEventPublisher events = new NoEvents();
        Clock clock = Clock.system(ZoneOffset.UTC);
        HomeQuota quota = new HomeQuota(new AllowAllPermissions(), 3, Permissions.QuotaReduction.MAX);
        Scheduler scheduler = new SyncScheduler();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneOffset.UTC);
        InviteToHome inviteToHome = new InviteToHome(repository, invites, notifier);
        UninviteFromHome uninviteFromHome = new UninviteFromHome(invites, notifier);
        VisitHome visitHome = new VisitHome(repository, invites, teleporter, notifier);
        MenuBindings bindings = new MenuBindings();
        bindings.condition("has-prev", (ctx, args) -> ctx.page() > 0);
        bindings.condition("has-next", (ctx, args) -> ctx.page() + 1 < ctx.pageCount());
        ItemRenderer itemRenderer = new ItemRenderer(new GuiText(messages), bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        server.getPluginManager()
                .registerEvents(
                        new MenuListener(renderer, bindings.actions(), bindings.conditions(), scheduler, plugin),
                        plugin);
        Menus menus = new Menus(renderer, scheduler, bindings.lists());
        HomeListMenu listView = new HomeListMenu(
                menus,
                messages,
                notifier,
                new AllowAllPermissions(),
                scheduler,
                new ListHomes(repository),
                quota,
                new CreateHomeAtSlot(
                        repository,
                        invites,
                        quota,
                        List.of(),
                        notifier,
                        events,
                        DomainGate.allowAll(),
                        freeCharge(),
                        1000,
                        clock),
                new SafeLocationGuard(server, false, false, 5),
                new AlwaysAllowClaimService(),
                HomeListLayout.codeDefault(),
                1000,
                fmt,
                (viewer, home) -> {});
        listView.register(bindings, Path.of("nonexistent"), NOOP_LOG);
        HomeAdmin homeAdmin = new HomeAdmin(repository, invites, teleporter, notifier, events, clock);
        return new HomeServices(
                listView,
                homeAdmin,
                visitHome,
                inviteToHome,
                uninviteFromHome,
                lookup,
                repository,
                new HomeApiWrites(
                        new CreateHomeAtSlot(
                                repository,
                                invites,
                                quota,
                                List.of(),
                                notifier,
                                events,
                                DomainGate.allowAll(),
                                freeCharge(),
                                1000,
                                clock),
                        new RelocateHome(
                                repository, List.of(), notifier, events, DomainGate.allowAll(), freeCharge(), clock),
                        new RenameHome(repository, notifier, events, clock),
                        new DeleteHome(repository, invites, notifier, events, DomainGate.allowAll())));
    }

    /**
     * A {@link PlayerLookup} that never resolves online but resolves a single known player via
     * {@link #findByName}, simulates an offline player profile still known to the server.
     */
    private static final class FakeOfflinePlayerLookup implements PlayerLookup {

        private final PlayerRef offline;

        FakeOfflinePlayerLookup(PlayerRef offline) {
            this.offline = offline;
        }

        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return Optional.empty(); // never online
        }

        @Override
        public Optional<PlayerRef> findByName(String name) {
            return offline.name().equalsIgnoreCase(name) ? Optional.of(offline) : Optional.empty();
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            return offline.uuid().equals(uuid) ? Optional.of(offline) : Optional.empty();
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return false;
        }
    }

    private static HomeCharge freeCharge() {
        return new HomeCharge(new AllowAllPermissions(), Optional.empty(), HomeChargeSettings.allFree());
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
}

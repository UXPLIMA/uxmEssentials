package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.homes.adapter.inbound.gui.HomeListLayout;
import com.uxplima.uxmessentials.homes.adapter.inbound.gui.HomeListMenu;
import com.uxplima.uxmessentials.homes.adapter.outbound.SafeLocationGuard;
import com.uxplima.uxmessentials.homes.application.CreateHomeAtSlot;
import com.uxplima.uxmessentials.homes.application.HomeCharge;
import com.uxplima.uxmessentials.homes.application.HomeChargeSettings;
import com.uxplima.uxmessentials.homes.application.HomeQuota;
import com.uxplima.uxmessentials.homes.application.ListHomes;
import com.uxplima.uxmessentials.homes.application.port.HomeInviteRepository;
import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
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
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The homes grid golden test: the engine-rendered {@code /home} slot grid must draw the exact grid the original {@code
 * HomeListView} drew. The fixture is a 3-slot limit with homes in slots 0 and 2, so the grid draws two filled RED_BED
 * cells (content slots 10 and 12), one empty GRAY_BED cell (slot 11), and the RED_BED page indicator (slot 22), and,
 * with everything on one page, no nav arrows. The engine window is snapshotted as {@code (slot -> material, plain
 * name)} and asserted equal, slot for slot, to the baseline the old view produced for this fixture, the page indicator
 * included. Then a click on the first filled cell through the engine's own {@link MenuListener} proves the migrated
 * path hands that home to the action-menu seam the old grid drove, faithful in both appearance and behaviour.
 */
class HomeListGoldenTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final List<Integer> CELLS = List.of(10, 11, 12, 13, 14, 15, 16);
    private static final int PAGE_INFO_SLOT = 22;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private FakeHomeRepository repository;
    private Messages messages;
    private final List<Home> openedAction = new ArrayList<>();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        server.addSimpleWorld("world");
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        repository = new FakeHomeRepository();
        messages = new KeyMessages();
        openedAction.clear();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void engineRendersTheSameFilledEmptyGridAndPageInfoAsTheOldView() {
        repository.put(home(0));
        repository.put(home(2));

        Map<Integer, Snapshot> baseline = oldViewBaseline();
        Map<Integer, Snapshot> engine = snapshotEngine();

        assertThat(engine.keySet()).containsExactlyInAnyOrderElementsOf(baseline.keySet());
        assertThat(engine).isEqualTo(baseline);
    }

    @Test
    void clickingAFilledCellThroughTheEngineOpensTheActionMenu() {
        repository.put(home(0));
        openEngine();

        fireClick(CELLS.get(0)); // the first content cell holds the home in slot 0

        assertThat(openedAction).extracting(h -> h.slot().index()).containsExactly(0);
    }

    /**
     * The slot -> (material, plain name) map the deleted {@code HomeListView} produced for this fixture (a 3-slot limit
     * with homes in slots 0 and 2), captured while both paths rendered it identically and frozen here: two filled
     * RED_BED cells, one empty GRAY_BED cell, and the RED_BED page indicator. The plain names are the bare catalog keys
     * because the test's {@code KeyMessages} returns each key verbatim, so a wrong key, material, or misplaced cell
     * still shows up as a mismatch.
     */
    private static Map<Integer, Snapshot> oldViewBaseline() {
        Map<Integer, Snapshot> baseline = new LinkedHashMap<>();
        baseline.put(CELLS.get(0), new Snapshot(Material.RED_BED, "home.menu.default-name"));
        baseline.put(CELLS.get(1), new Snapshot(Material.GRAY_BED, "home.menu.empty-name"));
        baseline.put(CELLS.get(2), new Snapshot(Material.RED_BED, "home.menu.default-name"));
        baseline.put(PAGE_INFO_SLOT, new Snapshot(Material.RED_BED, "home.menu.page-info"));
        return baseline;
    }

    private Map<Integer, Snapshot> snapshotEngine() {
        openEngine();
        return snapshot(player.getOpenInventory().getTopInventory());
    }

    private void openEngine() {
        MenuBindings bindings = new MenuBindings();
        // The nav arrows are gated by the page-position conditions production registers through MenuVocabulary;
        // mirror them here so a single-page grid hides both arrows exactly as the old view did.
        bindings.condition("has-prev", (ctx, args) -> ctx.page() > 0);
        bindings.condition("has-next", (ctx, args) -> ctx.page() + 1 < ctx.pageCount());
        ItemRenderer itemRenderer = new ItemRenderer(new GuiText(messages), bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        Scheduler scheduler = new SyncScheduler();
        MenuListener listener =
                new MenuListener(renderer, bindings.actions(), bindings.conditions(), scheduler, plugin);
        server.getPluginManager().registerEvents(listener, plugin);
        Menus menus = new Menus(renderer, scheduler, bindings.lists());

        HomeListMenu menu = menu(menus, scheduler);
        menu.register(bindings, Path.of("nonexistent"), NOOP);
        menu.open(viewer);
    }

    private HomeListMenu menu(Menus menus, Scheduler scheduler) {
        Notifier notifier = new Notifier(messages, (v, t) -> {});
        DomainEventPublisher events = new SilentEvents();
        Clock clock = Clock.system(ZoneOffset.UTC);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneOffset.UTC);
        HomeQuota quota = new HomeQuota(new AllowAllPermissions(), 3, Permissions.QuotaReduction.MAX);
        HomeInviteRepository invites = new NoInvites();
        CreateHomeAtSlot create = new CreateHomeAtSlot(
                repository,
                invites,
                quota,
                List.of(),
                notifier,
                events,
                DomainGate.allowAll(),
                freeCharge(),
                1000,
                clock);
        return new HomeListMenu(
                menus,
                messages,
                notifier,
                new AllowAllPermissions(),
                scheduler,
                new ListHomes(repository),
                quota,
                create,
                new SafeLocationGuard(server, false, false, 5),
                new AlwaysAllowClaimService(),
                HomeListLayout.codeDefault(),
                1000,
                fmt,
                (clickViewer, clickHome) -> openedAction.add(clickHome));
    }

    private Home home(int slot) {
        return Home.create(viewer, HomeSlot.of(slot), Position.of(WORLD, slot, 64, slot), Instant.EPOCH);
    }

    private void fireClick(int slot) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    /** The slot -> (material, plain name) map for every non-empty, non-filler slot of {@code inv}. */
    private static Map<Integer, Snapshot> snapshot(Inventory inv) {
        Map<Integer, Snapshot> out = new LinkedHashMap<>();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack item = inv.getItem(slot);
            if (item == null || item.getType() == Material.BLACK_STAINED_GLASS_PANE) {
                continue;
            }
            out.put(slot, new Snapshot(item.getType(), plainName(item)));
        }
        return out;
    }

    private static String plainName(ItemStack item) {
        // The title reads off the tile wherever the canon puts it: the display name of a bare button, or the
        // first lore line of a titled tile, whose display name is deliberately blank.
        return TileText.title(item);
    }

    /** What one rendered slot looks like for comparison: its material and the plain-text of its display name. */
    private record Snapshot(Material material, String name) {}

    private static HomeCharge freeCharge() {
        return new HomeCharge(new AllowAllPermissions(), Optional.empty(), HomeChargeSettings.allFree());
    }

    /** Asserts the open inventory is an engine-backed window. */
    @Test
    void theEngineWindowIsMenuBacked() {
        repository.put(home(0));
        openEngine();
        assertThat(player.getOpenInventory().getTopInventory().getHolder()).isInstanceOf(MenuHolder.class);
    }

    /** A map-backed home store keyed by (owner, slot). */
    private static final class FakeHomeRepository implements HomeRepository {
        private final Map<UUID, Map<Integer, Home>> byOwner = new ConcurrentHashMap<>();

        void put(Home home) {
            owned(home.owner()).put(home.slot().index(), home);
        }

        private Map<Integer, Home> owned(PlayerRef owner) {
            return byOwner.computeIfAbsent(owner.uuid(), id -> new java.util.TreeMap<>());
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
    }

    /** A no-op invite repository; the grid never reads it, kept for the create use case's wiring. */
    private static final class NoInvites implements HomeInviteRepository {
        @Override
        public java.util.Set<UUID> invites(PlayerRef owner, HomeSlot slot) {
            return java.util.Set.of();
        }

        @Override
        public void addInvite(PlayerRef owner, HomeSlot slot, UUID invited) {}

        @Override
        public void removeInvite(PlayerRef owner, HomeSlot slot, UUID invited) {}

        @Override
        public void removeAll(PlayerRef owner, HomeSlot slot) {}

        @Override
        public void removeAllForOwner(PlayerRef owner) {}
    }

    private static final class SilentEvents implements DomainEventPublisher {
        @Override
        public void publish(DomainEvent event) {}
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

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final Logger NOOP = new Logger() {
        @Override
        public void info(String m, Object... a) {}

        @Override
        public void warn(String m, Object... a) {}

        @Override
        public void error(String m, Throwable t) {}

        @Override
        public void debug(String m, Object... a) {}
    };

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

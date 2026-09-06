package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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

import com.uxplima.uxmessentials.homes.adapter.inbound.gui.HomeMenus;
import com.uxplima.uxmessentials.homes.adapter.inbound.gui.IconSelectorLayout;
import com.uxplima.uxmessentials.homes.application.HomesMessageKey;
import com.uxplima.uxmessentials.homes.application.SetHomeIcon;
import com.uxplima.uxmessentials.homes.application.port.HomeRepository;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeIcon;
import com.uxplima.uxmessentials.homes.domain.HomeSet;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The home-icon picker golden test: the engine-rendered picker must draw the exact palette the original
 * {@code IconSelectorView} drew. The code-default palette is 42 materials (RED_BED first), so the grid fills content
 * slots 0..41, with the BARRIER reset button (slot 45) and the three ARROW nav buttons (slots 48 back-row prev, 49
 * back, 50 next) on the bottom row. The window is snapshotted as {@code (slot -> material, plain name)} and asserted
 * equal, slot for slot, to the baseline the old view produced. Captured while both rendered the same fixture, then
 * frozen here so the old class could be deleted. Then a click on a palette cell, the reset button, and the back
 * button through the engine's own {@link MenuListener} prove the migrated path sets the icon, clears it, and returns
 * to the action menu, faithful in both appearance and behaviour.
 */
class HomeIconGoldenTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final HomeSlot SLOT = HomeSlot.of(0);

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private GuiText guiText;
    private Scheduler scheduler;
    private FakeHomeRepository repository;
    private final List<Home> reopenedAction = new ArrayList<>();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        server.addSimpleWorld("world");
        player = server.addPlayer("Owner");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        guiText = new GuiText(new KeyMessages());
        scheduler = new SyncScheduler();
        repository = new FakeHomeRepository();
        reopenedAction.clear();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void engineRendersTheSamePaletteAsTheOldView() {
        seedHome();
        Map<Integer, Snapshot> baseline = oldViewBaseline();

        Map<Integer, Snapshot> engine = snapshotEngine();

        assertThat(engine.keySet()).containsExactlyInAnyOrderElementsOf(baseline.keySet());
        assertThat(engine).isEqualTo(baseline);
    }

    @Test
    void clickingAPaletteCellThroughTheEngineSetsTheHomeIcon() {
        seedHome();
        openEngine();
        // Content slot 5 is the sixth palette material (BLACK_BED in the code default).
        Material expected = IconSelectorLayout.codeDefault().icons().get(5);
        fireClick(5);

        Home iconed = repository.findSlot(viewer, SLOT).orElseThrow();
        assertThat(iconed.icon()).map(HomeIcon::materialName).contains(expected.name());
        assertThat(reopenedAction).extracting(h -> h.slot().index()).containsExactly(SLOT.index());
    }

    @Test
    void clickingTheResetButtonThroughTheEngineClearsTheHomeIcon() {
        seedHome(Optional.of(HomeIcon.of("DIAMOND_BLOCK")));
        openEngine();
        fireClick(45); // the reset button

        Home cleared = repository.findSlot(viewer, SLOT).orElseThrow();
        assertThat(cleared.icon()).isEmpty();
        assertThat(reopenedAction).extracting(h -> h.slot().index()).containsExactly(SLOT.index());
    }

    @Test
    void clickingTheBackButtonThroughTheEngineReopensTheActionMenu() {
        seedHome();
        openEngine();
        fireClick(49); // the back button

        assertThat(reopenedAction).extracting(h -> h.slot().index()).containsExactly(SLOT.index());
        assertThat(repository.findSlot(viewer, SLOT).orElseThrow().icon()).isEmpty();
    }

    /**
     * The slot -> (material, plain name) map the deleted {@code IconSelectorView} produced for this fixture, captured
     * while both paths rendered it identically and frozen here as the contract: 42 palette icons in slots 0..41 (the
     * code-default materials, names surfacing through the {@code home_icon_name} token), the BARRIER reset button
     * (slot 45), and the three ARROW nav buttons (prev 48, back 49, next 50).
     */
    private static Map<Integer, Snapshot> oldViewBaseline() {
        Map<Integer, Snapshot> baseline = new LinkedHashMap<>();
        List<Material> icons = IconSelectorLayout.codeDefault().icons();
        for (int i = 0; i < icons.size(); i++) {
            baseline.put(i, new Snapshot(icons.get(i), icons.get(i).name()));
        }
        baseline.put(45, new Snapshot(Material.BARRIER, "home.icon.reset.name"));
        baseline.put(48, new Snapshot(Material.ARROW, "home.icon.prev"));
        baseline.put(49, new Snapshot(Material.ARROW, "home.icon.back"));
        baseline.put(50, new Snapshot(Material.ARROW, "home.icon.next"));
        return baseline;
    }

    private Map<Integer, Snapshot> snapshotEngine() {
        openEngine();
        return snapshot(player.getOpenInventory().getTopInventory());
    }

    private void openEngine() {
        MenuBindings bindings = new MenuBindings();
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        MenuListener listener =
                new MenuListener(renderer, bindings.actions(), bindings.conditions(), scheduler, plugin);
        server.getPluginManager().registerEvents(listener, plugin);
        Menus menus = new Menus(renderer, scheduler, bindings.lists());

        com.uxplima.uxmessentials.shared.application.message.Notifier notifier =
                new com.uxplima.uxmessentials.shared.application.message.Notifier(new KeyMessages(), (v, t) -> {});
        HomeMenus homeMenus = new HomeMenus(
                menus,
                scheduler,
                new SetHomeIcon(repository, notifier, new SilentEvents(), Clock.systemUTC()),
                IconSelectorLayout.codeDefault(),
                (p, v, home) -> reopenedAction.add(home));
        homeMenus.register(bindings, Path.of("nonexistent"), NOOP);
        homeMenus.openIcons(viewer, repository.findSlot(viewer, SLOT).orElseThrow());
    }

    private void fireClick(int slot) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

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

    private void seedHome() {
        seedHome(Optional.empty());
    }

    private void seedHome(Optional<HomeIcon> icon) {
        Home home = Home.create(viewer, SLOT, Position.of(WORLD, 0, 64, 0), Instant.EPOCH);
        if (icon.isPresent()) {
            home = home.withIcon(icon, Instant.EPOCH);
        }
        repository.save(home);
    }

    /** What one rendered slot looks like for comparison: its material and the plain-text of its display name. */
    private record Snapshot(Material material, String name) {}

    /** A map-backed home store keyed by (owner, slot). */
    private static final class FakeHomeRepository implements HomeRepository {
        private final Map<UUID, Map<Integer, Home>> byOwner = new ConcurrentHashMap<>();

        private Map<Integer, Home> owned(PlayerRef owner) {
            return byOwner.computeIfAbsent(owner.uuid(), id -> new ConcurrentHashMap<>());
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
            owned(home.owner()).put(home.slot().index(), home);
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

    private static final class SilentEvents implements DomainEventPublisher {
        @Override
        public void publish(DomainEvent event) {}
    }

    /** Surfaces the icon entry name's {@code home_icon_name} token; else the bare key, so a wrong key still shows. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            if (key.key().equals(HomesMessageKey.HOME_ICON_ENTRY_NAME.key())) {
                return placeholders.getOrDefault("home_icon_name", "");
            }
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

package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.itemworld.adapter.inbound.gui.ItemworldHubMenu;
import com.uxplima.uxmessentials.itemworld.application.ItemworldConfig;
import com.uxplima.uxmessentials.itemworld.application.PurgePolicy;
import com.uxplima.uxmessentials.itemworld.application.port.ItemworldAudit;
import com.uxplima.uxmessentials.itemworld.domain.MobSpec;
import com.uxplima.uxmessentials.itemworld.domain.PurgeSelection;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.MenuVocabulary;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.PlayerLocator;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.application.port.Warmups;
import com.uxplima.uxmessentials.shared.application.port.WorldLookup;
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
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The itemworld utilities-hub golden test: the engine-rendered launcher must draw the exact panel the original
 * {@code ItemworldHubView} drew, and its buttons must run the same surfaces. With every permission granted the
 * panel draws the eight workstation buttons, the two time and two weather buttons, the two cleanup buttons, the
 * back arrow, and the glass backdrop in between, snapshotted as {@code (slot -> material, plain name)} and
 * asserted equal, slot for slot, to the baseline the old view produced (frozen here so the old class could be
 * deleted). Clicking the ender-chest button opens that workstation, clicking the night button sets the world time
 * through the same path {@code /night} uses, and clicking the rain button storms the world like {@code /weather};
 * all three fire through the engine's own {@link MenuListener}. With no permission the world-action slots fall back
 * to the backdrop, exactly as the old launcher hid a button the viewer could not use.
 */
class ItemworldHubGoldenTest {

    private static final int ENDERCHEST_SLOT = 7;
    private static final int TIME_NIGHT_SLOT = 11;
    private static final int WEATHER_RAIN_SLOT = 13;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private World world;
    private Messages messages;
    private MutableConfig config;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        messages = new KeyMessages();
        config = new MutableConfig();
        Guis.install(plugin);
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall();
        MockBukkit.unmock();
    }

    @Test
    void engineRendersTheSameLauncherPanelAsTheOldView() {
        openEngine(new AllowAllPermissions());

        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(inv.getSize()).isEqualTo(27);
        assertThat(snapshot(inv)).isEqualTo(baseline());
    }

    @Test
    void clickingTheEnderchestButtonOpensThatWorkstation() {
        openEngine(new AllowAllPermissions());
        fireClick(ENDERCHEST_SLOT);

        assertThat(player.getOpenInventory().getType()).isEqualTo(InventoryType.ENDER_CHEST);
    }

    @Test
    void clickingTheTimeButtonSetsTheWorldTime() {
        world.setTime(6000L);
        openEngine(new AllowAllPermissions());
        fireClick(TIME_NIGHT_SLOT);

        assertThat(world.getTime()).isEqualTo(13_000L);
    }

    @Test
    void clickingTheWeatherButtonStormsTheWorld() {
        world.setStorm(false);
        openEngine(new AllowAllPermissions());
        fireClick(WEATHER_RAIN_SLOT);

        assertThat(world.hasStorm()).isTrue();
    }

    @Test
    void aButtonTheViewerMayNotUseFallsBackToTheBackdrop() {
        openEngine(new DenyAllPermissions());

        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(inv.getItem(TIME_NIGHT_SLOT).getType()).isEqualTo(Material.BLACK_STAINED_GLASS_PANE);
        assertThat(inv.getItem(ENDERCHEST_SLOT).getType()).isEqualTo(Material.BLACK_STAINED_GLASS_PANE);
        // The back button is never gated, so it is still drawn.
        assertThat(inv.getItem(22).getType()).isEqualTo(Material.ARROW);
    }

    /**
     * The slot -> (material, plain name) map the deleted {@code ItemworldHubView} produced for the all-permissions
     * fixture, captured once while both paths rendered it identically and frozen here as the contract: the eight
     * workstation buttons (each carrying the shared workstation key), the time/weather/cleanup buttons (their own
     * keys), the back arrow, and the glass backdrop filling every other slot. The plain names are the bare catalog
     * keys because the test's {@code KeyMessages} returns each key verbatim, so a real rendering difference still
     * shows up as a mismatch.
     */
    private static Map<Integer, Snapshot> baseline() {
        Map<Integer, Snapshot> baseline = new LinkedHashMap<>();
        for (int slot = 0; slot < 27; slot++) {
            baseline.put(slot, new Snapshot(Material.BLACK_STAINED_GLASS_PANE, ""));
        }
        String station = "itemworld.gui.hub.workstation";
        baseline.put(0, new Snapshot(Material.CRAFTING_TABLE, station));
        baseline.put(1, new Snapshot(Material.ANVIL, station));
        baseline.put(2, new Snapshot(Material.CARTOGRAPHY_TABLE, station));
        baseline.put(3, new Snapshot(Material.GRINDSTONE, station));
        baseline.put(4, new Snapshot(Material.LOOM, station));
        baseline.put(5, new Snapshot(Material.SMITHING_TABLE, station));
        baseline.put(6, new Snapshot(Material.STONECUTTER, station));
        baseline.put(7, new Snapshot(Material.ENDER_CHEST, station));
        baseline.put(10, new Snapshot(Material.SUNFLOWER, "itemworld.gui.hub.time-day"));
        baseline.put(11, new Snapshot(Material.CLOCK, "itemworld.gui.hub.time-night"));
        baseline.put(12, new Snapshot(Material.YELLOW_STAINED_GLASS, "itemworld.gui.hub.weather-clear"));
        baseline.put(13, new Snapshot(Material.WATER_BUCKET, "itemworld.gui.hub.weather-rain"));
        baseline.put(15, new Snapshot(Material.HOPPER, "itemworld.gui.hub.clear-drops"));
        baseline.put(16, new Snapshot(Material.IRON_SWORD, "itemworld.gui.hub.clear-mobs"));
        baseline.put(22, new Snapshot(Material.ARROW, "itemworld.gui.hub.back"));
        return baseline;
    }

    /** Build the engine, register the generic + hub bindings + spec, and open the launcher for the player. */
    private void openEngine(Permissions permissions) {
        GuiText guiText = new GuiText(messages);
        MenuBindings bindings = new MenuBindings();
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        Scheduler scheduler = new SyncScheduler();
        MenuListener listener =
                new MenuListener(renderer, bindings.actions(), bindings.conditions(), scheduler, plugin);
        server.getPluginManager().registerEvents(listener, plugin);
        Menus menus = new Menus(renderer, scheduler, bindings.lists());
        // The hub leans on the generic perm view-condition and close action, registered into the shared bindings
        // exactly as production wiring does.
        MenuVocabulary.registerConditions(bindings, permissions, new NoopLogger());
        MenuVocabulary.registerActions(bindings, menus, false, new NoopLogger());
        ItemworldHubMenu menu = new ItemworldHubMenu(menus, messages, scheduler, services(permissions), purge());
        menu.register(bindings, specDir(), new NoopLogger());
        menu.open(viewer);
    }

    private PurgePolicy purge() {
        return new PurgePolicy(ItemworldConfig.from(config));
    }

    private ItemworldServices services(Permissions permissions) {
        KernelPorts kernel = new KernelPorts(
                new SyncScheduler(),
                permissions,
                new NoCooldowns(),
                new NoWarmups(),
                messages,
                new NoopSink(),
                new NoPlayerLookup(),
                new NoWorldLookup(),
                new NoPlayerLocator(),
                new NoEvents(),
                new NoopLogger());
        return new ItemworldServices(
                kernel, new NoopAudit(), ItemworldConfig.from(config), GuiLayout.storageDefault(6));
    }

    private void fireClick(int slot) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    /** The bundled spec directory under the source tree, so the test loads the shipped hub spec. */
    private static Path specDir() {
        Path repoRoot = Path.of("").toAbsolutePath();
        while (repoRoot != null && !java.nio.file.Files.exists(repoRoot.resolve("settings.gradle.kts"))) {
            repoRoot = repoRoot.getParent();
        }
        Objects.requireNonNull(repoRoot, "repo root");
        return repoRoot.resolve("bukkit-adapter/src/main/resources");
    }

    private static Map<Integer, Snapshot> snapshot(Inventory inv) {
        Map<Integer, Snapshot> out = new LinkedHashMap<>();
        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack item = inv.getItem(slot);
            if (item == null) {
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

    private record Snapshot(Material material, String name) {}

    private static final class MutableConfig implements ConfigStore {
        private final Map<String, Object> values = new java.util.HashMap<>();

        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return values.get(path) instanceof Boolean b ? b : fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return values.get(path) instanceof String s ? s : fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return values.get(path) instanceof Integer i ? i : fallback;
        }
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
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

    private static final class DenyAllPermissions implements Permissions {
        @Override
        public boolean has(PlayerRef who, String node) {
            return false;
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
            return QuotaResult.limited(configDefault);
        }
    }

    private static final class NoopSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
    }

    private static final class NoCooldowns implements Cooldowns {
        @Override
        public com.uxplima.uxmessentials.shared.domain.Result<com.uxplima.uxmessentials.shared.domain.Unit, Duration>
                check(PlayerRef who, CooldownKind kind) {
            return com.uxplima.uxmessentials.shared.domain.Result.ok();
        }

        @Override
        public void stamp(PlayerRef who, CooldownKind kind) {}

        @Override
        public com.uxplima.uxmessentials.shared.domain.Result<com.uxplima.uxmessentials.shared.domain.Unit, Duration>
                checkLabel(PlayerRef who, String label) {
            return com.uxplima.uxmessentials.shared.domain.Result.ok();
        }

        @Override
        public void stampLabel(PlayerRef who, String label) {}
    }

    private static final class NoWarmups implements Warmups {
        @Override
        public WarmupHandle begin(PlayerRef who, WarmupKind kind, Runnable onComplete, Runnable onCancel) {
            onComplete.run();
            return new Warmups.CompletedWarmup(who);
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

    private static final class NoWorldLookup implements WorldLookup {
        @Override
        public Optional<WorldRef> findByName(String name) {
            return Optional.empty();
        }

        @Override
        public Optional<WorldRef> findByUid(UUID uid) {
            return Optional.empty();
        }
    }

    private static final class NoPlayerLocator implements PlayerLocator {
        @Override
        public Optional<Position> locate(PlayerRef who) {
            return Optional.empty();
        }
    }

    private static final class NoEvents implements DomainEventPublisher {
        @Override
        public void publish(DomainEvent event) {}
    }

    private static final class NoopAudit implements ItemworldAudit {
        @Override
        public void gave(PlayerRef actor, PlayerRef target, String itemKey, int amount) {}

        @Override
        public void spawnedMob(PlayerRef actor, MobSpec spec, int spawned) {}

        @Override
        public void retypedSpawner(PlayerRef actor, String mobType) {}

        @Override
        public void killed(PlayerRef actor, String target) {}

        @Override
        public void butchered(PlayerRef actor, PurgeSelection selection, int removed) {}

        @Override
        public void killedAll(PlayerRef actor, PurgeSelection selection, int removed) {}

        @Override
        public void removed(PlayerRef actor, PurgeSelection selection, int removed) {}

        @Override
        public void struckLightning(PlayerRef actor, Optional<PlayerRef> target) {}

        @Override
        public void launchedFireball(PlayerRef actor) {}

        @Override
        public void firedKittycannon(PlayerRef actor) {}

        @Override
        public void threwAntioch(PlayerRef actor) {}

        @Override
        public void firedBeezooka(PlayerRef actor) {}

        @Override
        public void brokeBlock(PlayerRef actor, String blockType) {}

        @Override
        public void grewTree(PlayerRef actor, String type) {}

        @Override
        public void nuked(PlayerRef actor, Optional<PlayerRef> target) {}
    }

    private static final class NoopLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
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
}

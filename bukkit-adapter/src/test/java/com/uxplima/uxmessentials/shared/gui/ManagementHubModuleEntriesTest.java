package com.uxplima.uxmessentials.shared.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.homes.application.HomesMessageKey;
import com.uxplima.uxmessentials.kits.application.KitsMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiEntry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementGuiRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.ManagementHubView;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.shared.menu.TestMenuEngine;
import com.uxplima.uxmessentials.vaults.application.VaultsMessageKey;
import com.uxplima.uxmessentials.warps.application.WarpsMessageKey;
import com.uxplima.uxmessentials.worlds.application.WorldEditorMessageKey;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * SP5 coverage: the existing module GUIs (homes, economy, kits, warps, vaults, worlds) are reachable from the
 * {@code /uxmess gui} management hub. Each module registers a single {@link ManagementGuiEntry} during its own
 * wiring whose opener delegates to that module's already-built list/menu/selector view; this test mirrors the
 * exact registration tuples bootstrap produces (id, label key, icon, and the existing perm node each reuses)
 * with a recording opener, and asserts the registry holds them in registration order and that the real
 * {@link ManagementHubView} routes a click on each entry to that entry's opener.
 *
 * <p>The view-open behaviour itself is unchanged and already covered by each module's command tests; this
 * guards only the integration seam SP5 adds: that every entry is present, permitted, and its opener fires.
 */
class ManagementHubModuleEntriesTest {

    /** The (id, label, icon, perm) each module's bootstrap registration produces, in wire order. */
    private static final List<ModuleEntry> EXPECTED = List.of(
            new ModuleEntry(
                    "worlds", WorldEditorMessageKey.LIST_TITLE, Material.GRASS_BLOCK, "uxmessentials.world.gui"),
            new ModuleEntry("homes", HomesMessageKey.HOME_MENU_TITLE, Material.RED_BED, "uxmessentials.home.use"),
            new ModuleEntry(
                    "economy", EconomyMessageKey.WALLET_GUI_TITLE, Material.GOLD_INGOT, "uxmessentials.economy.wallet"),
            new ModuleEntry("warps", WarpsMessageKey.WARP_MENU_TITLE, Material.ENDER_PEARL, "uxmessentials.warp.use"),
            new ModuleEntry("kits", KitsMessageKey.KIT_MENU_TITLE, Material.CHEST, "uxmessentials.kit.use"),
            new ModuleEntry(
                    "vaults", VaultsMessageKey.VAULT_SELECTOR_TITLE, Material.ENDER_CHEST, "uxmessentials.vault.use"));

    private static final int FIRST_CONTENT_SLOT = 10;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private GuiText guiText;
    private Scheduler scheduler;
    private com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus menus;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        guiText = new GuiText(new KeyMessages());
        scheduler = new SyncScheduler();
        TestMenuEngine engine = TestMenuEngine.create(new KeyMessages(), scheduler);
        engine.installListener(plugin);
        menus = engine.menus();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void registryHoldsEveryModuleEntryInWireOrderWithItsReusedPerm() {
        ManagementGuiRegistry registry = new ManagementGuiRegistry();
        register(registry, new LinkedHashMap<>());

        List<ManagementGuiEntry> entries = registry.entries();
        assertThat(entries).hasSize(EXPECTED.size());
        for (int i = 0; i < EXPECTED.size(); i++) {
            ModuleEntry expected = EXPECTED.get(i);
            ManagementGuiEntry actual = entries.get(i);
            assertThat(actual.id()).isEqualTo(expected.id());
            assertThat(actual.label()).isEqualTo(expected.label());
            assertThat(actual.icon()).isEqualTo(expected.icon());
            assertThat(actual.permission()).isEqualTo(expected.permission());
        }
    }

    @Test
    void clickingEachModuleEntryInvokesThatModulesOpener(@TempDir Path dir) throws Exception {
        ManagementGuiRegistry registry = new ManagementGuiRegistry();
        Map<String, AtomicReference<String>> opened = new LinkedHashMap<>();
        register(registry, opened);

        Set<String> allNodes = new HashSet<>();
        EXPECTED.forEach(e -> allNodes.add(e.permission()));
        ManagementHubView hub = new ManagementHubView(
                menus, guiText, scheduler, new FakePermissions(allNodes), registry, layout(dir, EXPECTED.size()));

        for (int i = 0; i < EXPECTED.size(); i++) {
            ModuleEntry expected = EXPECTED.get(i);
            hub.open(player, viewer);
            fireClick(FIRST_CONTENT_SLOT + i);
            assertThat(opened.get(expected.id()).get())
                    .as("entry %s opener fired", expected.id())
                    .isEqualTo(expected.id());
        }
    }

    @Test
    void anEntryIsHiddenWhenTheViewerLacksItsReusedPerm(@TempDir Path dir) throws Exception {
        ManagementGuiRegistry registry = new ManagementGuiRegistry();
        register(registry, new LinkedHashMap<>());
        // The viewer holds only the homes node, so only the homes icon is drawn in the first content slot.
        ManagementHubView hub = new ManagementHubView(
                menus,
                guiText,
                scheduler,
                new FakePermissions(Set.of("uxmessentials.home.use")),
                registry,
                layout(dir, EXPECTED.size()));
        hub.open(player, viewer);

        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(inv.getItem(FIRST_CONTENT_SLOT).getType()).isEqualTo(Material.RED_BED);
        // The second content slot has no permitted entry, so the engine draws the layout filler there, not a head.
        assertThat(inv.getItem(FIRST_CONTENT_SLOT + 1).getType()).isEqualTo(Material.GRAY_STAINED_GLASS_PANE);
    }

    /** Register one entry per expected module, each opener recording its own id into {@code opened}. */
    private void register(ManagementGuiRegistry registry, Map<String, AtomicReference<String>> opened) {
        for (ModuleEntry expected : EXPECTED) {
            AtomicReference<String> sink = new AtomicReference<>();
            opened.put(expected.id(), sink);
            registry.register(new ManagementGuiEntry(
                    expected.id(),
                    expected.label(),
                    expected.icon(),
                    expected.permission(),
                    (p, v) -> sink.set(expected.id())));
        }
    }

    private EntityListLayout layout(Path dir, int count) throws Exception {
        StringBuilder slots = new StringBuilder();
        for (int i = 0; i < count; i++) {
            slots.append(FIRST_CONTENT_SLOT + i);
            if (i < count - 1) {
                slots.append(", ");
            }
        }
        Path file = dir.resolve("modules").resolve("management").resolve("gui").resolve("hub.conf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                rows = 6
                nav-icon = "ARROW"
                filler = "GRAY_STAINED_GLASS_PANE"
                content-slots = [%s]
                prev-slot = 48
                next-slot = 50
                """.formatted(slots));
        return new GuiLayouts(dir, NOOP)
                .loadEntityList("management", "hub", EntityListLayout.paginatedDefault(Material.NETHER_STAR));
    }

    private void fireClick(int slot) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    /** The bootstrap registration tuple for one module's hub entry. */
    private record ModuleEntry(String id, MessageKey label, Material icon, String permission) {}

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    /** Grants exactly the nodes it is constructed with; quotas are unused by the hub. */
    private static final class FakePermissions implements Permissions {
        private final Set<String> granted;

        FakePermissions(Set<String> granted) {
            this.granted = new HashSet<>(granted);
        }

        @Override
        public boolean has(PlayerRef who, String node) {
            return granted.contains(node);
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
            return QuotaResult.limited(configDefault);
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

    /** Runs every scheduler hop inline, as the production schedulers would after their marshal. */
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

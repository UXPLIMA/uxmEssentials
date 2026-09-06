package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.economy.adapter.inbound.gui.EconomyAdminMenu;
import com.uxplima.uxmessentials.economy.adapter.inbound.gui.EconomyBulkMenu;
import com.uxplima.uxmessentials.economy.adapter.inbound.gui.EconomyTargetMenu;
import com.uxplima.uxmessentials.economy.adapter.inbound.gui.TransactionsHistoryMenu;
import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.PlayerPickerView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInputTestKit;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.MenuVocabulary;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmlib.gui.Guis;
import com.uxplima.uxmlib.gui.anvil.AnvilInput;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The bare-{@code /eco} admin-hub golden test: the engine-rendered hub must draw the exact window the original
 * {@code EconomyAdminView} drew, and its buttons must keep their routing. The hub draws a PLAYER_HEAD@11 [Manage a
 * player], a BEACON@13 [Server-wide], a BOOK@15 [Transaction history] and a BARRIER@22 close over a grey-glass
 * backdrop, snapshotted as {@code (slot -> material, plain name)} and asserted equal slot for slot to the baseline
 * the old view produced (frozen here so the old class could be deleted). [Manage a player] opens the shared player
 * picker (still bespoke); [Server-wide] opens the engine bulk screen; [Transaction history] opens the global
 * transaction log; the close button rides the engine's generic {@code close} action.
 */
class EconomyAdminGoldenTest {

    private static final Material FILLER = Material.GRAY_STAINED_GLASS_PANE;
    private static final int MANAGE_SLOT = 11;
    private static final int BULK_SLOT = 13;
    private static final int HISTORY_SLOT = 15;
    private static final int CLOSE_SLOT = 22;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock admin;
    private PlayerRef adminRef;

    private GuiText guiText;
    private Scheduler scheduler;
    private TestMenuEngine engine;
    private TextInput textInput;
    private AnvilInput anvil;
    private PlayerPickerView picker;
    private EconomyTargetMenu targetMenu;
    private EconomyBulkMenu bulkMenu;
    private TransactionsHistoryMenu historyView;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        admin = server.addPlayer("Admin");
        adminRef = new PlayerRef(admin.getUniqueId(), admin.getName());

        guiText = new GuiText(new KeyMessages());
        scheduler = new SyncScheduler();
        engine = TestMenuEngine.create(new KeyMessages(), scheduler);
        engine.installListener(plugin);
        anvil = new AnvilInput(plugin);
        anvil.install();
        Guis.install(plugin);
        textInput = TextInputTestKit.create(plugin, guiText, scheduler, Path.of("nonexistent"), NOOP);
        picker = new PlayerPickerView(engine.menus(), scheduler, textInput, server, new KeyMessages(), new NoopSink());
        picker.register(engine.bindings(), specDir(), NOOP);
        targetMenu = mock(EconomyTargetMenu.class);
        bulkMenu = mock(EconomyBulkMenu.class);
        historyView = mock(TransactionsHistoryMenu.class);
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall();
        MockBukkit.unmock();
    }

    @Test
    void engineRendersTheSameHubAsTheOldView() {
        EconomyAdminMenu hub = wire();
        hub.open(admin, adminRef);

        Inventory inv = admin.getOpenInventory().getTopInventory();
        assertThat(inv.getSize()).isEqualTo(27);
        assertThat(snapshot(inv)).isEqualTo(baseline());
    }

    @Test
    void manageClickOpensThePlayerPicker() {
        EconomyAdminMenu hub = wire();
        hub.open(admin, adminRef);

        fireClick(MANAGE_SLOT); // [Manage a player] -> the bespoke player picker
        Inventory pickerInv = admin.getOpenInventory().getTopInventory();
        // The picker grids online heads in roster order, so the only online player (admin) is present at slot 0.
        assertThat(pickerInv.getItem(0)).isNotNull();
    }

    @Test
    void bulkClickOpensTheEngineBulkScreen() {
        EconomyAdminMenu hub = wire();
        hub.open(admin, adminRef);

        fireClick(BULK_SLOT); // [Server-wide] -> the engine bulk screen
        verify(bulkMenu).open(eq(admin), eq(adminRef));
    }

    @Test
    void historyClickOpensTheGlobalTransactionLog() {
        EconomyAdminMenu hub = wire();
        hub.open(admin, adminRef);

        fireClick(HISTORY_SLOT); // [Transaction history] -> the global log
        verify(historyView).open(eq(adminRef), isNull(), eq("Global"));
    }

    @Test
    void closeClickClosesTheHub() {
        EconomyAdminMenu hub = wire();
        hub.open(admin, adminRef);
        assertThat(admin.getOpenInventory().getTopInventory().getHolder()).isInstanceOf(MenuHolder.class);

        fireClick(CLOSE_SLOT); // the generic close action
        assertThat(admin.getOpenInventory().getType()).isEqualTo(InventoryType.CRAFTING);
    }

    /**
     * The slot -> (material, plain name) map the deleted {@code EconomyAdminView} produced: the manage
     * PLAYER_HEAD@11, the bulk BEACON@13, the history BOOK@15 and the close BARRIER@22, each carrying its catalog
     * key (the test's {@code KeyMessages} returns each key verbatim, so a wrong key or material still mismatches).
     * The grey-glass backdrop fills every other slot.
     */
    private static Map<Integer, Snapshot> baseline() {
        Map<Integer, Snapshot> baseline = new LinkedHashMap<>();
        for (int slot = 0; slot < 27; slot++) {
            baseline.put(slot, new Snapshot(FILLER, ""));
        }
        baseline.put(MANAGE_SLOT, new Snapshot(Material.PLAYER_HEAD, key(EconomyMessageKey.ECO_ADMIN_GUI_MANAGE_NAME)));
        baseline.put(BULK_SLOT, new Snapshot(Material.BEACON, key(EconomyMessageKey.ECO_ADMIN_GUI_BULK_NAME)));
        baseline.put(HISTORY_SLOT, new Snapshot(Material.BOOK, key(EconomyMessageKey.ECO_ADMIN_GUI_HISTORY_NAME)));
        baseline.put(CLOSE_SLOT, new Snapshot(Material.BARRIER, key(EconomyMessageKey.ECO_ADMIN_GUI_CLOSE)));
        return baseline;
    }

    private EconomyAdminMenu wire() {
        MenuBindings bindings = engine.bindings();
        Menus menus = engine.menus();
        // The hub's close button rides the engine's generic close action, registered by the menu vocabulary.
        MenuVocabulary.registerActions(bindings, menus, false, NOOP);
        EconomyAdminMenu hub = new EconomyAdminMenu(
                menus, scheduler, picker, mock(PlayerLookup.class), targetMenu, bulkMenu, historyView);
        hub.register(bindings, specDir(), NOOP);
        return hub;
    }

    private static String key(EconomyMessageKey messageKey) {
        return messageKey.key();
    }

    private void fireClick(int slot) {
        InventoryView view = admin.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    /** The bundled spec directory under the source tree, so the menu loads the shipped admin-hub spec. */
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

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class NoopSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
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

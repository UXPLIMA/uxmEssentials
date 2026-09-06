package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.PlayerPickerView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInputTestKit;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.application.message.GuiMessageKey;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The player-picker golden test: the engine-rendered head grid must draw the exact picker the original
 * {@code PlayerPickerView} drew on uxmLib's {@code PaginatedGui}. The fixture is a small online roster over the
 * picker's six-row layout (content slots 0..44, prev at 45, next at 53, the offline NAME_TAG button at 49, two footer
 * buttons at 47 and 51, gray-glass filler everywhere else). The engine window is snapshotted as {@code (slot ->
 * material, plain name)} and asserted equal, slot for slot, to the analytic baseline the old view produced for this
 * fixture: content heads, nav, offline button and footer buttons all included. A real click on a head through the
 * engine's own {@link com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener} hands that target
 * to the recording {@code onPick}; a click on a footer button runs its own callback with the viewer. The offline-name
 * anvil branch is driven through the package-private {@code resolveTyped} apply seam, exercised in the same-package
 * {@code PlayerPickerViewTest}.
 */
class PlayerPickerGoldenTest {

    private static final int CONTENT_SLOTS = 45;
    private static final int PREV_SLOT = 45;
    private static final int NEXT_SLOT = 53;
    private static final int OFFLINE_SLOT = 49;
    private static final int FIRST_FOOTER_SLOT = 47;
    private static final int SECOND_FOOTER_SLOT = 51;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private GuiText guiText;
    private Scheduler scheduler;
    private TextInput textInput;
    private TestMenuEngine engine;
    private PlayerPickerView picker;
    private final List<PlayerRef> picked = new ArrayList<>();
    private final List<String> footerClicks = new ArrayList<>();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        // Two more online players so page 0 carries three heads (Alice, Bob, Carol) in content order.
        server.addPlayer("Bob");
        server.addPlayer("Carol");
        guiText = new GuiText(new KeyMessages());
        scheduler = new SyncScheduler();
        textInput = TextInputTestKit.create(plugin, guiText, scheduler, Path.of("nonexistent"), NOOP_LOG);
        engine = TestMenuEngine.create(new KeyMessages(), scheduler);
        engine.installListener(plugin);
        picker = new PlayerPickerView(engine.menus(), scheduler, textInput, server, new KeyMessages(), SINK);
        picker.register(engine.bindings(), Path.of("nonexistent"), NOOP_LOG);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void engineRendersTheSameHeadGridNavOfflineFooterAndFillerAsTheOldView() {
        openPicker();

        Map<Integer, Snapshot> baseline = oldViewBaseline(onlineCount());
        Map<Integer, Snapshot> rendered = snapshot(player.getOpenInventory().getTopInventory());

        assertThat(rendered.keySet()).containsExactlyInAnyOrderElementsOf(baseline.keySet());
        assertThat(rendered).isEqualTo(baseline);
    }

    @Test
    void theEngineWindowIsMenuBacked() {
        openPicker();
        assertThat(player.getOpenInventory().getTopInventory().getHolder()).isInstanceOf(MenuHolder.class);
    }

    @Test
    void clickingAHeadThroughTheEngineRunsOnPickForThatTarget() {
        openPicker();

        fireClick(0); // the first content head, the viewer Alice

        assertThat(picked).hasSize(1);
        assertThat(picked.get(0).uuid()).isEqualTo(player.getUniqueId());
    }

    @Test
    void clickingAFooterButtonThroughTheEngineRunsItsCallback() {
        openPicker();

        fireClick(SECOND_FOOTER_SLOT);

        assertThat(footerClicks).containsExactly("second");
    }

    private void openPicker() {
        picker.open(player, viewer, request());
    }

    private int onlineCount() {
        return server.getOnlinePlayers().size();
    }

    private PlayerPickerView.Request request() {
        List<PlayerPickerView.FooterButton> footers = List.of(
                new PlayerPickerView.FooterButton(
                        Key.FOOTER_A_NAME, Key.FOOTER_A_LORE, Material.BOOK, p -> footerClicks.add("first")),
                new PlayerPickerView.FooterButton(
                        Key.FOOTER_B_NAME, Key.FOOTER_B_LORE, Material.CHEST, p -> footerClicks.add("second")));
        return new PlayerPickerView.Request(Key.TITLE, picked::add, name -> Optional.empty(), Key.UNKNOWN, footers);
    }

    /**
     * The slot -> (material, plain name) map the bespoke {@code PlayerPickerView} produced for this fixture: a
     * PLAYER_HEAD at each content slot the page fills (named through the {@code gui.player-picker.head-name} key the
     * test's {@code KeyMessages} returns verbatim), the two ARROW nav buttons at 45 and 53, the NAME_TAG offline button
     * at 49, and the two footer buttons (BOOK at 47, CHEST at 51). The gray-glass filler slots are dropped from the
     * snapshot, so a wrong material, name, or misplaced button still shows up as a mismatch.
     */
    private static Map<Integer, Snapshot> oldViewBaseline(int onlineCount) {
        Map<Integer, Snapshot> baseline = new LinkedHashMap<>();
        int onThisPage = Math.min(CONTENT_SLOTS, onlineCount);
        for (int slot = 0; slot < onThisPage; slot++) {
            baseline.put(slot, new Snapshot(Material.PLAYER_HEAD, GuiMessageKey.PLAYER_PICKER_HEAD_NAME.key()));
        }
        baseline.put(PREV_SLOT, new Snapshot(Material.ARROW, GuiMessageKey.PLAYER_PICKER_PREV.key()));
        baseline.put(NEXT_SLOT, new Snapshot(Material.ARROW, GuiMessageKey.PLAYER_PICKER_NEXT.key()));
        baseline.put(OFFLINE_SLOT, new Snapshot(Material.NAME_TAG, GuiMessageKey.PLAYER_PICKER_CUSTOM.key()));
        baseline.put(FIRST_FOOTER_SLOT, new Snapshot(Material.BOOK, Key.FOOTER_A_NAME.key()));
        baseline.put(SECOND_FOOTER_SLOT, new Snapshot(Material.CHEST, Key.FOOTER_B_NAME.key()));
        return baseline;
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
            if (item == null || item.getType() == Material.GRAY_STAINED_GLASS_PANE) {
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

    /** Catalog keys for the synthetic request; their text is irrelevant beyond identifying each slot's label. */
    private enum Key implements MessageKey {
        TITLE("demo.picker.title"),
        UNKNOWN("demo.picker.unknown"),
        FOOTER_A_NAME("demo.picker.footer-a-name"),
        FOOTER_A_LORE("demo.picker.footer-a-lore"),
        FOOTER_B_NAME("demo.picker.footer-b-name"),
        FOOTER_B_LORE("demo.picker.footer-b-lore");

        private final String key;

        Key(String key) {
            this.key = key;
        }

        @Override
        public String key() {
            return key;
        }
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final MessageSink SINK = (viewer, message) -> {};

    private static final Logger NOOP_LOG = new Logger() {
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

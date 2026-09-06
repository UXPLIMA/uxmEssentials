package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityEditorLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInputTestKit;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.EditorSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ActionRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ConditionRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ListSourceRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.PlaceholderRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.EditorRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.EditableProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ListProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ListPropertyLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ListPropertyText;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmlib.gui.Guis;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of increment 7: a {@link ListProperty} clicked inside an editor running on the engine editor
 * runtime opens its list sub-menu as an engine child window. A {@link MenuHolder} routed by the one
 * {@link MenuListener}, rather than a uxmLib {@code SimpleGui}. The per-entry buttons branch on the click gesture
 * (left moves up, right moves down, shift-left edits through the anvil seam, shift-right removes via the engine
 * confirm window), the add button opens the anvil seam, and the back button reopens the parent editor. Each mutation
 * reopens the list child so the change shows.
 */
class MenuListChildTest {

    /** A mutable fake subject whose single list field the list property rewrites through a recording setter. */
    private static final class Widget {
        private List<String> lines = new ArrayList<>(List.of("alpha", "beta", "gamma"));
    }

    private static final int PROP_SLOT = 10;
    private static final List<Integer> ENTRY_SLOTS = List.of(0, 1, 2, 3);
    private static final int ADD_SLOT = 16;
    private static final int BACK_SLOT = 17;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private GuiText guiText;
    private Scheduler scheduler;
    private Menus menus;
    private Widget widget;
    private TextInput textInput;
    private List<List<String>> writes;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        guiText = new GuiText(new KeyMessages());
        scheduler = new SyncScheduler();
        widget = new Widget();
        writes = new ArrayList<>();
        // The add/edit anvil seam and the legacy uxmLib fallback both ride uxmLib's installed listener.
        Guis.install(plugin);
        textInput = TextInputTestKit.create(plugin, guiText, scheduler, Path.of("nonexistent"), NOOP_LOG);
        installEngine(scheduler);
    }

    @AfterEach
    void tearDown() {
        Guis.uninstall();
        MockBukkit.unmock();
    }

    @Test
    void clickingTheListPropertyOpensAnEngineChildWithOneEntryButtonPerEntryPlusAddAndBack() {
        openEditor();

        fireClick(PROP_SLOT, ClickType.LEFT);

        Inventory child = player.getOpenInventory().getTopInventory();
        assertThat(child.getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(child.getItem(ENTRY_SLOTS.get(0)).getType()).isEqualTo(Material.PAPER);
        assertThat(child.getItem(ENTRY_SLOTS.get(1)).getType()).isEqualTo(Material.PAPER);
        assertThat(child.getItem(ENTRY_SLOTS.get(2)).getType()).isEqualTo(Material.PAPER);
        // Only three entries, so the fourth entry slot stays filler, not a paper button.
        assertThat(child.getItem(ENTRY_SLOTS.get(3)).getType()).isNotEqualTo(Material.PAPER);
        assertThat(child.getItem(ADD_SLOT).getType()).isEqualTo(Material.EMERALD);
        assertThat(child.getItem(BACK_SLOT).getType()).isEqualTo(Material.ARROW);
    }

    @Test
    void leftClickingAnEntryMovesItUpThroughTheSetterAndReopensTheListChild() {
        openEditor();
        fireClick(PROP_SLOT, ClickType.LEFT);

        // The second entry (beta) moves up on a left-click, so the list becomes [beta, alpha, gamma].
        fireClick(ENTRY_SLOTS.get(1), ClickType.LEFT);

        assertThat(widget.lines).containsExactly("beta", "alpha", "gamma");
        assertThat(writes.get(writes.size() - 1)).containsExactly("beta", "alpha", "gamma");
        // The list child re-rendered in place: still an engine list window, not the parent editor.
        Inventory child = player.getOpenInventory().getTopInventory();
        assertThat(child.getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(((MenuHolder) child.getHolder()).editor()).isEmpty();
        assertThat(child.getItem(ADD_SLOT).getType()).isEqualTo(Material.EMERALD);
    }

    @Test
    void rightClickingAnEntryMovesItDownThroughTheSetter() {
        openEditor();
        fireClick(PROP_SLOT, ClickType.LEFT);

        // The first entry (alpha) moves down on a right-click, so the list becomes [beta, alpha, gamma].
        fireClick(ENTRY_SLOTS.get(0), ClickType.RIGHT);

        assertThat(widget.lines).containsExactly("beta", "alpha", "gamma");
    }

    @Test
    void shiftRightClickingAnEntryOpensTheEngineConfirmAndConfirmYesRemovesAndReopens() {
        openEditor();
        fireClick(PROP_SLOT, ClickType.LEFT);

        // Shift-right on the second entry (beta) opens the engine confirm window.
        fireClick(ENTRY_SLOTS.get(1), ClickType.SHIFT_RIGHT);
        Inventory confirm = player.getOpenInventory().getTopInventory();
        assertThat(confirm.getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(confirm.getItem(11).getType()).isEqualTo(Material.LIME_WOOL);
        assertThat(confirm.getItem(15).getType()).isEqualTo(Material.RED_WOOL);

        // Confirm yes removes beta and reopens the list child.
        fireClick(11, ClickType.LEFT);

        assertThat(widget.lines).containsExactly("alpha", "gamma");
        Inventory child = player.getOpenInventory().getTopInventory();
        assertThat(child.getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(((MenuHolder) child.getHolder()).editor()).isEmpty();
        assertThat(child.getItem(ADD_SLOT).getType()).isEqualTo(Material.EMERALD);
    }

    // The shift-left (edit) and add gestures route into the runtime-neutral anvil text seam, which calls
    // player.openAnvil. An operation MockBukkit leaves unimplemented, so a click that opens it cannot be exercised
    // here without the whole test aborting. The edit/add append-then-save behaviour is covered end to end by
    // ListPropertyApplyTest, which drives the package-private applyEdit/applyAdd seam the anvil's submit branch calls;
    // the gesture-to-handler routing those buttons share is exercised by the move and remove tests above.

    @Test
    void clickingBackReopensTheParentEditor() {
        openEditor();
        fireClick(PROP_SLOT, ClickType.LEFT);

        fireClick(BACK_SLOT, ClickType.LEFT);

        Inventory parent = player.getOpenInventory().getTopInventory();
        assertThat(parent.getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(((MenuHolder) parent.getHolder()).editor()).isPresent();
        assertThat(parent.getItem(PROP_SLOT).getType()).isEqualTo(Material.BOOK);
    }

    @Test
    void openClickEntryConfirmBackCloseLeavesNoLiveRefreshTask() {
        var recording = new RecordingScheduler();
        installEngine(recording);

        menus.openEditor(viewer, editorSpec(), widget);
        fireClick(PROP_SLOT, ClickType.LEFT); // open list child
        fireClick(ENTRY_SLOTS.get(1), ClickType.SHIFT_RIGHT); // open confirm child
        fireClick(11, ClickType.LEFT); // confirm yes → remove + reopen list child
        fireClick(BACK_SLOT, ClickType.LEFT); // back → parent editor
        player.closeInventory();
        player.closeInventory(); // a double-close is a harmless no-op

        // No editor, list child, or confirm arms a refresh timer, so start and cancel both stay balanced at zero.
        assertThat(recording.scheduled).isZero();
        assertThat(recording.cancelled).isZero();
    }

    private void installEngine(Scheduler sched) {
        EditorRenderer editorRenderer = new EditorRenderer(guiText);
        ItemRenderer itemRenderer = new ItemRenderer(guiText, new PlaceholderRegistry());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, new ConditionRegistry());
        menus = new Menus(renderer, sched, new ListSourceRegistry(), editorRenderer);
        MenuListener listener = new MenuListener(
                renderer,
                new ActionRegistry(),
                new ConditionRegistry(),
                sched,
                plugin,
                editorRenderer,
                menus.selectorOpener(),
                menus.confirmOpener());
        server.getPluginManager().registerEvents(listener, plugin);
    }

    private void openEditor() {
        menus.openEditor(viewer, editorSpec(), widget);
    }

    private EditorSpec editorSpec() {
        return EditorSpec.builder()
                .layout(layout())
                .title((v, subject) -> Component.text("edit"))
                .valueLore(Key.VALUE_LORE)
                .backName(Key.BACK)
                .properties(w -> List.<EditableProperty>of(listProperty()))
                .onBack((p, v) -> {})
                .build();
    }

    private ListProperty listProperty() {
        return new ListProperty(
                "demo-line",
                Key.LABEL,
                Material.BOOK,
                guiText,
                () -> widget.lines,
                next -> {
                    widget.lines = new ArrayList<>(next);
                    writes.add(new ArrayList<>(next));
                },
                listText(),
                listLayout(),
                textInput,
                scheduler);
    }

    private static ListPropertyText listText() {
        return new ListPropertyText(
                Key.TITLE,
                Key.ENTRY_NAME,
                Key.ENTRY_HINTS,
                Key.ADD_NAME,
                Key.ADD_PROMPT,
                Key.EDIT_PROMPT,
                Key.REMOVE_CONFIRM,
                Key.BACK);
    }

    private static ListPropertyLayout listLayout() {
        return new ListPropertyLayout(
                2,
                ENTRY_SLOTS,
                ADD_SLOT,
                BACK_SLOT,
                Material.PAPER,
                Material.EMERALD,
                Material.ARROW,
                Material.BLACK_STAINED_GLASS_PANE);
    }

    private static EntityEditorLayout layout() {
        return new EntityEditorLayout(
                3,
                List.of(PROP_SLOT),
                22,
                OptionalInt.empty(),
                Material.ARROW,
                Material.BARRIER,
                Material.BLACK_STAINED_GLASS_PANE);
    }

    private void fireClick(int slot, ClickType type) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event =
                new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, slot, type, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    /** Catalog keys for the test editor and list sub-menu; their text is irrelevant beyond the placeholders. */
    private enum Key implements MessageKey {
        LABEL("demo.prop.label"),
        VALUE_LORE("demo.editor.value-lore"),
        BACK("demo.editor.back"),
        TITLE("demo.list.title"),
        ENTRY_NAME("demo.list.entry-name"),
        ENTRY_HINTS("demo.list.entry-hints"),
        ADD_NAME("demo.list.add-name"),
        ADD_PROMPT("demo.list.add-prompt"),
        EDIT_PROMPT("demo.list.edit-prompt"),
        REMOVE_CONFIRM("demo.list.remove-confirm");

        private final String key;

        Key(String key) {
            this.key = key;
        }

        @Override
        public String key() {
            return key;
        }
    }

    /** A value-lore key renders as {@code value=<value>} so a value can be read back; everything else echoes the key. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            if (key.key().equals("demo.editor.value-lore")) {
                return "value=" + placeholders.getOrDefault("value", "");
            }
            return key.key();
        }
    }

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

    /** A scheduler that records every repeating-task start and cancel, for the leak-balance assertion. */
    private static final class RecordingScheduler implements Scheduler {
        int scheduled = 0;
        int cancelled = 0;

        @Override
        public AutoCloseable repeatGlobal(Runnable task, Duration initialDelay, Duration period) {
            scheduled++;
            return () -> cancelled++;
        }

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

package com.uxplima.uxmessentials.shared.gui;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

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
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityEditorView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInputTestKit;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.EditorRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ClickContexts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.EditableProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.NumberProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.TextProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ToggleProperty;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the shared {@link EntityEditorView} and its value editors, now a thin shim over the menu
 * engine: the view opens through {@link Menus#openEditor} as a holder-backed engine editor routed by the one
 * {@link MenuListener}. The editor is laid out entirely from a temp layout conf (no hardcoded slots): it draws one
 * button per property at its conf slot plus the conf'd back and delete buttons, a {@link ToggleProperty} click flips
 * the value and runs its setter, a {@link NumberProperty} click steps within bounds, a {@link TextProperty} routes a
 * validated line to its setter, and the delete button opens the engine confirm so a stray click cannot delete, only
 * a confirm-slot click does. The scheduler is synchronous so the off-thread setter runs inline.
 */
class EntityEditorViewTest {

    /** A mutable fake entity; the value editors mutate it through their setters so the test can observe writes. */
    private static final class Widget {
        private boolean enabled;
        private long count;
        private String label = "old";
    }

    private static final int CONFIRM_SLOT =
            11; // the engine confirm window's yes button slot (ConfirmRenderer.YES_SLOT)

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private GuiText guiText;
    private Scheduler scheduler;
    private TextInput textInput;
    private Menus menus;
    private Widget widget;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        guiText = new GuiText(new KeyMessages());
        scheduler = new SyncScheduler();
        textInput = TextInputTestKit.create(plugin, guiText, scheduler, java.nio.file.Path.of("nonexistent"), NOOP);
        widget = new Widget();
        // One editor-capable engine and its single listener: the shim opens through this Menus and its delete-confirm
        // child is routed by the one listener, the engine path the production wiring uses.
        EditorRenderer editorRenderer = new EditorRenderer(guiText);
        MenuBindings bindings = new MenuBindings();
        MenuRenderer renderer =
                new MenuRenderer(new ItemRenderer(guiText, bindings.placeholders()), bindings.conditions());
        menus = new Menus(renderer, scheduler, bindings.lists(), editorRenderer);
        MenuListener listener = new MenuListener(
                renderer,
                bindings.actions(),
                bindings.conditions(),
                scheduler,
                plugin,
                editorRenderer,
                menus.selectorOpener(),
                menus.confirmOpener());
        server.getPluginManager().registerEvents(listener, plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void rendersOnePropertyButtonPerConfSlotPlusBackAndDelete(@TempDir Path dir) throws Exception {
        EntityEditorLayout layout = layout(dir, "property-slots = [10, 12]\nback-slot = 22\ndelete-slot = 26\n");
        editor(layout, List.of(togglePill(), counter()), true).open(player, viewer, widget);

        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(inv.getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(inv.getItem(10).getType()).isEqualTo(Material.LEVER); // toggle icon
        assertThat(inv.getItem(12).getType()).isEqualTo(Material.CLOCK); // number icon
        assertThat(inv.getItem(22).getType()).isEqualTo(Material.ARROW); // back
        assertThat(inv.getItem(26).getType()).isEqualTo(Material.BARRIER); // delete
        assertThat(inv.getItem(0).getType()).isEqualTo(Material.BLACK_STAINED_GLASS_PANE); // filler
    }

    @Test
    void togglePropertyFlipsAndCallsItsSetter(@TempDir Path dir) throws Exception {
        EntityEditorLayout layout = layout(dir, "property-slots = [10]\nback-slot = 22\n");
        editor(layout, List.of(togglePill()), false).open(player, viewer, widget);

        assertThat(widget.enabled).isFalse();
        fireClick(10, ClickType.LEFT);

        assertThat(widget.enabled).isTrue();
    }

    @Test
    void numberPropertyStepsWithinBounds(@TempDir Path dir) throws Exception {
        EntityEditorLayout layout = layout(dir, "property-slots = [10]\nback-slot = 22\n");
        widget.count = 9;
        editor(layout, List.of(counter()), false).open(player, viewer, widget);

        fireClick(10, ClickType.LEFT); // +5 step, clamped to max 10

        assertThat(widget.count).isEqualTo(10);

        fireClick(10, ClickType.RIGHT); // -5 step, down to 5
        assertThat(widget.count).isEqualTo(5);
    }

    @Test
    void textPropertyRoutesValidatedInputToItsSetter() {
        TextProperty property = new TextProperty(
                "editor.text-field",
                Key.LABEL,
                Key.PROMPT,
                Material.NAME_TAG,
                () -> widget.label,
                raw -> raw.isBlank() ? Optional.empty() : Optional.of(raw.trim()),
                value -> widget.label = value,
                textInput,
                scheduler);

        property.applyInput(ClickContexts.carrier(player, viewer), "  Castle  ");

        assertThat(widget.label).isEqualTo("Castle");
    }

    @Test
    void textPropertyRejectsBlankInputWithoutCallingSetter() {
        TextProperty property = new TextProperty(
                "editor.text-field",
                Key.LABEL,
                Key.PROMPT,
                Material.NAME_TAG,
                () -> widget.label,
                raw -> raw.isBlank() ? Optional.empty() : Optional.of(raw.trim()),
                value -> widget.label = value,
                textInput,
                scheduler);

        property.applyInput(ClickContexts.carrier(player, viewer), "   ");

        assertThat(widget.label).isEqualTo("old");
    }

    @Test
    void deleteIsGatedByAConfirmMenu(@TempDir Path dir) throws Exception {
        EntityEditorLayout layout = layout(dir, "property-slots = [10]\nback-slot = 22\ndelete-slot = 26\n");
        AtomicBoolean deleted = new AtomicBoolean(false);
        EntityEditorView<Widget> view = EntityEditorView.<Widget>builder()
                .menus(menus)
                .guiText(guiText)
                .scheduler(scheduler)
                .layout(layout)
                .title((v, w) -> Component.text("edit"))
                .valueLore(Key.VALUE_LORE)
                .backName(Key.BACK)
                .properties(w -> List.of(togglePill()))
                .onBack((p, v) -> {})
                .onDelete(Key.DELETE, Key.DELETE_CONFIRM, (p, w) -> deleted.set(true))
                .build();
        view.open(player, viewer, widget);

        fireClick(26, ClickType.LEFT); // clicking delete opens the confirm menu, it does not delete

        Inventory confirm = player.getOpenInventory().getTopInventory();
        assertThat(confirm.getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(deleted).isFalse();

        fireClick(CONFIRM_SLOT, ClickType.LEFT); // the confirm button does delete

        assertThat(deleted).isTrue();
    }

    private EntityEditorView<Widget> editor(
            EntityEditorLayout layout, List<EditableProperty> properties, boolean withDelete) {
        EntityEditorView.Builder<Widget> builder = EntityEditorView.<Widget>builder()
                .menus(menus)
                .guiText(guiText)
                .scheduler(scheduler)
                .layout(layout)
                .title((v, w) -> Component.text("edit"))
                .valueLore(Key.VALUE_LORE)
                .backName(Key.BACK)
                .properties(w -> properties)
                .onBack((p, v) -> {});
        if (withDelete) {
            builder.onDelete(Key.DELETE, Key.DELETE_CONFIRM, (p, w) -> {});
        }
        return builder.build();
    }

    private ToggleProperty<Boolean> togglePill() {
        return ToggleProperty.ofBoolean(
                Key.LABEL,
                Material.LEVER,
                () -> widget.enabled,
                (v, state) -> state ? "On" : "Off",
                value -> widget.enabled = value,
                scheduler);
    }

    private NumberProperty counter() {
        return new NumberProperty(
                Key.LABEL, Material.CLOCK, () -> widget.count, 5, 10, 0, 10, value -> widget.count = value, scheduler);
    }

    private EntityEditorLayout layout(Path dir, String body) throws Exception {
        Path file = dir.resolve("modules").resolve("demo").resolve("gui").resolve("demo-editor.conf");
        Files.createDirectories(file.getParent());
        Files.writeString(
                file,
                "rows = 3\nback-icon = \"ARROW\"\ndelete-icon = \"BARRIER\"\n"
                        + "filler = \"BLACK_STAINED_GLASS_PANE\"\n" + body);
        return new GuiLayouts(dir, NOOP)
                .loadEntityEditor("demo", "demo-editor", EntityEditorLayout.codeDefault(List.of(0), 8));
    }

    private void fireClick(int slot, ClickType type) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event =
                new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, slot, type, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    /** Catalog keys for the test views; their text is irrelevant to the slot/state assertions. */
    private enum Key implements MessageKey {
        LABEL("demo.prop.label"),
        PROMPT("demo.prop.prompt"),
        VALUE_LORE("demo.editor.value-lore"),
        BACK("demo.editor.back"),
        DELETE("demo.editor.delete"),
        DELETE_CONFIRM("demo.editor.delete-confirm");

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

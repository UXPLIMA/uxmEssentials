package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityEditorLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
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
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ToggleProperty;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
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
 * MockBukkit coverage of the engine's property-editor capability (increments 1 to 4 of the editor framework): an
 * editor is opened through {@link Menus#openEditor} as a {@link MenuHolder}-backed window the one
 * {@link MenuListener} recognises, draws one button per {@link EditableProperty}, routes a property click into that
 * property's {@code onClick}, and repaints the same inventory in place after the setter runs. The scheduler is
 * synchronous so the property's async-setter-then-reopen hops run inline.
 */
class MenuEditorTest {

    /** A mutable fake subject; the toggle property flips its flag through a recording setter. */
    private static final class Widget {
        private boolean enabled;
    }

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private GuiText guiText;
    private Scheduler scheduler;
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
        widget = new Widget();
        EditorRenderer editorRenderer = new EditorRenderer(guiText);
        ItemRenderer itemRenderer = new ItemRenderer(guiText, new PlaceholderRegistry());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, new ConditionRegistry());
        menus = new Menus(renderer, scheduler, new ListSourceRegistry(), editorRenderer);
        MenuListener listener = new MenuListener(
                renderer,
                new ActionRegistry(),
                new ConditionRegistry(),
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
    void openEditorWithNoPropertiesShowsAMenuHolderWindowWithFillerAndBack() {
        EntityEditorLayout layout = layout(List.of(10, 12), 22);
        menus.openEditor(viewer, editorSpec(layout, w -> List.of()), widget);

        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(inv.getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(inv.getItem(22).getType()).isEqualTo(Material.ARROW); // back button
        assertThat(inv.getItem(0).getType()).isEqualTo(Material.BLACK_STAINED_GLASS_PANE); // filler
        assertThat(inv.getItem(10)).isEqualTo(filler()); // no property drawn, slot stays filler
    }

    @Test
    void editorRendersOneButtonPerPropertyWithItsValueLore() {
        EntityEditorLayout layout = layout(List.of(10, 12, 14), 22);
        AtomicReference<Boolean> a = new AtomicReference<>(true);
        AtomicReference<Boolean> b = new AtomicReference<>(false);
        AtomicReference<Boolean> c = new AtomicReference<>(true);
        List<EditableProperty> props =
                List.of(toggle(a, Material.LEVER), toggle(b, Material.TORCH), toggle(c, Material.REDSTONE_TORCH));
        menus.openEditor(viewer, editorSpec(layout, w -> props), widget);

        Inventory inv = player.getOpenInventory().getTopInventory();
        assertThat(inv.getItem(10).getType()).isEqualTo(Material.LEVER);
        assertThat(inv.getItem(12).getType()).isEqualTo(Material.TORCH);
        assertThat(inv.getItem(14).getType()).isEqualTo(Material.REDSTONE_TORCH);
        // The value-lore catalog line wraps each property's current value: true → "On", false → "Off".
        assertThat(valueLoreOf(inv.getItem(10))).isEqualTo("value=On");
        assertThat(valueLoreOf(inv.getItem(12))).isEqualTo("value=Off");
        assertThat(valueLoreOf(inv.getItem(14))).isEqualTo("value=On");
    }

    @Test
    void clickingATogglePropertyFlipsTheValueAndReRendersTheSlot() {
        EntityEditorLayout layout = layout(List.of(10), 22);
        menus.openEditor(viewer, editorSpec(layout, w -> List.of(widgetToggle())), widget);

        assertThat(widget.enabled).isFalse();
        assertThat(valueLoreOf(player.getOpenInventory().getTopInventory().getItem(10)))
                .isEqualTo("value=Off");

        fireClick(10, ClickType.LEFT);

        assertThat(widget.enabled).isTrue(); // recording setter ran
        assertThat(valueLoreOf(player.getOpenInventory().getTopInventory().getItem(10)))
                .isEqualTo("value=On");
    }

    @Test
    void reRenderRedrawsTheSameInventoryInPlaceWithoutReopening() {
        EntityEditorLayout layout = layout(List.of(10), 22);
        menus.openEditor(viewer, editorSpec(layout, w -> List.of(widgetToggle())), widget);
        Inventory before = player.getOpenInventory().getTopInventory();

        fireClick(10, ClickType.LEFT);

        Inventory after = player.getOpenInventory().getTopInventory();
        assertThat(after).isSameAs(before); // same window: no second openInventory, no new holder
        assertThat(after.getHolder()).isSameAs(before.getHolder());
        assertThat(valueLoreOf(after.getItem(10))).isEqualTo("value=On"); // new value shows in the reused window
    }

    private EditorSpec editorSpec(
            EntityEditorLayout layout,
            java.util.function.Function<@org.jspecify.annotations.Nullable Object, List<EditableProperty>> props) {
        return EditorSpec.builder()
                .layout(layout)
                .title((v, subject) -> Component.text("edit"))
                .valueLore(Key.VALUE_LORE)
                .backName(Key.BACK)
                .properties(props)
                .onBack((p, v) -> {})
                .build();
    }

    private ToggleProperty<Boolean> widgetToggle() {
        return ToggleProperty.ofBoolean(
                Key.LABEL,
                Material.LEVER,
                () -> widget.enabled,
                (v, state) -> state ? "On" : "Off",
                value -> widget.enabled = value,
                scheduler);
    }

    private ToggleProperty<Boolean> toggle(AtomicReference<Boolean> backing, Material icon) {
        return ToggleProperty.ofBoolean(
                Key.LABEL, icon, backing::get, (v, state) -> state ? "On" : "Off", backing::set, scheduler);
    }

    private static EntityEditorLayout layout(List<Integer> propertySlots, int backSlot) {
        return new EntityEditorLayout(
                3,
                propertySlots,
                backSlot,
                OptionalInt.empty(),
                Material.ARROW,
                Material.BARRIER,
                Material.BLACK_STAINED_GLASS_PANE);
    }

    private static ItemStack filler() {
        return com.uxplima.uxmlib.item.ItemBuilder.of(Material.BLACK_STAINED_GLASS_PANE)
                .name(Component.empty())
                .build();
    }

    private void fireClick(int slot, ClickType type) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event =
                new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, slot, type, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    /** The first lore line of an item rendered as plain text, the value-lore the editor wrote. */
    private static String valueLoreOf(ItemStack item) {
        List<Component> lore = TileText.body(item);
        assertThat(lore).isNotEmpty();
        return PlainTextComponentSerializer.plainText().serialize(lore.get(0));
    }

    /** Catalog keys for the test editor; their text is irrelevant beyond the value-lore placeholder. */
    private enum Key implements MessageKey {
        LABEL("demo.prop.label"),
        VALUE_LORE("demo.editor.value-lore"),
        BACK("demo.editor.back");

        private final String key;

        Key(String key) {
            this.key = key;
        }

        @Override
        public String key() {
            return key;
        }
    }

    /** A value-lore key renders as {@code value=<value>} so the test can read the wrapped current value back. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            if (key.key().equals("demo.editor.value-lore")) {
                return "value=" + placeholders.getOrDefault("value", "");
            }
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
}

package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;

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
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.EnumProperty;
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
 * MockBukkit coverage of increment 6: an {@link EnumProperty} clicked inside an editor running on the engine
 * editor runtime opens its option selector as an engine child window. A {@link MenuHolder} routed by the one
 * {@link MenuListener}, rather than a uxmLib {@code SimpleGui}. Choosing a non-selected option hands it to the
 * setter and reopens the parent editor in place; the selected option carries a glint. The whole open → click →
 * child → choose → back flow leaves no live refresh task.
 */
class MenuEnumSelectorTest {

    /** A mutable fake subject whose single field the enum property rewrites through a recording setter. */
    private static final class Widget {
        private Choice choice = Choice.ALPHA;
    }

    private enum Choice {
        ALPHA,
        BETA,
        GAMMA
    }

    private static final int PROP_SLOT = 10;
    private static final List<Integer> OPTION_SLOTS = List.of(11, 13, 15);

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
    void clickingTheEnumPropertyOpensAnEngineChildSelectorWithTheSelectedOptionGlinted() {
        openEditor();

        fireClick(PROP_SLOT, ClickType.LEFT);

        Inventory child = player.getOpenInventory().getTopInventory();
        assertThat(child.getHolder()).isInstanceOf(MenuHolder.class);
        // One option button per option slot, the icon material the selector was given.
        assertThat(child.getItem(OPTION_SLOTS.get(0)).getType()).isEqualTo(Material.PAPER);
        assertThat(child.getItem(OPTION_SLOTS.get(1)).getType()).isEqualTo(Material.PAPER);
        assertThat(child.getItem(OPTION_SLOTS.get(2)).getType()).isEqualTo(Material.PAPER);
        // ALPHA is the current value, so only its button carries the selection glint.
        assertThat(hasGlint(child.getItem(OPTION_SLOTS.get(0)))).isTrue();
        assertThat(hasGlint(child.getItem(OPTION_SLOTS.get(1)))).isFalse();
        assertThat(hasGlint(child.getItem(OPTION_SLOTS.get(2)))).isFalse();
    }

    @Test
    void choosingANonSelectedOptionRunsTheSetterAndReopensTheParentEditorWithTheNewValue() {
        openEditor();
        fireClick(PROP_SLOT, ClickType.LEFT);

        // BETA sits at the second option slot; choosing it must write through the setter and rebuild the editor.
        fireClick(OPTION_SLOTS.get(1), ClickType.LEFT);

        assertThat(widget.choice).isEqualTo(Choice.BETA);
        Inventory parent = player.getOpenInventory().getTopInventory();
        assertThat(parent.getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(((MenuHolder) parent.getHolder()).editor()).isPresent();
        assertThat(valueLoreOf(parent.getItem(PROP_SLOT))).isEqualTo("value=BETA");
    }

    @Test
    void openClickChildChooseBackLeavesNoLiveRefreshTask() {
        var recording = new RecordingScheduler();
        EditorRenderer editorRenderer = new EditorRenderer(guiText);
        ItemRenderer itemRenderer = new ItemRenderer(guiText, new PlaceholderRegistry());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, new ConditionRegistry());
        Menus leakMenus = new Menus(renderer, recording, new ListSourceRegistry(), editorRenderer);
        MenuListener leakListener = new MenuListener(
                renderer,
                new ActionRegistry(),
                new ConditionRegistry(),
                recording,
                plugin,
                editorRenderer,
                leakMenus.selectorOpener(),
                leakMenus.confirmOpener());
        server.getPluginManager().registerEvents(leakListener, plugin);

        leakMenus.openEditor(viewer, editorSpec(), widget);
        fireClick(PROP_SLOT, ClickType.LEFT); // open child selector
        fireClick(OPTION_SLOTS.get(1), ClickType.LEFT); // choose → setter + reopen parent
        player.closeInventory();
        player.closeInventory(); // double close is a harmless no-op

        // Neither the editor nor the selector arms a refresh timer, so start and cancel both stay balanced at zero.
        assertThat(recording.scheduled).isZero();
        assertThat(recording.cancelled).isZero();
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
                .properties(w -> List.<EditableProperty>of(enumProperty()))
                .onBack((p, v) -> {})
                .build();
    }

    private EnumProperty<Choice> enumProperty() {
        return new EnumProperty<>(
                Key.LABEL,
                Key.TITLE,
                Material.NETHER_STAR,
                guiText,
                List.of(Choice.ALPHA, Choice.BETA, Choice.GAMMA),
                () -> widget.choice,
                (v, choice) -> choice.name(),
                value -> widget.choice = value,
                Material.PAPER,
                Material.BLACK_STAINED_GLASS_PANE,
                OPTION_SLOTS,
                3,
                scheduler);
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

    private static boolean hasGlint(ItemStack item) {
        return item.containsEnchantment(Enchantment.UNBREAKING);
    }

    /** The first lore line of an item rendered as plain text, the value-lore the editor wrote. */
    private static String valueLoreOf(ItemStack item) {
        List<Component> lore = TileText.body(item);
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(lore.get(0));
    }

    /** Catalog keys for the test editor; their text is irrelevant beyond the value-lore placeholder. */
    private enum Key implements MessageKey {
        LABEL("demo.prop.label"),
        TITLE("demo.selector.title"),
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

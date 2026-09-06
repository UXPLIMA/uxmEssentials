package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
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
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ClickContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.EditableProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.colour.ColourPickerLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.colour.ColourPickerText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.colour.ColourProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.colour.ColourSwatch;
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
 * MockBukkit coverage of increment 8: a {@link ColourProperty} clicked inside an editor running on the engine
 * editor runtime opens its colour picker as an engine child window. A {@link MenuHolder} routed by the one
 * {@link MenuListener}, rather than a uxmLib {@code SimpleGui}. The picker renders the 16-colour palette plus the
 * custom-hex, clear, and back buttons at the layout slots; clicking a swatch hands its packed ARGB int to the
 * setter and reopens the parent editor in place, the clear button fires the clear runnable and reopens the parent,
 * and back reopens the parent. The custom-hex anvil submit branch rides the runtime-neutral apply seam
 * ({@link ColourProperty#applyCustom}): a valid hex writes through the setter, an invalid one writes nothing and
 * reopens the picker child. The whole open → swatch → back → close flow leaves no live refresh task.
 */
class MenuColourChildTest {

    /** A mutable fake subject whose single packed-ARGB field the colour property rewrites through a recording setter. */
    private static final class Widget {
        private int colour = SENTINEL;
    }

    private static final int SENTINEL = Integer.MIN_VALUE;
    private static final int PROP_SLOT = 10;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private GuiText guiText;
    private Scheduler scheduler;
    private TextInput textInput;
    private Menus menus;
    private Widget widget;
    private boolean cleared;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        guiText = new GuiText(new KeyMessages());
        scheduler = new SyncScheduler();
        widget = new Widget();
        cleared = false;
        // The custom-hex anvil seam and the legacy uxmLib fallback both ride uxmLib's installed listener.
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
    void clickingTheColourPropertyOpensAnEngineChildWithThePaletteCustomClearAndBack() {
        openEditor();

        fireClick(PROP_SLOT, ClickType.LEFT);

        Inventory child = player.getOpenInventory().getTopInventory();
        assertThat(child.getHolder()).isInstanceOf(MenuHolder.class);
        ColourPickerLayout layout = ColourPickerLayout.codeDefault();
        // Every palette slot carries its swatch's configured stained-glass pane material.
        for (int i = 0; i < layout.paletteSlots().size(); i++) {
            assertThat(child.getItem(layout.paletteSlots().get(i)).getType())
                    .isEqualTo(layout.paletteIcons().get(i));
        }
        assertThat(child.getItem(layout.customSlot()).getType()).isEqualTo(Material.ANVIL);
        assertThat(child.getItem(layout.clearSlot()).getType()).isEqualTo(Material.BARRIER);
        assertThat(child.getItem(layout.backSlot()).getType()).isEqualTo(Material.ARROW);
    }

    @Test
    void clickingASwatchWritesThePackedArgbAndReopensTheParentEditor() {
        openEditor();
        fireClick(PROP_SLOT, ClickType.LEFT);

        // Slot 11 is the second palette swatch (orange) in the code-default layout.
        fireClick(11, ClickType.LEFT);

        assertThat(widget.colour).isEqualTo(ColourSwatch.ORANGE.argb());
        // A swatch reopens the parent editor (matching the old picker, whose pick runs context.reopen()).
        Inventory parent = player.getOpenInventory().getTopInventory();
        assertThat(parent.getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(((MenuHolder) parent.getHolder()).editor()).isPresent();
        assertThat(parent.getItem(PROP_SLOT).getType()).isEqualTo(Material.PAINTING);
    }

    @Test
    void clickingClearFiresTheClearRunnableAndReopensTheParentEditor() {
        widget.colour = 0xFF112233;
        openEditor();
        fireClick(PROP_SLOT, ClickType.LEFT);

        fireClick(ColourPickerLayout.codeDefault().clearSlot(), ClickType.LEFT);

        assertThat(cleared).isTrue();
        assertThat(widget.colour).isEqualTo(SENTINEL);
        Inventory parent = player.getOpenInventory().getTopInventory();
        assertThat(parent.getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(((MenuHolder) parent.getHolder()).editor()).isPresent();
    }

    @Test
    void clickingBackReopensTheParentEditor() {
        openEditor();
        fireClick(PROP_SLOT, ClickType.LEFT);

        fireClick(ColourPickerLayout.codeDefault().backSlot(), ClickType.LEFT);

        Inventory parent = player.getOpenInventory().getTopInventory();
        assertThat(parent.getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(((MenuHolder) parent.getHolder()).editor()).isPresent();
        assertThat(parent.getItem(PROP_SLOT).getType()).isEqualTo(Material.PAINTING);
    }

    @Test
    void theCustomHexApplySeamSavesAValidColourAndReopensTheParent() {
        // The custom-hex anvil submit branch reaches the runtime-neutral applyCustom seam (player.openAnvil is
        // unimplemented in MockBukkit, exactly as ListPropertyApplyTest drives applyAdd/applyEdit). A valid hex
        // writes the parsed ARGB through the setter and reopens the parent editor.
        openEditor();
        fireClick(PROP_SLOT, ClickType.LEFT);
        ClickContext picker = engineContext();

        property().applyCustom(picker, "#FF5733");

        assertThat(widget.colour).isEqualTo(0xFFFF5733);
        Inventory parent = player.getOpenInventory().getTopInventory();
        assertThat(parent.getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(((MenuHolder) parent.getHolder()).editor()).isPresent();
    }

    @Test
    void theCustomHexApplySeamRejectsAnInvalidColourWithoutWritingAndReopensThePicker() {
        widget.colour = 0xFF445566;
        openEditor();
        fireClick(PROP_SLOT, ClickType.LEFT);
        ClickContext picker = engineContext();

        property().applyCustom(picker, "not-a-colour");

        // The invalid line wrote nothing and reopened the picker child, not the parent editor.
        assertThat(widget.colour).isEqualTo(0xFF445566);
        Inventory child = player.getOpenInventory().getTopInventory();
        assertThat(child.getHolder()).isInstanceOf(MenuHolder.class);
        assertThat(((MenuHolder) child.getHolder()).editor()).isEmpty();
        assertThat(child.getItem(ColourPickerLayout.codeDefault().customSlot()).getType())
                .isEqualTo(Material.ANVIL);
    }

    @Test
    void openSwatchBackCloseLeavesNoLiveRefreshTask() {
        var recording = new RecordingScheduler();
        installEngine(recording);

        menus.openEditor(viewer, editorSpec(), widget);
        fireClick(PROP_SLOT, ClickType.LEFT); // open picker child
        fireClick(11, ClickType.LEFT); // swatch → setter + reopen parent
        fireClick(PROP_SLOT, ClickType.LEFT); // reopen the picker child
        fireClick(ColourPickerLayout.codeDefault().backSlot(), ClickType.LEFT); // back → parent editor
        player.closeInventory();
        player.closeInventory(); // a double-close is a harmless no-op

        // Neither the editor nor the picker arms a refresh timer, so start and cancel both stay balanced at zero.
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
                .properties(w -> List.<EditableProperty>of(property()))
                .onBack((p, v) -> {})
                .build();
    }

    /** A context on the engine path: it carries the engine openers so a custom-hex reopen takes the engine branch. */
    private ClickContext engineContext() {
        return new ClickContext(
                player,
                viewer,
                false,
                false,
                () -> menus.openEditor(viewer, editorSpec(), widget),
                menus.selectorOpener(),
                menus.confirmOpener());
    }

    private ColourProperty property() {
        return new ColourProperty(
                Key.LABEL,
                Material.PAINTING,
                () -> widget.colour,
                value -> widget.colour = value,
                () -> {
                    cleared = true;
                    widget.colour = SENTINEL;
                },
                SENTINEL,
                v -> "default",
                guiText,
                ColourPickerText.shared(),
                ColourPickerLayout.codeDefault(),
                textInput,
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

    /** Catalog keys for the test editor and picker; their text is irrelevant beyond the value-lore placeholder. */
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

package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmlib.bedrock.BedrockButton;
import com.uxplima.uxmlib.bedrock.BedrockDetector;
import com.uxplima.uxmlib.bedrock.BedrockImage;
import com.uxplima.uxmlib.bedrock.BedrockScreen;
import com.uxplima.uxmlib.bedrock.BedrockWidget;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The proof that the grid→form degradation is clean: a Bedrock viewer's SimpleForm shows only the actionable buttons,
 * each labelled with the item's name and lore, and a tap maps into that filtered list, while the Java chest keeps
 * painting every item, decorative fillers included. A real {@link MenuSpecLoader} and {@link Menus} drive it; a fake
 * {@link BedrockDetector} / {@link BedrockScreen} stand in for the Cumulus/Floodgate SDK, a {@code compileOnly}
 * soft-depend absent from the test runtime.
 */
class BedrockDegradationGoldenTest {

    // Slot 0 and slot 2 carry click actions; slot 1 is a blank pane with no click, a decorative filler.
    private static final String SHOP = """
            title = "Shop"
            rows = 1
            items {
              shop { slot = 0, material = DIAMOND, name = "Shop", lore = ["Buy items"], click { left { click = ["record:shop"] } } }
              pane { slot = 1, material = GRAY_STAINED_GLASS_PANE, name = "" }
              info { slot = 2, material = BOOK, name = "Info", click { left { click = ["record:info"] } } }
            }
            """;

    private ServerMock server;
    private PlayerMock player;
    private MenuBindings bindings;
    private MenuRenderer renderer;
    private Scheduler scheduler;
    private MenuSpecLoader loader;
    private FakeBedrockDetector detector;
    private FakeBedrockScreen screen;
    private AtomicReference<String> captured;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin();
        player = server.addPlayer("Viewer");
        captured = new AtomicReference<>();

        bindings = new MenuBindings();
        bindings.action("record", ctx -> captured.set(ctx.arg()));
        GuiText guiText = new GuiText(new KeyMessages());
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        scheduler = new SyncScheduler();
        detector = new FakeBedrockDetector();
        screen = new FakeBedrockScreen();
        loader = new MenuSpecLoader();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aBedrockFormShowsOnlyActionableButtonsLabelledWithNameAndLore() {
        detector.bedrock = true;
        Menus menus = engine();
        menus.registerSpec("shop", loader.parse(SHOP));

        open(menus, "shop");

        assertThat(menuOpen())
                .as("a Bedrock viewer is redirected to a form, so no chest is opened")
                .isFalse();
        assertThat(screen.title).as("the form carries the menu's own title").isEqualTo("Shop");
        assertThat(screen.buttons)
                .extracting(BedrockButton::text)
                .as(
                        "the blank pane has no click, so it is skipped; the two actionable items become buttons in slot order")
                .containsExactly("Shop\nBuy items", "Info");
        assertThat(screen.buttons)
                .extracting(BedrockButton::image)
                .as("each actionable material item carries a Bedrock texture-path icon")
                .containsExactly(
                        new BedrockImage(BedrockImage.Kind.PATH, "textures/items/diamond"),
                        new BedrockImage(BedrockImage.Kind.PATH, "textures/items/book"));
    }

    @Test
    void tappingAButtonMapsIntoTheFilteredListSoTheFillerNeverShifts() {
        detector.bedrock = true;
        Menus menus = engine();
        menus.registerSpec("shop", loader.parse(SHOP));

        open(menus, "shop");

        screen.tap(0);
        assertThat(captured.get())
                .as("button 0 is Shop, the first actionable item")
                .isEqualTo("shop");

        screen.tap(1);
        assertThat(captured.get())
                .as("button 1 is Info. The filler at slot 1 was dropped, so Info moved up to index 1, not index 2")
                .isEqualTo("info");
    }

    @Test
    void aJavaViewerKeepsEveryItemInTheChestFillerIncluded() {
        detector.bedrock = false;
        Menus menus = engine();
        menus.registerSpec("shop", loader.parse(SHOP));

        open(menus, "shop");

        assertThat(menuOpen()).as("a Java viewer opens the chest as before").isTrue();
        assertThat(screen.sent).as("no form is sent for a Java viewer").isFalse();

        Inventory top = player.getOpenInventory().getTopInventory();
        assertThat(top.getItem(0))
                .extracting(item -> item == null ? null : item.getType())
                .as("the actionable Shop icon is painted")
                .isEqualTo(Material.DIAMOND);
        assertThat(top.getItem(1))
                .extracting(item -> item == null ? null : item.getType())
                .as("the decorative filler the form dropped is still painted in the chest")
                .isEqualTo(Material.GRAY_STAINED_GLASS_PANE);
        assertThat(top.getItem(2))
                .extracting(item -> item == null ? null : item.getType())
                .as("the actionable Info icon is painted")
                .isEqualTo(Material.BOOK);
    }

    /** The production-shaped façade: action registry threaded in, plus the fake detector and screen. */
    private Menus engine() {
        return new Menus(
                renderer,
                scheduler,
                bindings.lists(),
                null,
                bindings.actions(),
                bindings.conditions(),
                null,
                detector,
                screen);
    }

    private void open(Menus menus, String specId) {
        menus.open(new PlayerRef(player.getUniqueId(), player.getName()), specId, null);
    }

    /** Whether the viewer is looking at one of this engine's chest windows. A redirected open never opens one. */
    private boolean menuOpen() {
        InventoryView view = player.getOpenInventory();
        Inventory top = view == null ? null : view.getTopInventory();
        return top != null && top.getHolder() instanceof MenuHolder;
    }

    /** Records the last form it was asked to send and hands the test the select callback to invoke a tap with. */
    private static final class FakeBedrockScreen implements BedrockScreen {

        @Override
        public void sendCustomForm(
                Player player,
                String title,
                @Nullable String content,
                List<BedrockWidget> widgets,
                Consumer<Map<String, String>> onSubmit,
                Runnable onClose) {}

        private boolean sent;
        private @Nullable String title;
        private List<BedrockButton> buttons = List.of();
        private @Nullable IntConsumer onSelect;

        @Override
        public void sendSimpleForm(
                Player player,
                String title,
                @Nullable String content,
                List<BedrockButton> buttons,
                IntConsumer onSelect) {
            this.sent = true;
            this.title = title;
            this.buttons = List.copyOf(buttons);
            this.onSelect = onSelect;
        }

        @Override
        public void sendModalForm(
                Player player,
                String title,
                @Nullable String content,
                String button1,
                String button2,
                Runnable onButton1,
                Runnable onButton2) {}

        @Override
        public void sendInputForm(
                Player player,
                String title,
                String inputLabel,
                @Nullable String initial,
                Consumer<String> onSubmit,
                Runnable onClose) {}

        void tap(int index) {
            if (onSelect != null) {
                onSelect.accept(index);
            }
        }
    }

    /** A detector whose Bedrock answer the test flips per case; no Floodgate SDK involved. */
    private static final class FakeBedrockDetector implements BedrockDetector {
        private boolean bedrock;

        @Override
        public boolean isBedrock(UUID player) {
            return bedrock;
        }
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
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

        @Override
        public AutoCloseable repeatGlobal(Runnable task, Duration initialDelay, Duration period) {
            return () -> {};
        }
    }
}

package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

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
 * The proof that a per-menu {@code bedrock {}} native CustomForm override works end to end: a real
 * {@link MenuSpecLoader} parses the block, a real {@link Menus} wired with the live {@link MenuBindings} action
 * registry opens it, and a fake {@link BedrockDetector} / {@link BedrockScreen} stand in for the Cumulus/Floodgate
 * SDK (a {@code compileOnly} soft-depend absent from the test runtime). A Bedrock viewer opening a menu that HAS a
 * {@code bedrock {}} block gets that CustomForm, not the chest and not the automatic SimpleForm, with the widgets
 * resolved in order; submitting binds each widget value to a {@code %name%} placeholder the on-submit actions read.
 * A menu without the block still auto-degrades to a SimpleForm, and a Java viewer keeps the chest.
 */
class BedrockCustomFormGoldenTest {

    private static final String CUSTOM_FORM = """
            title = "Warps"
            rows = 1
            items { a { slot = 0, material = STONE, name = "A", click { left { click = ["record:tapped"] } } } }
            bedrock {
              title = "Create Warp"
              content = "Fill in the details"
              widgets = [
                { type = input,    name = warpname, label = "<gold>Name", placeholder = "spawn", default = "" }
                { type = dropdown, name = category, label = "Category", options = ["PvP","Hub"], default = 0 }
                { type = slider,   name = cost,     label = "Cost", min = 0, max = 1000, step = 50, default = 100 }
              ]
              on-submit = [ "record:%warpname%|%category%|%cost%" ]
            }
            """;

    private static final String NO_BEDROCK = """
            title = "Plain"
            rows = 1
            items { a { slot = 0, material = STONE, name = "A", click { left { click = ["record:auto"] } } } }
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
    void aBedrockViewerGetsTheExplicitCustomFormNotAChestAndNotTheAutoSimpleForm() {
        detector.bedrock = true;
        Menus menus = engine();
        menus.registerSpec("m", loader.parse(CUSTOM_FORM));

        open(menus, "m");

        assertThat(menuOpen())
                .as("a bedrock {} override redirects to a CustomForm, so no chest MenuHolder is opened")
                .isFalse();
        assertThat(screen.simpleFormSent)
                .as("the explicit override wins, so the automatic SimpleForm degradation is NOT used")
                .isFalse();
        assertThat(screen.customFormSent).as("the explicit CustomForm was sent").isTrue();
        assertThat(screen.title).isEqualTo("Create Warp");
        assertThat(screen.content).isEqualTo("Fill in the details");
        assertThat(screen.widgets)
                .as("the widgets arrive resolved (MiniMessage flattened) and in declared order")
                .containsExactly(
                        new BedrockWidget.Input("warpname", "Name", "spawn", ""),
                        new BedrockWidget.Dropdown("category", "Category", List.of("PvP", "Hub"), 0),
                        new BedrockWidget.Slider("cost", "Cost", 0, 1000, 50, 100));
    }

    @Test
    void submittingTheFormBindsEachWidgetValueAndRunsTheOnSubmitActions() {
        detector.bedrock = true;
        Menus menus = engine();
        menus.registerSpec("m", loader.parse(CUSTOM_FORM));
        open(menus, "m");

        screen.submit(Map.of("warpname", "spawn", "category", "Hub", "cost", "250"));

        assertThat(captured.get())
                .as("each submitted value binds to its %name% and the on-submit action runs with them resolved")
                .isEqualTo("spawn|Hub|250");
    }

    @Test
    void closingTheFormWithoutSubmittingRunsNothing() {
        detector.bedrock = true;
        Menus menus = engine();
        menus.registerSpec("m", loader.parse(CUSTOM_FORM));
        open(menus, "m");

        assertThatCode(() -> screen.close())
                .as("the closed handler is a plain no-op; the viewer just dismissed the form")
                .doesNotThrowAnyException();
        assertThat(captured.get())
                .as("no on-submit action runs on a bare close")
                .isNull();
    }

    @Test
    void aMenuWithoutABedrockBlockStillAutoDegradesToASimpleForm() {
        detector.bedrock = true;
        Menus menus = engine();
        menus.registerSpec("p", loader.parse(NO_BEDROCK));

        open(menus, "p");

        assertThat(screen.customFormSent)
                .as("no bedrock {} block, so the CustomForm override does not fire")
                .isFalse();
        assertThat(screen.simpleFormSent)
                .as("the automatic SimpleForm degradation still applies to a plain menu")
                .isTrue();
        assertThat(menuOpen()).as("a redirected menu opens no chest").isFalse();
    }

    @Test
    void aJavaViewerKeepsTheChestAndNoFormIsSent() {
        detector.bedrock = false;
        Menus menus = engine();
        menus.registerSpec("m", loader.parse(CUSTOM_FORM));

        open(menus, "m");

        assertThat(menuOpen())
                .as("a Java viewer opens the chest exactly as before")
                .isTrue();
        assertThat(screen.customFormSent || screen.simpleFormSent)
                .as("no Bedrock form is sent for a Java viewer")
                .isFalse();
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

    /** Whether the viewer is looking at one of this engine's chest windows. A gated/redirected open never opens one. */
    private boolean menuOpen() {
        InventoryView view = player.getOpenInventory();
        Inventory top = view == null ? null : view.getTopInventory();
        return top != null && top.getHolder() instanceof MenuHolder;
    }

    /** Records which form kind it was asked to send, plus the CustomForm's parts and the submit/close callbacks. */
    private static final class FakeBedrockScreen implements BedrockScreen {
        private boolean simpleFormSent;
        private boolean customFormSent;
        private @Nullable String title;
        private @Nullable String content;
        private List<BedrockWidget> widgets = List.of();
        private @Nullable Consumer<Map<String, String>> onSubmit;
        private @Nullable Runnable onClose;

        @Override
        public void sendSimpleForm(
                Player player,
                String title,
                @Nullable String content,
                List<BedrockButton> buttons,
                IntConsumer onSelect) {
            this.simpleFormSent = true;
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

        @Override
        public void sendCustomForm(
                Player player,
                String title,
                @Nullable String content,
                List<BedrockWidget> widgets,
                Consumer<Map<String, String>> onSubmit,
                Runnable onClose) {
            this.customFormSent = true;
            this.title = title;
            this.content = content;
            this.widgets = List.copyOf(widgets);
            this.onSubmit = onSubmit;
            this.onClose = onClose;
        }

        void submit(Map<String, String> values) {
            if (onSubmit != null) {
                onSubmit.accept(values);
            }
        }

        void close() {
            if (onClose != null) {
                onClose.run();
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

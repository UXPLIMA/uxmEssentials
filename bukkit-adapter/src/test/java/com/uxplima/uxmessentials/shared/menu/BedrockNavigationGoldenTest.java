package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.LastMenu;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.MenuVocabulary;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
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
 * The proof that a Bedrock form open is recorded into the viewer's back history and fires the menu's open-actions, so
 * sub-form navigation round-trips as native forms. Menu A carries a button that opens B, menu B a button that steps
 * back; opening A for a Bedrock viewer, tapping through to B, then tapping back re-sends A's form, the history the
 * form open recorded is what {@code back} steps to. A second menu proves a form open runs the same {@code open-actions}
 * a chest open does. A real {@link MenuSpecLoader}, a real {@link Menus} wired with the live {@link MenuBindings}
 * registries and a real {@link LastMenu}, and a fake {@link BedrockDetector}/{@link BedrockScreen} standing in for the
 * Cumulus/Floodgate SDK (a {@code compileOnly} soft-depend absent from the test runtime) drive it end to end.
 */
class BedrockNavigationGoldenTest {

    private static final String MENU_A = """
            title = "Menu A"
            rows = 1
            items {
              toB { slot = 0, material = DIAMOND, name = "Go To B", click { left { click = ["open:B"] } } }
            }
            """;

    private static final String MENU_B = """
            title = "Menu B"
            rows = 1
            items {
              back { slot = 0, material = EMERALD, name = "Back", click { left { click = ["back"] } } }
            }
            """;

    private static final String OPEN_ACTION_MENU = """
            title = "Marked"
            rows = 1
            open-actions = ["record:opened"]
            items { a { slot = 0, material = STONE, name = "A" } }
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
        detector.bedrock = true;
        screen = new FakeBedrockScreen();
        loader = new MenuSpecLoader();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aBedrockViewerNavigatesAToBAndBackAsNativeForms() {
        Menus menus = engine();
        menus.registerSpec("A", loader.parse(MENU_A));
        menus.registerSpec("B", loader.parse(MENU_B));

        open(menus, "A");

        assertThat(menuOpen())
                .as("a Bedrock viewer is redirected to a form, so no chest window is opened")
                .isFalse();
        assertThat(screen.buttons)
                .extracting(BedrockButton::text)
                .as("opening A sends A's form")
                .containsExactly("Go To B");

        screen.tap(0);

        assertThat(screen.buttons)
                .extracting(BedrockButton::text)
                .as("tapping A's open:B button sends B's form. Sub-form navigation as native forms")
                .containsExactly("Back");

        screen.tap(0);

        assertThat(screen.buttons)
                .extracting(BedrockButton::text)
                .as("tapping B's back button re-sends A's form from the recorded history")
                .containsExactly("Go To B");
        assertThat(menuOpen())
                .as("every step stayed a form; no chest window was ever opened")
                .isFalse();
    }

    @Test
    void openingABedrockFormRunsTheMenusOpenActions() {
        Menus menus = engine();
        menus.registerSpec("marked", loader.parse(OPEN_ACTION_MENU));

        open(menus, "marked");

        assertThat(screen.sent)
                .as("the Bedrock viewer gets a form, not a chest")
                .isTrue();
        assertThat(captured.get())
                .as("a form open fires the menu's open-actions, the same as a chest open does")
                .isEqualTo("opened");
    }

    /** The production-shaped façade: the action/condition registries, a real reopen tracker, the fake detector/screen. */
    private Menus engine() {
        Menus menus = new Menus(
                renderer,
                scheduler,
                bindings.lists(),
                null,
                bindings.actions(),
                bindings.conditions(),
                new LastMenu(),
                detector,
                screen);
        // The generic vocab (back/open) resolves through the very engine it drives; registering after construction is
        // fine because the engine holds the same registry instance this writes to.
        MenuVocabulary.registerActions(bindings, menus, false, new NoopLogger());
        return menus;
    }

    private void open(Menus menus, String specId) {
        menus.open(new PlayerRef(player.getUniqueId(), player.getName()), specId, null);
    }

    /** Whether the viewer is looking at one of this engine's chest windows. A redirected form open never opens one. */
    private boolean menuOpen() {
        InventoryView view = player.getOpenInventory();
        Inventory top = view == null ? null : view.getTopInventory();
        return top != null && top.getHolder() instanceof MenuHolder;
    }

    /** Records the last form it was asked to send and hands the test the select callback to invoke a tap with. */
    private static final class FakeBedrockScreen implements BedrockScreen {

        private boolean sent;
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
            this.buttons = List.copyOf(buttons);
            this.onSelect = onSelect;
        }

        @Override
        public void sendCustomForm(
                Player player,
                String title,
                @Nullable String content,
                List<BedrockWidget> widgets,
                Consumer<Map<String, String>> onSubmit,
                Runnable onClose) {}

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

    /** A detector whose Bedrock answer the test sets; no Floodgate SDK involved. */
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

    private static final class NoopLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
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

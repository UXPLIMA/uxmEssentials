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
 * The proof that a list-backed menu degrades to a paged Bedrock form: the current page's entries become buttons after
 * the static ones, form-native Previous/Next buttons appear only when a further page exists, and tapping one re-sends
 * the form one page over, while a static-only menu adds no page controls at all. A real {@link MenuSpecLoader} and
 * {@link Menus} drive it; a fake {@link BedrockDetector} / {@link BedrockScreen} stand in for the Cumulus/Floodgate
 * SDK, a {@code compileOnly} soft-depend absent from the test runtime.
 */
class BedrockPaginationGoldenTest {

    // A static actionable "Home" button plus a list of five warps over three content slots: page 0 shows three,
    // page 1 shows two. The template name is the entry itself, so every warp button is distinguishable.
    private static final String WARPS = """
            title = "Warps"
            rows = 2
            items {
              home { slot = 0, material = COMPASS, name = "Home", click { left { click = ["record:home"] } } }
              list {
                slots = ["9-11"],
                list { source = "warpsource", template { material = ENDER_PEARL, name = "%v%", click { left { click = ["pick"] } } } }
              }
            }
            """;

    // A static-only menu: no list item, so the form carries only the two static buttons and no page controls.
    private static final String STATIC = """
            title = "Menu"
            rows = 1
            items {
              a { slot = 0, material = DIAMOND, name = "Alpha", click { left { click = ["record:alpha"] } } }
              b { slot = 1, material = EMERALD, name = "Beta", click { left { click = ["record:beta"] } } }
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
    private AtomicReference<String> picked;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin();
        player = server.addPlayer("Viewer");
        captured = new AtomicReference<>();
        picked = new AtomicReference<>();

        bindings = new MenuBindings();
        bindings.action("record", ctx -> captured.set(ctx.arg()));
        bindings.action("pick", ctx -> picked.set(ctx.entry(String.class)));
        bindings.list("warpsource", ctx -> List.of("w1", "w2", "w3", "w4", "w5"));
        bindings.placeholders().register("v", ctx -> ctx.entry(String.class));
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
    void pageZeroShowsTheStaticButtonThenTheFirstPageEntriesThenNext() {
        detector.bedrock = true;
        Menus menus = engine();
        menus.registerSpec("warps", loader.parse(WARPS));

        open(menus, "warps");

        assertThat(menuOpen())
                .as("a Bedrock viewer is redirected to a form, so no chest is opened")
                .isFalse();
        assertThat(screen.title).as("the form carries the menu's own title").isEqualTo("Warps");
        assertThat(screen.buttons)
                .extracting(BedrockButton::text)
                .as("static Home, then page 0's three warp entries, then Next. No Previous on the first page")
                .containsExactly("Home", "w1", "w2", "w3", "gui.page.next");
        assertThat(screen.buttons)
                .extracting(BedrockButton::image)
                .as("the material buttons carry texture-path icons; the form-native page control is text-only")
                .containsExactly(
                        new BedrockImage(BedrockImage.Kind.PATH, "textures/items/compass"),
                        new BedrockImage(BedrockImage.Kind.PATH, "textures/items/ender_pearl"),
                        new BedrockImage(BedrockImage.Kind.PATH, "textures/items/ender_pearl"),
                        new BedrockImage(BedrockImage.Kind.PATH, "textures/items/ender_pearl"),
                        null);
    }

    @Test
    void tappingAnEntryRunsTheTemplateActionBoundToThatEntry() {
        detector.bedrock = true;
        Menus menus = engine();
        menus.registerSpec("warps", loader.parse(WARPS));

        open(menus, "warps");

        screen.tap(2);
        assertThat(picked.get())
                .as("button 2 is the second entry on page 0, so its template action runs with entry w2")
                .isEqualTo("w2");

        screen.tap(0);
        assertThat(captured.get())
                .as("button 0 is the static Home item, its own action runs")
                .isEqualTo("home");
    }

    @Test
    void tappingNextResendsThePageOneFormAndPreviousStepsBack() {
        detector.bedrock = true;
        Menus menus = engine();
        menus.registerSpec("warps", loader.parse(WARPS));

        open(menus, "warps");

        screen.tap(4);
        assertThat(screen.buttons)
                .extracting(BedrockButton::text)
                .as(
                        "the form is re-sent for page 1: static Home, the last two entries, then Previous, no Next on the last page")
                .containsExactly("Home", "w4", "w5", "gui.page.previous");

        screen.tap(1);
        assertThat(picked.get())
                .as("button 1 on page 1 is entry w4. The re-sent form routes into page 1's entries")
                .isEqualTo("w4");

        screen.tap(3);
        assertThat(screen.buttons)
                .extracting(BedrockButton::text)
                .as("tapping Previous steps back to page 0's button list")
                .containsExactly("Home", "w1", "w2", "w3", "gui.page.next");
    }

    @Test
    void aStaticOnlyMenuAddsNoPageControls() {
        detector.bedrock = true;
        Menus menus = engine();
        menus.registerSpec("s", loader.parse(STATIC));

        open(menus, "s");

        assertThat(screen.buttons)
                .extracting(BedrockButton::text)
                .as("no list item, so the form is just the static buttons, byte-identical to before this slice")
                .containsExactly("Alpha", "Beta");
    }

    /** The production-shaped façade: action + list registries threaded in, plus the fake detector and screen. */
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

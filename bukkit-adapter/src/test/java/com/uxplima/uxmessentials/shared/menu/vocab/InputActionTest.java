package com.uxplima.uxmessentials.shared.menu.vocab;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputMode;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.PagedListSourceRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuTextPrompt;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the {@code input:} menu step, the continuation split the click dispatcher runs when a
 * gesture's action chain reaches a text prompt. A {@link RecordingPrompt} stands in for the text-input seam so the
 * test drives the submit and cancel callbacks by hand, exactly where the real seam would fire them (the viewer's
 * entity thread). The chain is loaded from HOCON and opened through the real {@link Menus} path, so this exercises the
 * loader, the dispatcher and the {@code %input%} substitution end to end.
 */
class InputActionTest {

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private Menus menus;
    private MenuBindings bindings;
    private RecordingPrompt prompt;
    private List<String> captured;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        captured = new ArrayList<>();
        bindings = new MenuBindings();
        // A probe action that records its resolved argument, so a continuation ref written capture:%input% proves the
        // submitted line reached the next action.
        bindings.action("capture", ctx -> captured.add(ctx.arg()));
        Scheduler scheduler = new SyncScheduler();
        ItemRenderer itemRenderer = new ItemRenderer(new GuiText(new KeyMessages()), bindings.placeholders());
        MenuRenderer renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        menus = new Menus(renderer, scheduler, bindings.lists());
        prompt = new RecordingPrompt();
        com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener listener =
                new com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuListener(
                        renderer,
                        bindings.actions(),
                        bindings.conditions(),
                        scheduler,
                        plugin,
                        null,
                        null,
                        null,
                        0L,
                        System::currentTimeMillis,
                        new PagedListSourceRegistry(),
                        prompt);
        server.getPluginManager().registerEvents(listener, plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void submittingTheInputRunsTheNextActionWithInputSetToTheTypedLine() {
        openNamer("""
                { do = "input:test.rename", prompt = "@test.prompt", default = "@test.default" },
                "capture:%input%"
                """);
        leftClick();

        assertThat(prompt.prompts)
                .as("the click reached the input step and opened a prompt")
                .isEqualTo(1);
        assertThat(prompt.initialText)
                .as("the step's default resolved into the pre-fill")
                .isEqualTo("test.default");
        assertThat(captured).as("nothing runs until the line is submitted").isEmpty();

        prompt.submit("Hello");

        assertThat(captured)
                .as("the next action in the list saw %input% set to the submitted text")
                .containsExactly("Hello");
    }

    @Test
    void cancellingTheInputRunsTheDenyRefsAndNotTheRemainingRefs() {
        openNamer("""
                { do = "input:test.rename", prompt = "@test.prompt", deny = ["capture:CANCELLED"] },
                "capture:%input%"
                """);
        leftClick();

        prompt.cancel();

        assertThat(captured)
                .as("cancel runs the deny refs and abandons the rest of the chain")
                .containsExactly("CANCELLED");
    }

    @Test
    void anInputAsTheLastRefSubmitsWithoutErrorAndRunsNoContinuation() {
        openNamer("""
                { do = "input:test.rename", prompt = "@test.prompt" }
                """);
        leftClick();

        prompt.submit("Hello");

        assertThat(captured)
                .as("a trailing input step has no continuation to run")
                .isEmpty();
    }

    @Test
    void inputModeParsesSignAndDialog() {
        assertThat(InputMode.parse("sign")).contains(InputMode.SIGN);
        assertThat(InputMode.parse("dialog")).contains(InputMode.DIALOG);
    }

    /** Register and open a one-item menu whose left click carries {@code leftChain} as its action list. */
    private void openNamer(String leftChain) {
        menus.registerSpec(
                "menu",
                new com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecLoader()
                        .parse("""
                                rows = 1
                                items {
                                  namer { slot = 0, material = DIAMOND, name = "x", click {
                                    left = [ %s ]
                                  } }
                                }
                                """.formatted(leftChain)));
        menus.open(viewer, "menu", null);
    }

    private void leftClick() {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, 0, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    /** A synchronous stand-in for the text-input seam: it records the callbacks so the test fires submit/cancel. */
    private static final class RecordingPrompt implements MenuTextPrompt {
        int prompts;

        @Nullable String initialText;

        @Nullable Consumer<String> onSubmit;

        @Nullable Runnable onCancel;

        @Override
        public void prompt(
                org.bukkit.entity.Player player,
                String key,
                Component promptLabel,
                @Nullable String initialText,
                Consumer<String> onSubmit,
                Runnable onCancel) {
            this.prompts++;
            this.initialText = initialText;
            this.onSubmit = onSubmit;
            this.onCancel = onCancel;
        }

        void submit(String text) {
            java.util.Objects.requireNonNull(onSubmit, "onSubmit").accept(text);
        }

        void cancel() {
            java.util.Objects.requireNonNull(onCancel, "onCancel").run();
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
    }
}

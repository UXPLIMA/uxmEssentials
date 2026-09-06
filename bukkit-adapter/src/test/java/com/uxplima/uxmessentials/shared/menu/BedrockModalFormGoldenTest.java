package com.uxplima.uxmessentials.shared.menu;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ConfirmRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
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
 * The proof that the confirm choke-point renders as a native Bedrock ModalForm: a real {@link Menus} opens a confirm
 * for a viewer, a fake {@link BedrockDetector} / {@link BedrockScreen} stand in for the Cumulus/Floodgate SDK (a
 * {@code compileOnly} soft-depend absent from the test runtime), and a Bedrock viewer gets a two-button ModalForm
 * instead of the confirm chest. Tapping the first button runs {@code onYes}, the second runs {@code onNo}, each on
 * the viewer's entity thread; a Java viewer keeps the lime/red confirm chest byte-identically and the screen is
 * never called.
 */
class BedrockModalFormGoldenTest {

    private ServerMock server;
    private PlayerMock player;
    private MenuBindings bindings;
    private MenuRenderer renderer;
    private Scheduler scheduler;
    private FakeBedrockDetector detector;
    private FakeBedrockScreen screen;
    private AtomicBoolean yes;
    private AtomicBoolean no;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin();
        player = server.addPlayer("Viewer");

        bindings = new MenuBindings();
        GuiText guiText = new GuiText(new KeyMessages());
        ItemRenderer itemRenderer = new ItemRenderer(guiText, bindings.placeholders());
        renderer = new MenuRenderer(itemRenderer, bindings.conditions());
        scheduler = new SyncScheduler();
        detector = new FakeBedrockDetector();
        screen = new FakeBedrockScreen();
        yes = new AtomicBoolean(false);
        no = new AtomicBoolean(false);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aBedrockViewerGetsAModalFormNotAChestAndTheFirstButtonRunsOnYes() {
        detector.bedrock = true;
        Menus menus = engine();

        menus.confirm(viewer(), Component.text("Delete home?"), () -> yes.set(true), () -> no.set(true));

        assertThat(chestOpen())
                .as("a Bedrock viewer is redirected to a ModalForm, so no confirm chest is opened")
                .isFalse();
        assertThat(screen.sent)
                .as("the Bedrock screen is asked to send a modal form")
                .isTrue();
        assertThat(screen.title)
                .as("the form carries the confirm's own title as plain text")
                .isEqualTo("Delete home?");
        assertThat(screen.content).as("the confirm form has no body text").isNull();
        assertThat(screen.button1)
                .as("the yes button is labelled from the shared confirm-yes catalog key, not left blank")
                .isEqualTo(MenuMessage.CONFIRM_YES)
                .isNotBlank();
        assertThat(screen.button2)
                .as("the no button is labelled from the shared confirm-no catalog key, not left blank")
                .isEqualTo(MenuMessage.CONFIRM_NO)
                .isNotBlank();

        screen.tapButton1();

        assertThat(yes)
                .as("tapping the first button runs onYes on the viewer's entity thread")
                .isTrue();
        assertThat(no).as("onNo does not run when the first button is tapped").isFalse();
    }

    @Test
    void aBedrockViewersSecondButtonRunsOnNo() {
        detector.bedrock = true;
        Menus menus = engine();

        menus.confirm(viewer(), Component.text("Delete home?"), () -> yes.set(true), () -> no.set(true));
        screen.tapButton2();

        assertThat(no)
                .as("tapping the second button runs onNo on the viewer's entity thread")
                .isTrue();
        assertThat(yes)
                .as("onYes does not run when the second button is tapped")
                .isFalse();
    }

    @Test
    void aJavaViewerKeepsTheConfirmChestAndTheScreenIsNeverCalled() {
        detector.bedrock = false;
        Menus menus = engine();

        menus.confirm(viewer(), Component.text("Delete home?"), () -> yes.set(true), () -> no.set(true));

        assertThat(chestOpen())
                .as("a Java viewer opens the confirm chest exactly as before")
                .isTrue();
        Inventory top = player.getOpenInventory().getTopInventory();
        assertThat(top.getItem(ConfirmRenderer.YES_SLOT))
                .as("the lime yes button sits at its slot")
                .isNotNull()
                .extracting(item -> item.getType())
                .isEqualTo(Material.LIME_WOOL);
        assertThat(top.getItem(ConfirmRenderer.NO_SLOT))
                .as("the red no button sits at its slot")
                .isNotNull()
                .extracting(item -> item.getType())
                .isEqualTo(Material.RED_WOOL);
        assertThat(screen.sent)
                .as("the Bedrock screen is never asked to send a form for a Java viewer")
                .isFalse();
    }

    /** The production-shaped façade: the confirm renderer, plus the fake detector and screen. */
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

    private PlayerRef viewer() {
        return new PlayerRef(player.getUniqueId(), player.getName());
    }

    /** Whether the viewer is looking at one of this engine's chest windows. A gated/redirected confirm never opens one. */
    private boolean chestOpen() {
        InventoryView view = player.getOpenInventory();
        Inventory top = view == null ? null : view.getTopInventory();
        return top != null && top.getHolder() instanceof MenuHolder;
    }

    /** The two shared confirm-button keys as the fake {@link Messages} renders them: its catalog lookup is the key. */
    private static final class MenuMessage {
        static final String CONFIRM_YES = "gui.confirm.yes";
        static final String CONFIRM_NO = "gui.confirm.no";
    }

    /** Records the last modal form it was asked to send and lets the test fire either button's callback. */
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
        private @Nullable String content;
        private @Nullable String button1;
        private @Nullable String button2;
        private @Nullable Runnable onButton1;
        private @Nullable Runnable onButton2;

        @Override
        public void sendSimpleForm(
                Player player,
                String title,
                @Nullable String content,
                List<BedrockButton> buttons,
                IntConsumer onSelect) {}

        @Override
        public void sendModalForm(
                Player player,
                String title,
                @Nullable String content,
                String button1,
                String button2,
                Runnable onButton1,
                Runnable onButton2) {
            this.sent = true;
            this.title = title;
            this.content = content;
            this.button1 = button1;
            this.button2 = button2;
            this.onButton1 = onButton1;
            this.onButton2 = onButton2;
        }

        // This golden exercises only the confirm ModalForm path; the text-input CustomForm has its own golden.
        @Override
        public void sendInputForm(
                Player player,
                String title,
                String inputLabel,
                @Nullable String initial,
                Consumer<String> onSubmit,
                Runnable onClose) {}

        void tapButton1() {
            if (onButton1 != null) {
                onButton1.run();
            }
        }

        void tapButton2() {
            if (onButton2 != null) {
                onButton2.run();
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

    /** Resolves a key to its own lookup string, so a rendered label is the {@code gui.confirm.*} key itself. */
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

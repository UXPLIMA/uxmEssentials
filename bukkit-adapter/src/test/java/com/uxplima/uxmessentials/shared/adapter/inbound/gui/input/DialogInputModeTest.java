package com.uxplima.uxmessentials.shared.adapter.inbound.gui.input;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.application.message.GuiMessageKey;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmlib.bedrock.BedrockDetector;
import com.uxplima.uxmlib.bedrock.BedrockScreen;
import com.uxplima.uxmlib.gui.anvil.AnvilInput;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Proves the {@code dialog} input mode is backed by a real native dialog rather than a silent sign masquerade: a
 * wired-and-supported dialog backend is what a {@code dialog} key selects (never the sign), an unwired one falls back
 * with a single {@code event=input_mode_unavailable} log line so the operator is never left guessing, and the
 * {@link DialogTextBackend} delivers a submitted line and a cancel through the same {@link InputResult} contract the
 * sign backend uses — driven through a fake {@link DialogTextBackend.Prompt} seam, since MockBukkit cannot back a live
 * Paper dialog.
 */
class DialogInputModeTest {

    @TempDir
    Path dir;

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private PlayerRef viewer;
    private GuiText guiText;

    @BeforeEach
    void setUp() throws IOException {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        viewer = new PlayerRef(player.getUniqueId(), player.getName());
        guiText = new GuiText(new KeyMessages());
        Files.writeString(dir.resolve("text-input.conf"), "default-mode = dialog\n");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aDialogKeySelectsTheDialogBackendNotTheSign() {
        RecordingBackend dialog = new RecordingBackend();
        RecordingBackend sign = new RecordingBackend();
        CapturingLogger log = new CapturingLogger();
        TextInput textInput = textInput(sign, dialog, log);

        textInput.promptResolved(player, viewer, "k", Component.text("Name?"), "seed", value -> {}, () -> {});

        assertThat(dialog.opened)
                .as("a wired, supported dialog backend is selected for a dialog key")
                .isTrue();
        assertThat(sign.opened).as("the sign backend is not the one that ran").isFalse();
        assertThat(log.warns)
                .as("selecting a wired dialog backend logs no fallback")
                .isEmpty();
    }

    @Test
    void anUnavailableDialogFallsBackToTheSignAndLogsOnce() {
        RecordingBackend sign = new RecordingBackend();
        CapturingLogger log = new CapturingLogger();
        TextInput textInput = textInput(sign, null, log);

        textInput.promptResolved(player, viewer, "k", Component.text("Name?"), null, value -> {}, () -> {});
        textInput.promptResolved(player, viewer, "k", Component.text("Name?"), null, value -> {}, () -> {});

        assertThat(sign.opened)
                .as("an unwired dialog falls back to the sign backend")
                .isTrue();
        assertThat(log.warns)
                .as("the fallback is logged exactly once, not per prompt, and names the substitute backend")
                .containsExactly("event=input_mode_unavailable mode=dialog fallback=sign");
    }

    @Test
    void theDialogBackendDeliversSubmitThroughTheInputResultContract() {
        FakePrompt fake = new FakePrompt();
        DialogTextBackend backend = new DialogTextBackend(fake, guiText);
        List<InputResult> results = new ArrayList<>();

        backend.open(player, viewer, Component.text("Name?"), "seed", results::add);
        assertThat(fake.initial)
                .as("the dialog field is pre-seeded from the request's initial text")
                .isEqualTo("seed");
        fake.submit("HomeBase");

        assertThat(results).containsExactly(new InputResult.Submitted("HomeBase"));
    }

    @Test
    void theDialogBackendDeliversCancelThroughTheInputResultContract() {
        FakePrompt fake = new FakePrompt();
        DialogTextBackend backend = new DialogTextBackend(fake, guiText);
        List<InputResult> results = new ArrayList<>();

        backend.open(player, viewer, Component.text("Name?"), null, results::add);
        fake.cancel();

        assertThat(results).containsExactly(InputResult.Cancelled.INSTANCE);
    }

    @Test
    void theTwoButtonWordsComeFromTheCatalogAndNotFromTheCode() {
        FakePrompt fake = new FakePrompt();
        DialogTextBackend backend = new DialogTextBackend(fake, guiText);

        backend.open(player, viewer, Component.text("Name?"), null, result -> {});

        // uxmLib wrote these two words itself until 0.46.0. A plugin that hardcodes them instead ships a dialog
        // that answers in English on a Turkish server, and nothing fails to say so.
        assertThat(fake.submitLabel)
                .as("the submit button is resolved for this viewer through the message catalog")
                .isEqualTo(guiText.text(viewer, GuiMessageKey.INPUT_DIALOG_SUBMIT));
        assertThat(fake.cancelLabel)
                .as("the cancel button is resolved for this viewer through the message catalog")
                .isEqualTo(guiText.text(viewer, GuiMessageKey.INPUT_DIALOG_CANCEL));
    }

    private TextInput textInput(RecordingBackend sign, @Nullable RecordingBackend dialog, CapturingLogger log) {
        InputSettings settings = new InputSettings(dir.resolve("text-input.conf"), log);
        AnvilTextBackend anvilBackend = new AnvilTextBackend(new AnvilInput(plugin));
        ChatTextBackend chatBackend = new ChatTextBackend(plugin);
        return new TextInput(
                settings,
                guiText,
                new SyncScheduler(),
                anvilBackend,
                chatBackend,
                BedrockDetector.NONE,
                BedrockScreen.NONE,
                sign,
                dialog,
                log);
    }

    /** A backend that only records whether it was opened and with what pre-fill; it never fires the outcome. */
    private static final class RecordingBackend implements TextInputBackend {
        private boolean opened;

        @Override
        public void open(
                Player player,
                PlayerRef viewer,
                Component prompt,
                @Nullable String initialText,
                Consumer<InputResult> outcome) {
            this.opened = true;
        }
    }

    /**
     * A fake dialog seam: records the pre-fill and the two button words, and lets the test fire the submit or
     * cancel callback by hand.
     */
    private static final class FakePrompt implements DialogTextBackend.Prompt {
        @Nullable private String initial;

        @Nullable private Component submitLabel;

        @Nullable private Component cancelLabel;

        @Nullable private Consumer<String> onSubmit;

        @Nullable private Runnable onCancel;

        @Override
        public void show(
                Player player,
                Component title,
                Component label,
                Component submitLabel,
                Component cancelLabel,
                @Nullable String initial,
                Consumer<String> onSubmit,
                Runnable onCancel) {
            this.initial = initial;
            this.submitLabel = submitLabel;
            this.cancelLabel = cancelLabel;
            this.onSubmit = onSubmit;
            this.onCancel = onCancel;
        }

        void submit(String line) {
            if (onSubmit != null) {
                onSubmit.accept(line);
            }
        }

        void cancel() {
            if (onCancel != null) {
                onCancel.run();
            }
        }
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    /** Records each warn line with its {@code {}} placeholders expanded, so the fallback diagnostic can be asserted. */
    private static final class CapturingLogger implements Logger {
        private final List<String> warns = new ArrayList<>();

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {
            warns.add(expand(message, args));
        }

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}

        private static String expand(String message, Object... args) {
            String out = message;
            for (Object arg : args) {
                out = out.replaceFirst("\\{}", String.valueOf(arg));
            }
            return out;
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
        public void asyncAfter(Duration delay, Runnable task) {}
    }
}

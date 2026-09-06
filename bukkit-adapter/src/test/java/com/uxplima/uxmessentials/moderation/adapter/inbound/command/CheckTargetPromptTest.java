package com.uxplima.uxmessentials.moderation.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.moderation.adapter.ModerationServices;
import com.uxplima.uxmessentials.moderation.application.ModerationMessageKey;
import com.uxplima.uxmessentials.moderation.application.port.TargetResolver;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInputTestKit;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The bare {@code /checkban} / {@code /checkmute} anvil flow's resolve step ({@link CheckTargetPrompt#checkByName}).
 * A submitted name resolves through the moderation {@code TargetResolver} and is handed to the use-case call with
 * the resolved target; an unknown name fires no check and replies with the shared unknown-target line, the same
 * chat behaviour the raw {@code <player>} form gives. The anvil and the entity-thread open are not exercised here;
 * the {@code checkByName} seam is driven directly, mirroring how {@code PlayerPickerView.resolveTyped} is tested.
 */
class CheckTargetPromptTest {

    private static final UUID SUBJECT = UUID.fromString("00000000-0000-0000-0000-0000000000aa");
    private static final PlayerRef ACTOR =
            new PlayerRef(UUID.fromString("00000000-0000-0000-0000-0000000000bb"), "Mod");

    private ModerationServices services;
    private FakeTargets targets;
    private RecordingMessages messages;
    private RecordingSink sink;
    private List<PlayerRef> checked;

    @BeforeEach
    void setUp() {
        services = mock(ModerationServices.class);
        targets = new FakeTargets();
        messages = new RecordingMessages();
        sink = new RecordingSink();
        checked = new ArrayList<>();
        lenient().when(services.targets()).thenReturn(targets);
    }

    @Test
    void aResolvedNameRunsTheCheckWithTheResolvedTarget() {
        PlayerRef subject = new PlayerRef(SUBJECT, "Subject");
        targets.add(subject);

        prompt().checkByName(ACTOR, "Subject");

        assertThat(checked).containsExactly(subject);
        assertThat(messages.keys).isEmpty();
    }

    @Test
    void anUnknownNameRepliesUnknownTargetAndRunsNoCheck() {
        prompt().checkByName(ACTOR, "Nobody");

        assertThat(checked).isEmpty();
        assertThat(messages.keys).containsExactly("moderation.unknown-target");
    }

    private CheckTargetPrompt prompt() {
        Scheduler scheduler = new RunInline();
        TextInput textInput = TextInputTestKit.create(
                mock(Plugin.class),
                new GuiText(messages),
                scheduler,
                java.nio.file.Path.of("nonexistent"),
                new NoopLogger());
        return new CheckTargetPrompt(
                services,
                textInput,
                scheduler,
                messages,
                sink,
                "moderation.check-ban",
                ModerationMessageKey.MOD_GUI_CHECK_BAN_PROMPT,
                (actor, target) -> checked.add(target));
    }

    /** A name-to-ref resolver fake; an unseen name resolves to empty. */
    private static final class FakeTargets implements TargetResolver {
        private final Map<String, PlayerRef> known = new HashMap<>();

        void add(PlayerRef ref) {
            known.put(ref.name(), ref);
        }

        @Override
        public Optional<PlayerRef> resolve(String name) {
            return Optional.ofNullable(known.get(name));
        }
    }

    /** Runs the off-tick resolve inline so the check or the reply is observable in the same call. */
    private static final class RunInline implements Scheduler {
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

    private static final class RecordingMessages implements Messages {
        private final List<String> keys = new ArrayList<>();

        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            keys.add(key.key());
            return key.key();
        }
    }

    private static final class RecordingSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
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
}

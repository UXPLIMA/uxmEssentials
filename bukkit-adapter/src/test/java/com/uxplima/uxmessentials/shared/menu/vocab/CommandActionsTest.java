package com.uxplima.uxmessentials.shared.menu.vocab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.Plugin;

import io.papermc.paper.event.player.AsyncChatEvent;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickKind;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.CommandActions;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.BukkitClickCommandRunner;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.ClickCommandRunner;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the player/command action pack. A {@link RecordingClickCommandRunner} fake gives the
 * elevated and random command actions a deterministic assertion (which dispatch path ran, with which command),
 * MockBukkit's live event bus proves {@code chat-as-player} reaches the chat pipeline and {@code commandevent}
 * fires (and is vetoable through) the command event, and the real {@link BukkitClickCommandRunner} proves
 * {@code command-as-op} restores the viewer's prior op state. The pure {@code ;}-split and leading-{@code /} strip
 * edge cases live in assertions that need no server at all.
 */
class CommandActionsTest {

    private ServerMock server;
    private PlayerMock viewer;
    private Plugin plugin;
    private MenuBindings bindings;
    private RecordingLogger log;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        viewer = server.addPlayer("Viewer");
        bindings = new MenuBindings();
        log = new RecordingLogger();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // --- chat-as-player -----------------------------------------------------------------------------------------

    @Test
    void chatAsPlayerEmitsTheViewersChatIntoThePipeline() {
        ChatCapture capture = new ChatCapture();
        server.getPluginManager().registerEvents(capture, plugin);
        register(true, new RecordingClickCommandRunner());

        invoke("chat-as-player", "hello everyone");
        server.getScheduler().waitAsyncEventsFinished();

        assertThat(capture.messages).containsExactly("hello everyone");
    }

    @Test
    void chatIsAnAliasOfChatAsPlayer() {
        ChatCapture capture = new ChatCapture();
        server.getPluginManager().registerEvents(capture, plugin);
        register(true, new RecordingClickCommandRunner());

        invoke("chat", "aliased line");
        server.getScheduler().waitAsyncEventsFinished();

        assertThat(capture.messages).containsExactly("aliased line");
    }

    @Test
    void chatAsPlayerWithABlankMessageIsAFailSoftNoOp() {
        ChatCapture capture = new ChatCapture();
        server.getPluginManager().registerEvents(capture, plugin);
        register(true, new RecordingClickCommandRunner());

        assertThatCode(() -> invoke("chat-as-player", "   ")).doesNotThrowAnyException();
        server.getScheduler().waitAsyncEventsFinished();

        assertThat(capture.messages).isEmpty();
    }

    // --- command-as-op ------------------------------------------------------------------------------------------

    @Test
    void commandAsOpRunsAsTheViewerAndRestoresThePriorOpState() {
        RecordingCommand marker = registerCommand("opmarker");
        viewer.setOp(false);
        register(true, new BukkitClickCommandRunner());

        invoke("command-as-op", "opmarker");

        assertThat(marker.executed).as("the elevated command actually ran").isTrue();
        assertThat(viewer.isOp())
                .as("op is restored to its prior (false) state")
                .isFalse();
    }

    @Test
    void commandAsOpRoutesToTheElevatedDispatchWithTheSlashStripped() {
        RecordingClickCommandRunner runner = new RecordingClickCommandRunner();
        register(true, runner);

        invoke("command-as-op", "/give diamond");

        assertThat(runner.opCommands).containsExactly("give diamond");
        assertThat(runner.playerCommands).isEmpty();
        assertThat(runner.consoleCommands).isEmpty();
    }

    @Test
    void commandOpIsAnAliasOfCommandAsOp() {
        RecordingClickCommandRunner runner = new RecordingClickCommandRunner();
        register(true, runner);

        invoke("command-op", "fly");

        assertThat(runner.opCommands).containsExactly("fly");
    }

    @Test
    void commandAsOpIsGatedOffAndWarnsWhenConsoleDisallowed() {
        RecordingClickCommandRunner runner = new RecordingClickCommandRunner();
        register(false, runner);

        invoke("command-as-op", "give diamond");

        assertThat(runner.opCommands).as("the elevated dispatch is suppressed").isEmpty();
        assertThat(log.warnings).as("the disabled gate warns once").hasSize(1);
    }

    // --- commandevent -------------------------------------------------------------------------------------------

    @Test
    void commandEventFiresThePreprocessEventWithASlashAndPerformsTheCommand() {
        RecordingCommand marker = registerCommand("evtmarker");
        PreprocessCapture capture = new PreprocessCapture(false);
        server.getPluginManager().registerEvents(capture, plugin);
        register(true, new RecordingClickCommandRunner());

        invoke("commandevent", "evtmarker now");

        assertThat(capture.message).isEqualTo("/evtmarker now");
        assertThat(marker.executed).as("an uncancelled command is performed").isTrue();
    }

    @Test
    void commandEventThatAListenerCancelsIsNotPerformed() {
        RecordingCommand marker = registerCommand("evtmarker");
        PreprocessCapture capture = new PreprocessCapture(true);
        server.getPluginManager().registerEvents(capture, plugin);
        register(true, new RecordingClickCommandRunner());

        invoke("commandevent", "evtmarker now");

        assertThat(capture.message).isEqualTo("/evtmarker now");
        assertThat(marker.executed).as("a cancelled command never runs").isFalse();
    }

    @Test
    void commandEventWithABlankArgumentIsANoOp() {
        PreprocessCapture capture = new PreprocessCapture(false);
        server.getPluginManager().registerEvents(capture, plugin);
        register(true, new RecordingClickCommandRunner());

        assertThatCode(() -> invoke("commandevent", "   ")).doesNotThrowAnyException();

        assertThat(capture.message).as("a blank arg fires no command event").isNull();
    }

    // --- command-random -----------------------------------------------------------------------------------------

    @Test
    void commandRandomWithASingleEntryRunsThatCommandAsThePlayer() {
        RecordingClickCommandRunner runner = new RecordingClickCommandRunner();
        register(true, runner);

        invoke("command-random", "/spawn");

        assertThat(runner.playerCommands).containsExactly("spawn");
    }

    @Test
    void commandRandomPicksExactlyOneOfTheList() {
        RecordingClickCommandRunner runner = new RecordingClickCommandRunner();
        register(true, runner);

        invoke("command-random", "spawn ; warp hub ; home");

        assertThat(runner.playerCommands).hasSize(1);
        assertThat(runner.playerCommands.get(0)).isIn("spawn", "warp hub", "home");
    }

    @Test
    void commandRandomWithNoCandidatesIsANoOp() {
        RecordingClickCommandRunner runner = new RecordingClickCommandRunner();
        register(true, runner);

        invoke("command-random", "  ;  ; ");

        assertThat(runner.playerCommands).isEmpty();
    }

    // --- console-random -----------------------------------------------------------------------------------------

    @Test
    void consoleRandomPicksExactlyOneOfTheListForTheConsole() {
        RecordingClickCommandRunner runner = new RecordingClickCommandRunner();
        register(true, runner);

        invoke("console-random", "say a ; say b");

        assertThat(runner.consoleCommands).hasSize(1);
        assertThat(runner.consoleCommands.get(0)).isIn("say a", "say b");
    }

    @Test
    void consoleRandomIsGatedOffAndWarnsWhenConsoleDisallowed() {
        RecordingClickCommandRunner runner = new RecordingClickCommandRunner();
        register(false, runner);

        invoke("console-random", "say a ; say b");

        assertThat(runner.consoleCommands).isEmpty();
        assertThat(log.warnings).hasSize(1);
    }

    // --- pure grammar edge cases (no server needed) -------------------------------------------------------------

    @Test
    void choicesSplitsTrimsAndDropsBlanks() {
        assertThat(CommandActions.choices("a ; b ;; c ")).containsExactly("a", "b", "c");
    }

    @Test
    void choicesOfASingleEntryHasThatOneEntry() {
        assertThat(CommandActions.choices("/only")).containsExactly("/only");
    }

    @Test
    void stripSlashRemovesASingleLeadingSlashAfterTrimming() {
        assertThat(CommandActions.stripSlash("/spawn")).isEqualTo("spawn");
        assertThat(CommandActions.stripSlash("spawn")).isEqualTo("spawn");
        assertThat(CommandActions.stripSlash("  /warp hub ")).isEqualTo("warp hub");
        assertThat(CommandActions.stripSlash("//double")).isEqualTo("/double");
    }

    // --- helpers ------------------------------------------------------------------------------------------------

    private void register(boolean allowConsole, ClickCommandRunner runner) {
        CommandActions.register(bindings, runner, allowConsole, log);
    }

    /** Registers a recording command into the mock command map so a performed dispatch is observable. */
    private RecordingCommand registerCommand(String name) {
        RecordingCommand command = new RecordingCommand(name);
        server.getCommandMap().register("menutest", command);
        return command;
    }

    /** Builds the context the click listener would build, then fires the handler registered under {@code id}. */
    private void invoke(String id, String arg) {
        Consumer<MenuActionContext> handler =
                bindings.action(id).orElseThrow(() -> new AssertionError("action not registered: " + id));
        PlayerRef ref = new PlayerRef(viewer.getUniqueId(), viewer.getName());
        MenuActionContext ctx =
                new MenuActionContext(MenuContext.of(ref, null, 0), viewer, ClickKind.LEFT, Map.of("value", arg));
        handler.accept(ctx);
    }

    /** Records each dispatch path so a test can assert which route ran with which (slash-stripped) command. */
    private static final class RecordingClickCommandRunner implements ClickCommandRunner {
        private final List<String> consoleCommands = new ArrayList<>();
        private final List<String> playerCommands = new ArrayList<>();
        private final List<String> opCommands = new ArrayList<>();

        @Override
        public void runAsConsole(String command) {
            consoleCommands.add(command);
        }

        @Override
        public void runAsPlayer(Player player, String command) {
            playerCommands.add(command);
        }

        @Override
        public void runAsPlayerOp(Player player, String command) {
            opCommands.add(command);
        }
    }

    /** A command whose dispatch is recorded, so a performed (or suppressed) command is observable in a test. */
    private static final class RecordingCommand extends Command {
        private boolean executed;

        private RecordingCommand(String name) {
            super(name);
        }

        @Override
        public boolean execute(CommandSender sender, String label, String[] args) {
            executed = true;
            return true;
        }
    }

    /** Captures the plain text of every chat message the pipeline emits. */
    private static final class ChatCapture implements Listener {
        private final List<String> messages = new ArrayList<>();

        // The event bus invokes this reflectively, so Error Prone cannot see the call site.
        @SuppressWarnings("UnusedMethod")
        @EventHandler
        public void onChat(AsyncChatEvent event) {
            messages.add(PlainTextComponentSerializer.plainText().serialize(event.message()));
        }
    }

    /** Captures the preprocessed command line and optionally cancels the event to prove the veto path. */
    private static final class PreprocessCapture implements Listener {
        private final boolean cancel;
        private String message;

        private PreprocessCapture(boolean cancel) {
            this.cancel = cancel;
        }

        // The event bus invokes this reflectively, so Error Prone cannot see the call site.
        @SuppressWarnings("UnusedMethod")
        @EventHandler
        public void onCommand(PlayerCommandPreprocessEvent event) {
            if (message != null) {
                // MockBukkit's dispatchCommand (reached via performCommand) fires a second preprocess event that
                // real Paper does not; keep only the first: the one our action itself raised.
                return;
            }
            message = event.getMessage();
            if (cancel) {
                event.setCancelled(true);
            }
        }
    }

    /** Captures expanded info/warn lines so a test can assert what the console operator logger recorded. */
    private static final class RecordingLogger implements Logger {
        private final List<String> infos = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();

        @Override
        public void info(String message, Object... args) {
            infos.add(expand(message, args));
        }

        @Override
        public void warn(String message, Object... args) {
            warnings.add(expand(message, args));
        }

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}

        private static String expand(String message, Object... args) {
            String expanded = message;
            for (Object arg : args) {
                expanded = expanded.replaceFirst("\\{}", String.valueOf(arg));
            }
            return expanded;
        }
    }
}

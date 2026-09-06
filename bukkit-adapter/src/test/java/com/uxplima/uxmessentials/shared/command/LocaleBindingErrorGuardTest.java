package com.uxplima.uxmessentials.shared.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.LiteralMessage;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.LocaleBinding;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.LocaleStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;
import org.mockbukkit.mockbukkit.command.ConsoleCommandSenderMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * The locale binding is the one wrapper every published command flows through, so it is also the last place
 * a handler bug can be turned into something a player can read. When an executor throws an unexpected
 * {@link RuntimeException}, the binding must swallow it into the localized {@link SharedMessageKey#COMMAND_ERROR}
 * reply, record the fault for the operator, and report the command handled. Never let Paper's dispatcher hand
 * the player a raw red error. A {@link CommandSyntaxException} is Brigadier's own control flow (usage / parse
 * failures) and must still surface unchanged.
 */
class LocaleBindingErrorGuardTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final String FRIENDLY = "friendly-command-error";

    private ServerMock server;
    private RecordingLog log;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        log = new RecordingLog();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void playerReceivesFriendlyReplyWhenHandlerThrows() throws CommandSyntaxException {
        PlayerMock player = server.addPlayer("Alice");
        CommandDispatcher<CommandSourceStack> dispatcher = wrap(throwing(new IllegalStateException("kaboom")));

        int result = dispatcher.execute("boom", CommandSourceStackMock.from(player));

        assertThat(result).isEqualTo(Command.SINGLE_SUCCESS);
        assertThat(player.nextMessage()).contains(FRIENDLY);
        assertThat(log.lastCause).isInstanceOf(IllegalStateException.class);
        assertThat(log.lastMessage).contains("boom");
    }

    @Test
    void syntaxExceptionStillPropagates() {
        PlayerMock player = server.addPlayer("Alice");
        CommandDispatcher<CommandSourceStack> dispatcher = wrap(syntaxThrowing());

        assertThatThrownBy(() -> dispatcher.execute("boom", CommandSourceStackMock.from(player)))
                .isInstanceOf(CommandSyntaxException.class);
        assertThat(player.nextMessage())
                .as("Brigadier control flow is not turned into a friendly reply")
                .isNull();
        assertThat(log.lastMessage).isNull();
    }

    @Test
    void consoleReceivesFriendlyReplyWhenHandlerThrows() throws CommandSyntaxException {
        ConsoleCommandSenderMock console = server.getConsoleSender();
        CommandDispatcher<CommandSourceStack> dispatcher = wrap(throwing(new IllegalStateException("kaboom")));

        int result = dispatcher.execute("boom", CommandSourceStackMock.from(console));

        assertThat(result).isEqualTo(Command.SINGLE_SUCCESS);
        assertThat(PLAIN.serialize(console.nextComponentMessage())).contains(FRIENDLY);
    }

    private CommandDispatcher<CommandSourceStack> wrap(CommandRegistration registration) {
        LocaleBinding binding = new LocaleBinding(new NoOverrides(), Locale.ENGLISH, new FriendlyMessages(), log);
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(binding.wrap(registration).build());
        return dispatcher;
    }

    private static CommandRegistration throwing(RuntimeException failure) {
        return new StubRegistration(Commands.literal("boom")
                .executes(c -> {
                    throw failure;
                })
                .build());
    }

    private static CommandRegistration syntaxThrowing() {
        SimpleCommandExceptionType type = new SimpleCommandExceptionType(new LiteralMessage("nope"));
        return new StubRegistration(Commands.literal("boom")
                .executes(c -> {
                    throw type.create();
                })
                .build());
    }

    private record StubRegistration(LiteralCommandNode<CommandSourceStack> node) implements CommandRegistration {
        @Override
        public LiteralCommandNode<CommandSourceStack> build() {
            return node;
        }

        @Override
        public String description() {
            return "x";
        }

        @Override
        public List<String> aliases() {
            return List.of();
        }
    }

    /** Resolves the command-error key to a known marker so the friendly reply is assertable. */
    private static final class FriendlyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return SharedMessageKey.COMMAND_ERROR.key().equals(key.key()) ? FRIENDLY : key.key();
        }
    }

    /** Captures the last operator-facing error so the test can assert the fault was recorded with context. */
    private static final class RecordingLog implements Logger {
        private String lastMessage;
        private Throwable lastCause;

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {
            this.lastMessage = message;
            this.lastCause = cause;
        }

        @Override
        public void debug(String message, Object... args) {}
    }

    private static final class NoOverrides implements LocaleStore {
        @Override
        public Optional<Locale> override(PlayerRef player) {
            return Optional.empty();
        }

        @Override
        public void setOverride(PlayerRef player, Locale locale) {}

        @Override
        public void clearOverride(PlayerRef player) {}
    }
}

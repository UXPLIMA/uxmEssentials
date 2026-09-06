package com.uxplima.uxmessentials.shared.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import java.util.Map;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.UsageBinding;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * A command that requires arguments but carries no root executor (the {@code /gamemode}/{@code /pay} shape)
 * must, when run bare, resolve {@link SharedMessageKey#COMMAND_USAGE} and reply with the derived usage line
 * instead of falling through to Brigadier's red parse error. A command that already has a root executor is
 * left untouched, same executor instance, same dispatch behaviour. The recording {@link RecordingMessages}
 * echoes the resolved key and placeholders so the rendered reply is assertable.
 */
class UsageBindingTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private ServerMock server;
    private RecordingMessages messages;
    private UsageBinding binding;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        messages = new RecordingMessages();
        binding = new UsageBinding(messages);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void bareRequiredArgCommandSendsUsage() {
        PlayerMock player = server.addPlayer("Alice");
        player.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.gamemode.use", true);
        CommandRegistration wrapped = binding.wrap(new GamemodeStub());

        assertThatCode(() -> dispatch(wrapped, "gamemode", CommandSourceStackMock.from(player)))
                .doesNotThrowAnyException();

        assertThat(messages.lastKey).isEqualTo(SharedMessageKey.COMMAND_USAGE);
        assertThat(messages.lastPlaceholders)
                .containsEntry("command", "gamemode")
                .containsEntry("usage", "<mode> [<player>]")
                .containsEntry("description", "Set game mode.");
    }

    @Test
    void usageReplyReachesThePlayer() {
        PlayerMock player = server.addPlayer("Alice");
        player.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.gamemode.use", true);
        CommandRegistration wrapped = binding.wrap(new GamemodeStub());

        dispatch(wrapped, "gamemode", CommandSourceStackMock.from(player));

        String line = nextPlainMessage(player);
        assertThat(line).contains("gamemode").contains("<mode> [<player>]").contains("Set game mode.");
    }

    @Test
    void bareNestedSubcommandSendsItsOwnUsage() {
        PlayerMock player = server.addPlayer("Alice");
        var plugin = MockBukkit.createMockPlugin();
        player.addAttachment(plugin, "uxmessentials.warp.use", true);
        player.addAttachment(plugin, "uxmessentials.warp.create", true);
        CommandRegistration wrapped = binding.wrap(new WarpStub());

        assertThatCode(() -> dispatch(wrapped, "warp create", CommandSourceStackMock.from(player)))
                .doesNotThrowAnyException();

        assertThat(messages.lastKey).isEqualTo(SharedMessageKey.COMMAND_USAGE);
        assertThat(messages.lastPlaceholders)
                .containsEntry("command", "warp create")
                .containsEntry("usage", "<name>");
    }

    @Test
    void nestedArgOnlyNodeGainsAUsageExecutor() {
        LiteralCommandNode<CommandSourceStack> root =
                binding.wrap(new WarpStub()).build();

        CommandNode<CommandSourceStack> create = root.getChild("create");
        // The intermediate literal had no executor of its own (only a required <name> child); the binding gives it one
        // so bare input answers with usage rather than the vanilla parse error, while its <name> child is preserved.
        assertThat(create.getCommand()).isNotNull();
        assertThat(create.getChild("name")).isNotNull();
    }

    @Test
    void commandWithRootExecutorIsUnchanged() {
        Command<CommandSourceStack> root = c -> {
            c.getSource().getSender().sendMessage("root ran");
            return 1;
        };
        CommandRegistration wrapped = binding.wrap(new RootExecutorStub(root));

        assertThat(wrapped.build().getCommand()).isSameAs(root);
    }

    @Test
    void rootExecutorStubStillRunsItsOwnExecutor() {
        PlayerMock player = server.addPlayer("Bob");
        CommandRegistration wrapped = binding.wrap(new RootExecutorStub(c -> {
            c.getSource().getSender().sendMessage("root ran");
            return 1;
        }));

        dispatch(wrapped, "home", CommandSourceStackMock.from(player));

        assertThat(nextPlainMessage(player)).isEqualTo("root ran");
        assertThat(messages.lastKey).isNull();
    }

    @Test
    void idAndDefaultsArePreserved() {
        CommandRegistration wrapped = binding.wrap(new GamemodeStub());

        assertThat(wrapped.commandId()).isEqualTo("gamemode");
        assertThat(wrapped.defaultName()).isEqualTo("gamemode");
        assertThat(wrapped.defaultAliases()).containsExactly("gm");
        assertThat(wrapped.description()).isEqualTo("Set game mode.");
    }

    private static void dispatch(CommandRegistration reg, String input, CommandSourceStack source) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(reg.build());
        try {
            dispatcher.execute(input, source);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new AssertionError("command did not parse: " + input, e);
        }
    }

    private static String nextPlainMessage(PlayerMock player) {
        return PLAIN.serialize(
                net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(player.nextMessage()));
    }

    private record GamemodeStub() implements CommandRegistration {
        @Override
        public LiteralCommandNode<CommandSourceStack> build() {
            return Commands.literal("gamemode")
                    .requires(src -> src.getSender().hasPermission("uxmessentials.gamemode.use"))
                    .then(Commands.argument("mode", StringArgumentType.word())
                            .executes(c -> 1)
                            .then(Commands.argument("player", StringArgumentType.word())
                                    .executes(c -> 1)))
                    .build();
        }

        @Override
        public String description() {
            return "Set game mode.";
        }

        @Override
        public List<String> aliases() {
            return List.of("gm");
        }
    }

    private record WarpStub() implements CommandRegistration {
        @Override
        public LiteralCommandNode<CommandSourceStack> build() {
            return Commands.literal("warp")
                    .requires(src -> src.getSender().hasPermission("uxmessentials.warp.use"))
                    .then(Commands.literal("create")
                            .requires(src -> src.getSender().hasPermission("uxmessentials.warp.create"))
                            .then(Commands.argument("name", StringArgumentType.word())
                                    .executes(c -> 1)))
                    .build();
        }

        @Override
        public String description() {
            return "Manage warps.";
        }
    }

    private record RootExecutorStub(Command<CommandSourceStack> root) implements CommandRegistration {
        @Override
        public LiteralCommandNode<CommandSourceStack> build() {
            return Commands.literal("home")
                    .executes(root)
                    .then(Commands.literal("set").executes(c -> 1))
                    .build();
        }

        @Override
        public String description() {
            return "Home base.";
        }
    }

    /** Echoes the resolved key suffix and placeholders so the usage reply is assertable. */
    private static final class RecordingMessages implements Messages {
        private MessageKey lastKey;
        private Map<String, String> lastPlaceholders = Map.of();

        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            this.lastKey = key;
            this.lastPlaceholders = Map.copyOf(placeholders);
            StringBuilder out = new StringBuilder(key.key());
            placeholders.forEach(
                    (name, value) -> out.append(' ').append(name).append('=').append(value));
            return out.toString();
        }
    }
}

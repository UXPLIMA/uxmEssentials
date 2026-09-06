package com.uxplima.uxmessentials.moderation.command;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Map;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.moderation.adapter.ModerationServices;
import com.uxplima.uxmessentials.moderation.adapter.inbound.command.JailCommand;
import com.uxplima.uxmessentials.moderation.application.DelJail;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
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
 * Pins that jail removal now folds into {@code /jail del <name>} (the way {@code /warp del} sits under
 * {@code /warp}) and still drives the {@link DelJail} use case with the typed jail name, under the shared
 * {@code uxmessentials.moderation.jail} node. The standalone {@code /deljail} literal is gone: this guards the
 * replacement keeps the same behaviour as a subcommand of {@code /jail}. The bare {@code /jail} hub and the
 * {@code /jail <player> ...} subcommand are exercised by {@link JailGuiRootTest}.
 */
class JailDeleteSubcommandTest {

    private ServerMock server;
    private DelJail delJail;
    private ModerationServices services;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        delJail = mock(DelJail.class);
        services = mock(ModerationServices.class);
        lenient().when(services.delJail()).thenReturn(delJail);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void jailDelDrivesTheDeleteUseCaseWithTheTypedName() {
        PlayerMock staff = server.addPlayer("Operator");
        staff.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.moderation.jail", true);

        dispatch(staff, "jail del dungeon");

        verify(delJail).delete(any(PlayerRef.class), eq("dungeon"));
    }

    private void dispatch(PlayerMock sender, String input) {
        LiteralCommandNode<CommandSourceStack> node =
                new JailCommand(services, new KeyMessages(), new NoopSink(), null).build();
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(node);
        try {
            dispatcher.execute(input, CommandSourceStackMock.from(sender));
        } catch (CommandSyntaxException blockedOrBadSyntax) {
            throw new AssertionError("command did not parse: " + input, blockedOrBadSyntax);
        }
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class NoopSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
    }
}

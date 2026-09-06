package com.uxplima.uxmessentials.playerwarps.command;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.playerwarps.adapter.PlayerWarpServices;
import com.uxplima.uxmessentials.playerwarps.adapter.inbound.command.PlayerWarpCommand;
import com.uxplima.uxmessentials.playerwarps.adapter.inbound.gui.PlayerWarpBrowseMenu;
import com.uxplima.uxmessentials.playerwarps.application.EditPlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.RatePlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.UsePlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.WarpAccess;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Pins the self-service {@code /pwarp} subcommand wiring: each verb parses its arguments and drives the right use
 * case with them, and each is gated by its own permission node. The critical case is the password threading, a
 * bare {@code /pwarp <name>} teleports with {@link Optional#empty()} while {@code /pwarp <name> <password>} threads
 * the entered password to {@link UsePlayerWarp}'s {@code Optional<String>} overload (the fix that makes PASSWORD
 * warps usable again). The inline scheduler runs the off-tick task within the dispatch so the use-case call is
 * observable; the use-case logic itself is covered elsewhere, so these tests assert only the wiring.
 */
class PlayerWarpSubcommandWiringTest {

    private ServerMock server;
    private UsePlayerWarp usePlayerWarp;
    private EditPlayerWarp editPlayerWarp;
    private RatePlayerWarp ratePlayerWarp;
    private PlayerWarpServices services;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        usePlayerWarp = mock(UsePlayerWarp.class);
        editPlayerWarp = mock(EditPlayerWarp.class);
        ratePlayerWarp = mock(RatePlayerWarp.class);
        services = mock(PlayerWarpServices.class);
        lenient().when(services.usePlayerWarp()).thenReturn(usePlayerWarp);
        lenient().when(services.editPlayerWarp()).thenReturn(editPlayerWarp);
        lenient().when(services.ratePlayerWarp()).thenReturn(ratePlayerWarp);
        lenient().when(services.scheduler()).thenReturn(new RunInline());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void bareSlashPwarpOpensTheBrowseGrid() {
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);
        PlayerWarpBrowseMenu browse = mock(PlayerWarpBrowseMenu.class);
        lenient().when(services.browseView()).thenReturn(browse);

        dispatch(player, "pwarp");

        // No-arg /pwarp opens the paged pwarp-browse warp grid directly (the categories hub is reached from its own
        // control), not the landing or the management list.
        verify(browse).open(eq(player), eq(refOf(player)));
    }

    @Test
    void teleportThreadsTheEnteredPasswordToTheGate() {
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true); // the /pwarp node gates on a permission; op satisfies use without a permission wiring

        dispatch(player, "pwarp secret hunter2");

        verify(usePlayerWarp).useFor(eq(refOf(player)), eq(PlayerWarpName.of("secret")), eq(Optional.of("hunter2")));
    }

    @Test
    void teleportWithNoPasswordPassesAnEmptyPassword() {
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);

        dispatch(player, "pwarp secret");

        verify(usePlayerWarp).useFor(eq(refOf(player)), eq(PlayerWarpName.of("secret")), eq(Optional.<String>empty()));
    }

    @Test
    void accessEditReachesItsUseCaseWithTheParsedArgs() {
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);

        dispatch(player, "pwarp access base PUBLIC");

        verify(editPlayerWarp).setAccess(eq(refOf(player)), eq(PlayerWarpName.of("base")), eq(WarpAccess.PUBLIC));
    }

    @Test
    void rateIsRefusedWithoutItsNode() {
        PlayerMock player = server.addPlayer("Bob"); // not op. Holds only the base use node granted below
        player.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.pwarp.use", true);

        // Without uxmessentials.pwarp.rate the rate literal is filtered out by its .requires, so the input no longer
        // resolves to a runnable node and the verb never reaches its use case.
        assertThatThrownBy(() -> dispatcher().execute("pwarp rate base 5", CommandSourceStackMock.from(player)))
                .isInstanceOf(CommandSyntaxException.class);

        verifyNoInteractions(ratePlayerWarp);
    }

    @Test
    void rateReachesItsUseCaseWithItsNode() {
        PlayerMock player = server.addPlayer("Alice");
        player.setOp(true);

        dispatch(player, "pwarp rate base 5");

        verify(ratePlayerWarp).rate(eq(refOf(player)), eq(PlayerWarpName.of("base")), eq(5));
    }

    private static PlayerRef refOf(PlayerMock player) {
        return new PlayerRef(player.getUniqueId(), player.getName());
    }

    private void dispatch(PlayerMock sender, String input) {
        try {
            dispatcher().execute(input, CommandSourceStackMock.from(sender));
        } catch (CommandSyntaxException blockedOrBadSyntax) {
            throw new AssertionError("command did not parse: " + input, blockedOrBadSyntax);
        }
    }

    private CommandDispatcher<CommandSourceStack> dispatcher() {
        LiteralCommandNode<CommandSourceStack> node = new PlayerWarpCommand(services, new KeyMessages()).build();
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(node);
        return dispatcher;
    }

    /** Runs the off-tick task inline so the use-case call lands within the dispatch. */
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

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }
}

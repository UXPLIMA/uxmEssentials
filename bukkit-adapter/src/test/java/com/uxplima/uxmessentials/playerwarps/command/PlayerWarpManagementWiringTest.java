package com.uxplima.uxmessentials.playerwarps.command;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.playerwarps.adapter.PlayerWarpServices;
import com.uxplima.uxmessentials.playerwarps.adapter.inbound.command.PlayerWarpCommand;
import com.uxplima.uxmessentials.playerwarps.application.ArchivePlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.ManageBans;
import com.uxplima.uxmessentials.playerwarps.application.ManageMembers;
import com.uxplima.uxmessentials.playerwarps.application.ManageWhitelist;
import com.uxplima.uxmessentials.playerwarps.application.TransferPlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.WarpRole;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * Pins the {@code /pwarp} management and admin subcommand wiring: the people-management verbs
 * ({@code members}/{@code ban}/{@code unban}/{@code whitelist}) resolve their offline target and drive the T6b-2
 * use cases with the parsed arguments, and the admin group ({@code restore}/{@code purge}/{@code setowner}) drives
 * the by-id {@code :core} methods: with {@code purge} performing the delete only after the {@code confirm} step.
 * The inline scheduler runs the off-tick task within the dispatch so the use-case call is observable; the use-case
 * logic is covered in {@code :core}, so these assert only the wiring.
 */
class PlayerWarpManagementWiringTest {

    private static final UUID BOB = UUID.randomUUID();

    private ServerMock server;
    private ManageMembers manageMembers;
    private ManageBans manageBans;
    private ManageWhitelist manageWhitelist;
    private ArchivePlayerWarp archivePlayerWarp;
    private TransferPlayerWarp transferPlayerWarp;
    private PlayerLookup players;
    private PlayerWarpServices services;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        manageMembers = mock(ManageMembers.class);
        manageBans = mock(ManageBans.class);
        manageWhitelist = mock(ManageWhitelist.class);
        archivePlayerWarp = mock(ArchivePlayerWarp.class);
        transferPlayerWarp = mock(TransferPlayerWarp.class);
        players = mock(PlayerLookup.class);
        services = mock(PlayerWarpServices.class);
        lenient().when(services.manageMembers()).thenReturn(manageMembers);
        lenient().when(services.manageBans()).thenReturn(manageBans);
        lenient().when(services.manageWhitelist()).thenReturn(manageWhitelist);
        lenient().when(services.archivePlayerWarp()).thenReturn(archivePlayerWarp);
        lenient().when(services.transferPlayerWarp()).thenReturn(transferPlayerWarp);
        lenient().when(services.players()).thenReturn(players);
        lenient().when(services.scheduler()).thenReturn(new RunInline());
        lenient().when(players.findByName("Bob")).thenReturn(Optional.of(new PlayerRef(BOB, "Bob")));
        lenient()
                .when(archivePlayerWarp.adminRestore(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Result.ok());
        lenient()
                .when(archivePlayerWarp.adminHardDelete(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Result.ok());
        lenient()
                .when(transferPlayerWarp.adminSetOwner(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(Result.ok());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void memberAddParsesTheRoleAndReachesItsUseCase() {
        PlayerMock alice = op("Alice");

        dispatch(alice, "pwarp members base add Bob co-owner");

        verify(manageMembers)
                .addMember(eq(refOf(alice)), eq(PlayerWarpName.of("base")), eq(bobRef()), eq(WarpRole.CO_OWNER));
    }

    @Test
    void memberRemoveReachesItsUseCase() {
        PlayerMock alice = op("Alice");

        dispatch(alice, "pwarp members base remove Bob");

        verify(manageMembers).removeMember(eq(refOf(alice)), eq(PlayerWarpName.of("base")), eq(bobRef()));
    }

    @Test
    void banParsesTheDurationAndReasonAndReachesItsUseCase() {
        PlayerMock alice = op("Alice");

        dispatch(alice, "pwarp ban base Bob 7d griefing the plot");

        verify(manageBans)
                .ban(
                        eq(refOf(alice)),
                        eq(PlayerWarpName.of("base")),
                        eq(bobRef()),
                        eq(Optional.of(Duration.ofDays(7))),
                        eq(Optional.of("griefing the plot")));
    }

    @Test
    void banWithNoDurationIsPermanentWithNoReason() {
        PlayerMock alice = op("Alice");

        dispatch(alice, "pwarp ban base Bob");

        verify(manageBans)
                .ban(
                        eq(refOf(alice)),
                        eq(PlayerWarpName.of("base")),
                        eq(bobRef()),
                        eq(Optional.<Duration>empty()),
                        eq(Optional.<String>empty()));
    }

    @Test
    void banWithAnOutOfRangeDurationTokenFoldsItIntoTheReasonWithoutThrowing() {
        PlayerMock alice = op("Alice");

        // A digit run that overflows Long / Duration must not throw out of the handler; it is not a valid duration,
        // so it folds into the reason and the ban is permanent.
        dispatch(alice, "pwarp ban base Bob 99999999999999999999d cheating");

        verify(manageBans)
                .ban(
                        eq(refOf(alice)),
                        eq(PlayerWarpName.of("base")),
                        eq(bobRef()),
                        eq(Optional.<Duration>empty()),
                        eq(Optional.of("99999999999999999999d cheating")));
    }

    @Test
    void unbanReachesItsUseCase() {
        PlayerMock alice = op("Alice");

        dispatch(alice, "pwarp unban base Bob");

        verify(manageBans).unban(eq(refOf(alice)), eq(PlayerWarpName.of("base")), eq(bobRef()));
    }

    @Test
    void whitelistAddReachesItsUseCase() {
        PlayerMock alice = op("Alice");

        dispatch(alice, "pwarp whitelist base add Bob");

        verify(manageWhitelist).whitelist(eq(refOf(alice)), eq(PlayerWarpName.of("base")), eq(bobRef()));
    }

    @Test
    void adminRestoreReachesTheByIdCoreMethod() {
        PlayerMock alice = op("Alice");

        dispatch(alice, "pwarp admin restore 42");

        verify(archivePlayerWarp).adminRestore(eq(PlayerWarpId.of(42)));
    }

    @Test
    void adminPurgeWithoutConfirmDoesNotDelete() {
        PlayerMock alice = op("Alice");

        dispatch(alice, "pwarp admin purge 42");

        verify(archivePlayerWarp, never()).adminHardDelete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void adminPurgeWithConfirmDeletesById() {
        PlayerMock alice = op("Alice");

        dispatch(alice, "pwarp admin purge 42 confirm");

        verify(archivePlayerWarp).adminHardDelete(eq(PlayerWarpId.of(42)));
    }

    @Test
    void adminSetOwnerReassignsByIdToTheResolvedTarget() {
        PlayerMock alice = op("Alice");

        dispatch(alice, "pwarp admin setowner 42 Bob");

        verify(transferPlayerWarp).adminSetOwner(eq(PlayerWarpId.of(42)), eq(bobRef()));
    }

    @Test
    void theAdminGroupIsRefusedWithoutItsNode() {
        PlayerMock bob = server.addPlayer("Carl"); // not op, holds only the base use node
        bob.addAttachment(MockBukkit.createMockPlugin(), "uxmessentials.pwarp.use", true);

        assertThatThrownBy(() -> dispatcher().execute("pwarp admin restore 42", CommandSourceStackMock.from(bob)))
                .isInstanceOf(CommandSyntaxException.class);
    }

    private static PlayerRef bobRef() {
        return new PlayerRef(BOB, "Bob");
    }

    private PlayerMock op(String name) {
        PlayerMock player = server.addPlayer(name);
        player.setOp(true); // the verb nodes gate on permissions; op satisfies them without a permission wiring
        return player;
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

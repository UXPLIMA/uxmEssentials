package com.uxplima.uxmessentials.playerstate.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Map;

import org.bukkit.command.CommandSender;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.mojang.brigadier.CommandDispatcher;
import com.uxplima.uxmessentials.playerstate.adapter.PlayerStateServices;
import com.uxplima.uxmessentials.playerstate.adapter.inbound.command.BurnCommand;
import com.uxplima.uxmessentials.playerstate.application.Burn;
import com.uxplima.uxmessentials.playerstate.application.ClearInventory;
import com.uxplima.uxmessentials.playerstate.application.Extinguish;
import com.uxplima.uxmessentials.playerstate.application.Feed;
import com.uxplima.uxmessentials.playerstate.application.Freeze;
import com.uxplima.uxmessentials.playerstate.application.Heal;
import com.uxplima.uxmessentials.playerstate.application.ListNearby;
import com.uxplima.uxmessentials.playerstate.application.OpenContainer;
import com.uxplima.uxmessentials.playerstate.application.ResetPlaytime;
import com.uxplima.uxmessentials.playerstate.application.ResetRest;
import com.uxplima.uxmessentials.playerstate.application.SetAir;
import com.uxplima.uxmessentials.playerstate.application.SetExperience;
import com.uxplima.uxmessentials.playerstate.application.SetFoodLevel;
import com.uxplima.uxmessentials.playerstate.application.SetGamemode;
import com.uxplima.uxmessentials.playerstate.application.SetHealth;
import com.uxplima.uxmessentials.playerstate.application.SetPersonalTime;
import com.uxplima.uxmessentials.playerstate.application.SetPersonalWeather;
import com.uxplima.uxmessentials.playerstate.application.SetSpeed;
import com.uxplima.uxmessentials.playerstate.application.ShowPing;
import com.uxplima.uxmessentials.playerstate.application.ShowPlaytime;
import com.uxplima.uxmessentials.playerstate.application.ShowPosition;
import com.uxplima.uxmessentials.playerstate.application.Suicide;
import com.uxplima.uxmessentials.playerstate.application.ToggleClearInventoryConfirm;
import com.uxplima.uxmessentials.playerstate.application.ToggleFly;
import com.uxplima.uxmessentials.playerstate.application.ToggleGlow;
import com.uxplima.uxmessentials.playerstate.application.ToggleGod;
import com.uxplima.uxmessentials.playerstate.application.ToggleNightVision;
import com.uxplima.uxmessentials.playerstate.domain.BurnDuration;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of {@code /burn}'s selector fan-out through its real Brigadier node, standing in for the
 * whole air/feed/ice/gamemode family that shares {@code PlayerstateCommandSupport#resolveTargets}. With the
 * cross-cutting {@code uxmessentials.playerstate.others} node held, {@code /burn 5 @a} applies the verb once per
 * matched online player (here three), not once overall; a selector that matches nobody answers the unknown-player
 * rejection and burns no one. The {@link Burn} use case is a Mockito mock so the number of {@code burnFor} calls
 * is the observable: the effect itself is the use case's own concern and is exercised elsewhere.
 */
class BurnFanOutTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final String BASE = "uxmessentials.burn.use";
    private static final String OTHERS = "uxmessentials.playerstate.others";

    private ServerMock server;
    private Burn burn;
    private BurnCommand command;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        burn = mock(Burn.class);
        command = new BurnCommand(servicesWith(burn), new KeyEchoMessages());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void selectorMatchingThreePlayersBurnsEachOnce() {
        PlayerMock sender = staff("Alice");
        PlayerMock bob = server.addPlayer("Bob");
        PlayerMock carol = server.addPlayer("Carol");

        execute(sender, "burn 5 @a");

        // @a (the players() selector) resolves every online player in the source world: Alice, Bob, Carol.
        verify(burn).burnFor(any(), eq(BukkitRefs.toRef(sender)), any(BurnDuration.class));
        verify(burn).burnFor(any(), eq(BukkitRefs.toRef(bob)), any(BurnDuration.class));
        verify(burn).burnFor(any(), eq(BukkitRefs.toRef(carol)), any(BurnDuration.class));
        verify(burn, times(3)).burnFor(any(), any(), any(BurnDuration.class));
    }

    @Test
    void selectorMatchingNobodyRepliesUnknownAndBurnsNoOne() {
        PlayerMock sender = staff("Alice");

        execute(sender, "burn 5 Ghost");

        verify(burn, never()).burnFor(any(), any(), any(BurnDuration.class));
        assertThat(lastReply(sender)).contains(SharedMessageKey.COMMAND_UNKNOWN_PLAYER.key());
    }

    @Test
    void bareSelfFormBurnsOnlyTheSender() {
        PlayerMock sender = staff("Alice");
        server.addPlayer("Bob");

        execute(sender, "burn 5");

        verify(burn, times(1)).burnFor(any(), eq(BukkitRefs.toRef(sender)), any(BurnDuration.class));
    }

    @Test
    void consoleCanBurnAnExplicitTarget() {
        PlayerMock target = server.addPlayer("Bob");

        execute(server.getConsoleSender(), "burn 5 Bob");

        verify(burn).burnFor(eq(PlayerRef.system("CONSOLE")), eq(BukkitRefs.toRef(target)), any(BurnDuration.class));
    }

    private PlayerMock staff(String name) {
        PlayerMock player = server.addPlayer(name);
        player.addAttachment(MockBukkit.createMockPlugin(), BASE, true);
        player.addAttachment(MockBukkit.createMockPlugin(), OTHERS, true);
        return player;
    }

    private String lastReply(PlayerMock player) {
        return PLAIN.serialize(player.nextComponentMessage());
    }

    private void execute(CommandSender sender, String input) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(command.build());
        try {
            dispatcher.execute(input, CommandSourceStackMock.from(sender));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new AssertionError("command did not parse: " + input, e);
        }
    }

    private static PlayerStateServices servicesWith(Burn burn) {
        return new PlayerStateServices(
                mock(ToggleGod.class),
                mock(ToggleFly.class),
                mock(Heal.class),
                mock(Feed.class),
                mock(SetFoodLevel.class),
                mock(SetHealth.class),
                mock(SetGamemode.class),
                mock(SetSpeed.class),
                mock(Extinguish.class),
                mock(ClearInventory.class),
                mock(ToggleClearInventoryConfirm.class),
                mock(OpenContainer.class),
                mock(Suicide.class),
                mock(ListNearby.class),
                mock(ToggleNightVision.class),
                mock(ToggleGlow.class),
                mock(SetPersonalTime.class),
                mock(SetPersonalWeather.class),
                mock(SetExperience.class),
                mock(SetAir.class),
                burn,
                mock(Freeze.class),
                mock(ShowPosition.class),
                mock(ShowPing.class),
                mock(ShowPlaytime.class),
                mock(ResetPlaytime.class),
                mock(ResetRest.class),
                mock(PlayerLookup.class));
    }

    /** Echoes the resolved key's own identifier so the inline rejection key is observable on the sender. */
    private static final class KeyEchoMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }
}

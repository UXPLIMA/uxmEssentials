package com.uxplima.uxmessentials.playerstate.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Map;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.mojang.brigadier.CommandDispatcher;
import com.uxplima.uxmessentials.playerstate.adapter.PlayerStateServices;
import com.uxplima.uxmessentials.playerstate.adapter.inbound.command.WorldCommand;
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
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
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
 * MockBukkit coverage of {@code /world}: report the name, environment, and online player count of the world the
 * sender is standing in, read straight from {@code Player#getWorld()}. A pure read in the adapter, none of the
 * playerstate use cases run, so they are all mocked. The {@link Messages} fake echoes the resolved key and its
 * placeholders so the show line and the {@code world}/{@code environment}/{@code players} placeholders are
 * observable through the sender's message queue.
 */
class WorldCommandPathTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final String PERMISSION = "uxmessentials.world.use";

    private ServerMock server;
    private WorldCommand command;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        PlayerStateServices services = new PlayerStateServices(
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
                mock(Burn.class),
                mock(Freeze.class),
                mock(ShowPosition.class),
                mock(ShowPing.class),
                mock(ShowPlaytime.class),
                mock(ResetPlaytime.class),
                mock(ResetRest.class),
                mock(PlayerLookup.class));
        command = new WorldCommand(services, new EchoMessages());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theLiteralIsWorld() {
        assertThat(command.build().getLiteral()).isEqualTo("world");
    }

    @Test
    void reportsTheWorld() {
        PlayerMock player = server.addPlayer("Explorer");
        player.addAttachment(MockBukkit.createMockPlugin(), PERMISSION, true);

        execute(CommandSourceStackMock.from(player), "world");

        String line = PLAIN.serialize(player.nextComponentMessage());
        assertThat(line)
                .contains("playerstate.world.show")
                .contains("world=")
                .contains("environment=")
                .contains("players=");
    }

    private void execute(CommandSourceStack source, String input) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(command.build());
        try {
            dispatcher.execute(input, source);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new AssertionError("command did not parse: " + input, e);
        }
    }

    /** Echoes the full catalog key and its placeholders as one line so the rendered reply is assertable. */
    private static final class EchoMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            StringBuilder out = new StringBuilder(key.key());
            placeholders.forEach(
                    (name, value) -> out.append(' ').append(name).append('=').append(value));
            return out.toString();
        }
    }
}

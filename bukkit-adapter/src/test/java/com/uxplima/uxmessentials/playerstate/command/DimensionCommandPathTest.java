package com.uxplima.uxmessentials.playerstate.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Map;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.mojang.brigadier.CommandDispatcher;
import com.uxplima.uxmessentials.playerstate.adapter.PlayerStateServices;
import com.uxplima.uxmessentials.playerstate.adapter.inbound.command.DimensionCommand;
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
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * MockBukkit coverage of {@code /dimension}: report the canonical dimension key plus the friendly environment of
 * the world the player is standing in, read from {@code World#getKey()} and {@code World#getEnvironment()}. A pure
 * read in the adapter: none of the playerstate use cases run, so they are all mocked. The {@link Messages} fake
 * echoes the resolved key and its placeholders so the show line and the {@code dimension} placeholder are
 * observable through the sender's message queue. The dimension key is data, so the test asserts on the
 * {@code dimension=} token rather than a specific value to stay robust across MockBukkit's default world.
 */
class DimensionCommandPathTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final String PERMISSION = "uxmessentials.dimension.use";

    private ServerMock server;
    private DimensionCommand command;

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
        command = new DimensionCommand(services, new EchoMessages());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theLiteralIsDimension() {
        assertThat(command.build().getLiteral()).isEqualTo("dimension");
    }

    @Test
    void reportsTheDimension() {
        PlayerMock player = server.addPlayer("Explorer");
        player.addAttachment(MockBukkit.createMockPlugin(), PERMISSION, true);
        // MockBukkit's stock WorldMock#getKey() is unimplemented, so stand the player in a world that
        // returns a real key: that key is exactly what /dimension reports and /world never shows.
        WorldMock keyed = new KeyedWorldMock(NamespacedKey.minecraft("the_nether"));
        server.addWorld(keyed);
        player.setLocation(new Location(keyed, 0, 64, 0));

        execute(CommandSourceStackMock.from(player), "dimension");

        String line = PLAIN.serialize(player.nextComponentMessage());
        assertThat(line).contains("playerstate.dimension.show").contains("dimension=");
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

    /** A world that returns a real dimension key, since the stock {@link WorldMock} leaves {@code getKey()} unimplemented. */
    private static final class KeyedWorldMock extends WorldMock {
        private final NamespacedKey key;

        KeyedWorldMock(NamespacedKey key) {
            this.key = key;
        }

        @Override
        public NamespacedKey getKey() {
            return key;
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

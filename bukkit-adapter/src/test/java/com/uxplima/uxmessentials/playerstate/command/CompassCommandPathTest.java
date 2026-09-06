package com.uxplima.uxmessentials.playerstate.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Map;

import org.bukkit.Location;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.mojang.brigadier.CommandDispatcher;
import com.uxplima.uxmessentials.playerstate.adapter.PlayerStateServices;
import com.uxplima.uxmessentials.playerstate.adapter.inbound.command.CompassCommand;
import com.uxplima.uxmessentials.playerstate.application.Burn;
import com.uxplima.uxmessentials.playerstate.application.ClearInventory;
import com.uxplima.uxmessentials.playerstate.application.Extinguish;
import com.uxplima.uxmessentials.playerstate.application.Feed;
import com.uxplima.uxmessentials.playerstate.application.Freeze;
import com.uxplima.uxmessentials.playerstate.application.Heal;
import com.uxplima.uxmessentials.playerstate.application.ListNearby;
import com.uxplima.uxmessentials.playerstate.application.OpenContainer;
import com.uxplima.uxmessentials.playerstate.application.PlayerstateMessageKey;
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
 * MockBukkit coverage of {@code /compass}: report the cardinal direction the player is facing, mapped from
 * the look yaw to one of eight compass points. A pure read in the adapter. None of the playerstate use cases
 * run, so they are all mocked. The {@link Messages} fake echoes each resolved key's suffix so the chosen
 * direction word is observable through the sender's message queue, including the {@code degrees} placeholder.
 */
class CompassCommandPathTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final String PERMISSION = "uxmessentials.compass.use";

    private ServerMock server;
    private CompassCommand command;

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
        command = new CompassCommand(services, new EchoMessages());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theLiteralIsCompass() {
        assertThat(command.build().getLiteral()).isEqualTo("compass");
    }

    @Test
    void yawZeroFacesSouth() {
        assertThat(facingAt(0f)).contains("south").contains("degrees=0");
    }

    @Test
    void yawNinetyFacesWest() {
        assertThat(facingAt(90f)).contains("west").contains("degrees=90");
    }

    @Test
    void yawOneEightyFacesNorth() {
        assertThat(facingAt(180f)).contains("north").contains("degrees=180");
    }

    @Test
    void yawTwoSeventyFacesEast() {
        assertThat(facingAt(270f)).contains("east").contains("degrees=270");
    }

    @Test
    void yawFortyFiveFacesSouthWest() {
        assertThat(facingAt(45f)).contains("south-west");
    }

    private String facingAt(float yaw) {
        PlayerMock player = server.addPlayer("Wayfinder");
        player.addAttachment(MockBukkit.createMockPlugin(), PERMISSION, true);
        player.teleport(new Location(server.getWorld("world"), 0.5, 64, 0.5, yaw, 0f));

        execute(CommandSourceStackMock.from(player), "compass");

        return PLAIN.serialize(player.nextComponentMessage());
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

    /**
     * Echoes the resolved key's direction word so both the direction lookup and the framing line are
     * observable. {@code compass.show} carries the already-resolved {@code direction} word plus {@code degrees}
     * as placeholders, so the assembled line names the direction.
     */
    private static final class EchoMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            String suffix = suffixOf(key);
            StringBuilder out = new StringBuilder(suffix);
            placeholders.forEach(
                    (name, value) -> out.append(' ').append(name).append('=').append(value));
            return out.toString();
        }

        private static String suffixOf(MessageKey key) {
            if (key == PlayerstateMessageKey.COMPASS_SHOW) {
                return "show";
            }
            if (key == PlayerstateMessageKey.COMPASS_NORTH) {
                return "north";
            }
            if (key == PlayerstateMessageKey.COMPASS_NORTH_EAST) {
                return "north-east";
            }
            if (key == PlayerstateMessageKey.COMPASS_EAST) {
                return "east";
            }
            if (key == PlayerstateMessageKey.COMPASS_SOUTH_EAST) {
                return "south-east";
            }
            if (key == PlayerstateMessageKey.COMPASS_SOUTH) {
                return "south";
            }
            if (key == PlayerstateMessageKey.COMPASS_SOUTH_WEST) {
                return "south-west";
            }
            if (key == PlayerstateMessageKey.COMPASS_WEST) {
                return "west";
            }
            return "north-west";
        }
    }
}

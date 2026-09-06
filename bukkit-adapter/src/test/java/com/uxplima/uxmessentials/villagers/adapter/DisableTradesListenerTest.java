package com.uxplima.uxmessentials.villagers.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.villagers.adapter.inbound.listener.DisableTradesListener;
import com.uxplima.uxmessentials.villagers.adapter.outbound.PdcVillagerFlags;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * MockBukkit coverage of the disable-trades listener: a right-click on a villager is cancelled when the global switch
 * is on, when the individual villager carries the per-villager disable flag (even with the global switch off), and left
 * alone when neither applies: plus the off-hand interaction is ignored so only a main-hand open is refused.
 */
class DisableTradesListenerTest {

    private ServerMock server;
    private WorldMock world;
    private PlayerMock player;
    private Villager villager;
    private PdcVillagerFlags flags;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");
        player = server.addPlayer("Steve");
        villager = (Villager) world.spawnEntity(new Location(world, 0, 64, 0), EntityType.VILLAGER);
        flags = new PdcVillagerFlags();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void cancelsTheInteractionWhenTradingIsDisabledGlobally() {
        DisableTradesListener listener = new DisableTradesListener(true, flags, new KeyMessages());
        PlayerInteractEntityEvent event = mainHandClick();

        listener.onInteract(event);

        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    void cancelsTheInteractionForAPerVillagerFlagEvenWithTheGlobalSwitchOff() {
        flags.setTradesDisabled(villager, true);
        DisableTradesListener listener = new DisableTradesListener(false, flags, new KeyMessages());
        PlayerInteractEntityEvent event = mainHandClick();

        listener.onInteract(event);

        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    void leavesTheInteractionAloneWhenNeitherGlobalNorPerVillagerDisableApplies() {
        DisableTradesListener listener = new DisableTradesListener(false, flags, new KeyMessages());
        PlayerInteractEntityEvent event = mainHandClick();

        listener.onInteract(event);

        // Gate off = no-op: a villager with no disable flag opens its trade GUI as normal.
        assertThat(event.isCancelled()).isFalse();
    }

    @Test
    void ignoresTheOffHandInteraction() {
        DisableTradesListener listener = new DisableTradesListener(true, flags, new KeyMessages());
        PlayerInteractEntityEvent event = new PlayerInteractEntityEvent(player, villager, EquipmentSlot.OFF_HAND);

        listener.onInteract(event);

        assertThat(event.isCancelled()).isFalse();
    }

    private PlayerInteractEntityEvent mainHandClick() {
        return new PlayerInteractEntityEvent(player, villager, EquipmentSlot.HAND);
    }

    /** Resolves each key to its own id, enough for the command-feedback path the tests do not assert on. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }
}

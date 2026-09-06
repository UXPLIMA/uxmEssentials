package com.uxplima.uxmessentials.villagers.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.villagers.adapter.inbound.command.VillagerProtectToggle;
import com.uxplima.uxmessentials.villagers.adapter.outbound.PdcVillagerFlags;
import com.uxplima.uxmessentials.villagers.domain.VillagerProtectionPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * MockBukkit coverage of the {@code /villager protect} toggle: the first toggle marks the villager protected and, when
 * the no-despawn gate is on, keeps it loaded; a second toggle clears the mark; and with the no-despawn gate off the
 * mark still flips but persistence is left alone.
 */
class VillagerProtectToggleTest {

    private static final VillagerProtectionPolicy ALL_ON =
            new VillagerProtectionPolicy(true, false, true, true, true, true);

    private ServerMock server;
    private WorldMock world;
    private PlayerMock player;
    private PlayerRef ref;
    private Villager villager;
    private PdcVillagerFlags flags;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");
        player = server.addPlayer("Steve");
        ref = new PlayerRef(player.getUniqueId(), player.getName());
        villager = (Villager) world.spawnEntity(new Location(world, 0, 64, 0), EntityType.VILLAGER);
        villager.setPersistent(false);
        flags = new PdcVillagerFlags();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theFirstToggleProtectsTheVillagerAndKeepsItLoaded() {
        toggle(ALL_ON).toggle(player, ref, villager);

        assertThat(flags.isProtected(villager)).isTrue();
        assertThat(villager.isPersistent()).isTrue();
    }

    @Test
    void aSecondToggleClearsTheMark() {
        VillagerProtectToggle toggle = toggle(ALL_ON);
        toggle.toggle(player, ref, villager);
        toggle.toggle(player, ref, villager);

        assertThat(flags.isProtected(villager)).isFalse();
    }

    @Test
    void withNoDespawnOffTheMarkFlipsButPersistenceIsLeftAlone() {
        toggle(new VillagerProtectionPolicy(true, false, true, true, true, false))
                .toggle(player, ref, villager);

        assertThat(flags.isProtected(villager)).isTrue();
        assertThat(villager.isPersistent()).isFalse();
    }

    private VillagerProtectToggle toggle(VillagerProtectionPolicy policy) {
        return new VillagerProtectToggle(new SyncScheduler(), flags, policy, new KeyMessages());
    }

    /** Resolves each key to its own id: enough for the feedback path the tests do not assert text on. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    /** Runs every scheduled task inline so the toggle's entity-thread work happens before the assertions. */
    private static final class SyncScheduler implements Scheduler {
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
}

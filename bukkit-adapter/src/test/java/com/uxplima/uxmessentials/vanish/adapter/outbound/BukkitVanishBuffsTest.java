package com.uxplima.uxmessentials.vanish.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.bukkit.potion.PotionEffectType;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of {@link BukkitVanishBuffs}: applying the buffs grants night vision and a flight allowance, and
 * clearing them removes the night vision and (for a survival player) the flight allowance again, so a reappearing
 * player is left with no residual buff. A buff whose toggle is off is never granted.
 */
class BukkitVanishBuffsTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void applyGrantsNightVisionAndFlightAndClearRemovesThem() {
        PlayerMock alice = server.addPlayer("Alice");
        BukkitVanishBuffs buffs = new BukkitVanishBuffs(server, new InlineScheduler(), true, true);
        PlayerRef ref = BukkitRefs.toRef(alice);

        buffs.apply(ref);
        assertThat(alice.hasPotionEffect(PotionEffectType.NIGHT_VISION)).isTrue();
        assertThat(alice.getAllowFlight()).isTrue();

        buffs.clear(ref);
        assertThat(alice.hasPotionEffect(PotionEffectType.NIGHT_VISION)).isFalse();
        assertThat(alice.getAllowFlight()).isFalse(); // a survival player loses the granted allowance on reappear
    }

    @Test
    void aBuffWhoseToggleIsOffIsNeverGranted() {
        PlayerMock alice = server.addPlayer("Alice");
        BukkitVanishBuffs buffs = new BukkitVanishBuffs(server, new InlineScheduler(), false, false);

        buffs.apply(BukkitRefs.toRef(alice));

        assertThat(alice.hasPotionEffect(PotionEffectType.NIGHT_VISION)).isFalse();
        assertThat(alice.getAllowFlight()).isFalse();
    }

    /** A scheduler that runs every task inline so the entity-thread hop fires at once. */
    private static final class InlineScheduler implements Scheduler {
        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }

        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
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

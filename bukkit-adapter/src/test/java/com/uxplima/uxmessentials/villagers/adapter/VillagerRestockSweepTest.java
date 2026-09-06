package com.uxplima.uxmessentials.villagers.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.villagers.adapter.outbound.PdcVillagerFlags;
import com.uxplima.uxmessentials.villagers.adapter.outbound.VillagerRestockSweep;
import com.uxplima.uxmessentials.villagers.domain.RestockPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * MockBukkit coverage of the restock sweep: a stale villager (last restock older than the interval, or never restocked)
 * has its trades reset and re-stamped, a freshly-restocked villager is left alone, and a disabled sweep schedules
 * nothing. The sweep enumerates the world on the global thread and hops each restock onto the villager's region, a
 * recording scheduler runs both inline so the sweep completes.
 */
class VillagerRestockSweepTest {

    private static final Instant NOW = Instant.parse("2026-07-17T12:00:00Z");
    private static final Duration INTERVAL = Duration.ofSeconds(60);

    private ServerMock server;
    private WorldMock world;
    private Villager villager;
    private PdcVillagerFlags flags;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");
        villager = (Villager) world.spawnEntity(new Location(world, 0, 64, 0), EntityType.VILLAGER);
        villager.setRecipes(List.of(recipe(5)));
        flags = new PdcVillagerFlags();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void restocksAStaleVillagerAndReStampsIt() {
        flags.stampRestock(villager, NOW.minusSeconds(120)); // older than the 60s interval
        RecordingScheduler scheduler = new RecordingScheduler();

        sweep(scheduler, true).tick();

        assertThat(villager.getRecipes())
                .allSatisfy(recipe -> assertThat(recipe.getUses()).isZero());
        assertThat(flags.lastRestock(villager)).isEqualTo(NOW);
        assertThat(scheduler.regionHops).isEqualTo(1);
    }

    @Test
    void restocksAVillagerThatNeverRestocked() {
        // No stamp: the last restock reads as the epoch, which is always due.
        sweep(new RecordingScheduler(), true).tick();

        assertThat(villager.getRecipes())
                .allSatisfy(recipe -> assertThat(recipe.getUses()).isZero());
        assertThat(flags.lastRestock(villager)).isEqualTo(NOW);
    }

    @Test
    void leavesAFreshlyRestockedVillagerAlone() {
        flags.stampRestock(villager, NOW.minusSeconds(30)); // younger than the interval

        sweep(new RecordingScheduler(), true).tick();

        assertThat(villager.getRecipes().get(0).getUses()).isEqualTo(5);
    }

    @Test
    void aDisabledSweepSchedulesNothing() {
        RecordingScheduler scheduler = new RecordingScheduler();

        assertThatCode(() -> sweep(scheduler, false).start().close()).doesNotThrowAnyException();

        assertThat(scheduler.repeatScheduled).isFalse();
    }

    private VillagerRestockSweep sweep(Scheduler scheduler, boolean enabled) {
        return new VillagerRestockSweep(
                server,
                scheduler,
                flags,
                new RestockPolicy(INTERVAL),
                INTERVAL,
                Clock.fixed(NOW, ZoneOffset.UTC),
                enabled);
    }

    private static MerchantRecipe recipe(int uses) {
        return new MerchantRecipe(new ItemStack(Material.EMERALD), uses, 5, true);
    }

    /** Runs the per-region restock hop inline and records that a repeating task was (or was not) scheduled. */
    private static final class RecordingScheduler implements Scheduler {
        private int regionHops;
        private boolean repeatScheduled;

        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            regionHops++;
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

        @Override
        public AutoCloseable repeatGlobal(Runnable task, Duration initialDelay, Duration period) {
            repeatScheduled = true;
            return () -> {};
        }
    }
}

package com.uxplima.uxmessentials.itemworld.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;

import com.uxplima.uxmessentials.itemworld.domain.PurgeSelection;
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
 * Pins how {@link BukkitEntityPurger#purgeWorld} threads a world-wide sweep under Folia. A world's entities span
 * every region, so the purger must snapshot the roster on the <strong>global</strong> region thread (the one
 * thread that can read every region coherently) and then hop <em>each</em> removal onto the region that owns that
 * entity's location: never enumerating or removing across regions from one actor's thread. A recording
 * {@link Scheduler} freezes that shape: exactly one {@code onGlobal} snapshot, then one {@code onRegion} hop per
 * matching entity, the count aggregated across the hops, and the completion callback fired once with the total.
 *
 * <p>The sweep's invariants are checked here too, players and tamed pets are never removed, so a regression that
 * widened the match or dropped the per-region hop fails a concrete assertion rather than only surfacing on a live
 * Folia server.
 */
class BukkitEntityPurgerWorldDispatchTest {

    private ServerMock server;
    private World world;
    private PlayerMock actor;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
        actor = server.addPlayer("Alice");
        actor.teleport(new Location(world, 0, 64, 0));
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void snapshotsOnGlobalThenHopsEachRemovalOntoItsOwningRegion() {
        spawn(EntityType.ZOMBIE, 10);
        spawn(EntityType.ZOMBIE, 20);
        spawn(EntityType.SKELETON, 30);
        RecordingScheduler scheduler = new RecordingScheduler();
        AtomicInteger reported = new AtomicInteger(-1);

        BukkitEntityPurger.purgeWorld(scheduler, actor, PurgeSelection.killAll(""), reported::set);

        // One global snapshot, then one region hop for each of the three removable entities.
        assertThat(scheduler.globalHops).isEqualTo(1);
        assertThat(scheduler.regionHops).isEqualTo(3);
        // The callback fired exactly once carrying the aggregate, and the world is now empty of those entities.
        assertThat(reported.get()).isEqualTo(3);
        assertThat(monsterCount()).isZero();
    }

    @Test
    void neverRemovesThePlayerEvenOnAnAllEntitiesSweep() {
        spawn(EntityType.ZOMBIE, 10);
        RecordingScheduler scheduler = new RecordingScheduler();
        AtomicInteger reported = new AtomicInteger(-1);

        BukkitEntityPurger.purgeWorld(scheduler, actor, PurgeSelection.killAll(""), reported::set);

        assertThat(reported.get()).isEqualTo(1); // the zombie, not the player
        assertThat(actor.isValid()).isTrue();
    }

    @Test
    void anEmptyWorldStillCompletesThroughTheDrainPath() {
        RecordingScheduler scheduler = new RecordingScheduler();
        AtomicInteger reported = new AtomicInteger(-1);

        BukkitEntityPurger.purgeWorld(scheduler, actor, PurgeSelection.killAll("zombie"), reported::set);

        assertThat(scheduler.globalHops).isEqualTo(1);
        assertThat(scheduler.regionHops).isZero();
        assertThat(reported.get()).isZero();
    }

    @Test
    void rejectsARadiusSelection() {
        RecordingScheduler scheduler = new RecordingScheduler();

        assertThatThrownBy(() ->
                        BukkitEntityPurger.purgeWorld(scheduler, actor, PurgeSelection.butcher(8, 64), removed -> {}))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private void spawn(EntityType type, int x) {
        world.spawnEntity(new Location(world, x, 64, 0), type);
    }

    private long monsterCount() {
        return world.getEntities().stream()
                .filter(entity -> entity.getType() == EntityType.ZOMBIE || entity.getType() == EntityType.SKELETON)
                .count();
    }

    /** Records the global snapshot hop and each per-region removal hop, running them inline so the sweep completes. */
    private static final class RecordingScheduler implements Scheduler {
        private int globalHops;
        private int regionHops;

        @Override
        public void onGlobal(Runnable task) {
            globalHops++;
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
    }
}

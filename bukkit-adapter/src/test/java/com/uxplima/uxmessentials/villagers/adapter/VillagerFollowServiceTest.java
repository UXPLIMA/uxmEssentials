package com.uxplima.uxmessentials.villagers.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.villagers.adapter.outbound.PdcVillagerFlags;
import com.uxplima.uxmessentials.villagers.adapter.outbound.VillagerFollowService;
import com.uxplima.uxmessentials.villagers.adapter.outbound.VillagerMover;
import com.uxplima.uxmessentials.villagers.domain.FollowRange;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * MockBukkit coverage of the follow runtime: a first {@code /villager follow} stamps the owner and starts the villager
 * following (a tick then walks it toward its owner), a second stops it, an owner out of range halts the villager rather
 * than chasing it, and a disabled runtime schedules nothing. Movement is captured through a recording {@link
 * VillagerMover} because the live pathfinder API is not implemented by MockBukkit; the scheduler runs every hop inline.
 */
class VillagerFollowServiceTest {

    private static final FollowRange RANGE = new FollowRange(16.0);
    private static final double SPEED = 1.0;

    private ServerMock server;
    private WorldMock world;
    private PlayerMock player;
    private PlayerRef ref;
    private Villager villager;
    private PdcVillagerFlags flags;
    private RecordingMover mover;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin();
        world = server.addSimpleWorld("world");
        player = server.addPlayer("Steve");
        player.teleport(new Location(world, 0, 64, 0));
        ref = BukkitRefs.toRef(player);
        villager = (Villager) world.spawnEntity(new Location(world, 5, 64, 0), EntityType.VILLAGER);
        flags = new PdcVillagerFlags();
        mover = new RecordingMover();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aFollowedVillagerMovesTowardItsOwner() {
        VillagerFollowService service = service(true);

        service.toggle(player, ref, villager);
        assertThat(flags.followOwner(villager)).isEqualTo(player.getUniqueId());
        assertThat(service.isFollowing(villager.getUniqueId())).isTrue();

        service.tick();

        assertThat(mover.moveCount).isEqualTo(1);
        assertThat(mover.stopCount).isZero();
        assertThat(mover.lastTarget)
                .isNotNull()
                .extracting(Location::getX, Location::getZ)
                .containsExactly(
                        player.getLocation().getX(), player.getLocation().getZ());
        assertThat(mover.lastSpeed).isEqualTo(SPEED);
    }

    @Test
    void aSecondToggleStopsTheVillagerFollowing() {
        VillagerFollowService service = service(true);
        service.toggle(player, ref, villager);
        service.tick();

        service.toggle(player, ref, villager);

        assertThat(flags.followOwner(villager)).isNull();
        assertThat(service.isFollowing(villager.getUniqueId())).isFalse();
        assertThat(mover.stopCount).isEqualTo(1);
        int movesBefore = mover.moveCount;
        service.tick(); // no session left, so nothing more moves
        assertThat(mover.moveCount).isEqualTo(movesBefore);
    }

    @Test
    void anOwnerOutOfRangeHaltsTheVillager() {
        player.teleport(new Location(world, 100, 64, 100)); // well beyond the 16-block range
        VillagerFollowService service = service(true);
        service.toggle(player, ref, villager);

        service.tick();

        assertThat(mover.moveCount).isZero();
        assertThat(mover.stopCount).isEqualTo(1);
    }

    @Test
    void aDisabledRuntimeSchedulesNothing() {
        RecordingScheduler scheduler = new RecordingScheduler();
        VillagerFollowService service =
                new VillagerFollowService(server, scheduler, flags, mover, RANGE, SPEED, new KeyMessages(), false);

        assertThatCode(() -> service.start().close()).doesNotThrowAnyException();

        assertThat(scheduler.repeatScheduled).isFalse();
    }

    private VillagerFollowService service(boolean enabled) {
        return new VillagerFollowService(
                server, new RecordingScheduler(), flags, mover, RANGE, SPEED, new KeyMessages(), enabled);
    }

    /** Captures the pathfinder calls the service would make, standing in for the unimplemented live pathfinder. */
    private static final class RecordingMover implements VillagerMover {
        private @Nullable Location lastTarget;
        private double lastSpeed;
        private int moveCount;
        private int stopCount;

        @Override
        public void moveTo(Villager villager, Location target, double speed) {
            this.lastTarget = target;
            this.lastSpeed = speed;
            this.moveCount++;
        }

        @Override
        public void stop(Villager villager) {
            this.stopCount++;
        }
    }

    /** Resolves each key to its own id: the follow feedback path the tests do not assert text on. */
    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    /** Runs every scheduled hop inline and records whether a repeating task was scheduled. */
    private static final class RecordingScheduler implements Scheduler {
        private boolean repeatScheduled;

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

        @Override
        public AutoCloseable repeatGlobal(Runnable task, Duration initialDelay, Duration period) {
            repeatScheduled = true;
            return () -> {};
        }
    }
}

package com.uxplima.uxmessentials.vanish.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.shared.adapter.outbound.bus.BusPublisher;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.network.NetworkMessage;
import com.uxplima.uxmessentials.shared.network.VanishStateChanged;
import com.uxplima.uxmessentials.vanish.application.VanishSync;
import com.uxplima.uxmessentials.vanish.application.port.VanishStore;
import com.uxplima.uxmessentials.vanish.application.port.VanishView;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;
import com.uxplima.uxmessentials.vanish.domain.VanishState;
import org.junit.jupiter.api.Test;

/**
 * The vanish context's cross-server seam without a live bus: {@link BusVanishBus} translates a local {@link VanishSync}
 * to a {@link VanishStateChanged} frame and back, and {@link VanishNetworkApplier} applies an inbound frame to the
 * network-wide {@link InMemoryNetworkVanishStore}, marshalling any player work onto the target's region through the
 * injected {@link Scheduler}. A recording publisher and fakes stand in for the shared bus and Bukkit.
 */
class VanishCrossServerTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

    @Test
    void publishStampsTheOriginAndCarriesTheState() {
        RecordingPublisher publisher = new RecordingPublisher("survival-1");
        BusVanishBus bus = new BusVanishBus(publisher, true);

        bus.publish(VanishSync.vanished(new PlayerRef(PLAYER, "Alice"), VanishLevel.of(3)));

        assertThat(publisher.sent).hasSize(1);
        VanishStateChanged frame = (VanishStateChanged) publisher.sent.get(0);
        assertThat(frame.originServer()).isEqualTo("survival-1");
        assertThat(frame.player()).isEqualTo(PLAYER);
        assertThat(frame.playerName()).isEqualTo("Alice");
        assertThat(frame.vanished()).isTrue();
        assertThat(frame.level()).isEqualTo(3);
    }

    @Test
    void anInboundVanishFrameAppliesToTheNetworkStore() {
        InMemoryNetworkVanishStore network = new InMemoryNetworkVanishStore();
        FakeStore store = new FakeStore();
        RecordingView view = new RecordingView();
        BusVanishBus bus = new BusVanishBus(new RecordingPublisher("survival-2"), true);
        bus.subscribe(new VanishNetworkApplier(network, store, view, new InlineScheduler()));

        bus.onFrame(new VanishStateChanged("survival-1", PLAYER, "Alice", true, 2));

        assertThat(network.levelOf(PLAYER)).contains(VanishLevel.of(2));
        assertThat(network.nameOf(PLAYER)).contains("Alice");
        // The player happens to be online here (the inline scheduler runs the region task), so the local view
        // reconciles.
        assertThat(store.isVanished(PLAYER)).isTrue();
        assertThat(view.hidden).containsExactly(VanishLevel.of(2));
    }

    @Test
    void anInboundRevealFrameDropsThePlayerFromTheNetworkStore() {
        InMemoryNetworkVanishStore network = new InMemoryNetworkVanishStore();
        FakeStore store = new FakeStore();
        RecordingView view = new RecordingView();
        VanishNetworkApplier applier = new VanishNetworkApplier(network, store, view, new InlineScheduler());
        applier.accept(new VanishSync(PLAYER, "Alice", true, VanishLevel.of(2)));

        applier.accept(new VanishSync(PLAYER, "Alice", false, VanishLevel.DEFAULT));

        assertThat(network.levelOf(PLAYER)).isEmpty();
        assertThat(store.isVanished(PLAYER)).isFalse();
        assertThat(view.revealed).containsExactly(PLAYER);
    }

    @Test
    void networkStateIsTrackedEvenWhenTheTargetIsNotOnlineHere() {
        InMemoryNetworkVanishStore network = new InMemoryNetworkVanishStore();
        FakeStore store = new FakeStore();
        RecordingView view = new RecordingView();
        // A scheduler that never runs the entity task models a player offline on this backend.
        VanishNetworkApplier applier = new VanishNetworkApplier(network, store, view, new DroppingScheduler());

        applier.accept(new VanishSync(PLAYER, "Alice", true, VanishLevel.of(2)));

        assertThat(network.levelOf(PLAYER)).contains(VanishLevel.of(2)); // the network view still records it
        assertThat(store.isVanished(PLAYER)).isFalse(); // but no local player is touched
        assertThat(view.hidden).isEmpty();
    }

    private static final class RecordingPublisher implements BusPublisher {
        private final String serverId;
        private final List<NetworkMessage> sent = new ArrayList<>();

        RecordingPublisher(String serverId) {
            this.serverId = serverId;
        }

        @Override
        public void publish(NetworkMessage message) {
            sent.add(message);
        }

        @Override
        public String serverId() {
            return serverId;
        }
    }

    private static final class RecordingView implements VanishView {
        private final List<VanishLevel> hidden = new ArrayList<>();
        private final List<UUID> revealed = new ArrayList<>();

        @Override
        public void hide(PlayerRef who, VanishLevel level) {
            hidden.add(level);
        }

        @Override
        public void reveal(PlayerRef who) {
            revealed.add(who.uuid());
        }
    }

    private static final class FakeStore implements VanishStore {
        private final ConcurrentHashMap<UUID, VanishLevel> vanished = new ConcurrentHashMap<>();

        @Override
        public boolean isVanished(UUID who) {
            return vanished.containsKey(who);
        }

        @Override
        public void vanish(UUID who, VanishLevel level) {
            vanished.put(who, level);
        }

        @Override
        public void reveal(UUID who) {
            vanished.remove(who);
        }

        @Override
        public Optional<VanishLevel> levelOf(UUID who) {
            return Optional.ofNullable(vanished.get(who));
        }

        @Override
        public Set<UUID> vanished() {
            return Set.copyOf(vanished.keySet());
        }

        @Override
        public VanishState snapshot() {
            return new VanishState(vanished);
        }
    }

    /** Runs the entity task inline: models the target being online on this backend. */
    private static final class InlineScheduler implements Scheduler {
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

    /** Drops the entity task: models the target being offline here, so the Folia entity scheduler no-ops. */
    private static final class DroppingScheduler implements Scheduler {
        @Override
        public void onGlobal(Runnable task) {}

        @Override
        public void onRegion(Position position, Runnable task) {}

        @Override
        public void onEntity(PlayerRef player, Runnable task) {}

        @Override
        public void async(Runnable task) {}

        @Override
        public void asyncAfter(Duration delay, Runnable task) {}
    }
}

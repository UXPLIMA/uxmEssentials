package com.uxplima.uxmessentials.teleport.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Warmups;
import com.uxplima.uxmessentials.shared.application.port.WorldLookup;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.application.port.ArrivalGrace;
import com.uxplima.uxmessentials.teleport.application.port.JailGate;
import com.uxplima.uxmessentials.teleport.application.port.SafeLocationQueue;
import com.uxplima.uxmessentials.teleport.application.port.TeleportExecutor;
import com.uxplima.uxmessentials.teleport.application.port.TeleportFee;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.RtpSafeLocation;
import com.uxplima.uxmessentials.teleport.domain.TeleportError;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * The routing contract behind {@code /rtp <player>} (staff force) and {@code /rtp <world>}: both reach {@link
 * ResolveRtp#background(PlayerRef, WorldRef)}, so the player passed is the one served and teleported, and the world
 * passed is the one polled. A staff force hands the <em>target</em> (never the issuer) and the target's world; a
 * named-world RTP hands the sender and that world. Pinned in pure {@code :core} over a per-world fake queue and a
 * real {@link TeleportEngine} with recording fakes.
 */
class ResolveRtpTest {

    private static final WorldRef HOME = new WorldRef(UUID.randomUUID(), "world");
    private static final WorldRef RESOURCE = new WorldRef(UUID.randomUUID(), "resource");
    private static final PlayerRef ISSUER = new PlayerRef(UUID.randomUUID(), "Staff");
    private static final PlayerRef TARGET = new PlayerRef(UUID.randomUUID(), "Victim");
    private static final RtpSafeLocation HOME_LOC =
            new RtpSafeLocation(Position.of(HOME, 100, 70, 200), 200.0, Instant.EPOCH);
    private static final RtpSafeLocation RESOURCE_LOC =
            new RtpSafeLocation(Position.of(RESOURCE, 300, 64, 400), 500.0, Instant.EPOCH);

    @Test
    void forcingAPlayerRoutesTheTargetThroughTheResolver() {
        Fixture f = new Fixture();
        f.queue.put(HOME, HOME_LOC);

        // The command hands the TARGET and the target's own world: the issuer is never passed to the resolver.
        Result<Unit, TeleportError> result = f.resolveRtp.background(TARGET, HOME);

        assertThat(result.isOk()).isTrue();
        assertThat(f.queue.polled).isEqualTo(HOME); // the target's world was polled
        assertThat(f.executor.lastWho).isEqualTo(TARGET); // the target is the one teleported
        assertThat(f.executor.lastWho).isNotEqualTo(ISSUER);
    }

    @Test
    void rtpToANamedWorldResolvesThatWorldAndTeleportsThere() {
        Fixture f = new Fixture();
        f.queue.put(RESOURCE, RESOURCE_LOC);

        Result<Unit, TeleportError> result = f.resolveRtp.background(ISSUER, RESOURCE);

        assertThat(result.isOk()).isTrue();
        assertThat(f.queue.polled).isEqualTo(RESOURCE); // the named world, not the sender's current one
        assertThat(f.executor.lastWho).isEqualTo(ISSUER);
        Destination served = java.util.Objects.requireNonNull(f.executor.lastDestination);
        assertThat(served.position().world()).isEqualTo(RESOURCE);
    }

    private static final class Fixture {
        final PerWorldQueue queue = new PerWorldQueue();
        final RecordingExecutor executor = new RecordingExecutor();
        final ResolveRtp resolveRtp;

        Fixture() {
            TeleportSettings settings = new TeleportSettings(new EmptyConfig());
            Notifier notifier = new Notifier(new KeyMessages(), new NoopSink());
            TeleportEngine engine = new TeleportEngine(
                    new NoopCooldowns(),
                    new ImmediateWarmups(),
                    executor,
                    notifier,
                    new NoopEvents(),
                    settings,
                    JailGate.NEVER,
                    TeleportFee.FREE,
                    ArrivalGrace.NONE);
            this.resolveRtp = new ResolveRtp(queue, new NoRedirectWorlds(), engine, notifier, settings);
        }
    }

    /** Serves a preset location per world and records which world was last polled. */
    private static final class PerWorldQueue implements SafeLocationQueue {
        private final Map<UUID, RtpSafeLocation> byWorld = new HashMap<>();
        private @Nullable WorldRef polled;

        void put(WorldRef world, RtpSafeLocation location) {
            byWorld.put(world.uid(), location);
        }

        @Override
        public Optional<RtpSafeLocation> poll(WorldRef world) {
            polled = world;
            return Optional.ofNullable(byWorld.get(world.uid()));
        }

        @Override
        public Optional<RtpSafeLocation> urgentSearch(WorldRef world) {
            return poll(world);
        }

        @Override
        public boolean hasQueue(WorldRef world) {
            return byWorld.containsKey(world.uid());
        }

        @Override
        public void requestRefill(WorldRef world) {}
    }

    private static final class RecordingExecutor implements TeleportExecutor {
        private @Nullable PlayerRef lastWho;
        private @Nullable Destination lastDestination;

        @Override
        public void teleport(PlayerRef who, Destination destination, TeleportKind kind) {
            record(who, destination);
        }

        @Override
        public void teleport(PlayerRef who, Destination destination, TeleportKind kind, Runnable onLanded) {
            record(who, destination);
            onLanded.run();
        }

        private void record(PlayerRef who, Destination destination) {
            this.lastWho = who;
            this.lastDestination = destination;
        }
    }

    private static final class NoopCooldowns implements Cooldowns {
        @Override
        public Result<Unit, Duration> check(PlayerRef who, CooldownKind kind) {
            return Result.ok();
        }

        @Override
        public void stamp(PlayerRef who, CooldownKind kind) {}

        @Override
        public Result<Unit, Duration> checkLabel(PlayerRef who, String label) {
            return Result.ok();
        }

        @Override
        public void stampLabel(PlayerRef who, String label) {}
    }

    private static final class ImmediateWarmups implements Warmups {
        @Override
        public WarmupHandle begin(PlayerRef who, WarmupKind kind, Runnable onComplete, Runnable onCancel) {
            onComplete.run();
            return new Warmups.CompletedWarmup(who);
        }
    }

    private static final class NoopEvents implements DomainEventPublisher {
        @Override
        public void publish(DomainEvent event) {}
    }

    /** Never redirects: a world with a queue resolves to itself, matching a per-world serve. */
    private static final class NoRedirectWorlds implements WorldLookup {
        @Override
        public Optional<WorldRef> findByName(String name) {
            return Optional.empty();
        }

        @Override
        public Optional<WorldRef> findByUid(UUID uid) {
            return Optional.empty();
        }
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class NoopSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
    }

    private record EmptyConfig() implements ConfigStore {
        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return fallback;
        }

        @Override
        public List<String> getStringList(String path, List<String> fallback) {
            return fallback;
        }
    }
}

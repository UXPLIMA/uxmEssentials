package com.uxplima.uxmessentials.teleport.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Warmups;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.application.port.BackLocationStore;
import com.uxplima.uxmessentials.teleport.application.port.JailGate;
import com.uxplima.uxmessentials.teleport.application.port.TeleportExecutor;
import com.uxplima.uxmessentials.teleport.domain.BackLocation;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.TeleportError;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code /deathback}: return only to a death capture. With no capture, or a capture whose cause is a teleport
 * rather than a death, the command declines (DEATHBACK_NONE) and never hops. With a death capture it honours
 * the death-back gate exactly like {@code /back}: denied without it, returned with it. The general
 * {@code /back} still returns to whatever the latest capture is.
 */
class CaptureBackTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final PlayerRef WHO = new PlayerRef(UUID.randomUUID(), "Mover");
    private static final Position DEATH_POINT = Position.of(WORLD, 1, 64, 1);
    private static final Position TELEPORT_POINT = Position.of(WORLD, 2, 64, 2);
    private static final Instant T0 = Instant.parse("2026-06-03T00:00:00Z");

    private FakeBackStore store;
    private RecordingExecutor executor;
    private CapturingNotifier notifier;
    private CaptureBack captureBack;

    @BeforeEach
    void setUp() {
        store = new FakeBackStore();
        executor = new RecordingExecutor();
        notifier = new CapturingNotifier();
        TeleportEngine engine = new TeleportEngine(
                new NoopCooldowns(),
                new ImmediateWarmups(),
                executor,
                notifier.notifier(),
                new NoopEvents(),
                new TeleportSettings(new NoopConfig()),
                JailGate.NEVER);
        captureBack = new CaptureBack(store, engine, notifier.notifier(), new NoopEvents(), clock());
    }

    @Test
    void deathBackWithNoCaptureDeclines() {
        captureBack.backToDeath(WHO, true, 0L);

        assertThat(notifier.keys).containsExactly("teleport.deathback.none");
        assertThat(executor.hops).isZero();
    }

    @Test
    void deathBackIgnoresATeleportCapture() {
        store.capture(WHO, BackLocation.beforeTeleport(TELEPORT_POINT, T0));

        captureBack.backToDeath(WHO, true, 0L);

        assertThat(notifier.keys).containsExactly("teleport.deathback.none");
        assertThat(executor.hops).isZero();
    }

    @Test
    void deathBackToADeathCaptureIsDeniedWithoutTheGate() {
        store.capture(WHO, BackLocation.atDeath(DEATH_POINT, T0));

        captureBack.backToDeath(WHO, false, 0L);

        assertThat(notifier.keys).containsExactly("teleport.back.death-denied");
        assertThat(executor.hops).isZero();
    }

    @Test
    void deathBackToADeathCaptureReturnsWithTheGate() {
        store.capture(WHO, BackLocation.atDeath(DEATH_POINT, T0));

        Result<Unit, TeleportError> result = captureBack.backToDeath(WHO, true, 0L);

        assertThat(result.isOk()).isTrue();
        assertThat(executor.hops).isEqualTo(1);
        assertThat(notifier.keys).contains("teleport.back.returned");
    }

    @Test
    void backToADeathPointInsideTheDelayWindowIsRejected() {
        store.capture(WHO, BackLocation.atDeath(DEATH_POINT, T0));
        // Two seconds after the death, with a ten-second post-death delay configured.
        CaptureBack delayed = captureBackAt(T0.plusSeconds(2));

        Result<Unit, TeleportError> result = delayed.back(WHO, true, 10L);

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(TeleportError.BACK_ON_DEATH_DELAY);
        assertThat(executor.hops).isZero();
        assertThat(notifier.keys).contains("teleport.back.death-delay");
    }

    @Test
    void backToADeathPointAfterTheDelayWindowReturns() {
        store.capture(WHO, BackLocation.atDeath(DEATH_POINT, T0));
        // Eleven seconds after the death, past the ten-second post-death delay.
        CaptureBack delayed = captureBackAt(T0.plusSeconds(11));

        Result<Unit, TeleportError> result = delayed.back(WHO, true, 10L);

        assertThat(result.isOk()).isTrue();
        assertThat(executor.hops).isEqualTo(1);
        assertThat(notifier.keys).contains("teleport.back.returned");
    }

    @Test
    void backToATeleportPointIgnoresTheDeathDelay() {
        store.capture(WHO, BackLocation.beforeTeleport(TELEPORT_POINT, T0));
        // A teleport capture is never delay-gated, even right after it was taken.
        CaptureBack delayed = captureBackAt(T0.plusSeconds(1));

        Result<Unit, TeleportError> result = delayed.back(WHO, true, 10L);

        assertThat(result.isOk()).isTrue();
        assertThat(executor.hops).isEqualTo(1);
    }

    /** A {@link CaptureBack} whose clock reads {@code now}, sharing this test's store and executor. */
    private CaptureBack captureBackAt(Instant now) {
        TeleportEngine engine = new TeleportEngine(
                new NoopCooldowns(),
                new ImmediateWarmups(),
                executor,
                notifier.notifier(),
                new NoopEvents(),
                new TeleportSettings(new NoopConfig()),
                JailGate.NEVER);
        return new CaptureBack(store, engine, notifier.notifier(), new NoopEvents(), Clock.fixed(now, ZoneOffset.UTC));
    }

    private static Clock clock() {
        return Clock.fixed(T0, ZoneOffset.UTC);
    }

    private static final class FakeBackStore implements BackLocationStore {
        private final Map<UUID, BackLocation> current = new java.util.HashMap<>();

        @Override
        public void capture(PlayerRef who, BackLocation location) {
            current.put(who.uuid(), location);
        }

        @Override
        public Optional<BackLocation> current(PlayerRef who) {
            return Optional.ofNullable(current.get(who.uuid()));
        }

        @Override
        public void clear(PlayerRef who) {
            current.remove(who.uuid());
        }
    }

    private static final class RecordingExecutor implements TeleportExecutor {
        int hops;

        @Override
        public void teleport(PlayerRef who, Destination destination, TeleportKind kind) {
            hops++;
        }
    }

    private static final class CapturingNotifier {
        private final List<String> keys = new ArrayList<>();

        Notifier notifier() {
            return new Notifier(new RecordingMessages(keys), new NoopSink());
        }
    }

    private static final class RecordingMessages implements Messages {
        private final List<String> keys;

        RecordingMessages(List<String> keys) {
            this.keys = keys;
        }

        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            keys.add(key.key());
            return key.key();
        }
    }

    private static final class NoopSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
    }

    private static final class NoopEvents implements DomainEventPublisher {
        @Override
        public void publish(DomainEvent event) {}
    }

    private static final class NoopCooldowns implements Cooldowns {
        @Override
        public Result<Unit, java.time.Duration> check(PlayerRef who, CooldownKind kind) {
            return Result.ok();
        }

        @Override
        public void stamp(PlayerRef who, CooldownKind kind) {}

        @Override
        public Result<Unit, java.time.Duration> checkLabel(PlayerRef who, String label) {
            return Result.ok();
        }

        @Override
        public void stampLabel(PlayerRef who, String label) {}
    }

    private static final class ImmediateWarmups implements Warmups {
        @Override
        public WarmupHandle begin(PlayerRef who, WarmupKind kind, Runnable onComplete, Runnable onCancel) {
            onComplete.run();
            return new CompletedWarmup(who);
        }
    }

    private static final class NoopConfig implements com.uxplima.uxmessentials.shared.application.port.ConfigStore {
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
    }
}

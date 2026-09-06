package com.uxplima.uxmessentials.moderation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.JailState;
import com.uxplima.uxmessentials.moderation.fakes.FakeModerationRepository;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Warmups;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.teleport.application.TeleportEngine;
import com.uxplima.uxmessentials.teleport.application.TeleportSettings;
import com.uxplima.uxmessentials.teleport.application.port.JailGate;
import com.uxplima.uxmessentials.teleport.application.port.TeleportExecutor;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.TeleportError;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import org.junit.jupiter.api.Test;

/**
 * The jail → teleport cross-context seam: the real {@link com.uxplima.uxmessentials.moderation.application.RepositoryJailGate}
 * the moderation context provides, bound into the real {@link TeleportEngine}, denies a jailed player's
 * self-teleport with {@code JAILED} <em>before</em> the cooldown gate or the warmup, so no hop is ever
 * issued. An unjailed player flows through normally. This is what {@code /home}, {@code /warp}, {@code /spawn},
 * {@code /back} and {@code /rtp} all funnel through.
 */
class JailBlocksTeleportTest {

    private static final Instant T0 = Instant.parse("2026-05-31T00:00:00Z");
    private static final PlayerRef MOVER = new PlayerRef(UUID.randomUUID(), "jailed");

    @Test
    void aJailedPlayerCannotSelfTeleport() {
        FakeModerationRepository repository = new FakeModerationRepository();
        repository.saveJail(MOVER, JailState.permanent("cells", Issuer.console("op"), Optional.empty(), T0));
        JailGate gate = new com.uxplima.uxmessentials.moderation.application.RepositoryJailGate(
                repository, Clock.fixed(T0, ZoneOffset.UTC));
        RecordingExecutor executor = new RecordingExecutor();
        TeleportEngine engine = engine(gate, executor);

        Result<Unit, TeleportError> result = engine.launch(MOVER, destination(), TeleportKind.HOME);

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(TeleportError.JAILED);
        assertThat(executor.hops).isZero();
    }

    @Test
    void anUnjailedPlayerTeleportsNormally() {
        FakeModerationRepository repository = new FakeModerationRepository();
        JailGate gate = new com.uxplima.uxmessentials.moderation.application.RepositoryJailGate(
                repository, Clock.fixed(T0, ZoneOffset.UTC));
        RecordingExecutor executor = new RecordingExecutor();
        TeleportEngine engine = engine(gate, executor);

        Result<Unit, TeleportError> result = engine.launch(MOVER, destination(), TeleportKind.HOME);

        assertThat(result.isOk()).isTrue();
        // The immediate warmup fires synchronously, so the hop was issued.
        assertThat(executor.hops).isEqualTo(1);
    }

    private static TeleportEngine engine(JailGate gate, RecordingExecutor executor) {
        return new TeleportEngine(
                new NoCooldowns(),
                new ImmediateWarmups(),
                executor,
                new Notifier(new KeyMessages(), new NoopSink()),
                new NoopEvents(),
                new TeleportSettings(new EmptyConfig()),
                gate);
    }

    private static Destination destination() {
        com.uxplima.uxmessentials.shared.domain.WorldRef world =
                new com.uxplima.uxmessentials.shared.domain.WorldRef(UUID.randomUUID(), "world");
        return Destination.at(new com.uxplima.uxmessentials.shared.domain.Position(world, 0, 64, 0, 0f, 0f));
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
    }

    private static final class NoCooldowns implements Cooldowns {
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
            return new WarmupHandle() {
                @Override
                public void cancel() {}

                @Override
                public boolean isComplete() {
                    return true;
                }

                @Override
                public boolean isCancelled() {
                    return false;
                }
            };
        }
    }

    private static final class RecordingExecutor implements TeleportExecutor {
        int hops;

        @Override
        public void teleport(PlayerRef who, Destination destination, TeleportKind kind) {
            hops++;
        }
    }

    private static final class NoopEvents implements DomainEventPublisher {
        @Override
        public void publish(DomainEvent event) {}
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
}

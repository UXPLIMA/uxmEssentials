package com.uxplima.uxmessentials.teleport.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.DomainGate;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Warmups;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.DomainProposal;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.application.port.ArrivalGrace;
import com.uxplima.uxmessentials.teleport.application.port.TeleportExecutor;
import com.uxplima.uxmessentials.teleport.application.port.TeleportFee;
import com.uxplima.uxmessentials.teleport.domain.CooldownStartPhase;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.TeleportError;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import com.uxplima.uxmessentials.teleport.domain.event.PlayerTeleporting;
import org.junit.jupiter.api.Test;

/**
 * The cooldown/warmup machinery rules: a launch gated by an active cooldown never reaches the warmup; on
 * the {@code teleport} start phase the cooldown is stamped only after the warmup completes (so a
 * move-cancelled teleport never burns it); and a denied/cancelled tpa under the {@code accept} phase only
 * stamps when the engine is told the phase fired. The ports are hand-rolled fakes, no Bukkit, no
 * Mockito, so the rules are pinned in pure {@code :core}.
 */
class TeleportEngineTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final PlayerRef MOVER = new PlayerRef(UUID.randomUUID(), "Mover");
    private static final Destination DEST = Destination.at(Position.of(WORLD, 0, 64, 0));

    @Test
    void anActiveCooldownGateBlocksTheWarmupAndTeleport() {
        FakeCooldowns cooldowns = new FakeCooldowns(Duration.ofSeconds(7));
        ImmediateWarmups warmups = new ImmediateWarmups();
        RecordingExecutor executor = new RecordingExecutor();
        TeleportEngine engine = engine(cooldowns, warmups, executor, CooldownStartPhase.TELEPORT);

        Result<Unit, TeleportError> result = engine.launch(MOVER, DEST, TeleportKind.SPAWN);

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(TeleportError.ON_COOLDOWN);
        assertThat(warmups.begun).isZero();
        assertThat(executor.hops).isZero();
        assertThat(cooldowns.stamps).isZero();
    }

    @Test
    void teleportPhaseStampsOnlyAfterTheWarmupCompletes() {
        FakeCooldowns cooldowns = new FakeCooldowns(null);
        ImmediateWarmups warmups = new ImmediateWarmups(); // completes synchronously
        RecordingExecutor executor = new RecordingExecutor();
        TeleportEngine engine = engine(cooldowns, warmups, executor, CooldownStartPhase.TELEPORT);

        engine.launch(MOVER, DEST, TeleportKind.SPAWN);

        assertThat(executor.hops).isEqualTo(1);
        assertThat(cooldowns.stamps).isEqualTo(1); // stamped after the hop, under the teleport phase
    }

    @Test
    void aMoveCancelledWarmupNeverStampsTheCooldown() {
        FakeCooldowns cooldowns = new FakeCooldowns(null);
        CancellingWarmups warmups = new CancellingWarmups(); // fires onCancel, never onComplete
        RecordingExecutor executor = new RecordingExecutor();
        TeleportEngine engine = engine(cooldowns, warmups, executor, CooldownStartPhase.TELEPORT);

        engine.launch(MOVER, DEST, TeleportKind.SPAWN);

        assertThat(executor.hops).isZero();
        assertThat(cooldowns.stamps).isZero(); // cancelled before completion: cooldown untouched
    }

    @Test
    void stampForPhaseOnlyStampsWhenTheConfiguredPhaseMatches() {
        FakeCooldowns cooldowns = new FakeCooldowns(null);
        TeleportEngine accept =
                engine(cooldowns, new ImmediateWarmups(), new RecordingExecutor(), CooldownStartPhase.ACCEPT);
        TeleportEngine teleport =
                engine(cooldowns, new ImmediateWarmups(), new RecordingExecutor(), CooldownStartPhase.TELEPORT);

        teleport.stampForPhase(MOVER, CooldownStartPhase.ACCEPT); // configured TELEPORT, no stamp
        assertThat(cooldowns.stamps).isZero();

        accept.stampForPhase(MOVER, CooldownStartPhase.ACCEPT); // configured ACCEPT, stamps
        assertThat(cooldowns.stamps).isEqualTo(1);
    }

    @Test
    void aVetoedTeleportNeverBeginsTheWarmupAndNeverHops() {
        FakeCooldowns cooldowns = new FakeCooldowns(null);
        ImmediateWarmups warmups = new ImmediateWarmups();
        RecordingExecutor executor = new RecordingExecutor();
        TeleportEngine engine = engine(cooldowns, warmups, executor, CooldownStartPhase.TELEPORT, proposal -> false);

        Result<Unit, TeleportError> result = engine.launch(MOVER, DEST, TeleportKind.SPAWN);

        assertThat(result.errorOrThrow()).isEqualTo(TeleportError.VETOED);
        assertThat(warmups.begun)
                .as("a refused teleport must not make the player stand still first")
                .isZero();
        assertThat(executor.hops).isZero();
        assertThat(cooldowns.stamps).isZero();
    }

    @Test
    void everyVoluntaryEntryPointPassesThroughTheSameVeto() {
        // The point of gating inside the shared warmup rather than at each command: a plugin that refuses
        // teleports refuses all of them, including the ones added after it was written.
        RecordingGate gate = new RecordingGate();
        TeleportEngine engine =
                engine(new FakeCooldowns(null), new ImmediateWarmups(), new RecordingExecutor(), phaseAny(), gate);

        engine.launch(MOVER, DEST, TeleportKind.HOME);
        engine.beginGatedWarmup(MOVER, DEST, TeleportKind.WARP);
        engine.launchRandom(MOVER, DEST);

        assertThat(gate.kinds).containsExactly(TeleportKind.HOME, TeleportKind.WARP, TeleportKind.RANDOM);
    }

    @Test
    void aRespawnLandingIsNotSomethingAPluginCanRefuse() {
        // arriveRandomImmediately serves respawn and first join. There is nowhere to leave the player if it is
        // refused, so it deliberately bypasses the veto rather than offering one that cannot be honoured.
        RecordingGate gate = new RecordingGate();
        RecordingExecutor executor = new RecordingExecutor();
        TeleportEngine engine = engine(new FakeCooldowns(null), new ImmediateWarmups(), executor, phaseAny(), gate);

        engine.arriveRandomImmediately(MOVER, DEST);

        assertThat(gate.kinds).isEmpty();
        assertThat(executor.hops).isEqualTo(1);
    }

    private static CooldownStartPhase phaseAny() {
        return CooldownStartPhase.TELEPORT;
    }

    private static TeleportEngine engine(
            Cooldowns cooldowns, Warmups warmups, TeleportExecutor executor, CooldownStartPhase phase) {
        return engine(cooldowns, warmups, executor, phase, DomainGate.allowAll());
    }

    private static TeleportEngine engine(
            Cooldowns cooldowns,
            Warmups warmups,
            TeleportExecutor executor,
            CooldownStartPhase phase,
            DomainGate gate) {
        return new TeleportEngine(
                cooldowns,
                warmups,
                executor,
                new Notifier(new NoopMessages(), new NoopSink()),
                new NoopEvents(),
                new TeleportSettings(new PhaseConfig(phase)),
                com.uxplima.uxmessentials.teleport.application.port.JailGate.NEVER,
                com.uxplima.uxmessentials.teleport.application.port.CombatGate.NEVER,
                TeleportFee.FREE,
                ArrivalGrace.NONE,
                gate);
    }

    /** Allows everything, but remembers what it was asked about. */
    private static final class RecordingGate implements DomainGate {
        private final List<TeleportKind> kinds = new ArrayList<>();

        @Override
        public boolean allows(DomainProposal proposal) {
            if (proposal instanceof PlayerTeleporting teleporting) {
                kinds.add(teleporting.kind());
            }
            return true;
        }
    }

    /** A minimal {@link ConfigStore} that drives only the cooldown-start-phase the engine reads. */
    private record PhaseConfig(CooldownStartPhase phase) implements ConfigStore {
        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return "cooldown-start-phase".equals(path) ? phase.name().toLowerCase(java.util.Locale.ROOT) : fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return fallback;
        }
    }

    private static final class FakeCooldowns implements Cooldowns {
        private final @org.jspecify.annotations.Nullable Duration remaining;
        int stamps;

        FakeCooldowns(@org.jspecify.annotations.Nullable Duration remaining) {
            this.remaining = remaining;
        }

        @Override
        public Result<Unit, Duration> check(PlayerRef who, CooldownKind kind) {
            return remaining == null ? Result.ok() : Result.err(remaining);
        }

        @Override
        public void stamp(PlayerRef who, CooldownKind kind) {
            stamps++;
        }

        @Override
        public Result<Unit, Duration> checkLabel(PlayerRef who, String label) {
            return Result.ok();
        }

        @Override
        public void stampLabel(PlayerRef who, String label) {}
    }

    private static final class ImmediateWarmups implements Warmups {
        int begun;

        @Override
        public WarmupHandle begin(PlayerRef who, WarmupKind kind, Runnable onComplete, Runnable onCancel) {
            begun++;
            onComplete.run();
            return new CompletedWarmup(who);
        }
    }

    private static final class CancellingWarmups implements Warmups {
        @Override
        public WarmupHandle begin(PlayerRef who, WarmupKind kind, Runnable onComplete, Runnable onCancel) {
            onCancel.run();
            return new CompletedWarmup(who);
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
        final List<DomainEvent> published = new ArrayList<>();

        @Override
        public void publish(DomainEvent event) {
            published.add(event);
        }
    }

    private static final class NoopMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class NoopSink implements MessageSink {
        final AtomicInteger delivered = new AtomicInteger();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            delivered.incrementAndGet();
        }
    }
}

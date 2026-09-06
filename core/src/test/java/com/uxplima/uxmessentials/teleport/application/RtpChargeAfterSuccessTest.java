package com.uxplima.uxmessentials.teleport.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
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
import org.junit.jupiter.api.Test;

/**
 * The charge/cooldown/grace-after-success contract for {@code /rtp}, pinned in pure {@code :core} with
 * hand-rolled fakes (no Bukkit, no Mockito). Cost and cooldown are checked <em>before</em> the pool is ever
 * consulted; the withdrawal, the cooldown stamp, and the arrival grace fire once and only after the teleport
 * has actually landed. A drained pool, a player who cannot pay, and a teleport Paper refuses each leave the
 * balance and cooldown untouched.
 */
class RtpChargeAfterSuccessTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final PlayerRef WHO = new PlayerRef(UUID.randomUUID(), "Traveller");
    private static final BigDecimal COST = new BigDecimal("50");
    private static final RtpSafeLocation LOCATION =
            new RtpSafeLocation(Position.of(WORLD, 120, 70, 240), 240.0, Instant.EPOCH);

    @Test
    void aSuccessfulRtpWithdrawsStampsAndGracesExactlyOnceAfterTheLanding() {
        Fixture f = new Fixture(true, true); // affordable, lands
        f.queue.next = Optional.of(LOCATION);

        Result<Unit, TeleportError> result = f.resolveRtp.background(WHO, WORLD);

        assertThat(result.isOk()).isTrue();
        assertThat(f.queue.polls).isEqualTo(1);
        assertThat(f.fee.charges).isEqualTo(1);
        assertThat(f.cooldowns.stamps).isEqualTo(1);
        assertThat(f.grace.applied).isEqualTo(1);
    }

    @Test
    void aDrainedPoolNeverWithdrawsStampsOrGraces() {
        Fixture f = new Fixture(true, true);
        f.queue.next = Optional.empty(); // pool momentarily empty

        Result<Unit, TeleportError> result = f.resolveRtp.background(WHO, WORLD);

        assertThat(result.errorOrThrow()).isEqualTo(TeleportError.RTP_NO_SAFE_LOCATION);
        assertThat(f.queue.polls).isEqualTo(1); // the serve was attempted
        assertThat(f.fee.charges).isZero();
        assertThat(f.cooldowns.stamps).isZero();
        assertThat(f.grace.applied).isZero();
    }

    @Test
    void aPlayerWhoCannotAffordIsRejectedBeforeAnySearchRuns() {
        Fixture f = new Fixture(false, true); // cannot afford
        f.queue.next = Optional.of(LOCATION);

        Result<Unit, TeleportError> result = f.resolveRtp.background(WHO, WORLD);

        assertThat(result.errorOrThrow()).isEqualTo(TeleportError.RTP_CANT_AFFORD);
        assertThat(f.queue.polls).isZero(); // the pool was never even consulted
        assertThat(f.fee.canAffordChecks).isEqualTo(1);
        assertThat(f.fee.charges).isZero();
        assertThat(f.cooldowns.stamps).isZero();
        assertThat(f.grace.applied).isZero();
    }

    @Test
    void aRefusedTeleportLeavesTheBalanceAndCooldownUntouched() {
        Fixture f = new Fixture(true, false); // affordable, but the hop is refused (never lands)
        f.queue.next = Optional.of(LOCATION);

        Result<Unit, TeleportError> result = f.resolveRtp.background(WHO, WORLD);

        assertThat(result.isOk()).isTrue(); // the launch was dispatched
        assertThat(f.executor.hops).isEqualTo(1); // the executor was asked to teleport
        assertThat(f.fee.charges).isZero(); // ...but nothing landed, so nothing was charged
        assertThat(f.cooldowns.stamps).isZero();
        assertThat(f.grace.applied).isZero();
    }

    @Test
    void firstJoinServesImmediatelyWithGraceButNoWarmupCooldownOrCharge() {
        Fixture f = new Fixture(true, true);
        f.queue.next = Optional.of(LOCATION);

        Result<Unit, TeleportError> result = f.resolveRtp.firstJoin(WHO, WORLD);

        assertThat(result.isOk()).isTrue();
        assertThat(f.queue.polls).isEqualTo(1); // served straight from the pool, never a synchronous search
        assertThat(f.executor.hops).isEqualTo(1); // immediate hop, no warmup gate
        assertThat(f.grace.applied).isEqualTo(1); // the shield still applies on the fresh drop
        assertThat(f.cooldowns.stamps).isZero(); // involuntary: no cooldown burned
        assertThat(f.fee.charges).isZero(); // involuntary, free
    }

    @Test
    void firstJoinOnADrainedPoolLeavesThePlayerWhereTheyJoined() {
        Fixture f = new Fixture(true, true);
        f.queue.next = Optional.empty();

        Result<Unit, TeleportError> result = f.resolveRtp.firstJoin(WHO, WORLD);

        assertThat(result.errorOrThrow()).isEqualTo(TeleportError.RTP_NO_SAFE_LOCATION);
        assertThat(f.queue.polls).isEqualTo(1);
        assertThat(f.executor.hops).isZero();
        assertThat(f.grace.applied).isZero();
    }

    /** Wires a real {@link ResolveRtp} + {@link TeleportEngine} over recording fakes. */
    private static final class Fixture {
        final FakeQueue queue = new FakeQueue();
        final FakeFee fee;
        final FakeCooldowns cooldowns = new FakeCooldowns();
        final FakeGrace grace = new FakeGrace();
        final FakeExecutor executor;
        final ResolveRtp resolveRtp;

        Fixture(boolean affordable, boolean lands) {
            this.fee = new FakeFee(affordable);
            this.executor = new FakeExecutor(lands);
            TeleportSettings settings = new TeleportSettings(new CostConfig(COST));
            Notifier notifier = new Notifier(new NoopMessages(), new NoopSink());
            TeleportEngine engine = new TeleportEngine(
                    cooldowns,
                    new ImmediateWarmups(),
                    executor,
                    notifier,
                    new NoopEvents(),
                    settings,
                    JailGate.NEVER,
                    fee,
                    grace);
            this.resolveRtp = new ResolveRtp(queue, new SingleWorldLookup(), engine, notifier, settings);
        }
    }

    private static final class FakeQueue implements SafeLocationQueue {
        Optional<RtpSafeLocation> next = Optional.empty();
        int polls;

        @Override
        public Optional<RtpSafeLocation> poll(WorldRef world) {
            polls++;
            return next;
        }

        @Override
        public Optional<RtpSafeLocation> urgentSearch(WorldRef world) {
            return poll(world);
        }

        @Override
        public boolean hasQueue(WorldRef world) {
            return true;
        }

        @Override
        public void requestRefill(WorldRef world) {
            // no-op: the refill is fire-and-forget and irrelevant to the charge ordering
        }
    }

    private static final class FakeFee implements TeleportFee {
        private final boolean affordable;
        int canAffordChecks;
        int charges;

        FakeFee(boolean affordable) {
            this.affordable = affordable;
        }

        @Override
        public boolean canAfford(PlayerRef who, BigDecimal amount) {
            canAffordChecks++;
            return affordable;
        }

        @Override
        public void charge(PlayerRef who, BigDecimal amount) {
            charges++;
        }
    }

    private static final class FakeGrace implements ArrivalGrace {
        int applied;

        @Override
        public void applyOnArrival(PlayerRef who) {
            applied++;
        }
    }

    private static final class FakeExecutor implements TeleportExecutor {
        private final boolean lands;
        int hops;

        FakeExecutor(boolean lands) {
            this.lands = lands;
        }

        @Override
        public void teleport(PlayerRef who, Destination destination, TeleportKind kind) {
            hops++;
        }

        @Override
        public void teleport(PlayerRef who, Destination destination, TeleportKind kind, Runnable onLanded) {
            hops++;
            if (lands) {
                onLanded.run();
            }
        }
    }

    private static final class FakeCooldowns implements Cooldowns {
        int stamps;

        @Override
        public Result<Unit, Duration> check(PlayerRef who, CooldownKind kind) {
            return Result.ok();
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
        @Override
        public WarmupHandle begin(PlayerRef who, WarmupKind kind, Runnable onComplete, Runnable onCancel) {
            onComplete.run();
            return new CompletedWarmup(who);
        }
    }

    private static final class SingleWorldLookup implements WorldLookup {
        @Override
        public Optional<WorldRef> findByName(String name) {
            return Optional.of(WORLD);
        }

        @Override
        public Optional<WorldRef> findByUid(UUID uid) {
            return Optional.of(WORLD);
        }
    }

    private record CostConfig(BigDecimal cost) implements ConfigStore {
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
        public double getDouble(String path, double fallback) {
            return "rtp.cost".equals(path) ? cost.doubleValue() : fallback;
        }
    }

    private static final class NoopEvents implements DomainEventPublisher {
        @Override
        public void publish(DomainEvent event) {}
    }

    private static final class NoopMessages implements Messages {
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

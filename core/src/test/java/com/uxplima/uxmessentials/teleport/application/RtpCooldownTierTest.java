package com.uxplima.uxmessentials.teleport.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.application.port.ArrivalGrace;
import com.uxplima.uxmessentials.teleport.application.port.JailGate;
import com.uxplima.uxmessentials.teleport.application.port.TeleportExecutor;
import com.uxplima.uxmessentials.teleport.application.port.TeleportFee;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * The {@code /rtp} cooldown resolves its own numbered tier, {@code uxmessentials.rtp.cooldown.<seconds>}, not the
 * shared {@code tp} tier. Pinned in pure {@code :core} with a capturing {@link Cooldowns} fake: the pre-search gate
 * and the post-arrival stamp both key the {@code rtp} feature, and when the player holds no tier node the config
 * default flows through as the kind's fallback (the shared reducer, tested elsewhere, then refines it). This is the
 * seam the teleport context owns; picking the {@code rtp} feature is what routes resolution to the rtp node.
 */
class RtpCooldownTierTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final PlayerRef WHO = new PlayerRef(UUID.randomUUID(), "Traveller");
    private static final Destination DEST = Destination.at(Position.of(WORLD, 120, 70, 240));

    @Test
    void gateAndStampResolveTheRtpCooldownNode() {
        CapturingCooldowns cooldowns = new CapturingCooldowns();
        TeleportEngine engine = engine(cooldowns, new EmptyConfig());

        engine.gateRandom(WHO);
        engine.launchRandom(WHO, DEST); // ImmediateWarmups + a landing executor → onLanded stamps

        assertThat(cooldowns.checkedNode).isEqualTo("uxmessentials.rtp.cooldown");
        assertThat(cooldowns.stampedNode).isEqualTo("uxmessentials.rtp.cooldown");
        assertThat(cooldowns.checkedFeature).isEqualTo("rtp");
    }

    @Test
    void anAbsentTierFallsBackToTheConfigDefault() {
        CapturingCooldowns cooldowns = new CapturingCooldowns();

        engine(cooldowns, new DefaultCooldownConfig(9)).gateRandom(WHO);

        // No verb override and no tier node: the kind carries the config default-cooldown for the reducer to refine.
        assertThat(cooldowns.checkedDefaultSeconds).isEqualTo(9L);
    }

    private static TeleportEngine engine(Cooldowns cooldowns, ConfigStore config) {
        Notifier notifier = new Notifier(new KeyMessages(), new NoopSink());
        return new TeleportEngine(
                cooldowns,
                new ImmediateWarmups(),
                new LandingExecutor(),
                notifier,
                new NoopEvents(),
                new TeleportSettings(config),
                JailGate.NEVER,
                TeleportFee.FREE,
                ArrivalGrace.NONE);
    }

    /** Records the cooldown kind the gate checked and the stamp wrote so the resolved node/feature can be asserted. */
    private static final class CapturingCooldowns implements Cooldowns {
        private @Nullable String checkedNode;
        private @Nullable String checkedFeature;
        private long checkedDefaultSeconds;
        private @Nullable String stampedNode;

        @Override
        public Result<Unit, Duration> check(PlayerRef who, CooldownKind kind) {
            this.checkedNode = kind.cooldownNode();
            this.checkedFeature = kind.feature();
            this.checkedDefaultSeconds = kind.defaultSeconds();
            return Result.ok();
        }

        @Override
        public void stamp(PlayerRef who, CooldownKind kind) {
            this.stampedNode = kind.cooldownNode();
        }

        @Override
        public Result<Unit, Duration> checkLabel(PlayerRef who, String label) {
            return Result.ok();
        }

        @Override
        public void stampLabel(PlayerRef who, String label) {}
    }

    /** Runs onLanded immediately so the post-arrival stamp fires within the test call. */
    private static final class LandingExecutor implements TeleportExecutor {
        @Override
        public void teleport(PlayerRef who, Destination destination, TeleportKind kind) {}

        @Override
        public void teleport(PlayerRef who, Destination destination, TeleportKind kind, Runnable onLanded) {
            onLanded.run();
        }
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

    /** Returns {@code seconds} for {@code default-cooldown} and the fallback for everything else. */
    private record DefaultCooldownConfig(int seconds) implements ConfigStore {
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
            return "default-cooldown".equals(path) ? seconds : fallback;
        }

        @Override
        public List<String> getStringList(String path, List<String> fallback) {
            return fallback;
        }
    }
}

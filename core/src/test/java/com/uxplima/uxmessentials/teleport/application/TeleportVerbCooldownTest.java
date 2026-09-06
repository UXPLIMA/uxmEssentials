package com.uxplima.uxmessentials.teleport.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
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
import com.uxplima.uxmessentials.teleport.application.port.JailGate;
import com.uxplima.uxmessentials.teleport.application.port.TeleportExecutor;
import com.uxplima.uxmessentials.teleport.domain.CooldownStartPhase;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import org.junit.jupiter.api.Test;

/**
 * Per-verb teleport cooldowns: a verb left at {@code -1} keeps the shared {@code tp} stamp scope and the shared
 * {@code default-cooldown}, so all verbs rate-limit together exactly as before; a verb whose {@code
 * cooldowns.<verb>} override is set gets its own stamp scope ({@code tp.<verb>}) and that override as its
 * fallback default, while the tier-node feature stays {@code tp} so the {@code uxmessentials.tp.cooldown.<n>}
 * permission tiers still apply to every verb. The {@link Cooldowns} fake records the {@link Cooldowns.CooldownKind}
 * it is handed so the resolution is asserted without Bukkit.
 */
class TeleportVerbCooldownTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final PlayerRef MOVER = new PlayerRef(UUID.randomUUID(), "Mover");
    private static final Destination DEST = Destination.at(Position.of(WORLD, 0, 64, 0));

    @Test
    void aVerbWithoutAnOverrideUsesTheSharedScopeAndDefault() {
        RecordingCooldowns cooldowns = new RecordingCooldowns();
        TeleportEngine engine = engine(cooldowns, new VerbConfig(Map.of()));

        engine.launch(MOVER, DEST, TeleportKind.BACK);

        Cooldowns.CooldownKind stamped = cooldowns.lastStamped;
        assertThat(stamped.feature()).isEqualTo("tp");
        assertThat(stamped.stampScope()).isEqualTo("tp"); // shared stamp, back and home rate-limit together
        assertThat(stamped.defaultSeconds()).isEqualTo(5L); // the shared default-cooldown
    }

    @Test
    void aVerbWithAnOverrideGetsItsOwnScopeAndDefault() {
        RecordingCooldowns cooldowns = new RecordingCooldowns();
        TeleportEngine engine = engine(cooldowns, new VerbConfig(Map.of("cooldowns.back", 30)));

        engine.launch(MOVER, DEST, TeleportKind.BACK);

        Cooldowns.CooldownKind stamped = cooldowns.lastStamped;
        assertThat(stamped.feature()).isEqualTo("tp"); // tier node space stays shared so tiers still apply
        assertThat(stamped.stampScope()).isEqualTo("tp.back"); // independent stamp for /back
        assertThat(stamped.defaultSeconds()).isEqualTo(30L); // the per-verb override is /back's fallback
    }

    @Test
    void anOverrideOnOneVerbDoesNotChangeAnother() {
        RecordingCooldowns cooldowns = new RecordingCooldowns();
        TeleportEngine engine = engine(cooldowns, new VerbConfig(Map.of("cooldowns.back", 30)));

        engine.launch(MOVER, DEST, TeleportKind.HOME);

        Cooldowns.CooldownKind stamped = cooldowns.lastStamped;
        assertThat(stamped.stampScope()).isEqualTo("tp"); // /home untouched: shared scope and default
        assertThat(stamped.defaultSeconds()).isEqualTo(5L);
    }

    private static TeleportEngine engine(Cooldowns cooldowns, ConfigStore config) {
        return new TeleportEngine(
                cooldowns,
                new ImmediateWarmups(),
                new RecordingExecutor(),
                new Notifier(new NoopMessages(), new NoopSink()),
                new NoopEvents(),
                new TeleportSettings(config),
                JailGate.NEVER);
    }

    /** A {@link ConfigStore} that answers the cooldown-default and the named per-verb override ints. */
    private record VerbConfig(Map<String, Integer> overrides) implements ConfigStore {
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
            if ("default-cooldown".equals(path)) {
                return 5;
            }
            return overrides.getOrDefault(path, fallback);
        }
    }

    private static final class RecordingCooldowns implements Cooldowns {
        private CooldownKind lastStamped = new CooldownKind("tp", 0, CooldownStartPhase.TELEPORT);

        @Override
        public Result<Unit, Duration> check(PlayerRef who, CooldownKind kind) {
            return Result.ok();
        }

        @Override
        public void stamp(PlayerRef who, CooldownKind kind) {
            lastStamped = kind;
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

    private static final class RecordingExecutor implements TeleportExecutor {
        @Override
        public void teleport(PlayerRef who, Destination destination, TeleportKind kind) {}
    }

    private static final class NoopEvents implements DomainEventPublisher {
        private final List<DomainEvent> published = new ArrayList<>();

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
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
    }
}

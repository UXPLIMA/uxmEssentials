package com.uxplima.uxmessentials.teleport.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
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
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.application.port.JailGate;
import com.uxplima.uxmessentials.teleport.application.port.RequestRegistry;
import com.uxplima.uxmessentials.teleport.application.port.TeleportExecutor;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.RequestId;
import com.uxplima.uxmessentials.teleport.domain.TeleportError;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import com.uxplima.uxmessentials.teleport.domain.TeleportRequest;
import org.junit.jupiter.api.Test;

/**
 * The tpa-resolution verbs must not fail in silence either: {@code /tpaccept}, {@code /tpdeny} and
 * {@code /tpcancel} with nothing pending each return {@link TeleportError#NO_PENDING_REQUEST} <em>and</em>
 * tell the invoking player so via the {@code none-pending} key. Previously the verb just returned an
 * unread error and the player saw nothing. The empty registry is a hand-rolled fake; the engine is wired
 * but never reached on these paths.
 */
class AcceptTeleportTest {

    private static final Instant NOW = Instant.parse("2026-05-30T12:00:00Z");
    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final Position ANCHOR = Position.of(WORLD, 0, 64, 0);
    private static final PlayerRef ALICE = new PlayerRef(UUID.randomUUID(), "Alice");

    @Test
    void acceptWithNothingPendingNotifiesTheNonePendingKey() {
        CapturingSink sink = new CapturingSink();
        AcceptTeleport accept = accept(sink);

        Result<Unit, TeleportError> result = accept.accept(ALICE, ANCHOR);

        assertThat(result.errorOrNull()).isEqualTo(TeleportError.NO_PENDING_REQUEST);
        assertThat(sink.sent).containsExactly(TeleportMessageKey.TPA_NONE_PENDING.key());
    }

    @Test
    void denyWithNothingPendingNotifiesTheNonePendingKey() {
        CapturingSink sink = new CapturingSink();
        AcceptTeleport accept = accept(sink);

        Result<Unit, TeleportError> result = accept.deny(ALICE);

        assertThat(result.errorOrNull()).isEqualTo(TeleportError.NO_PENDING_REQUEST);
        assertThat(sink.sent).containsExactly(TeleportMessageKey.TPA_NONE_PENDING.key());
    }

    @Test
    void cancelWithNothingOutgoingNotifiesTheNonePendingKey() {
        CapturingSink sink = new CapturingSink();
        AcceptTeleport accept = accept(sink);

        Result<Unit, TeleportError> result = accept.cancel(ALICE);

        assertThat(result.errorOrNull()).isEqualTo(TeleportError.NO_PENDING_REQUEST);
        assertThat(sink.sent).containsExactly(TeleportMessageKey.TPA_NONE_PENDING.key());
    }

    private static AcceptTeleport accept(MessageSink sink) {
        Notifier notifier = new Notifier(new KeyOnlyMessages(), sink);
        return new AcceptTeleport(
                new EmptyRegistry(), engine(notifier), notifier, new NoopEvents(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static TeleportEngine engine(Notifier notifier) {
        return new TeleportEngine(
                new NoopCooldowns(),
                new NoopWarmups(),
                new NoopExecutor(),
                notifier,
                new NoopEvents(),
                new TeleportSettings(new MinimalConfig()),
                JailGate.NEVER);
    }

    /** A config that answers no override; the no-pending paths never read it anyway. */
    private record MinimalConfig() implements ConfigStore {
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

    private static final class EmptyRegistry implements RequestRegistry {
        @Override
        public void store(TeleportRequest request) {}

        @Override
        public Optional<TeleportRequest> byId(RequestId id) {
            return Optional.empty();
        }

        @Override
        public List<TeleportRequest> pendingFor(PlayerRef target) {
            return List.of();
        }

        @Override
        public Optional<TeleportRequest> outgoing(PlayerRef requester) {
            return Optional.empty();
        }

        @Override
        public void remove(RequestId id) {}
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

    private static final class NoopWarmups implements Warmups {
        @Override
        public WarmupHandle begin(PlayerRef who, WarmupKind kind, Runnable onComplete, Runnable onCancel) {
            return new CompletedWarmup(who);
        }
    }

    private static final class NoopExecutor implements TeleportExecutor {
        @Override
        public void teleport(PlayerRef who, Destination destination, TeleportKind kind) {}
    }

    private static final class NoopEvents implements DomainEventPublisher {
        @Override
        public void publish(DomainEvent event) {}
    }

    /** Folds a key to its catalog name so the notified key is asserted without a real catalog. */
    private static final class KeyOnlyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    /** Records the rendered text of every delivery, in order. */
    private static final class CapturingSink implements MessageSink {
        final List<String> sent = new ArrayList<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            sent.add(renderedText);
        }
    }
}

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
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.teleport.application.port.JailGate;
import com.uxplima.uxmessentials.teleport.application.port.RequestRegistry;
import com.uxplima.uxmessentials.teleport.application.port.TeleportFlags;
import com.uxplima.uxmessentials.teleport.domain.RequestDirection;
import com.uxplima.uxmessentials.teleport.domain.RequestId;
import com.uxplima.uxmessentials.teleport.domain.TeleportError;
import com.uxplima.uxmessentials.teleport.domain.TeleportRequest;
import org.junit.jupiter.api.Test;

/**
 * The {@code /tpa} / {@code /tpahere} gate must never reject in silence: a self-request, a toggled-off
 * target, a blocked requester and a jailed mover each return the modelled {@link TeleportError} <em>and</em>
 * notify the requester with that error's own catalog key. Exactly once, so the jailed path (which always
 * notified) does not double-send. A request that passes every gate stores nothing through the gate and
 * announces to both parties instead. The collaborators are hand-rolled fakes so the rule is pinned in
 * pure {@code :core}.
 */
class RequestTeleportTest {

    private static final Instant NOW = Instant.parse("2026-05-30T12:00:00Z");
    private static final PlayerRef ALICE = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final PlayerRef BOB = new PlayerRef(UUID.randomUUID(), "Bob");

    @Test
    void aSelfRequestIsRejectedAndNotifiesTheRequester() {
        CapturingSink sink = new CapturingSink();
        RequestTeleport tpa = tpa(new Flags(true, false), JailGate.NEVER, sink);

        Result<TeleportRequest, TeleportError> result = tpa.request(ALICE, ALICE, RequestDirection.TO_TARGET);

        assertThat(result.errorOrNull()).isEqualTo(TeleportError.SELF_REQUEST);
        assertThat(sink.sent).containsExactly(TeleportMessageKey.TPA_SELF.key());
    }

    @Test
    void aToggledOffTargetIsRejectedAndNotifiesTheRequester() {
        CapturingSink sink = new CapturingSink();
        RequestTeleport tpa = tpa(new Flags(false, false), JailGate.NEVER, sink);

        Result<TeleportRequest, TeleportError> result = tpa.request(ALICE, BOB, RequestDirection.TO_TARGET);

        assertThat(result.errorOrNull()).isEqualTo(TeleportError.TARGET_TOGGLED_OFF);
        assertThat(sink.sent).containsExactly(TeleportMessageKey.TPA_TOGGLED_OFF.key());
    }

    @Test
    void aBlockedRequesterIsRejectedAndNotifiesTheRequester() {
        CapturingSink sink = new CapturingSink();
        RequestTeleport tpa = tpa(new Flags(true, true), JailGate.NEVER, sink);

        Result<TeleportRequest, TeleportError> result = tpa.request(ALICE, BOB, RequestDirection.TO_TARGET);

        assertThat(result.errorOrNull()).isEqualTo(TeleportError.REQUESTER_BLOCKED);
        assertThat(sink.sent).containsExactly(TeleportMessageKey.TPA_BLOCKED.key());
    }

    @Test
    void aJailedRequesterIsRejectedAndNotifiedExactlyOnce() {
        CapturingSink sink = new CapturingSink();
        RequestTeleport tpa = tpa(new Flags(true, false), who -> true, sink);

        Result<TeleportRequest, TeleportError> result = tpa.request(ALICE, BOB, RequestDirection.TO_TARGET);

        assertThat(result.errorOrNull()).isEqualTo(TeleportError.JAILED);
        // The jailed branch always notified; the unification must not turn that into a double-send.
        assertThat(sink.sent).containsExactly(TeleportMessageKey.JAILED.key());
    }

    @Test
    void aPassingRequestAnnouncesToBothPartiesAndIsNotRejected() {
        CapturingSink sink = new CapturingSink();
        RequestTeleport tpa = tpa(new Flags(true, false), JailGate.NEVER, sink);

        Result<TeleportRequest, TeleportError> result = tpa.request(ALICE, BOB, RequestDirection.TO_TARGET);

        assertThat(result.isOk()).isTrue();
        assertThat(sink.sent).containsExactly(TeleportMessageKey.TPA_SENT.key(), TeleportMessageKey.TPA_RECEIVED.key());
    }

    private static RequestTeleport tpa(TeleportFlags flags, JailGate jail, MessageSink sink) {
        return new RequestTeleport(
                new NoopRegistry(),
                flags,
                new Notifier(new KeyOnlyMessages(), sink),
                new NoopEvents(),
                new TeleportSettings(new MinimalConfig()),
                jail,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /** A flags fake: {@code accepts} drives the toggle gate, {@code blocked} the block gate. */
    private record Flags(boolean accepts, boolean blocked) implements TeleportFlags {
        @Override
        public boolean acceptsRequests(PlayerRef who) {
            return accepts;
        }

        @Override
        public boolean hasBlocked(PlayerRef target, PlayerRef requester) {
            return blocked;
        }

        @Override
        public boolean toggleRequests(PlayerRef who) {
            return accepts;
        }

        @Override
        public void setAcceptsRequests(PlayerRef who, boolean accepting) {}

        @Override
        public boolean autoAccepts(PlayerRef who) {
            return false;
        }

        @Override
        public boolean toggleAutoAccepts(PlayerRef who) {
            return false;
        }

        @Override
        public void block(PlayerRef blocker, PlayerRef requester) {}

        @Override
        public void unblock(PlayerRef blocker, PlayerRef requester) {}
    }

    /** A config that answers only the request TTL the use case reads. */
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
            return "request-ttl-seconds".equals(path)
                    ? (int) Duration.ofMinutes(1).toSeconds()
                    : fallback;
        }
    }

    private static final class NoopRegistry implements RequestRegistry {
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

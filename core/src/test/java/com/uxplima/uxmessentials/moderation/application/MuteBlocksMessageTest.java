package com.uxplima.uxmessentials.moderation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.messaging.application.SendMessage;
import com.uxplima.uxmessentials.messaging.application.port.ConversationStore;
import com.uxplima.uxmessentials.messaging.application.port.IgnoreStore;
import com.uxplima.uxmessentials.messaging.application.port.MessageDelivery;
import com.uxplima.uxmessentials.messaging.application.port.MessageToggleStore;
import com.uxplima.uxmessentials.messaging.application.port.MutePolicy;
import com.uxplima.uxmessentials.messaging.application.port.SocialSpyStore;
import com.uxplima.uxmessentials.messaging.domain.IgnoreList;
import com.uxplima.uxmessentials.messaging.domain.LastConversation;
import com.uxplima.uxmessentials.messaging.domain.MessageBody;
import com.uxplima.uxmessentials.messaging.domain.MessagingError;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.moderation.fakes.FakeModerationRepository;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/**
 * The mute → messaging cross-context seam: the real {@link RepositoryMutePolicy} the moderation context
 * provides, bound into the real {@link SendMessage} use case, blocks a muted sender's {@code /msg} (the
 * placeholder {@code MutePolicy.NEVER} would let it through). An unmuted sender is delivered normally, and a
 * sender whose timed mute has expired is no longer blocked: the gate reads the live state against the clock.
 */
class MuteBlocksMessageTest {

    private static final Instant T0 = Instant.parse("2026-05-31T00:00:00Z");
    private static final PlayerRef SENDER = new PlayerRef(UUID.randomUUID(), "muted");
    private static final PlayerRef TARGET = new PlayerRef(UUID.randomUUID(), "friend");

    @Test
    void aMutedSenderCannotSendAPrivateMessage() {
        FakeModerationRepository repository = new FakeModerationRepository();
        repository.saveMute(SENDER, MuteState.permanent(Issuer.console("op"), Optional.empty(), T0));
        MutePolicy policy = new RepositoryMutePolicy(repository, Clock.fixed(T0, ZoneOffset.UTC));
        RecordingDelivery delivery = new RecordingDelivery();
        SendMessage send = sendMessage(policy, delivery);

        var result = send.send(SENDER, TARGET, MessageBody.of("hi"));

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(MessagingError.SENDER_MUTED);
        assertThat(delivery.delivered).isEmpty();
    }

    @Test
    void anUnmutedSenderIsDeliveredNormally() {
        FakeModerationRepository repository = new FakeModerationRepository();
        MutePolicy policy = new RepositoryMutePolicy(repository, Clock.fixed(T0, ZoneOffset.UTC));
        RecordingDelivery delivery = new RecordingDelivery();
        SendMessage send = sendMessage(policy, delivery);

        var result = send.send(SENDER, TARGET, MessageBody.of("hi"));

        assertThat(result.isOk()).isTrue();
        assertThat(delivery.delivered).containsExactly(TARGET);
    }

    @Test
    void anExpiredTimedMuteNoLongerBlocks() {
        FakeModerationRepository repository = new FakeModerationRepository();
        repository.saveMute(
                SENDER, MuteState.timed(T0.plus(Duration.ofMinutes(5)), Issuer.console("op"), Optional.empty(), T0));
        // The clock is past the expiry, so the gate sees no active mute even though the row is still present.
        MutePolicy policy =
                new RepositoryMutePolicy(repository, Clock.fixed(T0.plus(Duration.ofMinutes(10)), ZoneOffset.UTC));
        RecordingDelivery delivery = new RecordingDelivery();
        SendMessage send = sendMessage(policy, delivery);

        assertThat(send.send(SENDER, TARGET, MessageBody.of("hi")).isOk()).isTrue();
        assertThat(delivery.delivered).containsExactly(TARGET);
    }

    private static SendMessage sendMessage(MutePolicy policy, RecordingDelivery delivery) {
        return new SendMessage(
                delivery,
                new NoIgnores(),
                new NoopConversations(),
                new AlwaysAccepts(),
                new NoSpies(),
                policy,
                com.uxplima.uxmessentials.messaging.application.port.AfkStatus.NEVER,
                new NoopMail(),
                false,
                new com.uxplima.uxmessentials.shared.application.message.Notifier(new KeyMessages(), new NoopSink()),
                new NoopEvents(),
                Clock.fixed(T0, ZoneOffset.UTC));
    }

    private static final class RecordingDelivery implements MessageDelivery {
        final List<PlayerRef> delivered = new ArrayList<>();

        @Override
        public void echoSent(PlayerRef sender, PlayerRef recipient, MessageBody body) {}

        @Override
        public void deliverMessage(PlayerRef sender, PlayerRef recipient, MessageBody body) {
            delivered.add(recipient);
        }

        @Override
        public void notifyNewMail(PlayerRef recipient, com.uxplima.uxmessentials.messaging.domain.MailItem item) {}

        @Override
        public void deliverMailLine(PlayerRef reader, com.uxplima.uxmessentials.messaging.domain.MailItem item) {}

        @Override
        public void deliverHelpOp(PlayerRef requester, PlayerRef viewer, MessageBody body) {}

        @Override
        public void deliverSpy(PlayerRef observer, PlayerRef sender, PlayerRef recipient, MessageBody body) {}
    }

    private static final class NoIgnores implements IgnoreStore {
        @Override
        public IgnoreList load(PlayerRef owner) {
            return IgnoreList.empty(owner);
        }

        @Override
        public void ignore(
                PlayerRef owner, PlayerRef ignored, com.uxplima.uxmessentials.messaging.domain.IgnoreScope scope) {}

        @Override
        public void unignore(PlayerRef owner, PlayerRef ignored) {}
    }

    private static final class NoopConversations implements ConversationStore {
        @Override
        public Optional<LastConversation> latest(PlayerRef who) {
            return Optional.empty();
        }

        @Override
        public void remember(PlayerRef who, LastConversation conversation) {}

        @Override
        public void forget(PlayerRef who) {}
    }

    private static final class AlwaysAccepts implements MessageToggleStore {
        @Override
        public boolean acceptsMessages(PlayerRef who) {
            return true;
        }

        @Override
        public boolean toggle(PlayerRef who) {
            return true;
        }
    }

    private static final class NoSpies implements SocialSpyStore {
        @Override
        public boolean toggle(PlayerRef who) {
            return false;
        }

        @Override
        public boolean isSpying(PlayerRef who) {
            return false;
        }

        @Override
        public boolean toggleTarget(PlayerRef spy, PlayerRef target) {
            return false;
        }

        @Override
        public List<PlayerRef> activeSpies() {
            return List.of();
        }

        @Override
        public Set<PlayerRef> observersOf(PlayerRef sender, PlayerRef target) {
            return Set.of();
        }
    }

    private static final class NoopMail implements com.uxplima.uxmessentials.messaging.application.port.MailRepository {
        @Override
        public com.uxplima.uxmessentials.messaging.domain.MailBox load(PlayerRef recipient) {
            return com.uxplima.uxmessentials.messaging.domain.MailBox.empty(recipient);
        }

        @Override
        public long unreadCount(PlayerRef recipient) {
            return 0;
        }

        @Override
        public com.uxplima.uxmessentials.messaging.domain.MailItem append(
                com.uxplima.uxmessentials.messaging.domain.MailItem item) {
            return item;
        }

        @Override
        public void markAllRead(PlayerRef recipient) {}

        @Override
        public void clear(PlayerRef recipient) {}

        @Override
        public int deleteSentBefore(Instant cutoff) {
            return 0;
        }
    }

    private static final class NoopEvents implements DomainEventPublisher {
        @Override
        public void publish(DomainEvent event) {}
    }

    private static final class KeyMessages implements com.uxplima.uxmessentials.shared.application.port.Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }

    private static final class NoopSink implements com.uxplima.uxmessentials.shared.application.port.MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
    }
}

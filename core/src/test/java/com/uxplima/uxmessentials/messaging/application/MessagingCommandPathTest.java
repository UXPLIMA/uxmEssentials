package com.uxplima.uxmessentials.messaging.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.messaging.application.port.AfkStatus;
import com.uxplima.uxmessentials.messaging.application.port.ConversationStore;
import com.uxplima.uxmessentials.messaging.application.port.IgnoreStore;
import com.uxplima.uxmessentials.messaging.application.port.MailRepository;
import com.uxplima.uxmessentials.messaging.application.port.MessageDelivery;
import com.uxplima.uxmessentials.messaging.application.port.MessageToggleStore;
import com.uxplima.uxmessentials.messaging.application.port.MutePolicy;
import com.uxplima.uxmessentials.messaging.application.port.SocialSpyStore;
import com.uxplima.uxmessentials.messaging.application.port.VanishVisibility;
import com.uxplima.uxmessentials.messaging.domain.IgnoreList;
import com.uxplima.uxmessentials.messaging.domain.IgnoreScope;
import com.uxplima.uxmessentials.messaging.domain.LastConversation;
import com.uxplima.uxmessentials.messaging.domain.MailBox;
import com.uxplima.uxmessentials.messaging.domain.MailId;
import com.uxplima.uxmessentials.messaging.domain.MailItem;
import com.uxplima.uxmessentials.messaging.domain.MessageBody;
import com.uxplima.uxmessentials.messaging.domain.MessagingError;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The messaging private-channel paths through the real {@link SendMessage} and {@link Reply} use cases against
 * in-memory fakes: the same wiring the Brigadier handlers drive, minus Bukkit. It proves the ordered
 * delivery gates ({@code /msg} is mute-gated, self-rejected, toggle-gated, and ignore-aware, an ignore
 * silently drops but still echoes), the both-sides reply capture, and the {@code /reply} reply-TTL: a fresh
 * conversation resolves, a stale one declines.
 */
class MessagingCommandPathTest {

    private final PlayerRef alice = new PlayerRef(UUID.randomUUID(), "Alice");
    private final PlayerRef bob = new PlayerRef(UUID.randomUUID(), "Bob");
    private final PlayerRef carol = new PlayerRef(UUID.randomUUID(), "Carol");
    private final PlayerRef dave = new PlayerRef(UUID.randomUUID(), "Dave");
    private final PlayerRef spy = new PlayerRef(UUID.randomUUID(), "Spy");
    private final MessageBody hello = MessageBody.of("hello");

    private FakeDelivery delivery;
    private FakeIgnoreStore ignores;
    private InMemoryConversations conversations;
    private FakeToggleStore toggles;
    private FakeSocialSpy socialSpy;
    private SettableMute mute;
    private SettableAfk afk;
    private CapturingMail mail;
    private CapturingSink senderNotices;
    private RecordingEvents events;
    private MutableClock clock;
    private SendMessage sendMessage;
    private Reply reply;

    private SendMessage sendMessageWith(boolean offlineToMail) {
        Notifier notifier = new Notifier(new KeyMessages(), senderNotices);
        return new SendMessage(
                delivery,
                ignores,
                conversations,
                toggles,
                socialSpy,
                mute,
                afk,
                mail,
                offlineToMail,
                notifier,
                events,
                clock);
    }

    @BeforeEach
    void setUp() {
        delivery = new FakeDelivery();
        ignores = new FakeIgnoreStore();
        conversations = new InMemoryConversations();
        toggles = new FakeToggleStore();
        socialSpy = new FakeSocialSpy();
        mute = new SettableMute();
        afk = new SettableAfk();
        mail = new CapturingMail();
        senderNotices = new CapturingSink();
        events = new RecordingEvents();
        clock = new MutableClock(Instant.parse("2026-05-30T12:00:00Z"));
        Notifier notifier = new Notifier(new KeyMessages(), new NoopSink());
        sendMessage = sendMessageWith(false);
        reply = new Reply(
                sendMessage,
                conversations,
                new OnlineLookup(bob),
                VanishVisibility.ALWAYS_VISIBLE,
                new AcceptingReplyRouting(),
                notifier,
                Duration.ofMinutes(5),
                clock);
    }

    @Test
    void aPlainMessageIsDeliveredEchoedAndPublished() {
        var result = sendMessage.send(alice, bob, hello);

        assertThat(result.isOk()).isTrue();
        assertThat(delivery.delivered).containsExactly("Bob:hello");
        assertThat(delivery.echoed).containsExactly("Bob:hello");
        assertThat(events.published).hasSize(1);
    }

    @Test
    void messagingYourselfIsRejected() {
        assertThat(sendMessage.send(alice, alice, hello).errorOrThrow()).isEqualTo(MessagingError.SELF);
    }

    @Test
    void aMutedSenderCannotMessage() {
        mute.muted.add(alice.uuid());

        assertThat(sendMessage.send(alice, bob, hello).errorOrThrow()).isEqualTo(MessagingError.SENDER_MUTED);
        assertThat(delivery.delivered).isEmpty();
    }

    @Test
    void aToggledOffTargetRejectsWithAVisibleReason() {
        toggles.refusing.add(bob.uuid());

        assertThat(sendMessage.send(alice, bob, hello).errorOrThrow()).isEqualTo(MessagingError.TARGET_TOGGLED_OFF);
        assertThat(delivery.delivered).isEmpty();
    }

    @Test
    void anIgnoredSenderIsSilentlyDroppedButStillEchoed() {
        ignores.lists.put(bob.uuid(), IgnoreList.empty(bob).ignore(alice, IgnoreScope.ALL));

        var result = sendMessage.send(alice, bob, hello);

        assertThat(result.isOk()).isTrue(); // the ignore is not observable to the sender
        assertThat(delivery.echoed).containsExactly("Bob:hello"); // echo still happens
        assertThat(delivery.delivered).isEmpty(); // but no real delivery
    }

    @Test
    void sendingCapturesTheReplyTargetOnBothSides() {
        sendMessage.send(alice, bob, hello);

        assertThat(conversations.latest(alice)).isPresent();
        assertThat(conversations.latest(bob).orElseThrow().partner()).isEqualTo(alice);
    }

    @Test
    void replyResolvesAFreshConversation() {
        conversations.remember(alice, LastConversation.with(bob, clock.instant()));

        var result = reply.reply(alice, hello);

        assertThat(result.isOk()).isTrue();
        assertThat(delivery.delivered).containsExactly("Bob:hello");
    }

    @Test
    void replyDeclinesAStaleConversationPastTheTtl() {
        conversations.remember(alice, LastConversation.with(bob, clock.instant()));
        clock.advance(Duration.ofMinutes(6)); // past the 5-minute reply-TTL

        var result = reply.reply(alice, hello);

        assertThat(result.errorOrThrow()).isEqualTo(MessagingError.NO_REPLY_TARGET);
        assertThat(delivery.delivered).isEmpty();
    }

    @Test
    void replyWithNoConversationDeclines() {
        assertThat(reply.reply(alice, hello).errorOrThrow()).isEqualTo(MessagingError.NO_REPLY_TARGET);
    }

    @Test
    void replyToAPartnerWithReplyRoutingOffDeclinesWithoutLeaking() {
        Notifier notifier = new Notifier(new KeyMessages(), new NoopSink());
        Reply gated = new Reply(
                sendMessage,
                conversations,
                new OnlineLookup(bob),
                VanishVisibility.ALWAYS_VISIBLE,
                new RefusingReplyRouting(),
                notifier,
                Duration.ofMinutes(5),
                clock);
        conversations.remember(alice, LastConversation.with(bob, clock.instant()));

        var result = gated.reply(alice, hello);

        assertThat(result.errorOrThrow()).isEqualTo(MessagingError.NO_REPLY_TARGET);
        assertThat(delivery.delivered).isEmpty();
    }

    @Test
    void messagingAnAfkTargetDeliversAndNotifiesTheSender() {
        afk.reasons.put(bob.uuid(), "lunch");

        var result = sendMessage.send(alice, bob, hello);

        assertThat(result.isOk()).isTrue();
        assertThat(delivery.delivered).containsExactly("Bob:hello"); // AFK is a notice, not a block
        assertThat(senderNotices.keys).contains(MessagingMessageKey.MSG_TARGET_AFK.key());
    }

    @Test
    void messagingANonAfkTargetSendsNoAfkNotice() {
        var result = sendMessage.send(alice, bob, hello);

        assertThat(result.isOk()).isTrue();
        assertThat(senderNotices.keys).doesNotContain(MessagingMessageKey.MSG_TARGET_AFK.key());
    }

    @Test
    void anAfkTargetWhoIgnoresTheSenderGetsNoAfkNoticeLeak() {
        ignores.lists.put(bob.uuid(), IgnoreList.empty(bob).ignore(alice, IgnoreScope.ALL));
        afk.reasons.put(bob.uuid(), "lunch");

        var result = sendMessage.send(alice, bob, hello);

        assertThat(result.isOk()).isTrue();
        assertThat(delivery.delivered).isEmpty(); // silently dropped
        // No AFK notice: it would reveal the ignoring target is online.
        assertThat(senderNotices.keys).doesNotContain(MessagingMessageKey.MSG_TARGET_AFK.key());
    }

    @Test
    void anOfflineTargetIsStoredAsMailWhenTheFallbackIsOn() {
        SendMessage offlineToMail = sendMessageWith(true);

        var result = offlineToMail.send(alice, bob, hello, false);

        assertThat(result.isOk()).isTrue();
        assertThat(mail.appended).hasSize(1);
        MailItem stored = mail.appended.get(0);
        assertThat(stored.recipient()).isEqualTo(bob);
        assertThat(stored.body()).isEqualTo(hello);
        assertThat(stored.sender().name()).isEqualTo(alice.name());
        assertThat(senderNotices.keys).contains(MessagingMessageKey.MSG_SENT_TO_MAIL.key());
        assertThat(delivery.delivered).isEmpty(); // not delivered live. The target is offline
    }

    @Test
    void anOfflineTargetRejectsAsBeforeWhenTheFallbackIsOff() {
        var result = sendMessage.send(alice, bob, hello, false); // fallback off (default)

        assertThat(result.errorOrThrow()).isEqualTo(MessagingError.TARGET_OFFLINE);
        assertThat(mail.appended).isEmpty();
    }

    @Test
    void aMutedSenderCannotMailAnOfflineTarget() {
        mute.muted.add(alice.uuid());
        SendMessage offlineToMail = sendMessageWith(true);

        var result = offlineToMail.send(alice, bob, hello, false);

        assertThat(result.errorOrThrow()).isEqualTo(MessagingError.SENDER_MUTED);
        assertThat(mail.appended).isEmpty();
    }

    @Test
    void aGlobalSpySeesEveryPrivateMessage() {
        new SocialSpy(socialSpy, new Notifier(new KeyMessages(), new NoopSink())).toggle(spy);

        sendMessage.send(alice, bob, hello);
        sendMessage.send(carol, dave, hello);

        assertThat(delivery.spied).containsExactlyInAnyOrder("Spy<-Alice:Bob:hello", "Spy<-Carol:Dave:hello");
    }

    @Test
    void aTargetedSpyOnlySeesConversationsItsTargetIsAPartyTo() {
        SocialSpy spies = new SocialSpy(socialSpy, new Notifier(new KeyMessages(), new NoopSink()));
        spies.watch(spy, bob); // watch Bob only

        sendMessage.send(alice, bob, hello); // Bob is the recipient, matches
        sendMessage.send(bob, carol, hello); // Bob is the sender, matches
        sendMessage.send(carol, dave, hello); // neither party is Bob, must not match

        assertThat(delivery.spied).containsExactlyInAnyOrder("Spy<-Alice:Bob:hello", "Spy<-Bob:Carol:hello");
    }

    @Test
    void aTargetedSpyDoesNotSeeUnrelatedMessages() {
        new SocialSpy(socialSpy, new Notifier(new KeyMessages(), new NoopSink())).watch(spy, bob);

        sendMessage.send(carol, dave, hello); // Bob is not a party

        assertThat(delivery.spied).isEmpty();
    }

    @Test
    void aSpyWhoIsAPartyToTheConversationDoesNotGetASpyLine() {
        new SocialSpy(socialSpy, new Notifier(new KeyMessages(), new NoopSink())).toggle(spy);

        sendMessage.send(spy, bob, hello); // the spy sends. Must not also be spied

        assertThat(delivery.spied).isEmpty();
    }

    @Test
    void observersOfReturnsAllSpiesPlusMatchingTargetSpies() {
        socialSpy.toggle(alice); // Alice is a global ALL spy
        socialSpy.toggleTarget(spy, bob); // Spy watches Bob only

        assertThat(socialSpy.observersOf(carol, bob)).containsExactlyInAnyOrder(alice, spy);
        assertThat(socialSpy.observersOf(carol, dave)).containsExactly(alice); // only the ALL spy
    }

    @Test
    void watchTogglesATargetAndNotifies() {
        SocialSpy spies = new SocialSpy(socialSpy, new Notifier(new KeyMessages(), senderNotices));

        assertThat(spies.watch(spy, bob)).isTrue(); // added
        assertThat(senderNotices.keys).contains(MessagingMessageKey.SOCIALSPY_WATCHING.key());
        assertThat(spies.watch(spy, bob)).isFalse(); // removed
        assertThat(senderNotices.keys).contains(MessagingMessageKey.SOCIALSPY_UNWATCHED.key());
    }

    @Test
    void theGlobalToggleStillFlipsAndNotifies() {
        SocialSpy spies = new SocialSpy(socialSpy, new Notifier(new KeyMessages(), senderNotices));

        assertThat(spies.toggle(spy)).isTrue();
        assertThat(senderNotices.keys).contains(MessagingMessageKey.SOCIALSPY_ON.key());
        assertThat(spies.toggle(spy)).isFalse();
        assertThat(senderNotices.keys).contains(MessagingMessageKey.SOCIALSPY_OFF.key());
    }

    // --- fakes -------------------------------------------------------------------------------------------

    private static final class FakeDelivery implements MessageDelivery {
        final List<String> delivered = new ArrayList<>();
        final List<String> echoed = new ArrayList<>();
        final List<String> spied = new ArrayList<>();

        @Override
        public void deliverMessage(PlayerRef sender, PlayerRef recipient, MessageBody body) {
            delivered.add(recipient.name() + ":" + body.value());
        }

        @Override
        public void echoSent(PlayerRef sender, PlayerRef recipient, MessageBody body) {
            echoed.add(recipient.name() + ":" + body.value());
        }

        @Override
        public void notifyNewMail(PlayerRef recipient, MailItem item) {}

        @Override
        public void deliverMailLine(PlayerRef reader, MailItem item) {}

        @Override
        public void deliverHelpOp(PlayerRef requester, PlayerRef viewer, MessageBody body) {}

        @Override
        public void deliverSpy(PlayerRef observer, PlayerRef sender, PlayerRef recipient, MessageBody body) {
            spied.add(observer.name() + "<-" + sender.name() + ":" + recipient.name() + ":" + body.value());
        }
    }

    private static final class FakeIgnoreStore implements IgnoreStore {
        final ConcurrentHashMap<UUID, IgnoreList> lists = new ConcurrentHashMap<>();

        @Override
        public IgnoreList load(PlayerRef owner) {
            return lists.getOrDefault(owner.uuid(), IgnoreList.empty(owner));
        }

        @Override
        public void ignore(PlayerRef owner, PlayerRef ignored, IgnoreScope scope) {
            lists.compute(
                    owner.uuid(), (id, list) -> (list == null ? IgnoreList.empty(owner) : list).ignore(ignored, scope));
        }

        @Override
        public void unignore(PlayerRef owner, PlayerRef ignored) {
            lists.computeIfPresent(owner.uuid(), (id, list) -> list.unignore(ignored));
        }
    }

    private static final class InMemoryConversations implements ConversationStore {
        private final ConcurrentHashMap<UUID, LastConversation> latest = new ConcurrentHashMap<>();

        @Override
        public void remember(PlayerRef owner, LastConversation conversation) {
            latest.put(owner.uuid(), conversation);
        }

        @Override
        public Optional<LastConversation> latest(PlayerRef owner) {
            return Optional.ofNullable(latest.get(owner.uuid()));
        }

        @Override
        public void forget(PlayerRef owner) {
            latest.remove(owner.uuid());
        }
    }

    private static final class FakeToggleStore implements MessageToggleStore {
        final java.util.Set<UUID> refusing = ConcurrentHashMap.newKeySet();

        @Override
        public boolean acceptsMessages(PlayerRef who) {
            return !refusing.contains(who.uuid());
        }

        @Override
        public boolean toggle(PlayerRef who) {
            return refusing.remove(who.uuid()) || !refusing.add(who.uuid());
        }
    }

    private static final class AcceptingReplyRouting
            implements com.uxplima.uxmessentials.messaging.application.port.ReplyRoutingStore {
        @Override
        public boolean acceptsReplies(PlayerRef who) {
            return true;
        }

        @Override
        public boolean toggle(PlayerRef who) {
            return true;
        }
    }

    private static final class RefusingReplyRouting
            implements com.uxplima.uxmessentials.messaging.application.port.ReplyRoutingStore {
        @Override
        public boolean acceptsReplies(PlayerRef who) {
            return false;
        }

        @Override
        public boolean toggle(PlayerRef who) {
            return false;
        }
    }

    /** A targeting-aware spy store: a global ALL set plus per-spy target sets, resolving every spy as online. */
    private static final class FakeSocialSpy implements SocialSpyStore {
        final java.util.Map<UUID, PlayerRef> all = new java.util.HashMap<>();
        final java.util.Map<UUID, PlayerRef> spyRefs = new java.util.HashMap<>();
        final java.util.Map<UUID, Set<UUID>> targets = new java.util.HashMap<>();

        @Override
        public boolean isSpying(PlayerRef who) {
            return all.containsKey(who.uuid());
        }

        @Override
        public boolean toggle(PlayerRef who) {
            spyRefs.put(who.uuid(), who);
            if (all.remove(who.uuid()) != null) {
                return false;
            }
            all.put(who.uuid(), who);
            return true;
        }

        @Override
        public boolean toggleTarget(PlayerRef spy, PlayerRef target) {
            spyRefs.put(spy.uuid(), spy);
            Set<UUID> watched = targets.computeIfAbsent(spy.uuid(), id -> new HashSet<>());
            if (watched.remove(target.uuid())) {
                return false;
            }
            watched.add(target.uuid());
            return true;
        }

        @Override
        public List<PlayerRef> activeSpies() {
            return List.copyOf(all.values());
        }

        @Override
        public Set<PlayerRef> observersOf(PlayerRef sender, PlayerRef target) {
            Set<PlayerRef> observers = new HashSet<>(all.values());
            targets.forEach((spy, watched) -> {
                if (watched.contains(sender.uuid()) || watched.contains(target.uuid())) {
                    observers.add(spyRefs.get(spy));
                }
            });
            return observers;
        }
    }

    private static final class SettableMute implements MutePolicy {
        final java.util.Set<UUID> muted = ConcurrentHashMap.newKeySet();

        @Override
        public boolean isMuted(PlayerRef who) {
            return muted.contains(who.uuid());
        }
    }

    private static final class SettableAfk implements AfkStatus {
        final ConcurrentHashMap<UUID, String> reasons = new ConcurrentHashMap<>();

        @Override
        public Optional<String> afkReasonOf(PlayerRef who) {
            return Optional.ofNullable(reasons.get(who.uuid()));
        }
    }

    private static final class CapturingMail implements MailRepository {
        final List<MailItem> appended = new ArrayList<>();

        @Override
        public MailBox load(PlayerRef recipient) {
            return MailBox.empty(recipient);
        }

        @Override
        public long unreadCount(PlayerRef recipient) {
            return 0;
        }

        @Override
        public MailItem append(MailItem item) {
            MailItem stored = item.withId(MailId.of(appended.size() + 1L));
            appended.add(stored);
            return stored;
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

    private record OnlineLookup(PlayerRef online) implements PlayerLookup {
        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return online.name().equals(name) ? Optional.of(online) : Optional.empty();
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            return online.uuid().equals(uuid) ? Optional.of(online) : Optional.empty();
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return online.uuid().equals(uuid);
        }
    }

    private static final class RecordingEvents implements DomainEventPublisher {
        final List<DomainEvent> published = new ArrayList<>();

        @Override
        public void publish(DomainEvent event) {
            published.add(event);
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

    /** Captures the message keys delivered to a viewer; {@link KeyMessages} renders each key to its own name. */
    private static final class CapturingSink implements MessageSink {
        final List<String> keys = new ArrayList<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            keys.add(renderedText);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}

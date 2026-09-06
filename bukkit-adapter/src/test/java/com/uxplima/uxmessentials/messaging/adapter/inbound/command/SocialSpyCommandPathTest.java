package com.uxplima.uxmessentials.messaging.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.uxplima.uxmessentials.messaging.adapter.MessagingServices;
import com.uxplima.uxmessentials.messaging.adapter.MutableAfkStatus;
import com.uxplima.uxmessentials.messaging.adapter.outbound.BukkitMessageDelivery;
import com.uxplima.uxmessentials.messaging.adapter.outbound.BukkitStaffAudience;
import com.uxplima.uxmessentials.messaging.adapter.outbound.InMemoryConversationStore;
import com.uxplima.uxmessentials.messaging.adapter.outbound.InMemorySocialSpyStore;
import com.uxplima.uxmessentials.messaging.application.ClearMail;
import com.uxplima.uxmessentials.messaging.application.HelpOp;
import com.uxplima.uxmessentials.messaging.application.Ignore;
import com.uxplima.uxmessentials.messaging.application.ListIgnores;
import com.uxplima.uxmessentials.messaging.application.MessagingMessageKey;
import com.uxplima.uxmessentials.messaging.application.MsgToggle;
import com.uxplima.uxmessentials.messaging.application.ReadMail;
import com.uxplima.uxmessentials.messaging.application.Reply;
import com.uxplima.uxmessentials.messaging.application.ReplyToggle;
import com.uxplima.uxmessentials.messaging.application.SendMail;
import com.uxplima.uxmessentials.messaging.application.SendMailToAll;
import com.uxplima.uxmessentials.messaging.application.SendMessage;
import com.uxplima.uxmessentials.messaging.application.SocialSpy;
import com.uxplima.uxmessentials.messaging.application.Unignore;
import com.uxplima.uxmessentials.messaging.application.port.MutePolicy;
import com.uxplima.uxmessentials.messaging.application.port.ReplyRoutingStore;
import com.uxplima.uxmessentials.messaging.application.port.VanishVisibility;
import com.uxplima.uxmessentials.messaging.domain.IgnoreList;
import com.uxplima.uxmessentials.messaging.domain.IgnoreScope;
import com.uxplima.uxmessentials.messaging.domain.MailBox;
import com.uxplima.uxmessentials.messaging.domain.MailId;
import com.uxplima.uxmessentials.messaging.domain.MailItem;
import com.uxplima.uxmessentials.messaging.domain.MessageBody;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the {@code /socialspy} Brigadier node in both forms. The no-argument form keeps
 * toggling the global ALL flag (every private message), while {@code /socialspy <player>} toggles one player on
 * the staff member's target set so they see only that player's conversations. Both ride the one
 * {@code uxmessentials.msg.socialspy} node, and the spy fan-out is exercised through the real
 * {@link InMemorySocialSpyStore} + {@link SendMessage} so a targeted spy receives matching DMs and nothing
 * else. The {@link Messages}/{@link MessageSink} are recording fakes so each outcome is asserted by the key it
 * delivered.
 */
class SocialSpyCommandPathTest {

    private static final Instant T0 = Instant.parse("2026-06-14T12:00:00Z");

    private ServerMock server;
    private RecordingSink sink;
    private InMemorySocialSpyStore spies;
    private DeliveryRecorder delivery;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        sink = new RecordingSink();
        spies = new InMemorySocialSpyStore();
        delivery = new DeliveryRecorder();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void bareSocialSpyTogglesTheGlobalFlagOnThenOff() {
        PlayerMock staff = server.addPlayer("Staff");
        staff.setOp(true);

        executeSocialSpy(staff, "socialspy");
        assertThat(spies.isSpying(ref(staff))).isTrue();
        assertThat(sink.keys).contains(MessagingMessageKey.SOCIALSPY_ON);

        executeSocialSpy(staff, "socialspy");
        assertThat(spies.isSpying(ref(staff))).isFalse();
        assertThat(sink.keys).contains(MessagingMessageKey.SOCIALSPY_OFF);
    }

    @Test
    void socialSpyWithAPlayerTogglesATargetWithoutSettingTheGlobalFlag() {
        PlayerMock staff = server.addPlayer("Staff");
        staff.setOp(true);
        server.addPlayer("Bob");

        executeSocialSpy(staff, "socialspy Bob");
        assertThat(spies.isSpying(ref(staff))).isFalse(); // targeted mode, not global ALL
        assertThat(sink.keys).contains(MessagingMessageKey.SOCIALSPY_WATCHING);

        executeSocialSpy(staff, "socialspy Bob"); // toggle the same target off
        assertThat(sink.keys).contains(MessagingMessageKey.SOCIALSPY_UNWATCHED);
    }

    @Test
    void aTargetedSpyReceivesOnlyTheWatchedPlayersConversations() {
        PlayerMock staff = server.addPlayer("Staff");
        staff.setOp(true);
        PlayerMock alice = server.addPlayer("Alice");
        PlayerMock bob = server.addPlayer("Bob");
        PlayerMock carol = server.addPlayer("Carol");

        executeSocialSpy(staff, "socialspy Bob"); // watch Bob

        SendMessage send = sendMessage();
        send.send(ref(alice), ref(bob), MessageBody.of("hi bob")); // Bob is a party, spied
        send.send(ref(alice), ref(carol), MessageBody.of("hi carol")); // Bob is not, not spied

        assertThat(delivery.spied).containsExactly("Staff:Alice:Bob:hi bob");
    }

    @Test
    void socialSpyTargetingAnOfflinePlayerResolvesAndWatches() {
        PlayerMock staff = server.addPlayer("Staff");
        staff.setOp(true);
        // "Ghost" never joins; the lookup resolves it through findByName (offline). The watch is a UUID, so an
        // offline target is registered all the same.
        executeSocialSpy(staff, "socialspy Ghost");

        assertThat(sink.keys).contains(MessagingMessageKey.SOCIALSPY_WATCHING);
    }

    @Test
    void socialSpyIsGatedBehindItsPermission() {
        PlayerMock alice = server.addPlayer("Alice"); // no permission

        assertThatThrownBy(() -> executeSocialSpyRaw(alice, "socialspy")).isInstanceOf(CommandSyntaxException.class);
        assertThat(spies.isSpying(ref(alice))).isFalse();
    }

    private void executeSocialSpy(Player sender, String input) {
        try {
            executeSocialSpyRaw(sender, input);
        } catch (CommandSyntaxException e) {
            throw new AssertionError("command did not parse: " + input, e);
        }
    }

    private void executeSocialSpyRaw(Player sender, String input) throws CommandSyntaxException {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(new SocialSpyCommand(services(), new KeyMessages(), sink).build());
        dispatcher.execute(input, CommandSourceStackMock.from(sender));
    }

    private SendMessage sendMessage() {
        Messages messages = new KeyMessages();
        Notifier notifier = new Notifier(messages, sink);
        return new SendMessage(
                delivery,
                new NoIgnores(),
                new InMemoryConversationStore(),
                new AcceptingToggles(),
                spies,
                MutePolicy.NEVER,
                new MutableAfkStatus(),
                new CapturingMail(),
                false,
                notifier,
                new NoEvents(),
                Clock.fixed(T0, ZoneOffset.UTC));
    }

    private MessagingServices services() {
        Messages messages = new KeyMessages();
        Notifier notifier = new Notifier(messages, sink);
        BukkitMessageDelivery realDelivery = new BukkitMessageDelivery(messages, sink);
        MutePolicy mute = MutePolicy.NEVER;
        PlayerLookup players = new CapturingLookup(server);
        InMemoryConversationStore conversations = new InMemoryConversationStore();
        ReplyRoutingStore replies = new AcceptingReplies();
        NoIgnores ignores = new NoIgnores();
        CapturingMail mail = new CapturingMail();
        VanishVisibility vanish = (viewer, target) -> false;
        DomainEventPublisher events = new NoEvents();
        Scheduler scheduler = new InlineScheduler();
        Clock clock = Clock.fixed(T0, ZoneOffset.UTC);
        SendMessage sendMessage = new SendMessage(
                realDelivery,
                ignores,
                conversations,
                new AcceptingToggles(),
                spies,
                mute,
                new MutableAfkStatus(),
                mail,
                false,
                notifier,
                events,
                clock);
        return new MessagingServices(
                sendMessage,
                new Reply(sendMessage, conversations, players, vanish, replies, notifier, Duration.ofMinutes(5), clock),
                new SendMail(mail, ignores, realDelivery, mute, notifier, events, clock),
                new SendMailToAll(mail, clock),
                new ReadMail(mail, realDelivery, notifier),
                new ClearMail(mail, notifier),
                new MsgToggle(new AcceptingToggles(), notifier),
                new ReplyToggle(replies, notifier),
                new Ignore(ignores, notifier),
                new Unignore(ignores, notifier),
                new ListIgnores(ignores, notifier),
                new SocialSpy(spies, notifier),
                new HelpOp(new BukkitStaffAudience(), realDelivery, mute, notifier, events, clock),
                players,
                vanish,
                scheduler);
    }

    private static PlayerRef ref(Player player) {
        return new PlayerRef(player.getUniqueId(), player.getName());
    }

    /** Resolves online players against the live mock server; "Ghost" is the one known offline profile. */
    private static final class CapturingLookup implements PlayerLookup {
        private final ServerMock server;

        CapturingLookup(ServerMock server) {
            this.server = server;
        }

        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            Player online = server.getPlayerExact(name);
            return online == null ? Optional.empty() : Optional.of(ref(online));
        }

        @Override
        public Optional<PlayerRef> findByName(String name) {
            Optional<PlayerRef> online = findOnlineByName(name);
            if (online.isPresent()) {
                return online;
            }
            return name.equals("Ghost") ? Optional.of(new PlayerRef(UUID.randomUUID(), "Ghost")) : Optional.empty();
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            Player online = server.getPlayer(uuid);
            return online == null ? Optional.empty() : Optional.of(ref(online));
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return server.getPlayer(uuid) != null;
        }
    }

    /** Records each spy line as observer:from:to:message so a targeted fan-out can be asserted exactly. */
    private static final class DeliveryRecorder
            implements com.uxplima.uxmessentials.messaging.application.port.MessageDelivery {
        final List<String> spied = new ArrayList<>();

        @Override
        public void deliverMessage(PlayerRef sender, PlayerRef recipient, MessageBody body) {}

        @Override
        public void echoSent(PlayerRef sender, PlayerRef recipient, MessageBody body) {}

        @Override
        public void notifyNewMail(PlayerRef recipient, MailItem item) {}

        @Override
        public void deliverMailLine(PlayerRef reader, MailItem item) {}

        @Override
        public void deliverHelpOp(PlayerRef requester, PlayerRef viewer, MessageBody body) {}

        @Override
        public void deliverSpy(PlayerRef observer, PlayerRef sender, PlayerRef recipient, MessageBody body) {
            spied.add(observer.name() + ":" + sender.name() + ":" + recipient.name() + ":" + body.value());
        }
    }

    private static final class CapturingMail
            implements com.uxplima.uxmessentials.messaging.application.port.MailRepository {
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
            return item.withId(MailId.of(1L));
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

    private static final class NoIgnores implements com.uxplima.uxmessentials.messaging.application.port.IgnoreStore {
        @Override
        public IgnoreList load(PlayerRef owner) {
            return IgnoreList.empty(owner);
        }

        @Override
        public void ignore(PlayerRef owner, PlayerRef ignored, IgnoreScope scope) {}

        @Override
        public void unignore(PlayerRef owner, PlayerRef ignored) {}
    }

    private static final class AcceptingToggles
            implements com.uxplima.uxmessentials.messaging.application.port.MessageToggleStore {
        @Override
        public boolean acceptsMessages(PlayerRef who) {
            return true;
        }

        @Override
        public boolean toggle(PlayerRef who) {
            return true;
        }
    }

    private static final class AcceptingReplies implements ReplyRoutingStore {
        @Override
        public boolean acceptsReplies(PlayerRef who) {
            return true;
        }

        @Override
        public boolean toggle(PlayerRef who) {
            return true;
        }
    }

    private static final class NoEvents implements DomainEventPublisher {
        @Override
        public void publish(DomainEvent event) {}
    }

    /** Resolves a key to its own string and records it so each path's outcome is asserted by the key. */
    private final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            sink.keys.add(key);
            return key.key();
        }
    }

    private static final class RecordingSink implements MessageSink {
        private final List<MessageKey> keys = new ArrayList<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
    }

    private static final class InlineScheduler implements Scheduler {
        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(Position position, Runnable task) {
            task.run();
        }

        @Override
        public void onEntity(PlayerRef player, Runnable task) {
            task.run();
        }

        @Override
        public void async(Runnable task) {
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }
}

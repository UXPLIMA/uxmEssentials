package com.uxplima.uxmessentials.messaging.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
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
import com.uxplima.uxmessentials.messaging.adapter.inbound.gui.MessagingGuiViews;
import com.uxplima.uxmessentials.messaging.adapter.outbound.BukkitMessageDelivery;
import com.uxplima.uxmessentials.messaging.adapter.outbound.BukkitStaffAudience;
import com.uxplima.uxmessentials.messaging.adapter.outbound.InMemoryConversationStore;
import com.uxplima.uxmessentials.messaging.adapter.outbound.InMemorySocialSpyStore;
import com.uxplima.uxmessentials.messaging.adapter.outbound.PresenceAfkStatus;
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
import com.uxplima.uxmessentials.messaging.application.port.ConversationStore;
import com.uxplima.uxmessentials.messaging.application.port.IgnoreStore;
import com.uxplima.uxmessentials.messaging.application.port.MailRepository;
import com.uxplima.uxmessentials.messaging.application.port.MessageDelivery;
import com.uxplima.uxmessentials.messaging.application.port.MutePolicy;
import com.uxplima.uxmessentials.messaging.application.port.ReplyRoutingStore;
import com.uxplima.uxmessentials.messaging.application.port.VanishVisibility;
import com.uxplima.uxmessentials.messaging.domain.IgnoreList;
import com.uxplima.uxmessentials.messaging.domain.IgnoreScope;
import com.uxplima.uxmessentials.messaging.domain.MailBox;
import com.uxplima.uxmessentials.messaging.domain.MailId;
import com.uxplima.uxmessentials.messaging.domain.MailItem;
import com.uxplima.uxmessentials.presence.adapter.outbound.InMemoryPresenceStore;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the messaging send-path gaps through the real {@code /msg} and {@code /mail} Brigadier
 * nodes: the offline-{@code /msg} → mail fallback (the command passes {@code targetOnline=false} so the core
 * stores mail), the AFK courtesy notice on a {@code /msg} to an AFK online target (the {@link PresenceAfkStatus}
 * soft-couple over the presence store), and {@code /mail sendall} mailing every online recipient off-tick,
 * counting them, and being gated behind {@code uxmessentials.mail.sendall}. The {@link Messages}/{@link
 * MessageSink} are recording fakes so a path's outcome is asserted by the {@link MessageKey} it delivered; the
 * {@link Scheduler} is inline so the off-tick fan-out is observable synchronously.
 */
class MessagingSendPathTest {

    private static final Instant T0 = Instant.parse("2026-06-14T12:00:00Z");
    private static final String MAIL_USE_PERMISSION = "uxmessentials.mail.use";

    private ServerMock server;
    private RecordingSink sink;
    private CapturingMail mail;
    private InMemoryPresenceStore presence;
    private MutableAfkStatus afk;
    private CountingScheduler scheduler;
    private VanishVisibility vanish;
    private boolean offlineToMail;

    @org.junit.jupiter.api.io.TempDir
    Path guiDir;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        sink = new RecordingSink();
        mail = new CapturingMail();
        presence = new InMemoryPresenceStore(Clock.fixed(T0, ZoneOffset.UTC), uuid -> false);
        afk = new MutableAfkStatus();
        scheduler = new CountingScheduler();
        vanish = (viewer, target) -> false; // no one hidden unless a test opts in
        offlineToMail = true; // the shipped default; a test flips it to exercise the policy-off branch
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void msgToAnOfflinePlayerIsStoredAsMail() {
        PlayerMock alice = server.addPlayer("Alice");
        alice.setOp(true); // holds uxmessentials.msg.use
        // "Ghost" never joins, so the lookup resolves it only through findByName (offline); the command passes
        // targetOnline=false and the core stores it as mail rather than rejecting.
        executeMsg(alice, "msg Ghost see you tomorrow");

        assertThat(mail.appended).hasSize(1);
        MailItem stored = mail.appended.get(0);
        assertThat(stored.recipient().name()).isEqualTo("Ghost");
        assertThat(stored.body().value()).isEqualTo("see you tomorrow");
        assertThat(sink.keys).contains(MessagingMessageKey.MSG_SENT_TO_MAIL);
    }

    @Test
    void msgToAnUnknownNameIsRejectedAndStoresNoMail() {
        PlayerMock alice = server.addPlayer("Alice");
        alice.setOp(true); // holds uxmessentials.msg.use
        // CapturingLookup only knows online players and an explicit offline set; an unseen name resolves to
        // neither, so it is rejected as unknown and nothing is mailed.
        executeMsg(alice, "msg Nobody hello");

        assertThat(mail.appended).isEmpty();
        assertThat(sink.keys).contains(MessagingCommandSupport.UNKNOWN_PLAYER);
    }

    @Test
    void msgToAnAfkOnlineTargetDeliversAndAddsTheAfkNotice() {
        PlayerMock alice = server.addPlayer("Alice");
        alice.setOp(true); // holds uxmessentials.msg.use
        PlayerMock bob = server.addPlayer("Bob");
        PlayerRef bobRef = ref(bob);
        presence.update(bobRef, p -> p.markAfk(Optional.of("dinner")));
        afk.bind(new PresenceAfkStatus(presence)); // presence module wired → real AFK status bound

        executeMsg(alice, "msg Bob you there?");

        // Delivered live (no mail), and the sender additionally got the AFK courtesy notice.
        assertThat(mail.appended).isEmpty();
        assertThat(sink.keys).contains(MessagingMessageKey.MSG_TARGET_AFK);
    }

    @Test
    void msgToAHiddenOnlineTargetIsIndistinguishableFromOfflineWhenMailIsOn() {
        PlayerMock alice = server.addPlayer("Alice");
        alice.setOp(true); // holds uxmessentials.msg.use
        PlayerMock bob = server.addPlayer("Bob");
        vanish = (viewer, target) -> true; // Bob is online but unseeable by Alice

        executeMsg(alice, "msg Bob you there?");

        // No live delivery (neither the sender echo nor the recipient line), but a mail was stored for Bob and
        // the sender got MSG_SENT_TO_MAIL: byte-identical to messaging a genuinely-offline player.
        assertThat(sink.keys).doesNotContain(MessagingMessageKey.MSG_RECEIVED, MessagingMessageKey.MSG_SENT);
        assertThat(mail.appended).hasSize(1);
        MailItem stored = mail.appended.get(0);
        assertThat(stored.recipient().name()).isEqualTo("Bob");
        assertThat(stored.recipient().uuid()).isEqualTo(bob.getUniqueId()); // mailed to Bob's real ref
        assertThat(stored.body().value()).isEqualTo("you there?");
        assertThat(sink.keys).contains(MessagingMessageKey.MSG_SENT_TO_MAIL);
    }

    @Test
    void msgToAHiddenOnlineTargetIsIndistinguishableFromOfflineWhenMailIsOff() {
        offlineToMail = false; // policy off: a hidden target must look exactly like an offline one
        PlayerMock alice = server.addPlayer("Alice");
        alice.setOp(true); // holds uxmessentials.msg.use
        server.addPlayer("Bob");
        vanish = (viewer, target) -> true; // Bob is online but unseeable by Alice

        executeMsg(alice, "msg Bob you there?");

        // Same as a genuinely-offline target with the policy off: TARGET_OFFLINE, no live delivery, no mail.
        assertThat(sink.keys).contains(MessagingMessageKey.MSG_TARGET_OFFLINE);
        assertThat(sink.keys).doesNotContain(MessagingMessageKey.MSG_RECEIVED, MessagingMessageKey.MSG_SENT);
        assertThat(mail.appended).isEmpty();
    }

    @Test
    void mailSendallMailsEachOnlineRecipientAndCountsThem() {
        PlayerMock staff = server.addPlayer("Staff");
        staff.setOp(true);
        server.addPlayer("Alice");
        server.addPlayer("Bob");

        executeMail(CommandSourceStackMock.from(staff), "mail sendall server restart soon");

        // One durable mail per online player (Staff included: the broadcaster is not auto-excluded).
        assertThat(mail.appended).hasSize(3);
        assertThat(mail.appended)
                .extracting(item -> item.recipient().name())
                .containsExactlyInAnyOrder("Staff", "Alice", "Bob");
        assertThat(mail.appended)
                .allSatisfy(item -> assertThat(item.body().value()).isEqualTo("server restart soon"));
        assertThat(sink.keys).contains(MessagingMessageKey.MAIL_SENDALL_DONE);
        assertThat(scheduler.asyncTasks).isOne(); // the fan-out ran off-tick
    }

    @Test
    void mailSendallIsGatedBehindItsPermission() {
        PlayerMock alice = server.addPlayer("Alice");
        // Has /mail use but not /mail sendall: the sendall literal's requires() filters the node out, so the
        // dispatcher cannot reach it and nothing is mailed.
        alice.addAttachment(MockBukkit.createMockPlugin(), MAIL_USE_PERMISSION, true);

        assertThatThrownBy(() -> executeMailRaw(CommandSourceStackMock.from(alice), "mail sendall hi everyone"))
                .isInstanceOf(CommandSyntaxException.class);

        assertThat(mail.appended).isEmpty();
        assertThat(scheduler.asyncTasks).isZero();
    }

    private void executeMsg(Player sender, String input) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(new MsgCommand(services(), new KeyMessages(), sink).build());
        try {
            dispatcher.execute(input, CommandSourceStackMock.from(sender));
        } catch (CommandSyntaxException e) {
            throw new AssertionError("command did not parse: " + input, e);
        }
    }

    private void executeMail(CommandSourceStack source, String input) {
        try {
            executeMailRaw(source, input);
        } catch (CommandSyntaxException e) {
            throw new AssertionError("command did not parse: " + input, e);
        }
    }

    private void executeMailRaw(CommandSourceStack source, String input) throws CommandSyntaxException {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(new MailCommand(services(), new KeyMessages(), sink, guiViews()).build());
        dispatcher.execute(input, source);
    }

    /**
     * The GUIs back the bare {@code /mail} branch, which this command-path test never dispatches (it exercises
     * the {@code sendall} subcommand only). Built over the same mail store and a deny-all permission seam so the
     * instance is real but inert here.
     */
    private MessagingGuiViews guiViews() {
        var plugin = MockBukkit.createMockPlugin();
        var guiText = new com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText(new KeyMessages());
        var textInput = com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInputTestKit.create(
                plugin, guiText, scheduler, Path.of("nonexistent"), NO_LOG);
        var layouts = new com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts(guiDir, NO_LOG);
        var bindings = new com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings();
        var itemRenderer = new com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer(
                guiText, bindings.placeholders());
        var renderer = new com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer(
                itemRenderer, bindings.conditions());
        var menus = new com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus(
                renderer, scheduler, bindings.lists());
        return MessagingGuiViews.create(
                guiText,
                scheduler,
                new KeyMessages(),
                DENY_ALL,
                services(),
                new AcceptingToggles(),
                new InMemorySocialSpyStore(),
                new NoIgnores(),
                mail,
                new CapturingLookup(server),
                textInput,
                layouts,
                menus,
                bindings,
                guiDir,
                NO_LOG);
    }

    /** A deny-all permission seam: the GUIs built here are inert, so nothing depends on a real check. */
    private static final Permissions DENY_ALL = new Permissions() {
        @Override
        public boolean has(PlayerRef who, String node) {
            return false;
        }

        @Override
        public QuotaResult resolveQuota(
                PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
            return QuotaResult.limited(configDefault);
        }
    };

    private static final com.uxplima.uxmessentials.shared.application.port.Logger NO_LOG =
            new com.uxplima.uxmessentials.shared.application.port.Logger() {
                @Override
                public void info(String message, Object... args) {}

                @Override
                public void warn(String message, Object... args) {}

                @Override
                public void error(String message, Throwable throwable) {}

                @Override
                public void debug(String message, Object... args) {}
            };

    private MessagingServices services() {
        Messages messages = new KeyMessages();
        Notifier notifier = new Notifier(messages, sink);
        MessageDelivery delivery = new BukkitMessageDelivery(messages, sink);
        MutePolicy mute = MutePolicy.NEVER;
        PlayerLookup players = new CapturingLookup(server);
        ConversationStore conversations = new InMemoryConversationStore();
        ReplyRoutingStore replies = new AcceptingReplies();
        IgnoreStore ignores = new NoIgnores();
        InMemorySocialSpyStore spies = new InMemorySocialSpyStore();
        DomainEventPublisher events = new NoEvents();
        Clock clock = Clock.fixed(T0, ZoneOffset.UTC);
        SendMessage sendMessage = new SendMessage(
                delivery,
                ignores,
                conversations,
                new AcceptingToggles(),
                spies,
                mute,
                afk,
                mail,
                offlineToMail,
                notifier,
                events,
                clock);
        return new MessagingServices(
                sendMessage,
                new Reply(sendMessage, conversations, players, vanish, replies, notifier, Duration.ofMinutes(5), clock),
                new SendMail(mail, ignores, delivery, mute, notifier, events, clock),
                new SendMailToAll(mail, clock),
                new ReadMail(mail, delivery, notifier),
                new ClearMail(mail, notifier),
                new MsgToggle(new AcceptingToggles(), notifier),
                new ReplyToggle(replies, notifier),
                new Ignore(ignores, notifier),
                new Unignore(ignores, notifier),
                new ListIgnores(ignores, notifier),
                new SocialSpy(spies, notifier),
                new HelpOp(new BukkitStaffAudience(), delivery, mute, notifier, events, clock),
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
            // Exactly one played-before offline profile, so the offline → mail path is exercised deterministically.
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

    /** An in-memory mail repository capturing every appended item. */
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

    private static final class NoIgnores implements IgnoreStore {
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

    /** Resolves a key to its own string and records it so a path's outcome is asserted by the key it produced. */
    private final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            sink.keys.add(key);
            return key.key();
        }
    }

    /** Records each delivered key for assertions; the rendered text is the key string (see KeyMessages). */
    private static final class RecordingSink implements MessageSink {
        private final List<MessageKey> keys = new ArrayList<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            // The key list (recorded by KeyMessages.resolve) is what the tests assert on.
        }
    }

    /** Runs async work inline (counting it) so the off-tick sendall fan-out is observable synchronously. */
    private static final class CountingScheduler implements Scheduler {
        private int asyncTasks;

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
            asyncTasks++;
            task.run();
        }

        @Override
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }
    }
}

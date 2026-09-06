package com.uxplima.uxmessentials.communication.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.event.player.PlayerJoinEvent;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.mojang.brigadier.CommandDispatcher;
import com.uxplima.uxmessentials.communication.adapter.inbound.command.CommunicationCommands;
import com.uxplima.uxmessentials.communication.adapter.inbound.command.InfoPageCommand;
import com.uxplima.uxmessentials.communication.adapter.inbound.listener.ConnectionMessageListener;
import com.uxplima.uxmessentials.communication.adapter.outbound.AnnouncerTask;
import com.uxplima.uxmessentials.communication.adapter.outbound.AtomicSequenceCounter;
import com.uxplima.uxmessentials.communication.adapter.outbound.BukkitAnnouncerBroadcaster;
import com.uxplima.uxmessentials.communication.adapter.outbound.BukkitInfoSender;
import com.uxplima.uxmessentials.communication.adapter.outbound.ChatMetaSource;
import com.uxplima.uxmessentials.communication.adapter.outbound.PdcBroadcastOptOutStore;
import com.uxplima.uxmessentials.communication.adapter.outbound.ThreadLocalRandomSource;
import com.uxplima.uxmessentials.communication.application.BroadcastOptOut;
import com.uxplima.uxmessentials.communication.application.CommunicationMessageKey;
import com.uxplima.uxmessentials.communication.application.InfoRegistry;
import com.uxplima.uxmessentials.communication.application.NextAnnouncement;
import com.uxplima.uxmessentials.communication.application.ResolveConnectionMessage;
import com.uxplima.uxmessentials.communication.application.ResolveJoinMessage;
import com.uxplima.uxmessentials.communication.application.ResolveQuitMessage;
import com.uxplima.uxmessentials.communication.application.port.BroadcastOptOutStore;
import com.uxplima.uxmessentials.communication.domain.InfoPage;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.ChannelBroadcaster;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.display.ConditionContext;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the communication adapter against a real (mock) Bukkit server, end-to-end through the
 * split {@code modules/communication} content the {@link CommunicationContentCodec} parses: the
 * {@link ConnectionMessageListener} overriding a join message per a {@code CUSTOM} policy, an auto-registered info
 * command ({@code /rules}) printing the configured page through the real Brigadier node, and
 * {@code /broadcasttoggle} flipping the PDC-backed opt-out and confirming with the plugin's own
 * {@link CommunicationMessageKey}. The message sink records what each path delivered; the info commands are built
 * from the config-derived {@link InfoRegistry} exactly as the wiring builds them.
 */
class CommunicationAdapterTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private ServerMock server;
    private PlayerMock player;
    private RecordingSink sink;
    private CommunicationSettings settings;
    private BroadcastOptOutStore optOutStore;

    @BeforeEach
    void setUp(@TempDir Path dataDir) throws Exception {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        player = server.addPlayer("Alice");
        player.setOp(true);
        sink = new RecordingSink();
        settings = new CommunicationSettings(writeContent(dataDir), new NoopLogger());
        // One store over one mock plugin so the toggle write and the assertion read the same PDC namespace.
        optOutStore = new PdcBroadcastOptOutStore(MockBukkit.createMockPlugin());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void theJoinListenerReplacesTheVanillaLineWithTheCustomTemplate() {
        ResolveConnectionMessage engine = new ResolveConnectionMessage(new AtomicSequenceCounter(), random());
        ConnectionMessageListener listener = new ConnectionMessageListener(
                new ResolveJoinMessage(engine, settings::joinPolicies, settings::firstJoinPolicy),
                new ResolveQuitMessage(engine, settings::quitPolicy),
                settings,
                new BukkitInfoSender(sink),
                ChatMetaSource.empty());
        PlayerJoinEvent join = new PlayerJoinEvent(player, Component.text("Alice joined the game"));

        listener.onJoin(join);

        Component overridden = join.joinMessage();
        assertThat(overridden).isNotNull();
        assertThat(PLAIN.serialize(overridden)).isEqualTo("welcome Alice"); // {player} substituted, vanilla replaced
    }

    @Test
    void theJoinListenerSendsTheMotdPageWhenMotdOnJoinIsOn() {
        // motd-on-join defaults true and the test content ships a one-line motd, so a join delivers that line to the
        // joiner through the info sender. {player} is substituted per viewer.
        ConnectionMessageListener listener = joinListener();
        PlayerJoinEvent join = new PlayerJoinEvent(player, Component.text("Alice joined the game"));

        listener.onJoin(join);

        assertThat(sink.lines).containsExactly("Welcome Alice");
    }

    @Test
    void theJoinListenerSendsNoMotdWhenMotdOnJoinIsOff(@TempDir Path offDir) throws Exception {
        // With motd-on-join = false the joiner is sent nothing, only the join broadcast (set on the event) fires.
        Path dir = offDir.resolve("modules").resolve("communication");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("info-pages.conf"), """
                motd-on-join = false
                info-pages { motd = [ "Welcome {player}" ] }
                """);
        CommunicationSettings offSettings = new CommunicationSettings(dir, new NoopLogger());
        ResolveConnectionMessage engine = new ResolveConnectionMessage(new AtomicSequenceCounter(), random());
        ConnectionMessageListener listener = new ConnectionMessageListener(
                new ResolveJoinMessage(engine, offSettings::joinPolicies, offSettings::firstJoinPolicy),
                new ResolveQuitMessage(engine, offSettings::quitPolicy),
                offSettings,
                new BukkitInfoSender(sink),
                ChatMetaSource.empty());
        PlayerJoinEvent join = new PlayerJoinEvent(player, Component.text("Alice joined the game"));

        listener.onJoin(join);

        assertThat(sink.lines).isEmpty();
    }

    @Test
    void theJoinListenerSendsTheDedicatedMotdListToTheJoiner(@TempDir Path motdDir) throws Exception {
        // A dedicated motd list is delivered to the joiner (and takes precedence over the legacy info-page motd).
        Path dir = motdDir.resolve("modules").resolve("communication");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("join-quit.conf"), """
                join { mode = DEFAULT }
                quit { mode = DEFAULT }
                death { mode = DEFAULT }
                motd = [ "Hi {player}", "Enjoy your stay" ]
                """);
        CommunicationSettings motdSettings = new CommunicationSettings(dir, new NoopLogger());
        ResolveConnectionMessage engine = new ResolveConnectionMessage(new AtomicSequenceCounter(), random());
        ConnectionMessageListener listener = new ConnectionMessageListener(
                new ResolveJoinMessage(engine, motdSettings::joinPolicies, motdSettings::firstJoinPolicy),
                new ResolveQuitMessage(engine, motdSettings::quitPolicy),
                motdSettings,
                new BukkitInfoSender(sink),
                ChatMetaSource.empty());
        PlayerJoinEvent join = new PlayerJoinEvent(player, Component.text("Alice joined the game"));

        listener.onJoin(join);

        assertThat(sink.lines).containsExactly("Hi Alice", "Enjoy your stay");
    }

    private ConnectionMessageListener joinListener() {
        ResolveConnectionMessage engine = new ResolveConnectionMessage(new AtomicSequenceCounter(), random());
        return new ConnectionMessageListener(
                new ResolveJoinMessage(engine, settings::joinPolicies, settings::firstJoinPolicy),
                new ResolveQuitMessage(engine, settings::quitPolicy),
                settings,
                new BukkitInfoSender(sink),
                ChatMetaSource.empty());
    }

    @Test
    void theAutoRegisteredRulesCommandPrintsTheConfiguredPage() {
        CommandDispatcher<CommandSourceStack> dispatcher = registerCommands();

        execute(dispatcher, "rules");

        // The /rules command was auto-registered from the info-pages map and printed both operator lines verbatim.
        assertThat(sink.lines).containsExactly("Rule one", "Rule two");
    }

    @Test
    void aMultiPageInfoCommandDrawsTheHeaderAndFooterChromeAroundTheFirstPageSlice() {
        InfoPageCommand info = new InfoPageCommand(
                "info",
                InfoRegistry.of(List.of(InfoPage.of("info", List.of("a", "b", "c", "d", "e"), 2))),
                new BukkitInfoSender(sink),
                new Notifier(sink, sink),
                sink);
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(info.build());

        execute(dispatcher, "info");

        // Page one of a three-page body: the header is drawn first, then the first two body lines, then the footer.
        // The double resolves a MessageKey to its key string and delivers it, so the chrome appears in lines too
        // assert the full interleaved order, which pins header → body slice → footer.
        assertThat(sink.lines)
                .containsExactly(
                        CommunicationMessageKey.INFO_PAGE_HEADER.key(),
                        "a",
                        "b",
                        CommunicationMessageKey.INFO_PAGE_FOOTER.key());
        assertThat(sink.keys)
                .containsExactly(CommunicationMessageKey.INFO_PAGE_HEADER, CommunicationMessageKey.INFO_PAGE_FOOTER);
    }

    @Test
    void theLastPageOfAnInfoCommandDrawsAHeaderButNoNextPageFooter() {
        InfoPageCommand info = new InfoPageCommand(
                "info",
                InfoRegistry.of(List.of(InfoPage.of("info", List.of("a", "b", "c", "d", "e"), 2))),
                new BukkitInfoSender(sink),
                new Notifier(sink, sink),
                sink);
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(info.build());

        execute(dispatcher, "info 3");

        // The final page shows the header but no "next page" footer; the slice is the single trailing line.
        assertThat(sink.lines).containsExactly(CommunicationMessageKey.INFO_PAGE_HEADER.key(), "e");
        assertThat(sink.keys).containsExactly(CommunicationMessageKey.INFO_PAGE_HEADER);
    }

    @Test
    void aSinglePageInfoCommandPrintsTheBodyWithNoPaginationChrome() {
        InfoPageCommand info = new InfoPageCommand(
                "info",
                InfoRegistry.of(List.of(InfoPage.of("info", List.of("one", "two"), 8))),
                new BukkitInfoSender(sink),
                new Notifier(sink, sink),
                sink);
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(info.build());

        execute(dispatcher, "info");

        // A body that fits in one page is printed verbatim with no header or footer keys.
        assertThat(sink.lines).containsExactly("one", "two");
        assertThat(sink.keys).isEmpty();
    }

    @Test
    void broadcastToggleFlipsTheOptOutBitAndConfirmsWithThePluginString() {
        CommandDispatcher<CommandSourceStack> dispatcher = registerCommands();
        assertThat(optOutStore.receivesBroadcasts(ref())).isTrue(); // opted in by default

        execute(dispatcher, "broadcasttoggle"); // opt out

        assertThat(optOutStore.receivesBroadcasts(ref())).isFalse();
        assertThat(sink.keys).containsExactly(CommunicationMessageKey.BROADCAST_TOGGLE_OFF);
    }

    @Test
    void broadcastToggleTwiceOptsBackIn() {
        CommandDispatcher<CommandSourceStack> dispatcher = registerCommands();

        execute(dispatcher, "broadcasttoggle"); // out
        execute(dispatcher, "broadcasttoggle"); // back in

        assertThat(optOutStore.receivesBroadcasts(ref())).isTrue();
        assertThat(sink.keys)
                .containsExactly(
                        CommunicationMessageKey.BROADCAST_TOGGLE_OFF, CommunicationMessageKey.BROADCAST_TOGGLE_ON);
    }

    @Test
    void theInfoRegistryAutoRegistersExactlyTheConfiguredPagesPlusBroadcastToggle() {
        List<String> literals = new ArrayList<>();
        for (CommandRegistration command : commands()) {
            literals.add(command.build().getLiteral());
        }
        assertThat(literals)
                .containsExactlyInAnyOrder(
                        "broadcast",
                        "broadcastworld",
                        "broadcasttoggle",
                        "announce",
                        "me",
                        "clearchat",
                        "togglechat",
                        "rules",
                        "motd");
    }

    @Test
    void broadcastIsListedInTheCommandSurface() {
        List<String> literals = new ArrayList<>();
        for (CommandRegistration command : commands()) {
            literals.add(command.build().getLiteral());
        }
        assertThat(literals).contains("broadcast");
    }

    @Test
    void broadcastDeliversTheMessageToOnlinePlayersAsOperatorContent() {
        CommandDispatcher<CommandSourceStack> dispatcher = registerCommands();

        execute(dispatcher, "broadcast hello world");

        // The operator-authored body lands in the sink with the configured prefix prepended, never as a MessageKey.
        assertThat(sink.lines).hasSize(1);
        assertThat(sink.lines.get(0)).endsWith("hello world");
        assertThat(sink.keys).isEmpty();
    }

    @Test
    void meCommandBroadcastsActionToAllOnlinePlayers() {
        PlayerMock bob = server.addPlayer("Bob");
        CommandDispatcher<CommandSourceStack> dispatcher = registerCommands();

        execute(dispatcher, "me waves");

        // The action lands on every online player through the parity-checked ME key, never as raw operator content.
        assertThat(sink.keys).containsExactly(CommunicationMessageKey.ME, CommunicationMessageKey.ME);
        assertThat(bob.getName()).isNotBlank();
    }

    private CommandDispatcher<CommandSourceStack> registerCommands() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        for (CommandRegistration command : commands()) {
            dispatcher.getRoot().addChild(command.build());
        }
        return dispatcher;
    }

    private final InMemoryAnnouncerSettingsStore announcerSettingsStore = new InMemoryAnnouncerSettingsStore();
    private final InMemoryAnnouncementStore commandAnnouncementStore = new InMemoryAnnouncementStore();

    private List<CommandRegistration> commands() {
        Notifier notifier = new Notifier(sink, sink);
        BroadcastOptOut optOut = new BroadcastOptOut(optOutStore, notifier, new NoEvents(), Clock.systemUTC());
        InfoRegistry registry = settings.infoRegistry();
        com.uxplima.uxmessentials.communication.application.MergeAnnouncements merge =
                new com.uxplima.uxmessentials.communication.application.MergeAnnouncements(
                        commandAnnouncementStore, announcerSettingsStore);
        java.util.function.Supplier<com.uxplima.uxmessentials.communication.domain.AnnouncerConfig> mergedConfig =
                () -> merge.merge(settings.announcerConfig());
        BukkitAnnouncerBroadcaster broadcaster = announcerBroadcaster();
        Scheduler scheduler = new SyncScheduler();
        AnnouncerTask announcer = new AnnouncerTask(
                scheduler,
                new NextAnnouncement(() -> settings.announcerConfig().rotating(), random()),
                broadcaster,
                settings::announcerConfig,
                () -> true);
        return CommunicationCommands.all(
                optOut,
                registry,
                new BukkitInfoSender(sink),
                notifier,
                sink,
                broadcaster,
                announcer,
                mergedConfig,
                scheduler,
                sink,
                new com.uxplima.uxmessentials.communication.adapter.ChatLock(),
                settings,
                editorView(scheduler));
    }

    /** A real editor view over the in-memory stores; the command tests do not open it, only build the command tree. */
    private com.uxplima.uxmessentials.communication.adapter.inbound.gui.AnnouncementEditorView editorView(
            Scheduler scheduler) {
        com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText guiText =
                new com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText(sink);
        return new com.uxplima.uxmessentials.communication.adapter.inbound.gui.AnnouncementEditorView(
                editorEngine(guiText, scheduler),
                guiText,
                scheduler,
                sink,
                commandAnnouncementStore,
                announcerSettingsStore,
                new com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts(
                        java.nio.file.Path.of("."), new NoopLogger()),
                com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInputTestKit.create(
                        MockBukkit.createMockPlugin(),
                        guiText,
                        scheduler,
                        java.nio.file.Path.of("nonexistent"),
                        new NoopLogger()));
    }

    /** A minimal editor-capable engine for the editor view; the command tests never open it, so it is inert here. */
    private com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus editorEngine(
            com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText guiText, Scheduler scheduler) {
        com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings bindings =
                new com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings();
        com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer renderer =
                new com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer(
                        new com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ItemRenderer(
                                guiText, bindings.placeholders()),
                        bindings.conditions());
        com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.EditorRenderer editorRenderer =
                new com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.EditorRenderer(guiText);
        return new com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus(
                renderer, scheduler, bindings.lists(), editorRenderer);
    }

    private BukkitAnnouncerBroadcaster announcerBroadcaster() {
        ChannelBroadcaster channels = new ChannelBroadcaster(new SyncScheduler(), settings.announcerDisplay());
        return new BukkitAnnouncerBroadcaster(sink, optOutStore, channels, this::conditionContext, new SyncScheduler());
    }

    private ConditionContext conditionContext(org.bukkit.entity.Player who) {
        return new ConditionContext(
                who::hasPermission,
                who.getWorld().getName(),
                who.getGameMode().name(),
                java.util.function.UnaryOperator.identity());
    }

    private com.uxplima.uxmessentials.communication.application.port.RandomSource random() {
        return new ThreadLocalRandomSource();
    }

    private PlayerRef ref() {
        return new PlayerRef(player.getUniqueId(), player.getName());
    }

    private Path writeContent(Path dataDir) throws Exception {
        Path dir = dataDir.resolve("modules").resolve("communication");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("join-quit.conf"), """
                join { mode = CUSTOM, ordering = SEQUENTIAL, templates = [ "welcome {player}" ] }
                quit { mode = DEFAULT }
                death { mode = DEFAULT }
                """);
        Files.writeString(
                dir.resolve("announcer.conf"),
                "announcer { interval-seconds = 60, min-players = 0, ordering = SEQUENTIAL, lines = [] }\n");
        Files.writeString(dir.resolve("info-pages.conf"), """
                info-pages {
                  rules = [ "Rule one", "Rule two" ]
                  motd = [ "Welcome {player}" ]
                }
                """);
        return dir;
    }

    private void execute(CommandDispatcher<CommandSourceStack> dispatcher, String input) {
        try {
            dispatcher.execute(input, CommandSourceStackMock.from(player));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new AssertionError("command did not parse: " + input, e);
        }
    }

    /**
     * Doubles as both ports: {@link Messages#resolve} records the {@link MessageKey} (and echoes its key as the
     * resolved string) so a delivered plugin string is observable, and {@link MessageSink#deliver} records the
     * raw operator line (info-page body, announcer line). The plugin's own confirmations therefore land in
     * {@code keys} while operator content lands in {@code lines}.
     */
    private static final class InMemoryAnnouncementStore
            implements com.uxplima.uxmessentials.communication.application.port.AnnouncementStore {
        private final Map<String, com.uxplima.uxmessentials.communication.domain.StoredAnnouncement> rows =
                new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public List<com.uxplima.uxmessentials.communication.domain.StoredAnnouncement> all() {
            return List.copyOf(rows.values());
        }

        @Override
        public List<com.uxplima.uxmessentials.communication.domain.StoredAnnouncement> enabled() {
            return all().stream()
                    .filter(com.uxplima.uxmessentials.communication.domain.StoredAnnouncement::enabled)
                    .toList();
        }

        @Override
        public java.util.Optional<com.uxplima.uxmessentials.communication.domain.StoredAnnouncement> find(String id) {
            return java.util.Optional.ofNullable(rows.get(id));
        }

        @Override
        public boolean exists(String id) {
            return rows.containsKey(id);
        }

        @Override
        public void save(com.uxplima.uxmessentials.communication.domain.StoredAnnouncement announcement) {
            rows.put(announcement.id(), announcement);
        }

        @Override
        public boolean delete(String id) {
            return rows.remove(id) != null;
        }
    }

    private static final class InMemoryAnnouncerSettingsStore
            implements com.uxplima.uxmessentials.communication.application.port.AnnouncerSettingsStore {
        private com.uxplima.uxmessentials.communication.domain.AnnouncerSettings settings =
                com.uxplima.uxmessentials.communication.domain.AnnouncerSettings.unset();

        @Override
        public com.uxplima.uxmessentials.communication.domain.AnnouncerSettings load() {
            return settings;
        }

        @Override
        public void save(com.uxplima.uxmessentials.communication.domain.AnnouncerSettings value) {
            settings = value;
        }
    }

    private static final class RecordingSink implements MessageSink, Messages {
        private final List<String> lines = new ArrayList<>();
        private final List<MessageKey> keys = new ArrayList<>();

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            lines.add(renderedText);
        }

        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            keys.add(key);
            return key.key();
        }
    }

    private static final class NoEvents implements DomainEventPublisher {
        @Override
        public void publish(DomainEvent event) {
            // discarded: event bridging is not under test here
        }
    }

    private static final class NoopLogger implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }

    /** Runs every hop inline so the entity-thread and async-after work executes within the test. */
    private static final class SyncScheduler implements Scheduler {
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
        public void asyncAfter(java.time.Duration delay, Runnable task) {
            task.run();
        }
    }
}

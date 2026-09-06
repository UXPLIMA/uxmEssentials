package com.uxplima.uxmessentials.communication.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.uxplima.uxmessentials.communication.adapter.CommunicationSettings;
import com.uxplima.uxmessentials.communication.adapter.inbound.gui.AnnouncementEditorView;
import com.uxplima.uxmessentials.communication.adapter.outbound.AnnouncerTask;
import com.uxplima.uxmessentials.communication.adapter.outbound.BukkitAnnouncerBroadcaster;
import com.uxplima.uxmessentials.communication.adapter.outbound.PdcBroadcastOptOutStore;
import com.uxplima.uxmessentials.communication.application.BroadcastOptOut;
import com.uxplima.uxmessentials.communication.application.CommunicationMessageKey;
import com.uxplima.uxmessentials.communication.application.NextAnnouncement;
import com.uxplima.uxmessentials.communication.application.port.BroadcastOptOutStore;
import com.uxplima.uxmessentials.communication.application.port.RandomSource;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.ChannelBroadcaster;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.ChannelDisplay;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Logger;
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
 * MockBukkit coverage of the {@code /announce} command through the real Brigadier node and
 * {@link CommunicationSettings}: {@code reload} re-reads the config and confirms with the announcement count,
 * {@code list} prints a header and one entry per announcement, {@code preview <id>} shows the announcement to the
 * sender (and an unknown id answers with the not-found key), and the whole tree is gated by the admin permission.
 * The {@link Messages} double echoes each key so the plugin's own framing is observable in the sender's chat.
 */
class AnnounceCommandTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private ServerMock server;
    private PlayerMock alice;
    private Path moduleDir;
    private CommunicationSettings settings;
    private BukkitAnnouncerBroadcaster broadcaster;
    private BroadcastOptOutStore optOutStore;
    private RecordingScheduler scheduler;
    private InMemoryAnnouncementStore announcementStore;
    private InMemoryAnnouncerSettingsStore settingsStore;
    private java.util.function.Supplier<com.uxplima.uxmessentials.communication.domain.AnnouncerConfig> mergedConfig;

    @BeforeEach
    void setUp(@TempDir Path dataDir) throws Exception {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");
        alice = server.addPlayer("Alice");
        alice.setOp(true);
        moduleDir = writeAnnouncer(dataDir, "ann-one", "<gold>first");
        settings = new CommunicationSettings(moduleDir, new NoopLogger());
        optOutStore = new PdcBroadcastOptOutStore(MockBukkit.createMockPlugin());
        ChannelBroadcaster channels = new ChannelBroadcaster(new SyncScheduler(), display());
        broadcaster = new BukkitAnnouncerBroadcaster(
                new EchoMessagesSink(), optOutStore, channels, AnnounceCommandTest::context, new SyncScheduler());
        scheduler = new RecordingScheduler();
        announcementStore = new InMemoryAnnouncementStore();
        settingsStore = new InMemoryAnnouncerSettingsStore();
        com.uxplima.uxmessentials.communication.application.MergeAnnouncements merge =
                new com.uxplima.uxmessentials.communication.application.MergeAnnouncements(
                        announcementStore, settingsStore);
        mergedConfig = () -> merge.merge(settings.announcerConfig());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void reloadReReadsTheConfigAndConfirmsTheCount() throws Exception {
        CommandDispatcher<CommandSourceStack> dispatcher = dispatcher();
        // Rewrite the file with two announcements, then reload.
        Files.writeString(moduleDir.resolve("announcer.conf"), """
                announcer {
                  default-interval-seconds = 100
                  announcements = [
                    { id = "a", channels = [ "CHAT" ], lines = [ "one" ] }
                    { id = "b", channels = [ "CHAT" ], lines = [ "two" ] }
                  ]
                }
                """);

        execute(dispatcher, "announce reload");

        assertThat(settings.announcerConfig().announcementCount()).isEqualTo(2);
        assertThat(PLAIN.serialize(alice.nextComponentMessage()))
                .contains(CommunicationMessageKey.ANNOUNCER_RELOADED.key());
    }

    @Test
    void reloadRunsOffTickAndReArmsTheOverrideLoops() throws Exception {
        CommandDispatcher<CommandSourceStack> dispatcher = dispatcher();
        // A reload that newly gives an announcement an interval-seconds override: it leaves the shared rotation, so
        // a fresh override loop must be armed or it would never broadcast again.
        Files.writeString(moduleDir.resolve("announcer.conf"), """
                announcer {
                  announcements = [
                    { id = "timed", channels = [ "CHAT" ], lines = [ "tick" ], interval-seconds = 30 }
                  ]
                }
                """);

        execute(dispatcher, "announce reload");

        // The config I/O ran off the tick thread, then re-armed an override loop (one asyncAfter for "timed").
        assertThat(scheduler.asyncCalls()).isGreaterThanOrEqualTo(1);
        assertThat(scheduler.asyncAfterCalls()).isGreaterThanOrEqualTo(1);
        assertThat(settings.announcerConfig().announcementCount()).isEqualTo(1);
        assertThat(PLAIN.serialize(alice.nextComponentMessage()))
                .contains(CommunicationMessageKey.ANNOUNCER_RELOADED.key());
    }

    @Test
    void listPrintsAHeaderAndOneEntryPerAnnouncement() {
        CommandDispatcher<CommandSourceStack> dispatcher = dispatcher();

        execute(dispatcher, "announce list");

        assertThat(PLAIN.serialize(alice.nextComponentMessage()))
                .contains(CommunicationMessageKey.ANNOUNCE_LIST_HEADER.key());
        assertThat(PLAIN.serialize(alice.nextComponentMessage()))
                .contains(CommunicationMessageKey.ANNOUNCE_LIST_ENTRY.key());
    }

    @Test
    void previewShowsTheAnnouncementToTheSender() {
        CommandDispatcher<CommandSourceStack> dispatcher = dispatcher();

        execute(dispatcher, "announce preview ann-one");

        assertThat(PLAIN.serialize(alice.nextComponentMessage())).isEqualTo("first");
    }

    @Test
    void previewOfAnUnknownIdAnswersWithTheNotFoundKey() {
        CommandDispatcher<CommandSourceStack> dispatcher = dispatcher();

        execute(dispatcher, "announce preview nope");

        assertThat(PLAIN.serialize(alice.nextComponentMessage()))
                .contains(CommunicationMessageKey.ANNOUNCE_PREVIEW_UNKNOWN.key());
    }

    @Test
    void listShowsAnEditorCreatedStoreAnnouncement() {
        // An announcement created in the GUI (enabled) lives only in the store, not announcer.conf. list reads the
        // merged set, so it appears alongside the file announcement rather than being missed.
        announcementStore.save(
                com.uxplima.uxmessentials.communication.domain.StoredAnnouncement.fresh("deneme", "<gold>gui line"));
        CommandDispatcher<CommandSourceStack> dispatcher = dispatcher();

        execute(dispatcher, "announce list");

        assertThat(PLAIN.serialize(alice.nextComponentMessage()))
                .contains(CommunicationMessageKey.ANNOUNCE_LIST_HEADER.key());
        java.util.List<String> entries = java.util.List.of(
                PLAIN.serialize(alice.nextComponentMessage()), PLAIN.serialize(alice.nextComponentMessage()));
        assertThat(entries).allMatch(line -> line.contains(CommunicationMessageKey.ANNOUNCE_LIST_ENTRY.key()));
    }

    @Test
    void previewResolvesAnEditorCreatedStoreAnnouncementById() {
        announcementStore.save(
                com.uxplima.uxmessentials.communication.domain.StoredAnnouncement.fresh("deneme", "gui line"));
        CommandDispatcher<CommandSourceStack> dispatcher = dispatcher();

        execute(dispatcher, "announce preview deneme");

        // The store announcement previews (delivered to the sender), not answered with the unknown-id key.
        assertThat(PLAIN.serialize(alice.nextComponentMessage())).isEqualTo("gui line");
    }

    @Test
    void previewOfADisabledStoreAnnouncementIsUnknown() {
        announcementStore.save(com.uxplima.uxmessentials.communication.domain.StoredAnnouncement.fresh("off", "hidden")
                .withEnabled(false));
        CommandDispatcher<CommandSourceStack> dispatcher = dispatcher();

        execute(dispatcher, "announce preview off");

        assertThat(PLAIN.serialize(alice.nextComponentMessage()))
                .contains(CommunicationMessageKey.ANNOUNCE_PREVIEW_UNKNOWN.key());
    }

    @Test
    void theTreeIsGatedByTheAdminPermission() {
        alice.setOp(false);
        CommandDispatcher<CommandSourceStack> dispatcher = dispatcher();

        // Without the node the requires() predicate hides the literal, so parsing fails rather than running.
        assertThat(canParse(dispatcher, "announce list")).isFalse();
    }

    @Test
    void theEditorSubcommandParsesAndTheBareRootInstallsTheOpener() {
        CommandDispatcher<CommandSourceStack> dispatcher = dispatcher();
        AnnounceCommand command = new AnnounceCommand(
                settings,
                mergedConfig,
                broadcaster,
                new BroadcastOptOut(
                        optOutStore,
                        new Notifier(new EchoMessagesSink(), new EchoMessagesSink()),
                        new NoEvents(),
                        Clock.systemUTC()),
                new AnnouncerTask(
                        scheduler,
                        new NextAnnouncement(() -> settings.announcerConfig().rotating(), new ZeroRandom()),
                        broadcaster,
                        settings::announcerConfig,
                        () -> true),
                editorView(),
                scheduler,
                new EchoMessagesSink());

        // /announce editor parses (the subcommand keeps the reload/list/preview/toggle tree intact alongside it).
        assertThat(canParse(dispatcher, "announce editor")).isTrue();
        // The bare /announce gains the editor opener via guiRoot(): the GuiRootBinding installs it on the root.
        assertThat(command.guiRoot()).isPresent();
    }

    private CommandDispatcher<CommandSourceStack> dispatcher() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        Notifier notifier = new Notifier(new EchoMessagesSink(), new EchoMessagesSink());
        BroadcastOptOut optOut = new BroadcastOptOut(optOutStore, notifier, new NoEvents(), Clock.systemUTC());
        AnnouncerTask announcer = new AnnouncerTask(
                scheduler,
                new NextAnnouncement(() -> settings.announcerConfig().rotating(), new ZeroRandom()),
                broadcaster,
                settings::announcerConfig,
                () -> true);
        AnnounceCommand command = new AnnounceCommand(
                settings,
                mergedConfig,
                broadcaster,
                optOut,
                announcer,
                editorView(),
                scheduler,
                new EchoMessagesSink());
        dispatcher.getRoot().addChild(command.build());
        return dispatcher;
    }

    /** A real editor view over the in-memory stores; the subcommand tests never open it, only the editor subcommand. */
    private AnnouncementEditorView editorView() {
        EchoMessagesSink messages = new EchoMessagesSink();
        com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText guiText =
                new com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText(messages);
        return new AnnouncementEditorView(
                editorEngine(guiText),
                guiText,
                scheduler,
                messages,
                announcementStore,
                settingsStore,
                new com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts(moduleDir, new NoopLogger()),
                com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInputTestKit.create(
                        MockBukkit.createMockPlugin(),
                        guiText,
                        scheduler,
                        java.nio.file.Path.of("nonexistent"),
                        new NoopLogger()));
    }

    /** A minimal editor-capable engine for the editor view; the subcommand tests never open it, so it is inert here. */
    private com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus editorEngine(
            com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText guiText) {
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

    private void execute(CommandDispatcher<CommandSourceStack> dispatcher, String input) {
        try {
            dispatcher.execute(input, CommandSourceStackMock.from(alice));
        } catch (CommandSyntaxException e) {
            throw new AssertionError("command did not parse: " + input, e);
        }
    }

    private boolean canParse(CommandDispatcher<CommandSourceStack> dispatcher, String input) {
        return !dispatcher
                .parse(input, CommandSourceStackMock.from(alice))
                .getReader()
                .canRead();
    }

    private static ConditionContext context(Player player) {
        return new ConditionContext(
                player::hasPermission,
                player.getWorld().getName(),
                player.getGameMode().name(),
                java.util.function.UnaryOperator.identity());
    }

    private static Path writeAnnouncer(Path dataDir, String id, String line) throws Exception {
        Path dir = dataDir.resolve("modules").resolve("communication");
        Files.createDirectories(dir);
        Files.writeString(
                dir.resolve("announcer.conf"),
                "announcer { announcements = [ { id = \"" + id + "\", channels = [ \"CHAT\" ], lines = [ \"" + line
                        + "\" ] } ] }\n");
        return dir;
    }

    private static ChannelDisplay display() {
        return new ChannelDisplay(100, 500, 100, BossBar.Color.PURPLE, BossBar.Overlay.PROGRESS, 1);
    }

    /**
     * Doubles as {@link Messages} (echoing a key to its key string so the framing is observable) and
     * {@code MessageSink} (the legacy /broadcast path, unused here). Distinct instances are handed to the notifier
     * and the command; both just echo.
     */
    private static final class EchoMessagesSink
            implements Messages, com.uxplima.uxmessentials.shared.application.port.MessageSink {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            // unused: the /announce path never delivers a raw line through the sink
        }
    }

    private static final class NoEvents implements DomainEventPublisher {
        @Override
        public void publish(DomainEvent event) {
            // discarded
        }
    }

    private static final class InMemoryAnnouncementStore
            implements com.uxplima.uxmessentials.communication.application.port.AnnouncementStore {
        private final Map<String, com.uxplima.uxmessentials.communication.domain.StoredAnnouncement> rows =
                new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public java.util.List<com.uxplima.uxmessentials.communication.domain.StoredAnnouncement> all() {
            return java.util.List.copyOf(rows.values());
        }

        @Override
        public java.util.List<com.uxplima.uxmessentials.communication.domain.StoredAnnouncement> enabled() {
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

    private static final class ZeroRandom implements RandomSource {
        @Override
        public int nextBounded(int bound) {
            return 0;
        }
    }

    /**
     * Records how often {@code async} / {@code asyncAfter} are called so the reload test can prove the config I/O ran
     * off-tick and the override loops were re-armed, while running every task inline so the effect is observable.
     */
    private static final class RecordingScheduler implements Scheduler {
        private int asyncCalls;
        private int asyncAfterCalls;

        int asyncCalls() {
            return asyncCalls;
        }

        int asyncAfterCalls() {
            return asyncAfterCalls;
        }

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
            asyncCalls++;
            task.run();
        }

        @Override
        public void asyncAfter(java.time.Duration delay, Runnable task) {
            asyncAfterCalls++;
            // Do not run: an override loop's asyncAfter would reschedule itself forever. Recording the call is enough
            // to prove the loop was armed.
        }
    }

    /** Runs every hop inline so the preview delivery executes within the test. */
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

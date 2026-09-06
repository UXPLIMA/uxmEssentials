package com.uxplima.uxmessentials.vote.gui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.plugin.Plugin;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.CommandDispatcher;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.ListDisplayMode;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuHolder;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.display.BroadcastChannel;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.menu.TestMenuEngine;
import com.uxplima.uxmessentials.vote.adapter.VoteServices;
import com.uxplima.uxmessentials.vote.adapter.inbound.command.VoteCommand;
import com.uxplima.uxmessentials.vote.adapter.inbound.gui.VoteSitesMenu;
import com.uxplima.uxmessentials.vote.application.AddPartyCount;
import com.uxplima.uxmessentials.vote.application.ApplyQueuedRewards;
import com.uxplima.uxmessentials.vote.application.BroadcastSettings;
import com.uxplima.uxmessentials.vote.application.ForceParty;
import com.uxplima.uxmessentials.vote.application.GiveVote;
import com.uxplima.uxmessentials.vote.application.HandleVote;
import com.uxplima.uxmessentials.vote.application.PartyConfig;
import com.uxplima.uxmessentials.vote.application.ResetVoterTotals;
import com.uxplima.uxmessentials.vote.application.RewardEngine;
import com.uxplima.uxmessentials.vote.application.SetPartyCount;
import com.uxplima.uxmessentials.vote.application.ShowLastVote;
import com.uxplima.uxmessentials.vote.application.ShowNextVote;
import com.uxplima.uxmessentials.vote.application.ShowVoteStreak;
import com.uxplima.uxmessentials.vote.application.ShowVoteTotals;
import com.uxplima.uxmessentials.vote.application.TopVoters;
import com.uxplima.uxmessentials.vote.application.VoteLinks;
import com.uxplima.uxmessentials.vote.application.VoteMessageKey;
import com.uxplima.uxmessentials.vote.application.VotePartyStatus;
import com.uxplima.uxmessentials.vote.application.VoteReminderEligibility;
import com.uxplima.uxmessentials.vote.application.port.BroadcastThrottle;
import com.uxplima.uxmessentials.vote.application.port.BroadcastVisibility;
import com.uxplima.uxmessentials.vote.application.port.ReminderPreferences;
import com.uxplima.uxmessentials.vote.application.port.RewardApplier;
import com.uxplima.uxmessentials.vote.application.port.RewardDispatcher;
import com.uxplima.uxmessentials.vote.application.port.VoteAudience;
import com.uxplima.uxmessentials.vote.application.port.VoteBroadcaster;
import com.uxplima.uxmessentials.vote.application.port.VoteContext;
import com.uxplima.uxmessentials.vote.application.port.VoteRanking;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;
import com.uxplima.uxmessentials.vote.domain.BroadcastType;
import com.uxplima.uxmessentials.vote.domain.PartyResetSchedule;
import com.uxplima.uxmessentials.vote.domain.QueuedReward;
import com.uxplima.uxmessentials.vote.domain.VotePeriod;
import com.uxplima.uxmessentials.vote.domain.VoteSiteCatalog;
import com.uxplima.uxmessentials.vote.domain.VoteSiteSpec;
import com.uxplima.uxmessentials.vote.domain.VoteTally;
import com.uxplima.uxmessentials.vote.domain.reward.RewardCatalog;
import com.uxplima.uxmessentials.vote.domain.reward.RewardGrant;
import com.uxplima.uxmessentials.vote.domain.reward.RewardSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of {@link VoteSitesMenu} rendered through the menu engine: the window is a holder-backed
 * engine list (a {@link MenuHolder} routed by the one menu listener), drawn slot-for-slot from the resolved site
 * entries with the votable/cooldown materials the config supplies, and a click on a votable site sends that site's
 * clickable vote link. Unknown/blank materials still fall back, a site with no URL or one on cooldown is a silent
 * no-op, and the {@code /vote sites} command path routes to the view (or falls back to chat links when the catalog
 * is empty). The scheduler is a synchronous double so the async→entity hops run inline.
 */
class VoteSitesMenuTest {

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock player;
    private TestMenuEngine engine;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        player = server.addPlayer("Alice");
        player.setOp(true);
        engine = TestMenuEngine.create(new FakeMessages(), new SyncScheduler());
        engine.installListener(plugin);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // --- GUI open tests ---

    @Test
    void openBuildsAndOpensGuiForViewerWithVotableAndCooldownSites() {
        VoteSiteCatalog catalog = catalogOf(
                new VoteSiteSpec("PlanetMinecraft", Optional.of("https://planetminecraft.com"), Duration.ofHours(24)),
                new VoteSiteSpec("minecraft-mp", Optional.of("https://minecraft-mp.com"), Duration.ofHours(24)));

        // The second site is on cooldown (voted 1 hour ago, cooldown 24h).
        FakeVoteRepository repository = new FakeVoteRepository();
        repository.setLastVotedAtSite(
                player.getUniqueId(), "minecraft-mp", Instant.now().minus(Duration.ofHours(1)));

        VoteSitesMenu view = view(catalog, repository, VoteSitesMenu.GuiConfig.defaults());

        assertThatCode(() -> view.open(player)).doesNotThrowAnyException();
        Inventory top = player.getOpenInventory().getTopInventory();
        assertThat(top.getHolder()).isInstanceOf(MenuHolder.class);
        // The first content slot is the votable site (green default PAPER), the second the cooled-down one (CLOCK).
        assertThat(top.getItem(0).getType()).isEqualTo(Material.PAPER);
        assertThat(top.getItem(1).getType()).isEqualTo(Material.CLOCK);
    }

    @Test
    void openDrawsTheVotableAndCooldownMaterialsTheConfigSupplies() {
        VoteSiteCatalog catalog = catalogOf(
                new VoteSiteSpec("Votable", Optional.of("https://votable.example"), Duration.ofHours(24)),
                new VoteSiteSpec("Cooling", Optional.of("https://cooling.example"), Duration.ofHours(24)));
        FakeVoteRepository repository = new FakeVoteRepository();
        repository.setLastVotedAtSite(
                player.getUniqueId(), "Cooling", Instant.now().minus(Duration.ofHours(1)));
        VoteSitesMenu.GuiConfig cfg =
                new VoteSitesMenu.GuiConfig(true, Material.EMERALD_BLOCK, Material.REDSTONE_BLOCK);

        view(catalog, repository, cfg).open(player);

        Inventory top = player.getOpenInventory().getTopInventory();
        // Votable site renders with the configured votable material, the cooled-down one with the cooldown material.
        assertThat(top.getItem(0).getType()).isEqualTo(Material.EMERALD_BLOCK);
        assertThat(top.getItem(1).getType()).isEqualTo(Material.REDSTONE_BLOCK);
        // The bottom-row corners carry the two ARROW nav buttons the shipped spec puts there.
        assertThat(top.getItem(18).getType()).isEqualTo(Material.ARROW);
        assertThat(top.getItem(26).getType()).isEqualTo(Material.ARROW);
    }

    @Test
    void clickingAVotableSiteSendsTheClickableVoteLink() {
        VoteSiteCatalog catalog = catalogOf(
                new VoteSiteSpec("PlanetMinecraft", Optional.of("https://pmc.example"), Duration.ofHours(24)));
        view(catalog, new FakeVoteRepository(), VoteSitesMenu.GuiConfig.defaults())
                .open(player);

        fireClick(0); // the only content slot holds the votable site

        // The click sent the vote-link prompt (the engine routed the entity click to onSelect).
        assertThat(player.nextMessage()).contains(VoteMessageKey.VOTE_GUI_CLICK.key());
    }

    @Test
    void clickingACooledDownSiteSendsNoLink() {
        VoteSiteCatalog catalog =
                catalogOf(new VoteSiteSpec("Cooling", Optional.of("https://cooling.example"), Duration.ofHours(24)));
        FakeVoteRepository repository = new FakeVoteRepository();
        repository.setLastVotedAtSite(
                player.getUniqueId(), "Cooling", Instant.now().minus(Duration.ofHours(1)));
        view(catalog, repository, VoteSitesMenu.GuiConfig.defaults()).open(player);

        fireClick(0);

        // A site still on cooldown is a no-op: no vote-link prompt is sent.
        assertThat(player.nextMessage()).isNull();
    }

    @Test
    void openWithUnknownMaterialFallsBackToDefaultWithoutThrowing() {
        // parseMaterial with a garbage name falls back silently.
        Material parsed = VoteSitesMenu.GuiConfig.parseMaterial("OBVIOUSLY_INVALID_MATERIAL_XYZ", Material.PAPER);
        assertThat(parsed).isEqualTo(Material.PAPER);

        VoteSitesMenu.GuiConfig cfg = new VoteSitesMenu.GuiConfig(true, parsed, Material.CLOCK);
        VoteSiteCatalog catalog =
                catalogOf(new VoteSiteSpec("TestSite", Optional.of("https://example.com"), Duration.ofHours(24)));
        VoteSitesMenu view = view(catalog, new FakeVoteRepository(), cfg);

        assertThatCode(() -> view.open(player)).doesNotThrowAnyException();
    }

    @Test
    void openWithBlankMaterialNameFallsBackToDefault() {
        Material parsed = VoteSitesMenu.GuiConfig.parseMaterial("", Material.CLOCK);
        assertThat(parsed).isEqualTo(Material.CLOCK);
    }

    @Test
    void openSiteWithNoUrlRendersWithoutClickLinkAndDoesNotThrow() {
        // A site with no URL must still render and not throw on click.
        VoteSiteCatalog catalog = catalogOf(new VoteSiteSpec("NoUrlSite", Optional.empty(), Duration.ofHours(24)));
        VoteSitesMenu view = view(catalog, new FakeVoteRepository(), VoteSitesMenu.GuiConfig.defaults());

        assertThatCode(() -> view.open(player)).doesNotThrowAnyException();
        assertThat(player.getOpenInventory().getTopInventory().getHolder()).isInstanceOf(MenuHolder.class);
        // The URL-less site renders as a votable green icon but a click sends nothing.
        fireClick(0);
        assertThat(player.nextMessage()).isNull();
    }

    @Test
    void openEmptyCatalogStillOpensWithoutThrowing() {
        VoteSitesMenu view =
                view(VoteSiteCatalog.empty(), new FakeVoteRepository(), VoteSitesMenu.GuiConfig.defaults());

        assertThatCode(() -> view.open(player)).doesNotThrowAnyException();
    }

    // --- Command routing tests ---

    @Test
    void voteSitesCommandOpensGuiWhenCatalogIsNonEmpty() {
        VoteSiteCatalog catalog = catalogOf(
                new VoteSiteSpec("PlanetMinecraft", Optional.of("https://planetminecraft.com"), Duration.ofHours(24)));
        VoteSitesMenu view = view(catalog, new FakeVoteRepository(), VoteSitesMenu.GuiConfig.defaults());

        VoteServices services = services(catalog, view, new FakeMessages());
        CommandDispatcher<CommandSourceStack> dispatcher = register(services, ListDisplayMode.GUI);

        execute(dispatcher, "vote sites");

        assertThat(player.getOpenInventory().getTopInventory().getHolder()).isInstanceOf(MenuHolder.class);
    }

    @Test
    void voteNoArgInGuiModeOpensGuiWhenCatalogIsNonEmpty() {
        VoteSiteCatalog catalog =
                catalogOf(new VoteSiteSpec("TestSite", Optional.of("https://example.com"), Duration.ofHours(24)));
        VoteSitesMenu view = view(catalog, new FakeVoteRepository(), VoteSitesMenu.GuiConfig.defaults());

        FakeMessages messages = new FakeMessages();
        VoteServices services = services(catalog, view, messages);
        CommandDispatcher<CommandSourceStack> dispatcher = register(services, ListDisplayMode.GUI); // GUI mode

        execute(dispatcher, "vote");

        assertThat(player.getOpenInventory().getTopInventory().getHolder()).isInstanceOf(MenuHolder.class);
    }

    @Test
    void voteSitesCommandFallsBackToChatLinksWhenCatalogIsEmpty() {
        VoteSitesMenu view =
                view(VoteSiteCatalog.empty(), new FakeVoteRepository(), VoteSitesMenu.GuiConfig.defaults());

        FakeMessages messages = new FakeMessages();
        VoteServices services = services(VoteSiteCatalog.empty(), view, messages);
        CommandDispatcher<CommandSourceStack> dispatcher = register(services, ListDisplayMode.GUI);

        execute(dispatcher, "vote sites");

        // With empty catalog, falls back to chat links (which sends VOTE_LINKS_EMPTY).
        assertThat(messages.resolvedKeys).contains(VoteMessageKey.VOTE_LINKS_EMPTY.key());
    }

    @Test
    void voteNoArgInChatModeSendsLinksInsteadOfOpeningGui() {
        VoteSiteCatalog catalog =
                catalogOf(new VoteSiteSpec("TestSite", Optional.of("https://example.com"), Duration.ofHours(24)));
        VoteSitesMenu view = view(catalog, new FakeVoteRepository(), VoteSitesMenu.GuiConfig.defaults());

        FakeMessages messages = new FakeMessages();
        VoteServices services = services(catalog, view, messages);
        // chat mode: GUI must NOT open
        CommandDispatcher<CommandSourceStack> dispatcher = register(services, ListDisplayMode.CHAT);

        execute(dispatcher, "vote");

        // In chat mode the links use case was called: messages were resolved rather than opening a GUI.
        assertThat(messages.resolvedKeys).isNotEmpty();
    }

    // --- helpers ---

    /**
     * Build the board over the test engine and register its spec, the way vote wiring does. The spec resource is
     * read from the classpath because the fixture points the data folder at a path that does not exist.
     */
    private VoteSitesMenu view(VoteSiteCatalog catalog, FakeVoteRepository repository, VoteSitesMenu.GuiConfig cfg) {
        VoteSitesMenu menu = new VoteSitesMenu(engine.menus(), new FakeMessages(), catalog, repository, cfg);
        menu.register(engine.bindings(), Path.of("nonexistent"), NOOP_LOG);
        return menu;
    }

    private void fireClick(int slot) {
        InventoryView view = player.getOpenInventory();
        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
    }

    private VoteSiteCatalog catalogOf(VoteSiteSpec... specs) {
        return new VoteSiteCatalog(List.of(specs));
    }

    private VoteServices services(VoteSiteCatalog catalog, VoteSitesMenu view, FakeMessages messages) {
        Notifier notifier = new Notifier(messages, new NoSink());
        RewardSpec noOp = new RewardSpec(100, Optional.empty(), List.of(), List.of(), List.of(), List.of(), Set.of());
        PartyConfig party = new PartyConfig(noOp, 25, false, 0, PartyResetSchedule.NONE, List.of());
        FakeVoteRepository repo = new FakeVoteRepository();
        VoteBroadcaster broadcaster = new NoOpBroadcaster();
        BroadcastSettings broadcastSettings =
                new BroadcastSettings(BroadcastType.EVERY_VOTE, Duration.ZERO, Set.of(BroadcastChannel.CHAT), Set.of());
        HandleVote handleVote = new HandleVote(
                repo,
                new RewardEngine(RewardCatalog.empty(), Set.of()),
                new NoOpRewardApplier(),
                new NoOpVoteContext(),
                new NoOpVoteAudience(),
                broadcastSettings,
                broadcaster,
                new NoOpBroadcastThrottle(),
                new NoEvents(),
                party,
                0,
                ZoneId.of("UTC"));
        VoteLinks links = new VoteLinks(List.of(), notifier);
        return new VoteServices(
                handleVote,
                new ApplyQueuedRewards(repo, new NoOpRewardDispatcher()),
                links,
                view,
                new VotePartyStatus(repo, notifier, 25),
                new ShowVoteTotals(repo, notifier),
                new ShowVoteStreak(repo, notifier),
                new TopVoters(repo, notifier, 10),
                new ShowNextVote(repo, catalog, notifier),
                new ShowLastVote(repo, catalog, notifier),
                new VoteReminderEligibility(repo, catalog),
                new NoOpReminderPreferences(),
                new NoOpBroadcastVisibility(),
                new ForceParty(
                        repo,
                        new NoOpRewardApplier(),
                        new NoOpVoteAudience(),
                        notifier,
                        broadcaster,
                        Set.of(BroadcastChannel.CHAT),
                        new NoEvents(),
                        party),
                new SetPartyCount(repo, notifier),
                new AddPartyCount(
                        repo,
                        new NoOpRewardApplier(),
                        new NoOpVoteAudience(),
                        notifier,
                        broadcaster,
                        Set.of(BroadcastChannel.CHAT),
                        new NoEvents(),
                        party),
                new GiveVote(handleVote, notifier),
                new ResetVoterTotals(repo, notifier),
                new NoOpPlayerLookup(),
                new SyncScheduler(),
                messages);
    }

    private CommandDispatcher<CommandSourceStack> register(VoteServices services, ListDisplayMode mode) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        VoteCommand cmd = new VoteCommand(services, () -> mode);
        dispatcher.getRoot().addChild(cmd.build());
        return dispatcher;
    }

    private void execute(CommandDispatcher<CommandSourceStack> dispatcher, String input) {
        try {
            dispatcher.execute(input, CommandSourceStackMock.from(player));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new AssertionError("command did not parse: " + input, e);
        }
    }

    // --- fakes ---

    private static final Logger NOOP_LOG = new Logger() {
        @Override
        public void info(String m, Object... a) {}

        @Override
        public void warn(String m, Object... a) {}

        @Override
        public void error(String m, Throwable t) {}

        @Override
        public void debug(String m, Object... a) {}
    };

    private static final class FakeVoteRepository implements VoteRepository {

        private final Map<String, Instant> lastVotes = new java.util.HashMap<>();

        void setLastVotedAtSite(UUID playerUuid, String site, Instant at) {
            lastVotes.put(playerUuid + ":" + site, at);
        }

        @Override
        public Optional<Instant> lastVoteAtSite(PlayerRef player, String site) {
            return Optional.ofNullable(lastVotes.get(player.uuid() + ":" + site));
        }

        @Override
        public void recordLastVoteAtSite(PlayerRef player, String site, Instant at) {}

        @Override
        public int partyCount() {
            return 0;
        }

        @Override
        public void setPartyCount(int count) {}

        @Override
        public int incrementAndGetPartyCount() {
            return 1;
        }

        @Override
        public void enqueue(QueuedReward reward) {}

        @Override
        public List<QueuedReward> drainFor(PlayerRef player) {
            return List.of();
        }

        @Override
        public boolean hasPending(PlayerRef player) {
            return false;
        }

        @Override
        public int queuedCount(PlayerRef player) {
            return 0;
        }

        @Override
        public VoteTally totalsOf(PlayerRef player) {
            return VoteTally.empty();
        }

        @Override
        public void saveTotals(PlayerRef player, VoteTally tally) {}

        @Override
        public List<VoteRanking> topVoters(VotePeriod period, int limit) {
            return List.of();
        }

        @Override
        public void markPartyParticipant(PlayerRef player) {}

        @Override
        public Set<UUID> partyParticipants() {
            return Set.of();
        }

        @Override
        public void clearPartyParticipants() {}

        @Override
        public long partyPeriodKey() {
            return 0L;
        }

        @Override
        public void setPartyPeriodKey(long key) {}

        @Override
        public int thresholdOverride() {
            return 0;
        }

        @Override
        public void setThresholdOverride(int override) {}

        @Override
        public boolean claimPartyFire(int threshold) {
            return false;
        }

        @Override
        public void resetTotals(PlayerRef player) {}
    }

    private static final class FakeMessages implements Messages {

        final List<String> resolvedKeys = new ArrayList<>();

        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            if (!"prefix".equals(key.key())) {
                resolvedKeys.add(key.key());
            }
            return key.key();
        }
    }

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
        public void asyncAfter(Duration delay, Runnable task) {
            task.run();
        }

        @Override
        public AutoCloseable repeatGlobal(Runnable task, Duration initialDelay, Duration period) {
            return () -> {};
        }
    }

    private static final class NoSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
    }

    private static final class NoEvents implements DomainEventPublisher {
        @Override
        public void publish(DomainEvent event) {}
    }

    private static final class NoOpRewardApplier implements RewardApplier {
        @Override
        public void apply(PlayerRef voter, boolean online, RewardGrant grant) {}
    }

    private static final class NoOpBroadcaster implements VoteBroadcaster {
        @Override
        public void broadcast(MessageKey key, Map<String, String> placeholders, Set<BroadcastChannel> channels) {}
    }

    private static final class NoOpBroadcastThrottle implements BroadcastThrottle {
        @Override
        public Optional<Instant> lastBroadcastAt(PlayerRef voter) {
            return Optional.empty();
        }

        @Override
        public void recordBroadcast(PlayerRef voter, Instant at) {}
    }

    private static final class NoOpBroadcastVisibility implements BroadcastVisibility {
        @Override
        public boolean receivesBroadcasts(PlayerRef who) {
            return true;
        }

        @Override
        public boolean toggle(PlayerRef who) {
            return true;
        }
    }

    private static final class NoOpRewardDispatcher implements RewardDispatcher {
        @Override
        public void dispatch(List<String> commands, String playerName) {}
    }

    private static final class NoOpVoteContext implements VoteContext {
        @Override
        public String worldOf(PlayerRef voter) {
            return "";
        }

        @Override
        public boolean hasPermission(PlayerRef voter, String node) {
            return false;
        }

        @Override
        public boolean roll(int chancePercent) {
            return chancePercent >= 100;
        }

        @Override
        public boolean isOnline(PlayerRef voter) {
            return false;
        }
    }

    private static final class NoOpVoteAudience implements VoteAudience {
        @Override
        public Collection<PlayerRef> online() {
            return List.of();
        }
    }

    private static final class NoOpPlayerLookup
            implements com.uxplima.uxmessentials.shared.application.port.PlayerLookup {

        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return Optional.empty();
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            return Optional.empty();
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return false;
        }
    }

    private static final class NoOpReminderPreferences implements ReminderPreferences {
        @Override
        public boolean wantsReminders(PlayerRef who) {
            return false;
        }

        @Override
        public boolean toggle(PlayerRef who) {
            return false;
        }
    }
}

package com.uxplima.uxmessentials.vote.command;

import static org.assertj.core.api.Assertions.assertThat;

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

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.CommandDispatcher;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.ListDisplayMode;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.display.BroadcastChannel;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
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
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/**
 * MockBukkit coverage of the {@code /vote next}, {@code /vote last}, and {@code /vote remind}
 * subcommands added in V4-3.
 *
 * <ul>
 *   <li>{@code /vote next} routes to {@link ShowNextVote}. Asserts the use case is called by
 *       checking the repository is consulted for per-site timestamps.
 *   <li>{@code /vote last} routes to {@link ShowLastVote}, same assertion shape.
 *   <li>{@code /vote remind} toggles the reminder preference and sends the correct key.
 * </ul>
 */
class VoteNextLastRemindCommandPathTest {

    private ServerMock server;
    private PlayerMock sender;
    private RecordingRepository repository;
    private RecordingMessages messages;
    private RecordingReminderPreferences reminderPrefs;
    private RecordingBroadcastVisibility broadcastVisibility;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin();
        sender = server.addPlayer("Alice");
        sender.setOp(true);
        repository = new RecordingRepository();
        messages = new RecordingMessages();
        reminderPrefs = new RecordingReminderPreferences(true); // starts opted-in
        broadcastVisibility = new RecordingBroadcastVisibility(true); // starts receiving
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void voteNextSubcommandCallsLastVoteAtSiteForEachConfiguredSite() {
        VoteSiteCatalog catalog = new VoteSiteCatalog(List.of(
                new VoteSiteSpec("PMC", Optional.of("https://pmc.example"), Duration.ofHours(24)),
                new VoteSiteSpec("MP", Optional.empty(), Duration.ofHours(24))));
        CommandDispatcher<CommandSourceStack> dispatcher = register(catalog);

        execute(dispatcher, "vote next");

        // ShowNextVote calls lastVoteAtSite for each site.
        assertThat(repository.lastVoteAtSiteCalls).containsExactlyInAnyOrder("PMC", "MP");
    }

    @Test
    void voteLastSubcommandCallsLastVoteAtSiteForEachConfiguredSite() {
        VoteSiteCatalog catalog = new VoteSiteCatalog(
                List.of(new VoteSiteSpec("PMC", Optional.of("https://pmc.example"), Duration.ofHours(24))));
        CommandDispatcher<CommandSourceStack> dispatcher = register(catalog);

        execute(dispatcher, "vote last");

        assertThat(repository.lastVoteAtSiteCalls).contains("PMC");
    }

    @Test
    void voteNextSendsNoneKeyWhenCatalogIsEmpty() {
        CommandDispatcher<CommandSourceStack> dispatcher = register(VoteSiteCatalog.empty());

        execute(dispatcher, "vote next");

        assertThat(messages.lastKey()).isEqualTo(VoteMessageKey.VOTE_NEXT_NONE.key());
    }

    @Test
    void voteLastSendsNoneKeyWhenCatalogIsEmpty() {
        CommandDispatcher<CommandSourceStack> dispatcher = register(VoteSiteCatalog.empty());

        execute(dispatcher, "vote last");

        // ShowLastVote reuses VOTE_NEXT_NONE when the catalog is empty.
        assertThat(messages.lastKey()).isEqualTo(VoteMessageKey.VOTE_NEXT_NONE.key());
    }

    @Test
    void voteRemindFlipsPrefFromOnToOffAndSendsDisabledKey() {
        // starts opted-in; first toggle should turn it off
        CommandDispatcher<CommandSourceStack> dispatcher = register(VoteSiteCatalog.empty());

        execute(dispatcher, "vote remind");

        assertThat(reminderPrefs.lastToggleResult).isFalse();
        assertThat(messages.lastKey()).isEqualTo(VoteMessageKey.VOTE_REMIND_DISABLED.key());
    }

    @Test
    void voteRemindFlipsPrefFromOffToOnAndSendsEnabledKey() {
        // Start opted-out.
        reminderPrefs = new RecordingReminderPreferences(false);
        CommandDispatcher<CommandSourceStack> dispatcher = register(VoteSiteCatalog.empty());

        execute(dispatcher, "vote remind");

        assertThat(reminderPrefs.lastToggleResult).isTrue();
        assertThat(messages.lastKey()).isEqualTo(VoteMessageKey.VOTE_REMIND_ENABLED.key());
    }

    @Test
    void voteBroadcastsFlipsVisibilityFromOnToOffAndSendsHiddenKey() {
        // starts receiving; first toggle should hide broadcasts
        CommandDispatcher<CommandSourceStack> dispatcher = register(VoteSiteCatalog.empty());

        execute(dispatcher, "vote broadcasts");

        assertThat(broadcastVisibility.lastToggleResult).isFalse();
        assertThat(messages.lastKey()).isEqualTo(VoteMessageKey.VOTE_BROADCASTS_HIDDEN.key());
    }

    @Test
    void voteBroadcastsFlipsVisibilityFromOffToOnAndSendsShownKey() {
        // Start hidden.
        broadcastVisibility = new RecordingBroadcastVisibility(false);
        CommandDispatcher<CommandSourceStack> dispatcher = register(VoteSiteCatalog.empty());

        execute(dispatcher, "vote broadcasts");

        assertThat(broadcastVisibility.lastToggleResult).isTrue();
        assertThat(messages.lastKey()).isEqualTo(VoteMessageKey.VOTE_BROADCASTS_SHOWN.key());
    }

    @Test
    void voteBroadcastsSubcommandExistsInCommandTree() {
        VoteCommand command = new VoteCommand(services(VoteSiteCatalog.empty()), () -> ListDisplayMode.CHAT);
        var root = command.build();
        assertThat(root.getChild("broadcasts"))
                .as("/vote broadcasts must exist")
                .isNotNull();
    }

    @Test
    void voteNextSubcommandExistsInCommandTree() {
        VoteCommand command = new VoteCommand(services(VoteSiteCatalog.empty()), () -> ListDisplayMode.CHAT);
        var root = command.build();
        assertThat(root.getChild("next")).as("/vote next must exist").isNotNull();
    }

    @Test
    void voteLastSubcommandExistsInCommandTree() {
        VoteCommand command = new VoteCommand(services(VoteSiteCatalog.empty()), () -> ListDisplayMode.CHAT);
        var root = command.build();
        assertThat(root.getChild("last")).as("/vote last must exist").isNotNull();
    }

    @Test
    void voteRemindSubcommandExistsInCommandTree() {
        VoteCommand command = new VoteCommand(services(VoteSiteCatalog.empty()), () -> ListDisplayMode.CHAT);
        var root = command.build();
        assertThat(root.getChild("remind")).as("/vote remind must exist").isNotNull();
    }

    // --- helpers ---

    private CommandDispatcher<CommandSourceStack> register(VoteSiteCatalog catalog) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        CommandRegistration command = new VoteCommand(services(catalog), () -> ListDisplayMode.CHAT);
        dispatcher.getRoot().addChild(command.build());
        return dispatcher;
    }

    private void execute(CommandDispatcher<CommandSourceStack> dispatcher, String input) {
        try {
            dispatcher.execute(input, CommandSourceStackMock.from(sender));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new AssertionError("command did not parse: " + input, e);
        }
    }

    private VoteServices services(VoteSiteCatalog catalog) {
        Notifier notifier = new Notifier(messages, new NoSink());
        RewardSpec noOp = new RewardSpec(100, Optional.empty(), List.of(), List.of(), List.of(), List.of(), Set.of());
        PartyConfig party = new PartyConfig(noOp, 25, false, 0, PartyResetSchedule.NONE, List.of());
        NoOpAudience audience = new NoOpAudience();
        NoOpApplier applier = new NoOpApplier();
        NoEvents events = new NoEvents();
        NoLookup lookup = new NoLookup();
        VoteBroadcaster broadcaster = new NoOpBroadcaster();
        BroadcastSettings broadcastSettings =
                new BroadcastSettings(BroadcastType.EVERY_VOTE, Duration.ZERO, Set.of(BroadcastChannel.CHAT), Set.of());
        HandleVote handleVote = new HandleVote(
                repository,
                new RewardEngine(RewardCatalog.empty(), Set.of()),
                applier,
                new NoOpContext(),
                audience,
                broadcastSettings,
                broadcaster,
                new NoOpBroadcastThrottle(),
                events,
                party,
                0,
                ZoneId.of("UTC"));
        ApplyQueuedRewards applyQueuedRewards = new ApplyQueuedRewards(repository, new NoOpDispatcher());
        VoteLinks voteLinks = new VoteLinks(List.of(), notifier);
        VoteSitesMenu sitesGui = new VoteSitesMenu(
                TestMenuEngine.create(messages, new SyncScheduler()).menus(),
                messages,
                catalog,
                repository,
                VoteSitesMenu.GuiConfig.defaults());
        VotePartyStatus partyStatus = new VotePartyStatus(repository, notifier, 25);
        ShowVoteTotals showVoteTotals = new ShowVoteTotals(repository, notifier);
        ShowVoteStreak showVoteStreak = new ShowVoteStreak(repository, notifier);
        TopVoters topVoters = new TopVoters(repository, notifier, 10);
        ShowNextVote showNextVote = new ShowNextVote(repository, catalog, notifier);
        ShowLastVote showLastVote = new ShowLastVote(repository, catalog, notifier);
        VoteReminderEligibility eligibility = new VoteReminderEligibility(repository, catalog);
        ForceParty forceParty = new ForceParty(
                repository, applier, audience, notifier, broadcaster, Set.of(BroadcastChannel.CHAT), events, party);
        SetPartyCount setPartyCount = new SetPartyCount(repository, notifier);
        AddPartyCount addPartyCount = new AddPartyCount(
                repository, applier, audience, notifier, broadcaster, Set.of(BroadcastChannel.CHAT), events, party);
        GiveVote giveVote = new GiveVote(handleVote, notifier);
        ResetVoterTotals resetVoterTotals = new ResetVoterTotals(repository, notifier);
        return new VoteServices(
                handleVote,
                applyQueuedRewards,
                voteLinks,
                sitesGui,
                partyStatus,
                showVoteTotals,
                showVoteStreak,
                topVoters,
                showNextVote,
                showLastVote,
                eligibility,
                reminderPrefs,
                broadcastVisibility,
                forceParty,
                setPartyCount,
                addPartyCount,
                giveVote,
                resetVoterTotals,
                lookup,
                new SyncScheduler(),
                messages);
    }

    // --- recording fakes ---

    private static final class RecordingRepository implements VoteRepository {
        final List<String> lastVoteAtSiteCalls = new ArrayList<>();

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

        @Override
        public Optional<Instant> lastVoteAtSite(PlayerRef player, String site) {
            lastVoteAtSiteCalls.add(site);
            return Optional.empty();
        }

        @Override
        public void recordLastVoteAtSite(PlayerRef player, String site, Instant at) {}
    }

    private static final class RecordingMessages implements Messages {
        final List<String> resolvedKeys = new ArrayList<>();

        @Nullable String lastKey() {
            return resolvedKeys.isEmpty() ? null : resolvedKeys.get(resolvedKeys.size() - 1);
        }

        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            if (!"prefix".equals(key.key())) {
                resolvedKeys.add(key.key());
            }
            return key.key();
        }
    }

    /**
     * Reminder-preferences recording stub. Starts with a known toggle state and tracks the most
     * recent result returned by {@code toggle()}.
     */
    private static final class RecordingReminderPreferences implements ReminderPreferences {
        private boolean wantsReminders;

        @Nullable Boolean lastToggleResult;

        RecordingReminderPreferences(boolean initialWants) {
            this.wantsReminders = initialWants;
        }

        @Override
        public boolean wantsReminders(PlayerRef who) {
            return wantsReminders;
        }

        @Override
        public boolean toggle(PlayerRef who) {
            wantsReminders = !wantsReminders;
            lastToggleResult = wantsReminders;
            return wantsReminders;
        }
    }

    private static final class RecordingBroadcastVisibility implements BroadcastVisibility {
        private boolean receives;

        @Nullable Boolean lastToggleResult;

        RecordingBroadcastVisibility(boolean initialReceives) {
            this.receives = initialReceives;
        }

        @Override
        public boolean receivesBroadcasts(PlayerRef who) {
            return receives;
        }

        @Override
        public boolean toggle(PlayerRef who) {
            receives = !receives;
            lastToggleResult = receives;
            return receives;
        }
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

    // --- minimal stubs ---

    private static final class NoSink implements MessageSink {
        @Override
        public void deliver(PlayerRef viewer, String renderedText) {}
    }

    private static final class NoEvents implements DomainEventPublisher {
        @Override
        public void publish(DomainEvent event) {}
    }

    private static final class NoLookup implements PlayerLookup {
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

    private static final class SyncScheduler implements Scheduler {
        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(com.uxplima.uxmessentials.shared.domain.Position p, Runnable task) {
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

    private static final class NoOpDispatcher implements RewardDispatcher {
        @Override
        public void dispatch(List<String> commands, String playerName) {}
    }

    private static final class NoOpApplier implements RewardApplier {
        @Override
        public void apply(PlayerRef voter, boolean online, RewardGrant grant) {}
    }

    private static final class NoOpContext implements VoteContext {
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

    private static final class NoOpAudience implements VoteAudience {
        @Override
        public Collection<PlayerRef> online() {
            return List.of();
        }
    }
}

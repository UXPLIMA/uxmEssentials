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

import org.bukkit.plugin.Plugin;

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
 * MockBukkit coverage of the {@code /vote total} and {@code /vote top} subcommands. Asserts that
 * {@code total} routes to {@link ShowVoteTotals} for the sender (self) and a resolved offline target,
 * that unknown targets receive the {@code VOTE_TOTAL_UNKNOWN} key, that {@code top} routes to
 * {@link TopVoters} with the correct period, and that the {@code top} subcommand is gated by
 * {@code uxmessentials.vote.top}.
 *
 * <p>Both use cases are final classes so they are not subclassed; instead the tests record calls via
 * a recording {@link VoteRepository} and notifier that capture what the use cases read and emit.
 */
class VoteCommandPathTest {

    private ServerMock server;
    private Plugin plugin;
    private PlayerMock sender;
    private PlayerMock target;
    private RecordingVoteRepository repository;
    private RecordingMessages messages;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        sender = server.addPlayer("Alice");
        sender.setOp(true);
        target = server.addPlayer("Bob");
        repository = new RecordingVoteRepository();
        messages = new RecordingMessages();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void voteTotalSelfCallsShowVoteTotalsWithSenderAsBothViewerAndTarget() {
        CommandDispatcher<CommandSourceStack> dispatcher = register(new ServerPlayerLookup());

        execute(dispatcher, "vote total");

        // ShowVoteTotals.show calls totalsOf on the repository; assert the target UUID is the sender's.
        assertThat(repository.totalsOfCalls).hasSize(1);
        assertThat(repository.totalsOfCalls.get(0).uuid()).isEqualTo(sender.getUniqueId());
    }

    @Test
    void voteTotalOtherResolvesTargetViaPlayerLookup() {
        CommandDispatcher<CommandSourceStack> dispatcher = register(new ServerPlayerLookup());

        execute(dispatcher, "vote total Bob");

        assertThat(repository.totalsOfCalls).hasSize(1);
        assertThat(repository.totalsOfCalls.get(0).uuid()).isEqualTo(target.getUniqueId());
    }

    @Test
    void voteTotalUnknownPlayerSendsUnknownKey() {
        CommandDispatcher<CommandSourceStack> dispatcher = register(new ServerPlayerLookup());

        execute(dispatcher, "vote total UnknownPlayer");

        // The repository must not be queried for an unresolvable target.
        assertThat(repository.totalsOfCalls).isEmpty();
        assertThat(messages.lastKey()).isEqualTo(VoteMessageKey.VOTE_TOTAL_UNKNOWN.key());
    }

    @Test
    void voteStreakSelfCallsShowVoteStreakWithSenderAsBothViewerAndTarget() {
        CommandDispatcher<CommandSourceStack> dispatcher = register(new ServerPlayerLookup());

        execute(dispatcher, "vote streak");

        // ShowVoteStreak.show calls totalsOf on the repository; assert the target UUID is the sender's.
        assertThat(repository.totalsOfCalls).hasSize(1);
        assertThat(repository.totalsOfCalls.get(0).uuid()).isEqualTo(sender.getUniqueId());
    }

    @Test
    void voteStreakOtherResolvesTargetViaPlayerLookup() {
        CommandDispatcher<CommandSourceStack> dispatcher = register(new ServerPlayerLookup());

        execute(dispatcher, "vote streak Bob");

        assertThat(repository.totalsOfCalls).hasSize(1);
        assertThat(repository.totalsOfCalls.get(0).uuid()).isEqualTo(target.getUniqueId());
    }

    @Test
    void voteStreakUnknownPlayerSendsUnknownKey() {
        CommandDispatcher<CommandSourceStack> dispatcher = register(new ServerPlayerLookup());

        execute(dispatcher, "vote streak UnknownPlayer");

        // The repository must not be queried for an unresolvable target.
        assertThat(repository.totalsOfCalls).isEmpty();
        assertThat(messages.lastKey()).isEqualTo(VoteMessageKey.VOTE_TOTAL_UNKNOWN.key());
    }

    @Test
    void streakSubcommandExistsUnderVote() {
        VoteCommand command = new VoteCommand(services(new ServerPlayerLookup()), () -> ListDisplayMode.CHAT);
        var root = command.build();
        var streakNode = root.getChild("streak");
        assertThat(streakNode).as("streak subcommand must exist under /vote").isNotNull();
    }

    @Test
    void voteTopMonthlyIsTheDefaultPeriod() {
        CommandDispatcher<CommandSourceStack> dispatcher = register(new ServerPlayerLookup());

        execute(dispatcher, "vote top");

        assertThat(repository.topVotersCalls).hasSize(1);
        assertThat(repository.topVotersCalls.get(0)).isEqualTo(VotePeriod.MONTHLY);
    }

    @Test
    void voteTopDailyDispatchesWithDailyPeriod() {
        CommandDispatcher<CommandSourceStack> dispatcher = register(new ServerPlayerLookup());

        execute(dispatcher, "vote top daily");

        assertThat(repository.topVotersCalls).hasSize(1);
        assertThat(repository.topVotersCalls.get(0)).isEqualTo(VotePeriod.DAILY);
    }

    @Test
    void voteTopWeeklyDispatchesWithWeeklyPeriod() {
        CommandDispatcher<CommandSourceStack> dispatcher = register(new ServerPlayerLookup());

        execute(dispatcher, "vote top weekly");

        assertThat(repository.topVotersCalls).hasSize(1);
        assertThat(repository.topVotersCalls.get(0)).isEqualTo(VotePeriod.WEEKLY);
    }

    @Test
    void voteTopAlltimeDispatchesWithAlltimePeriod() {
        CommandDispatcher<CommandSourceStack> dispatcher = register(new ServerPlayerLookup());

        execute(dispatcher, "vote top alltime");

        assertThat(repository.topVotersCalls).hasSize(1);
        assertThat(repository.topVotersCalls.get(0)).isEqualTo(VotePeriod.ALLTIME);
    }

    @Test
    void topSubcommandRequiresVoteTopPermissionNotJustVoteUse() {
        VoteCommand command = new VoteCommand(services(new ServerPlayerLookup()), () -> ListDisplayMode.CHAT);
        var root = command.build();
        var topNode = root.getChild("top");
        assertThat(topNode).as("top subcommand must exist under /vote").isNotNull();

        // A player with vote.use but not vote.top must not reach the top subcommand.
        PlayerMock restricted = server.addPlayer();
        restricted.addAttachment(plugin, "uxmessentials.vote.use", true);
        assertThat(topNode.canUse(CommandSourceStackMock.from(restricted))).isFalse();

        // A player with vote.top may reach it.
        PlayerMock privileged = server.addPlayer();
        privileged.addAttachment(plugin, "uxmessentials.vote.top", true);
        assertThat(topNode.canUse(CommandSourceStackMock.from(privileged))).isTrue();
    }

    @Test
    void totalSubcommandExistsUnderVote() {
        VoteCommand command = new VoteCommand(services(new ServerPlayerLookup()), () -> ListDisplayMode.CHAT);
        var root = command.build();
        var totalNode = root.getChild("total");
        assertThat(totalNode).as("total subcommand must exist under /vote").isNotNull();
    }

    @Test
    void leaderboardNameResolverQueriesPlayerLookupByUuid() {
        // Put a ranked player in the repository; the command must resolve the name via PlayerLookup.
        UUID bobUuid = target.getUniqueId();
        // Repository returns Bob's UUID as the ranked player; lookup can resolve it to "Bob".
        repository.topResult = List.of(new VoteRanking(new PlayerRef(bobUuid, bobUuid.toString()), 10L));
        CommandDispatcher<CommandSourceStack> dispatcher = register(new ServerPlayerLookup());

        execute(dispatcher, "vote top monthly");

        // The entry line must carry "Bob" (the resolved name) not the UUID string.
        assertThat(messages.resolvedNames).contains("Bob");
    }

    @Test
    void adminSubtreeExistsUnderVote() {
        VoteCommand command = new VoteCommand(services(new ServerPlayerLookup()), () -> ListDisplayMode.CHAT);
        var root = command.build();
        var adminNode = root.getChild("admin");
        assertThat(adminNode).as("admin subcommand must exist under /vote").isNotNull();
    }

    @Test
    void adminSubtreeRequiresVoteAdminPermission() {
        VoteCommand command = new VoteCommand(services(new ServerPlayerLookup()), () -> ListDisplayMode.CHAT);
        var adminNode = command.build().getChild("admin");
        assertThat(adminNode).isNotNull();

        // A player without vote.admin must not reach the admin node.
        PlayerMock noAdmin = server.addPlayer();
        noAdmin.addAttachment(plugin, "uxmessentials.vote.use", true);
        assertThat(adminNode.canUse(CommandSourceStackMock.from(noAdmin))).isFalse();

        // A player with vote.admin may reach it.
        PlayerMock admin = server.addPlayer();
        admin.addAttachment(plugin, "uxmessentials.vote.admin", true);
        assertThat(adminNode.canUse(CommandSourceStackMock.from(admin))).isTrue();
    }

    @Test
    void voteAdminGiveVoteUnknownPlayerSendsUnknownKey() {
        sender.addAttachment(plugin, "uxmessentials.vote.admin", true);
        CommandDispatcher<CommandSourceStack> dispatcher = registerWithAdmin();

        execute(dispatcher, "vote admin givevote NonExistentPlayer");

        assertThat(messages.lastKey()).isEqualTo(VoteMessageKey.VOTE_TOTAL_UNKNOWN.key());
    }

    @Test
    void voteAdminResetUnknownPlayerSendsUnknownKey() {
        sender.addAttachment(plugin, "uxmessentials.vote.admin", true);
        CommandDispatcher<CommandSourceStack> dispatcher = registerWithAdmin();

        execute(dispatcher, "vote admin reset NonExistentPlayer");

        assertThat(messages.lastKey()).isEqualTo(VoteMessageKey.VOTE_TOTAL_UNKNOWN.key());
    }

    @Test
    void voteAdminGiveVoteCallsGiveVoteUseCase() {
        sender.addAttachment(plugin, "uxmessentials.vote.admin", true);
        CommandDispatcher<CommandSourceStack> dispatcher = registerWithAdmin();

        execute(dispatcher, "vote admin givevote Bob 3");

        // GiveVote calls HandleVote.handle 3 times; HandleVote calls incrementAndGetPartyCount once per vote.
        assertThat(repository.incrementCalls).isEqualTo(3);
    }

    @Test
    void voteAdminResetCallsResetTotals() {
        sender.addAttachment(plugin, "uxmessentials.vote.admin", true);
        CommandDispatcher<CommandSourceStack> dispatcher = registerWithAdmin();

        execute(dispatcher, "vote admin reset Bob");

        assertThat(repository.resetCalls).hasSize(1);
        assertThat(repository.resetCalls.get(0).uuid()).isEqualTo(target.getUniqueId());
    }

    @Test
    void voteClaimWithQueuedRewardsSendsPaidKeyWithTheCount() {
        // Production drains a player's queued rows into ONE batch carrying every command (see
        // JooqVoteRepository.selectBatch), so the fake mirrors that: a single batch of two commands →
        // applyFor returns the command total (2) → VOTE_CLAIM_PAID with count=2.
        repository.pendingForClaim = List.of(new QueuedReward(
                new PlayerRef(sender.getUniqueId(), "Alice"), List.of("say a", "say b"), Instant.now()));
        CommandDispatcher<CommandSourceStack> dispatcher = register(new ServerPlayerLookup());

        execute(dispatcher, "vote claim");

        assertThat(messages.lastKey()).isEqualTo(VoteMessageKey.VOTE_CLAIM_PAID.key());
        assertThat(messages.placeholdersByKey.get(VoteMessageKey.VOTE_CLAIM_PAID.key()))
                .containsEntry("count", "2");
    }

    @Test
    void voteClaimWithAnEmptyQueueSendsTheEmptyKey() {
        // Default: pendingForClaim is empty → applyFor returns 0 → VOTE_CLAIM_EMPTY.
        CommandDispatcher<CommandSourceStack> dispatcher = register(new ServerPlayerLookup());

        execute(dispatcher, "vote claim");

        assertThat(messages.lastKey()).isEqualTo(VoteMessageKey.VOTE_CLAIM_EMPTY.key());
    }

    @Test
    void claimSubcommandExistsUnderVote() {
        VoteCommand command = new VoteCommand(services(new ServerPlayerLookup()), () -> ListDisplayMode.CHAT);
        var claimNode = command.build().getChild("claim");
        assertThat(claimNode).as("claim subcommand must exist under /vote").isNotNull();
    }

    // --- helpers ---

    private CommandDispatcher<CommandSourceStack> register(PlayerLookup lookup) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        CommandRegistration command = new VoteCommand(services(lookup), () -> ListDisplayMode.CHAT);
        dispatcher.getRoot().addChild(command.build());
        return dispatcher;
    }

    private CommandDispatcher<CommandSourceStack> registerWithAdmin() {
        // Use a lookup that can find Bob (online player)
        return register(new ServerPlayerLookup());
    }

    private void execute(CommandDispatcher<CommandSourceStack> dispatcher, String input) {
        try {
            dispatcher.execute(input, CommandSourceStackMock.from(sender));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new AssertionError("command did not parse: " + input, e);
        }
    }

    private VoteServices services(PlayerLookup lookup) {
        Notifier notifier = new Notifier(messages, new NoSink());
        RewardSpec noOpSpec =
                new RewardSpec(100, Optional.empty(), List.of(), List.of(), List.of(), List.of(), Set.of());
        PartyConfig party = new PartyConfig(noOpSpec, 25, false, 0, PartyResetSchedule.NONE, List.of());
        NoOpVoteAudience audience = new NoOpVoteAudience();
        NoOpRewardApplier applier = new NoOpRewardApplier();
        NoEvents events = new NoEvents();
        VoteBroadcaster broadcaster = new NoOpBroadcaster();
        BroadcastSettings broadcastSettings =
                new BroadcastSettings(BroadcastType.EVERY_VOTE, Duration.ZERO, Set.of(BroadcastChannel.CHAT), Set.of());
        HandleVote handleVote = new HandleVote(
                repository,
                new RewardEngine(RewardCatalog.empty(), Set.of()),
                applier,
                new NoOpVoteContext(),
                audience,
                broadcastSettings,
                broadcaster,
                new NoOpBroadcastThrottle(),
                events,
                party,
                0,
                ZoneId.of("UTC"));
        ApplyQueuedRewards applyQueuedRewards = new ApplyQueuedRewards(repository, new NoOpRewardDispatcher());
        VoteLinks voteLinks = new VoteLinks(List.of(), notifier);
        VoteSitesMenu sitesGui = new VoteSitesMenu(
                TestMenuEngine.create(messages, new SyncScheduler()).menus(),
                messages,
                VoteSiteCatalog.empty(),
                repository,
                VoteSitesMenu.GuiConfig.defaults());
        VotePartyStatus votePartyStatus = new VotePartyStatus(repository, notifier, 25);
        ShowVoteTotals showVoteTotals = new ShowVoteTotals(repository, notifier);
        ShowVoteStreak showVoteStreak = new ShowVoteStreak(repository, notifier);
        TopVoters topVoters = new TopVoters(repository, notifier, 10);
        ShowNextVote showNextVote = new ShowNextVote(repository, VoteSiteCatalog.empty(), notifier);
        ShowLastVote showLastVote = new ShowLastVote(repository, VoteSiteCatalog.empty(), notifier);
        VoteReminderEligibility reminderEligibility = new VoteReminderEligibility(repository, VoteSiteCatalog.empty());
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
                votePartyStatus,
                showVoteTotals,
                showVoteStreak,
                topVoters,
                showNextVote,
                showLastVote,
                reminderEligibility,
                new NoOpReminderPreferences(),
                new NoOpBroadcastVisibility(),
                forceParty,
                setPartyCount,
                addPartyCount,
                giveVote,
                resetVoterTotals,
                lookup,
                new SyncScheduler(),
                messages);
    }

    /** Resolves online players through the live mock server. */
    private final class ServerPlayerLookup implements PlayerLookup {
        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return Optional.ofNullable(server.getPlayerExact(name))
                    .map(p -> new PlayerRef(p.getUniqueId(), p.getName()));
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            return Optional.ofNullable(server.getPlayer(uuid)).map(p -> new PlayerRef(p.getUniqueId(), p.getName()));
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return server.getPlayer(uuid) != null;
        }
    }

    // --- recording fakes ---

    private static final class RecordingVoteRepository implements VoteRepository {

        final List<PlayerRef> totalsOfCalls = new ArrayList<>();
        final List<VotePeriod> topVotersCalls = new ArrayList<>();
        final List<PlayerRef> resetCalls = new ArrayList<>();
        List<VoteRanking> topResult = List.of();
        int incrementCalls = 0;
        /**
         * What {@code /vote claim} drains. Production collapses a player's queued rows into ONE batch (see
         * {@code JooqVoteRepository.selectBatch}), so this is at most a single-element list and {@code drainFor}
         * returns it verbatim; empty (the default) means an empty queue.
         */
        List<QueuedReward> pendingForClaim = List.of();

        @Override
        public int partyCount() {
            return 0;
        }

        @Override
        public void setPartyCount(int count) {}

        @Override
        public int incrementAndGetPartyCount() {
            incrementCalls++;
            // Return a value well below threshold so the party never fires during tests.
            return 1;
        }

        @Override
        public void enqueue(QueuedReward reward) {}

        @Override
        public List<QueuedReward> drainFor(PlayerRef player) {
            return pendingForClaim;
        }

        @Override
        public boolean hasPending(PlayerRef player) {
            return !pendingForClaim.isEmpty();
        }

        @Override
        public int queuedCount(PlayerRef player) {
            // One row per command in production, so count commands across the (collapsed) batch, not batches.
            return pendingForClaim.stream()
                    .mapToInt(batch -> batch.commands().size())
                    .sum();
        }

        @Override
        public VoteTally totalsOf(PlayerRef player) {
            totalsOfCalls.add(player);
            return VoteTally.empty();
        }

        @Override
        public void saveTotals(PlayerRef player, VoteTally tally) {}

        @Override
        public List<VoteRanking> topVoters(VotePeriod period, int limit) {
            topVotersCalls.add(period);
            return topResult;
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
        public void resetTotals(PlayerRef player) {
            resetCalls.add(player);
        }

        @Override
        public java.util.Optional<java.time.Instant> lastVoteAtSite(PlayerRef player, String site) {
            return java.util.Optional.empty();
        }

        @Override
        public void recordLastVoteAtSite(PlayerRef player, String site, java.time.Instant at) {}
    }

    /**
     * Records every MessageKey resolved (excluding the shared "prefix" infrastructure key) and
     * collects the {@code player} placeholder values so tests can assert the leaderboard renderer
     * used the real resolved name.
     */
    private static final class RecordingMessages implements Messages {
        final List<String> resolvedKeys = new ArrayList<>();
        final List<String> resolvedNames = new ArrayList<>();
        final Map<String, Map<String, String>> placeholdersByKey = new java.util.HashMap<>();

        /** The most recent non-prefix key resolved, or {@code null} if none yet. */
        @Nullable String lastKey() {
            return resolvedKeys.isEmpty() ? null : resolvedKeys.get(resolvedKeys.size() - 1);
        }

        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            // Skip the shared "prefix" infrastructure resolve: it is not a feature message.
            if (!"prefix".equals(key.key())) {
                resolvedKeys.add(key.key());
                placeholdersByKey.put(key.key(), Map.copyOf(placeholders));
            }
            String playerName = placeholders.get("player");
            if (playerName != null) {
                resolvedNames.add(playerName);
            }
            return key.key();
        }
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

    private static final class SyncScheduler implements Scheduler {
        @Override
        public void onGlobal(Runnable task) {
            task.run();
        }

        @Override
        public void onRegion(com.uxplima.uxmessentials.shared.domain.Position position, Runnable task) {
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

    private static final class NoOpReminderPreferences implements ReminderPreferences {
        @Override
        public boolean wantsReminders(PlayerRef who) {
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

    private static final class NoOpRewardApplier implements RewardApplier {
        @Override
        public void apply(PlayerRef voter, boolean online, RewardGrant grant) {}
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
}

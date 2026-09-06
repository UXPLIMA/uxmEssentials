package com.uxplima.uxmessentials.vote.adapter.inbound.listener;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.display.BroadcastChannel;
import com.uxplima.uxmessentials.shared.domain.DomainEvent;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.menu.TestMenuEngine;
import com.uxplima.uxmessentials.vote.adapter.VoteServices;
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
import com.uxplima.uxmessentials.vote.domain.VoterNameRules;
import com.uxplima.uxmessentials.vote.domain.reward.RewardCatalog;
import com.uxplima.uxmessentials.vote.domain.reward.RewardGrant;
import com.uxplima.uxmessentials.vote.domain.reward.RewardSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Covers the voter-name validation gate {@link VotifierListener#handleRaw} runs before a vote is built: a
 * blank, {@code "null"}, or over-16-character username is rejected, no vote is dispatched, while a valid
 * name flows through to {@link HandleVote}. The dispatch is witnessed through the repository's
 * {@code incrementAndGetPartyCount}, which {@link HandleVote} calls exactly once per handled vote, so a
 * rejected name leaves the counter untouched.
 */
class VotifierListenerNameValidationTest {

    private static final VoterNameRules RULES = VoterNameRules.of(16, "");

    private Plugin plugin;
    private CountingRepository repository;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        plugin = MockBukkit.createMockPlugin();
        repository = new CountingRepository();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void aValidNameIsHandled() {
        VotifierListener listener = listener();

        listener.handleRaw("PMC", "Alice");

        assertThat(repository.handled).isEqualTo(1);
    }

    @Test
    void aBlankNameIsRejected() {
        VotifierListener listener = listener();

        listener.handleRaw("PMC", "   ");

        assertThat(repository.handled).isZero();
    }

    @Test
    void theLiteralNullNameIsRejected() {
        VotifierListener listener = listener();

        listener.handleRaw("PMC", "null");

        assertThat(repository.handled).isZero();
    }

    @Test
    void anOverLongNameIsRejected() {
        VotifierListener listener = listener();

        listener.handleRaw("PMC", "ThisNameIsWayTooLongForMinecraft");

        assertThat(repository.handled).isZero();
    }

    @Test
    void aVoteUnderADifferentCaseCreditsTheAccountThatVoted() {
        PlayerRef account = new PlayerRef(UUID.randomUUID(), "Cofteey");
        VotifierListener listener =
                new VotifierListener(plugin, services(), new KnownLookup(account), RULES, new NoLog());

        listener.handleRaw("PMC", "cofteey");

        assertThat(repository.voter).isEqualTo(account);
    }

    @Test
    void aVoterTheServerHasNeverSeenIsStillCredited() {
        VotifierListener listener = listener();

        listener.handleRaw("PMC", "Newcomer");

        assertThat(repository.handled).isEqualTo(1);
        assertThat(repository.voter).isNotNull();
    }

    private VotifierListener listener() {
        return new VotifierListener(plugin, services(), new NoLookup(), RULES, new NoLog());
    }

    private VoteServices services() {
        Messages messages = (viewer, key, placeholders) -> key.key();
        Notifier notifier = new Notifier(messages, new NoSink());
        RewardSpec noOpSpec =
                new RewardSpec(100, Optional.empty(), List.of(), List.of(), List.of(), List.of(), Set.of());
        PartyConfig party = new PartyConfig(noOpSpec, 25, false, 0, PartyResetSchedule.NONE, List.of());
        NoOpAudience audience = new NoOpAudience();
        NoOpApplier applier = new NoOpApplier();
        NoEvents events = new NoEvents();
        VoteBroadcaster broadcaster = new NoOpBroadcaster();
        BroadcastSettings broadcastSettings =
                new BroadcastSettings(BroadcastType.NONE, Duration.ZERO, Set.of(BroadcastChannel.CHAT), Set.of());
        HandleVote handleVote = new HandleVote(
                repository,
                new RewardEngine(RewardCatalog.empty(), Set.of()),
                applier,
                new NoOpContext(),
                audience,
                broadcastSettings,
                broadcaster,
                new NoOpThrottle(),
                events,
                party,
                0,
                ZoneId.of("UTC"));
        ApplyQueuedRewards applyQueuedRewards = new ApplyQueuedRewards(repository, new NoOpDispatcher());
        VoteSitesMenu sitesGui = new VoteSitesMenu(
                TestMenuEngine.create(messages, new SyncScheduler()).menus(),
                messages,
                VoteSiteCatalog.empty(),
                repository,
                VoteSitesMenu.GuiConfig.defaults());
        return new VoteServices(
                handleVote,
                applyQueuedRewards,
                new VoteLinks(List.of(), notifier),
                sitesGui,
                new VotePartyStatus(repository, notifier, 25),
                new ShowVoteTotals(repository, notifier),
                new ShowVoteStreak(repository, notifier),
                new TopVoters(repository, notifier, 10),
                new ShowNextVote(repository, VoteSiteCatalog.empty(), notifier),
                new ShowLastVote(repository, VoteSiteCatalog.empty(), notifier),
                new VoteReminderEligibility(repository, VoteSiteCatalog.empty()),
                new NoOpReminderPreferences(),
                new NoOpBroadcastVisibility(),
                new ForceParty(
                        repository,
                        applier,
                        audience,
                        notifier,
                        broadcaster,
                        Set.of(BroadcastChannel.CHAT),
                        events,
                        party),
                new SetPartyCount(repository, notifier),
                new AddPartyCount(
                        repository,
                        applier,
                        audience,
                        notifier,
                        broadcaster,
                        Set.of(BroadcastChannel.CHAT),
                        events,
                        party),
                new GiveVote(handleVote, notifier),
                new ResetVoterTotals(repository, notifier),
                new NoLookup(),
                new SyncScheduler(),
                messages);
    }

    // --- fakes ---

    /** Counts handled votes via the increment {@link HandleVote} calls exactly once per vote. */
    private static final class CountingRepository implements VoteRepository {
        int handled = 0;

        /** The account the handled vote was credited to, witnessed as the tally is written. */
        @org.jspecify.annotations.Nullable PlayerRef voter = null;

        @Override
        public int incrementAndGetPartyCount() {
            handled++;
            return 1; // well below threshold so no party fires
        }

        @Override
        public int partyCount() {
            return 0;
        }

        @Override
        public void setPartyCount(int count) {}

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
        public void saveTotals(PlayerRef player, VoteTally tally) {
            voter = player;
        }

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
            return Optional.empty();
        }

        @Override
        public void recordLastVoteAtSite(PlayerRef player, String site, Instant at) {}
    }

    /** The kernel lookup as the decorated one behaves: one known account, resolvable by name in any case. */
    private record KnownLookup(PlayerRef account) implements PlayerLookup {
        @Override
        public Optional<PlayerRef> findOnlineByName(String name) {
            return Optional.empty();
        }

        @Override
        public Optional<PlayerRef> findByName(String name) {
            return account.name().equalsIgnoreCase(name) ? Optional.of(account) : Optional.empty();
        }

        @Override
        public Optional<PlayerRef> findByUuid(UUID uuid) {
            return account.uuid().equals(uuid) ? Optional.of(account) : Optional.empty();
        }

        @Override
        public boolean isOnline(UUID uuid) {
            return false;
        }
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

    private static final class NoOpDispatcher implements RewardDispatcher {
        @Override
        public void dispatch(List<String> commands, String playerName) {}
    }

    private static final class NoOpBroadcaster implements VoteBroadcaster {
        @Override
        public void broadcast(MessageKey key, Map<String, String> placeholders, Set<BroadcastChannel> channels) {}
    }

    private static final class NoOpThrottle implements BroadcastThrottle {
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

    private static final class NoLog implements Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}

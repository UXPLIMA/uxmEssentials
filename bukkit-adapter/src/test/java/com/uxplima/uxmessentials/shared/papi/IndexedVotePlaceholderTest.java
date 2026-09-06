package com.uxplima.uxmessentials.shared.papi;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import com.uxplima.uxmessentials.shared.adapter.outbound.papi.PlaceholderContexts;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.PlaceholderResolver;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.RepositoryVotePlaceholders;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.port.VoteRanking;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;
import com.uxplima.uxmessentials.vote.domain.QueuedReward;
import com.uxplima.uxmessentials.vote.domain.VotePeriod;
import com.uxplima.uxmessentials.vote.domain.VoteTally;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Verifies the indexed vote leaderboard placeholders in {@link RepositoryVotePlaceholders} and their
 * resolution through {@link PlaceholderResolver}: {@code votes_top_monthly_1_name},
 * {@code votes_top_monthly_1_votes}, {@code votes_position_monthly}, and edge cases (out-of-range
 * rank, absent player position, unknown field).
 */
class IndexedVotePlaceholderTest {

    private static final PlayerRef ALICE = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final PlayerRef BOB = new PlayerRef(UUID.randomUUID(), "Bob");
    private static final PlayerRef CHARLIE = new PlayerRef(UUID.randomUUID(), "Charlie");

    private StubVoteRepository repository;
    private RepositoryVotePlaceholders placeholders;
    private PlaceholderResolver resolver;

    /** Maps the three known players' UUIDs to their names; falls back to the UUID string otherwise. */
    private static String resolveName(UUID uuid) {
        return Map.of(
                        ALICE.uuid(), ALICE.name(),
                        BOB.uuid(), BOB.name(),
                        CHARLIE.uuid(), CHARLIE.name())
                .getOrDefault(uuid, uuid.toString());
    }

    @BeforeEach
    void setUp() {
        repository = new StubVoteRepository();
        placeholders = new RepositoryVotePlaceholders(repository, 25, IndexedVotePlaceholderTest::resolveName);
        PlaceholderContexts contexts =
                PlaceholderContexts.builder().vote(placeholders).build();
        resolver = new PlaceholderResolver(contexts);
    }

    // --- topAt ---

    @Test
    void topAtRank1NameReturnsFirstPlaceName() {
        repository.topResult = List.of(new VoteRanking(ALICE, 100), new VoteRanking(BOB, 80));

        Optional<String> result = resolver.resolve(CHARLIE, true, "votes_top_monthly_1_name");

        assertThat(result).contains("Alice");
    }

    @Test
    void topAtRank1VotesReturnsFirstPlaceCount() {
        repository.topResult = List.of(new VoteRanking(ALICE, 100));

        Optional<String> result = resolver.resolve(CHARLIE, true, "votes_top_monthly_1_votes");

        assertThat(result).contains("100");
    }

    @Test
    void topAtRank2NameReturnsSecondPlace() {
        repository.topResult = List.of(new VoteRanking(ALICE, 100), new VoteRanking(BOB, 80));

        Optional<String> result = resolver.resolve(CHARLIE, true, "votes_top_monthly_2_name");

        assertThat(result).contains("Bob");
    }

    @Test
    void topAtOutOfRangeRankDegradesToDash() {
        repository.topResult = List.of(new VoteRanking(ALICE, 100));

        // rank 5 is out of range (only 1 row).
        Optional<String> result = resolver.resolve(CHARLIE, true, "votes_top_monthly_5_name");

        assertThat(result).contains(PlaceholderResolver.EMPTY);
    }

    @Test
    void topAtUnknownFieldDegradesToDash() {
        repository.topResult = List.of(new VoteRanking(ALICE, 100));

        Optional<String> result = resolver.resolve(CHARLIE, true, "votes_top_monthly_1_uuid");

        assertThat(result).contains(PlaceholderResolver.EMPTY);
    }

    @Test
    void topAtDailyPeriodIsResolvedCorrectly() {
        repository.topResult = List.of(new VoteRanking(BOB, 50));

        Optional<String> result = resolver.resolve(CHARLIE, true, "votes_top_daily_1_name");

        assertThat(result).contains("Bob");
        assertThat(repository.lastQueriedPeriod).isEqualTo(VotePeriod.DAILY);
    }

    @Test
    void topAtNameUsesResolverWhenRowCarriesUuidString() {
        // Mirror the persistence layer: the ranked player's name is its UUID string.
        UUID steveUuid = UUID.randomUUID();
        repository.topResult = List.of(new VoteRanking(new PlayerRef(steveUuid, steveUuid.toString()), 100));
        Function<UUID, String> steveResolver = uuid -> uuid.equals(steveUuid) ? "Steve" : uuid.toString();
        RepositoryVotePlaceholders resolving = new RepositoryVotePlaceholders(repository, 25, steveResolver);
        PlaceholderResolver localResolver = new PlaceholderResolver(
                PlaceholderContexts.builder().vote(resolving).build());

        Optional<String> result = localResolver.resolve(CHARLIE, true, "votes_top_monthly_1_name");

        assertThat(result).contains("Steve");
    }

    @Test
    void topAtNameFallsBackToUuidWhenResolverUnknown() {
        UUID ghostUuid = UUID.randomUUID();
        repository.topResult = List.of(new VoteRanking(new PlayerRef(ghostUuid, ghostUuid.toString()), 50));
        // Resolver that never knows anyone: returns the UUID string.
        RepositoryVotePlaceholders resolving = new RepositoryVotePlaceholders(repository, 25, UUID::toString);
        PlaceholderResolver localResolver = new PlaceholderResolver(
                PlaceholderContexts.builder().vote(resolving).build());

        Optional<String> result = localResolver.resolve(CHARLIE, true, "votes_top_monthly_1_name");

        assertThat(result).contains(ghostUuid.toString());
    }

    // --- positionOf ---

    @Test
    void positionOfReturnsOneBasedRank() {
        repository.topResult =
                List.of(new VoteRanking(ALICE, 100), new VoteRanking(BOB, 80), new VoteRanking(CHARLIE, 50));

        Optional<String> result = resolver.resolve(CHARLIE, true, "votes_position_monthly");

        assertThat(result).contains("3");
    }

    @Test
    void positionOfAbsentPlayerDegradesToDash() {
        repository.topResult = List.of(new VoteRanking(ALICE, 100));
        PlayerRef nobody = new PlayerRef(UUID.randomUUID(), "Nobody");

        Optional<String> result = resolver.resolve(nobody, true, "votes_position_monthly");

        assertThat(result).contains(PlaceholderResolver.EMPTY);
    }

    @Test
    void positionOfAlltimePeriodIsResolved() {
        repository.topResult = List.of(new VoteRanking(ALICE, 200));

        Optional<String> result = resolver.resolve(ALICE, true, "votes_position_alltime");

        assertThat(result).contains("1");
        assertThat(repository.lastQueriedPeriod).isEqualTo(VotePeriod.ALLTIME);
    }

    // --- plain count keys still work ---

    @Test
    void plainVotesMonthlyKeyStillReturnsCount() {
        repository.monthlyCount = 42L;

        Optional<String> result = resolver.resolve(ALICE, true, "votes_monthly");

        assertThat(result).contains("42");
    }

    // --- streak keys ---

    @Test
    void votesStreakCurrentReturnsCurrentStreak() {
        repository.currentStreak = 5L;

        Optional<String> result = resolver.resolve(ALICE, true, "votes_streak_current");

        assertThat(result).contains("5");
    }

    @Test
    void votesStreakBestReturnsBestStreak() {
        repository.bestStreak = 12L;

        Optional<String> result = resolver.resolve(ALICE, true, "votes_streak_best");

        assertThat(result).contains("12");
    }

    @Test
    void votesStreakUnknownFieldDegradesToDash() {
        Optional<String> result = resolver.resolve(ALICE, true, "votes_streak_longest");

        assertThat(result).contains(PlaceholderResolver.EMPTY);
    }

    // --- stubs ---

    private static final class StubVoteRepository implements VoteRepository {
        List<VoteRanking> topResult = List.of();
        VotePeriod lastQueriedPeriod = VotePeriod.MONTHLY;
        long monthlyCount = 0L;
        long currentStreak = 0L;
        long bestStreak = 0L;

        @Override
        public int partyCount() {
            return 0;
        }

        @Override
        public void setPartyCount(int count) {}

        @Override
        public int incrementAndGetPartyCount() {
            return 0;
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
            // alltime, daily, weekly, monthly, dayKey, weekKey, monthKey, currentStreak, bestStreak, streakDayKey
            return new VoteTally(monthlyCount, 0L, 0L, monthlyCount, 0L, 0L, 0L, currentStreak, bestStreak, 0L);
        }

        @Override
        public void saveTotals(PlayerRef player, VoteTally tally) {}

        @Override
        public List<VoteRanking> topVoters(VotePeriod period, int limit) {
            lastQueriedPeriod = period;
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
        public void resetTotals(PlayerRef player) {}

        @Override
        public Optional<Instant> lastVoteAtSite(PlayerRef player, String site) {
            return Optional.empty();
        }

        @Override
        public void recordLastVoteAtSite(PlayerRef player, String site, Instant at) {}
    }
}

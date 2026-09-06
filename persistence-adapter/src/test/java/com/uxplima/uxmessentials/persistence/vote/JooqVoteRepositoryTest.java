package com.uxplima.uxmessentials.persistence.vote;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.port.VoteRanking;
import com.uxplima.uxmessentials.vote.domain.QueuedReward;
import com.uxplima.uxmessentials.vote.domain.VotePeriod;
import com.uxplima.uxmessentials.vote.domain.VoteTally;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end coverage of {@link JooqVoteRepository} against the embedded SQLite backend with the Flyway
 * baseline. Covers the atomic party-counter increment, offline-reward queue ordering and drain-once
 * semantics, and the V24 {@code vote_totals} table: totals round-trip, upsert idempotency, leaderboard
 * ordering and limit, empty-DB behaviour, unknown-player defaults, and the V27 streak columns
 * (current/best streak and streak day-key) round-tripping and upserting.
 */
class JooqVoteRepositoryTest {

    private Persistence persistence;
    private JooqVoteRepository repository;
    private PlayerRef bob;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(new SqliteConfig(), dataFolder, List.of("db/migration"), new NoopLogger());
        repository = new JooqVoteRepository(persistence.dsl());
        bob = new PlayerRef(UUID.randomUUID(), "Bob");
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    void incrementReturnsThePostIncrementValueAndPersistsIt() {
        assertThat(repository.partyCount()).isZero();

        assertThat(repository.incrementAndGetPartyCount()).isEqualTo(1);
        assertThat(repository.incrementAndGetPartyCount()).isEqualTo(2);
        assertThat(repository.incrementAndGetPartyCount()).isEqualTo(3);

        assertThat(repository.partyCount()).isEqualTo(3);
    }

    @Test
    void incrementContinuesFromAnExplicitlySetCount() {
        repository.setPartyCount(24);

        assertThat(repository.incrementAndGetPartyCount()).isEqualTo(25);
        assertThat(repository.partyCount()).isEqualTo(25);
    }

    @Test
    void successiveEnqueuesForOnePlayerKeepClimbingTheIndexWithoutColliding() {
        repository.enqueue(reward(bob, "give {player} diamond 1"));
        repository.enqueue(reward(bob, "give {player} emerald 2"));
        repository.enqueue(reward(bob, "give {player} apple 3"));

        assertThat(repository.hasPending(bob)).isTrue();

        List<QueuedReward> drained = repository.drainFor(bob);

        assertThat(drained).hasSize(1);
        assertThat(drained.get(0).commands())
                .containsExactly("give {player} diamond 1", "give {player} emerald 2", "give {player} apple 3");
        assertThat(repository.hasPending(bob)).isFalse();
    }

    @Test
    void aMultiCommandBatchKeepsItsOrderAcrossADrain() {
        repository.enqueue(new QueuedReward(bob, List.of("first", "second", "third"), Instant.EPOCH));

        List<QueuedReward> drained = repository.drainFor(bob);

        assertThat(drained).hasSize(1);
        assertThat(drained.get(0).commands()).containsExactly("first", "second", "third");
    }

    @Test
    void queuedCountIsZeroForAFreshPlayer() {
        assertThat(repository.queuedCount(bob)).isZero();
    }

    @Test
    void queuedCountReflectsTheNumberOfCommandsInTheQueue() {
        repository.enqueue(
                new QueuedReward(bob, List.of("give {player} diamond 1", "give {player} emerald 2"), Instant.EPOCH));

        assertThat(repository.queuedCount(bob)).isEqualTo(2);
    }

    @Test
    void queuedCountReturnsToZeroAfterDrain() {
        repository.enqueue(new QueuedReward(bob, List.of("first", "second"), Instant.EPOCH));

        repository.drainFor(bob);

        assertThat(repository.queuedCount(bob)).isZero();
    }

    @Test
    void queuedCountIsIndependentPerPlayer() {
        PlayerRef alice = new PlayerRef(UUID.randomUUID(), "Alice");
        repository.enqueue(new QueuedReward(bob, List.of("one", "two", "three"), Instant.EPOCH));

        assertThat(repository.queuedCount(bob)).isEqualTo(3);
        assertThat(repository.queuedCount(alice)).isZero();
    }

    // -------------------------------------------------------------------------
    // vote_totals: totalsOf / saveTotals / topVoters
    // -------------------------------------------------------------------------

    @Test
    void totalsOfUnknownPlayerReturnsEmpty() {
        PlayerRef stranger = new PlayerRef(UUID.randomUUID(), "Stranger");
        assertThat(repository.totalsOf(stranger)).isEqualTo(VoteTally.empty());
    }

    @Test
    void saveTotalsRoundTrip() {
        VoteTally tally = new VoteTally(42L, 3L, 7L, 12L, 19900L, 202401L, 24289L, 0L, 0L, 0L);
        repository.saveTotals(bob, tally);
        assertThat(repository.totalsOf(bob)).isEqualTo(tally);
    }

    @Test
    void saveTotalsUpsertsWithoutDuplicating() {
        VoteTally first = new VoteTally(1L, 1L, 1L, 1L, 100L, 202401L, 24277L, 0L, 0L, 0L);
        VoteTally second = new VoteTally(2L, 2L, 2L, 2L, 101L, 202401L, 24278L, 0L, 0L, 0L);

        repository.saveTotals(bob, first);
        repository.saveTotals(bob, second);

        assertThat(repository.totalsOf(bob)).isEqualTo(second);
    }

    @Test
    void saveTotalsRoundTripsStreakFields() {
        VoteTally tally = new VoteTally(42L, 3L, 7L, 12L, 19900L, 202401L, 24289L, 5L, 9L, 20000L);

        repository.saveTotals(bob, tally);

        VoteTally read = repository.totalsOf(bob);
        assertThat(read.currentStreak()).isEqualTo(5L);
        assertThat(read.bestStreak()).isEqualTo(9L);
        assertThat(read.streakDayKey()).isEqualTo(20000L);
        assertThat(read).isEqualTo(tally);
    }

    @Test
    void saveTotalsUpsertUpdatesStreakFields() {
        VoteTally first = new VoteTally(10L, 1L, 1L, 1L, 100L, 202401L, 24277L, 3L, 3L, 19998L);
        VoteTally second = new VoteTally(11L, 2L, 2L, 2L, 101L, 202401L, 24278L, 4L, 7L, 19999L);

        repository.saveTotals(bob, first);
        repository.saveTotals(bob, second);

        VoteTally read = repository.totalsOf(bob);
        assertThat(read.currentStreak()).isEqualTo(4L);
        assertThat(read.bestStreak()).isEqualTo(7L);
        assertThat(read.streakDayKey()).isEqualTo(19999L);
        assertThat(read).isEqualTo(second);
    }

    @Test
    void totalsOfUnknownPlayerHasZeroStreakFields() {
        PlayerRef stranger = new PlayerRef(UUID.randomUUID(), "Stranger");

        VoteTally read = repository.totalsOf(stranger);

        assertThat(read).isEqualTo(VoteTally.empty());
        assertThat(read.currentStreak()).isZero();
        assertThat(read.bestStreak()).isZero();
        assertThat(read.streakDayKey()).isZero();
    }

    @Test
    void topVotersReturnsPlayersOrderedByPeriodColumnDescending() {
        PlayerRef alice = new PlayerRef(UUID.randomUUID(), "Alice");
        PlayerRef charlie = new PlayerRef(UUID.randomUUID(), "Charlie");

        // Monthly: alice=5, bob=3, charlie=10, expected order: charlie, alice, bob.
        repository.saveTotals(alice, new VoteTally(10L, 2L, 4L, 5L, 100L, 202401L, 24277L, 0L, 0L, 0L));
        repository.saveTotals(bob, new VoteTally(5L, 1L, 2L, 3L, 100L, 202401L, 24277L, 0L, 0L, 0L));
        repository.saveTotals(charlie, new VoteTally(15L, 4L, 8L, 10L, 100L, 202401L, 24277L, 0L, 0L, 0L));

        List<VoteRanking> top = repository.topVoters(VotePeriod.MONTHLY, 10);

        assertThat(top).extracting(VoteRanking::votes).containsExactly(10L, 5L, 3L);
    }

    @Test
    void topVotersRespectsLimit() {
        PlayerRef alice = new PlayerRef(UUID.randomUUID(), "Alice");
        PlayerRef charlie = new PlayerRef(UUID.randomUUID(), "Charlie");

        repository.saveTotals(alice, new VoteTally(10L, 2L, 4L, 5L, 100L, 202401L, 24277L, 0L, 0L, 0L));
        repository.saveTotals(bob, new VoteTally(5L, 1L, 2L, 3L, 100L, 202401L, 24277L, 0L, 0L, 0L));
        repository.saveTotals(charlie, new VoteTally(15L, 4L, 8L, 10L, 100L, 202401L, 24277L, 0L, 0L, 0L));

        List<VoteRanking> top = repository.topVoters(VotePeriod.MONTHLY, 2);

        assertThat(top).hasSize(2);
        assertThat(top.get(0).votes()).isEqualTo(10L);
        assertThat(top.get(1).votes()).isEqualTo(5L);
    }

    @Test
    void topVotersExcludesZeroCountRows() {
        // A player whose alltime count is zero should not appear in the leaderboard.
        repository.saveTotals(bob, new VoteTally(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L));

        List<VoteRanking> top = repository.topVoters(VotePeriod.ALLTIME, 10);

        assertThat(top).isEmpty();
    }

    @Test
    void topVotersOnEmptyDatabaseReturnsEmptyList() {
        assertThat(repository.topVoters(VotePeriod.WEEKLY, 10)).isEmpty();
    }

    @Test
    void topVotersAlltimePeriodOrdersByAlltimeColumn() {
        PlayerRef alice = new PlayerRef(UUID.randomUUID(), "Alice");

        repository.saveTotals(alice, new VoteTally(100L, 1L, 1L, 1L, 100L, 202401L, 24277L, 0L, 0L, 0L));
        repository.saveTotals(bob, new VoteTally(200L, 1L, 1L, 1L, 100L, 202401L, 24277L, 0L, 0L, 0L));

        List<VoteRanking> top = repository.topVoters(VotePeriod.ALLTIME, 10);

        assertThat(top).extracting(VoteRanking::votes).containsExactly(200L, 100L);
    }

    // -------------------------------------------------------------------------
    // Party participants: markPartyParticipant / partyParticipants / clearPartyParticipants
    // -------------------------------------------------------------------------

    @Test
    void partyParticipantsIsEmptyOnFreshDatabase() {
        assertThat(repository.partyParticipants()).isEmpty();
    }

    @Test
    void markPartyParticipantAddsPlayerToSet() {
        repository.markPartyParticipant(bob);

        Set<UUID> participants = repository.partyParticipants();

        assertThat(participants).containsExactly(bob.uuid());
    }

    @Test
    void markPartyParticipantIsIdempotent() {
        repository.markPartyParticipant(bob);
        repository.markPartyParticipant(bob);

        assertThat(repository.partyParticipants()).hasSize(1);
    }

    @Test
    void clearPartyParticipantsEmptiesTheSet() {
        PlayerRef alice = new PlayerRef(UUID.randomUUID(), "Alice");
        repository.markPartyParticipant(bob);
        repository.markPartyParticipant(alice);

        repository.clearPartyParticipants();

        assertThat(repository.partyParticipants()).isEmpty();
    }

    @Test
    void multipleParticipantsAreAllReturned() {
        PlayerRef alice = new PlayerRef(UUID.randomUUID(), "Alice");
        PlayerRef charlie = new PlayerRef(UUID.randomUUID(), "Charlie");

        repository.markPartyParticipant(bob);
        repository.markPartyParticipant(alice);
        repository.markPartyParticipant(charlie);

        assertThat(repository.partyParticipants()).containsExactlyInAnyOrder(bob.uuid(), alice.uuid(), charlie.uuid());
    }

    // -------------------------------------------------------------------------
    // Party period key: partyPeriodKey / setPartyPeriodKey
    // -------------------------------------------------------------------------

    @Test
    void partyPeriodKeyIsZeroOnFreshDatabase() {
        assertThat(repository.partyPeriodKey()).isZero();
    }

    @Test
    void setPartyPeriodKeyRoundTrips() {
        repository.setPartyPeriodKey(20240601L);

        assertThat(repository.partyPeriodKey()).isEqualTo(20240601L);
    }

    @Test
    void setPartyPeriodKeyOverwritesPreviousValue() {
        repository.setPartyPeriodKey(20240101L);
        repository.setPartyPeriodKey(20240601L);

        assertThat(repository.partyPeriodKey()).isEqualTo(20240601L);
    }

    // -------------------------------------------------------------------------
    // Threshold override: thresholdOverride / setThresholdOverride
    // -------------------------------------------------------------------------

    @Test
    void thresholdOverrideIsZeroOnFreshDatabase() {
        assertThat(repository.thresholdOverride()).isZero();
    }

    @Test
    void setThresholdOverrideRoundTrips() {
        repository.setThresholdOverride(50);

        assertThat(repository.thresholdOverride()).isEqualTo(50);
    }

    @Test
    void setThresholdOverrideToZeroClearsOverride() {
        repository.setThresholdOverride(25);
        repository.setThresholdOverride(0);

        assertThat(repository.thresholdOverride()).isZero();
    }

    // -------------------------------------------------------------------------
    // resetTotals
    // -------------------------------------------------------------------------

    @Test
    void resetTotalsClearsPlayerTotals() {
        VoteTally tally = new VoteTally(10L, 3L, 5L, 7L, 100L, 202401L, 24277L, 0L, 0L, 0L);
        repository.saveTotals(bob, tally);

        repository.resetTotals(bob);

        assertThat(repository.totalsOf(bob)).isEqualTo(VoteTally.empty());
    }

    @Test
    void resetTotalsOnUnknownPlayerIsANoop() {
        PlayerRef stranger = new PlayerRef(UUID.randomUUID(), "Stranger");

        // Should not throw.
        repository.resetTotals(stranger);

        assertThat(repository.totalsOf(stranger)).isEqualTo(VoteTally.empty());
    }

    @Test
    void resetTotalsDoesNotAffectOtherPlayers() {
        PlayerRef alice = new PlayerRef(UUID.randomUUID(), "Alice");
        VoteTally aliceTally = new VoteTally(5L, 1L, 2L, 3L, 100L, 202401L, 24277L, 0L, 0L, 0L);
        VoteTally bobTally = new VoteTally(8L, 2L, 3L, 4L, 100L, 202401L, 24277L, 0L, 0L, 0L);

        repository.saveTotals(alice, aliceTally);
        repository.saveTotals(bob, bobTally);

        repository.resetTotals(bob);

        assertThat(repository.totalsOf(alice)).isEqualTo(aliceTally);
        assertThat(repository.totalsOf(bob)).isEqualTo(VoteTally.empty());
    }

    // -------------------------------------------------------------------------
    // claimPartyFire: atomic compare-and-reset
    // -------------------------------------------------------------------------

    @Test
    void claimPartyFireReturnsTrueAndResetsCountWhenAtThreshold() {
        repository.setPartyCount(25);

        assertThat(repository.claimPartyFire(25)).isTrue();
        assertThat(repository.partyCount()).isZero();
    }

    @Test
    void claimPartyFireReturnsTrueAndResetsCountWhenAboveThreshold() {
        repository.setPartyCount(26);

        assertThat(repository.claimPartyFire(25)).isTrue();
        assertThat(repository.partyCount()).isZero();
    }

    @Test
    void claimPartyFireReturnsFalseWhenCountBelowThreshold() {
        repository.setPartyCount(24);

        assertThat(repository.claimPartyFire(25)).isFalse();
        // Counter was not touched.
        assertThat(repository.partyCount()).isEqualTo(24);
    }

    @Test
    void claimPartyFireReturnsFalseOnSecondCallAfterFirstWon() {
        repository.setPartyCount(25);

        boolean firstWin = repository.claimPartyFire(25);
        boolean secondWin = repository.claimPartyFire(25);

        assertThat(firstWin).isTrue();
        assertThat(secondWin).isFalse();
        assertThat(repository.partyCount()).isZero();
    }

    // -------------------------------------------------------------------------
    // Per-site cooldown: lastVoteAtSite / recordLastVoteAtSite
    // -------------------------------------------------------------------------

    @Test
    void lastVoteAtSiteReturnsEmptyWhenNoRowExists() {
        assertThat(repository.lastVoteAtSite(bob, "PMC")).isEmpty();
    }

    @Test
    void recordAndReadLastVoteAtSiteRoundTrip() {
        // Epoch millis is the column precision; truncate the test instant to millis so the
        // stored and expected values agree without relying on sub-millisecond equality.
        Instant recorded = Instant.ofEpochMilli(Instant.now().toEpochMilli());

        repository.recordLastVoteAtSite(bob, "PMC", recorded);

        assertThat(repository.lastVoteAtSite(bob, "PMC")).hasValue(recorded);
    }

    @Test
    void recordLastVoteAtSiteOverwritesPreviousTimestamp() {
        Instant first = Instant.ofEpochMilli(1_000_000L);
        Instant second = Instant.ofEpochMilli(2_000_000L);

        repository.recordLastVoteAtSite(bob, "PMC", first);
        repository.recordLastVoteAtSite(bob, "PMC", second);

        assertThat(repository.lastVoteAtSite(bob, "PMC")).hasValue(second);
    }

    @Test
    void recordLastVoteAtSiteIsCaseInsensitiveOnLookup() {
        Instant recorded = Instant.ofEpochMilli(1_234_567_890L);

        // Write with mixed case; the repository normalises to lowercase on write.
        repository.recordLastVoteAtSite(bob, "PMC", recorded);

        // Reading with the lowercase form must still find the row.
        assertThat(repository.lastVoteAtSite(bob, "pmc")).hasValue(recorded);
    }

    @Test
    void twoDifferentSitesForOnePlayerAreIndependent() {
        Instant pmcInstant = Instant.ofEpochMilli(1_000_000L);
        Instant mcInstant = Instant.ofEpochMilli(2_000_000L);

        repository.recordLastVoteAtSite(bob, "PMC", pmcInstant);
        repository.recordLastVoteAtSite(bob, "Minecraftservers", mcInstant);

        assertThat(repository.lastVoteAtSite(bob, "pmc")).hasValue(pmcInstant);
        assertThat(repository.lastVoteAtSite(bob, "minecraftservers")).hasValue(mcInstant);
    }

    // -------------------------------------------------------------------------
    // Regression: existing party-count increment still works after schema change
    // -------------------------------------------------------------------------

    @Test
    void partyCountIncrementUnaffectedByNewColumns() {
        // Verify the id=1 row insert path (which now has new columns defaulting to 0) still works.
        assertThat(repository.incrementAndGetPartyCount()).isEqualTo(1);
        assertThat(repository.incrementAndGetPartyCount()).isEqualTo(2);

        // New columns should read their defaults when the row was first written by the counter.
        assertThat(repository.partyPeriodKey()).isZero();
        assertThat(repository.thresholdOverride()).isZero();
    }

    private static QueuedReward reward(PlayerRef player, String command) {
        return new QueuedReward(player, List.of(command), Instant.EPOCH);
    }

    /** A config that selects the embedded SQLite backend with every default: no network coordinates. */
    private record SqliteConfig() implements ConfigStore {
        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return fallback;
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
}

package com.uxplima.uxmessentials.vote.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

/**
 * Rollover semantics for {@link VoteTally}: each periodic bucket resets when the calendar window
 * advances, alltime never resets, and the compact constructor rejects negative inputs.
 */
class VoteTallyTest {

    private static final ZoneId UTC = ZoneId.of("UTC");

    // 2024-01-15 12:00:00 UTC, a Monday, ISO week 3 of 2024
    private static final Instant JAN_15 = Instant.parse("2024-01-15T12:00:00Z");
    // 2024-01-16 12:00:00 UTC, the next day (still ISO week 3 of 2024)
    private static final Instant JAN_16 = Instant.parse("2024-01-16T12:00:00Z");
    // 2024-01-17 12:00:00 UTC. Two days after Jan 15 (one day missed if voting jumps Jan 15 → Jan 17)
    private static final Instant JAN_17 = Instant.parse("2024-01-17T12:00:00Z");
    // 2024-02-25 12:00:00 UTC, 41 days after Jan 15 (a different ISO week and a different month)
    private static final Instant FEB_25 = Instant.parse("2024-02-25T12:00:00Z");
    // 2025-01-15 12:00:00 UTC. A different year, different everything
    private static final Instant JAN_15_NEXT_YEAR = Instant.parse("2025-01-15T12:00:00Z");

    @Test
    void emptyTallyHasAllZeros() {
        VoteTally empty = VoteTally.empty();

        assertThat(empty.alltime()).isZero();
        assertThat(empty.daily()).isZero();
        assertThat(empty.weekly()).isZero();
        assertThat(empty.monthly()).isZero();
        assertThat(empty.dayKey()).isZero();
        assertThat(empty.weekKey()).isZero();
        assertThat(empty.monthKey()).isZero();
        assertThat(empty.currentStreak()).isZero();
        assertThat(empty.bestStreak()).isZero();
        assertThat(empty.streakDayKey()).isZero();
    }

    @Test
    void recordVoteTwiceOnTheSameDayAccumulatesEachBucket() {
        VoteTally after1 = VoteTally.empty().recordVote(JAN_15, UTC);
        VoteTally after2 = after1.recordVote(JAN_15, UTC);

        assertThat(after2.alltime()).isEqualTo(2);
        assertThat(after2.daily()).isEqualTo(2);
        assertThat(after2.weekly()).isEqualTo(2);
        assertThat(after2.monthly()).isEqualTo(2);
    }

    @Test
    void recordVoteOnANewDayResetsDailyButWeeklyAndMonthlyKeepClimbing() {
        VoteTally after1 = VoteTally.empty().recordVote(JAN_15, UTC);
        VoteTally after2 = after1.recordVote(JAN_16, UTC);

        assertThat(after2.alltime()).isEqualTo(2);
        assertThat(after2.daily()).isEqualTo(1); // new day → reset to 1
        assertThat(after2.weekly()).isEqualTo(2); // same ISO week → keeps climbing
        assertThat(after2.monthly()).isEqualTo(2); // same month → keeps climbing
    }

    @Test
    void recordVoteInANewMonthResetsMonthlyAndDailyButAlltimeStillClimbs() {
        VoteTally after1 = VoteTally.empty().recordVote(JAN_15, UTC);
        VoteTally after2 = after1.recordVote(FEB_25, UTC);

        assertThat(after2.alltime()).isEqualTo(2);
        assertThat(after2.daily()).isEqualTo(1); // different day
        assertThat(after2.weekly()).isEqualTo(1); // different ISO week
        assertThat(after2.monthly()).isEqualTo(1); // different month → reset to 1
    }

    @Test
    void alltimeNeverResetsAcrossYears() {
        VoteTally t = VoteTally.empty()
                .recordVote(JAN_15, UTC)
                .recordVote(JAN_16, UTC)
                .recordVote(FEB_25, UTC)
                .recordVote(JAN_15_NEXT_YEAR, UTC);

        assertThat(t.alltime()).isEqualTo(4);
        assertThat(t.daily()).isEqualTo(1);
        assertThat(t.weekly()).isEqualTo(1);
        assertThat(t.monthly()).isEqualTo(1);
    }

    @Test
    void countForDelegatesToTheCorrectField() {
        VoteTally t = VoteTally.empty().recordVote(JAN_15, UTC).recordVote(JAN_15, UTC);

        assertThat(t.countFor(VotePeriod.ALLTIME)).isEqualTo(t.alltime());
        assertThat(t.countFor(VotePeriod.DAILY)).isEqualTo(t.daily());
        assertThat(t.countFor(VotePeriod.WEEKLY)).isEqualTo(t.weekly());
        assertThat(t.countFor(VotePeriod.MONTHLY)).isEqualTo(t.monthly());
    }

    @Test
    void negativeAlltimeIsRejected() {
        assertThatThrownBy(() -> new VoteTally(-1, 0, 0, 0, 0, 0, 0, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("alltime");
    }

    @Test
    void negativeDayKeyIsRejected() {
        assertThatThrownBy(() -> new VoteTally(0, 0, 0, 0, -1, 0, 0, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dayKey");
    }

    @Test
    void negativeCurrentStreakIsRejected() {
        assertThatThrownBy(() -> new VoteTally(0, 0, 0, 0, 0, 0, 0, -1, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("currentStreak");
    }

    @Test
    void negativeBestStreakIsRejected() {
        assertThatThrownBy(() -> new VoteTally(0, 0, 0, 0, 0, 0, 0, 0, -1, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("bestStreak");
    }

    @Test
    void negativeStreakDayKeyIsRejected() {
        assertThatThrownBy(() -> new VoteTally(0, 0, 0, 0, 0, 0, 0, 0, 0, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("streakDayKey");
    }

    @Test
    void firstVoteStartsTheStreakAtOneAndSetsTheBest() {
        VoteTally after = VoteTally.empty().recordVote(JAN_15, UTC);

        assertThat(after.currentStreak()).isEqualTo(1);
        assertThat(after.bestStreak()).isEqualTo(1);
        assertThat(after.streakDayKey())
                .isEqualTo(LocalDate.ofInstant(JAN_15, UTC).toEpochDay());
    }

    @Test
    void aSameDayReVoteLeavesTheStreakUnchanged() {
        VoteTally after = VoteTally.empty().recordVote(JAN_15, UTC).recordVote(JAN_15, UTC);

        assertThat(after.currentStreak()).isEqualTo(1);
        assertThat(after.bestStreak()).isEqualTo(1);
    }

    @Test
    void aNextDayVoteExtendsTheStreakAndBestTracksIt() {
        VoteTally after = VoteTally.empty().recordVote(JAN_15, UTC).recordVote(JAN_16, UTC);

        assertThat(after.currentStreak()).isEqualTo(2);
        assertThat(after.bestStreak()).isEqualTo(2);
        assertThat(after.streakDayKey())
                .isEqualTo(LocalDate.ofInstant(JAN_16, UTC).toEpochDay());
    }

    @Test
    void aTwoDayGapWithNoGraceResetsTheStreakToOne() {
        // JAN_15 then JAN_17: one day missed, grace 0 → streak breaks.
        VoteTally after = VoteTally.empty().recordVote(JAN_15, UTC).recordVote(JAN_17, UTC, 0);

        assertThat(after.currentStreak()).isEqualTo(1);
        // best still reflects the high-water mark, which here is also 1.
        assertThat(after.bestStreak()).isEqualTo(1);
    }

    @Test
    void aTwoDayGapWithOneGraceDayContinuesTheStreak() {
        // JAN_15 then JAN_17: one day missed, grace 1 → streak continues.
        VoteTally after = VoteTally.empty().recordVote(JAN_15, UTC).recordVote(JAN_17, UTC, 1);

        assertThat(after.currentStreak()).isEqualTo(2);
        assertThat(after.bestStreak()).isEqualTo(2);
    }

    @Test
    void bestStreakNeverDecreasesWhenTheCurrentStreakBreaks() {
        // Build a streak of 3, then break it with a long gap; best stays at 3.
        VoteTally streak3 = VoteTally.empty()
                .recordVote(JAN_15, UTC)
                .recordVote(JAN_16, UTC)
                .recordVote(JAN_17, UTC);
        assertThat(streak3.currentStreak()).isEqualTo(3);
        assertThat(streak3.bestStreak()).isEqualTo(3);

        VoteTally broken = streak3.recordVote(FEB_25, UTC);
        assertThat(broken.currentStreak()).isEqualTo(1);
        assertThat(broken.bestStreak()).isEqualTo(3);
    }

    @Test
    void streakDayKeyAdoptsTheCurrentDayOnEveryVote() {
        VoteTally after = VoteTally.empty().recordVote(JAN_15, UTC).recordVote(FEB_25, UTC);

        assertThat(after.streakDayKey())
                .isEqualTo(LocalDate.ofInstant(FEB_25, UTC).toEpochDay());
    }
}

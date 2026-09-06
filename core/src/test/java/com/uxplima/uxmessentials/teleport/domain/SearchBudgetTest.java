package com.uxplima.uxmessentials.teleport.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Pins the pure {@link SearchBudget}: it rejects non-positive ceilings at construction and answers
 * {@link SearchBudget#allowsAnotherAttempt(int, int, long)} by letting a search continue only while every
 * ceiling (attempts, chunk loads, and elapsed wall clock) still has room. Any one ceiling reaching its
 * cap stops the search, which is what guarantees termination.
 */
class SearchBudgetTest {

    private final SearchBudget budget = new SearchBudget(5, 3, 1000);

    @Test
    void rejectsNonPositiveCeilings() {
        assertThatThrownBy(() -> new SearchBudget(0, 3, 1000)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SearchBudget(5, 0, 1000)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SearchBudget(5, 3, 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allowsAnotherAttemptWhileEveryCeilingHasRoom() {
        assertThat(budget.allowsAnotherAttempt(0, 0, 0)).isTrue();
        assertThat(budget.allowsAnotherAttempt(4, 2, 999)).isTrue();
    }

    @Test
    void stopsWhenTheAttemptCeilingIsReached() {
        assertThat(budget.allowsAnotherAttempt(5, 0, 0)).isFalse();
    }

    @Test
    void stopsWhenTheChunkLoadCeilingIsReached() {
        assertThat(budget.allowsAnotherAttempt(0, 3, 0)).isFalse();
    }

    @Test
    void stopsWhenTheWallClockCeilingIsReached() {
        assertThat(budget.allowsAnotherAttempt(0, 0, 1000)).isFalse();
    }
}

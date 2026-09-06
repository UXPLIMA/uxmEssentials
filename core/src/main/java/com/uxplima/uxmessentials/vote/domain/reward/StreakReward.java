package com.uxplima.uxmessentials.vote.domain.reward;

import java.util.Objects;
import java.util.OptionalLong;

/**
 * A streak reward keyed off a player's current consecutive-day voting streak. Exactly one of {@code at}
 * (a one-shot streak length paid the single time the streak reaches it) or {@code every} (a recurring
 * length paid each time the streak is a positive multiple of it) is present; both being present or both
 * absent is a config error the constructor rejects. The present value must be strictly positive.
 *
 * <p>Mirrors {@link MilestoneReward}, but it fires on the day the streak advances rather than on an
 * all-time count. The engine only evaluates these when the streak actually grew, so a same-day re-vote
 * never re-pays a streak reward.
 *
 * @param at the one-shot streak length to pay at, or empty when this is a recurring streak reward
 * @param every the recurring step to pay on multiples of, or empty when this is a one-shot streak reward
 * @param reward the spec to pay when the streak matches
 */
public record StreakReward(OptionalLong at, OptionalLong every, RewardSpec reward) {

    public StreakReward {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(every, "every");
        Objects.requireNonNull(reward, "reward");
        if (at.isPresent() == every.isPresent()) {
            throw new IllegalArgumentException("exactly one of at/every must be present");
        }
        if (at.isPresent() && at.getAsLong() <= 0) {
            throw new IllegalArgumentException("at must be positive: " + at.getAsLong());
        }
        if (every.isPresent() && every.getAsLong() <= 0) {
            throw new IllegalArgumentException("every must be positive: " + every.getAsLong());
        }
    }

    /** True when this reward fires at the given streak length: equality for {@code at}, a positive multiple for {@code every}. */
    public boolean matches(long streak) {
        return at.isPresent() ? streak == at.getAsLong() : streak > 0 && streak % every.getAsLong() == 0;
    }
}

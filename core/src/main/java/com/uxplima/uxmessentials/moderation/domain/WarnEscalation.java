package com.uxplima.uxmessentials.moderation.domain;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The warn-escalation ladder: a list of steps that turn a player's running warning count into an automatic
 * follow-up sanction. An operator configures rungs like "on the 3rd warning, tempmute for an hour; on the 5th,
 * tempban for a day"; this value object decides which rung, if any, a freshly-issued warning trips.
 *
 * <p>Matching is by exact count: {@link #stepFor(int)} returns the step whose {@link EscalationStep#atCount()}
 * equals the new total, so each rung fires once, on the warning that reaches it. (Exact match is the simplest
 * rule and the one operators expect from "3:tempmute"; a "highest rung at-or-below the count" rule would
 * re-fire the top rung on every subsequent warning, which is rarely wanted.) A count no rung names returns
 * empty and nothing escalates. Pure and self-contained: the ladder lives in {@code :core} and is tested
 * without a server.
 */
public final class WarnEscalation {

    /** An escalation ladder with no rungs: every count returns empty. The default when none is configured. */
    public static final WarnEscalation NONE = new WarnEscalation(List.of());

    private final List<EscalationStep> steps;

    public WarnEscalation(List<EscalationStep> steps) {
        Objects.requireNonNull(steps, "steps");
        this.steps = List.copyOf(steps);
    }

    /** The step that {@code newWarnCount} trips exactly, or empty when no rung names that count. */
    public Optional<EscalationStep> stepFor(int newWarnCount) {
        return steps.stream().filter(step -> step.atCount() == newWarnCount).findFirst();
    }

    /** The configured rungs, lowest-to-highest as supplied. */
    public List<EscalationStep> steps() {
        return steps;
    }

    /** The kind of automatic sanction a rung applies. A timed action carries the {@code duration}; the rest ignore it. */
    public enum EscalationAction {
        MUTE,
        TEMPMUTE,
        KICK,
        TEMPBAN,
        BAN
    }

    /**
     * One rung of the ladder: at {@code atCount} warnings, apply {@code action} for {@code duration}. The
     * {@code duration} is required for the timed actions ({@code TEMPMUTE}, {@code TEMPBAN}) and absent for the
     * rest ({@code MUTE} is permanent, {@code KICK}/{@code BAN} carry no span).
     *
     * @param atCount the warning total this rung fires on (1 or more)
     * @param action the sanction to apply
     * @param duration the span for a timed action, else empty
     */
    public record EscalationStep(int atCount, EscalationAction action, Optional<Duration> duration) {

        public EscalationStep {
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(duration, "duration");
            if (atCount < 1) {
                throw new IllegalArgumentException("atCount must be at least 1: " + atCount);
            }
            boolean timed = action == EscalationAction.TEMPMUTE || action == EscalationAction.TEMPBAN;
            if (timed && duration.isEmpty()) {
                throw new IllegalArgumentException(action + " escalation requires a duration");
            }
        }
    }
}

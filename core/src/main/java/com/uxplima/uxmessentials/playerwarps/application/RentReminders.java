package com.uxplima.uxmessentials.playerwarps.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.RentMailer;
import com.uxplima.uxmessentials.playerwarps.application.port.RentReminderCandidate;
import org.jspecify.annotations.NullMarked;

/**
 * Sends a warp owner at most one "rent due soon" mail per reminder window. The window a candidate has reached maps
 * to a monotonic stage (widest window = stage 1, tightest = the last stage): as the paid term approaches, the stage
 * climbs. A mail is left only when the reached stage exceeds the {@code rent_reminded_stage} already recorded, and
 * the counter is then bumped, so the same window never mails twice, and {@code SettleRent} resets it to 0 on
 * payment so the next term's reminders start over.
 *
 * <p>The mail itself goes through the narrow {@link RentMailer} seam (the messaging store, resolved in the owner's
 * locale), so this use case imports no messaging type and never touches the Bukkit API.
 */
@NullMarked
public final class RentReminders {

    private final PlayerWarpRepository repository;
    private final RentMailer mailer;
    private final RentConfig config;
    private final Clock clock;

    public RentReminders(PlayerWarpRepository repository, RentMailer mailer, RentConfig config, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.mailer = Objects.requireNonNull(mailer, "mailer");
        this.config = Objects.requireNonNull(config, "config");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Mail {@code candidate}'s owner if they have crossed a new reminder window since they were last reminded, and
     * return whether a mail was sent. A disabled sub-group and a warp with no configured windows never mail.
     */
    public boolean remind(RentReminderCandidate candidate) {
        Objects.requireNonNull(candidate, "candidate");
        if (!config.enabled() || config.reminderWindows().isEmpty()) {
            return false;
        }
        Instant now = clock.instant();
        int stage = stageFor(candidate.paidUntil(), now);
        if (stage <= candidate.remindedStage()) {
            return false;
        }
        mailer.mail(candidate.owner(), PlayerwarpsMessageKey.PWARP_RENT_REMINDER, placeholders(candidate, stage));
        repository.markRentReminded(candidate.id(), stage);
        return true;
    }

    /**
     * The reminder stage a term with {@code paidUntil} has reached at {@code now}: the number of configured windows
     * whose lead time the remaining term now fits inside. Zero while still further out than the widest window; the
     * full window count once the term has lapsed.
     */
    int stageFor(Instant paidUntil, Instant now) {
        Duration remaining = Duration.between(now, paidUntil);
        if (remaining.isZero() || remaining.isNegative()) {
            return config.maxReminderStage();
        }
        int stage = 0;
        for (Duration window : config.reminderWindows()) {
            if (remaining.compareTo(window) <= 0) {
                stage++;
            }
        }
        return stage;
    }

    private Map<String, String> placeholders(RentReminderCandidate candidate, int stage) {
        long windowHours = config.reminderWindows().get(stage - 1).toHours();
        return Map.of(
                "warp", candidate.warp().value(),
                "hours", Long.toString(windowHours),
                "amount", config.amount().toPlainString(),
                "currency", config.currencyId());
    }
}

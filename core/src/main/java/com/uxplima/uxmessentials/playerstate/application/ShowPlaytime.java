package com.uxplima.uxmessentials.playerstate.application;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.playerstate.application.port.PlayerInfo;
import com.uxplima.uxmessentials.playerstate.application.port.PlaytimeRepository;
import com.uxplima.uxmessentials.playerstate.domain.PlaytimeSummary;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /playtime [player]}: show a player's playtime breakdown. Active (non-AFK) and AFK time across today, the
 * last seven days, the last thirty days, and all time, read from the DB-backed {@link PlaytimeRepository} the
 * periodic sampler feeds. The viewer sees their own breakdown, or another player's with the {@code .others} node;
 * the adapter has already resolved the target before this runs. The DB summary survives a world rollback (it is
 * never PDC), unlike the Bukkit lifetime statistic, which is still surfaced as one continuity line through the
 * {@link PlayerInfo} port when the target is online, so an operator who watched the old number recognises it.
 *
 * <p>Nothing is mutated. A target with no tracked rows renders a clean all-zero breakdown rather than no answer.
 */
public final class ShowPlaytime {

    private final PlaytimeRepository repository;
    private final PlayerInfo info;
    private final Notifier notifier;
    private final Clock clock;

    public ShowPlaytime(PlaytimeRepository repository, PlayerInfo info, Notifier notifier, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.info = Objects.requireNonNull(info, "info");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Show {@code who} their own breakdown. */
    public void show(PlayerRef who) {
        showFor(who, who);
    }

    /** Show {@code actor} the breakdown of {@code subject}. */
    public void showFor(PlayerRef actor, PlayerRef subject) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(subject, "subject");
        notifier.send(actor, headerKey(actor, subject), breakdown(subject));
    }

    /**
     * The rendered placeholder map for {@code subject}'s breakdown. The same compact-formatted today / week /
     * month / all-time / lifetime values the chat lines interpolate. Exposed so the {@code /playtime} GUI renders
     * the identical figures without duplicating the formatting; reads the repository, so callers run it off-tick.
     */
    public Map<String, String> breakdown(PlayerRef subject) {
        Objects.requireNonNull(subject, "subject");
        PlaytimeSummary summary = repository.summaryOf(subject.uuid(), LocalDate.now(clock));
        return placeholders(subject, summary);
    }

    private static PlayerstateMessageKey headerKey(PlayerRef actor, PlayerRef subject) {
        return actor.equals(subject) ? PlayerstateMessageKey.PLAYTIME_SHOW : PlayerstateMessageKey.PLAYTIME_SHOW_OTHER;
    }

    private Map<String, String> placeholders(PlayerRef subject, PlaytimeSummary summary) {
        Map<String, String> data = new HashMap<>();
        data.put("player", subject.name());
        data.put("today_active", PlaytimeFormat.compact(summary.todayActive()));
        data.put("today_afk", PlaytimeFormat.compact(summary.todayAfk()));
        data.put("week_active", PlaytimeFormat.compact(summary.weekActive()));
        data.put("week_afk", PlaytimeFormat.compact(summary.weekAfk()));
        data.put("month_active", PlaytimeFormat.compact(summary.monthActive()));
        data.put("month_afk", PlaytimeFormat.compact(summary.monthAfk()));
        data.put("total_active", PlaytimeFormat.compact(summary.totalActive()));
        data.put("total_afk", PlaytimeFormat.compact(summary.totalAfk()));
        data.put("total", PlaytimeFormat.compact(summary.totalCombined()));
        data.put("lifetime", PlaytimeFormat.compact(lifetime(subject)));
        return data;
    }

    /** The vanilla play-one-minute statistic for continuity, or the tracked all-time total when offline. */
    private Duration lifetime(PlayerRef subject) {
        Optional<Duration> bukkit = info.playtimeOf(subject);
        return bukkit.orElseGet(
                () -> repository.summaryOf(subject.uuid(), LocalDate.now(clock)).totalCombined());
    }
}

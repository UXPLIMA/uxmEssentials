package com.uxplima.uxmessentials.moderation.application;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.domain.JailEntry;
import com.uxplima.uxmessentials.moderation.domain.SanctionDuration;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /jailedplayers}: review the jails still in effect, the companion of {@code /banlist} and
 * {@code /mutelist}. A read-only, bounded query against the DB-backed sanction store rendering a header with
 * the count, one entry per jailed target (player, jail, issuer, remaining time), or an empty notice when no
 * one is jailed. The query is capped at {@link #PAGE_LIMIT} rows so the read stays within the main-thread
 * budget; the command runs it off the tick thread.
 *
 * <p>The remaining-time column is rendered uniformly for all three sentence shapes: {@code permanent} when the
 * jail never expires, the compact {@link SanctionDuration} label of the online time still to serve for an
 * online-only sentence, and the wall-clock expiry instant for a wall-clock sentence.
 */
public final class ListJailed {

    private static final int PAGE_LIMIT = 50;

    private final ModerationRepository repository;
    private final PlayerLookup players;
    private final Notifier notifier;
    private final Clock clock;

    public ListJailed(ModerationRepository repository, PlayerLookup players, Notifier notifier, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.players = Objects.requireNonNull(players, "players");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Render the currently jailed players to {@code actor}. */
    public void list(PlayerRef actor) {
        Objects.requireNonNull(actor, "actor");
        List<JailEntry> jails = repository.activeJails(clock.instant(), PAGE_LIMIT);
        if (jails.isEmpty()) {
            notifier.send(actor, ModerationMessageKey.JAILEDLIST_EMPTY);
            return;
        }
        notifier.send(actor, ModerationMessageKey.JAILEDLIST_HEADER, Map.of("count", Integer.toString(jails.size())));
        jails.forEach(jail -> notifier.send(actor, ModerationMessageKey.JAILEDLIST_ENTRY, entry(jail)));
    }

    private Map<String, String> entry(JailEntry jail) {
        String name = players.findByUuid(jail.target())
                .map(PlayerRef::name)
                .orElseGet(() -> jail.target().toString());
        return Map.of(
                "player", name,
                "jail", jail.jail(),
                "issuer", jail.issuer().name(),
                "reason", jail.reason().orElse(""),
                "remaining", remaining(jail));
    }

    private static String remaining(JailEntry jail) {
        if (jail.remaining().isPresent()) {
            return SanctionDuration.format(jail.remaining().orElseThrow());
        }
        return jail.until().map(java.time.Instant::toString).orElse("permanent");
    }
}

package com.uxplima.uxmessentials.moderation.application;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.domain.Warn;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /warns <player>}: review a player's warning history newest-first. A read-only query against the
 * append-only history. It renders a header with the count, one entry per warning (issuer, reason, when), or
 * an empty notice when the target has none. A {@code /tempwarn} that has lapsed by the read instant is dropped
 * from both the count and the listing, so a review reflects only the warnings still in effect.
 */
public final class ReviewWarns {

    private final ModerationRepository repository;
    private final Notifier notifier;
    private final Clock clock;

    public ReviewWarns(ModerationRepository repository, Notifier notifier, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Render {@code target}'s warnings still in effect to {@code actor}, newest-first. */
    public void review(PlayerRef actor, PlayerRef target) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        List<Warn> warns = repository.warns(target, clock.instant());
        if (warns.isEmpty()) {
            notifier.send(actor, ModerationMessageKey.WARNS_EMPTY, Map.of("player", target.name()));
            return;
        }
        notifier.send(
                actor,
                ModerationMessageKey.WARNS_HEADER,
                Map.of("player", target.name(), "count", Integer.toString(warns.size())));
        warns.forEach(warn -> notifier.send(actor, ModerationMessageKey.WARNS_ENTRY, entry(warn)));
    }

    private static Map<String, String> entry(Warn warn) {
        return Map.of(
                "issuer", warn.issuer().name(),
                "reason", warn.reason().orElse(""),
                "when", warn.issuedAt().toString());
    }
}

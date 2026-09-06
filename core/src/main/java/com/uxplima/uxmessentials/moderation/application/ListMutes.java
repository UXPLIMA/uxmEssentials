package com.uxplima.uxmessentials.moderation.application;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.domain.MuteEntry;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /mutelist}: review the mutes still in effect, the companion of {@code /banlist}. A read-only, bounded
 * query against the DB-backed sanction store rendering a header with the count, one entry per mute (target,
 * issuer, reason, expiry. {@code permanent} when the mute never expires), or an empty notice when nobody is
 * muted. The query is capped at {@link #PAGE_LIMIT} rows so the read stays within the main-thread budget; the
 * command runs it off the tick thread.
 */
public final class ListMutes {

    private static final int PAGE_LIMIT = 50;

    private final ModerationRepository repository;
    private final PlayerLookup players;
    private final Notifier notifier;
    private final Clock clock;

    public ListMutes(ModerationRepository repository, PlayerLookup players, Notifier notifier, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.players = Objects.requireNonNull(players, "players");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Render the currently muted players to {@code actor}. */
    public void list(PlayerRef actor) {
        Objects.requireNonNull(actor, "actor");
        List<MuteEntry> mutes = repository.activeMutes(clock.instant(), PAGE_LIMIT);
        if (mutes.isEmpty()) {
            notifier.send(actor, ModerationMessageKey.MUTELIST_EMPTY);
            return;
        }
        notifier.send(actor, ModerationMessageKey.MUTELIST_HEADER, Map.of("count", Integer.toString(mutes.size())));
        mutes.forEach(mute -> notifier.send(actor, ModerationMessageKey.MUTELIST_ENTRY, entry(mute)));
    }

    private Map<String, String> entry(MuteEntry mute) {
        String name = players.findByUuid(mute.target())
                .map(PlayerRef::name)
                .orElseGet(() -> mute.target().toString());
        String expires = mute.until().map(java.time.Instant::toString).orElse("permanent");
        return Map.of(
                "player",
                name,
                "issuer",
                mute.issuer().name(),
                "reason",
                mute.reason().orElse(""),
                "expires",
                expires);
    }
}

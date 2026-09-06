package com.uxplima.uxmessentials.moderation.application;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.domain.BanEntry;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /banlist}: review the bans still in effect. A read-only, bounded query against the DB-backed sanction
 * store. It renders a header with the count, one entry per ban (target, issuer, reason, expiry), or an empty
 * notice when nobody is banned. The query is capped at {@link #PAGE_LIMIT} rows so the read stays within the
 * main-thread budget; the command runs it off the tick thread. Target names are resolved from each entry's
 * stored UUID through the {@link PlayerLookup}, falling back to the raw UUID when the account is unknown.
 */
public final class ListBans {

    private static final int PAGE_LIMIT = 50;

    private final ModerationRepository repository;
    private final PlayerLookup players;
    private final Notifier notifier;
    private final Clock clock;

    public ListBans(ModerationRepository repository, PlayerLookup players, Notifier notifier, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.players = Objects.requireNonNull(players, "players");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Render the currently banned players to {@code actor}. */
    public void list(PlayerRef actor) {
        Objects.requireNonNull(actor, "actor");
        List<BanEntry> bans = repository.activeBans(clock.instant(), PAGE_LIMIT);
        if (bans.isEmpty()) {
            notifier.send(actor, ModerationMessageKey.BANLIST_EMPTY);
            return;
        }
        notifier.send(actor, ModerationMessageKey.BANLIST_HEADER, Map.of("count", Integer.toString(bans.size())));
        bans.forEach(ban -> notifier.send(actor, ModerationMessageKey.BANLIST_ENTRY, entry(ban)));
    }

    private Map<String, String> entry(BanEntry ban) {
        String name = players.findByUuid(ban.target())
                .map(PlayerRef::name)
                .orElseGet(() -> ban.target().toString());
        return Map.of(
                "player", name,
                "issuer", ban.issuer().name(),
                "reason", ban.reason().orElse(""),
                "expires", ban.until().toString());
    }
}

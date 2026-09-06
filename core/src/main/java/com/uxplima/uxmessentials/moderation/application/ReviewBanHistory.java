package com.uxplima.uxmessentials.moderation.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.moderation.application.port.SanctionHistory;
import com.uxplima.uxmessentials.moderation.domain.SanctionHistoryEntry;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /banhistory <player>}: review a player's ban-family history (every {@code /ban}, {@code /tempban},
 * {@code /banip}, {@code /unban}, {@code /unbanip} applied to them) newest-first. A read-only, bounded query
 * against the append-only history. It renders a header with the count, one entry per row (action, issuer,
 * reason, expiry, ip, when), or an empty notice when the target has none. The query is capped at
 * {@link #PAGE_LIMIT} rows so the read stays within budget; the command runs it off the tick thread.
 */
public final class ReviewBanHistory {

    static final int PAGE_LIMIT = 20;

    private final SanctionHistory history;
    private final Notifier notifier;

    public ReviewBanHistory(SanctionHistory history, Notifier notifier) {
        this.history = Objects.requireNonNull(history, "history");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Render {@code target}'s ban history to {@code actor}, newest-first. */
    public void review(PlayerRef actor, PlayerRef target) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        List<SanctionHistoryEntry> rows = history.banHistory(target.uuid(), PAGE_LIMIT);
        if (rows.isEmpty()) {
            notifier.send(actor, ModerationMessageKey.BANHISTORY_EMPTY, Map.of("player", target.name()));
            return;
        }
        notifier.send(
                actor,
                ModerationMessageKey.BANHISTORY_HEADER,
                Map.of("player", target.name(), "count", Integer.toString(rows.size())));
        rows.forEach(row -> notifier.send(actor, ModerationMessageKey.BANHISTORY_ENTRY, SanctionHistoryLine.of(row)));
    }
}

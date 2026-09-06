package com.uxplima.uxmessentials.moderation.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.moderation.application.port.SanctionHistory;
import com.uxplima.uxmessentials.moderation.domain.SanctionHistoryEntry;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /mutehistory <player>}: review a player's mute-family history (every {@code /mute}, {@code /tempmute}
 * and {@code /unmute} applied to them) newest-first. A read-only, bounded query against the append-only
 * history. It renders a header with the count, one entry per row (action, issuer, reason, expiry, when), or
 * an empty notice when the target has none. The query is capped at {@link ReviewBanHistory#PAGE_LIMIT} rows
 * so the read stays within budget; the command runs it off the tick thread.
 */
public final class ReviewMuteHistory {

    private final SanctionHistory history;
    private final Notifier notifier;

    public ReviewMuteHistory(SanctionHistory history, Notifier notifier) {
        this.history = Objects.requireNonNull(history, "history");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Render {@code target}'s mute history to {@code actor}, newest-first. */
    public void review(PlayerRef actor, PlayerRef target) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        List<SanctionHistoryEntry> rows = history.muteHistory(target.uuid(), ReviewBanHistory.PAGE_LIMIT);
        if (rows.isEmpty()) {
            notifier.send(actor, ModerationMessageKey.MUTEHISTORY_EMPTY, Map.of("player", target.name()));
            return;
        }
        notifier.send(
                actor,
                ModerationMessageKey.MUTEHISTORY_HEADER,
                Map.of("player", target.name(), "count", Integer.toString(rows.size())));
        rows.forEach(row -> notifier.send(actor, ModerationMessageKey.MUTEHISTORY_ENTRY, SanctionHistoryLine.of(row)));
    }
}

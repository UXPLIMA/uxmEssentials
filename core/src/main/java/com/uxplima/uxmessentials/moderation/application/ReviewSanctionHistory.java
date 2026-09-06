package com.uxplima.uxmessentials.moderation.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.moderation.application.port.SanctionHistory;
import com.uxplima.uxmessentials.moderation.domain.SanctionHistoryEntry;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /history <player>}: the unified review of a player's full disciplinary record, every {@code /ban},
 * {@code /tempban}, {@code /banip}, {@code /unban}, {@code /mute}, {@code /tempmute}, {@code /unmute},
 * {@code /warn}, {@code /tempwarn} and {@code /kick} applied to them, folded into one newest-first timeline.
 * Where {@code /banhistory} and {@code /mutehistory} stay scoped to a single family, this read folds every
 * {@link com.uxplima.uxmessentials.moderation.domain.SanctionAction} kind.
 *
 * <p>A read-only, bounded query against the append-only history: it renders a header with the count, one entry
 * per row (action, issuer, reason, expiry, ip, when), or an empty notice when the target has none. The query
 * is capped at {@link ReviewBanHistory#PAGE_LIMIT} rows so the read stays within budget; the command runs it
 * off the tick thread.
 */
public final class ReviewSanctionHistory {

    private final SanctionHistory history;
    private final Notifier notifier;

    public ReviewSanctionHistory(SanctionHistory history, Notifier notifier) {
        this.history = Objects.requireNonNull(history, "history");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Render {@code target}'s full sanction history to {@code viewer}, every kind, newest-first. */
    public void show(PlayerRef viewer, PlayerRef target) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(target, "target");
        List<SanctionHistoryEntry> rows = history.recentForTarget(target.uuid(), ReviewBanHistory.PAGE_LIMIT);
        if (rows.isEmpty()) {
            notifier.send(viewer, ModerationMessageKey.MOD_HISTORY_EMPTY, Map.of("player", target.name()));
            return;
        }
        notifier.send(
                viewer,
                ModerationMessageKey.MOD_HISTORY_HEADER,
                Map.of("player", target.name(), "count", Integer.toString(rows.size())));
        rows.forEach(row -> notifier.send(viewer, ModerationMessageKey.MOD_HISTORY_ENTRY, SanctionHistoryLine.of(row)));
    }
}

package com.uxplima.uxmessentials.moderation.application;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.moderation.application.port.SanctionHistory;
import com.uxplima.uxmessentials.moderation.domain.SanctionHistoryEntry;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /staffhistory <staff>}: review the sanctions a staff member has issued, every ban/mute/warn/kick they
 * applied, of any kind, folded into one newest-first timeline. The mirror image of {@link ReviewSanctionHistory}:
 * where that one scopes by who was punished, this scopes by who did the punishing, so an operator can audit a
 * staff member's disciplinary activity.
 *
 * <p>A read-only, bounded query against the append-only history: it renders a header with the count, one entry
 * per row (the punished target, action, reason, when), or an empty notice when the staff member has issued
 * none. Each row's target name is resolved from its stored UUID through the {@link PlayerLookup}, falling back
 * to the raw UUID when the account is unknown. The query is capped at {@link ReviewBanHistory#PAGE_LIMIT} rows
 * so the read stays within budget; the command runs it off the tick thread.
 */
public final class ReviewStaffHistory {

    private final SanctionHistory history;
    private final PlayerLookup players;
    private final Notifier notifier;

    public ReviewStaffHistory(SanctionHistory history, PlayerLookup players, Notifier notifier) {
        this.history = Objects.requireNonNull(history, "history");
        this.players = Objects.requireNonNull(players, "players");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Render the sanctions {@code staff} has issued to {@code viewer}, of any kind, newest-first. */
    public void show(PlayerRef viewer, PlayerRef staff) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(staff, "staff");
        List<SanctionHistoryEntry> rows = history.recentByActor(staff.uuid(), ReviewBanHistory.PAGE_LIMIT);
        if (rows.isEmpty()) {
            notifier.send(viewer, ModerationMessageKey.MOD_STAFFHISTORY_EMPTY, Map.of("staff", staff.name()));
            return;
        }
        notifier.send(
                viewer,
                ModerationMessageKey.MOD_STAFFHISTORY_HEADER,
                Map.of("staff", staff.name(), "count", Integer.toString(rows.size())));
        rows.forEach(row -> notifier.send(viewer, ModerationMessageKey.MOD_STAFFHISTORY_ENTRY, entry(staff, row)));
    }

    private Map<String, String> entry(PlayerRef staff, SanctionHistoryEntry row) {
        String target = players.findByUuid(row.target())
                .map(PlayerRef::name)
                .orElseGet(() -> row.target().toString());
        return Map.of(
                "staff", staff.name(),
                "action", SanctionHistoryLine.label(row.action()),
                "target", target,
                "reason", row.reason().orElse(""),
                "when", row.at().toString());
    }
}

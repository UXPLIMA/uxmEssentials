package com.uxplima.uxmessentials.moderation.application;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.application.port.ModerationAudit;
import com.uxplima.uxmessentials.moderation.application.port.ModerationRepository;
import com.uxplima.uxmessentials.moderation.application.port.SanctionHistory;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.moderation.domain.SanctionAction;
import com.uxplima.uxmessentials.moderation.domain.SanctionHistoryEntry;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * {@code /staffrollback <staff> [limit]}: revoke a (rogue or mistaken) staff member's still-active sanctions
 * un-ban, un-mute and clear-warns every target they sanctioned that is currently still under <em>that staff
 * member's</em> sanction. The append-only history records every sanction a staff member ever applied but carries
 * <em>no</em> active flag, so each revoke is gated by a live-state read against the DB-backed sanction store at
 * the injected {@link Clock}: a ban already lifted (by an {@code /unban}, an {@code /unbanip}, or a since-lapsed
 * timed sentence) reads as not-active and is left alone. Only what is in effect <em>now</em> is undone, so the
 * rollback never resurrects a sanction or emits a spurious "unbanned X" for a player who was not banned.
 *
 * <p><b>Issuer-scoped.</b> The revoke is gated not only on "is the target currently sanctioned" but on "was the
 * <em>currently-active</em> sanction issued by the staff member being rolled back". If staffA banned X and then
 * staffB re-banned X, the live ban's issuer is staffB, so {@code /staffrollback staffA} leaves it untouched, a
 * rollback never lifts another staff member's still-active sanction. The match is on the issuer UUID: a
 * console/system issuer (no UUID) never matches a real staff UUID, so a sanction now standing under a
 * console-issued ban/mute is also left alone. Warnings are removed per-issuer through
 * {@link ModerationRepository#clearWarnsByActor} so only the rolled-back staff member's warnings on a target are
 * cleared, never another staff member's.
 *
 * <p>Targets are deduped per action through a {@link Set} of UUIDs: a target the staff member banned twice (or
 * banned then re-banned) is un-banned once. {@link SanctionAction#UNBAN}, {@link SanctionAction#UNMUTE} and
 * {@link SanctionAction#KICK} rows are skipped. A kick is a live disconnect with nothing to undo, and a lift is
 * itself not a sanction. The {@code limit} caps how far back the history read reaches (newest-first), so an
 * operator can scope a rollback to a staff member's recent activity. The result is a {@link RollbackSummary} of
 * the counts undone, reported to the actor; when nothing was in effect the actor is told so instead.
 */
public final class StaffRollback {

    private final SanctionHistory history;
    private final ModerationRepository repository;
    private final Unban unban;
    private final Unmute unmute;
    private final Notifier notifier;
    private final ModerationAudit audit;
    private final Clock clock;

    public StaffRollback(
            SanctionHistory history,
            ModerationRepository repository,
            Unban unban,
            Unmute unmute,
            Notifier notifier,
            ModerationAudit audit,
            Clock clock) {
        this.history = Objects.requireNonNull(history, "history");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.unban = Objects.requireNonNull(unban, "unban");
        this.unmute = Objects.requireNonNull(unmute, "unmute");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Revoke {@code staff}'s still-active sanctions over their most recent {@code limit} history rows. */
    public RollbackSummary rollback(PlayerRef actor, PlayerRef staff, int limit) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(staff, "staff");
        Instant now = clock.instant();
        List<SanctionHistoryEntry> rows = history.recentByActor(staff.uuid(), limit);
        Set<UUID> unbanned = new HashSet<>();
        Set<UUID> unmuted = new HashSet<>();
        Set<UUID> cleared = new HashSet<>();
        int bans = 0;
        int mutes = 0;
        int warns = 0;
        for (SanctionHistoryEntry row : rows) {
            switch (row.action()) {
                case BAN -> bans += revokeBan(actor, staff, row.target(), now, unbanned);
                case MUTE -> mutes += revokeMute(actor, staff, row.target(), now, unmuted);
                case WARN -> warns += revokeWarns(actor, staff, row.target(), now, cleared);
                case UNBAN, UNMUTE, KICK -> {
                    // A lift is not a sanction and a kick is a live disconnect: nothing to undo.
                }
            }
        }
        RollbackSummary summary = new RollbackSummary(bans, mutes, warns);
        notify(actor, staff, summary);
        return summary;
    }

    private int revokeBan(PlayerRef actor, PlayerRef staff, UUID target, Instant now, Set<UUID> seen) {
        if (!seen.add(target)) {
            return 0;
        }
        PlayerRef ref = ref(target);
        if (repository.loadTempban(ref) instanceof TempbanState.Active active
                && active.isActiveAt(now)
                && issuedBy(active.issuer(), staff)) {
            unban.unban(actor, ref);
            return 1;
        }
        return 0;
    }

    private int revokeMute(PlayerRef actor, PlayerRef staff, UUID target, Instant now, Set<UUID> seen) {
        if (!seen.add(target)) {
            return 0;
        }
        PlayerRef ref = ref(target);
        MuteState mute = repository.loadMute(ref);
        if (mute.isActiveAt(now) && issuedBy(muteIssuer(mute), staff)) {
            unmute.unmute(actor, ref);
            return 1;
        }
        return 0;
    }

    private int revokeWarns(PlayerRef actor, PlayerRef staff, UUID target, Instant now, Set<UUID> seen) {
        if (!seen.add(target)) {
            return 0;
        }
        PlayerRef ref = ref(target);
        boolean staffWarnActive = repository.warns(ref, now).stream().anyMatch(warn -> issuedBy(warn.issuer(), staff));
        if (!staffWarnActive) {
            return 0;
        }
        int removed = repository.clearWarnsByActor(ref, staff);
        audit.clearedWarns(actor, ref, true, removed);
        return 1;
    }

    /** True only when {@code issuer} carries the rolled-back {@code staff} member's UUID (console never matches). */
    private static boolean issuedBy(Issuer issuer, PlayerRef staff) {
        return issuer.uuid().filter(staff.uuid()::equals).isPresent();
    }

    private static Issuer muteIssuer(MuteState mute) {
        return switch (mute) {
            case MuteState.Permanent permanent -> permanent.issuer();
            case MuteState.Timed timed -> timed.issuer();
            case MuteState.None none -> Issuer.console("");
        };
    }

    private void notify(PlayerRef actor, PlayerRef staff, RollbackSummary summary) {
        if (summary.total() == 0) {
            notifier.send(actor, ModerationMessageKey.MOD_STAFFROLLBACK_NONE, Map.of("staff", staff.name()));
            return;
        }
        notifier.send(
                actor,
                ModerationMessageKey.MOD_STAFFROLLBACK_SUMMARY,
                Map.of(
                        "staff", staff.name(),
                        "bans", Integer.toString(summary.bans()),
                        "mutes", Integer.toString(summary.mutes()),
                        "warns", Integer.toString(summary.warns()),
                        "total", Integer.toString(summary.total())));
    }

    /**
     * The target as a {@link PlayerRef}, name-resolved through the DB-backed seen record, never the Bukkit
     * player lookup, since the whole rollback runs off the tick thread and a Bukkit call there would violate
     * the threading invariant. An unseen target (no row) falls back to a UUID-only ref; the revoke use cases
     * and {@link ModerationRepository#clearWarnsByActor} all key on the UUID, so a name-less ref still works.
     */
    private PlayerRef ref(UUID target) {
        PlayerRef key = new PlayerRef(target, target.toString());
        return repository.seen(key).map(record -> record.player()).orElse(key);
    }

    /**
     * The tally a {@code /staffrollback} undid: the count of bans lifted, mutes lifted and warn-sets cleared,
     * each deduped per target. {@link #total()} folds the three for the summary line.
     *
     * @param bans the number of distinct targets un-banned
     * @param mutes the number of distinct targets un-muted
     * @param warns the number of distinct targets whose warnings were cleared
     */
    public record RollbackSummary(int bans, int mutes, int warns) {

        /** The combined count of sanctions revoked across all three kinds. */
        public int total() {
            return bans + mutes + warns;
        }
    }
}

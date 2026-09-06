package com.uxplima.uxmessentials.vote.adapter.inbound.listener;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.ApplyQueuedRewards;
import com.uxplima.uxmessentials.vote.application.VoteMessageKey;
import com.uxplima.uxmessentials.vote.application.VoteReminderEligibility;
import com.uxplima.uxmessentials.vote.application.port.ReminderPreferences;
import com.uxplima.uxmessentials.vote.application.port.VoteRepository;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * On join:
 * <ol>
 *   <li>Pays out the rewards a player accrued while offline (drains the queue via {@link ApplyQueuedRewards})
 *: only when auto-claim is enabled. With auto-claim off the queue is left for the player to drain
 *       with {@code /vote claim}.
 *   <li>Warms the repository cache with the player's vote totals so the first PAPI placeholder call is
 *       cheap (no cold DB hit).
 *   <li>Optionally sends a one-shot vote reminder after a short delay when {@code remindersEnabled} is
 *       {@code true}, the player has opted in, and they have at least one available site.
 * </ol>
 *
 * <p>All tasks run off-tick via the {@link Scheduler} port. The reminder delay uses
 * {@link Scheduler#asyncAfter(Duration, Runnable)} so the Scheduler port is the only scheduling
 * mechanism in use: no {@code BukkitScheduler}, no raw threads.
 */
@NullMarked
public final class VoteJoinListener implements Listener {

    private final ApplyQueuedRewards applyQueuedRewards;
    private final VoteRepository repository;
    private final Scheduler scheduler;
    private final boolean autoClaim;
    private final boolean remindersEnabled;
    private final Duration loginDelay;
    /** Null when {@code remindersEnabled} is false: avoids a useless object allocation. */
    private final @Nullable VoteReminderEligibility eligibility;

    private final @Nullable ReminderPreferences reminderPreferences;
    private final @Nullable Notifier notifier;

    /**
     * Legacy constructor. Queue drain only, no reminders (equivalent to
     * {@code remindersEnabled = false}). Auto-claim is on, so the queue still drains on join. The
     * repository cache-warm still fires.
     */
    public VoteJoinListener(ApplyQueuedRewards applyQueuedRewards, Scheduler scheduler) {
        this(applyQueuedRewards, null, scheduler, true, false, Duration.ZERO, null, null, null);
    }

    /**
     * Full constructor used when reminders are enabled.
     *
     * @param repository may be {@code null} when provided via the legacy single-arg ctor; the
     *                   cache-warm is then skipped
     * @param autoClaim  when {@code true} the offline reward queue is drained on join; when {@code false}
     *                   the join leaves the queue for the player to pay out with {@code /vote claim}
     */
    public VoteJoinListener(
            ApplyQueuedRewards applyQueuedRewards,
            @Nullable VoteRepository repository,
            Scheduler scheduler,
            boolean autoClaim,
            boolean remindersEnabled,
            Duration loginDelay,
            @Nullable VoteReminderEligibility eligibility,
            @Nullable ReminderPreferences reminderPreferences,
            @Nullable Notifier notifier) {
        this.applyQueuedRewards = Objects.requireNonNull(applyQueuedRewards, "applyQueuedRewards");
        this.repository = repository != null ? repository : NoopRepository.INSTANCE;
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.autoClaim = autoClaim;
        this.remindersEnabled = remindersEnabled;
        this.loginDelay = Objects.requireNonNull(loginDelay, "loginDelay");
        this.eligibility = eligibility;
        this.reminderPreferences = reminderPreferences;
        this.notifier = notifier;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerRef who = BukkitRefs.toRef(player);

        // Warm the PAPI cache always; drain offline rewards only when auto-claim is on (otherwise the
        // player pays the queue out with /vote claim). Both run async, never on the join tick.
        scheduler.async(() -> {
            if (autoClaim) {
                applyQueuedRewards.applyFor(who);
            }
            repository.totalsOf(who); // warms the Caffeine cache, no user-visible side effect
        });

        if (!remindersEnabled || eligibility == null || reminderPreferences == null || notifier == null) {
            return;
        }
        VoteReminderEligibility elig = eligibility;
        ReminderPreferences prefs = reminderPreferences;
        Notifier note = notifier;

        // One-shot reminder after loginDelay. The eligibility check hits the DB off-tick; the PDC opt-in
        // read and the send then hop to the player's entity thread, guarded against a logout in between.
        scheduler.asyncAfter(loginDelay, () -> {
            if (!elig.canVoteSomewhere(who, Instant.now())) {
                return;
            }
            scheduler.onEntity(who, () -> {
                if (Bukkit.getPlayer(who.uuid()) == null) {
                    return; // logged off between the eligibility check and the entity hop
                }
                if (prefs.wantsReminders(who)) {
                    note.send(who, VoteMessageKey.VOTE_REMINDER);
                }
            });
        });
    }

    /**
     * A minimal no-op repository used when the cache-warm is not needed (legacy ctor path). Keeps
     * the main logic simple: no branches on null in {@link #onJoin}.
     */
    private static final class NoopRepository implements VoteRepository {
        static final NoopRepository INSTANCE = new NoopRepository();

        @Override
        public int partyCount() {
            return 0;
        }

        @Override
        public void setPartyCount(int count) {}

        @Override
        public int incrementAndGetPartyCount() {
            return 0;
        }

        @Override
        public void enqueue(com.uxplima.uxmessentials.vote.domain.QueuedReward reward) {}

        @Override
        public java.util.List<com.uxplima.uxmessentials.vote.domain.QueuedReward> drainFor(PlayerRef player) {
            return java.util.List.of();
        }

        @Override
        public boolean hasPending(PlayerRef player) {
            return false;
        }

        @Override
        public int queuedCount(PlayerRef player) {
            return 0;
        }

        @Override
        public com.uxplima.uxmessentials.vote.domain.VoteTally totalsOf(PlayerRef player) {
            return com.uxplima.uxmessentials.vote.domain.VoteTally.empty();
        }

        @Override
        public void saveTotals(PlayerRef player, com.uxplima.uxmessentials.vote.domain.VoteTally tally) {}

        @Override
        public java.util.List<com.uxplima.uxmessentials.vote.application.port.VoteRanking> topVoters(
                com.uxplima.uxmessentials.vote.domain.VotePeriod period, int limit) {
            return java.util.List.of();
        }

        @Override
        public void markPartyParticipant(PlayerRef player) {}

        @Override
        public java.util.Set<java.util.UUID> partyParticipants() {
            return java.util.Set.of();
        }

        @Override
        public void clearPartyParticipants() {}

        @Override
        public long partyPeriodKey() {
            return 0L;
        }

        @Override
        public void setPartyPeriodKey(long key) {}

        @Override
        public int thresholdOverride() {
            return 0;
        }

        @Override
        public void setThresholdOverride(int override) {}

        @Override
        public boolean claimPartyFire(int threshold) {
            return false;
        }

        @Override
        public void resetTotals(PlayerRef player) {}

        @Override
        public java.util.Optional<Instant> lastVoteAtSite(PlayerRef player, String site) {
            return java.util.Optional.empty();
        }

        @Override
        public void recordLastVoteAtSite(PlayerRef player, String site, Instant at) {}
    }
}

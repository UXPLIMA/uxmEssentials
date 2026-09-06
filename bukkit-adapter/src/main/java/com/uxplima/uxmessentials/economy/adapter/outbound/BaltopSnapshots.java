package com.uxplima.uxmessentials.economy.adapter.outbound;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import com.uxplima.uxmessentials.economy.application.port.BaltopExemption;
import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.application.port.EconomyProvider;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;

/**
 * The provider-independent {@code /baltop} read-model machinery ({@code docs/11-economy-integration.md} §11):
 * a per-currency {@link AtomicReference} snapshot, refreshed on a bounded interval off-tick rather than
 * recomputed per command, with exempt owners ({@link BaltopExemption}) filtered out where the snapshot is
 * built so the hot path never re-checks the node. A {@code /baltop [currency]} invocation reads its currency's
 * snapshot lock-free. The provider's {@code top(currency, limit)} is the seam; the snapshot/cache/budget
 * lives here, above the port, so it serves the native ledger, a Treasury, or a Vault provider unchanged.
 *
 * <p>The refresh over-fetches by a margin so that filtering exempt owners still leaves a full page; the
 * snapshot is bounded by {@code capacity}. The loop is gated on {@link #running} so a disabled or reloaded
 * module stops refreshing.
 */
@NullMarked
public final class BaltopSnapshots {

    /** Whether an owner is banned, for the optional {@code baltop.exclude-banned} filter; the testable seam. */
    @FunctionalInterface
    public interface BannedLookup {
        boolean isBanned(com.uxplima.uxmessentials.shared.domain.PlayerRef owner);
    }

    private final Map<Currency, AtomicReference<List<BaltopRow>>> snapshots = new ConcurrentHashMap<>();
    private final EconomyProvider provider;
    private final BaltopExemption exemption;
    private final Scheduler scheduler;
    private final Duration ttl;
    private final int capacity;
    private final boolean excludeBanned;
    private final java.math.BigDecimal minBalance;
    private final BannedLookup banned;
    private volatile boolean running;

    public BaltopSnapshots(
            EconomyProvider provider,
            BaltopExemption exemption,
            Scheduler scheduler,
            Duration ttl,
            int capacity,
            boolean excludeBanned,
            java.math.BigDecimal minBalance,
            BannedLookup banned) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.exemption = Objects.requireNonNull(exemption, "exemption");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.ttl = Objects.requireNonNull(ttl, "ttl");
        if (capacity <= 0) {
            throw new IllegalArgumentException("baltop snapshot capacity must be positive: " + capacity);
        }
        this.capacity = capacity;
        this.excludeBanned = excludeBanned;
        this.minBalance = Objects.requireNonNull(minBalance, "minBalance");
        this.banned = Objects.requireNonNull(banned, "banned");
    }

    /** Prime a snapshot for every configured currency and arm the refresh loop. Called on module start. */
    public void start() {
        if (running) {
            return;
        }
        running = true;
        for (Currency currency : provider.currencies()) {
            if (currency.leaderboardEnabled()) {
                snapshots.computeIfAbsent(currency, c -> new AtomicReference<>(List.of()));
            }
        }
        refreshAll();
        scheduleNext();
    }

    /** Stop the refresh loop. Called on module stop and before a reload swap. */
    public void stop() {
        running = false;
    }

    /**
     * The cached top of {@code currency}, bounded by {@code limit}, served lock-free from the snapshot. Falls
     * back to a direct (already exempt-filtered) read when no snapshot is primed yet, so the very first
     * command after start is correct rather than empty.
     */
    public List<BaltopRow> top(Currency currency, int limit) {
        Objects.requireNonNull(currency, "currency");
        if (limit <= 0) {
            throw new IllegalArgumentException("baltop limit must be positive: " + limit);
        }
        if (!currency.leaderboardEnabled()) {
            return List.of(); // hidden from leaderboards by its capability flag. Never primed, never built
        }
        AtomicReference<List<BaltopRow>> slot = snapshots.get(currency);
        List<BaltopRow> snapshot = slot == null ? null : slot.get();
        List<BaltopRow> rows = snapshot == null ? buildSnapshot(currency) : snapshot;
        return rows.size() <= limit ? rows : List.copyOf(rows.subList(0, limit));
    }

    public void refreshAll() {
        for (Map.Entry<Currency, AtomicReference<List<BaltopRow>>> entry : snapshots.entrySet()) {
            entry.getValue().set(buildSnapshot(entry.getKey()));
        }
    }

    private List<BaltopRow> buildSnapshot(Currency currency) {
        // Over-fetch so filtering exempt owners still leaves a full snapshot; the descending order from the
        // provider is preserved by appending in iteration order.
        List<BaltopRow> ranked = provider.top(currency, capacity * 2);
        List<BaltopRow> filtered = new ArrayList<>(Math.min(ranked.size(), capacity));
        for (BaltopRow row : ranked) {
            if (filtered.size() >= capacity) {
                break;
            }
            if (!included(row)) {
                continue;
            }
            filtered.add(row);
        }
        return List.copyOf(filtered);
    }

    /** Whether {@code row} belongs in the leaderboard: not exempt, above the floor, and (optionally) not banned. */
    private boolean included(BaltopRow row) {
        if (exemption.isExempt(row.owner())) {
            return false;
        }
        if (row.balance().amount().compareTo(minBalance) < 0) {
            return false;
        }
        return !excludeBanned || !banned.isBanned(row.owner());
    }

    private void scheduleNext() {
        scheduler.asyncAfter(ttl, () -> {
            if (!running) {
                return;
            }
            refreshAll();
            scheduleNext();
        });
    }
}

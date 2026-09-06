package com.uxplima.uxmessentials.playerwarps.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.playerwarps.application.RentConfig;
import com.uxplima.uxmessentials.playerwarps.application.RentPolicy;
import com.uxplima.uxmessentials.playerwarps.application.RentReminders;
import com.uxplima.uxmessentials.playerwarps.application.SettleRent;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpEconomy;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.RentMailer;
import com.uxplima.uxmessentials.playerwarps.application.port.RentReminderCandidate;
import com.uxplima.uxmessentials.playerwarps.domain.ChargeError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.RentState;
import com.uxplima.uxmessentials.playerwarps.domain.WarpStatus;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.Test;

class RentSweepTest {

    private static final Instant NOW = Instant.parse("2026-07-10T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Duration INTERVAL = Duration.ofMinutes(60);
    private static final WorldRef WORLD = new WorldRef(new UUID(9L, 9L), "world");
    private static final PlayerRef OWNER = new PlayerRef(new UUID(1L, 1L), "mara");

    private final FakeRepo repo = new FakeRepo();
    private final OkEconomy economy = new OkEconomy();
    private final RecordingMailer mailer = new RecordingMailer();

    private static RentConfig config(boolean enabled) {
        return new RentConfig(
                enabled,
                new BigDecimal("100"),
                "default",
                Duration.ofDays(7),
                Duration.ofDays(3),
                List.of(Duration.ofHours(24)),
                Set.of(),
                Set.of(),
                Set.of());
    }

    private RentSweep sweep(RentConfig config, Scheduler scheduler) {
        SettleRent settle = new SettleRent(repo, economy, new RentPolicy(), config, CLOCK);
        RentReminders reminders = new RentReminders(repo, mailer, config, CLOCK);
        return new RentSweep(repo, settle, reminders, config, scheduler, INTERVAL, CLOCK, mock(Logger.class));
    }

    private static PlayerWarp warp(long id, String name, WarpStatus status, RentState rent) {
        return PlayerWarp.create(OWNER, "mara", PlayerWarpName.of(name), Position.of(WORLD, 0, 64, 0), NOW)
                .withId(PlayerWarpId.of(id))
                .withStatus(status, NOW)
                .withRent(rent, NOW);
    }

    @Test
    void aDisabledSubGroupSchedulesNothing() {
        Scheduler scheduler = mock(Scheduler.class);

        sweep(config(false), scheduler).start();

        verify(scheduler, never()).asyncAfter(any(), any());
    }

    @Test
    void anEnabledSubGroupSchedulesTheLoop() {
        Scheduler scheduler = mock(Scheduler.class);

        sweep(config(true), scheduler).start();

        verify(scheduler).asyncAfter(eq(INTERVAL), any());
    }

    @Test
    void oneSweepChargesDueWarpsRetriesSuspendedAndSendsReminders() {
        repo.due.add(warp(1, "citadel", WarpStatus.ACTIVE, rent(NOW.minusSeconds(60))));
        repo.suspended.add(warp(
                2,
                "haven",
                WarpStatus.SUSPENDED,
                new RentState(
                        NOW.minusSeconds(120), Optional.of(NOW.minusSeconds(60)), Optional.of(NOW.plusSeconds(3600)))));
        repo.candidates.add(new RentReminderCandidate(
                PlayerWarpId.of(3), OWNER, PlayerWarpName.of("spire"), NOW.plus(Duration.ofHours(20)), 0));

        sweep(config(true), mock(Scheduler.class)).sweepOnce();

        // The due warp renewed (ACTIVE, term advanced) and the suspended one was restored: both saved.
        PlayerWarp renewed = repo.saved(1);
        assertThat(renewed.status()).isEqualTo(WarpStatus.ACTIVE);
        assertThat(renewed.rent().orElseThrow().paidUntil()).isEqualTo(NOW.plus(Duration.ofDays(7)));
        assertThat(repo.saved(2).status()).isEqualTo(WarpStatus.ACTIVE);
        // The approaching warp got exactly one reminder mail and its dedup stage was bumped.
        assertThat(mailer.count).isEqualTo(1);
        assertThat(repo.reminded.get(PlayerWarpId.of(3))).isEqualTo(1);
    }

    private static RentState rent(Instant paidUntil) {
        return new RentState(paidUntil, Optional.empty(), Optional.empty());
    }

    /** A hand-seeded repository: presets the due/suspended/reminder rows and records saves and reminder bumps. */
    private static final class FakeRepo implements PlayerWarpRepository {
        final List<PlayerWarp> due = new ArrayList<>();
        final List<PlayerWarp> suspended = new ArrayList<>();
        final List<RentReminderCandidate> candidates = new ArrayList<>();
        final Map<PlayerWarpId, PlayerWarp> saved = new LinkedHashMap<>();
        final Map<PlayerWarpId, Integer> reminded = new LinkedHashMap<>();

        PlayerWarp saved(long id) {
            return java.util.Objects.requireNonNull(saved.get(PlayerWarpId.of(id)), "no saved warp " + id);
        }

        @Override
        public List<PlayerWarp> dueForRent(Instant now, int limit) {
            return due.stream().limit(limit).toList();
        }

        @Override
        public List<PlayerWarp> suspendedForRent(int limit) {
            return suspended.stream().limit(limit).toList();
        }

        @Override
        public List<RentReminderCandidate> remindableForRent(Instant now, Instant horizon, int maxStage, int limit) {
            return candidates.stream().limit(limit).toList();
        }

        @Override
        public void markRentReminded(PlayerWarpId id, int stage) {
            reminded.put(id, stage);
        }

        @Override
        public PlayerWarpId save(PlayerWarp warp) {
            PlayerWarpId id = warp.id().orElseThrow();
            saved.put(id, warp);
            return id;
        }

        @Override
        public Optional<PlayerWarp> findByName(PlayerWarpName name) {
            return Optional.empty();
        }

        @Override
        public Optional<PlayerWarp> findById(PlayerWarpId id) {
            return Optional.ofNullable(saved.get(id));
        }

        @Override
        public List<PlayerWarp> ownedBy(PlayerRef owner) {
            return List.of();
        }

        @Override
        public List<PlayerWarp> publicOwnedBy(PlayerRef owner) {
            return List.of();
        }

        @Override
        public int count(PlayerRef owner) {
            return 0;
        }

        @Override
        public boolean existsByName(PlayerWarpName name) {
            return false;
        }

        @Override
        public void deleteById(PlayerWarpId id) {}

        @Override
        public void recordVisit(PlayerWarpId id) {}

        @Override
        public void updateRating(PlayerWarpId id, com.uxplima.uxmessentials.playerwarps.domain.RatingSummary summary) {}

        @Override
        public void refreshFavouriteCount(PlayerWarpId id) {}
    }

    /** An economy whose rent collection always succeeds, for the renew/restore paths. */
    private static final class OkEconomy implements PlayerWarpEconomy {
        @Override
        public Result<Unit, ChargeError> chargeAndAccrue(
                PlayerRef payer, PlayerWarpId warp, BigDecimal price, String currencyId) {
            return Result.ok();
        }

        @Override
        public boolean canAfford(PlayerRef who, BigDecimal amount, String currencyId) {
            return true;
        }

        @Override
        public Result<Unit, ChargeError> withdraw(PlayerWarpId warp, PlayerRef to) {
            return Result.ok();
        }

        @Override
        public Result<Unit, ChargeError> refund(PlayerRef to, BigDecimal amount, String currencyId) {
            return Result.ok();
        }

        @Override
        public Result<Unit, ChargeError> collectRent(
                PlayerWarpId warp, PlayerRef owner, BigDecimal amount, String currencyId) {
            return Result.ok();
        }

        @Override
        public Result<Unit, ChargeError> chargeOwner(PlayerRef owner, BigDecimal amount, String currencyId) {
            return Result.ok();
        }
    }

    /** Counts the reminder mails the pass leaves. */
    private static final class RecordingMailer implements RentMailer {
        private int count;

        @Override
        public void mail(PlayerRef owner, MessageKey key, Map<String, String> placeholders) {
            count++;
        }
    }
}

package com.uxplima.uxmessentials.moderation.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.PunishmentReport;
import com.uxplima.uxmessentials.moderation.domain.SanctionAction;
import com.uxplima.uxmessentials.moderation.domain.SanctionHistoryEntry;
import com.uxplima.uxmessentials.moderation.domain.StaffPunishmentCount;
import org.junit.jupiter.api.Test;

/**
 * The pure {@link PunishmentStats} aggregation over fixture rows: it groups by the issuing staff member,
 * tallies only the four punitive kinds (ban/mute/warn/kick), folds lifts out, honours the optional window, and
 * orders the leaderboard most-active first. No ports, no clock, a deterministic function of the rows and the
 * window.
 */
class PunishmentStatsTest {

    private static final Instant T0 = Instant.parse("2026-07-01T00:00:00Z");

    private final PunishmentStats stats = new PunishmentStats();

    @Test
    void groupsByStaffAndCountsEachPunitiveKind() {
        Issuer mod = staff("Mod");
        List<SanctionHistoryEntry> rows = List.of(
                by(mod, SanctionAction.BAN, T0),
                by(mod, SanctionAction.MUTE, T0.plusSeconds(1)),
                by(mod, SanctionAction.WARN, T0.plusSeconds(2)),
                by(mod, SanctionAction.KICK, T0.plusSeconds(3)),
                by(mod, SanctionAction.BAN, T0.plusSeconds(4)));

        PunishmentReport report = stats.aggregate(rows, Optional.empty());

        assertThat(report.leaderboard()).hasSize(1);
        StaffPunishmentCount count = report.leaderboard().get(0);
        assertThat(count.staffName()).isEqualTo("Mod");
        assertThat(count.bans()).isEqualTo(2);
        assertThat(count.mutes()).isEqualTo(1);
        assertThat(count.warns()).isEqualTo(1);
        assertThat(count.kicks()).isEqualTo(1);
        assertThat(count.total()).isEqualTo(5);
    }

    @Test
    void foldsLiftsOutSoAReverserWithNoPunishmentsIsAbsent() {
        Issuer reverser = staff("OnlyLifts");
        List<SanctionHistoryEntry> rows =
                List.of(by(reverser, SanctionAction.UNBAN, T0), by(reverser, SanctionAction.UNMUTE, T0.plusSeconds(1)));

        PunishmentReport report = stats.aggregate(rows, Optional.empty());

        assertThat(report.isEmpty()).isTrue();
    }

    @Test
    void ordersLeaderboardByTotalDescending() {
        Issuer busy = staff("Busy");
        Issuer quiet = staff("Quiet");
        List<SanctionHistoryEntry> rows = List.of(
                by(quiet, SanctionAction.WARN, T0),
                by(busy, SanctionAction.BAN, T0.plusSeconds(1)),
                by(busy, SanctionAction.MUTE, T0.plusSeconds(2)));

        PunishmentReport report = stats.aggregate(rows, Optional.empty());

        assertThat(report.leaderboard())
                .extracting(StaffPunishmentCount::staffName)
                .containsExactly("Busy", "Quiet");
    }

    @Test
    void breaksLeaderboardTiesByNameSoOrderingIsStable() {
        List<SanctionHistoryEntry> rows = List.of(
                by(staff("Zeta"), SanctionAction.BAN, T0), by(staff("Alpha"), SanctionAction.BAN, T0.plusSeconds(1)));

        PunishmentReport report = stats.aggregate(rows, Optional.empty());

        assertThat(report.leaderboard())
                .extracting(StaffPunishmentCount::staffName)
                .containsExactly("Alpha", "Zeta");
    }

    @Test
    void windowExcludesRowsBeforeTheThreshold() {
        Issuer mod = staff("Mod");
        Instant threshold = T0.plusSeconds(100);
        List<SanctionHistoryEntry> rows = List.of(
                by(mod, SanctionAction.BAN, T0), // before the window
                by(mod, SanctionAction.MUTE, threshold), // on the boundary, included
                by(mod, SanctionAction.WARN, threshold.plusSeconds(10)));

        PunishmentReport report = stats.aggregate(rows, Optional.of(threshold));

        StaffPunishmentCount count = report.leaderboard().get(0);
        assertThat(count.bans()).isZero();
        assertThat(count.mutes()).isEqualTo(1);
        assertThat(count.warns()).isEqualTo(1);
        assertThat(count.total()).isEqualTo(2);
    }

    @Test
    void consoleRowsFoldTogetherUnderTheConsoleName() {
        List<SanctionHistoryEntry> rows = List.of(
                byConsole("Console", SanctionAction.KICK, T0),
                byConsole("Console", SanctionAction.BAN, T0.plusSeconds(1)));

        PunishmentReport report = stats.aggregate(rows, Optional.empty());

        assertThat(report.leaderboard()).hasSize(1);
        StaffPunishmentCount count = report.leaderboard().get(0);
        assertThat(count.staff()).isEmpty();
        assertThat(count.total()).isEqualTo(2);
    }

    @Test
    void usesTheMostRecentNameForARenamedStaffMember() {
        UUID id = UUID.randomUUID();
        List<SanctionHistoryEntry> rows = List.of(
                by(Issuer.stored(Optional.of(id), "OldName"), SanctionAction.BAN, T0),
                by(Issuer.stored(Optional.of(id), "NewName"), SanctionAction.MUTE, T0.plusSeconds(60)));

        PunishmentReport report = stats.aggregate(rows, Optional.empty());

        assertThat(report.leaderboard()).hasSize(1);
        assertThat(report.leaderboard().get(0).staffName()).isEqualTo("NewName");
    }

    @Test
    void serverTotalsAreTheColumnSumsOfTheLeaderboard() {
        List<SanctionHistoryEntry> rows = List.of(
                by(staff("A"), SanctionAction.BAN, T0),
                by(staff("B"), SanctionAction.BAN, T0.plusSeconds(1)),
                by(staff("A"), SanctionAction.MUTE, T0.plusSeconds(2)));

        PunishmentReport report = stats.aggregate(rows, Optional.empty());

        assertThat(report.totalBans()).isEqualTo(2);
        assertThat(report.totalMutes()).isEqualTo(1);
        assertThat(report.total()).isEqualTo(3);
    }

    private static Issuer staff(String name) {
        return Issuer.stored(Optional.of(UUID.randomUUID()), name);
    }

    private static SanctionHistoryEntry by(Issuer actor, SanctionAction action, Instant at) {
        return new SanctionHistoryEntry(
                action, UUID.randomUUID(), actor, Optional.empty(), at, Optional.empty(), Optional.empty());
    }

    private static SanctionHistoryEntry byConsole(String name, SanctionAction action, Instant at) {
        return by(Issuer.console(name), action, at);
    }
}

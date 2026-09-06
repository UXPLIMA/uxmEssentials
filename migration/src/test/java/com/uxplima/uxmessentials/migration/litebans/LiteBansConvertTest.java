package com.uxplima.uxmessentials.migration.litebans;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.migration.ImportOptions;
import com.uxplima.uxmessentials.migration.ImportPlan;
import com.uxplima.uxmessentials.migration.ImportRecord;
import com.uxplima.uxmessentials.migration.convert.litebans.LiteBansConfig;
import com.uxplima.uxmessentials.migration.convert.litebans.LiteBansConvert;
import com.uxplima.uxmessentials.moderation.domain.IpBan;
import com.uxplima.uxmessentials.moderation.domain.MuteState;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * In-process round-trip for the LiteBans JDBC source. It stands up an H2 in-memory database with the three
 * LiteBans tables, inserts rows covering every mapping branch, permanent ({@code until=-1}, {@code until=0}),
 * temp, removed ({@code active=0}), an IP ban, a sentinel target (skipped), a console-issued ban, a mute, and
 * a warning, then drives {@link LiteBansConvert#plan} and asserts the mapped {@link ImportRecord}s.
 *
 * <p>This validates the reader and mapper logic against a real JDBC driver. The H2 <em>file</em> layout a live
 * LiteBans 2.x install writes is a separate compatibility concern (a much older bundled engine), and is a
 * maintainer-verify caveat: this test exercises the dependable connectable-URL path.
 */
class LiteBansConvertTest {

    private static final UUID TARGET = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TEMP_TARGET = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID ZERO_TARGET = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID REMOVED_TARGET = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID CONSOLE_TARGET = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final UUID MUTE_TARGET = UUID.fromString("66666666-6666-6666-6666-666666666666");
    private static final UUID WARN_TARGET = UUID.fromString("77777777-7777-7777-7777-777777777777");
    private static final UUID STAFF = UUID.fromString("99999999-9999-9999-9999-999999999999");

    private static final long NOW = 1_700_000_000_000L;
    private static final long FUTURE = 1_900_000_000_000L;

    private String url;
    private Connection keepAlive;

    @BeforeEach
    void setUp() throws SQLException {
        url = "jdbc:h2:mem:litebans_" + UUID.randomUUID().toString().replace("-", "") + ";DB_CLOSE_DELAY=-1";
        keepAlive = DriverManager.getConnection(url);
        createSchema();
        insertRows();
    }

    @AfterEach
    void tearDown() throws SQLException {
        keepAlive.close();
    }

    @Test
    void streamsTheMappedSanctionsAndSkipsRemovedAndSentinelRows() {
        List<ImportRecord> records = plan();

        // 7 ban rows inserted: permanent(-1), permanent(0), temp, console-issued, removed(active=0), ip ban,
        // and an #offline# sentinel. Removed and sentinel are dropped; the ip ban becomes an ip-ban record;
        // the four remaining UUID bans become ban records.
        assertThat(records).filteredOn(r -> r instanceof ImportRecord.BanRecord).hasSize(4);
        assertThat(records)
                .filteredOn(r -> r instanceof ImportRecord.IpBanRecord)
                .hasSize(1);
        assertThat(records)
                .filteredOn(r -> r instanceof ImportRecord.ModerationRecord)
                .hasSize(1);
        assertThat(records)
                .filteredOn(r -> r instanceof ImportRecord.WarnRecord)
                .hasSize(1);
    }

    @Test
    void aMinusOneUntilBecomesAPermanentBanFarInTheFuture() {
        TempbanState.Active ban = banFor(TARGET);
        Instant expiry = ban.expiry().orElseThrow();
        // until <= 0 => issuedAt + PERMANENT_SPAN (365_000 days), far past any temp ban.
        assertThat(expiry).isEqualTo(Instant.ofEpochMilli(NOW).plus(Duration.ofDays(365_000L)));
    }

    @Test
    void aZeroUntilAlsoBecomesAPermanentBan() {
        TempbanState.Active ban = banFor(ZERO_TARGET);
        assertThat(ban.expiry()).contains(Instant.ofEpochMilli(NOW).plus(Duration.ofDays(365_000L)));
    }

    @Test
    void aFutureUntilBecomesATempBanWithThatExpiry() {
        TempbanState.Active ban = banFor(TEMP_TARGET);
        assertThat(ban.expiry()).contains(Instant.ofEpochMilli(FUTURE));
    }

    @Test
    void aConsoleIssuedBanCarriesAConsoleIssuer() {
        TempbanState.Active ban = banFor(CONSOLE_TARGET);
        assertThat(ban.issuer().uuid()).isEmpty();
        assertThat(ban.issuer().name()).isEqualTo("Console");
    }

    @Test
    void aPlayerIssuedBanCarriesAStoredIssuer() {
        TempbanState.Active ban = banFor(TARGET);
        assertThat(ban.issuer().uuid()).contains(STAFF);
        assertThat(ban.issuer().name()).isEqualTo("StaffMember");
    }

    @Test
    void anIpBanIsKeyedByAddressAndCarriesItsTarget() {
        ImportRecord.IpBanRecord record = (ImportRecord.IpBanRecord) plan().stream()
                .filter(r -> r instanceof ImportRecord.IpBanRecord)
                .findFirst()
                .orElseThrow();
        IpBan ban = record.ban();
        assertThat(ban.ip()).isEqualTo("203.0.113.7");
        assertThat(ban.until()).isEmpty(); // until=-1 -> permanent ip ban (empty expiry)
    }

    @Test
    void aMuteMapsToAPermanentMuteModerationRecord() {
        ImportRecord.ModerationRecord record = (ImportRecord.ModerationRecord) plan().stream()
                .filter(r -> r instanceof ImportRecord.ModerationRecord)
                .findFirst()
                .orElseThrow();
        assertThat(record.moderation().hasMute()).isTrue();
        assertThat(record.moderation().profile().target().uuid()).isEqualTo(MUTE_TARGET);
        assertThat(record.moderation().profile().mute()).isInstanceOf(MuteState.Permanent.class);
    }

    @Test
    void aWarningMapsToATimedWarnRecord() {
        ImportRecord.WarnRecord record = (ImportRecord.WarnRecord) plan().stream()
                .filter(r -> r instanceof ImportRecord.WarnRecord)
                .findFirst()
                .orElseThrow();
        assertThat(record.target().uuid()).isEqualTo(WARN_TARGET);
        assertThat(record.warn().expiresAt()).contains(Instant.ofEpochMilli(FUTURE));
    }

    @Test
    void detectIsTrueWhenTheDatabaseOpens() {
        assertThat(convert().detect(java.nio.file.Path.of("."))).isTrue();
    }

    private TempbanState.Active banFor(UUID target) {
        return plan().stream()
                .filter(r -> r instanceof ImportRecord.BanRecord)
                .map(r -> (ImportRecord.BanRecord) r)
                .filter(r -> r.target().uuid().equals(target))
                .map(r -> (TempbanState.Active) r.ban())
                .findFirst()
                .orElseThrow();
    }

    private List<ImportRecord> plan() {
        try (ImportPlan plan = convert().plan(ImportOptions.live(java.nio.file.Path.of(".")))) {
            return plan.records().toList();
        }
    }

    private LiteBansConvert convert() {
        LiteBansConfig config = new LiteBansConfig(Optional.of(url), "", "", "litebans_", Optional.empty());
        return new LiteBansConvert(config, NoOpLogger.INSTANCE);
    }

    private void createSchema() throws SQLException {
        try (Statement st = keepAlive.createStatement()) {
            st.execute(banLikeTable("litebans_bans", ""));
            st.execute(banLikeTable("litebans_mutes", ""));
            st.execute(banLikeTable("litebans_warnings", ", warned BOOLEAN"));
        }
    }

    private static String banLikeTable(String name, String extraColumns) {
        return "CREATE TABLE " + name + " ("
                + "uuid VARCHAR(36), ip VARCHAR(45), reason VARCHAR(255), "
                + "banned_by_uuid VARCHAR(36), banned_by_name VARCHAR(64), "
                + "time BIGINT, until BIGINT, "
                + "ipban BOOLEAN, ipban_wildcard BOOLEAN, active BOOLEAN, silent BOOLEAN"
                + extraColumns + ")";
    }

    private void insertRows() throws SQLException {
        // bans
        insertBan(TARGET.toString(), null, "grief", STAFF.toString(), "StaffMember", -1, false, false, true);
        insertBan(ZERO_TARGET.toString(), null, "grief", STAFF.toString(), "StaffMember", 0, false, false, true);
        insertBan(TEMP_TARGET.toString(), null, "temp", STAFF.toString(), "StaffMember", FUTURE, false, false, true);
        insertBan(REMOVED_TARGET.toString(), null, "lifted", STAFF.toString(), "StaffMember", -1, false, false, false);
        insertBan("203.0.113.7-target", "203.0.113.7", "ip", STAFF.toString(), "StaffMember", -1, true, false, true);
        insertBan("#offline#", null, "sentinel", STAFF.toString(), "StaffMember", -1, false, false, true);
        insertBan(CONSOLE_TARGET.toString(), null, "auto", "CONSOLE", "Console", -1, false, false, true);
        // mute (permanent) + warning (timed)
        insertMute(MUTE_TARGET.toString(), "spam", STAFF.toString(), "StaffMember", -1);
        insertWarning(WARN_TARGET.toString(), "rude", STAFF.toString(), "StaffMember", FUTURE);
    }

    private void insertBan(
            String uuid,
            String ip,
            String reason,
            String byUuid,
            String byName,
            long until,
            boolean ipban,
            boolean wildcard,
            boolean active)
            throws SQLException {
        String sql = "INSERT INTO litebans_bans (uuid, ip, reason, banned_by_uuid, banned_by_name, time, until, "
                + "ipban, ipban_wildcard, active, silent) VALUES (?,?,?,?,?,?,?,?,?,?,?)";
        try (var ps = keepAlive.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setString(2, ip);
            ps.setString(3, reason);
            ps.setString(4, byUuid);
            ps.setString(5, byName);
            ps.setLong(6, NOW);
            ps.setLong(7, until);
            ps.setBoolean(8, ipban);
            ps.setBoolean(9, wildcard);
            ps.setBoolean(10, active);
            ps.setBoolean(11, false);
            ps.executeUpdate();
        }
    }

    private void insertMute(String uuid, String reason, String byUuid, String byName, long until) throws SQLException {
        String sql = "INSERT INTO litebans_mutes (uuid, ip, reason, banned_by_uuid, banned_by_name, time, until, "
                + "ipban, ipban_wildcard, active, silent) VALUES (?,?,?,?,?,?,?,?,?,?,?)";
        try (var ps = keepAlive.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setString(2, null);
            ps.setString(3, reason);
            ps.setString(4, byUuid);
            ps.setString(5, byName);
            ps.setLong(6, NOW);
            ps.setLong(7, until);
            ps.setBoolean(8, false);
            ps.setBoolean(9, false);
            ps.setBoolean(10, true);
            ps.setBoolean(11, false);
            ps.executeUpdate();
        }
    }

    private void insertWarning(String uuid, String reason, String byUuid, String byName, long until)
            throws SQLException {
        String sql = "INSERT INTO litebans_warnings (uuid, ip, reason, banned_by_uuid, banned_by_name, time, until, "
                + "ipban, ipban_wildcard, active, silent, warned) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
        try (var ps = keepAlive.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setString(2, null);
            ps.setString(3, reason);
            ps.setString(4, byUuid);
            ps.setString(5, byName);
            ps.setLong(6, NOW);
            ps.setLong(7, until);
            ps.setBoolean(8, false);
            ps.setBoolean(9, false);
            ps.setBoolean(10, true);
            ps.setBoolean(11, false);
            ps.setBoolean(12, true);
            ps.executeUpdate();
        }
    }

    private enum NoOpLogger implements Logger {
        INSTANCE;

        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}

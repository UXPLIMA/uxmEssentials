package com.uxplima.uxmessentials.migration.convert.litebans.map;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import com.uxplima.uxmessentials.migration.ImportRecord;
import com.uxplima.uxmessentials.migration.convert.litebans.parse.LiteBansRow;
import com.uxplima.uxmessentials.moderation.domain.IpBan;
import com.uxplima.uxmessentials.moderation.domain.Issuer;
import com.uxplima.uxmessentials.moderation.domain.TempbanState;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Maps one {@code litebans_bans} row into the moderation domain, a UUID {@link ImportRecord.BanRecord} or,
 * when the row is an IP ban, an {@link ImportRecord.IpBanRecord} (docs/12-migration §5.4). Pure ACL: no
 * JDBC, no Bukkit.
 *
 * <p>LiteBans marks a permanent punishment with {@code until <= 0}. A permanent UUID ban becomes a
 * {@link TempbanState.Active} whose expiry is the far-future sentinel span (mirroring how the live
 * {@code /ban} writes a permanent ban as a far-out tempban, {@code ImportRecord.BanRecord} javadoc); a temp
 * ban carries its real {@code until}. A wildcard IP ban (an address range) and a row whose target UUID is a
 * LiteBans sentinel name no real account, so both are dropped: the importer never invents a target.
 */
@NullMarked
public final class LiteBansBanMapper {

    // Mirrors moderation.application.Ban.PERMANENT_SPAN (Duration.ofDays(365_000)), which is package-private
    // to :core, so the importer cannot reference it directly. A permanent ban's expiry is issuedAt + this.
    private static final Duration PERMANENT_SPAN = Duration.ofDays(365_000L);

    /** Map a bans-table row to a ban record, or empty when the row is skipped (wildcard / sentinel target). */
    public Optional<ImportRecord> map(LiteBansRow row) {
        if (row.ipban()) {
            return ipBan(row);
        }
        return uuidBan(row);
    }

    private Optional<ImportRecord> uuidBan(LiteBansRow row) {
        Optional<PlayerRef> target = LiteBansActors.target(row);
        if (target.isEmpty()) {
            return Optional.empty();
        }
        Instant issuedAt = Instant.ofEpochMilli(row.time());
        Instant until = expiry(row, issuedAt);
        TempbanState ban = TempbanState.active(until, LiteBansActors.issuer(row), row.reason(), issuedAt);
        return Optional.of(new ImportRecord.BanRecord(target.get(), ban));
    }

    private Optional<ImportRecord> ipBan(LiteBansRow row) {
        if (row.ipbanWildcard() || row.ip().isEmpty()) {
            // A wildcard (range) ban has no single address to key on, and a row without an IP cannot be an
            // address ban: drop both rather than guess.
            return Optional.empty();
        }
        Instant issuedAt = Instant.ofEpochMilli(row.time());
        Optional<Instant> until = row.isPermanent() ? Optional.empty() : Optional.of(Instant.ofEpochMilli(row.until()));
        Issuer issuer = LiteBansActors.issuer(row);
        IpBan ban = new IpBan(
                row.ip().orElseThrow(),
                until,
                row.reason(),
                LiteBansActors.target(row).map(PlayerRef::uuid),
                issuer,
                issuedAt);
        return Optional.of(new ImportRecord.IpBanRecord(ban));
    }

    private static Instant expiry(LiteBansRow row, Instant issuedAt) {
        return row.isPermanent() ? issuedAt.plus(PERMANENT_SPAN) : Instant.ofEpochMilli(row.until());
    }
}

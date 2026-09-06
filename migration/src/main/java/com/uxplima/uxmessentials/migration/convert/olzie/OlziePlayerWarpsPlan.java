package com.uxplima.uxmessentials.migration.convert.olzie;

import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.migration.ImportPlan;
import com.uxplima.uxmessentials.migration.ImportRecord;
import com.uxplima.uxmessentials.migration.convert.jdbc.JdbcConnection;
import com.uxplima.uxmessentials.migration.convert.jdbc.JdbcSource;
import com.uxplima.uxmessentials.migration.convert.jdbc.RowMapper;
import com.uxplima.uxmessentials.migration.convert.map.ImportedPlayerWarp;
import com.uxplima.uxmessentials.migration.convert.olzie.map.OlzieWarpMapper;
import com.uxplima.uxmessentials.migration.convert.olzie.parse.OlzieBanRow;
import com.uxplima.uxmessentials.migration.convert.olzie.parse.OlziePlayerRow;
import com.uxplima.uxmessentials.migration.convert.olzie.parse.OlzieRatingRow;
import com.uxplima.uxmessentials.migration.convert.olzie.parse.OlzieRowReaders;
import com.uxplima.uxmessentials.migration.convert.olzie.parse.OlzieTables;
import com.uxplima.uxmessentials.migration.convert.olzie.parse.OlzieVisitRow;
import com.uxplima.uxmessentials.migration.convert.olzie.parse.OlzieWarpRow;
import com.uxplima.uxmessentials.playerwarps.domain.WarpRole;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The Olzie PlayerWarps source's {@link ImportPlan}. Olzie stores every player as a uuid text and keys each side table
 * on the warp's SQLite {@code rowid}, so the plan first loads the small side tables. The whitelist, managers, bans,
 * ratings, favourites and the distinct-visitor tally. Grouped by warp id, then streams the warps, joining each to its
 * side data and handing it to the {@link OlzieWarpMapper}. The side tables are bounded (a player-warp network is modest
 * next to a 50 000-file userdata tree), so gathering them eagerly is what lets each warp be assembled without a
 * per-warp query; the warp stream itself stays lazy.
 *
 * <p>Resilience mirrors the other sources' "one bad record is skipped, never fatal" rule: a table that fails to query
 * (missing, or the database unreachable) is logged and yields no rows rather than aborting the run; a warp whose owner
 * uuid does not parse or whose world the server does not know is dropped and counted as skipped, not failed.
 */
@NullMarked
final class OlziePlayerWarpsPlan implements ImportPlan {

    private final JdbcConnection connection;
    private final OlzieWarpMapper mapper;
    private final Logger log;

    OlziePlayerWarpsPlan(JdbcConnection connection, OlzieWarpMapper mapper, Logger log) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public Stream<ImportRecord> records() {
        Map<Long, List<UUID>> whitelist = playerListMap(OlzieTables.SELECT_WHITELIST);
        Map<Long, List<UUID>> favourites = playerListMap(OlzieTables.SELECT_FAVOURITES);
        Map<Long, List<ImportedPlayerWarp.Member>> managers = managerMap();
        Map<Long, List<ImportedPlayerWarp.Ban>> bans = banMap();
        Map<Long, List<ImportedPlayerWarp.Rating>> ratings = ratingMap();
        Map<Long, Integer> unique = visitMap();
        return warpRows().stream().flatMap(row -> mapWarp(row, whitelist, favourites, managers, bans, ratings, unique));
    }

    private Stream<ImportRecord> mapWarp(
            OlzieWarpRow row,
            Map<Long, List<UUID>> whitelist,
            Map<Long, List<UUID>> favourites,
            Map<Long, List<ImportedPlayerWarp.Member>> managers,
            Map<Long, List<ImportedPlayerWarp.Ban>> bans,
            Map<Long, List<ImportedPlayerWarp.Rating>> ratings,
            Map<Long, Integer> unique) {
        OlzieWarpMapper.Sidecar side = new OlzieWarpMapper.Sidecar(
                whitelist.getOrDefault(row.id(), List.of()),
                bans.getOrDefault(row.id(), List.of()),
                favourites.getOrDefault(row.id(), List.of()),
                managers.getOrDefault(row.id(), List.of()),
                ratings.getOrDefault(row.id(), List.of()),
                unique.getOrDefault(row.id(), 0));
        try {
            return mapper.map(row, side).<ImportRecord>map(ImportRecord.PlayerWarpRecord::new).stream();
        } catch (RuntimeException badRow) {
            log.warn("skipping a malformed Olzie warp {}: {}", row.name(), String.valueOf(badRow.getMessage()));
            return Stream.empty();
        }
    }

    private Map<Long, List<UUID>> playerListMap(String sql) {
        Map<Long, List<UUID>> byWarp = new HashMap<>();
        for (OlziePlayerRow row : rows(sql, OlzieRowReaders.playerRow())) {
            parseUuid(row.playerUuid())
                    .ifPresent(uuid -> byWarp.computeIfAbsent(row.warpId(), key -> new ArrayList<>())
                            .add(uuid));
        }
        return byWarp;
    }

    private Map<Long, List<ImportedPlayerWarp.Member>> managerMap() {
        Map<Long, List<ImportedPlayerWarp.Member>> byWarp = new HashMap<>();
        for (OlziePlayerRow row : rows(OlzieTables.SELECT_MANAGERS, OlzieRowReaders.playerRow())) {
            parseUuid(row.playerUuid())
                    .ifPresent(uuid -> byWarp.computeIfAbsent(row.warpId(), key -> new ArrayList<>())
                            // Olzie records no grant timestamp for a manager, so the membership is stamped at the
                            // epoch.
                            .add(new ImportedPlayerWarp.Member(uuid, WarpRole.MANAGER, Instant.EPOCH)));
        }
        return byWarp;
    }

    private Map<Long, List<ImportedPlayerWarp.Ban>> banMap() {
        Map<Long, List<ImportedPlayerWarp.Ban>> byWarp = new HashMap<>();
        for (OlzieBanRow row : rows(OlzieTables.SELECT_BANNED, OlzieRowReaders.banRow())) {
            parseUuid(row.playerUuid())
                    .ifPresent(uuid -> byWarp.computeIfAbsent(row.warpId(), key -> new ArrayList<>())
                            .add(new ImportedPlayerWarp.Ban(
                                    uuid,
                                    Optional.empty(),
                                    reason(row.reason()),
                                    Optional.empty(),
                                    instant(row.time()))));
        }
        return byWarp;
    }

    private Map<Long, List<ImportedPlayerWarp.Rating>> ratingMap() {
        Map<Long, List<ImportedPlayerWarp.Rating>> byWarp = new HashMap<>();
        for (OlzieRatingRow row : rows(OlzieTables.SELECT_RATES, OlzieRowReaders.ratingRow())) {
            parseUuid(row.reviewerUuid())
                    .ifPresent(uuid -> byWarp.computeIfAbsent(row.warpId(), key -> new ArrayList<>())
                            .add(new ImportedPlayerWarp.Rating(uuid, clampStars(row.rate()), Instant.EPOCH)));
        }
        return byWarp;
    }

    private Map<Long, Integer> visitMap() {
        Map<Long, Integer> byWarp = new HashMap<>();
        for (OlzieVisitRow row : rows(OlzieTables.SELECT_VISITS, OlzieRowReaders.visitRow())) {
            byWarp.put(row.warpId(), Math.max(0, row.uniqueVisitors()));
        }
        return byWarp;
    }

    private List<OlzieWarpRow> warpRows() {
        return rows(OlzieTables.SELECT_WARPS, OlzieRowReaders.warp());
    }

    private static Optional<UUID> parseUuid(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw.strip()));
        } catch (IllegalArgumentException malformed) {
            return Optional.empty();
        }
    }

    private static Optional<String> reason(@Nullable String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String trimmed = raw.strip();
        return trimmed.isEmpty() ? Optional.empty() : Optional.of(trimmed);
    }

    private <R> List<R> rows(String sql, RowMapper<R> reader) {
        try {
            return JdbcSource.query(connection, sql, reader);
        } catch (SQLException unreadable) {
            // A missing table or an unreachable database yields no rows; the run continues with the rest.
            log.warn("Olzie query failed, skipping table: {}", String.valueOf(unreadable.getMessage()));
            return List.of();
        }
    }

    private static int clampStars(int stars) {
        return Math.max(1, Math.min(5, stars));
    }

    private static Instant instant(long epochMillis) {
        return epochMillis > 0 ? Instant.ofEpochMilli(epochMillis) : Instant.EPOCH;
    }

    @Override
    public void close() {
        // Each query opens and closes its own read-only connection; nothing is held open across records.
    }
}

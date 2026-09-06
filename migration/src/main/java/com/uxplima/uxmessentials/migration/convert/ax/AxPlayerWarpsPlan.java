package com.uxplima.uxmessentials.migration.convert.ax;

import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

import com.uxplima.uxmessentials.migration.ImportPlan;
import com.uxplima.uxmessentials.migration.ImportRecord;
import com.uxplima.uxmessentials.migration.convert.ax.map.AxWarpMapper;
import com.uxplima.uxmessentials.migration.convert.ax.parse.AxAccessRow;
import com.uxplima.uxmessentials.migration.convert.ax.parse.AxPlayer;
import com.uxplima.uxmessentials.migration.convert.ax.parse.AxRatingRow;
import com.uxplima.uxmessentials.migration.convert.ax.parse.AxRowReaders;
import com.uxplima.uxmessentials.migration.convert.ax.parse.AxTables;
import com.uxplima.uxmessentials.migration.convert.ax.parse.AxVisitRow;
import com.uxplima.uxmessentials.migration.convert.ax.parse.AxWarpRow;
import com.uxplima.uxmessentials.migration.convert.jdbc.JdbcConnection;
import com.uxplima.uxmessentials.migration.convert.jdbc.JdbcSource;
import com.uxplima.uxmessentials.migration.convert.jdbc.RowMapper;
import com.uxplima.uxmessentials.migration.convert.map.ImportedPlayerWarp;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;

/**
 * The AxPlayerWarps source's {@link ImportPlan}. AxPlayerWarps stores every foreign reference as an integer id, so the
 * plan first loads the small lookup tables. The players once, and the whitelist / blacklist / favourite / rating /
 * visit side tables grouped by warp, resolving each player id to a uuid up front. It then streams the warps, joining
 * each to its side data and handing it to the {@link AxWarpMapper}. The side tables are bounded (a player-warp network
 * is modest next to a 50 000-file userdata tree), so gathering them eagerly is what lets each warp be assembled without
 * a per-warp query; the warp stream itself stays lazy.
 *
 * <p>Resilience mirrors the other sources' "one bad record is skipped, never fatal" rule: a table that fails to query
 * (missing, or the database unreachable) is logged and yields no rows rather than aborting the run; a warp whose owner
 * id is unknown or whose world the server does not know is dropped and counted as skipped, not failed.
 */
@NullMarked
final class AxPlayerWarpsPlan implements ImportPlan {

    private final JdbcConnection connection;
    private final AxWarpMapper mapper;
    private final Logger log;

    AxPlayerWarpsPlan(JdbcConnection connection, AxWarpMapper mapper, Logger log) {
        this.connection = Objects.requireNonNull(connection, "connection");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public Stream<ImportRecord> records() {
        Map<Integer, AxPlayer> players = playerMap();
        Map<Integer, List<UUID>> whitelist = playerListMap(AxTables.SELECT_WHITELIST, players);
        Map<Integer, List<UUID>> favourites = playerListMap(AxTables.SELECT_FAVOURITES, players);
        Map<Integer, List<ImportedPlayerWarp.Ban>> bans = banMap(players);
        Map<Integer, List<ImportedPlayerWarp.Rating>> ratings = ratingMap(players);
        Map<Integer, AxVisitRow> visits = visitMap();
        return warpRows().stream().flatMap(row -> mapWarp(row, players, whitelist, favourites, bans, ratings, visits));
    }

    private Stream<ImportRecord> mapWarp(
            AxWarpRow row,
            Map<Integer, AxPlayer> players,
            Map<Integer, List<UUID>> whitelist,
            Map<Integer, List<UUID>> favourites,
            Map<Integer, List<ImportedPlayerWarp.Ban>> bans,
            Map<Integer, List<ImportedPlayerWarp.Rating>> ratings,
            Map<Integer, AxVisitRow> visits) {
        AxPlayer owner = players.get(row.ownerId());
        if (owner == null) {
            log.warn("skipping AxPlayerWarps warp {} with an unknown owner id {}", row.name(), row.ownerId());
            return Stream.empty();
        }
        AxVisitRow tally = visits.getOrDefault(row.id(), new AxVisitRow(row.id(), 0L, 0L));
        AxWarpMapper.Sidecar side = new AxWarpMapper.Sidecar(
                whitelist.getOrDefault(row.id(), List.of()),
                bans.getOrDefault(row.id(), List.of()),
                favourites.getOrDefault(row.id(), List.of()),
                ratings.getOrDefault(row.id(), List.of()),
                tally.total(),
                (int) Math.min(Integer.MAX_VALUE, tally.unique()));
        try {
            return mapper.map(row, owner, side).<ImportRecord>map(ImportRecord.PlayerWarpRecord::new).stream();
        } catch (RuntimeException badRow) {
            log.warn("skipping a malformed AxPlayerWarps warp {}: {}", row.name(), String.valueOf(badRow.getMessage()));
            return Stream.empty();
        }
    }

    private Map<Integer, AxPlayer> playerMap() {
        Map<Integer, AxPlayer> byId = new HashMap<>();
        for (AxPlayer player : rows(AxTables.SELECT_PLAYERS, AxRowReaders.player())) {
            byId.put(player.id(), player);
        }
        return byId;
    }

    private Map<Integer, List<UUID>> playerListMap(String sql, Map<Integer, AxPlayer> players) {
        Map<Integer, List<UUID>> byWarp = new HashMap<>();
        for (AxAccessRow row : rows(sql, AxRowReaders.accessRow())) {
            resolve(players, row.playerId())
                    .ifPresent(uuid -> byWarp.computeIfAbsent(row.warpId(), key -> new ArrayList<>())
                            .add(uuid));
        }
        return byWarp;
    }

    private Map<Integer, List<ImportedPlayerWarp.Ban>> banMap(Map<Integer, AxPlayer> players) {
        Map<Integer, List<ImportedPlayerWarp.Ban>> byWarp = new HashMap<>();
        for (AxAccessRow row : rows(AxTables.SELECT_BLACKLIST, AxRowReaders.accessRow())) {
            resolve(players, row.playerId())
                    .ifPresent(uuid -> byWarp.computeIfAbsent(row.warpId(), key -> new ArrayList<>())
                            .add(new ImportedPlayerWarp.Ban(
                                    uuid,
                                    java.util.Optional.empty(),
                                    java.util.Optional.empty(),
                                    java.util.Optional.empty(),
                                    instant(row.date()))));
        }
        return byWarp;
    }

    private Map<Integer, List<ImportedPlayerWarp.Rating>> ratingMap(Map<Integer, AxPlayer> players) {
        Map<Integer, List<ImportedPlayerWarp.Rating>> byWarp = new HashMap<>();
        for (AxRatingRow row : rows(AxTables.SELECT_RATINGS, AxRowReaders.ratingRow())) {
            resolve(players, row.reviewerId())
                    .ifPresent(uuid -> byWarp.computeIfAbsent(row.warpId(), key -> new ArrayList<>())
                            .add(new ImportedPlayerWarp.Rating(uuid, clampStars(row.stars()), instant(row.date()))));
        }
        return byWarp;
    }

    private Map<Integer, AxVisitRow> visitMap() {
        Map<Integer, AxVisitRow> byWarp = new HashMap<>();
        for (AxVisitRow row : rows(AxTables.SELECT_VISITS, AxRowReaders.visitRow())) {
            byWarp.put(row.warpId(), row);
        }
        return byWarp;
    }

    private List<AxWarpRow> warpRows() {
        return rows(AxTables.SELECT_WARPS, AxRowReaders.warp());
    }

    private static java.util.Optional<UUID> resolve(Map<Integer, AxPlayer> players, int playerId) {
        AxPlayer player = players.get(playerId);
        if (player == null) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(UUID.fromString(player.uuid()));
        } catch (IllegalArgumentException malformed) {
            return java.util.Optional.empty();
        }
    }

    private <R> List<R> rows(String sql, RowMapper<R> reader) {
        try {
            return JdbcSource.query(connection, sql, reader);
        } catch (SQLException unreadable) {
            // A missing table or an unreachable database yields no rows; the run continues with the rest.
            log.warn("AxPlayerWarps query failed, skipping table: {}", String.valueOf(unreadable.getMessage()));
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

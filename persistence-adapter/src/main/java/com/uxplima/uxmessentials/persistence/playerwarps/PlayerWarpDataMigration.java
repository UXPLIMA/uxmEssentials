package com.uxplima.uxmessentials.persistence.playerwarps;

import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerWarpRatings.PLAYER_WARP_RATINGS;
import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerWarpRatingsV1Legacy.PLAYER_WARP_RATINGS_V1_LEGACY;
import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerWarps.PLAYER_WARPS;
import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerWarpsV1Legacy.PLAYER_WARPS_V1_LEGACY;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import com.uxplima.uxmessentials.persistence.jooq.tables.records.PlayerWarpRatingsV1LegacyRecord;
import com.uxplima.uxmessentials.persistence.jooq.tables.records.PlayerWarpsRecord;
import com.uxplima.uxmessentials.persistence.jooq.tables.records.PlayerWarpsV1LegacyRecord;
import com.uxplima.uxmessentials.persistence.runtime.JooqRepository;
import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.playerwarps.application.port.PasswordHasher;
import com.uxplima.uxmessentials.playerwarps.domain.PasswordHash;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.WarpAccess;
import com.uxplima.uxmessentials.playerwarps.domain.WarpStatus;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.jspecify.annotations.Nullable;

/**
 * One-shot copy of the shipped player-warp data from the {@code _v1_legacy} tables V70 preserved into the new
 * surrogate-id schema. V70 rebuilt {@code player_warps} around a durable {@code id} and made {@code name}
 * globally unique, renaming the old tables to {@code player_warps_v1_legacy} / {@code player_warp_ratings_v1_legacy}
 * rather than dropping them so this routine can still read them. It runs once on player-warps enable, off the tick
 * thread, and self-disables afterwards.
 *
 * <p><b>Idempotency</b> rides on the presence of {@code player_warps_v1_legacy}: if the legacy table is gone the
 * data was already copied (or this is a fresh post-migrate boot) and {@link #run()} no-ops. Otherwise the whole
 * copy and the two {@code DROP TABLE} statements happen inside a single {@link #write} transaction, so either every
 * warp, every rehomed rating and both drops commit together, or a failure rolls the lot back and the next enable
 * retries from the untouched legacy tables. A second enable then finds no legacy table and returns immediately.
 *
 * <p><b>What each legacy row becomes.</b> Each legacy name is first coerced into the V70 {@link PlayerWarpName}
 * shape, lowercase {@code [a-z0-9_-]}, {@value PlayerWarpName#MIN_LENGTH}..{@value PlayerWarpName#MAX_LENGTH}
 * characters, because the old schema let a name be any charset up to 64 characters, and a name that no longer
 * constructs would throw on the very next read and break the browse for everyone. Rows are then read in a
 * deterministic {@code created_at, owner, name} order so the first claimant of a now-globally-unique name keeps it
 * and later duplicates are renamed ({@code base} → {@code base2}, …). A {@link PlayerWarpRenameNotice} is emitted
 * whenever the stored name ends up different from the legacy name for <em>any</em> reason, a charset or length fix
 * as well as a collision suffix. {@code access} is derived from the legacy
 * flags, a set password wins ({@code PASSWORD}), then {@code is_locked} maps to owner-only {@code PRIVATE}, then
 * {@code is_public} to {@code PUBLIC}, else {@code PRIVATE}. A legacy plaintext password is run through the
 * {@link PasswordHasher} and stored only as its salted digest; the plaintext is never persisted or logged. The
 * welcome-message blob is dropped (its count is logged as one aggregate line). Ratings are rehomed onto the new
 * surrogate id with their star value clamped to {@code [1,5]}, and every warp that received ratings has its
 * {@code rating_sum}/{@code rating_count}/{@code rating_average} recomputed ({@code rating_score} tracks the
 * average for now; the Bayesian score is a later phase). {@code server_id} is left NULL for the cross-server phase
 * to claim.
 */
public final class PlayerWarpDataMigration extends JooqRepository {

    private static final String LEGACY_WARPS_TABLE = PLAYER_WARPS_V1_LEGACY.getName();

    /** Every character the V70 name charset forbids; each match is folded to a dash during sanitising. */
    private static final Pattern NON_NAME_CHAR = Pattern.compile("[^a-z0-9_-]");

    /** The base handed out when a legacy name sanitises to nothing (an all-whitespace or empty name). */
    private static final String FALLBACK_NAME = "warp";

    private final PasswordHasher hasher;
    private final Consumer<PlayerWarpRenameNotice> onRename;
    private final Logger log;

    public PlayerWarpDataMigration(
            DSLContext dsl, PasswordHasher hasher, Consumer<PlayerWarpRenameNotice> onRename, Logger log) {
        super(dsl);
        this.hasher = Objects.requireNonNull(hasher, "hasher");
        this.onRename = Objects.requireNonNull(onRename, "onRename");
        this.log = Objects.requireNonNull(log, "log");
    }

    /**
     * Build and run the migration over an already-open {@link Persistence} handle, hiding the jOOQ {@code DSLContext}
     * from the bukkit-adapter caller (jOOQ is off the consumer's compile classpath) exactly as the repository
     * factory does. This is the entry point bootstrap schedules off the tick thread on player-warps enable.
     */
    public static void run(Persistence persistence, Consumer<PlayerWarpRenameNotice> onRename, Logger log) {
        Objects.requireNonNull(persistence, "persistence");
        new PlayerWarpDataMigration(persistence.dsl(), new Pbkdf2PasswordHasher(), onRename, log).run();
    }

    /** Copy the legacy data across once, then drop the legacy tables; a no-op if they are already gone. */
    public void run() {
        if (!read(this::legacyPresent)) {
            return;
        }
        // Re-check inside the transaction: on a single-writer backend the write lock serialises this run, so a
        // concurrent enable that already migrated leaves no legacy table and this one falls through to the no-op.
        Outcome outcome = write(dsl -> legacyPresent(dsl) ? migrate(dsl) : Outcome.empty());
        report(outcome);
    }

    private boolean legacyPresent(DSLContext dsl) {
        return dsl.meta().getTables().stream().anyMatch(table -> table.getName().equalsIgnoreCase(LEGACY_WARPS_TABLE));
    }

    private Outcome migrate(DSLContext dsl) {
        List<PlayerWarpsV1LegacyRecord> legacy = readLegacyWarps(dsl);
        NameAllocator allocator = new NameAllocator();
        Map<LegacyKey, WarpRef> byKey = new HashMap<>();
        List<PlayerWarpRenameNotice> renames = new ArrayList<>();
        long nextId = maxId(dsl) + 1;
        int welcomeDropped = 0;
        for (PlayerWarpsV1LegacyRecord row : legacy) {
            String allocated = allocator.allocate(sanitize(row.getName()));
            // Belt and braces: the sanitised, collision-resolved name must satisfy the new value object.
            // Constructing it here turns any future logic slip into a loud failure during this one-shot run
            // rather than a row that silently poisons the global browse on the very next read.
            String name = PlayerWarpName.of(allocated).value();
            long id = nextId++;
            insertWarp(dsl, id, name, row);
            byKey.put(new LegacyKey(row.getOwner(), row.getName()), new WarpRef(id, row.getCreatedAt()));
            recordRename(renames, row, name);
            if (hasText(row.getWelcomeMessage())) {
                welcomeDropped++;
            }
        }
        RatingsResult ratings = rehomeRatings(dsl, byKey);
        dropLegacyTables(dsl);
        return new Outcome(legacy.size(), ratings.inserted(), renames, welcomeDropped, ratings.orphans());
    }

    private static List<PlayerWarpsV1LegacyRecord> readLegacyWarps(DSLContext dsl) {
        return dsl.selectFrom(PLAYER_WARPS_V1_LEGACY)
                .orderBy(
                        PLAYER_WARPS_V1_LEGACY.CREATED_AT.asc(),
                        PLAYER_WARPS_V1_LEGACY.OWNER.asc(),
                        PLAYER_WARPS_V1_LEGACY.NAME.asc())
                .fetch();
    }

    private void insertWarp(DSLContext dsl, long id, String name, PlayerWarpsV1LegacyRecord row) {
        PlayerWarpsRecord record = dsl.newRecord(PLAYER_WARPS);
        record.setId(id).setName(name);
        applyLocation(record, row);
        applyAppearanceAndTiming(record, row);
        applyAccessAndPassword(record, row);
        // Every column left unset takes its schema default: money 0/'default', the rating rollups and visitor
        // counters 0 (ratings are recomputed below), and display_name/category/description/server_id NULL.
        dsl.insertInto(PLAYER_WARPS).set(record).execute();
    }

    private static void applyLocation(PlayerWarpsRecord record, PlayerWarpsV1LegacyRecord row) {
        record.setOwner(row.getOwner())
                .setWorld(row.getWorld())
                .setWorldName(row.getWorldName())
                .setX(row.getX())
                .setY(row.getY())
                .setZ(row.getZ())
                .setYaw(row.getYaw())
                .setPitch(row.getPitch());
    }

    private static void applyAppearanceAndTiming(PlayerWarpsRecord record, PlayerWarpsV1LegacyRecord row) {
        record.setIcon(row.getIconMaterial())
                .setStatus(WarpStatus.ACTIVE.name())
                .setVisitCount(row.getVisitors())
                .setWarmupSeconds(row.getWarmupSeconds())
                .setCooldownSeconds(row.getCooldownSeconds())
                .setDepartureSound(row.getDepartureSound())
                .setArrivalSound(row.getArrivalSound())
                .setDepartureParticle(row.getDepartureParticle())
                .setArrivalParticle(row.getArrivalParticle())
                // Legacy carried no updated_at, so creation time seeds both instants.
                .setCreatedAt(row.getCreatedAt())
                .setUpdatedAt(row.getCreatedAt());
    }

    private void applyAccessAndPassword(PlayerWarpsRecord record, PlayerWarpsV1LegacyRecord row) {
        record.setAccess(deriveAccess(row));
        String plaintext = row.getPassword();
        if (hasText(plaintext)) {
            PasswordHash digest = hasher.hash(plaintext);
            record.setPasswordAlgorithm(digest.algorithm())
                    .setPasswordSalt(digest.salt())
                    .setPasswordHash(digest.hash());
        }
    }

    private static String deriveAccess(PlayerWarpsV1LegacyRecord row) {
        if (hasText(row.getPassword())) {
            return WarpAccess.PASSWORD.name();
        }
        if (isSet(row.getIsLocked())) {
            return WarpAccess.PRIVATE.name();
        }
        if (isSet(row.getIsPublic())) {
            return WarpAccess.PUBLIC.name();
        }
        return WarpAccess.PRIVATE.name();
    }

    private RatingsResult rehomeRatings(DSLContext dsl, Map<LegacyKey, WarpRef> byKey) {
        Map<Long, int[]> summaries = new HashMap<>();
        int inserted = 0;
        int orphans = 0;
        for (PlayerWarpRatingsV1LegacyRecord rating :
                dsl.selectFrom(PLAYER_WARP_RATINGS_V1_LEGACY).fetch()) {
            WarpRef warp = byKey.get(new LegacyKey(rating.getOwnerUuid(), rating.getWarpName()));
            if (warp == null) {
                orphans++;
                continue;
            }
            int stars = clampStars(rating.getRating());
            insertRating(dsl, warp.id(), rating.getPlayerUuid(), stars, warp.createdAt());
            int[] running = summaries.computeIfAbsent(warp.id(), key -> new int[2]);
            running[0] += stars;
            running[1]++;
            inserted++;
        }
        updateRatingSummaries(dsl, summaries);
        return new RatingsResult(inserted, orphans);
    }

    private static void insertRating(DSLContext dsl, long warpId, String player, int stars, long ratedAt) {
        dsl.insertInto(PLAYER_WARP_RATINGS)
                .set(PLAYER_WARP_RATINGS.WARP_ID, warpId)
                .set(PLAYER_WARP_RATINGS.PLAYER_UUID, player)
                .set(PLAYER_WARP_RATINGS.STARS, stars)
                .set(PLAYER_WARP_RATINGS.RATED_AT, ratedAt)
                .execute();
    }

    private static void updateRatingSummaries(DSLContext dsl, Map<Long, int[]> summaries) {
        for (Map.Entry<Long, int[]> entry : summaries.entrySet()) {
            long sum = entry.getValue()[0];
            int count = entry.getValue()[1];
            double average = (double) sum / count;
            dsl.update(PLAYER_WARPS)
                    .set(PLAYER_WARPS.RATING_SUM, sum)
                    .set(PLAYER_WARPS.RATING_COUNT, count)
                    .set(PLAYER_WARPS.RATING_AVERAGE, average)
                    // rating_score mirrors the average until the Bayesian score lands in a later phase.
                    .set(PLAYER_WARPS.RATING_SCORE, average)
                    .where(PLAYER_WARPS.ID.eq(entry.getKey()))
                    .execute();
        }
    }

    private static void dropLegacyTables(DSLContext dsl) {
        dsl.dropTable(PLAYER_WARP_RATINGS_V1_LEGACY).execute();
        dsl.dropTable(PLAYER_WARPS_V1_LEGACY).execute();
    }

    private void report(Outcome outcome) {
        if (outcome.isEmpty()) {
            return;
        }
        log.info("event=playerwarp_migration_welcome_dropped count={}", outcome.welcomeDropped());
        if (outcome.orphanRatings() > 0) {
            log.info("event=playerwarp_migration_ratings_orphaned count={}", outcome.orphanRatings());
        }
        outcome.renames().forEach(onRename);
        log.info(
                "event=playerwarp_migration_complete warps={} ratings={} renamed={}",
                outcome.warps(),
                outcome.ratings(),
                outcome.renames().size());
    }

    private static void recordRename(
            List<PlayerWarpRenameNotice> renames, PlayerWarpsV1LegacyRecord row, String resolved) {
        String legacyName = row.getName();
        // A notice fires whenever the stored name differs from the legacy name for any reason, a charset or length
        // fix as much as a collision suffix. Legacy names were already lower-cased on the old write path, so a warp
        // that needed no change compares equal here and stays silent.
        if (!resolved.equals(legacyName)) {
            renames.add(new PlayerWarpRenameNotice(UUID.fromString(row.getOwner()), legacyName, resolved));
        }
    }

    /**
     * Coerce a legacy name into the shape the V70 {@link PlayerWarpName} enforces so it always constructs: lower-case
     * and strip, fold every character outside {@code [a-z0-9_-]} (spaces, punctuation, unicode) to a dash, fall back
     * to a generic base when nothing survives, cap the result at {@link PlayerWarpName#MAX_LENGTH} and pad it up to
     * {@link PlayerWarpName#MIN_LENGTH}. This is plain string work, not SQL, so the regex fold is safe here.
     */
    private static String sanitize(String legacyName) {
        String folded = NON_NAME_CHAR
                .matcher(legacyName.strip().toLowerCase(Locale.ROOT))
                .replaceAll("-");
        String base = folded.isBlank() ? FALLBACK_NAME : folded;
        String capped = base.length() > PlayerWarpName.MAX_LENGTH ? base.substring(0, PlayerWarpName.MAX_LENGTH) : base;
        return pad(capped);
    }

    private static String pad(String name) {
        if (name.length() >= PlayerWarpName.MIN_LENGTH) {
            return name;
        }
        StringBuilder padded = new StringBuilder(name);
        while (padded.length() < PlayerWarpName.MIN_LENGTH) {
            padded.append('-');
        }
        return padded.toString();
    }

    private static long maxId(DSLContext dsl) {
        Long max = dsl.select(DSL.max(PLAYER_WARPS.ID)).from(PLAYER_WARPS).fetchOne(0, Long.class);
        return max == null ? 0L : max;
    }

    private static int clampStars(@Nullable Double rating) {
        long rounded = Math.round(rating == null ? 0.0 : rating);
        return (int) Math.max(1L, Math.min(5L, rounded));
    }

    private static boolean isSet(@Nullable Integer flag) {
        return flag != null && flag != 0;
    }

    private static boolean hasText(@Nullable String value) {
        return value != null && !value.isBlank();
    }

    /**
     * Allocates a globally-unique lower-cased name per legacy row. The first row to want a name keeps it; a later
     * row whose name is taken gets the first free {@code name2}, {@code name3}, … The base is capped to the
     * {@code player_warps.name} width and, when a numeric suffix would push it over that width, the base is
     * truncated so the whole still fits.
     */
    private static final class NameAllocator {

        private final Set<String> claimed = new HashSet<>();

        String allocate(String legacyName) {
            String base = cap(legacyName.toLowerCase(Locale.ROOT));
            if (claimed.add(base)) {
                return base;
            }
            for (int suffix = 2; ; suffix++) {
                String candidate = withSuffix(base, suffix);
                if (claimed.add(candidate)) {
                    return candidate;
                }
            }
        }

        private static String withSuffix(String base, int suffix) {
            String tail = Integer.toString(suffix);
            int room = PlayerWarpName.MAX_LENGTH - tail.length();
            String head = base.length() > room ? base.substring(0, room) : base;
            return head + tail;
        }

        private static String cap(String name) {
            return name.length() > PlayerWarpName.MAX_LENGTH ? name.substring(0, PlayerWarpName.MAX_LENGTH) : name;
        }
    }

    /** The legacy {@code (owner, name)} pair, the key both a legacy warp and its ratings shared before V70. */
    private record LegacyKey(String owner, String name) {}

    /** The new surrogate id a legacy warp was inserted under and its creation time, for rehoming that warp's ratings. */
    private record WarpRef(long id, long createdAt) {}

    /** How many ratings landed on a new warp id and how many pointed at a warp that no longer exists. */
    private record RatingsResult(int inserted, int orphans) {}

    /** The tally the run reports once the transaction has committed. */
    private record Outcome(
            int warps, int ratings, List<PlayerWarpRenameNotice> renames, int welcomeDropped, int orphanRatings) {

        static Outcome empty() {
            return new Outcome(0, 0, List.of(), 0, 0);
        }

        boolean isEmpty() {
            return warps == 0 && ratings == 0 && renames.isEmpty() && welcomeDropped == 0 && orphanRatings == 0;
        }
    }
}

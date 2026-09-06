package com.uxplima.uxmessentials.migration;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import com.uxplima.uxmessentials.migration.convert.map.ImportedPlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpPasswordStore;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpBanStore;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpFavouriteStore;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpMemberStore;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpRatingStore;
import com.uxplima.uxmessentials.playerwarps.application.port.WarpWhitelistStore;
import com.uxplima.uxmessentials.playerwarps.domain.BanRecord;
import com.uxplima.uxmessentials.playerwarps.domain.DisplayName;
import com.uxplima.uxmessentials.playerwarps.domain.IconSpec;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.RatingSummary;
import com.uxplima.uxmessentials.playerwarps.domain.VisitSummary;
import com.uxplima.uxmessentials.playerwarps.domain.WarpDescription;
import com.uxplima.uxmessentials.playerwarps.domain.WarpEarnings;
import com.uxplima.uxmessentials.playerwarps.domain.WarpMember;
import com.uxplima.uxmessentials.playerwarps.domain.WarpStatus;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.domain.WarpCost;
import org.jspecify.annotations.NullMarked;

/**
 * The shared write path for every player-warp importer (docs/12-migration). Given an {@link ImportedPlayerWarp} it
 * lands the warp on the new surrogate-id {@code player_warps} schema through the player-warps ports, mirroring the P3
 * {@code PlayerWarpDataMigration} that copied the shipped legacy data:
 *
 * <ul>
 *   <li><b>Name.</b> The source name is coerced into the {@link PlayerWarpName} shape (lowercase {@code [a-z0-9_-]},
 *       3..32) and then globally de-collided against the live table. The first claimant keeps the base, a later
 *       warp that wants a taken name gets {@code base2}, {@code base3}, ….
 *   <li><b>Password.</b> A plaintext password is hashed through the {@link PlayerWarpPasswordStore} (PBKDF2 behind
 *       that seam) and only the digest is written; the plaintext never leaves this method and is never logged.
 *   <li><b>The rest.</b> Access, category, description, icon, price, earnings, the rating and visit rollups, and
 *       {@code createdAt} carry across; {@code status} is {@link WarpStatus#ACTIVE} and {@code server_id} is left
 *       local (NULL). The members / whitelist / bans / favourites / per-vote ratings go into their side stores.
 * </ul>
 *
 * <p><b>Idempotent.</b> The collision walk doubles as the existing-import check: if it reaches a name already owned by
 * this same owner, the warp was imported on a previous run and is {@linkplain RecordOutcome#SKIPPED skipped}, so a
 * re-run imports nothing new. <b>Dry-run.</b> {@link #preview} runs the identical resolution read-only and reports the
 * outcome the record would get without writing a row. This class holds no Bukkit type. The source's mapper already
 * resolved the world, so it composes over the domain ports alone.
 */
@NullMarked
public final class PlayerWarpRecordWriter {

    /** Bounds the collision walk so a pathological table can never spin the writer forever. */
    private static final int MAX_COLLISION_ATTEMPTS = 10_000;

    /** Bounds retries when a concurrent writer claims the resolved name between the check and the insert. */
    private static final int MAX_SAVE_RETRIES = 8;

    private static final Pattern NON_NAME_CHAR = Pattern.compile("[^a-z0-9_-]");
    private static final String FALLBACK_NAME = "warp";

    private final PlayerWarpRepository repository;
    private final PlayerWarpPasswordStore passwords;
    private final WarpMemberStore members;
    private final WarpWhitelistStore whitelist;
    private final WarpBanStore bans;
    private final WarpFavouriteStore favourites;
    private final WarpRatingStore ratings;

    public PlayerWarpRecordWriter(
            PlayerWarpRepository repository,
            PlayerWarpPasswordStore passwords,
            WarpMemberStore members,
            WarpWhitelistStore whitelist,
            WarpBanStore bans,
            WarpFavouriteStore favourites,
            WarpRatingStore ratings) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.passwords = Objects.requireNonNull(passwords, "passwords");
        this.members = Objects.requireNonNull(members, "members");
        this.whitelist = Objects.requireNonNull(whitelist, "whitelist");
        this.bans = Objects.requireNonNull(bans, "bans");
        this.favourites = Objects.requireNonNull(favourites, "favourites");
        this.ratings = Objects.requireNonNull(ratings, "ratings");
    }

    /** Land {@code imported} on the schema, or skip it when this owner already holds the same import. */
    public RecordOutcome write(ImportedPlayerWarp imported) {
        Objects.requireNonNull(imported, "imported");
        for (int attempt = 0; attempt < MAX_SAVE_RETRIES; attempt++) {
            Resolution resolution = resolve(imported);
            if (resolution.alreadyImported()) {
                return RecordOutcome.SKIPPED;
            }
            PlayerWarpName name = resolution.name().orElseThrow();
            if (trySave(imported, name)) {
                return RecordOutcome.WRITTEN;
            }
            // A concurrent writer took the resolved name between the collision check and the insert; the name now
            // exists, so re-resolve (which walks past it) and try the next free suffix rather than failing the record.
        }
        throw new IllegalStateException(
                "player-warp import could not settle a free name after retries: " + imported.name());
    }

    /** Report the outcome {@code imported} would get, read-only: the dry-run path writes nothing. */
    public RecordOutcome preview(ImportedPlayerWarp imported) {
        Objects.requireNonNull(imported, "imported");
        return resolve(imported).alreadyImported() ? RecordOutcome.SKIPPED : RecordOutcome.WRITTEN;
    }

    private boolean trySave(ImportedPlayerWarp imported, PlayerWarpName name) {
        try {
            PlayerWarpId id = repository.save(aggregate(imported, name));
            imported.plaintextPassword().ifPresent(plaintext -> passwords.set(id, plaintext));
            writeSideLists(imported, id);
            return true;
        } catch (RuntimeException failure) {
            // Distinguish a lost name race (retryable) from a genuine failure: only a now-taken name is retried, so a
            // real write error still surfaces to the executor's per-record FAILED accounting rather than being masked.
            if (repository.existsByName(name)) {
                return false;
            }
            throw failure;
        }
    }

    private void writeSideLists(ImportedPlayerWarp imported, PlayerWarpId id) {
        for (ImportedPlayerWarp.Member member : imported.members()) {
            members.put(id, new WarpMember(member.player(), member.role(), member.at()));
        }
        for (java.util.UUID player : imported.whitelist()) {
            whitelist.add(id, player);
        }
        for (ImportedPlayerWarp.Ban ban : imported.bans()) {
            bans.ban(id, new BanRecord(ban.player(), ban.until(), ban.reason(), ban.bannedBy(), ban.at()));
        }
        for (java.util.UUID player : imported.favourites()) {
            favourites.add(player, id);
        }
        for (ImportedPlayerWarp.Rating rating : imported.ratings()) {
            ratings.put(id, rating.player(), rating.stars(), rating.at());
        }
    }

    private PlayerWarp aggregate(ImportedPlayerWarp imported, PlayerWarpName name) {
        Instant createdAt = imported.createdAt();
        return new PlayerWarp(
                Optional.empty(),
                new PlayerRef(imported.owner(), imported.ownerName()),
                imported.ownerName(),
                name,
                displayName(imported.displayName()),
                imported.location(),
                Optional.empty(),
                imported.categoryId().filter(id -> !id.isBlank()),
                description(imported.description()),
                icon(imported.icon()),
                imported.access(),
                imported.plaintextPassword().isPresent(),
                WarpStatus.ACTIVE,
                new WarpCost(imported.price(), imported.currencyId()),
                WarpEarnings.of(imported.earnedMoney(), imported.currencyId()),
                ratingSummary(imported),
                new VisitSummary(imported.visits(), imported.uniqueVisitors()),
                0,
                Optional.empty(),
                Optional.empty(),
                com.uxplima.uxmessentials.playerwarps.domain.WarpEffects.none(),
                com.uxplima.uxmessentials.playerwarps.domain.WarpTimingOverrides.none(),
                createdAt,
                createdAt);
    }

    private static RatingSummary ratingSummary(ImportedPlayerWarp imported) {
        int count = imported.ratings().size();
        if (count == 0) {
            return RatingSummary.empty();
        }
        long sum = imported.ratings().stream()
                .mapToLong(ImportedPlayerWarp.Rating::stars)
                .sum();
        double average = (double) sum / count;
        // The Bayesian score lands with the live rate use case; the migration mirrors P3 and seeds it with the average.
        return RatingSummary.of(sum, count, average, average);
    }

    private Resolution resolve(ImportedPlayerWarp imported) {
        String base = sanitize(imported.name());
        for (int suffix = 1; suffix < MAX_COLLISION_ATTEMPTS; suffix++) {
            String candidate = suffix == 1 ? base : withSuffix(base, suffix);
            PlayerWarpName name = PlayerWarpName.of(candidate);
            Optional<PlayerWarp> existing = repository.findByName(name);
            if (existing.isEmpty()) {
                return Resolution.allocate(name);
            }
            if (existing.orElseThrow().owner().uuid().equals(imported.owner())) {
                return Resolution.skip();
            }
        }
        throw new IllegalStateException("player-warp import exhausted name suffixes for base: " + base);
    }

    private static Optional<DisplayName> displayName(Optional<String> raw) {
        return trimmed(raw).map(value -> DisplayName.of(clamp(value, DisplayName.MAX_LENGTH)));
    }

    private static Optional<WarpDescription> description(Optional<String> raw) {
        return trimmed(raw).map(value -> WarpDescription.of(clamp(value, WarpDescription.MAX_LENGTH)));
    }

    private static Optional<IconSpec> icon(Optional<String> raw) {
        return trimmed(raw).map(value -> IconSpec.of(clamp(value, IconSpec.MAX_LENGTH)));
    }

    private static Optional<String> trimmed(Optional<String> raw) {
        return raw.map(String::strip).filter(value -> !value.isBlank());
    }

    /** Clamp to the value object's column width so a long source field never throws its length check. */
    private static String clamp(String value, int maxLength) {
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    /**
     * Coerce a source name into the {@link PlayerWarpName} shape so it always constructs: lower-case and strip, fold
     * every character outside {@code [a-z0-9_-]} to a dash, fall back to a generic base when nothing survives, cap at
     * {@link PlayerWarpName#MAX_LENGTH}, and pad up to {@link PlayerWarpName#MIN_LENGTH}. Plain string work, not SQL.
     */
    private static String sanitize(String rawName) {
        String folded =
                NON_NAME_CHAR.matcher(rawName.strip().toLowerCase(Locale.ROOT)).replaceAll("-");
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

    private static String withSuffix(String base, int suffix) {
        String tail = Integer.toString(suffix);
        int room = PlayerWarpName.MAX_LENGTH - tail.length();
        String head = base.length() > room ? base.substring(0, room) : base;
        return head + tail;
    }

    /** The outcome of resolving a source name to a free, globally-unique name, or to an already-imported skip. */
    private record Resolution(boolean alreadyImported, Optional<PlayerWarpName> name) {

        static Resolution allocate(PlayerWarpName name) {
            return new Resolution(false, Optional.of(name));
        }

        static Resolution skip() {
            return new Resolution(true, Optional.empty());
        }
    }
}

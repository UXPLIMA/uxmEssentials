package com.uxplima.uxmessentials.persistence.playerwarps;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * The paged-browse anti-regression guard: the read model must fetch one bounded page, never materialise the whole
 * {@code player_warps} table (the "verimsiz" complaint the rebuild answers).
 *
 * <p><strong>The bug this freezes out.</strong> The old browse ran {@code SELECT *} over {@code player_warps},
 * rebuilt one {@code PlayerWarp} aggregate per row, and paginated the list in Java, so opening the browse on a
 * hundred-thousand-warp server loaded a hundred thousand aggregates to show forty-five cards. The rewrite
 * ({@link JooqPlayerWarpBrowse}) issues a projected {@code LIMIT}/{@code OFFSET} query plus a {@code COUNT} over the
 * same predicate. This guard makes sure the bounded shape cannot be silently reverted to a full-table read.
 *
 * <p><strong>What it scans.</strong> The tracked source of {@link JooqPlayerWarpBrowse}, located from the repo root
 * (so it reads only a checked-in file and is CI-safe). It asserts the source issues a {@code .limit(...)} and a
 * {@code selectCount(}-bounded total, and that it never {@code selectFrom(PLAYER_WARPS)} (the full-record
 * materialiser), never calls a repository {@code .all(}, and never rehydrates an aggregate through
 * {@code PlayerWarpRows}.
 *
 * <p><strong>Proof of teeth.</strong> {@link #guardTripsWhenTheLimitIsRemoved} and
 * {@link #guardTripsWhenTheWholeTableIsMaterialised} run the same predicates against mutated copies of the source
 * (one with the {@code LIMIT} stripped, others with each forbidden full-table shape spliced in) and assert the
 * guard flags them, so a regression that drops the {@code LIMIT} or reintroduces a full scan fails the build.
 */
class PlayerWarpBrowseIsPagedDriftTest {

    private static final String BROWSE_SOURCE =
            "persistence-adapter/src/main/java/com/uxplima/uxmessentials/persistence/playerwarps/JooqPlayerWarpBrowse.java";

    @Test
    void theBrowseIssuesABoundedLimitedPageQuery() {
        String source = browseSource();
        assertThat(issuesBoundedPage(source))
                .as("the browse must page with a LIMIT and a bounded selectCount total")
                .isTrue();
    }

    @Test
    void theBrowseNeverMaterialisesTheWholeTable() {
        String source = browseSource();
        assertThat(materialisesWholeTable(source))
                .as("the browse must not selectFrom(PLAYER_WARPS), call .all(), or rebuild an aggregate")
                .isFalse();
    }

    @Test
    void guardTripsWhenTheLimitIsRemoved() {
        String withoutLimit = browseSource().replace(".limit(", ".withoutLimit(");
        assertThat(issuesBoundedPage(withoutLimit))
                .as("dropping the LIMIT must fail the paged-browse guard")
                .isFalse();
    }

    @Test
    void guardTripsWhenTheWholeTableIsMaterialised() {
        String source = browseSource();
        assertThat(materialisesWholeTable(source + "\n// selectFrom(PLAYER_WARPS)"))
                .as("an unbounded selectFrom(PLAYER_WARPS) must fail the guard")
                .isTrue();
        assertThat(materialisesWholeTable(source + "\n// repository.all()"))
                .as("a repository.all() must fail the guard")
                .isTrue();
        assertThat(materialisesWholeTable(source + "\n// PlayerWarpRows.toPlayerWarp"))
                .as("rehydrating an aggregate through PlayerWarpRows must fail the guard")
                .isTrue();
    }

    private static boolean issuesBoundedPage(String source) {
        return source.contains(".limit(") && source.contains("selectCount(");
    }

    private static boolean materialisesWholeTable(String source) {
        return source.contains("selectFrom(PLAYER_WARPS)")
                || source.contains(".all(")
                || source.contains("PlayerWarpRows");
    }

    private static String browseSource() {
        Path source = repoRoot().resolve(BROWSE_SOURCE);
        assertThat(Files.isRegularFile(source))
                .as("browse source must exist at %s", source)
                .isTrue();
        try {
            return Files.readString(source, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + source, e);
        }
    }

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            if (Files.exists(dir.resolve("settings.gradle.kts"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("could not locate the repo root (settings.gradle.kts)");
    }
}

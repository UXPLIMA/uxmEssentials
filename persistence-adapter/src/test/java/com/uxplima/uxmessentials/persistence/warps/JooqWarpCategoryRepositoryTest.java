package com.uxplima.uxmessentials.persistence.warps;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.warps.domain.Warp;
import com.uxplima.uxmessentials.warps.domain.WarpCategory;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end coverage of {@link JooqWarpCategoryRepository} against the default embedded SQLite backend with
 * the Flyway migrations applied: the same shape {@link JooqWarpRepositoryTest} uses for warps. It proves the
 * category round-trip (save → find with optional material, lore list, slot, and parent), the id-key upsert (an
 * edit overwrites in place rather than inserting), the delete, the id-ordered list, and, the cross-table
 * invariant the feature relies on. That a warp assigned to a category persists its reference and is found
 * under that category id.
 */
class JooqWarpCategoryRepositoryTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");

    private Persistence persistence;
    private JooqWarpCategoryRepository categories;
    private JooqWarpRepository warps;
    private PlayerRef owner;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(new SqliteConfig(), dataFolder, List.of("db/migration"), new NoopLogger());
        categories = new JooqWarpCategoryRepository(persistence.dsl());
        warps = new JooqWarpRepository(persistence.dsl());
        owner = new PlayerRef(UUID.randomUUID(), "Operator");
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    void savesAndFindsACategoryRoundTrip() {
        categories.save(new WarpCategory(
                "pvp",
                "PvP Arenas",
                Optional.of("DIAMOND_SWORD"),
                List.of("Fight here", "Bring armour"),
                3,
                Optional.empty()));

        Optional<WarpCategory> loaded = categories.find("pvp");

        assertThat(loaded).isPresent();
        WarpCategory reloaded = loaded.orElseThrow();
        assertThat(reloaded.displayName()).isEqualTo("PvP Arenas");
        assertThat(reloaded.displayMaterial()).contains("DIAMOND_SWORD");
        assertThat(reloaded.displayLore()).containsExactly("Fight here", "Bring armour");
        assertThat(reloaded.slot()).isEqualTo(3);
        assertThat(reloaded.parentCategoryId()).isEmpty();
    }

    @Test
    void savePersistsTheOptionalParentForANestedCategory() {
        categories.save(category("worlds"));
        categories.save(new WarpCategory("nether", "Nether", Optional.empty(), List.of(), 0, Optional.of("worlds")));

        WarpCategory reloaded = categories.find("nether").orElseThrow();
        assertThat(reloaded.parentCategoryId()).contains("worlds");
    }

    @Test
    void saveUpsertsOnTheIdKeyRatherThanInserting() {
        categories.save(category("pvp"));
        categories.save(category("pvp").withDisplayName("Renamed")); // same id, an edit

        assertThat(categories.all()).hasSize(1);
        assertThat(categories.find("pvp").orElseThrow().displayName()).isEqualTo("Renamed");
    }

    @Test
    void deleteRemovesTheCategory() {
        categories.save(category("pvp"));
        categories.save(category("shops"));

        categories.delete("pvp");

        assertThat(categories.find("pvp")).isEmpty();
        assertThat(categories.all()).hasSize(1);
    }

    @Test
    void allReturnsEveryCategoryInIdOrder() {
        categories.save(category("zoo"));
        categories.save(category("alpha"));
        categories.save(category("mid"));

        assertThat(categories.all().stream().map(WarpCategory::id)).containsExactly("alpha", "mid", "zoo");
    }

    @Test
    void aWarpAssignedToACategoryPersistsAndIsFoundUnderIt() {
        categories.save(category("pvp"));
        Warp warp = Warp.create(WarpName.of("arena"), Position.of(WORLD, 5, 64, 5), owner, Instant.ofEpochMilli(1_000))
                .withCategoryId(Optional.of("pvp"));
        warps.save(warp);

        Warp reloaded = warps.find(WarpName.of("arena")).orElseThrow();
        assertThat(reloaded.categoryId()).contains("pvp");

        List<Warp> underPvp = warps.all().stream()
                .filter(w -> w.categoryId().equals(Optional.of("pvp")))
                .toList();
        assertThat(underPvp).extracting(w -> w.name().value()).containsExactly("arena");
    }

    @Test
    void aWarpWithNoCategoryStaysUncategorised() {
        warps.save(Warp.create(WarpName.of("spawn"), Position.of(WORLD, 0, 64, 0), owner, Instant.ofEpochMilli(1_000)));

        assertThat(warps.find(WarpName.of("spawn")).orElseThrow().categoryId()).isEmpty();
    }

    private static WarpCategory category(String id) {
        return new WarpCategory(id, id, Optional.empty(), List.of(), 0, Optional.empty());
    }

    /** A config that selects the embedded SQLite backend with every default: no network coordinates. */
    private record SqliteConfig() implements ConfigStore {
        @Override
        public boolean getBoolean(String path, boolean fallback) {
            return fallback;
        }

        @Override
        public String getString(String path, String fallback) {
            return fallback;
        }

        @Override
        public int getInt(String path, int fallback) {
            return fallback;
        }
    }

    private static final class NoopLogger implements Logger {
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

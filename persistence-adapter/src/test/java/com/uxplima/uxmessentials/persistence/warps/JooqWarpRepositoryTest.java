package com.uxplima.uxmessentials.persistence.warps;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
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
import com.uxplima.uxmessentials.warps.domain.WarpCost;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end coverage of {@link JooqWarpRepository} against the default embedded SQLite backend with the
 * Flyway V1 baseline applied. The tested default of the backend-parity matrix (network backends run the
 * same DSL behind Testcontainers, which this environment may not have). It proves the round-trip
 * (save → find), the name-key upsert (a re-anchor overwrites in place rather than inserting), the delete,
 * the {@code exists} check the {@code /setwarp} name-taken path relies on, the creation-order list, and the
 * nullable cost / required-permission columns mapping to a free, ungated warp.
 */
class JooqWarpRepositoryTest {

    private static final WorldRef WORLD = new WorldRef(UUID.randomUUID(), "world");

    private Persistence persistence;
    private JooqWarpRepository repository;
    private PlayerRef owner;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(new SqliteConfig(), dataFolder, List.of("db/migration"), new NoopLogger());
        repository = new JooqWarpRepository(persistence.dsl());
        owner = new PlayerRef(UUID.randomUUID(), "Operator");
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    void resolvesTheOwnerDisplayNameThroughTheProvidedResolver() {
        UUID ownerId = UUID.randomUUID();
        JooqWarpRepository resolving =
                new JooqWarpRepository(persistence.dsl(), uuid -> uuid.equals(ownerId) ? "Alice" : uuid.toString());
        resolving.save(Warp.create(
                WarpName.of("shop"),
                Position.of(WORLD, 1, 2, 3),
                new PlayerRef(ownerId, "ignored-at-write"),
                Instant.ofEpochMilli(1_000)));

        Warp loaded = resolving.find(WarpName.of("shop")).orElseThrow();

        assertThat(loaded.owner().uuid()).isEqualTo(ownerId);
        assertThat(loaded.owner().name()).isEqualTo("Alice");
    }

    @Test
    void defaultRepositoryLeavesTheOwnerNameAsTheUuid() {
        repository.save(warp("shop", 1, 2, 3));

        Warp loaded = repository.find(WarpName.of("shop")).orElseThrow();

        assertThat(loaded.owner().name()).isEqualTo(owner.uuid().toString());
    }

    @Test
    void savesAndFindsAWarpRoundTrip() {
        repository.save(warp("shop", 10, 64, 20));

        Optional<Warp> loaded = repository.find(WarpName.of("shop"));

        assertThat(loaded).isPresent();
        Warp reloaded = loaded.orElseThrow();
        assertThat(reloaded.location().blockX()).isEqualTo(10);
        assertThat(reloaded.location().blockZ()).isEqualTo(20);
        assertThat(reloaded.location().world().name()).isEqualTo("world");
        assertThat(reloaded.cost().isFree()).isTrue();
        assertThat(reloaded.requiredPermission()).isEmpty();
    }

    @Test
    void saveUpsertsOnTheNameKeyRatherThanInserting() {
        repository.save(warp("shop", 0, 0, 0));
        repository.save(warp("shop", 100, 70, 100)); // same name, a re-anchor

        assertThat(repository.all()).hasSize(1);
        Warp reanchored = repository.find(WarpName.of("shop")).orElseThrow();
        assertThat(reanchored.location().blockX()).isEqualTo(100);
    }

    @Test
    void existsReflectsWhetherAWarpIsStored() {
        assertThat(repository.exists(WarpName.of("shop"))).isFalse();

        repository.save(warp("shop", 0, 0, 0));

        assertThat(repository.exists(WarpName.of("shop"))).isTrue();
    }

    @Test
    void deleteRemovesTheRow() {
        repository.save(warp("shop", 0, 0, 0));
        repository.save(warp("pvp", 1, 1, 1));

        repository.delete(WarpName.of("shop"));

        assertThat(repository.exists(WarpName.of("shop"))).isFalse();
        assertThat(repository.all()).hasSize(1);
    }

    @Test
    void allPreservesCreationOrder() {
        repository.save(warpAt("first", 0, Instant.ofEpochMilli(1_000)));
        repository.save(warpAt("second", 1, Instant.ofEpochMilli(2_000)));
        repository.save(warpAt("third", 2, Instant.ofEpochMilli(3_000)));

        assertThat(repository.all().stream().map(w -> w.name().value())).containsExactly("first", "second", "third");
    }

    @Test
    void persistsTheOptionalCostAndRequiredPermission() {
        Warp gated = Warp.create(
                WarpName.of("vip"),
                Position.of(WORLD, 5, 64, 5),
                owner,
                Instant.ofEpochMilli(1_000),
                WarpCost.of(new BigDecimal("250.0000")),
                Optional.of("uxmessentials.warp.vip"));
        repository.save(gated);

        Warp reloaded = repository.find(WarpName.of("vip")).orElseThrow();
        assertThat(reloaded.cost().amount()).isEqualByComparingTo("250.0000");
        assertThat(reloaded.requiredPermission()).contains("uxmessentials.warp.vip");
    }

    private Warp warp(String name, double x, double y, double z) {
        return Warp.create(WarpName.of(name), Position.of(WORLD, x, y, z), owner, Instant.ofEpochMilli(1_000));
    }

    private Warp warpAt(String name, double x, Instant createdAt) {
        return Warp.create(WarpName.of(name), Position.of(WORLD, x, 64, x), owner, createdAt);
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

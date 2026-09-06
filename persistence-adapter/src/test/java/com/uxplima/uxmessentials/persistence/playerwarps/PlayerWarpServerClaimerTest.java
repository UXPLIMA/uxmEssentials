package com.uxplima.uxmessentials.persistence.playerwarps;

import static com.uxplima.uxmessentials.persistence.jooq.tables.PlayerWarps.PLAYER_WARPS;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end coverage of {@link PlayerWarpServerClaimer} against the default embedded SQLite backend with the Flyway
 * V1-V70 migrations applied. It proves the per-backend claim only ever touches NULL-{@code server_id} rows that live
 * in a passed-in local world, so it is idempotent and never overwrites a row already stamped by this or another
 * backend, and that an empty world set runs no unscoped UPDATE.
 */
class PlayerWarpServerClaimerTest {

    private static final WorldRef LOCAL_WORLD = new WorldRef(UUID.randomUUID(), "world");
    private static final WorldRef REMOTE_WORLD = new WorldRef(UUID.randomUUID(), "world_nether");
    private static final PlayerRef OWNER = new PlayerRef(UUID.randomUUID(), "Owner");
    private static final String SERVER_ID = "survival-2";

    private Persistence persistence;
    private JooqPlayerWarpRepository repo;
    private PlayerWarpServerClaimer claimer;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(new SqliteConfig(), dataFolder, List.of("db/migration"), new NoopLogger());
        repo = new JooqPlayerWarpRepository(persistence.dsl());
        claimer = new PlayerWarpServerClaimer(persistence.dsl());
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    void claimsNullRowsInLocalWorldsAndLeavesEverythingElseUntouched() {
        long localId = saveWarp("home", LOCAL_WORLD);
        long remoteId = saveWarp("outpost", REMOTE_WORLD);
        long alreadyStampedId = saveStampedWarp("depot", LOCAL_WORLD, "other-server");

        int claimed = claimer.claim(SERVER_ID, Set.of(LOCAL_WORLD.uid().toString()));

        assertThat(claimed)
                .as("only the NULL row in the passed local world is claimed")
                .isOne();
        assertThat(serverIdOf(localId)).isEqualTo(SERVER_ID);
        assertThat(serverIdOf(remoteId))
                .as("a NULL row in a world not passed stays NULL")
                .isNull();
        assertThat(serverIdOf(alreadyStampedId))
                .as("a row already stamped by another backend is never overwritten")
                .isEqualTo("other-server");
    }

    @Test
    void anEmptyWorldSetClaimsNothing() {
        long localId = saveWarp("home", LOCAL_WORLD);

        int claimed = claimer.claim(SERVER_ID, List.of());

        assertThat(claimed)
                .as("no worlds loaded ⇒ no unscoped UPDATE, nothing claimed")
                .isZero();
        assertThat(serverIdOf(localId)).isNull();
    }

    @Test
    void aSecondClaimIsANoOp() {
        long localId = saveWarp("home", LOCAL_WORLD);
        Set<String> worlds = Set.of(LOCAL_WORLD.uid().toString());

        assertThat(claimer.claim(SERVER_ID, worlds)).isOne();
        assertThat(claimer.claim(SERVER_ID, worlds))
                .as("the row is no longer NULL, so a second pass claims nothing")
                .isZero();
        assertThat(serverIdOf(localId)).isEqualTo(SERVER_ID);
    }

    private long saveWarp(String name, WorldRef world) {
        Position position = new Position(world, 8.0, 64.0, 8.0, 0.0f, 0.0f);
        PlayerWarp warp =
                PlayerWarp.create(OWNER, OWNER.name(), PlayerWarpName.of(name), position, Instant.ofEpochMilli(1_000L));
        return repo.save(warp).value();
    }

    private long saveStampedWarp(String name, WorldRef world, String serverId) {
        long id = saveWarp(name, world);
        // Seed a non-NULL server_id directly (the P4 rich-create path would set it); the pool runs with autoCommit
        // off, so the seed has to go through a committed transaction to be visible to the claim's own transaction.
        persistence
                .dsl()
                .transaction(configuration -> org.jooq
                        .impl
                        .DSL
                        .using(configuration)
                        .update(PLAYER_WARPS)
                        .set(PLAYER_WARPS.SERVER_ID, serverId)
                        .where(PLAYER_WARPS.ID.eq(id))
                        .execute());
        return id;
    }

    private String serverIdOf(long id) {
        return persistence
                .dsl()
                .select(PLAYER_WARPS.SERVER_ID)
                .from(PLAYER_WARPS)
                .where(PLAYER_WARPS.ID.eq(id))
                .fetchOne(PLAYER_WARPS.SERVER_ID);
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

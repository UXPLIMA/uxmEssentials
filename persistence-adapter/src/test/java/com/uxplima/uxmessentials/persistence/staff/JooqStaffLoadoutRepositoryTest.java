package com.uxplima.uxmessentials.persistence.staff;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.staff.domain.LoadoutBlob;
import com.uxplima.uxmessentials.staff.domain.SavedLoadout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end coverage of {@link JooqStaffLoadoutRepository} against the default embedded SQLite backend with the
 * Flyway staff_loadout table applied through V62: the tested default of the backend-parity matrix. It proves the
 * round-trip (save → load): all four opaque item/effect regions survive the base64 TEXT columns byte-for-byte
 * and all five scalars reconstruct equal, so a saved {@link SavedLoadout} equals the loaded one; that a re-save
 * upserts on the {@code (player, server_id)} key rather than inserting a second row (the second save wins); that
 * load on a player with no row is empty; and that delete removes exactly the one row.
 *
 * <p>Since V62 the loadout is keyed per {@code (player, server_id)}: the {@code serverScoping} tests prove a
 * loadout saved under server A for a player is independent of server B. B's save does not overwrite A's row, B's
 * load does not return A's row, and B's delete does not remove A's row, so two backends sharing one DB can no
 * longer clobber each other's captured pre-mode inventory.
 */
class JooqStaffLoadoutRepositoryTest {

    private static final String SERVER_A = "server-a";
    private static final String SERVER_B = "server-b";

    private Persistence persistence;
    private JooqStaffLoadoutRepository repository;
    private UUID owner;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(new SqliteConfig(), dataFolder, List.of("db/migration"), new NoopLogger());
        repository = repositoryFor(SERVER_A);
        owner = UUID.randomUUID();
    }

    private JooqStaffLoadoutRepository repositoryFor(String serverId) {
        return new JooqStaffLoadoutRepository(
                persistence.dsl(), Clock.fixed(Instant.ofEpochMilli(123_456), ZoneOffset.UTC), serverId);
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    void savesAndLoadsRoundTrippingEveryBlobAndScalar() {
        SavedLoadout loadout = new SavedLoadout(
                LoadoutBlob.of(new byte[] {0, 1, 2, 3, (byte) 200, (byte) 255}),
                LoadoutBlob.of(new byte[] {10, 11, 12}),
                LoadoutBlob.of(new byte[] {42}),
                4,
                30,
                0.75f,
                "CREATIVE",
                true,
                true,
                LoadoutBlob.of(new byte[] {7, 8, 9, (byte) 250}),
                true);

        repository.save(owner, loadout);

        SavedLoadout loaded = repository.load(owner).orElseThrow();
        assertThat(loaded).isEqualTo(loadout);
        // The pre-mode vanish and flight-allowance flags survive the SMALLINT column round-trip.
        assertThat(loaded.vanishedBefore()).isTrue();
        assertThat(loaded.allowFlight()).isTrue();
    }

    @Test
    void aNotVanishedBeforeFlagRoundTripsBackToFalse() {
        SavedLoadout loadout = new SavedLoadout(
                LoadoutBlob.of(new byte[] {1}),
                LoadoutBlob.empty(),
                LoadoutBlob.empty(),
                0,
                0,
                0f,
                "SURVIVAL",
                false,
                false,
                LoadoutBlob.empty(),
                false);

        repository.save(owner, loadout);

        SavedLoadout loaded = repository.load(owner).orElseThrow();
        assertThat(loaded.vanishedBefore()).isFalse();
        assertThat(loaded.allowFlight()).isFalse();
        assertThat(loaded).isEqualTo(loadout);
    }

    @Test
    void anEmptyRegionRoundTripsBackToEmpty() {
        SavedLoadout loadout = new SavedLoadout(
                LoadoutBlob.empty(),
                LoadoutBlob.empty(),
                LoadoutBlob.empty(),
                0,
                0,
                0.0f,
                "SURVIVAL",
                false,
                false,
                LoadoutBlob.empty(),
                false);

        repository.save(owner, loadout);

        SavedLoadout loaded = repository.load(owner).orElseThrow();
        assertThat(loaded).isEqualTo(loadout);
        assertThat(loaded.inventory().isEmpty()).isTrue();
        assertThat(loaded.potionEffects().isEmpty()).isTrue();
    }

    @Test
    void saveUpsertsOnThePlayerServerKeyRatherThanInserting() {
        SavedLoadout first = loadoutWith(LoadoutBlob.of(new byte[] {1}), 1, "SURVIVAL", false);
        SavedLoadout second = loadoutWith(LoadoutBlob.of(new byte[] {9, 9}), 7, "SPECTATOR", true);

        repository.save(owner, first);
        repository.save(owner, second); // same owner, same server, a re-save

        SavedLoadout loaded = repository.load(owner).orElseThrow();
        assertThat(loaded).isEqualTo(second);
    }

    @Test
    void loadIsEmptyForAPlayerWithNoSavedLoadout() {
        assertThat(repository.load(UUID.randomUUID())).isEmpty();
    }

    @Test
    void deleteRemovesTheRow() {
        repository.save(owner, loadoutWith(LoadoutBlob.of(new byte[] {1, 2, 3}), 2, "SURVIVAL", false));

        repository.delete(owner);

        assertThat(repository.load(owner)).isEmpty();
    }

    @Test
    void deletingAPlayerWithNoLoadoutIsANoOp() {
        repository.delete(owner); // never entered staff mode, no row to remove

        assertThat(repository.load(owner)).isEmpty();
    }

    @Test
    void loadIsScopedToTheServerSoServerBDoesNotSeeServerAsRow() {
        JooqStaffLoadoutRepository serverA = repositoryFor(SERVER_A);
        JooqStaffLoadoutRepository serverB = repositoryFor(SERVER_B);
        serverA.save(owner, loadoutWith(LoadoutBlob.of(new byte[] {1}), 1, "SURVIVAL", false));

        // The same player, entered on server B, has no loadout there: A's row must not leak across servers.
        assertThat(serverB.load(owner)).isEmpty();
    }

    @Test
    void saveIsScopedToTheServerSoServerBDoesNotOverwriteServerAsRow() {
        JooqStaffLoadoutRepository serverA = repositoryFor(SERVER_A);
        JooqStaffLoadoutRepository serverB = repositoryFor(SERVER_B);
        SavedLoadout onA = loadoutWith(LoadoutBlob.of(new byte[] {1, 1, 1}), 2, "CREATIVE", true);
        SavedLoadout onB = loadoutWith(LoadoutBlob.of(new byte[] {9, 9}), 5, "SPECTATOR", false);

        serverA.save(owner, onA);
        serverB.save(owner, onB); // same player, different backend, must insert a second row, not clobber A

        // Each server round-trips its own captured loadout independently.
        assertThat(serverA.load(owner)).contains(onA);
        assertThat(serverB.load(owner)).contains(onB);
    }

    @Test
    void deleteIsScopedToTheServerSoServerBDoesNotRemoveServerAsRow() {
        JooqStaffLoadoutRepository serverA = repositoryFor(SERVER_A);
        JooqStaffLoadoutRepository serverB = repositoryFor(SERVER_B);
        SavedLoadout onA = loadoutWith(LoadoutBlob.of(new byte[] {4, 2}), 3, "SURVIVAL", false);
        serverA.save(owner, onA);
        serverB.save(owner, loadoutWith(LoadoutBlob.of(new byte[] {7}), 6, "ADVENTURE", true));

        serverB.delete(owner); // exiting staff mode on B must not drop A's still-active loadout

        assertThat(serverA.load(owner)).contains(onA);
        assertThat(serverB.load(owner)).isEmpty();
    }

    private static SavedLoadout loadoutWith(LoadoutBlob inventory, int heldSlot, String gameMode, boolean flying) {
        return new SavedLoadout(
                inventory,
                LoadoutBlob.of(new byte[] {5}),
                LoadoutBlob.of(new byte[] {6}),
                heldSlot,
                15,
                0.5f,
                gameMode,
                flying,
                flying,
                LoadoutBlob.of(new byte[] {7}),
                flying);
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

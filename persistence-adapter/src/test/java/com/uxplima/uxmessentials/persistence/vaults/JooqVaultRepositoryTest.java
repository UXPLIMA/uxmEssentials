package com.uxplima.uxmessentials.persistence.vaults;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.uxplima.uxmessentials.persistence.runtime.Persistence;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vaults.application.VaultSummary;
import com.uxplima.uxmessentials.vaults.domain.Vault;
import com.uxplima.uxmessentials.vaults.domain.VaultContents;
import com.uxplima.uxmessentials.vaults.domain.VaultId;
import com.uxplima.uxmessentials.vaults.domain.VaultSize;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end coverage of {@link JooqVaultRepository} against the default embedded SQLite backend with the
 * Flyway V6 vaults table applied: the tested default of the backend-parity matrix. It proves the round-trip
 * (save → find), that the opaque serialized contents survive the base64 TEXT column byte-for-byte, that an
 * empty vault stores a null {@code contents} cell and round-trips back to empty, that the optional per-vault
 * display name and icon persist (and round-trip to null when unset, updating in place on a re-save), that the
 * presentation {@code summaries} read returns index, name and icon ascending, that a re-save upserts on the
 * {@code (owner, idx)} key rather than inserting, that the per-owner index listing reads ascending, that the
 * count the amount quota relies on reflects the rows, and that a delete removes exactly the one row (freeing the
 * quota slot, idempotent on a missing row, leaving the owner's other vaults intact). It also proves the
 * inactive-vault purge: rows touched strictly before the cutoff go, rows at or after it stay, the affected count
 * is returned, a cutoff before every row removes nothing, and a surviving row stays fully readable.
 */
class JooqVaultRepositoryTest {

    private Persistence persistence;
    private JooqVaultRepository repository;
    private PlayerRef owner;

    @BeforeEach
    void setUp(@TempDir Path dataFolder) {
        persistence = Persistence.open(new SqliteConfig(), dataFolder, List.of("db/migration"), new NoopLogger());
        repository = new JooqVaultRepository(persistence.dsl());
        owner = new PlayerRef(UUID.randomUUID(), "Alice");
    }

    @AfterEach
    void tearDown() {
        persistence.close();
    }

    @Test
    void savesAndLoadsAVaultRoundTrippingTheOpaqueContents() {
        byte[] payload = {0, 1, 2, 3, 4, (byte) 200, (byte) 255};
        repository.save(vault(1, 6, VaultContents.of(payload)));

        Vault loaded = repository.find(VaultId.of(owner, 1)).orElseThrow();

        assertThat(loaded.index()).isEqualTo(1);
        assertThat(loaded.size().rows()).isEqualTo(6);
        assertThat(loaded.contents().payload())
                .get()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.BYTE_ARRAY)
                .containsExactly(payload);
    }

    @Test
    void anEmptyVaultStoresNoBlobAndRoundTripsToEmpty() {
        repository.save(vault(2, 3, VaultContents.empty()));

        Vault loaded = repository.find(VaultId.of(owner, 2)).orElseThrow();

        assertThat(loaded.contents().isEmpty()).isTrue();
    }

    @Test
    void saveUpsertsOnTheOwnerIndexKeyRatherThanInserting() {
        repository.save(vault(1, 3, VaultContents.of(new byte[] {1})));
        repository.save(vault(1, 6, VaultContents.of(new byte[] {9, 9}))); // same (owner, idx), a re-save

        assertThat(repository.count(owner)).isEqualTo(1);
        Vault reloaded = repository.find(VaultId.of(owner, 1)).orElseThrow();
        assertThat(reloaded.size().rows()).isEqualTo(6);
        assertThat(reloaded.contents().payload())
                .get()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.BYTE_ARRAY)
                .containsExactly(9, 9);
    }

    @Test
    void savesAndLoadsThePerVaultDisplayNameAndIcon() {
        repository.save(named(1, "Loot Stash", "ENDER_CHEST"));

        Vault loaded = repository.find(VaultId.of(owner, 1)).orElseThrow();

        assertThat(loaded.displayName()).isEqualTo("Loot Stash");
        assertThat(loaded.iconMaterial()).isEqualTo("ENDER_CHEST");
    }

    @Test
    void aVaultWithoutAppearanceStoresNullColumnsAndRoundTripsToNull() {
        repository.save(vault(1, 6, VaultContents.empty()));

        Vault loaded = repository.find(VaultId.of(owner, 1)).orElseThrow();

        assertThat(loaded.displayName()).isNull();
        assertThat(loaded.iconMaterial()).isNull();
    }

    @Test
    void reSavingUpdatesTheDisplayNameAndIconOnTheUpsertPath() {
        repository.save(named(1, "Old Name", "CHEST"));
        repository.save(named(1, "New Name", "BARREL")); // same (owner, idx), a re-save through onConflict

        assertThat(repository.count(owner)).isEqualTo(1);
        Vault reloaded = repository.find(VaultId.of(owner, 1)).orElseThrow();
        assertThat(reloaded.displayName()).isEqualTo("New Name");
        assertThat(reloaded.iconMaterial()).isEqualTo("BARREL");
    }

    @Test
    void summariesReturnTheIndexNameAndIconAscendingByIndex() {
        repository.save(named(3, "Gear", "DIAMOND_CHESTPLATE"));
        repository.save(named(1, "Loot", "ENDER_CHEST"));
        repository.save(vault(2, 6, VaultContents.empty())); // no appearance set, null name and icon

        assertThat(repository.summaries(owner))
                .containsExactly(
                        new VaultSummary(1, "Loot", "ENDER_CHEST"),
                        new VaultSummary(2, null, null),
                        new VaultSummary(3, "Gear", "DIAMOND_CHESTPLATE"));
    }

    @Test
    void summariesAreEmptyForAnOwnerWithNoVaults() {
        assertThat(repository.summaries(owner)).isEmpty();
    }

    @Test
    void ownedIndicesAreReturnedAscending() {
        repository.save(vault(3, 6, VaultContents.empty()));
        repository.save(vault(1, 6, VaultContents.empty()));
        repository.save(vault(2, 6, VaultContents.empty()));

        assertThat(repository.ownedIndices(owner)).containsExactly(1, 2, 3);
        assertThat(repository.count(owner)).isEqualTo(3);
    }

    @Test
    void findIsEmptyForAnUnopenedVault() {
        assertThat(repository.find(VaultId.of(owner, 9))).isEmpty();
        assertThat(repository.ownedIndices(owner)).isEmpty();
    }

    @Test
    void deleteRemovesTheRowAndFreesTheQuotaSlot() {
        repository.save(vault(1, 6, VaultContents.of(new byte[] {1, 2, 3})));

        repository.delete(VaultId.of(owner, 1));

        assertThat(repository.find(VaultId.of(owner, 1))).isEmpty();
        assertThat(repository.ownedIndices(owner)).isEmpty();
        assertThat(repository.count(owner)).isZero();
    }

    @Test
    void deletingANonExistentVaultIsANoOp() {
        repository.delete(VaultId.of(owner, 7)); // never opened, no row to remove

        assertThat(repository.count(owner)).isZero();
    }

    @Test
    void deletingOneVaultLeavesTheOwnersOtherVaultsIntact() {
        repository.save(vault(1, 6, VaultContents.of(new byte[] {1})));
        repository.save(vault(2, 3, VaultContents.of(new byte[] {2})));

        repository.delete(VaultId.of(owner, 1));

        assertThat(repository.find(VaultId.of(owner, 1))).isEmpty();
        assertThat(repository.find(VaultId.of(owner, 2))).isPresent();
        assertThat(repository.ownedIndices(owner)).containsExactly(2);
        assertThat(repository.count(owner)).isEqualTo(1);
    }

    @Test
    void deleteUntouchedBeforePurgesRowsOlderThanTheCutoffAndKeepsTheRest() {
        repository.save(vaultTouchedAt(1, Instant.ofEpochMilli(1_000)));
        repository.save(vaultTouchedAt(2, Instant.ofEpochMilli(2_000)));
        repository.save(vaultTouchedAt(3, Instant.ofEpochMilli(3_000)));

        int purged = repository.deleteUntouchedBefore(Instant.ofEpochMilli(2_500));

        assertThat(purged).isEqualTo(2);
        assertThat(repository.find(VaultId.of(owner, 1))).isEmpty();
        assertThat(repository.find(VaultId.of(owner, 2))).isEmpty();
        assertThat(repository.find(VaultId.of(owner, 3))).isPresent();
        assertThat(repository.ownedIndices(owner)).containsExactly(3);
    }

    @Test
    void deleteUntouchedBeforeIsStrictlyBeforeSoARowAtTheCutoffSurvives() {
        repository.save(vaultTouchedAt(1, Instant.ofEpochMilli(5_000)));

        int purged = repository.deleteUntouchedBefore(Instant.ofEpochMilli(5_000));

        assertThat(purged).isZero();
        assertThat(repository.find(VaultId.of(owner, 1))).isPresent();
    }

    @Test
    void deleteUntouchedBeforeRemovesNothingWhenTheCutoffPrecedesEveryRow() {
        repository.save(vaultTouchedAt(1, Instant.ofEpochMilli(10_000)));
        repository.save(vaultTouchedAt(2, Instant.ofEpochMilli(20_000)));

        int purged = repository.deleteUntouchedBefore(Instant.ofEpochMilli(1_000));

        assertThat(purged).isZero();
        assertThat(repository.count(owner)).isEqualTo(2);
    }

    @Test
    void deleteUntouchedBeforeKeepsAKeptRowFullyReadable() {
        byte[] payload = {7, 8, 9};
        repository.save(Vault.of(
                VaultId.of(owner, 1),
                new VaultSize(6),
                VaultContents.of(payload),
                null,
                null,
                Instant.ofEpochMilli(9_000)));

        repository.deleteUntouchedBefore(Instant.ofEpochMilli(5_000));

        Vault kept = repository.find(VaultId.of(owner, 1)).orElseThrow();
        assertThat(kept.size().rows()).isEqualTo(6);
        assertThat(kept.contents().payload())
                .get()
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.BYTE_ARRAY)
                .containsExactly(payload);
    }

    private Vault vault(int index, int rows, VaultContents contents) {
        return Vault.of(
                VaultId.of(owner, index), new VaultSize(rows), contents, null, null, Instant.ofEpochMilli(1_000));
    }

    private Vault vaultTouchedAt(int index, Instant lastTouched) {
        return Vault.of(VaultId.of(owner, index), new VaultSize(6), VaultContents.empty(), null, null, lastTouched);
    }

    private Vault named(int index, String displayName, String iconMaterial) {
        return Vault.of(
                VaultId.of(owner, index),
                new VaultSize(6),
                VaultContents.empty(),
                displayName,
                iconMaterial,
                Instant.ofEpochMilli(1_000));
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

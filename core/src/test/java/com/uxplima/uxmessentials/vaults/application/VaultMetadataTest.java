package com.uxplima.uxmessentials.vaults.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.vaults.application.port.VaultRepository;
import com.uxplima.uxmessentials.vaults.domain.Vault;
import com.uxplima.uxmessentials.vaults.domain.VaultError;
import com.uxplima.uxmessentials.vaults.domain.VaultId;
import com.uxplima.uxmessentials.vaults.domain.VaultSize;
import org.junit.jupiter.api.Test;

/**
 * The per-vault presentation use cases and the summary listing. {@code RenameVault} and {@code SetVaultIcon}
 * load an owned vault, apply the pure transition, persist it and notify the owner, and refuse an index with no
 * row as {@link VaultError#VAULT_UNKNOWN}, writing nothing. {@code ListVaults} returns each owned vault's
 * {@link VaultSummary} (index + name + icon) or {@link VaultError#NONE_OWNED} when the owner has none. In-memory
 * fakes capture the repository writes and the rendered notifications.
 */
class VaultMetadataTest {

    private static final PlayerRef OWNER = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-14T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void renameSetsTheNameSavesAndNotifies() {
        FakeRepository repository = new FakeRepository();
        repository.save(Vault.allocate(VaultId.of(OWNER, 1), new VaultSize(3), CLOCK.instant()));
        RecordingSink sink = new RecordingSink();
        RenameVault rename = new RenameVault(repository, notifier(sink));

        Result<Unit, VaultError> result = rename.rename(OWNER, 1, "Loot");

        assertThat(result.isOk()).isTrue();
        assertThat(repository.find(VaultId.of(OWNER, 1)).orElseThrow().displayName())
                .isEqualTo("Loot");
        assertThat(sink.keys).containsExactly(VaultsMessageKey.VAULT_RENAMED);
    }

    @Test
    void renameWithNullClearsTheNameAndNotifiesCleared() {
        FakeRepository repository = new FakeRepository();
        repository.save(Vault.allocate(VaultId.of(OWNER, 1), new VaultSize(3), CLOCK.instant())
                .renamedTo("Loot"));
        RecordingSink sink = new RecordingSink();
        RenameVault rename = new RenameVault(repository, notifier(sink));

        Result<Unit, VaultError> result = rename.rename(OWNER, 1, null);

        assertThat(result.isOk()).isTrue();
        assertThat(repository.find(VaultId.of(OWNER, 1)).orElseThrow().displayName())
                .isNull();
        assertThat(sink.keys).containsExactly(VaultsMessageKey.VAULT_NAME_CLEARED);
    }

    @Test
    void renameOfAnUnknownVaultReturnsVaultUnknownAndWritesNothing() {
        FakeRepository repository = new FakeRepository();
        RecordingSink sink = new RecordingSink();
        RenameVault rename = new RenameVault(repository, notifier(sink));

        Result<Unit, VaultError> result = rename.rename(OWNER, 9, "Loot");

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(VaultError.VAULT_UNKNOWN);
        assertThat(repository.find(VaultId.of(OWNER, 9))).isEmpty();
        assertThat(sink.keys).containsExactly(VaultsMessageKey.VAULT_RENAME_UNKNOWN);
    }

    @Test
    void setIconSetsTheIconSavesAndNotifies() {
        FakeRepository repository = new FakeRepository();
        repository.save(Vault.allocate(VaultId.of(OWNER, 1), new VaultSize(3), CLOCK.instant()));
        RecordingSink sink = new RecordingSink();
        SetVaultIcon setIcon = new SetVaultIcon(repository, notifier(sink));

        Result<Unit, VaultError> result = setIcon.setIcon(OWNER, 1, "ENDER_CHEST");

        assertThat(result.isOk()).isTrue();
        assertThat(repository.find(VaultId.of(OWNER, 1)).orElseThrow().iconMaterial())
                .isEqualTo("ENDER_CHEST");
        assertThat(sink.keys).containsExactly(VaultsMessageKey.VAULT_ICON_SET);
    }

    @Test
    void setIconWithNullClearsTheIconSilently() {
        FakeRepository repository = new FakeRepository();
        repository.save(Vault.allocate(VaultId.of(OWNER, 1), new VaultSize(3), CLOCK.instant())
                .iconSet("CHEST"));
        RecordingSink sink = new RecordingSink();
        SetVaultIcon setIcon = new SetVaultIcon(repository, notifier(sink));

        Result<Unit, VaultError> result = setIcon.setIcon(OWNER, 1, null);

        assertThat(result.isOk()).isTrue();
        assertThat(repository.find(VaultId.of(OWNER, 1)).orElseThrow().iconMaterial())
                .isNull();
        assertThat(sink.keys).isEmpty(); // no icon-cleared key yet; clear persists silently
    }

    @Test
    void setIconOfAnUnknownVaultReturnsVaultUnknownAndWritesNothing() {
        FakeRepository repository = new FakeRepository();
        RecordingSink sink = new RecordingSink();
        SetVaultIcon setIcon = new SetVaultIcon(repository, notifier(sink));

        Result<Unit, VaultError> result = setIcon.setIcon(OWNER, 9, "ENDER_CHEST");

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(VaultError.VAULT_UNKNOWN);
        assertThat(sink.keys).containsExactly(VaultsMessageKey.VAULT_RENAME_UNKNOWN);
    }

    @Test
    void listReturnsTheSummariesWithNameAndIconAscending() {
        FakeRepository repository = new FakeRepository();
        repository.save(Vault.allocate(VaultId.of(OWNER, 2), new VaultSize(3), CLOCK.instant())
                .iconSet("CHEST"));
        repository.save(Vault.allocate(VaultId.of(OWNER, 1), new VaultSize(3), CLOCK.instant())
                .renamedTo("Loot"));

        Result<List<VaultSummary>, VaultError> result = new ListVaults(repository).list(OWNER);

        assertThat(result.isOk()).isTrue();
        assertThat(result.orElseThrow())
                .containsExactly(new VaultSummary(1, "Loot", null), new VaultSummary(2, null, "CHEST"));
    }

    @Test
    void listOfAnOwnerWithNoVaultsReturnsNoneOwned() {
        Result<List<VaultSummary>, VaultError> result = new ListVaults(new FakeRepository()).list(OWNER);

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(VaultError.NONE_OWNED);
    }

    private static VaultNotifier notifier(RecordingSink sink) {
        return new VaultNotifier(passThrough(sink), sink);
    }

    private static Messages passThrough(RecordingSink sink) {
        return (viewer, key, placeholders) -> {
            sink.record(key);
            return key.key();
        };
    }

    /** A {@link MessageSink} that records the {@link MessageKey} of each resolved message it delivers. */
    private static final class RecordingSink implements MessageSink {
        private final List<MessageKey> keys = new ArrayList<>();

        void record(MessageKey key) {
            keys.add(key);
        }

        @Override
        public void deliver(PlayerRef viewer, String renderedText) {
            // The key was recorded at resolve time; delivery is a no-op for the test.
        }
    }

    /** An in-memory {@code VaultRepository} keyed by {@code (owner, index)}. */
    private static final class FakeRepository implements VaultRepository {
        private final Map<VaultId, Vault> rows = new ConcurrentHashMap<>();

        @Override
        public Optional<Vault> find(VaultId id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public List<Integer> ownedIndices(PlayerRef owner) {
            List<Integer> indices = new ArrayList<>();
            rows.keySet().stream()
                    .filter(id -> id.owner().equals(owner.uuid()))
                    .map(VaultId::index)
                    .sorted()
                    .forEach(indices::add);
            return indices;
        }

        @Override
        public List<VaultSummary> summaries(PlayerRef owner) {
            List<VaultSummary> out = new ArrayList<>();
            rows.values().stream()
                    .filter(v -> v.owner().equals(owner.uuid()))
                    .sorted((a, b) -> Integer.compare(a.index(), b.index()))
                    .forEach(v -> out.add(new VaultSummary(v.index(), v.displayName(), v.iconMaterial())));
            return out;
        }

        @Override
        public int count(PlayerRef owner) {
            return (int) rows.keySet().stream()
                    .filter(id -> id.owner().equals(owner.uuid()))
                    .count();
        }

        @Override
        public void save(Vault vault) {
            rows.put(vault.id(), Objects.requireNonNull(vault, "vault"));
        }

        @Override
        public void delete(VaultId id) {
            rows.remove(id);
        }

        @Override
        public int deleteUntouchedBefore(Instant cutoff) {
            // Not exercised here.
            return 0;
        }
    }
}

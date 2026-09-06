package com.uxplima.uxmessentials.vaults.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.vaults.application.port.VaultAudit;
import com.uxplima.uxmessentials.vaults.application.port.VaultEconomy;
import com.uxplima.uxmessentials.vaults.application.port.VaultRepository;
import com.uxplima.uxmessentials.vaults.domain.Vault;
import com.uxplima.uxmessentials.vaults.domain.VaultError;
import com.uxplima.uxmessentials.vaults.domain.VaultId;
import com.uxplima.uxmessentials.vaults.domain.VaultSize;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * {@code DeleteVault} removes a vault row, freeing the owner's quota slot, and, on the own path, pays back
 * the configured refund and notifies the owner. Deleting an index with no row returns
 * {@link VaultError#DELETE_UNKNOWN} and writes nothing. The admin path audits the override and, by design,
 * pays no refund. In-memory fakes capture the repository writes, the refund deposit, the audit line and the
 * rendered notification.
 */
class DeleteVaultTest {

    private static final PlayerRef OWNER = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final PlayerRef STAFF = new PlayerRef(UUID.randomUUID(), "Mod");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-14T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void deletesAnExistingVaultRefundsAndNotifies() {
        FakeRepository repository = new FakeRepository();
        repository.save(Vault.allocate(VaultId.of(OWNER, 2), new VaultSize(3), CLOCK.instant()));
        CapturingEconomy economy = new CapturingEconomy();
        FakeAudit audit = new FakeAudit();
        RecordingSink sink = new RecordingSink();
        DeleteVault delete = new DeleteVault(
                repository,
                new VaultCharge(denyAll(), Optional.of(economy), VaultChargeSettings.of(0, 0, 25)),
                audit,
                notifier(sink));

        Result<Unit, VaultError> result = delete.delete(OWNER, 2);

        assertThat(result.isOk()).isTrue();
        assertThat(repository.find(VaultId.of(OWNER, 2))).isEmpty(); // slot freed
        assertThat(economy.deposits).containsExactly(BigDecimal.valueOf(25.0)); // refunded
        assertThat(sink.keys).containsExactly(VaultsMessageKey.VAULT_DELETED);
        assertThat(audit.deletions).isEmpty(); // own delete is not audited
    }

    @Test
    void deletingAnUnknownIndexReturnsDeleteUnknownAndWritesNothing() {
        FakeRepository repository = new FakeRepository();
        CapturingEconomy economy = new CapturingEconomy();
        RecordingSink sink = new RecordingSink();
        DeleteVault delete = new DeleteVault(
                repository,
                new VaultCharge(denyAll(), Optional.of(economy), VaultChargeSettings.of(0, 0, 25)),
                new FakeAudit(),
                notifier(sink));

        Result<Unit, VaultError> result = delete.delete(OWNER, 7);

        assertThat(result.isErr()).isTrue();
        assertThat(result.errorOrThrow()).isEqualTo(VaultError.DELETE_UNKNOWN);
        assertThat(economy.deposits).isEmpty(); // no refund for a non-existent vault
        assertThat(sink.keys).containsExactly(VaultsMessageKey.VAULT_DELETE_UNKNOWN);
    }

    @Test
    void adminDeleteRemovesTheVaultAuditsAndPaysNoRefund() {
        FakeRepository repository = new FakeRepository();
        repository.save(Vault.allocate(VaultId.of(OWNER, 1), new VaultSize(3), CLOCK.instant()));
        CapturingEconomy economy = new CapturingEconomy();
        FakeAudit audit = new FakeAudit();
        RecordingSink sink = new RecordingSink();
        DeleteVault delete = new DeleteVault(
                repository,
                new VaultCharge(denyAll(), Optional.of(economy), VaultChargeSettings.of(0, 0, 25)),
                audit,
                notifier(sink));

        Result<Unit, VaultError> result = delete.deleteOther(STAFF, OWNER, 1);

        assertThat(result.isOk()).isTrue();
        assertThat(repository.find(VaultId.of(OWNER, 1))).isEmpty();
        assertThat(audit.deletions).containsExactly(new Deletion(STAFF, OWNER, OWNER.uuid(), 1));
        assertThat(economy.deposits).isEmpty(); // admin delete pays no refund (actor is not the owner)
        assertThat(sink.keys).containsExactly(VaultsMessageKey.VAULT_DELETED); // staff is told
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

    private static Permissions denyAll() {
        return new Permissions() {
            @Override
            public boolean has(PlayerRef who, String node) {
                return false;
            }

            @Override
            public QuotaResult resolveQuota(
                    PlayerRef who, QuotaFamily family, @Nullable WorldRef world, long configDefault) {
                return QuotaResult.limited(configDefault);
            }
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

    private record Deletion(PlayerRef actor, PlayerRef owner, UUID ownerUuid, int index) {}

    private static final class FakeAudit implements VaultAudit {
        private final List<Deletion> deletions = new ArrayList<>();

        @Override
        public void adminOpened(PlayerRef actor, PlayerRef owner, UUID ownerUuid, int index) {
            // Not exercised here.
        }

        @Override
        public void adminDeleted(PlayerRef actor, PlayerRef owner, UUID ownerUuid, int index) {
            deletions.add(new Deletion(actor, owner, ownerUuid, index));
        }

        @Override
        public void purged(int count) {
            // Not exercised here.
        }
    }

    private static final class CapturingEconomy implements VaultEconomy {
        private final List<BigDecimal> deposits = new ArrayList<>();

        @Override
        public boolean canAfford(PlayerRef who, BigDecimal amount) {
            return true;
        }

        @Override
        public boolean withdraw(PlayerRef who, BigDecimal amount) {
            // Not exercised here.
            return true;
        }

        @Override
        public boolean deposit(PlayerRef who, BigDecimal amount) {
            deposits.add(amount);
            return true;
        }
    }

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
            rows.put(vault.id(), vault);
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

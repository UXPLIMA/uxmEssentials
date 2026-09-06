package com.uxplima.uxmessentials.vaults.application;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vaults.application.port.VaultAudit;
import com.uxplima.uxmessentials.vaults.application.port.VaultRepository;
import com.uxplima.uxmessentials.vaults.domain.Vault;
import com.uxplima.uxmessentials.vaults.domain.VaultId;
import com.uxplima.uxmessentials.vaults.domain.VaultSize;

/**
 * {@code /vault <player> [n]}: the staff override that opens another player's vault for inspection. Unlike the
 * owner path, the amount quota is not enforced, staff may open any index, but the open is always
 * audit-logged through {@link VaultAudit} so the override is replayable from the audit channel (the
 * {@code event=vault_admin_open} line). An absent vault is opened as an empty one at the owner's resolved
 * size, so staff can audit a player who has not used that index yet without a missing-row error.
 *
 * <p>The use case loads (or synthesises) the {@link Vault}, emits the audit line, and hands the vault back; the
 * adapter opens the GUI and raises the {@code VaultOpened} event for the live staff viewer.
 */
public final class OpenAdminVault {

    private final VaultRepository repository;
    private final VaultSizeQuota sizeQuota;
    private final VaultAudit audit;
    private final Clock clock;

    public OpenAdminVault(VaultRepository repository, VaultSizeQuota sizeQuota, VaultAudit audit, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.sizeQuota = Objects.requireNonNull(sizeQuota, "sizeQuota");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Open {@code owner}'s vault at {@code index} on behalf of {@code actor}, audit-logging the override. The
     * audited player's stored vault is returned when present; otherwise an empty vault at their resolved size.
     */
    public Vault open(PlayerRef actor, PlayerRef owner, int index) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(owner, "owner");
        VaultId id = VaultId.of(owner, index);
        VaultSize size = sizeQuota.resolve(owner);
        Optional<Vault> stored = repository.find(id);
        Vault vault = stored.map(existing -> existing.openedAt(size))
                .orElseGet(() -> Vault.allocate(id, size, clock.instant()));
        audit.adminOpened(actor, owner, owner.uuid(), index);
        return vault;
    }
}

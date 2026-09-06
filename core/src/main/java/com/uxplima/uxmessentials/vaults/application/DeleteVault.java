package com.uxplima.uxmessentials.vaults.application;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.vaults.application.port.VaultAudit;
import com.uxplima.uxmessentials.vaults.application.port.VaultRepository;
import com.uxplima.uxmessentials.vaults.domain.VaultError;
import com.uxplima.uxmessentials.vaults.domain.VaultId;

/**
 * {@code /vault delete <n>} (own) and {@code /vault delete <player> <n>} (admin): remove a vault row, freeing
 * the owner's amount-quota slot, and, for an owner deleting their own vault, pay back the configured refund.
 * Mirrors the create path's order in reverse: the vault must exist before it can be removed, so an index with
 * no row returns {@link VaultError#DELETE_UNKNOWN} and writes nothing.
 *
 * <p>The own path refunds through {@link VaultCharge#refund(PlayerRef)} (a no-op when no refund is configured,
 * the player holds the bypass node, or economy is unwired). The admin path is audited through
 * {@link VaultAudit#adminDeleted} and intentionally pays <em>no</em> refund: the actor is not the owner, and
 * the owner is typically offline, so a destructive staff override is recorded rather than reimbursed.
 *
 * <p>This use case is pure: it touches the repository, the charge gate and the audit/notifier ports only,
 * leaving cache invalidation and the cross-server {@code VaultChanged} emit to the repository adapter.
 */
public final class DeleteVault {

    private final VaultRepository repository;
    private final VaultCharge charge;
    private final VaultAudit audit;
    private final VaultNotifier notifier;

    public DeleteVault(VaultRepository repository, VaultCharge charge, VaultAudit audit, VaultNotifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.charge = Objects.requireNonNull(charge, "charge");
        this.audit = Objects.requireNonNull(audit, "audit");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Delete {@code owner}'s own vault at one-based {@code index}, refunding when configured and notifying them. */
    public Result<Unit, VaultError> delete(PlayerRef owner, int index) {
        Objects.requireNonNull(owner, "owner");
        VaultId id = VaultId.of(owner, index);
        if (repository.find(id).isEmpty()) {
            notifier.deleteUnknown(owner, index);
            return Result.err(VaultError.DELETE_UNKNOWN);
        }
        repository.delete(id);
        // A capped/clamped refund returns false; the vault is already gone, so the delete still succeeds. The
        // boolean is the dropped-refund seam (VaultCharge holds no logger); there is nothing more this pure
        // use case can do, and the owner is told the vault was deleted either way.
        charge.refund(owner);
        notifier.deleted(owner, index);
        return Result.ok();
    }

    /**
     * Delete {@code owner}'s vault at one-based {@code index} on behalf of staff {@code actor}, audit-logging
     * the override and notifying the actor. No refund is paid: the actor is not the owner (see the class doc).
     */
    public Result<Unit, VaultError> deleteOther(PlayerRef actor, PlayerRef owner, int index) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(owner, "owner");
        VaultId id = VaultId.of(owner, index);
        if (repository.find(id).isEmpty()) {
            notifier.deleteUnknown(actor, index);
            return Result.err(VaultError.DELETE_UNKNOWN);
        }
        repository.delete(id);
        audit.adminDeleted(actor, owner, owner.uuid(), index);
        notifier.deleted(actor, index);
        return Result.ok();
    }
}

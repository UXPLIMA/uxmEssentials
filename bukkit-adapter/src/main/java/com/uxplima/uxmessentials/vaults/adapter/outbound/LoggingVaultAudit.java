package com.uxplima.uxmessentials.vaults.adapter.outbound;

import java.util.Objects;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vaults.application.port.VaultAudit;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link VaultAudit} implementation that writes the vaults audit trail to the dedicated
 * {@code com.uxplima.uxmessentials.audit} channel as structured {@code event=<name> actor=<staff> key=value}
 * lines (docs/09-deployment.md §Audit logging), mirroring the moderation and itemworld audit. Only the staff
 * overrides are audited (a player opening or deleting their own vault is not) so one
 * {@code event=vault_admin_open} line is emitted per inspect override ({@code /vault <player> [n]}) and one
 * {@code event=vault_admin_delete} line per delete override ({@code /vault delete <player> <n>}), with the
 * actor and owner logged by UUID for stable identity and the owner name carried for the human-readable render.
 *
 * <p>Audit lines are operator-facing, so they go through a {@link Logger} bound to the audit channel, never
 * the player-facing {@code MessageKey} catalog.
 */
@NullMarked
public final class LoggingVaultAudit implements VaultAudit {

    private final Logger audit;

    public LoggingVaultAudit(Logger audit) {
        this.audit = Objects.requireNonNull(audit, "audit");
    }

    @Override
    public void adminOpened(PlayerRef actor, PlayerRef owner, UUID ownerUuid, int index) {
        audit.info(
                "event=vault_admin_open actor={} owner={} owner_name={} idx={}",
                actor.uuid(),
                ownerUuid,
                owner.name(),
                index);
    }

    @Override
    public void adminDeleted(PlayerRef actor, PlayerRef owner, UUID ownerUuid, int index) {
        audit.info(
                "event=vault_admin_delete actor={} owner={} owner_name={} idx={}",
                actor.uuid(),
                ownerUuid,
                owner.name(),
                index);
    }

    @Override
    public void purged(int count) {
        audit.info("event=vault_purge actor=system count={}", count);
    }
}

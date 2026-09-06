package com.uxplima.uxmessentials.vaults.application;

import java.time.Clock;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.vaults.application.port.VaultRepository;
import com.uxplima.uxmessentials.vaults.domain.Vault;
import com.uxplima.uxmessentials.vaults.domain.VaultAmount;
import com.uxplima.uxmessentials.vaults.domain.VaultError;
import com.uxplima.uxmessentials.vaults.domain.VaultId;
import com.uxplima.uxmessentials.vaults.domain.VaultSize;

/**
 * {@code /vault <n>}: resolve and hand back the vault a player wants to open. The amount quota
 * ({@code uxmessentials.vault.amount.<n>}) gates which indices the owner may reach; the size quota
 * ({@code uxmessentials.vault.size.<rows>}) sets how tall a freshly allocated or re-opened vault is. An
 * already-owned vault re-opens regardless of a since-shrunk amount quota. A player never loses access to
 * items they already stored, and adopts the owner's current size on open. A not-yet-allocated vault within
 * the amount quota is created empty.
 *
 * <p>The economy charge is gated through {@link VaultCharge} in the same quota→charge→allocate order
 * {@code CreateHomeAtSlot} uses (the free quota check runs before the paid gate). A first allocation pays the
 * combined create + per-open fee in a single atomic withdrawal, so a new vault is never half-charged;
 * re-opening an existing vault pays only the per-open fee. Each fee is free unless its config cost is
 * positive, so with economy disabled or unwired every open proceeds for free. A fee the player cannot afford
 * fails the open with {@link VaultError#CANNOT_AFFORD} and writes nothing.
 *
 * <p>This use case is pure: it loads or allocates the {@link Vault} and returns it (or a {@link VaultError}),
 * leaving the GUI open and the {@code VaultOpened} event to the adapter, which knows the live player. The
 * allocation is persisted up front so the vault exists as a row the close-save can upsert against.
 */
public final class OpenVault {

    private final VaultRepository repository;
    private final VaultAmountQuota amountQuota;
    private final VaultSizeQuota sizeQuota;
    private final VaultCharge charge;
    private final Clock clock;

    public OpenVault(
            VaultRepository repository,
            VaultAmountQuota amountQuota,
            VaultSizeQuota sizeQuota,
            VaultCharge charge,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.amountQuota = Objects.requireNonNull(amountQuota, "amountQuota");
        this.sizeQuota = Objects.requireNonNull(sizeQuota, "sizeQuota");
        this.charge = Objects.requireNonNull(charge, "charge");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Resolve {@code owner}'s vault at one-based {@code index}, allocating it within quota when absent. */
    public Result<Vault, VaultError> open(PlayerRef owner, int index) {
        Objects.requireNonNull(owner, "owner");
        VaultId id = VaultId.of(owner, index);
        VaultSize size = sizeQuota.resolve(owner);
        return repository
                .find(id)
                .map(existing -> reopen(owner, existing, size))
                .orElseGet(() -> allocate(owner, id, index, size));
    }

    private Result<Vault, VaultError> reopen(PlayerRef owner, Vault existing, VaultSize size) {
        Result<Unit, VaultError> charged = charge.chargeOpen(owner);
        if (charged.isErr()) {
            return Result.err(charged.errorOrThrow());
        }
        Vault opened = existing.openedAt(size);
        if (opened != existing) {
            // The size quota changed since this vault was last saved; persist the new height so the row matches
            // the GUI the player now sees and the close-save upserts against the current size.
            repository.save(opened);
        }
        return Result.ok(opened);
    }

    private Result<Vault, VaultError> allocate(PlayerRef owner, VaultId id, int index, VaultSize size) {
        VaultAmount amount = amountQuota.resolve(owner);
        if (!amount.allows(index)) {
            return Result.err(VaultError.AMOUNT_EXCEEDED);
        }
        // Quota passed (the free check); charge the combined create + per-open fee in one atomic withdrawal
        // before allocating, so a player who cannot afford the vault never leaves a phantom row behind and the
        // create fee is never taken on its own when the open fee cannot be paid.
        Result<Unit, VaultError> charged = charge.chargeAllocate(owner);
        if (charged.isErr()) {
            return Result.err(charged.errorOrThrow());
        }
        Vault allocated = Vault.allocate(id, size, clock.instant());
        repository.save(allocated);
        return Result.ok(allocated);
    }
}

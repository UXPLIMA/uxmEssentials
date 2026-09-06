package com.uxplima.uxmessentials.vaults.application;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.vaults.application.port.VaultRepository;
import com.uxplima.uxmessentials.vaults.domain.Vault;
import com.uxplima.uxmessentials.vaults.domain.VaultError;
import com.uxplima.uxmessentials.vaults.domain.VaultId;
import org.jspecify.annotations.Nullable;

/**
 * {@code /vault rename <n> [name]}: set or clear the player-chosen display name of a vault they already own.
 * Mirrors {@link DeleteVault}'s order. The vault must exist before its presentation can change, so an index
 * with no row returns {@link VaultError#VAULT_UNKNOWN} and writes nothing. A {@code null} (or blank, after the
 * adapter trims) name clears the display name; a non-null name applies it through {@link Vault#renamedTo}, which
 * enforces the domain length guard. The size, contents, icon and last-touched are preserved.
 *
 * <p>This use case is pure: it touches the repository and the notifier only, leaving cache invalidation and the
 * cross-server {@code VaultChanged} emit to the repository adapter (the {@code save} write-through carries the
 * new name across servers for free).
 */
public final class RenameVault {

    private final VaultRepository repository;
    private final VaultNotifier notifier;

    public RenameVault(VaultRepository repository, VaultNotifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Rename {@code owner}'s vault at one-based {@code index} to {@code name} ({@code null} clears it). */
    public Result<Unit, VaultError> rename(PlayerRef owner, int index, @Nullable String name) {
        Objects.requireNonNull(owner, "owner");
        VaultId id = VaultId.of(owner, index);
        Vault vault = repository.find(id).orElse(null);
        if (vault == null) {
            notifier.renameUnknown(owner, index);
            return Result.err(VaultError.VAULT_UNKNOWN);
        }
        repository.save(vault.renamedTo(name));
        if (name == null) {
            notifier.nameCleared(owner, index);
        } else {
            notifier.renamed(owner, index, name);
        }
        return Result.ok();
    }
}

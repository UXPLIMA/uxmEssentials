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
 * {@code /vault icon <n> [material]}: set or clear the player-chosen icon of a vault they already own. Mirrors
 * {@link RenameVault}. The vault must exist before its presentation can change, so an index with no row returns
 * {@link VaultError#VAULT_UNKNOWN} and writes nothing. The icon is a material <em>name</em> the adapter has
 * already validated against the real {@code Material} registry before calling in; the domain stays Bukkit-free.
 * A {@code null} material clears the icon; a non-null one applies it through {@link Vault#iconSet}. The size,
 * contents, display name and last-touched are preserved.
 *
 * <p>This use case is pure: it touches the repository and the notifier only, leaving cache invalidation and the
 * cross-server {@code VaultChanged} emit to the repository adapter (the {@code save} write-through carries the
 * new icon across servers for free).
 */
public final class SetVaultIcon {

    private final VaultRepository repository;
    private final VaultNotifier notifier;

    public SetVaultIcon(VaultRepository repository, VaultNotifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Set {@code owner}'s vault at one-based {@code index} to use {@code materialName} ({@code null} clears it). */
    public Result<Unit, VaultError> setIcon(PlayerRef owner, int index, @Nullable String materialName) {
        Objects.requireNonNull(owner, "owner");
        VaultId id = VaultId.of(owner, index);
        Vault vault = repository.find(id).orElse(null);
        if (vault == null) {
            notifier.renameUnknown(owner, index);
            return Result.err(VaultError.VAULT_UNKNOWN);
        }
        repository.save(vault.iconSet(materialName));
        // There is no dedicated icon-cleared key in the catalog yet (D3); a clear persists silently and the GUI
        // simply falls back to the default icon. A set confirms with the material name.
        if (materialName != null) {
            notifier.iconSet(owner, index, materialName);
        }
        return Result.ok();
    }
}

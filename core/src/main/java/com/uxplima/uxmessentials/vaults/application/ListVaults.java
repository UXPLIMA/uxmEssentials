package com.uxplima.uxmessentials.vaults.application;

import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.vaults.application.port.VaultRepository;
import com.uxplima.uxmessentials.vaults.domain.VaultError;

/**
 * {@code /vault} with no index when the owner has several vaults: list the vaults they own so they can pick
 * one. The default-vault shortcut ({@code /vault} opening vault 1 when that is all they have) is the adapter's
 * decision; this use case answers the "which vaults do I own?" question the listing renders, each as a
 * {@link VaultSummary} carrying its index, display name and icon, and reports {@link VaultError#NONE_OWNED}
 * when the player has opened none yet.
 */
public final class ListVaults {

    private final VaultRepository repository;

    public ListVaults(VaultRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    /** The ascending summaries of {@code owner}'s vaults, or {@link VaultError#NONE_OWNED} when they own none. */
    public Result<List<VaultSummary>, VaultError> list(PlayerRef owner) {
        Objects.requireNonNull(owner, "owner");
        List<VaultSummary> summaries = repository.summaries(owner);
        return summaries.isEmpty() ? Result.err(VaultError.NONE_OWNED) : Result.ok(summaries);
    }
}

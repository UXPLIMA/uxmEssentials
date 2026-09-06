package com.uxplima.uxmessentials.vaults.application;

import java.time.Clock;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.application.port.DomainEventPublisher;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vaults.application.port.VaultRepository;
import com.uxplima.uxmessentials.vaults.domain.Vault;
import com.uxplima.uxmessentials.vaults.domain.VaultContents;

/**
 * The write-through that runs when an open vault GUI closes ({@code InventoryCloseEvent}). The adapter resolves
 * the owning {@link Vault} from the inventory holder, serialises the live inventory into opaque
 * {@link VaultContents}, and hands both here; this use case applies the {@link Vault#withContents} transition,
 * persists the result through the repository, and publishes the {@code VaultContentsChanged} event. A vault is
 * the authority for its items, not the GUI, so the close is what makes the stored state durable, and a
 * region-safe save is the adapter's job to schedule before calling in.
 */
public final class SaveVault {

    private final VaultRepository repository;
    private final DomainEventPublisher events;
    private final Clock clock;

    public SaveVault(VaultRepository repository, DomainEventPublisher events, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Write {@code newContents} into {@code vault} as {@code owner}'s save and publish the change. */
    public void save(PlayerRef owner, Vault vault, VaultContents newContents) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(vault, "vault");
        Objects.requireNonNull(newContents, "newContents");
        Vault.Change change = vault.withContents(owner, newContents, clock.instant());
        repository.save(change.result());
        events.publish(change.event());
    }
}

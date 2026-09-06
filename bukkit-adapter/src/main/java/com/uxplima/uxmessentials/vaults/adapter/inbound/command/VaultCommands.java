package com.uxplima.uxmessentials.vaults.adapter.inbound.command;

import java.util.List;

import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.vaults.adapter.VaultServices;
import org.jspecify.annotations.NullMarked;

/**
 * Builds the vaults context's Brigadier command surface (docs/10-feature-modules.md §15.11), the single
 * {@code /vault} command: as {@link CommandRegistration}s over the constructed {@link VaultServices}. Kept in
 * one collector for symmetry with the other contexts so the plugin's {@code LifecycleEvents.COMMANDS} handler
 * registers each in a uniform loop, even though the surface is a single command.
 */
@NullMarked
public final class VaultCommands {

    private VaultCommands() {}

    /** Every vaults command, in surface order. */
    public static List<CommandRegistration> all(VaultServices services) {
        return List.of(new VaultCommand(services));
    }
}

package com.uxplima.uxmessentials.vaults.application;

import java.util.List;
import java.util.function.Function;

import com.uxplima.uxmessentials.shared.application.module.BrigadierCommand;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;

/**
 * The vaults context's command surface (docs/10-feature-modules.md §15.11) as a platform-neutral
 * {@link CommandSpec}: the single {@code /vault} literal, gated by {@code uxmessentials.vault.use}, that
 * serves all three forms, {@code /vault} (open the default vault or list when several exist), {@code /vault
 * <n>} (open the Nth), and {@code /vault <player> [n]} (the audit-logged staff override, gated inside the
 * handler by {@code uxmessentials.vault.others}). The vaults inbound adapter realises the Brigadier node from
 * the started module's context on the next {@code COMMANDS} fire. Collected here so {@code VaultsModule} stays
 * small and the command/permission pairing is one greppable row the permissions guard checks against
 * {@code paper-plugin.yml}.
 */
final class VaultCommandSurface {

    private VaultCommandSurface() {}

    static List<CommandSpec> all() {
        return List.of(spec("vault", "uxmessentials.vault.use", new VaultCommand("vault", "Open one of your vaults")));
    }

    private static CommandSpec spec(String literal, String permission, BrigadierCommand command) {
        Function<ModuleContext, BrigadierCommand> factory = ctx -> command;
        return new CommandSpec(literal, permission, factory);
    }

    /** The kernel-side description of the vault command, literal and help text, no Brigadier type. */
    private record VaultCommand(String literal, String description) implements BrigadierCommand {}
}

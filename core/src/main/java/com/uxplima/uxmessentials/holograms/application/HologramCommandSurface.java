package com.uxplima.uxmessentials.holograms.application;

import java.util.List;
import java.util.function.Function;

import com.uxplima.uxmessentials.shared.application.module.BrigadierCommand;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;

/**
 * The holograms context's command surface as a platform-neutral {@link CommandSpec}: the single
 * {@code /hologram} literal, gated by {@code uxmessentials.hologram.use}, that serves every form through its
 * subcommands, {@code create}, {@code delete}, {@code list}, {@code addline}, {@code setline},
 * {@code removeline}, {@code movehere}, and the {@code item} / {@code block} type setters (plus the appearance
 * and visibility styling subcommands). The holograms inbound adapter realises the Brigadier node from
 * the started module's context on the next {@code COMMANDS} fire. Collected here so {@code HologramsModule}
 * stays small and the command/permission pairing is one greppable row the permissions guard checks against
 * {@code paper-plugin.yml}.
 */
final class HologramCommandSurface {

    private HologramCommandSurface() {}

    static List<CommandSpec> all() {
        return List.of(spec(
                "hologram",
                "uxmessentials.hologram.use",
                new HologramCommand("hologram", "Create and manage Display-entity holograms")));
    }

    private static CommandSpec spec(String literal, String permission, BrigadierCommand command) {
        Function<ModuleContext, BrigadierCommand> factory = ctx -> command;
        return new CommandSpec(literal, permission, factory);
    }

    /** The kernel-side description of the hologram command, literal and help text, no Brigadier type. */
    private record HologramCommand(String literal, String description) implements BrigadierCommand {}
}

package com.uxplima.uxmessentials.npc.application;

import java.util.List;
import java.util.function.Function;

import com.uxplima.uxmessentials.shared.application.module.BrigadierCommand;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;

/**
 * The npc context's command surface as a platform-neutral {@link CommandSpec}: the single {@code /npc} literal,
 * gated by {@code uxmessentials.npc.admin}, that serves every form through its subcommands, {@code create},
 * {@code delete}, {@code list}, {@code movehere}, {@code skin}, and {@code command}. The npc inbound adapter
 * realises the Brigadier node from the started module's context on the next {@code COMMANDS} fire. Collected
 * here so {@code NpcModule} stays small and the command/permission pairing is one greppable row the permissions
 * guard checks against {@code paper-plugin.yml}.
 */
final class NpcCommandSurface {

    private NpcCommandSurface() {}

    static List<CommandSpec> all() {
        return List.of(
                spec("npc", "uxmessentials.npc.admin", new NpcCommand("npc", "Create and manage fake-player NPCs")));
    }

    private static CommandSpec spec(String literal, String permission, BrigadierCommand command) {
        Function<ModuleContext, BrigadierCommand> factory = ctx -> command;
        return new CommandSpec(literal, permission, factory);
    }

    /** The kernel-side description of the npc command, literal and help text, no Brigadier type. */
    private record NpcCommand(String literal, String description) implements BrigadierCommand {}
}

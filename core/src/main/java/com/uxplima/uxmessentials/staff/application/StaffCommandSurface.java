package com.uxplima.uxmessentials.staff.application;

import java.util.List;
import java.util.function.Function;

import com.uxplima.uxmessentials.shared.application.module.BrigadierCommand;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;

/**
 * The staff context's command surface as platform-neutral {@link CommandSpec}s: {@code /staffmode} guarded
 * by {@code uxmessentials.staff.mode} (toggle staff mode on or off), {@code /staffchat} (alias {@code /sc})
 * guarded by {@code uxmessentials.staff.chat} (send a line on the staff channel), and {@code /stafflist}
 * guarded by {@code uxmessentials.staff.list} (show who is currently in staff mode). The literals and nodes
 * are fixed and greppable so the surface guard checks them against {@code paper-plugin.yml}; the realised
 * Brigadier handlers are built in the adapter.
 *
 * <p>There is deliberately no sanction command here: staff mode only orchestrates the existing modules.
 */
final class StaffCommandSurface {

    private StaffCommandSurface() {}

    static List<CommandSpec> all() {
        return List.of(
                spec("staffmode", "uxmessentials.staff.mode", command("staffmode", "Toggle staff mode", List.of())),
                spec(
                        "staffchat",
                        "uxmessentials.staff.chat",
                        command("staffchat", "Send a message on the staff channel", List.of("sc"))),
                spec(
                        "stafflist",
                        "uxmessentials.staff.list",
                        command("stafflist", "List who is currently in staff mode", List.of())));
    }

    private static CommandSpec spec(String literal, String permission, BrigadierCommand command) {
        Function<ModuleContext, BrigadierCommand> factory = ctx -> command;
        return new CommandSpec(literal, permission, factory);
    }

    private static BrigadierCommand command(String literal, String description, List<String> aliases) {
        return new StaffCommand(literal, description, aliases);
    }

    /** The kernel-side description of one staff command: literal, help text, and aliases, no Brigadier type. */
    private record StaffCommand(String literal, String description, List<String> aliases) implements BrigadierCommand {}
}

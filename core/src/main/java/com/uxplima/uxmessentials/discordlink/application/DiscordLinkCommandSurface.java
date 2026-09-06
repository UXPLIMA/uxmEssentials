package com.uxplima.uxmessentials.discordlink.application;

import java.util.List;
import java.util.function.Function;

import com.uxplima.uxmessentials.shared.application.module.BrigadierCommand;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;

/**
 * The discord-link context's command surface as platform-neutral {@link CommandSpec}s: each pairs a literal
 * with its base permission node and a factory that produces the kernel-side {@link BrigadierCommand}
 * description. The bukkit-side inbound adapter realises the Brigadier node from the started module's context on
 * the next {@code COMMANDS} fire. Collected here so {@code DiscordlinkModule} stays small and the
 * command/permission pairing is one greppable table.
 *
 * <p>{@code /discordlink status} is a subcommand of {@code /discordlink} guarded by the same
 * {@code uxmessentials.discord.link} node, not a separate literal, so only the two top-level literals appear
 * here. The {@code /link} slash command lives entirely inside the Discord bridge and is not a Minecraft command.
 */
final class DiscordLinkCommandSurface {

    /** The single node guarding every in-game discord-link command. */
    static final String PERMISSION = "uxmessentials.discord.link";

    private DiscordLinkCommandSurface() {}

    static List<CommandSpec> all() {
        return List.of(
                spec("discordlink", DiscordLinkVerb.of("discordlink", "Link your account to Discord")),
                spec("discordunlink", DiscordLinkVerb.of("discordunlink", "Unlink your Discord account")));
    }

    private static CommandSpec spec(String literal, BrigadierCommand command) {
        Function<ModuleContext, BrigadierCommand> factory = ctx -> command;
        return new CommandSpec(literal, PERMISSION, factory);
    }

    /** The kernel-side description of one discord-link command, literal and help text, no Brigadier type. */
    private record DiscordLinkVerb(String literal, String description) implements BrigadierCommand {

        static DiscordLinkVerb of(String literal, String description) {
            return new DiscordLinkVerb(literal, description);
        }
    }
}

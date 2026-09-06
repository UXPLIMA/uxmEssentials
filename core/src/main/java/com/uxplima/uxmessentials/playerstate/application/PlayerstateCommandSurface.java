package com.uxplima.uxmessentials.playerstate.application;

import java.util.List;
import java.util.function.Function;

import com.uxplima.uxmessentials.shared.application.module.BrigadierCommand;
import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;

/**
 * The playerstate context's command surface (docs/10-feature-modules.md §15.6) as platform-neutral
 * {@link CommandSpec}s: each pairs a literal with its base permission node and a factory that produces the
 * kernel-side {@link BrigadierCommand} description. The playerstate inbound adapter realises the Brigadier
 * node from the started module's context on the next {@code COMMANDS} fire. Collected here so
 * {@code PlayerstateModule} stays small and the command/permission pairing is one greppable table the
 * permissions guard checks against {@code paper-plugin.yml}.
 *
 * <p>The {@code .others} target form of each self/other command is gated by the single shared
 * {@code uxmessentials.playerstate.others} node, checked per-invocation in the adapter rather than declared
 * as a separate command literal, so it is intentionally absent from this table. The {@code /gmc /gms /gma
 * /gmsp} mode shortcuts, {@code /nv}, and {@code /extinguish} are aliases of their base commands, registered
 * through the {@link BrigadierCommand#aliases()} list, not as separate specs. {@code /repair}, {@code /repairall},
 * {@code /hat}, and {@code /more} belong to the itemworld context and are intentionally not registered here.
 */
final class PlayerstateCommandSurface {

    private PlayerstateCommandSurface() {}

    static List<CommandSpec> all() {
        return List.of(
                spec("god", "uxmessentials.god.use", cmd("god", "Toggle damage immunity")),
                spec("fly", "uxmessentials.fly.use", cmd("fly", "Toggle flight")),
                spec("heal", "uxmessentials.heal.use", cmd("heal", "Restore health")),
                spec("feed", "uxmessentials.feed.use", cmd("feed", "Restore hunger")),
                spec("foodlevel", "uxmessentials.foodlevel.use", cmd("foodlevel", "Set a player's food level")),
                spec("health", "uxmessentials.health.use", cmd("health", "Set a player's health")),
                spec("gamemode", "uxmessentials.gamemode.use", cmd("gamemode", "Set game mode")),
                spec("speed", "uxmessentials.speed.use", cmd("speed", "Set walk and fly speed")),
                spec("flyspeed", "uxmessentials.speed.use", cmd("flyspeed", "Set fly speed")),
                spec("walkspeed", "uxmessentials.speed.use", cmd("walkspeed", "Set walk speed")),
                spec("ext", "uxmessentials.extinguish.use", cmd("ext", "Put out a burning player")),
                spec(
                        "clearinventory",
                        "uxmessentials.clearinventory.use",
                        cmd("clearinventory", "Clear a player's inventory")),
                spec(
                        "clearinventoryconfirmtoggle",
                        "uxmessentials.clearinventory.confirmtoggle",
                        cmd("clearinventoryconfirmtoggle", "Toggle a confirmation before /clearinventory")),
                spec("invsee", "uxmessentials.invsee.use", cmd("invsee", "View a player's inventory")),
                spec("endersee", "uxmessentials.endersee.use", cmd("endersee", "View a player's ender chest")),
                spec("suicide", "uxmessentials.suicide.use", cmd("suicide", "Kill yourself")),
                spec("near", "uxmessentials.near.use", cmd("near", "List nearby players")),
                spec("nightvision", "uxmessentials.nightvision.use", cmd("nightvision", "Toggle night vision")),
                spec("glow", "uxmessentials.glow.use", cmd("glow", "Toggle a glowing outline")),
                spec("ptime", "uxmessentials.ptime.use", cmd("ptime", "Set your personal time")),
                spec("pweather", "uxmessentials.pweather.use", cmd("pweather", "Set your personal weather")),
                spec("exp", "uxmessentials.exp.use", cmd("exp", "Get or set experience")),
                spec("air", "uxmessentials.air.use", cmd("air", "Set remaining air")),
                spec("burn", "uxmessentials.burn.use", cmd("burn", "Set a player on fire")),
                spec("ice", "uxmessentials.ice.use", cmd("ice", "Freeze a player")),
                spec("getpos", "uxmessentials.getpos.use", cmd("getpos", "Show a player's coordinates")),
                spec("ping", "uxmessentials.ping.use", cmd("ping", "Show a player's ping")),
                spec("playtime", "uxmessentials.playtime.use", cmd("playtime", "Show a player's playtime breakdown")),
                spec("rest", "uxmessentials.rest.use", cmd("rest", "Reset time-since-rest so phantoms stop")),
                spec("depth", "uxmessentials.depth.use", cmd("depth", "Show how far you are above or below sea level")),
                spec("biome", "uxmessentials.biome.use", cmd("biome", "Show the biome you are standing in")),
                spec("seed", "uxmessentials.seed.use", cmd("seed", "Show the seed of the world you are standing in")),
                spec("compass", "uxmessentials.compass.use", cmd("compass", "Show the direction you are facing")),
                spec("world", "uxmessentials.world.use", cmd("world", "Show the world you are standing in")),
                spec(
                        "dimension",
                        "uxmessentials.dimension.use",
                        cmd("dimension", "Show the dimension you are standing in")));
    }

    private static CommandSpec spec(String literal, String permission, BrigadierCommand command) {
        Function<ModuleContext, BrigadierCommand> factory = ctx -> command;
        return new CommandSpec(literal, permission, factory);
    }

    private static BrigadierCommand cmd(String literal, String description) {
        return new PlayerstateCommand(literal, description);
    }

    /** The kernel-side description of one playerstate command, literal and help text, no Brigadier type. */
    private record PlayerstateCommand(String literal, String description) implements BrigadierCommand {}
}

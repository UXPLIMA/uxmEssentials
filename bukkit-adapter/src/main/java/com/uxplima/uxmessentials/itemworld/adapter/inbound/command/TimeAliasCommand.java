package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.util.Map;
import java.util.function.Supplier;

import org.bukkit.World;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.itemworld.domain.SubFeatureGroup;
import com.uxplima.uxmessentials.itemworld.domain.TimeSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import org.jspecify.annotations.NullMarked;

/**
 * One quick time-alias verb, {@code /day} ({@code TimeSpec.day()}) or {@code /night} ({@code TimeSpec.night()})
 *, parameterised by the {@link TimeSpec} it sets and its own literal/permission so the two share one class
 * rather than two near-identical files. Each is its own command literal in the time/weather sub-feature group,
 * gated by its own per-command disable. World time is global state, so the set runs on the global region thread
 * through the kernel {@code Scheduler#onGlobal} and is reported through {@link ItemworldMessageKey#TIME_SET}.
 */
@NullMarked
public final class TimeAliasCommand extends ItemworldCommandSupport implements CommandRegistration {

    private final String permission;
    private final Supplier<TimeSpec> spec;

    private TimeAliasCommand(
            ItemworldServices services, String literal, String permission, String help, Supplier<TimeSpec> spec) {
        super(services, literal, SubFeatureGroup.TIME_WEATHER, help);
        this.permission = java.util.Objects.requireNonNull(permission, "permission");
        this.spec = java.util.Objects.requireNonNull(spec, "spec");
    }

    /** {@code /day}, set the world to daytime. */
    public static TimeAliasCommand day(ItemworldServices services) {
        return new TimeAliasCommand(services, "day", "uxmessentials.time.alias", "Set the time to day.", TimeSpec::day);
    }

    /** {@code /night}, set the world to nighttime. */
    public static TimeAliasCommand night(ItemworldServices services) {
        return new TimeAliasCommand(
                services, "night", "uxmessentials.time.alias", "Set the time to night.", TimeSpec::night);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(literal())
                .requires(src -> src.getSender().hasPermission(permission))
                .executes(this::run)
                .build();
    }

    @Override
    public String description() {
        return describe();
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        if (!enabled(ctx)) {
            return Command.SINGLE_SUCCESS;
        }
        Player player = player(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        World world = player.getWorld();
        TimeSpec resolved = spec.get();
        services.kernel().scheduler().onGlobal(() -> {
            world.setTime(resolved.ticks());
            reply(
                    ctx,
                    ItemworldMessageKey.TIME_SET,
                    Map.of("world", world.getName(), "ticks", String.valueOf(world.getTime())));
        });
        return Command.SINGLE_SUCCESS;
    }
}

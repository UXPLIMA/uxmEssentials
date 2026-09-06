package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.World;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.itemworld.domain.SubFeatureGroup;
import com.uxplima.uxmessentials.itemworld.domain.TimeSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /time <set|add> <value>}: set or advance the world's time. The value is validated by the domain
 * {@link TimeSpec}. A tick number or a named keyword ({@code day}, {@code noon}, {@code night}, …), a set
 * bounded to {@code [0, 24000)}: before the world is touched (else {@link ItemworldMessageKey#BAD_TIME}). The
 * {@code /day} and {@code /night} alias verbs share this group through their own classes.
 *
 * <p>World time is global game state, so the change runs on the global region thread through the kernel
 * {@code Scheduler#onGlobal} (the one place that serialisation is correct); the result is reported through
 * {@link ItemworldMessageKey#TIME_SET}.
 */
@NullMarked
public final class TimeCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.time.use";

    // The two modes the parser accepts (set the absolute time, or add a delta).
    private static final List<String> MODES = List.of("set", "add");

    // The named keywords TimeSpec.parse resolves; a raw tick number is also accepted, so the type-ahead offers the
    // memorable keywords and leaves the numeric form to the player.
    private static final List<String> VALUES = List.of("day", "noon", "sunset", "night", "midnight", "sunrise");

    public TimeCommand(ItemworldServices services) {
        super(services, "time", SubFeatureGroup.TIME_WEATHER, "Set or add world time.");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(literal())
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.argument("mode", StringArgumentType.word())
                        .suggests(CommandSuggestions.fromStrings(() -> MODES))
                        .then(Commands.argument("value", StringArgumentType.word())
                                .suggests(CommandSuggestions.fromStrings(() -> VALUES))
                                .executes(this::run)))
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
        TimeSpec.Mode mode = parseMode(ctx.getArgument("mode", String.class));
        if (mode == null) {
            reply(ctx, ItemworldMessageKey.BAD_TIME);
            return Command.SINGLE_SUCCESS;
        }
        Optional<TimeSpec> spec = TimeSpec.parse(mode, ctx.getArgument("value", String.class));
        if (spec.isEmpty()) {
            reply(ctx, ItemworldMessageKey.BAD_TIME);
            return Command.SINGLE_SUCCESS;
        }
        apply(ctx, player.getWorld(), spec.get());
        return Command.SINGLE_SUCCESS;
    }

    private void apply(CommandContext<CommandSourceStack> ctx, World world, TimeSpec spec) {
        services.kernel().scheduler().onGlobal(() -> {
            long resolved = spec.mode() == TimeSpec.Mode.SET ? spec.ticks() : world.getTime() + spec.ticks();
            world.setTime(resolved);
            reply(
                    ctx,
                    ItemworldMessageKey.TIME_SET,
                    Map.of("world", world.getName(), "ticks", String.valueOf(world.getTime())));
        });
    }

    private static TimeSpec.@org.jspecify.annotations.Nullable Mode parseMode(String raw) {
        return switch (raw.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "set" -> TimeSpec.Mode.SET;
            case "add" -> TimeSpec.Mode.ADD;
            default -> null;
        };
    }
}

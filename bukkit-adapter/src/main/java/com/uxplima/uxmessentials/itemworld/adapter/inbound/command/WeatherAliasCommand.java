package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.util.Locale;
import java.util.Map;

import org.bukkit.World;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.itemworld.adapter.outbound.BukkitWeatherApplier;
import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.itemworld.domain.SubFeatureGroup;
import com.uxplima.uxmessentials.itemworld.domain.WeatherSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import org.jspecify.annotations.NullMarked;

/**
 * One quick weather-alias verb, {@code /sun} ({@link WeatherSpec.Kind#CLEAR}), {@code /rain}
 * ({@link WeatherSpec.Kind#RAIN}) or {@code /thunder} ({@link WeatherSpec.Kind#THUNDER}), parameterised by its
 * {@link WeatherSpec.Kind} and its own literal so the three share one class rather than three near-identical
 * files. Each is its own command literal in the time/weather sub-feature group, gated by its own per-command
 * disable. World weather is global state, so the set runs on the global region thread through the kernel
 * {@code Scheduler#onGlobal} via {@link BukkitWeatherApplier} and is reported through
 * {@link ItemworldMessageKey#WEATHER_SET}.
 */
@NullMarked
public final class WeatherAliasCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.weather.alias";

    private final WeatherSpec.Kind kind;

    private WeatherAliasCommand(ItemworldServices services, String literal, String help, WeatherSpec.Kind kind) {
        super(services, literal, SubFeatureGroup.TIME_WEATHER, help);
        this.kind = java.util.Objects.requireNonNull(kind, "kind");
    }

    /** {@code /sun}, clear the weather. */
    public static WeatherAliasCommand sun(ItemworldServices services) {
        return new WeatherAliasCommand(services, "sun", "Clear the weather.", WeatherSpec.Kind.CLEAR);
    }

    /** {@code /rain}, start rain. */
    public static WeatherAliasCommand rain(ItemworldServices services) {
        return new WeatherAliasCommand(services, "rain", "Start rain.", WeatherSpec.Kind.RAIN);
    }

    /** {@code /thunder}, start a thunderstorm. */
    public static WeatherAliasCommand thunder(ItemworldServices services) {
        return new WeatherAliasCommand(services, "thunder", "Start a thunderstorm.", WeatherSpec.Kind.THUNDER);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(literal())
                .requires(src -> src.getSender().hasPermission(PERMISSION))
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
        WeatherSpec spec = WeatherSpec.of(kind);
        services.kernel().scheduler().onGlobal(() -> {
            BukkitWeatherApplier.apply(world, spec);
            reply(
                    ctx,
                    ItemworldMessageKey.WEATHER_SET,
                    Map.of("world", world.getName(), "weather", kind.name().toLowerCase(Locale.ROOT)));
        });
        return Command.SINGLE_SUCCESS;
    }
}

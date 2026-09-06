package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.itemworld.adapter.outbound.BukkitEntityResolver;
import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.itemworld.domain.MobSpec;
import com.uxplima.uxmessentials.itemworld.domain.SubFeatureGroup;
import com.uxplima.uxmessentials.itemworld.domain.event.MobsSpawned;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /spawnmob <type> [amount]}: spawn mobs at the actor's location. An abusable verb: the count is
 * validated against the configured {@code spawnmob-cap} in the domain {@link MobSpec} before any entity
 * materialises, and the spawn is audit-logged (docs/10-feature-modules.md §15.10). The mob type is validated at
 * the boundary: an unknown id replies {@link ItemworldMessageKey#SPAWNMOB_UNKNOWN}; an out-of-range count
 * replies {@link ItemworldMessageKey#AMOUNT_OUT_OF_RANGE}.
 *
 * <p>Spawning is entity/region-bound, so it runs on the actor's region thread through the kernel
 * {@code Scheduler}; the count actually spawned is reported, audit-logged, and published as a
 * {@link MobsSpawned} domain event for other plugins to observe.
 */
@NullMarked
public final class SpawnMobCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.spawnmob.use";

    public SpawnMobCommand(ItemworldServices services) {
        super(services, "spawnmob", SubFeatureGroup.MOB_ENTITY, "Spawn mobs.");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(literal())
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(mobTypeArgument()
                        .executes(ctx -> run(ctx, 1))
                        .then(Commands.literal("at").then(explicitPositionArguments()))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                .executes(ctx -> run(ctx, IntegerArgumentType.getInteger(ctx, "amount")))))
                .build();
    }

    @Override
    public String description() {
        return describe();
    }

    private int run(CommandContext<CommandSourceStack> ctx, int requestedAmount) {
        if (!enabled(ctx)) {
            return Command.SINGLE_SUCCESS;
        }
        Player player = player(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        String rawType = ctx.getArgument("type", String.class);
        Optional<MobSpec> spec =
                MobSpec.of(rawType, requestedAmount, services.config().spawnMobCap());
        if (spec.isEmpty()) {
            reply(ctx, ItemworldMessageKey.SPAWNMOB_UNKNOWN, Map.of("mob", rawType));
            return Command.SINGLE_SUCCESS;
        }
        Optional<EntityType> type =
                BukkitEntityResolver.spawnable("minecraft:" + spec.get().typeId());
        if (type.isEmpty()) {
            reply(ctx, ItemworldMessageKey.SPAWNMOB_UNKNOWN, Map.of("mob", rawType));
            return Command.SINGLE_SUCCESS;
        }
        if (!allowsType(
                ctx,
                ctx.getSource().getSender(),
                "spawnmob",
                type.get().getKey().getKey())) {
            return Command.SINGLE_SUCCESS;
        }
        PlayerRef actor = ref(player);
        spawn(
                ctx,
                actor,
                BukkitRefs.toPosition(Objects.requireNonNull(player.getLocation(), "player location")),
                spec.get(),
                type.get());
        return Command.SINGLE_SUCCESS;
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> explicitPositionArguments() {
        return Commands.argument("world", StringArgumentType.word())
                .suggests(CommandSuggestions.loadedWorlds())
                .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                        .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                                .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                        .executes(ctx -> runAt(ctx, 1))
                                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                                .executes(ctx ->
                                                        runAt(ctx, IntegerArgumentType.getInteger(ctx, "amount")))))));
    }

    private int runAt(CommandContext<CommandSourceStack> ctx, int requestedAmount) {
        if (!enabled(ctx)) {
            return Command.SINGLE_SUCCESS;
        }
        CommandSender sender = ctx.getSource().getSender();
        String worldName = StringArgumentType.getString(ctx, "world");
        World world = sender.getServer().getWorld(worldName);
        if (world == null) {
            reply(ctx, SharedMessageKey.COMMAND_UNKNOWN_WORLD, Map.of("world", worldName));
            return 0;
        }
        double x = DoubleArgumentType.getDouble(ctx, "x");
        double y = DoubleArgumentType.getDouble(ctx, "y");
        double z = DoubleArgumentType.getDouble(ctx, "z");
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            reply(ctx, SharedMessageKey.COMMAND_INVALID_POSITION);
            return 0;
        }
        String rawType = ctx.getArgument("type", String.class);
        Optional<MobSpec> spec =
                MobSpec.of(rawType, requestedAmount, services.config().spawnMobCap());
        Optional<EntityType> type =
                spec.flatMap(value -> BukkitEntityResolver.spawnable("minecraft:" + value.typeId()));
        if (spec.isEmpty() || type.isEmpty()) {
            reply(ctx, ItemworldMessageKey.SPAWNMOB_UNKNOWN, Map.of("mob", rawType));
            return Command.SINGLE_SUCCESS;
        }
        if (!allowsType(ctx, sender, "spawnmob", type.get().getKey().getKey())) {
            return Command.SINGLE_SUCCESS;
        }
        spawn(ctx, actor(ctx), new Position(BukkitRefs.toRef(world), x, y, z, 0f, 0f), spec.get(), type.get());
        return Command.SINGLE_SUCCESS;
    }

    private void spawn(
            CommandContext<CommandSourceStack> ctx, PlayerRef actor, Position position, MobSpec spec, EntityType type) {
        services.kernel().scheduler().onRegion(position, () -> {
            World world = Bukkit.getWorld(position.world().uid());
            if (world == null) {
                return;
            }
            Location at = new Location(world, position.x(), position.y(), position.z());
            int spawned = 0;
            for (int i = 0; i < spec.amount(); i++) {
                if (world.spawnEntity(at, type) != null) {
                    spawned++;
                }
            }
            reply(
                    ctx,
                    ItemworldMessageKey.SPAWNMOB_SPAWNED,
                    Map.of("amount", String.valueOf(spawned), "mob", spec.typeId()));
            services.audit().spawnedMob(actor, spec, spawned);
            services.kernel().events().publish(new MobsSpawned(actor, spec, position.world(), spawned, Instant.now()));
        });
    }
}

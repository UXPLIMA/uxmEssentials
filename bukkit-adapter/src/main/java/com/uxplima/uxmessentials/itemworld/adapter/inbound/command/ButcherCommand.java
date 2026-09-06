package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.time.Instant;
import java.util.Map;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.itemworld.adapter.outbound.BukkitEntityPurger;
import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.itemworld.application.PurgePolicy;
import com.uxplima.uxmessentials.itemworld.domain.PurgeSelection;
import com.uxplima.uxmessentials.itemworld.domain.SubFeatureGroup;
import com.uxplima.uxmessentials.itemworld.domain.event.EntitiesPurged;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /butcher [radius]}: purge hostile mobs within a radius around the actor. An entity-purge verb, the
 * most abusable in the surface, so the radius is clamped to {@code purge-max-radius} in the domain
 * {@link PurgePolicy} and the sweep is audit-logged (docs/10-feature-modules.md §15.10). Tamed pets and players
 * are never swept (the purger's invariant).
 *
 * <p>The sweep is region-bound, so it runs on the actor's region thread through the kernel {@code Scheduler};
 * the count removed is reported through {@link ItemworldMessageKey#BUTCHER_DONE}, audited, and published as an
 * {@link EntitiesPurged} domain event.
 */
@NullMarked
public final class ButcherCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.butcher.use";
    private static final int DEFAULT_RADIUS = 64;

    private final PurgePolicy policy;

    public ButcherCommand(ItemworldServices services, PurgePolicy policy) {
        super(services, "butcher", SubFeatureGroup.MOB_ENTITY, "Purge nearby mobs.");
        this.policy = java.util.Objects.requireNonNull(policy, "policy");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(literal())
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(ctx -> run(ctx, DEFAULT_RADIUS))
                .then(Commands.argument("radius", IntegerArgumentType.integer(1))
                        .executes(ctx -> run(ctx, IntegerArgumentType.getInteger(ctx, "radius"))))
                .build();
    }

    @Override
    public String description() {
        return describe();
    }

    private int run(CommandContext<CommandSourceStack> ctx, int radius) {
        if (!enabled(ctx)) {
            return Command.SINGLE_SUCCESS;
        }
        Player player = player(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        PurgeSelection selection = policy.butcher(radius);
        sweep(ctx, player, selection);
        return Command.SINGLE_SUCCESS;
    }

    private void sweep(CommandContext<CommandSourceStack> ctx, Player player, PurgeSelection selection) {
        PlayerRef actor = ref(player);
        services.kernel().scheduler().onEntity(actor, () -> {
            int removed = BukkitEntityPurger.purge(player, selection);
            reply(
                    ctx,
                    ItemworldMessageKey.BUTCHER_DONE,
                    Map.of("count", String.valueOf(removed), "radius", String.valueOf(selection.radius())));
            services.audit().butchered(actor, selection, removed);
            services.kernel()
                    .events()
                    .publish(new EntitiesPurged(
                            actor, selection, BukkitRefs.toRef(player.getWorld()), removed, Instant.now()));
        });
    }
}

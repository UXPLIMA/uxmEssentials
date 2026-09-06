package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
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
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /killall [type]}: purge entities world-wide. A single named type, or every removable entity when no
 * type is given. An entity-purge verb (audit-logged). The selection is shaped by the domain {@link PurgePolicy}
 * ({@code killall} with a blank type sweeps all entities, a named type sweeps that type only); players and tamed
 * pets are never swept.
 *
 * <p>A world sweep spans every region of the world, so it cannot run on one actor's region thread under Folia:
 * the purger snapshots the roster on the global region thread and removes each matching entity on the region that
 * owns it, then reports the aggregate count through {@link ItemworldMessageKey#KILLALL_DONE}, audits it, and
 * publishes an {@link EntitiesPurged} domain event.
 */
@NullMarked
public final class KillAllCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.killall.use";

    private final PurgePolicy policy;

    public KillAllCommand(ItemworldServices services, PurgePolicy policy) {
        super(services, "killall", SubFeatureGroup.MOB_ENTITY, "Purge entities world-wide.");
        this.policy = java.util.Objects.requireNonNull(policy, "policy");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(literal())
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(ctx -> run(ctx, ""))
                .then(mobTypeArgument().executes(ctx -> run(ctx, StringArgumentType.getString(ctx, "type"))))
                .build();
    }

    @Override
    public String description() {
        return describe();
    }

    private int run(CommandContext<CommandSourceStack> ctx, String type) {
        if (!enabled(ctx)) {
            return Command.SINGLE_SUCCESS;
        }
        Player player = player(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        PurgeSelection selection = policy.killAll(type);
        sweep(ctx, player, selection);
        return Command.SINGLE_SUCCESS;
    }

    private void sweep(CommandContext<CommandSourceStack> ctx, Player player, PurgeSelection selection) {
        PlayerRef actor = ref(player);
        Optional<String> type = selection.typeId();
        // The world ref is captured here on the dispatch thread; the completion callback runs on a region thread,
        // where re-reading the player's world would be a cross-region read.
        WorldRef world = BukkitRefs.toRef(player.getWorld());
        // The world roster spans every region, so the purger does its own global-snapshot-then-per-region removal;
        // the completion callback runs on whichever region ran the last removal, carrying the aggregate count. The
        // reply hops back to the player's own thread through the message sink, and audit/event publish are pure.
        BukkitEntityPurger.purgeWorld(services.kernel().scheduler(), player, selection, removed -> {
            reply(
                    ctx,
                    ItemworldMessageKey.KILLALL_DONE,
                    Map.of("count", String.valueOf(removed), "type", type.orElse("all")));
            services.audit().killedAll(actor, selection, removed);
            services.kernel().events().publish(new EntitiesPurged(actor, selection, world, removed, Instant.now()));
        });
    }
}

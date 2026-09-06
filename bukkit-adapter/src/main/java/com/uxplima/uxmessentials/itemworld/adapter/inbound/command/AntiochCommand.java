package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.itemworld.domain.SubFeatureGroup;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /antioch} (alias {@code /grenade}): lob a short-fused primed TNT from the caller's eye in their facing
 * direction, a holy-hand-grenade gag. An admin-fun verb (audit-logged): the throw is a
 * destructive, abusable effect, so it is recorded with the actor.
 *
 * <p>Spawning and propelling the TNT is region-bound, so it runs on the caller's region thread through the
 * kernel {@code Scheduler}; the throw is reported through {@link ItemworldMessageKey#ANTIOCH_THROWN} and audited.
 */
@NullMarked
public final class AntiochCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.antioch.use";
    private static final double SPEED = 1.5;
    private static final int FUSE_TICKS = 40;

    public AntiochCommand(ItemworldServices services) {
        super(services, "antioch", SubFeatureGroup.ADMIN_FUN, "Throw a primed TNT grenade.");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(literal())
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::run)
                .build();
    }

    @Override
    public List<String> aliases() {
        return List.of("grenade");
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
        PlayerRef actor = ref(player);
        services.kernel().scheduler().onEntity(actor, () -> {
            Location eye = player.getEyeLocation();
            TNTPrimed tnt = player.getWorld().spawn(eye, TNTPrimed.class);
            tnt.setFuseTicks(FUSE_TICKS);
            tnt.setVelocity(eye.getDirection().multiply(SPEED));
            reply(ctx, ItemworldMessageKey.ANTIOCH_THROWN);
            services.audit().threwAntioch(actor);
        });
        return Command.SINGLE_SUCCESS;
    }
}

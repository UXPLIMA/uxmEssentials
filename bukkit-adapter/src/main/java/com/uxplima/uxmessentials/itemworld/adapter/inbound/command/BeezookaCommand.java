package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.entity.Bee;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

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
 * {@code /beezooka} (alias {@code /beecannon}): launch an angry bee from the caller's eye in their facing
 * direction, a bee-cannon gag. An admin-fun verb (audit-logged).
 *
 * <p>Spawning and propelling the bee is region-bound, so it runs on the caller's region thread through the
 * kernel {@code Scheduler}; the launch is reported through {@link ItemworldMessageKey#BEEZOOKA_FIRED} and
 * audited.
 */
@NullMarked
public final class BeezookaCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.beezooka.use";
    private static final double SPEED = 1.5;
    private static final int ANGER_TICKS = 600;

    public BeezookaCommand(ItemworldServices services) {
        super(services, "beezooka", SubFeatureGroup.ADMIN_FUN, "Launch an angry bee.");
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
        return List.of("beecannon");
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
            if (player.getWorld().spawnEntity(eye, EntityType.BEE) instanceof Bee bee) {
                bee.setAnger(ANGER_TICKS);
                bee.setVelocity(eye.getDirection().multiply(SPEED));
            }
            reply(ctx, ItemworldMessageKey.BEEZOOKA_FIRED);
            services.audit().firedBeezooka(actor);
        });
        return Command.SINGLE_SUCCESS;
    }
}

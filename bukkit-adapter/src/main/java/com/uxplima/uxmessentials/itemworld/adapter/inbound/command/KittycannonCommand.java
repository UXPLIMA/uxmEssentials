package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import org.bukkit.Location;
import org.bukkit.entity.Cat;
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
 * {@code /kittycannon}: launch a cat from the caller's eye that detonates in a harmless cosmetic explosion. An
 * admin-fun verb (audit-logged). The cat is propelled forward and a non-block-breaking, non-fire explosion is
 * created at the launch point, so the effect is the gag without the griefing.
 *
 * <p>Spawning and launching the cat is region-bound, so it runs on the caller's region thread through the kernel
 * {@code Scheduler}; the launch is reported through {@link ItemworldMessageKey#KITTYCANNON_FIRED} and audited.
 */
@NullMarked
public final class KittycannonCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.kittycannon.use";
    private static final double SPEED = 1.5F;
    private static final float COSMETIC_YIELD = 0.0F;

    public KittycannonCommand(ItemworldServices services) {
        super(services, "kittycannon", SubFeatureGroup.ADMIN_FUN, "Launch an exploding cat.");
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
        PlayerRef actor = ref(player);
        services.kernel().scheduler().onEntity(actor, () -> {
            Location eye = player.getEyeLocation();
            if (player.getWorld().spawnEntity(eye, EntityType.CAT) instanceof Cat cat) {
                cat.setVelocity(eye.getDirection().multiply(SPEED));
            }
            player.getWorld().createExplosion(eye, COSMETIC_YIELD, false, false);
            reply(ctx, ItemworldMessageKey.KITTYCANNON_FIRED);
            services.audit().firedKittycannon(actor);
        });
        return Command.SINGLE_SUCCESS;
    }
}

package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.util.List;
import java.util.Map;

import org.bukkit.Location;
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
import com.uxplima.uxmessentials.shared.adapter.inbound.command.PlayerTargets;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /lightning [player]} (alias {@code /smite}): strike lightning at a player. With no argument the strike
 * lands wherever the caller is aiming. The self form is the "strike where I aim" verb: the block under the
 * cross-hair, or the point along the look direction when that is open air (so aiming at the sky still strikes
 * out in front, not back on the caller). With a named player or a selector the strike lands on each
 * <em>target's own position</em>, never the caller's look direction, and {@code @a} fans the strike out to
 * every online player. An admin-fun verb (audit-logged): a cosmetic-but-abusable effect, so the strike is
 * recorded with actor and target. A name or selector that matches no online player answers
 * {@link ItemworldMessageKey#UNKNOWN_TARGET}.
 *
 * <p>Each strike is region-bound, so it runs on its target's region thread through the kernel {@code Scheduler}.
 * The targeted form names its struck player through {@link ItemworldMessageKey#LIGHTNING_STRUCK}; the self/look
 * form, which has no target player, reports through {@link ItemworldMessageKey#LIGHTNING_STRUCK_SELF}. Both are
 * audited.
 */
@NullMarked
public final class LightningCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.lightning.use";
    private static final int REACH = 64;

    public LightningCommand(ItemworldServices services) {
        super(services, "lightning", SubFeatureGroup.ADMIN_FUN, "Strike lightning.");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(literal())
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::runSelf)
                .then(PlayerTargets.players("player").executes(this::runTargets))
                .build();
    }

    @Override
    public String description() {
        return describe();
    }

    @Override
    public java.util.List<String> aliases() {
        return java.util.List.of("smite");
    }

    private int runSelf(CommandContext<CommandSourceStack> ctx) {
        if (!enabled(ctx)) {
            return Command.SINGLE_SUCCESS;
        }
        Player self = player(ctx);
        if (self == null) {
            return Command.SINGLE_SUCCESS;
        }
        // The self form keeps the "strike where I aim" behaviour: the strike lands where the caller looks.
        strikeAt(ctx, self, self, true);
        return Command.SINGLE_SUCCESS;
    }

    private int runTargets(CommandContext<CommandSourceStack> ctx) {
        if (!enabled(ctx)) {
            return Command.SINGLE_SUCCESS;
        }
        PlayerRef actor = actor(ctx);
        List<Player> targets = PlayerTargets.resolveAll(ctx, "player");
        if (targets.isEmpty()) {
            reply(ctx, ItemworldMessageKey.UNKNOWN_TARGET, Map.of("player", typedTarget(ctx)));
            return Command.SINGLE_SUCCESS;
        }
        for (Player target : targets) {
            // A named/selected target is struck at its own position, not where the caller is looking.
            strikeAt(ctx, actor, target, false);
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Schedule the strike on {@code at}'s region thread. {@code aimAtLook} chooses the point: the self form
     * uses the block the caller looks at, a targeted form uses the target's own live position. Both are read on
     * the target's region thread (where the position is owned on Folia), never from the command thread.
     */
    private void strikeAt(CommandContext<CommandSourceStack> ctx, Player actor, Player at, boolean aimAtLook) {
        strikeAt(ctx, ref(actor), at, aimAtLook);
    }

    private void strikeAt(CommandContext<CommandSourceStack> ctx, PlayerRef actorRef, Player at, boolean aimAtLook) {
        String label = at.getName();
        boolean self = actorRef.equals(ref(at));
        services.kernel().scheduler().onEntity(ref(at), () -> {
            Location where = aimAtLook ? lookCentre(at) : selfLocation(at);
            at.getWorld().strikeLightning(where);
            if (aimAtLook) {
                reply(ctx, ItemworldMessageKey.LIGHTNING_STRUCK_SELF, Map.of());
            } else {
                reply(ctx, ItemworldMessageKey.LIGHTNING_STRUCK, Map.of("target", label));
            }
            services.audit()
                    .struckLightning(
                            actorRef, self ? java.util.Optional.empty() : java.util.Optional.of(BukkitRefs.toRef(at)));
        });
    }

    /**
     * The point of the self/look strike: the block under the cross-hair, or, when that is open air, the point
     * {@code REACH} away along the caller's look direction. Aiming at the sky must never fall back to the
     * caller's own position; the bolt follows the aim and grounds itself at that column.
     */
    private static Location lookCentre(Player self) {
        org.bukkit.block.@org.jspecify.annotations.Nullable Block targetBlock = self.getTargetBlockExact(REACH);
        if (targetBlock != null) {
            return targetBlock.getLocation();
        }
        Location eye = self.getEyeLocation();
        return eye.add(eye.getDirection().multiply(REACH));
    }

    private static Location selfLocation(Player player) {
        // Paper marks Player#getLocation() nullable (null only for an entity with no world, which a connected
        // player never is), so the fallback is requireNonNull rather than a nullable return.
        return java.util.Objects.requireNonNull(player.getLocation(), "player location");
    }

    private static String typedTarget(CommandContext<CommandSourceStack> ctx) {
        return ctx.getNodes().stream()
                .filter(node -> "player".equals(node.getNode().getName()))
                .findFirst()
                .map(node -> node.getRange().get(ctx.getInput()))
                .orElse("");
    }
}

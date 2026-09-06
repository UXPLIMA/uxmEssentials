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
 * {@code /nuke [player]}: rain lightning over the target area. With no argument the centre is wherever the
 * caller is aiming. The self form is the "storm where I aim" verb: the block under the cross-hair, or the
 * point along the look direction when that is open air (so aiming at the sky still strikes out in front, not
 * back on the caller). With a named player or a selector the centre is each <em>target's own position</em>,
 * never the caller's look direction, and {@code @a} fans the storm out to every online player. An admin-fun
 * verb (audit-logged): a destructive, abusable effect, so each storm is recorded with actor and target. A name
 * or selector that matches no online player answers {@link ItemworldMessageKey#UNKNOWN_TARGET}.
 *
 * <p>Each storm is region-bound, so it runs on its target's region thread through the kernel {@code Scheduler}.
 * To stay bounded it strikes a fixed set of offset points around the centre rather than every block in the
 * area. The targeted form names its struck player through {@link ItemworldMessageKey#NUKE_DONE}; the self/look
 * form, which has no target player, reports through {@link ItemworldMessageKey#NUKE_DONE_SELF}. Both are
 * audited.
 */
@NullMarked
public final class NukeCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.nuke.use";
    private static final int REACH = 64;
    // A bounded plus/diagonal pattern around the centre: thirteen strikes spanning a five-block radius. Fixed
    // offsets keep this well clear of a per-block O(n^2) storm while still reading as an area effect.
    private static final int[][] OFFSETS = {
        {0, 0}, {3, 0}, {-3, 0}, {0, 3}, {0, -3}, {2, 2}, {-2, 2}, {2, -2}, {-2, -2}, {5, 0}, {-5, 0}, {0, 5}, {0, -5}
    };

    public NukeCommand(ItemworldServices services) {
        super(services, "nuke", SubFeatureGroup.ADMIN_FUN, "Rain lightning over an area.");
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
    public List<String> aliases() {
        return List.of();
    }

    private int runSelf(CommandContext<CommandSourceStack> ctx) {
        if (!enabled(ctx)) {
            return Command.SINGLE_SUCCESS;
        }
        Player self = player(ctx);
        if (self == null) {
            return Command.SINGLE_SUCCESS;
        }
        // The self form keeps the "storm where I aim" behaviour: the centre is the block the caller looks at.
        nukeAt(ctx, self, self, true);
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
            nukeAt(ctx, actor, target, false);
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Schedule the storm on {@code at}'s region thread. {@code aimAtLook} chooses the centre: the self form
     * uses the block the caller looks at, a targeted form uses the target's own live position. Both are read on
     * the target's region thread (where the position is owned on Folia), never from the command thread.
     */
    private void nukeAt(CommandContext<CommandSourceStack> ctx, Player actor, Player at, boolean aimAtLook) {
        nukeAt(ctx, ref(actor), at, aimAtLook);
    }

    private void nukeAt(CommandContext<CommandSourceStack> ctx, PlayerRef actorRef, Player at, boolean aimAtLook) {
        String label = at.getName();
        boolean self = actorRef.equals(ref(at));
        services.kernel().scheduler().onEntity(ref(at), () -> {
            Location centre = aimAtLook ? lookCentre(at) : selfLocation(at);
            for (Location point : strikePoints(centre)) {
                at.getWorld().strikeLightning(point);
            }
            if (aimAtLook) {
                reply(ctx, ItemworldMessageKey.NUKE_DONE_SELF, Map.of());
            } else {
                reply(ctx, ItemworldMessageKey.NUKE_DONE, Map.of("target", label));
            }
            services.audit()
                    .nuked(actorRef, self ? java.util.Optional.empty() : java.util.Optional.of(BukkitRefs.toRef(at)));
        });
    }

    private static List<Location> strikePoints(Location centre) {
        java.util.List<Location> points = new java.util.ArrayList<>(OFFSETS.length);
        for (int[] offset : OFFSETS) {
            points.add(centre.clone().add(offset[0], 0, offset[1]));
        }
        return points;
    }

    /**
     * The centre of the self/look storm: the block under the cross-hair, or, when that is open air, the point
     * {@code REACH} away along the caller's look direction. Aiming at the sky must never fall back to the
     * caller's own position; the storm follows the aim, and the bolt grounds itself at that column.
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

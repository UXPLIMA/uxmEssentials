package com.uxplima.uxmessentials.poses.adapter.inbound.command;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.poses.adapter.inbound.SeatGeometry;
import com.uxplima.uxmessentials.poses.adapter.inbound.SeatGeometry.SeatTarget;
import com.uxplima.uxmessentials.poses.application.PoseSessions;
import com.uxplima.uxmessentials.poses.application.PosesMessageKey;
import com.uxplima.uxmessentials.poses.application.StartSit;
import com.uxplima.uxmessentials.poses.application.StartSit.SitOutcome;
import com.uxplima.uxmessentials.poses.application.StopPose;
import com.uxplima.uxmessentials.poses.domain.PoseSession;
import com.uxplima.uxmessentials.poses.domain.PoseType;
import com.uxplima.uxmessentials.poses.domain.SittableBlocks;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /sit} ({@code uxmessentials.sit.use}): sit down where you stand, or on the sittable block you look at
 * within reach. The {@link StartSit} use case owns the region gate, the one-session invariant, and the seat; this
 * handler only resolves <em>which</em> block to sit on, the {@link SeatGeometry} of the looked-at block when
 * {@code sit-on-blocks} is on and it is sittable, otherwise the player's own feet, and renders the outcome.
 *
 * <p>The command is a toggle: run it while already sitting and it stands the player back up, so the same word both
 * seats and unseats. Only a plain sit toggles; a player sitting <em>on another player</em> is a different session
 * type and falls through to {@link StartSit}, whose one-session invariant turns the attempt away.
 */
@NullMarked
public final class SitCommand implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.sit.use";

    private final StartSit startSit;
    private final StopPose stopPose;
    private final PoseSessions sessions;
    private final SittableBlocks sittableBlocks;
    private final CommandFeedback feedback;
    private final PoseCooldownNotice cooldownNotice;
    private final boolean sitOnBlocks;
    private final int reach;

    public SitCommand(
            StartSit startSit,
            StopPose stopPose,
            PoseSessions sessions,
            SittableBlocks sittableBlocks,
            Messages messages,
            PoseCooldownNotice cooldownNotice,
            boolean sitOnBlocks,
            double maxDistance) {
        this.startSit = Objects.requireNonNull(startSit, "startSit");
        this.stopPose = Objects.requireNonNull(stopPose, "stopPose");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.sittableBlocks = Objects.requireNonNull(sittableBlocks, "sittableBlocks");
        this.feedback = new CommandFeedback(Objects.requireNonNull(messages, "messages"));
        this.cooldownNotice = Objects.requireNonNull(cooldownNotice, "cooldownNotice");
        this.sitOnBlocks = sitOnBlocks;
        this.reach = Math.max(1, (int) Math.ceil(maxDistance));
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("sit")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::sit)
                .build();
    }

    @Override
    public String description() {
        return "Sit down where you stand or on the block you're looking at; run again to stand up.";
    }

    private int sit(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            feedback.send(sender, SharedMessageKey.COMMAND_PLAYERS_ONLY);
            return 0;
        }
        PlayerRef who = BukkitRefs.toRef(player);
        if (isSitting(who)) {
            // Toggle off: StopPose removes the seat entity, then confirm they stood back up.
            stopPose.stop(who);
            feedback.send(player, PosesMessageKey.POSES_STOOD_UP);
            return Command.SINGLE_SUCCESS;
        }
        Spot spot = resolveSpot(player);
        SitOutcome outcome = startSit.start(who, spot.position(), spot.yaw(), spot.inPlace());
        render(player, who, outcome);
        return Command.SINGLE_SUCCESS;
    }

    /** Whether the player already holds a plain sit, so running the command again stands them back up. */
    private boolean isSitting(PlayerRef who) {
        Optional<PoseSession> session = sessions.current(who);
        return session.isPresent() && session.get().type() == PoseType.SIT;
    }

    /** Where to sit: the looked-at sittable block when offered and in reach, otherwise the player's own feet. */
    private Spot resolveSpot(Player player) {
        Location location = Objects.requireNonNull(player.getLocation(), "player location");
        if (sitOnBlocks) {
            Block target = player.getTargetBlockExact(reach);
            if (target != null) {
                Optional<SeatTarget> seat = SeatGeometry.forBlock(target, sittableBlocks, location.getYaw());
                if (seat.isPresent()) {
                    return new Spot(seat.get().position(), seat.get().yaw(), false);
                }
            }
        }
        Position feet = BukkitRefs.toPosition(location);
        return new Spot(feet, feet.yaw(), true);
    }

    /** Render the outcome: the plain refusals resolve one catalog key; ON_COOLDOWN carries the remaining seconds. */
    private void render(Player player, PlayerRef who, SitOutcome outcome) {
        switch (outcome) {
            case STARTED -> feedback.send(player, PosesMessageKey.POSES_SITTING);
            case DISABLED -> feedback.send(player, PosesMessageKey.POSES_SIT_DISABLED);
            case DENIED_REGION -> feedback.send(player, PosesMessageKey.POSES_CANNOT_HERE);
            case ALREADY_POSING -> feedback.send(player, PosesMessageKey.POSES_ALREADY_POSING);
            case ON_COOLDOWN -> cooldownNotice.send(player, who);
        }
    }

    private record Spot(Position position, float yaw, boolean inPlace) {}
}

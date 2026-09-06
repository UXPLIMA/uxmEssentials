package com.uxplima.uxmessentials.poses.adapter.inbound.command;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.poses.application.PoseSessions;
import com.uxplima.uxmessentials.poses.application.PosesMessageKey;
import com.uxplima.uxmessentials.poses.application.StartPose;
import com.uxplima.uxmessentials.poses.application.StartPose.PoseOutcome;
import com.uxplima.uxmessentials.poses.application.StopPose;
import com.uxplima.uxmessentials.poses.domain.PoseSession;
import com.uxplima.uxmessentials.poses.domain.PoseType;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.NullMarked;

/**
 * One free-pose command ({@code /lay}, {@code /bellyflop}, or {@code /spin}) gated on its own
 * {@code uxmessentials.<pose>.use} node. A free pose is always struck where the player stands, so this handler
 * resolves nothing but the player's own feet and hands the pose to {@link StartPose}, which owns the feature gate,
 * the region gate, the one-session invariant, and the anchor. The three commands share this class, each constructed
 * with its literal, permission, {@link PoseType}, and the feedback line for a successful start.
 *
 * <p>Each command is a toggle, the way {@code /crawl} already is: run while already holding <em>this</em> pose and
 * it ends, so the same word that lies you down stands you back up. A player holding a <em>different</em> pose still
 * falls through to {@link StartPose}, which turns the attempt away with its one-session invariant rather than
 * silently swapping one pose for another.
 */
@NullMarked
public final class PoseCommand implements CommandRegistration {

    private final String literal;
    private final String permission;
    private final PoseType pose;
    private final MessageKey startedKey;
    private final String description;
    private final StartPose startPose;
    private final StopPose stopPose;
    private final PoseSessions sessions;
    private final CommandFeedback feedback;
    private final PoseCooldownNotice cooldownNotice;

    public PoseCommand(
            String literal,
            String permission,
            PoseType pose,
            MessageKey startedKey,
            String description,
            StartPose startPose,
            StopPose stopPose,
            PoseSessions sessions,
            Messages messages,
            PoseCooldownNotice cooldownNotice) {
        this.literal = Objects.requireNonNull(literal, "literal");
        this.permission = Objects.requireNonNull(permission, "permission");
        this.pose = Objects.requireNonNull(pose, "pose");
        this.startedKey = Objects.requireNonNull(startedKey, "startedKey");
        this.description = Objects.requireNonNull(description, "description");
        this.startPose = Objects.requireNonNull(startPose, "startPose");
        this.stopPose = Objects.requireNonNull(stopPose, "stopPose");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.feedback = new CommandFeedback(Objects.requireNonNull(messages, "messages"));
        this.cooldownNotice = Objects.requireNonNull(cooldownNotice, "cooldownNotice");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(literal)
                .requires(src -> src.getSender().hasPermission(permission))
                .executes(this::run)
                .build();
    }

    @Override
    public String description() {
        return description;
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player player)) {
            feedback.send(sender, SharedMessageKey.COMMAND_PLAYERS_ONLY);
            return 0;
        }
        PlayerRef who = BukkitRefs.toRef(player);
        if (holdsThisPose(who)) {
            // Toggle off: StopPose removes the seat and clears the render, then confirm they stood back up.
            stopPose.stop(who);
            feedback.send(player, PosesMessageKey.POSES_STOOD_UP);
            return Command.SINGLE_SUCCESS;
        }
        Location location = Objects.requireNonNull(player.getLocation(), "player location");
        Position feet = BukkitRefs.toPosition(location);
        PoseOutcome outcome = startPose.start(who, pose, feet, feet.yaw());
        render(player, who, outcome);
        return Command.SINGLE_SUCCESS;
    }

    /** Whether the player is already holding the very pose this command strikes, so running it again ends it. */
    private boolean holdsThisPose(PlayerRef who) {
        Optional<PoseSession> session = sessions.current(who);
        return session.isPresent() && session.get().type() == pose;
    }

    /** Render the outcome: the plain lines resolve one catalog key; ON_COOLDOWN carries the remaining seconds. */
    private void render(Player player, PlayerRef who, PoseOutcome outcome) {
        switch (outcome) {
            case STARTED -> feedback.send(player, startedKey);
            case DISABLED -> feedback.send(player, PosesMessageKey.POSES_POSE_DISABLED);
            case DENIED_REGION -> feedback.send(player, PosesMessageKey.POSES_CANNOT_HERE);
            case ALREADY_POSING -> feedback.send(player, PosesMessageKey.POSES_ALREADY_POSING);
            case ON_COOLDOWN -> cooldownNotice.send(player, who);
        }
    }
}

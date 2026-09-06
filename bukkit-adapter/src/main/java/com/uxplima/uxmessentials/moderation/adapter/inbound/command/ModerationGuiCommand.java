package com.uxplima.uxmessentials.moderation.adapter.inbound.command;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.moderation.adapter.ModerationServices;
import com.uxplima.uxmessentials.moderation.adapter.inbound.gui.ModerationGuiViews;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /mod}: open the moderation management GUI. The active-punishments list an admin drills into to view a
 * punishment's detail, revoke it (confirm-gated), and inspect a target's history. A players-only command gated
 * by {@code uxmessentials.moderation.gui} (default op); it opens the same {@link ModerationGuiViews} entry the
 * {@code /uxmess gui} hub does. It issues no sanction itself. Every punishment action routes through the
 * existing moderation use cases the views call.
 */
@NullMarked
public final class ModerationGuiCommand extends ModerationCommandSupport implements CommandRegistration {

    static final String PERMISSION = "uxmessentials.moderation.gui";

    private final ModerationGuiViews views;

    public ModerationGuiCommand(
            ModerationServices services, Messages messages, MessageSink sink, ModerationGuiViews views) {
        super(services, messages, sink);
        this.views = java.util.Objects.requireNonNull(views, "views");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("mod")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::open)
                .build();
    }

    @Override
    public String description() {
        return "Open the moderation management GUI (active punishments + per-player history).";
    }

    private int open(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        views.open(sender, BukkitRefs.toRef(sender));
        return Command.SINGLE_SUCCESS;
    }
}

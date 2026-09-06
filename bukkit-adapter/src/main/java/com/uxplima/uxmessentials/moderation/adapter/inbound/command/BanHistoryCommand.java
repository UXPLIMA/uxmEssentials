package com.uxplima.uxmessentials.moderation.adapter.inbound.command;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.moderation.adapter.ModerationServices;
import com.uxplima.uxmessentials.moderation.adapter.inbound.gui.ModerationGuiViews;
import com.uxplima.uxmessentials.moderation.application.ModerationMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.PlayerPickerView;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * {@code /banhistory <player>}: review a player's full ban/unban history newest-first. A read-only companion
 * to the ban family: the {@code ReviewBanHistory} use case runs the bounded, append-only history query. It
 * reuses the {@code uxmessentials.moderation.ban} node ({@code /ban}/{@code /unban}), the same surface staff
 * already hold to act on bans. The lookup is hopped off the tick thread through the {@link Scheduler} port so
 * a large history table never blocks the command.
 *
 * <p>Bare {@code /banhistory} (no arguments) opens a player picker when the command's catalog {@code gui} flag is
 * on: {@link #guiRoot()} returns the opener and the shared {@code GuiRootBinding} installs it as the root
 * executor. Picking a player opens that target's read-only history through the same {@link ModerationGuiViews}
 * history view the {@code /mod} detail screen drills into. The picker's offline-name resolver is the moderation
 * {@code TargetResolver}, so a typed offline name still resolves; an unknown typed name gets the shared
 * unknown-target reply. The raw {@code /banhistory <player>} chat form is unchanged either way, and the same
 * {@code .requires} permission gate covers the bare-root opener.
 */
@NullMarked
public final class BanHistoryCommand extends ModerationCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.moderation.ban";

    private final Scheduler scheduler;
    private final @Nullable PlayerPickerView picker;
    private final @Nullable ModerationGuiViews views;

    public BanHistoryCommand(
            ModerationServices services,
            Messages messages,
            MessageSink sink,
            Scheduler scheduler,
            @Nullable PlayerPickerView picker,
            @Nullable ModerationGuiViews views) {
        super(services, messages, sink);
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.picker = picker;
        this.views = views;
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("banhistory")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(CommandSuggestions.playerArgument("player").executes(this::run))
                .build();
    }

    @Override
    public String description() {
        return "Review a player's ban history.";
    }

    @Override
    public Optional<Command<CommandSourceStack>> guiRoot() {
        if (picker == null || views == null) {
            return Optional.empty();
        }
        return Optional.of(ctx -> {
            if (ctx.getSource().getSender() instanceof Player sender) {
                PlayerRef viewer = BukkitRefs.toRef(sender);
                picker.open(sender, viewer, pickRequest(sender, viewer));
            }
            return Command.SINGLE_SUCCESS;
        });
    }

    /** The picker request: a chosen target opens that player's read-only history view. */
    private PlayerPickerView.Request pickRequest(Player sender, PlayerRef viewer) {
        return new PlayerPickerView.Request(
                ModerationMessageKey.MOD_GUI_PICK_HISTORY_TITLE,
                target -> Objects.requireNonNull(views).openHistory(sender, viewer, target),
                name -> services.targets().resolve(name),
                ModerationMessageKey.UNKNOWN_TARGET);
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        PlayerRef actor = actor(ctx);
        Optional<PlayerRef> target = targetByName(ctx, ctx.getArgument("player", String.class));
        target.ifPresent(to -> scheduler.async(() -> services.reviewBanHistory().review(actor, to)));
        return Command.SINGLE_SUCCESS;
    }
}

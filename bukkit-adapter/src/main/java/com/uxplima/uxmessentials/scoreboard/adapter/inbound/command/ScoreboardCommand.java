package com.uxplima.uxmessentials.scoreboard.adapter.inbound.command;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.scoreboard.adapter.inbound.gui.ScoreboardSettingsView;
import com.uxplima.uxmessentials.scoreboard.adapter.outbound.ScoreboardRenderer;
import com.uxplima.uxmessentials.scoreboard.application.ScoreboardMessageKey;
import com.uxplima.uxmessentials.scoreboard.application.ToggleScoreboard;
import com.uxplima.uxmessentials.scoreboard.application.port.ScoreboardVisibilityStore;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * {@code /scoreboard} (alias {@code /sb}, {@code uxmessentials.scoreboard.use}): flip whether the invoking player
 * sees the scoreboard display. The {@link ToggleScoreboard} use case owns the PDC-backed bit flip, the confirmation
 * (one of the plugin's own {@link ScoreboardMessageKey} strings through the {@code MessageSink}), and the toggled
 * domain event; this handler maps the invoking player and then immediately renders or clears their board based on the
 * returned hidden flag, so the player sees the change at once rather than after a refresh tick. The {@code gui}
 * subcommand opens the settings panel that carries the same toggle, gated on {@code uxmessentials.scoreboard.gui}.
 *
 * <p>A console source gets the players-only rejection: a board belongs to a live player. The render/clear hop runs
 * on the player's entity thread because it touches the live scoreboard.
 */
@NullMarked
public final class ScoreboardCommand
        implements com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration {

    private static final String PERMISSION = "uxmessentials.scoreboard.use";
    private static final String GUI_PERMISSION = "uxmessentials.scoreboard.gui";

    private final ToggleScoreboard toggle;
    private final ScoreboardRenderer renderer;
    private final Scheduler scheduler;
    private final CommandFeedback feedback;
    private final ScoreboardSettingsView view;
    private final ScoreboardVisibilityStore visibility;

    public ScoreboardCommand(
            ToggleScoreboard toggle,
            ScoreboardRenderer renderer,
            Scheduler scheduler,
            Messages messages,
            ScoreboardSettingsView view,
            ScoreboardVisibilityStore visibility) {
        this.toggle = Objects.requireNonNull(toggle, "toggle");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.feedback = new CommandFeedback(Objects.requireNonNull(messages, "messages"));
        this.view = Objects.requireNonNull(view, "view");
        this.visibility = Objects.requireNonNull(visibility, "visibility");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("scoreboard")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.literal("gui")
                        .requires(src -> src.getSender().hasPermission(GUI_PERMISSION))
                        .executes(this::openGui))
                .then(Commands.literal("toggle").executes(this::run))
                .then(Commands.literal("on").executes(ctx -> set(ctx, false)))
                .then(Commands.literal("off").executes(ctx -> set(ctx, true)))
                .then(Commands.literal("refresh").executes(this::refresh))
                .executes(this::run)
                .build();
    }

    private int openGui(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        view.open(sender, BukkitRefs.toRef(sender));
        return Command.SINGLE_SUCCESS;
    }

    @Override
    public String description() {
        return "Toggle whether you see the scoreboard display.";
    }

    @Override
    public List<String> aliases() {
        return List.of("sb");
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef who = BukkitRefs.toRef(sender);
        boolean hidden = toggle.toggle(who);
        scheduler.onEntity(who, () -> apply(sender, hidden));
        return Command.SINGLE_SUCCESS;
    }

    private int set(CommandContext<CommandSourceStack> ctx, boolean hidden) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef who = BukkitRefs.toRef(sender);
        boolean current = visibility.hidden(who);
        if (current != hidden) {
            hidden = toggle.toggle(who);
        }
        boolean target = hidden;
        scheduler.onEntity(who, () -> apply(sender, target));
        return Command.SINGLE_SUCCESS;
    }

    private int refresh(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef who = BukkitRefs.toRef(sender);
        scheduler.onEntity(who, () -> renderer.renderFor(sender));
        return Command.SINGLE_SUCCESS;
    }

    private void apply(Player player, boolean hidden) {
        if (hidden) {
            renderer.clear(player);
        } else {
            renderer.renderFor(player);
        }
    }

    private @Nullable Player player(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (sender instanceof Player player) {
            return player;
        }
        feedback.send(sender, ScoreboardMessageKey.SCOREBOARD_PLAYERS_ONLY, Map.of());
        return null;
    }
}

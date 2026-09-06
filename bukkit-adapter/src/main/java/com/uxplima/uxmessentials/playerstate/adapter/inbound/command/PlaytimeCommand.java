package com.uxplima.uxmessentials.playerstate.adapter.inbound.command;

import java.util.Optional;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.playerstate.adapter.PlayerStateServices;
import com.uxplima.uxmessentials.playerstate.adapter.inbound.gui.PlaytimeView;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * {@code /playtime [player]} ({@code uxmessentials.playtime.use}): show a player's playtime breakdown, active
 * (non-AFK) and AFK time across today / last 7 days / last 30 days / all time, read from the DB-backed
 * {@code ShowPlaytime} use case, plus a lifetime continuity line. The target is a plain online-player name (an
 * online-name-completing word, never an {@code @a}/{@code @p}/{@code @s} selector): showing one player's stats is
 * a single-target read where a fan-out to every player is nonsensical. The {@code .others} target is gated by the
 * shared {@code uxmessentials.playtime.others} (or the cross-cutting {@code uxmessentials.playerstate.others}) node.
 *
 * <p>The breakdown can show as a GUI panel or as the chat lines. Bare {@code /playtime} opens the viewer's own
 * panel through {@link #guiRoot()} when the command's catalog {@code gui} flag is on, and renders the chat
 * {@code ShowPlaytime} on its own bare-root executor when it is off, so the catalog flag toggles the self view
 * exactly as the shared framework intends. {@code /playtime <name>} opens that player's panel (the richer view of
 * the same data, mirroring how {@code /warp <name>} opens its editor unconditionally). With no GUI wired every
 * form renders the chat breakdown.
 *
 * <p>{@code /playtime reset [player]} ({@code uxmessentials.playtime.reset}): wipe a player's tracked playtime.
 * The reset capability itself is the {@code uxmessentials.playtime.reset} admin node (resetting even your own
 * tracked time is an administrative action, off by default); resetting <em>another</em> player additionally
 * requires the shared {@code uxmessentials.playtime.others} (or the cross-cutting {@code uxmessentials.playerstate.others}) node, the same target gate every other
 * self/other playerstate command uses.
 *
 * <p>{@code /playtime resetall} ({@code uxmessentials.playtime.reset}): wipe every player's tracked ledger in one
 * call, gated by the same reset node, with a whole-ledger confirmation.
 */
@NullMarked
public final class PlaytimeCommand extends PlayerstateCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.playtime.use";
    private static final String RESET_PERMISSION = "uxmessentials.playtime.reset";

    private final @Nullable PlaytimeView view;

    public PlaytimeCommand(PlayerStateServices services, Messages messages) {
        this(services, messages, null);
    }

    public PlaytimeCommand(PlayerStateServices services, Messages messages, @Nullable PlaytimeView view) {
        super(services, messages);
        this.view = view;
    }

    /** Targeting somebody else takes this node, or the cross-cutting playerstate one. */
    @Override
    String othersNode() {
        return "uxmessentials.playtime.others";
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("playtime")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::showSelfChat)
                .then(Commands.literal("reset")
                        .requires(src -> src.getSender().hasPermission(RESET_PERMISSION))
                        .executes(this::reset)
                        .then(CommandSuggestions.playerArgument("player").executes(this::reset)))
                .then(Commands.literal("resetall")
                        .requires(src -> src.getSender().hasPermission(RESET_PERMISSION))
                        .executes(this::resetAll))
                .then(CommandSuggestions.playerArgument("player").executes(this::showNamed))
                .build();
    }

    @Override
    public String description() {
        return "Show or reset a player's playtime breakdown.";
    }

    /**
     * Bare {@code /playtime} opens the viewer's own breakdown panel when the catalog {@code gui} flag is on;
     * with it off, the bare root keeps its chat {@code show} executor. With no GUI wired the opener is absent, so
     * the flag has no effect and the bare root renders the chat breakdown.
     */
    @Override
    public Optional<Command<CommandSourceStack>> guiRoot() {
        if (view == null) {
            return Optional.empty();
        }
        return Optional.of(ctx -> {
            if (ctx.getSource().getSender() instanceof Player sender) {
                PlayerRef viewer = ref(sender);
                view.open(sender, viewer, viewer);
            }
            return Command.SINGLE_SUCCESS;
        });
    }

    /**
     * The bare-root chat fallback: render the viewer's own breakdown as the chat lines. Installed on the bare root
     * directly, so it runs only when the catalog {@code gui} flag is off (gui-on swaps in the {@link #guiRoot()}
     * panel opener instead) or when no GUI is wired at all.
     */
    private int showSelfChat(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.showPlaytime().showFor(ref(sender), ref(sender));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * {@code /playtime <name>}: open the named player's panel when a GUI is wired (the richer view of the same
     * data), or render their chat breakdown otherwise. The named form is the staff inspection path, gated by the
     * shared {@code .others} node inside {@link #resolveNamedTarget}.
     */
    private int showNamed(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Optional<PlayerRef> target = resolveNamedTarget(ctx, sender);
        if (target.isEmpty()) {
            return 0;
        }
        PlayerRef viewer = actor(ctx);
        if (view != null && sender instanceof Player player) {
            view.open(player, viewer, target.get());
        } else {
            services.showPlaytime().showFor(viewer, target.get());
        }
        return Command.SINGLE_SUCCESS;
    }

    private int reset(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        Optional<PlayerRef> target = resolveNamedTarget(ctx, sender);
        if (target.isEmpty()) {
            return 0;
        }
        services.resetPlaytime().resetFor(actor(ctx), target.get());
        return Command.SINGLE_SUCCESS;
    }

    private int resetAll(CommandContext<CommandSourceStack> ctx) {
        services.resetPlaytime().resetAll(actor(ctx));
        return Command.SINGLE_SUCCESS;
    }
}

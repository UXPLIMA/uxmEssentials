package com.uxplima.uxmessentials.moderation.adapter.inbound.command;

import java.util.Optional;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.moderation.adapter.ModerationServices;
import com.uxplima.uxmessentials.moderation.adapter.inbound.gui.JailGuiViews;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * {@code /jail <player> <jail> [duration] [reason]}: confine a player to a named jail. With no duration the
 * jail is permanent; with one it is timed (online-only by default, wall-clock per {@code moderation.conf}).
 * The unknown-jail / exempt / duration gating and the audit line are the {@code Jail} use case's job; this
 * handler maps the name, the jail, the optional duration token and the greedy reason. The target may be
 * offline (offline jail re-applied at the next login).
 *
 * <p>{@code /jail del <name>} removes a stored jail, freeing its name, the folded-in counterpart to
 * {@code /jail <player> <jail> ...}, mirroring how {@code /warp del} sits under {@code /warp}. Only a stored
 * jail can be removed this way; a config-defined jail name lives in {@code moderation.conf}, not the store. The
 * not-found feedback and the confirmation are the {@link com.uxplima.uxmessentials.moderation.application.DelJail}
 * use case's job. A console source is allowed since no position is captured.
 *
 * <p>Bare {@code /jail} (no arguments) opens the jail management hub, the player picker → jail chooser →
 * duration flow, with footer buttons into the jail-list manager and the jailed-players release list, when the
 * command's catalog {@code gui} flag is on: {@link #guiRoot()} returns the opener and the shared
 * {@code GuiRootBinding} installs it as the root executor. The raw {@code /jail <player> <jail> ...} subcommand
 * is unchanged either way, and the same {@code .requires} gate covers the bare-root opener.
 */
@NullMarked
public final class JailCommand extends ModerationCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.moderation.jail";

    private final @Nullable JailGuiViews jailGui;

    public JailCommand(
            ModerationServices services, Messages messages, MessageSink sink, @Nullable JailGuiViews jailGui) {
        super(services, messages, sink);
        this.jailGui = jailGui;
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("jail")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(Commands.literal("del")
                        .requires(src -> src.getSender().hasPermission(PERMISSION))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests(CommandSuggestions.fromStrings(
                                        () -> services.listJails().suggestionNames()))
                                .executes(this::runDelete)))
                .then(CommandSuggestions.playerArgument("player")
                        .then(Commands.argument("jail", StringArgumentType.word())
                                .suggests(CommandSuggestions.fromStrings(
                                        () -> services.listJails().suggestionNames()))
                                .executes(ctx -> run(ctx, "", Optional.empty()))
                                .then(Commands.argument("duration", StringArgumentType.word())
                                        .executes(ctx ->
                                                run(ctx, ctx.getArgument("duration", String.class), Optional.empty()))
                                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                                .executes(ctx -> run(
                                                        ctx,
                                                        ctx.getArgument("duration", String.class),
                                                        optionalReason(ctx)))))))
                .build();
    }

    @Override
    public String description() {
        return "Confine a player to a jail, optionally for a duration.";
    }

    @Override
    public Optional<Command<CommandSourceStack>> guiRoot() {
        if (jailGui == null) {
            return Optional.empty();
        }
        return Optional.of(ctx -> {
            if (ctx.getSource().getSender() instanceof Player sender) {
                jailGui.openHub(sender, BukkitRefs.toRef(sender));
            }
            return Command.SINGLE_SUCCESS;
        });
    }

    private int run(CommandContext<CommandSourceStack> ctx, String duration, Optional<String> reason) {
        PlayerRef actor = actor(ctx);
        String jail = ctx.getArgument("jail", String.class);
        Optional<PlayerRef> target = targetByName(ctx, ctx.getArgument("player", String.class));
        target.ifPresent(to -> services.jail().jail(actor, to, jail, duration, reason));
        return Command.SINGLE_SUCCESS;
    }

    private int runDelete(CommandContext<CommandSourceStack> ctx) {
        services.delJail().delete(actor(ctx), ctx.getArgument("name", String.class));
        return Command.SINGLE_SUCCESS;
    }
}

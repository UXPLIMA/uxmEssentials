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
import com.uxplima.uxmessentials.moderation.adapter.inbound.gui.PunishmentAction;
import com.uxplima.uxmessentials.moderation.adapter.inbound.gui.PunishmentGuiFlow;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * {@code /ban <player> [-s] [reason]}: a permanent UUID ban. Unlike {@code /tempban} there is no duration
 * argument. The {@code Ban} use case records a far-future tempban row, kicks an online target immediately and
 * the login listener bars reconnection. This handler maps the name and the greedy reason; a leading {@code -s}
 * in the reason suppresses the staff broadcast. An exempt target or an unknown name is reported by the use case
 * and the resolver respectively.
 *
 * <p>Bare {@code /ban} (no arguments) opens the player-picker → confirm GUI flow when the command's catalog
 * {@code gui} flag is on: {@link #guiRoot()} returns the opener and the shared {@code GuiRootBinding} installs it
 * as the root executor. The raw subcommand form is unchanged either way, and the same {@code .requires} permission
 * gate covers the bare-root opener so a non-holder cannot open the picker.
 */
@NullMarked
public final class BanCommand extends ModerationCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.moderation.ban";

    private final boolean silentByDefault;
    private final @Nullable PunishmentGuiFlow guiFlow;

    public BanCommand(
            ModerationServices services,
            Messages messages,
            MessageSink sink,
            boolean silentByDefault,
            @Nullable PunishmentGuiFlow guiFlow) {
        super(services, messages, sink);
        this.silentByDefault = silentByDefault;
        this.guiFlow = guiFlow;
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("ban")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(CommandSuggestions.playerArgument("player")
                        .executes(ctx -> run(ctx, Optional.empty()))
                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                .executes(ctx -> run(ctx, optionalReason(ctx)))))
                .build();
    }

    @Override
    public String description() {
        return "Permanently ban a player (prefix the reason with -s to ban silently).";
    }

    @Override
    public Optional<Command<CommandSourceStack>> guiRoot() {
        if (guiFlow == null) {
            return Optional.empty();
        }
        return Optional.of(ctx -> {
            if (ctx.getSource().getSender() instanceof Player sender) {
                guiFlow.open(sender, BukkitRefs.toRef(sender), PunishmentAction.BAN);
            }
            return Command.SINGLE_SUCCESS;
        });
    }

    private int run(CommandContext<CommandSourceStack> ctx, Optional<String> reason) {
        PlayerRef actor = actor(ctx);
        SilentReason parsed = silentReason(reason, silentByDefault);
        Optional<PlayerRef> target = targetByName(ctx, ctx.getArgument("player", String.class));
        target.ifPresent(to -> services.ban().ban(actor, to, parsed.reason(), parsed.silent()));
        return Command.SINGLE_SUCCESS;
    }
}

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
import com.uxplima.uxmessentials.moderation.application.ModerationMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * {@code /checkban <player>}: report whether a player is currently banned, and if so the issuer, reason and
 * expiry of the active ban. Where {@code /banhistory} lists the full record, this answers the single "is this
 * player banned right now?" question staff ask before a fresh sanction. The {@code CheckBan} use case runs the
 * read against the DB-backed sanction store. It shares the {@code uxmessentials.moderation.check} node with
 * {@code /checkmute}. The lookup is hopped off the tick thread through the {@link Scheduler} port; the target
 * resolves online-first, then from the profile cache, so an offline player's ban state is still checkable.
 *
 * <p>Bare {@code /checkban} (no arguments) prompts for a player name through the shared text-input seam when the
 * command's catalog {@code gui} flag is on; a submitted name resolves through the {@code TargetResolver} and runs the
 * same {@code CheckBan.show} chat output the raw form does. The prompt captures the name only, the result is chat,
 * not a GUI. The raw {@code /checkban <player>} child is unchanged either way, and the same {@code .requires}
 * permission gate covers the bare-root opener.
 */
@NullMarked
public final class CheckBanCommand extends ModerationCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.moderation.check";
    private static final String INPUT_KEY = "moderation.check-ban";

    private final Scheduler scheduler;
    private final @Nullable CheckTargetPrompt prompt;

    public CheckBanCommand(
            ModerationServices services,
            Messages messages,
            MessageSink sink,
            Scheduler scheduler,
            @Nullable GuiText guiText,
            @Nullable TextInput textInput) {
        super(services, messages, sink);
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.prompt = (guiText == null || textInput == null)
                ? null
                : new CheckTargetPrompt(
                        services,
                        textInput,
                        scheduler,
                        messages,
                        sink,
                        INPUT_KEY,
                        ModerationMessageKey.MOD_GUI_CHECK_BAN_PROMPT,
                        (actor, target) -> services.checkBan().show(actor, target));
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("checkban")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(CommandSuggestions.playerArgument("player").executes(this::run))
                .build();
    }

    @Override
    public String description() {
        return "Check whether a player is banned.";
    }

    @Override
    public Optional<Command<CommandSourceStack>> guiRoot() {
        if (prompt == null) {
            return Optional.empty();
        }
        return Optional.of(ctx -> {
            if (ctx.getSource().getSender() instanceof Player sender) {
                prompt.open(sender);
            }
            return Command.SINGLE_SUCCESS;
        });
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        PlayerRef actor = actor(ctx);
        Optional<PlayerRef> target = targetByName(ctx, ctx.getArgument("player", String.class));
        target.ifPresent(to -> scheduler.async(() -> services.checkBan().show(actor, to)));
        return Command.SINGLE_SUCCESS;
    }
}

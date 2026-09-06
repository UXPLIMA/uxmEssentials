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
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * {@code /banlist}: list the players currently banned. A read-only companion to the ban family, the
 * {@code ListBans} use case runs the bounded DB query and renders the page. The query is hopped off the tick
 * thread through the {@link Scheduler} port so a large ban table never blocks the command; the use case's
 * notifier hops each reply back to the actor's region thread.
 *
 * <p>Bare {@code /banlist} (no arguments) opens the active-punishments GUI when the command's catalog {@code gui}
 * flag is on: {@link #guiRoot()} returns the opener and the shared {@code GuiRootBinding} installs it as the root
 * executor, replacing the chat-list executor. {@code ActivePunishmentsView} lists active bans, mutes and jails
 * together with no bans-only filter, so the bare GUI is the full active-punishments list (the same screen
 * {@code /mod} opens); the raw chat output stays bans-only. With gui off the chat list executor is left in place.
 */
@NullMarked
public final class BanListCommand extends ModerationCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.moderation.banlist";

    private final Scheduler scheduler;
    private final @Nullable ModerationGuiViews views;

    public BanListCommand(
            ModerationServices services,
            Messages messages,
            MessageSink sink,
            Scheduler scheduler,
            @Nullable ModerationGuiViews views) {
        super(services, messages, sink);
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.views = views;
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("banlist")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::run)
                .build();
    }

    @Override
    public String description() {
        return "Review currently banned players.";
    }

    @Override
    public Optional<Command<CommandSourceStack>> guiRoot() {
        if (views == null) {
            return Optional.empty();
        }
        return Optional.of(ctx -> {
            if (ctx.getSource().getSender() instanceof Player sender) {
                views.open(sender, BukkitRefs.toRef(sender));
            }
            return Command.SINGLE_SUCCESS;
        });
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        PlayerRef actor = actor(ctx);
        scheduler.async(() -> services.listBans().list(actor));
        return Command.SINGLE_SUCCESS;
    }
}

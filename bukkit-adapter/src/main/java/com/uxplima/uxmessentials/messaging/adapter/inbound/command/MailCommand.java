package com.uxplima.uxmessentials.messaging.adapter.inbound.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.messaging.adapter.MessagingServices;
import com.uxplima.uxmessentials.messaging.adapter.inbound.gui.MessagingGuiViews;
import com.uxplima.uxmessentials.messaging.application.MessagingMessageKey;
import com.uxplima.uxmessentials.messaging.domain.MessageBody;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /mail <read|send|sendall|clear>}: the persistent-mailbox surface. {@code read} renders the mailbox in
 * chat and marks it read, {@code send <player> <text>} leaves a piece of mail (offline delivery, mute-gated,
 * ignore-aware), {@code sendall <text>} broadcasts mail to every online player (a staff/operator action
 * behind {@code uxmessentials.mail.sendall}), and {@code clear} empties the box. Each sub-command maps to one
 * use case; the bare {@code /mail} opens the mailbox GUI (which marks the box read on open), while
 * {@code /mail read} keeps the chat rendering. Mail is text-only: there are no item attachments. The
 * {@code send} target is resolved by name (mail to an offline player is valid and waits for them, so this is a
 * plain online-or-offline lookup, not the vanish-aware online-only resolution {@code /msg} uses).
 *
 * <p><strong>{@code sendall} recipient scope:</strong> v1 broadcasts to the currently-online roster only. The
 * recipient set is snapshotted on the tick thread (the only safe place to read {@code getOnlinePlayers}) and
 * the durable-mail fan-out then runs off-tick through the {@code Scheduler.async} port. A broadcast is a
 * bounded DB write per recipient and never blocks the tick. Mailing every known mailbox owner (offline
 * profiles included) is a deliberate future enhancement, not v1.
 */
@NullMarked
public final class MailCommand extends MessagingCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.mail.use";
    private static final String SENDALL_PERMISSION = "uxmessentials.mail.sendall";

    private final MessagingGuiViews views;

    public MailCommand(MessagingServices services, Messages messages, MessageSink sink, MessagingGuiViews views) {
        super(services, messages, sink);
        this.views = Objects.requireNonNull(views, "views");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("mail")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::openGui)
                .then(Commands.literal("read").executes(this::read))
                .then(Commands.literal("clear").executes(this::clear))
                .then(Commands.literal("send")
                        .then(CommandSuggestions.playerArgument("player")
                                .then(Commands.argument("text", StringArgumentType.greedyString())
                                        .executes(this::send))))
                .then(Commands.literal("sendall")
                        .requires(src -> src.getSender().hasPermission(SENDALL_PERMISSION))
                        .then(Commands.argument("text", StringArgumentType.greedyString())
                                .executes(this::sendAll)))
                .build();
    }

    @Override
    public String description() {
        return "Open your mailbox, or read, send or clear your persistent mail.";
    }

    private int openGui(CommandContext<CommandSourceStack> ctx) {
        Player reader = player(ctx);
        if (reader == null) {
            return 0;
        }
        views.openMailbox(reader, ref(reader));
        return Command.SINGLE_SUCCESS;
    }

    private int read(CommandContext<CommandSourceStack> ctx) {
        Player reader = player(ctx);
        if (reader != null) {
            services.readMail().read(ref(reader));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int clear(CommandContext<CommandSourceStack> ctx) {
        Player owner = player(ctx);
        if (owner != null) {
            services.clearMail().clear(ref(owner));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int send(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        MessageBody text = body(sender, ctx.getArgument("text", String.class));
        if (text == null) {
            return 0;
        }
        PlayerRef from = ref(sender);
        String name = ctx.getArgument("player", String.class);
        Optional<PlayerRef> recipient = services.players().findOnlineByName(name);
        if (recipient.isEmpty()) {
            notify(from, UNKNOWN_PLAYER, Map.of("player", name));
            return 0;
        }
        services.sendMail().send(from, recipient.get(), text);
        return Command.SINGLE_SUCCESS;
    }

    private int sendAll(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        MessageBody text = body(sender, ctx.getArgument("text", String.class));
        if (text == null) {
            return 0;
        }
        PlayerRef from = ref(sender);
        // Snapshot the online roster here, on the global region thread Paper dispatches this Brigadier handler on
        // the one thread where Bukkit.getOnlinePlayers() is consistently readable on Folia, then fan the durable
        // per-recipient mail writes off-tick so the broadcast never blocks the tick.
        List<PlayerRef> recipients = onlineRecipients();
        services.scheduler().async(() -> {
            int stored = services.sendMailToAll().sendToAll(from, text, recipients);
            notify(from, MessagingMessageKey.MAIL_SENDALL_DONE, Map.of("count", Integer.toString(stored)));
        });
        return Command.SINGLE_SUCCESS;
    }

    private static List<PlayerRef> onlineRecipients() {
        List<PlayerRef> recipients = new ArrayList<>();
        for (Player online : Bukkit.getOnlinePlayers()) {
            recipients.add(BukkitRefs.toRef(online));
        }
        return List.copyOf(recipients);
    }
}

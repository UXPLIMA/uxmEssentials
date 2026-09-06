package com.uxplima.uxmessentials.communication.adapter.inbound.command;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.communication.application.CommunicationMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /clearchat} (alias {@code /chatclear}): flush the visible chat for online players by pushing a
 * screenful of blank lines, the staff chat-clear verb. A staff member with
 * {@code uxmessentials.communication.clearchat.exempt} keeps their scrollback (the blank lines skip them), so
 * an admin can audit what was on screen. Stateless, no DB and no persistence.
 *
 * <p>The online roster is enumerated on the global region thread (Folia forbids iterating
 * {@code Bukkit.getOnlinePlayers()} off it); the blank lines and the confirmations then flow through the
 * {@link MessageSink} / {@link Notifier}, which hop to each viewer's region thread inside the sink, so
 * the fan-out never touches a Bukkit scheduler. The flushed players get a
 * {@link CommunicationMessageKey#CLEARCHAT_CLEARED} notice and the actor a
 * {@link CommunicationMessageKey#CLEARCHAT_BY} confirmation carrying their name.
 */
@NullMarked
public final class ClearChatCommand extends CommunicationCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.communication.clearchat";
    private static final String EXEMPT_PERMISSION = "uxmessentials.communication.clearchat.exempt";
    private static final int BLANK_LINES = 100;
    private static final String BLANK_LINE = "";

    private final Notifier notifier;
    private final MessageSink sink;
    private final Scheduler scheduler;

    public ClearChatCommand(Messages messages, Notifier notifier, MessageSink sink, Scheduler scheduler) {
        super(messages);
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("clearchat")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::run)
                .build();
    }

    @Override
    public String description() {
        return "Flush the chat for online players.";
    }

    @Override
    public List<String> aliases() {
        return List.of("chatclear");
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        String actor = ctx.getSource().getSender().getName();
        scheduler.onGlobal(() -> {
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (!viewer.hasPermission(EXEMPT_PERMISSION)) {
                    flush(BukkitRefs.toRef(viewer));
                }
            }
        });
        // Console may run /clearchat as an operator action; only a player actor gets the chat confirmation.
        if (ctx.getSource().getSender() instanceof Player sender) {
            notifier.send(ref(sender), CommunicationMessageKey.CLEARCHAT_BY, Map.of("player", actor));
        }
        return Command.SINGLE_SUCCESS;
    }

    private void flush(PlayerRef viewer) {
        for (int line = 0; line < BLANK_LINES; line++) {
            sink.deliver(viewer, BLANK_LINE);
        }
        notifier.send(viewer, CommunicationMessageKey.CLEARCHAT_CLEARED);
    }
}

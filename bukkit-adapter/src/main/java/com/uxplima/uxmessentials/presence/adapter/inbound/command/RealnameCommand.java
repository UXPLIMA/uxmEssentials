package com.uxplima.uxmessentials.presence.adapter.inbound.command;

import java.util.List;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.presence.adapter.PresenceServices;
import com.uxplima.uxmessentials.presence.application.PresenceMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * {@code /realname <player>} ({@code uxmessentials.realname.use}): resolve a display name back to the underlying
 * account name, the inverse of the cosmetic rename a chat plugin or nickname feature applies. The query matches
 * either a player's account name or their rendered display name, case-insensitively, against the online set
 * filtered through the sender's {@code canSee} graph, the same seam {@code /list}, {@code /msg} and {@code /tpa}
 * read, so a vanished player the sender may not see is unresolvable, never revealing a name they could not
 * otherwise learn. The console has no {@code canSee} graph and may resolve anyone. A pure read: no use case, no
 * state mutation, just a scan of the visible online set and one resolved reply.
 */
@NullMarked
public final class RealnameCommand extends PresenceCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.realname.use";
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    public RealnameCommand(PresenceServices services, Messages messages, Scheduler scheduler) {
        super(services, messages, scheduler);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("realname")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(CommandSuggestions.playerArgument("player").executes(this::run))
                .build();
    }

    @Override
    public List<String> aliases() {
        return List.of();
    }

    @Override
    public String description() {
        return "Look up a player's real name.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String query = StringArgumentType.getString(ctx, "player");
        // The roster (names + display names + per-viewer canSee) is read on the global region thread (Folia forbids
        // iterating Bukkit.getOnlinePlayers() off it); the one reply then lands on the sender's own thread.
        scheduler.onGlobal(() -> {
            Player match = findVisibleMatch(sender, query);
            if (match == null) {
                replyOnSenderThread(
                        sender,
                        () -> feedback.send(sender, PresenceMessageKey.REALNAME_NOT_FOUND, Map.of("query", query)));
                return;
            }
            String display = PLAIN.serialize(match.displayName());
            String name = match.getName();
            replyOnSenderThread(
                    sender,
                    () -> feedback.send(
                            sender, PresenceMessageKey.REALNAME_RESULT, Map.of("display", display, "name", name)));
        });
        return Command.SINGLE_SUCCESS;
    }

    /** First online player the sender may see whose account or display name matches {@code query}, else {@code null}. */
    private @Nullable Player findVisibleMatch(CommandSender sender, String query) {
        Player viewer = sender instanceof Player player ? player : null;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (viewer != null && !viewer.canSee(target)) {
                continue;
            }
            if (target.getName().equalsIgnoreCase(query)
                    || PLAIN.serialize(target.displayName()).equalsIgnoreCase(query)) {
                return target;
            }
        }
        return null;
    }
}

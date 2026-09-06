package com.uxplima.uxmessentials.presence.adapter.inbound.command;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.presence.adapter.PresenceServices;
import com.uxplima.uxmessentials.presence.adapter.inbound.gui.OnlinePlayerListView;
import com.uxplima.uxmessentials.presence.application.PresenceMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * {@code /list} ({@code uxmessentials.list.use}): print the online roster, vanish-aware. When a player runs it the
 * roster is filtered through that player's {@code canSee} graph (the same seam the vanish context drives and
 * that messaging {@code /msg} and teleport {@code /tpa} already read), so a vanished player they may not see is
 * absent from both the line and the count. The
 * console has no {@code canSee} graph and sees everyone. This is a pure read: no use case, no state mutation, just
 * the online set, a name-sorted join, and one resolved line.
 *
 * <p>With its catalog {@code gui} flag on, the bare {@code /list} a player runs opens the
 * {@link OnlinePlayerListView} head grid instead: the same vanish-aware roster, rendered as one head per player.
 * The {@link #runGui} opener installed on the bare root by the {@code GuiRootBinding} snapshots the visible roster
 * on the global region thread (Folia forbids iterating the online set off it) and hands it to the view; a console
 * has no inventory and always falls back to the chat line. With the flag off the bare root keeps the chat
 * listing, and the {@code /list} alias forms continue to chat regardless.
 */
@NullMarked
public final class ListCommand extends PresenceCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.list.use";
    private static final MessageKey LIST_PLAYERS = PresenceMessageKey.LIST_PLAYERS;

    private final Messages messages;
    private final @Nullable OnlinePlayerListView listView;

    public ListCommand(PresenceServices services, Messages messages, Scheduler scheduler) {
        this(services, messages, scheduler, null);
    }

    public ListCommand(
            PresenceServices services,
            Messages messages,
            Scheduler scheduler,
            @Nullable OnlinePlayerListView listView) {
        super(services, messages, scheduler);
        this.messages = messages;
        this.listView = listView;
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("list")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::run)
                .build();
    }

    @Override
    public List<String> aliases() {
        return List.of("who", "online", "playerlist");
    }

    @Override
    public String description() {
        return "List online players.";
    }

    /**
     * Bare {@code /list} opens the online-roster menu when the command's catalog {@code gui} flag is on; with it
     * off the bare root falls back to the chat listing. Empty when no view was wired (the GUI module is absent),
     * so the command keeps its chat-only shape.
     */
    @Override
    public Optional<Command<CommandSourceStack>> guiRoot() {
        return listView == null ? Optional.empty() : Optional.of(this::runGui);
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        // The roster (names + per-viewer canSee) is read on the global region thread (Folia forbids iterating
        // Bukkit.getOnlinePlayers() off it); the one reply then lands on the sender's own thread.
        scheduler.onGlobal(() -> {
            List<String> names = collectVisibleNames(sender);
            String joined = String.join(", ", names);
            Map<String, String> placeholders = Map.of("count", String.valueOf(names.size()), "players", joined);
            replyOnSenderThread(sender, () -> feedback.send(sender, LIST_PLAYERS, placeholders));
        });
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Bare {@code /list} with gui on: snapshot the viewer's visible roster on the global region thread, then open
     * the head grid. A console has no inventory, so it falls back to the chat listing.
     */
    private int runGui(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (listView == null || !(sender instanceof Player viewer)) {
            return run(ctx);
        }
        PlayerRef viewerRef = BukkitRefs.toRef(viewer);
        scheduler.onGlobal(() -> {
            List<OnlinePlayerListView.Entry> roster = collectVisibleEntries(viewer, viewerRef);
            scheduler.onEntity(viewerRef, () -> listView.open(viewer, viewerRef, roster));
        });
        return Command.SINGLE_SUCCESS;
    }

    /** Online names the sender may see, sorted; a player's {@code canSee} hides vanished targets, the console sees all. */
    private List<String> collectVisibleNames(CommandSender sender) {
        Player viewer = sender instanceof Player player ? player : null;
        return Bukkit.getOnlinePlayers().stream()
                .filter(target -> viewer == null || viewer.canSee(target))
                .map(Player::getName)
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());
    }

    /**
     * The visible online players as menu entries, name-sorted, resolved on the global thread. Each carries the
     * uuid (for the head skin), name, world, and a localised status word, only data already read from the live
     * {@link Player} the chat path filters, plus the world the player stands in.
     */
    private List<OnlinePlayerListView.Entry> collectVisibleEntries(Player viewer, PlayerRef viewerRef) {
        String online = messages.resolve(viewerRef, PresenceMessageKey.LIST_GUI_STATUS_ONLINE, Map.of());
        List<Player> visible = Bukkit.getOnlinePlayers().stream()
                .filter(viewer::canSee)
                .sorted(Comparator.comparing(Player::getName))
                .collect(Collectors.toList());
        List<OnlinePlayerListView.Entry> entries = new ArrayList<>(visible.size());
        for (Player target : visible) {
            entries.add(new OnlinePlayerListView.Entry(
                    target.getUniqueId(), target.getName(), target.getWorld().getName(), online));
        }
        return entries;
    }
}

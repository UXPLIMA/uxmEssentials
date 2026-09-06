package com.uxplima.uxmessentials.staff.adapter.inbound.command;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.bukkit.Server;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.staff.adapter.StaffServices;
import com.uxplima.uxmessentials.staff.adapter.inbound.gui.StaffPlayerMenu;
import com.uxplima.uxmessentials.staff.application.StaffMessageKey;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /stafflist} ({@code uxmessentials.staff.list}): open the online-staff GUI, heads of every online staff
 * member the sender can see, and teleport to whichever head they click. The {@link StaffPlayerMenu} owns the
 * engine spec, the head label, and the admin-teleport on click; this handler reads the vanish-aware staff roster on
 * the global region thread (iterating the online list off it is illegal on Folia) and hands it to the menu, or
 * sends {@link StaffMessageKey#STAFF_LIST_EMPTY} when no staff are online rather than opening an empty window. This
 * is the GUI counterpart to the presence context's text {@code /staff} roster. It agrees on who is staff via the
 * same {@code uxmessentials.staff.member} marker.
 */
@NullMarked
public final class StaffListCommand extends StaffCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.staff.list";
    private static final String STAFF_MEMBER_NODE = "uxmessentials.staff.member";

    private final Scheduler scheduler;
    private final Server server;
    private final Messages messages;
    private final MessageSink sink;
    private final StaffPlayerMenu playerMenu;

    public StaffListCommand(
            StaffServices services,
            Messages messages,
            Scheduler scheduler,
            Server server,
            MessageSink sink,
            StaffPlayerMenu playerMenu) {
        super(services, messages);
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.server = Objects.requireNonNull(server, "server");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.playerMenu = Objects.requireNonNull(playerMenu, "playerMenu");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("stafflist")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::open)
                .build();
    }

    @Override
    public String description() {
        return "List who is currently online and on staff.";
    }

    /**
     * Open the online-staff GUI for {@code sender}, the same screen {@code /stafflist} opens. The
     * {@code /uxmess gui} hub entry routes here so both surfaces share one roster snapshot and one empty-roster
     * reply.
     */
    public void open(Player sender, PlayerRef looker) {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(looker, "looker");
        scheduler.onGlobal(() -> openRoster(sender, looker));
    }

    private int open(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef lookerRef = ref(sender);
        scheduler.onGlobal(() -> openRoster(sender, lookerRef));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Snapshot the visible online staff roster on the global region thread, then either open the picker over it or
     * send the empty line: mirroring the old view's onGlobal open and empty-roster handling.
     */
    private void openRoster(Player sender, PlayerRef lookerRef) {
        List<PlayerRef> roster = server.getOnlinePlayers().stream()
                .filter(sender::canSee)
                .filter(online -> online.hasPermission(STAFF_MEMBER_NODE))
                .map(BukkitRefs::toRef)
                .collect(Collectors.toList());
        if (roster.isEmpty()) {
            sink.deliver(lookerRef, messages.resolve(lookerRef, StaffMessageKey.STAFF_LIST_EMPTY, Map.of()));
            return;
        }
        playerMenu.openList(lookerRef, roster);
    }
}

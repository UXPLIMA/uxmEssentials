package com.uxplima.uxmessentials.staff.adapter.inbound.gui;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.bukkit.Server;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.staff.application.StaffMessageKey;
import com.uxplima.uxmessentials.staff.application.port.StaffTeleport;
import org.jspecify.annotations.NullMarked;

/**
 * Registers the two staff teleport pickers. The COMPASS navigator ({@code staff-navigator}) and {@code /stafflist}
 * ({@code staff-list}): with the menu engine and opens them. Both draw a paginated grid of player heads and, on a
 * head click, teleport the looking staff member onto that player through the soft-coupled {@link StaffTeleport}
 * (a no-op feedback when teleport is off), sending {@link StaffMessageKey#STAFF_TELEPORTED} or
 * {@link StaffMessageKey#STAFF_TELEPORT_FAILED}. The two specs differ only in their candidate roster and title; the
 * shared {@code staff:players} list source, the {@code staff_player_name} head label, and the
 * {@code staff:teleport-to} click action are registered once here.
 *
 * <p>The candidate roster is read from the online players on the global region thread (iterating the online list
 * off it is illegal on Folia), so each open site computes the visible-player list there and hands it in as the
 * {@link PlayerHeads} subject; the {@code staff:players} source only reads that subject: it touches no Bukkit API.
 * Building and opening the window runs on the looker's entity thread (the engine hops there), and the head click
 * fires its action there too (the {@code MenuListener} dispatches actions on the viewer's entity thread), where the
 * target is re-resolved and the admin teleport runs: exactly the threads the old bespoke picker base used.
 */
@NullMarked
public final class StaffPlayerMenu {

    /** The engine spec ids the navigator and the staff list register and open under. */
    public static final String NAVIGATOR_SPEC_ID = "staff-navigator";

    public static final String LIST_SPEC_ID = "staff-list";

    private static final String NAVIGATOR_RESOURCE = "modules/staff/gui/staff-navigator.conf";
    private static final String LIST_RESOURCE = "modules/staff/gui/staff-list.conf";

    private final Menus menus;
    private final Server server;
    private final Messages messages;
    private final MessageSink sink;
    private final StaffTeleport teleport;

    public StaffPlayerMenu(Menus menus, Server server, Messages messages, MessageSink sink, StaffTeleport teleport) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.server = Objects.requireNonNull(server, "server");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.teleport = Objects.requireNonNull(teleport, "teleport");
    }

    /** Register the shared list source, head label, and teleport action, plus both specs; called once at wire time. */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.list("staff:players", ctx -> ctx.subject(PlayerHeads.class).players());
        bindings.placeholder(
                "staff_player_name", ctx -> ctx.entry(PlayerRef.class).name());
        bindings.action("staff:teleport-to", this::teleportTo);
        menus.registerSpec(NAVIGATOR_SPEC_ID, MenuSpecs.loadOrBundled(NAVIGATOR_RESOURCE, dataFolder, 6, log));
        menus.registerSpec(LIST_SPEC_ID, MenuSpecs.loadOrBundled(LIST_RESOURCE, dataFolder, 6, log));
    }

    /** Open the COMPASS navigator for {@code looker} over the pre-computed visible roster {@code players}. */
    public void openNavigator(PlayerRef looker, List<PlayerRef> players) {
        open(NAVIGATOR_SPEC_ID, looker, players);
    }

    /** Open {@code /stafflist} for {@code looker} over the pre-computed visible staff roster {@code players}. */
    public void openList(PlayerRef looker, List<PlayerRef> players) {
        open(LIST_SPEC_ID, looker, players);
    }

    private void open(String specId, PlayerRef looker, List<PlayerRef> players) {
        Objects.requireNonNull(looker, "looker");
        Objects.requireNonNull(players, "players");
        menus.open(looker, specId, new PlayerHeads(players));
    }

    /**
     * Left-click a head: close the picker, re-resolve the clicked player, and admin-teleport the looker onto them,
     * sending the success or failure line. Runs on the looker's entity thread (the engine dispatches actions
     * there), so the target read and the teleport are region-safe, mirroring the old picker's click handler.
     */
    private void teleportTo(MenuActionContext ctx) {
        PlayerRef looker = ctx.viewer();
        UUID targetId = ctx.entry(PlayerRef.class).uuid();
        ctx.player().closeInventory();
        Player target = server.getPlayer(targetId);
        if (target == null) {
            deliver(looker, StaffMessageKey.STAFF_TELEPORT_FAILED, Map.of());
            return;
        }
        PlayerRef targetRef = new PlayerRef(target.getUniqueId(), target.getName());
        boolean ok = teleport.teleportTo(looker, targetRef);
        MessageKey key = ok ? StaffMessageKey.STAFF_TELEPORTED : StaffMessageKey.STAFF_TELEPORT_FAILED;
        deliver(looker, key, Map.of("target", target.getName()));
    }

    private void deliver(PlayerRef looker, MessageKey key, Map<String, String> args) {
        sink.deliver(looker, messages.resolve(looker, key, args));
    }

    /**
     * The subject of an open picker: the pre-computed candidate players, each a clickable head. The open site
     * snapshots the visible online roster on the global region thread and passes it here, so the list source reads
     * plain {@link PlayerRef}s and never touches the Bukkit API.
     *
     * @param players the candidate players, in the order their heads are laid out
     */
    public record PlayerHeads(List<PlayerRef> players) {

        public PlayerHeads {
            players = List.copyOf(Objects.requireNonNull(players, "players"));
        }
    }
}

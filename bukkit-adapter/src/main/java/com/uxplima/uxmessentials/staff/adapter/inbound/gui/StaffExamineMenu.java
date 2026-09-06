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
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.staff.adapter.inbound.gui.StaffPlayerMenu.PlayerHeads;
import com.uxplima.uxmessentials.staff.application.StaffMessageKey;
import com.uxplima.uxmessentials.staff.application.port.StaffInspector;
import org.jspecify.annotations.NullMarked;

/**
 * Registers the EXAMINE gadget's online-player picker ({@code staff-examine}) with the menu engine and opens it.
 * A paginated grid of player heads, one per online player; clicking a head opens that player's inventory through
 * the soft-coupled {@link StaffInspector} (a no-op when playerstate is off) and sends the {@code STAFF_EXAMINE_INFO}
 * line (ping/gamemode/health/world) regardless, so the gadget always tells the staff member something even when
 * the inventory open degrades.
 *
 * <p>The picker reuses the shared {@code staff:players} list source and {@code staff_player_name} head label that
 * {@link StaffPlayerMenu} registers, over the same {@link PlayerHeads} subject, so the EXAMINE gadget snapshots the
 * online roster on the global region thread and hands it in, and the source touches no Bukkit API off-thread. Only
 * the {@code staff:examine} click is registered here. The click runs on the looker's entity thread (the engine
 * dispatches actions there), where the target's live state read and the inventory open are region-safe, mirroring
 * the old {@code StaffExamineView}'s click.
 */
@NullMarked
public final class StaffExamineMenu {

    /** The engine spec id the examine picker registers and opens under. */
    public static final String SPEC_ID = "staff-examine";

    private static final String SPEC_RESOURCE = "modules/staff/gui/staff-examine.conf";

    private final Menus menus;
    private final Server server;
    private final Messages messages;
    private final MessageSink sink;
    private final StaffInspector inspector;

    public StaffExamineMenu(Menus menus, Server server, Messages messages, MessageSink sink, StaffInspector inspector) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.server = Objects.requireNonNull(server, "server");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.inspector = Objects.requireNonNull(inspector, "inspector");
    }

    /**
     * Register the examine click and the spec; called once at staff wiring time, after {@link StaffPlayerMenu} has
     * registered the shared {@code staff:players} source and {@code staff_player_name} label the examine spec reuses.
     */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.action("staff:examine", this::examine);
        menus.registerSpec(SPEC_ID, MenuSpecs.loadOrBundled(SPEC_RESOURCE, dataFolder, 6, log));
    }

    /** Open the examine picker for {@code looker} over the pre-computed online roster {@code players}. */
    public void open(PlayerRef looker, List<PlayerRef> players) {
        Objects.requireNonNull(looker, "looker");
        Objects.requireNonNull(players, "players");
        menus.open(looker, SPEC_ID, new PlayerHeads(players));
    }

    /**
     * Left-click a head: close the picker, then, if the clicked player is still online, open their inventory
     * through the inspector and send the info line. Runs on the looker's entity thread (the engine dispatches
     * actions there), so the target read and the inventory open are region-safe, mirroring the old view's click.
     */
    private void examine(MenuActionContext ctx) {
        PlayerRef looker = ctx.viewer();
        UUID targetId = ctx.entry(PlayerRef.class).uuid();
        Player target = server.getPlayer(targetId);
        ctx.player().closeInventory();
        if (target == null) {
            return;
        }
        PlayerRef targetRef = new PlayerRef(target.getUniqueId(), target.getName());
        inspector.inspect(looker, targetRef);
        sink.deliver(looker, messages.resolve(looker, StaffMessageKey.STAFF_EXAMINE_INFO, info(target)));
    }

    private static Map<String, String> info(Player target) {
        return Map.of(
                "target", target.getName(),
                "ping", Integer.toString(target.getPing()),
                "gamemode", target.getGameMode().name(),
                "health", Integer.toString((int) Math.round(target.getHealth())),
                "world", target.getWorld().getName());
    }
}

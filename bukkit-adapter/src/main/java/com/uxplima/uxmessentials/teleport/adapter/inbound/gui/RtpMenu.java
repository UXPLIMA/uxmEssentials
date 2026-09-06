package com.uxplima.uxmessentials.teleport.adapter.inbound.gui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.adapter.TeleportServices;
import com.uxplima.uxmessentials.teleport.application.TeleportMessageKey;
import org.jspecify.annotations.NullMarked;

/**
 * The menu-engine {@code /rtp gui} world picker (gated {@code uxmessentials.rtp.gui}). One tile per RTP-enabled loaded
 * world across the top rows; a left click random-teleports the viewer within that world through the same {@link
 * com.uxplima.uxmessentials.teleport.application.ResolveRtp} path {@code /rtp <world>} drives, so the pool serve, the
 * gating, and the charge-after-success all stay identical. A static hint tile points the player at {@code /rtp biome
 * <biome>} for biome-targeted landings, which carry no world-picker tile.
 *
 * <p>Mirrors {@code WarpBrowseMenu}: the RTP-world set is resolved up front on the viewer's entity thread (a warm
 * server read) and handed to the engine as the menu subject, so the {@code teleport:rtp-worlds} list source only reads
 * that subject and the engine never touches a port off-thread. A world click runs the RTP on the viewer's entity
 * thread (the hop moves the live player).
 */
@NullMarked
public final class RtpMenu {

    /** The engine spec id this menu registers and opens under. */
    public static final String SPEC_ID = "rtp";

    /** Disk-first then bundled, mirroring the GUI-layout loader, so an operator edit to the spec takes effect. */
    private static final String SPEC_RESOURCE = "modules/teleport/gui/rtp.conf";

    private final Menus menus;
    private final Scheduler scheduler;
    private final Messages messages;
    private final Server server;
    private final TeleportServices services;

    public RtpMenu(Menus menus, Scheduler scheduler, Messages messages, Server server, TeleportServices services) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.server = Objects.requireNonNull(server, "server");
        this.services = Objects.requireNonNull(services, "services");
    }

    /** Register the world list source, the per-tile placeholders, the world-click action, and the spec. */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.list(
                "teleport:rtp-worlds", ctx -> ctx.subject(WorldLevel.class).rows());
        bindings.placeholder("rtp_world_icon", ctx -> rowOf(ctx).icon());
        bindings.placeholder("rtp_world_name", ctx -> rowOf(ctx).name());
        bindings.action("teleport:rtp-world", this::clickWorld);
        menus.registerSpec(SPEC_ID, MenuSpecs.loadOrBundled(SPEC_RESOURCE, dataFolder, 6, log));
    }

    /**
     * Open the world picker for {@code viewer} on their entity thread. The RTP-enabled world set and each tile's name
     * are resolved there off warm server reads and the catalog, then handed to the engine as the subject.
     */
    public void open(PlayerRef viewer) {
        Objects.requireNonNull(viewer, "viewer");
        scheduler.onEntity(viewer, () -> menus.open(viewer, SPEC_ID, level(viewer)));
    }

    private WorldRow rowOf(MenuContext ctx) {
        return ctx.entry(WorldRow.class);
    }

    /** The RTP-enabled loaded worlds as fully-resolved tiles: an environment-flavoured icon and a catalog name. */
    private WorldLevel level(PlayerRef viewer) {
        List<WorldRow> rows = new ArrayList<>();
        for (World world : server.getWorlds()) {
            WorldRef ref = BukkitRefs.toRef(world);
            if (!services.rtpQueue().hasQueue(ref)) {
                continue; // a world with no valid RTP zone is not offered. Clicking it would only be denied
            }
            rows.add(new WorldRow(
                    ref,
                    iconFor(world).name(),
                    messages.resolve(viewer, TeleportMessageKey.RTP_GUI_WORLD_NAME, Map.of("world", world.getName()))));
        }
        return new WorldLevel(rows);
    }

    /** A world's tile icon, flavoured by its dimension so the picker reads at a glance. */
    private static Material iconFor(World world) {
        return switch (world.getEnvironment()) {
            case NETHER -> Material.NETHERRACK;
            case THE_END -> Material.END_STONE;
            default -> Material.GRASS_BLOCK;
        };
    }

    /** Random-teleport the viewer within the clicked world and close the menu, on the viewer's entity thread. */
    private void clickWorld(MenuActionContext ctx) {
        WorldRow row = ctx.entry(WorldRow.class);
        PlayerRef viewer = ctx.viewer();
        scheduler.onEntity(viewer, () -> {
            services.notifier().send(viewer, TeleportMessageKey.RTP_SEARCHING);
            services.resolveRtp().background(viewer, row.world());
            ctx.player().closeInventory();
        });
    }

    /**
     * The subject of an open picker: the RTP-enabled worlds as already-resolved tiles. The list source reads
     * {@link #rows()}; nothing is re-read on a click, so the menu carries no port read once it opens.
     *
     * @param rows one tile per offerable world, each with its icon and rendered name
     */
    public record WorldLevel(List<WorldRow> rows) {

        public WorldLevel {
            rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
        }
    }

    /**
     * One world tile, fully resolved on the entity thread so the menu never reads a port again: the world a click
     * random-teleports the viewer within, its icon material name, and its rendered display name.
     *
     * @param world the world a click RTPs the viewer within
     * @param icon the icon material name
     * @param name the rendered display name in the viewer's locale
     */
    public record WorldRow(WorldRef world, String icon, String name) {

        public WorldRow {
            Objects.requireNonNull(world, "world");
            Objects.requireNonNull(icon, "icon");
            Objects.requireNonNull(name, "name");
        }
    }
}

package com.uxplima.uxmessentials.tablist.adapter;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.AnimationRegistry;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.tablist.adapter.inbound.listener.TablistConnectionListener;
import com.uxplima.uxmessentials.tablist.adapter.outbound.BukkitMojangProfileSource;
import com.uxplima.uxmessentials.tablist.adapter.outbound.TablistRenderTask;
import com.uxplima.uxmessentials.tablist.adapter.outbound.TablistRenderer;
import com.uxplima.uxmessentials.tablist.adapter.outbound.TablistSkinResolver;
import com.uxplima.uxmessentials.tablist.adapter.outbound.TablistSuppression;
import com.uxplima.uxmlib.hud.Tablist;
import com.uxplima.uxmlib.packet.tablist.TabListPackets;
import com.uxplima.uxmlib.packet.tablist.internal.NmsTabListPackets;
import com.uxplima.uxmlib.pipeline.ChannelResolver;
import com.uxplima.uxmlib.pipeline.PacketListenerRegistry;
import com.uxplima.uxmlib.pipeline.PacketPipeline;
import com.uxplima.uxmlib.pipeline.PacketSender;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the tablist context's adapters over the injected kernel ports and the operator content under
 * {@code modules/tablist/config.conf}, and produces everything the plugin must register: the join/quit connection
 * listener and the self-rescheduling render timer on the {@code Scheduler} port. This is the one place the tablist
 * context is wired.
 *
 * <p>The tablist is always-on for every viewer when enabled. There is no per-player visibility toggle, so the context
 * publishes no command. It persists nothing: the header/footer content is config-authored under
 * {@code modules/tablist/config.conf}. The renderer dogfoods uxmLib's {@link Tablist} for the header/footer and, for a
 * format that authors a custom-skin row, uxmLib's packet {@link TabListPackets} (a {@link ChannelResolver} →
 * {@link PacketSender} → {@link NmsTabListPackets} stack): the one tab thing native Paper cannot do. Offline skin names
 * are fetched off the tick thread and cached by the {@link TablistSkinResolver}. The render timer on the
 * {@code Scheduler} port is stopped and every active header/footer cleared on disable so a disable or reload tears
 * down cleanly.
 */
@NullMarked
public final class TablistWiring {

    private static final String MODULE_DIR = "modules/tablist";

    private TablistWiring() {}

    /** Build the tablist adapters from {@code plugin} and {@code ctx}, ready to register. */
    public static Wired wire(Plugin plugin, ModuleContext ctx) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        KernelPorts kernel = ctx.kernel();
        Path dir = plugin.getDataFolder().toPath().resolve(MODULE_DIR);
        TablistSettings settings = new TablistSettings(dir, kernel.log());
        AtomicBoolean running = new AtomicBoolean(true);

        // The animation registry holds the stateful uxmLib animators, so it is built once from the load-time catalog,
        // shared by the renderer (which reads frames) and the render task (which advances the clock once a tick).
        AnimationRegistry animations = new AnimationRegistry(settings.animations());

        // The packet stack for custom-skin tab rows (the one tab thing native Paper cannot do): a channel resolver
        // finds
        // each viewer's Netty channel, the sender writes to it, and the NMS port builds the player-info packets.
        // Mirrors
        // the nametag wiring. The skin resolver reads online textures inline and fetches offline names off the tick
        // thread through the kernel Scheduler, caching the result.
        TabListPackets packets = new NmsTabListPackets(new PacketSender(new ChannelResolver()));
        TablistSkinResolver skinResolver =
                new TablistSkinResolver(new BukkitMojangProfileSource(kernel.skins()), kernel.scheduler());

        // The opt-in "suppress real players" interception (TAB-C): a pipeline splices a per-viewer interceptor that
        // rewrites outbound player-info packets to force every non-filler entry unlisted while the viewer's selected
        // format asks for it. The pipeline owns its own listener registry (namespaced handler) so it never collides
        // with the nametag/npc packet stacks, and listener faults are logged off the I/O thread through the kernel log.
        PacketListenerRegistry suppressRegistry = new PacketListenerRegistry();
        PacketPipeline suppressPipeline = new PacketPipeline(
                new ChannelResolver(),
                suppressRegistry,
                "uxmessentials:tablist-suppress",
                fault -> kernel.log().error("tablist suppress listener fault", fault));
        TablistSuppression suppression = new TablistSuppression(
                new ConnectionGate(suppressPipeline),
                suppressRegistry,
                packets,
                Bukkit::getOnlinePlayers,
                kernel.log());
        TablistRenderer renderer = new TablistRenderer(
                settings::formats,
                animations,
                packets,
                skinResolver,
                Bukkit::getOnlinePlayers,
                kernel.scheduler(),
                suppression);
        TablistRenderTask renderTask = new TablistRenderTask(
                kernel.scheduler(), renderer, animations, kernel.log(), settings::refreshInterval, running::get);
        Runnable reload = () -> {
            settings.reload();
            animations.replace(settings.animations());
            renderTask.refreshNow();
        };

        List<CommandRegistration> commands = List.of();
        List<Listener> listeners = List.of(new TablistConnectionListener(renderer));
        return new Wired(commands, listeners, renderer, renderTask, running, kernel.scheduler(), reload);
    }

    /** Bridges {@link TablistSuppression}'s connection seam onto uxmLib's {@link PacketPipeline} inject/eject. */
    private record ConnectionGate(PacketPipeline pipeline) implements TablistSuppression.ConnectionGate {

        private ConnectionGate {
            Objects.requireNonNull(pipeline, "pipeline");
        }

        @Override
        public boolean inject(Player viewer) {
            return pipeline.inject(viewer);
        }

        @Override
        public boolean eject(Player viewer) {
            return pipeline.eject(viewer);
        }
    }

    /**
     * Everything the tablist module contributes once wired: the connection listener, the self-rescheduling render
     * timer, and the {@code running} flag the timer observes. The renderer is held so {@link #stop()} can clear every
     * active header/footer. The command list is always empty, the tablist has no per-player toggle, but it is kept
     * to mirror the other contexts' {@code Wired} shape so the bootstrap wires every context the same way.
     *
     * @param commands the Brigadier command registrations to publish (always empty for tablist)
     * @param listeners the join/quit listener to register
     * @param renderer the per-player renderer, used to clear header/footer on stop
     * @param renderTask the self-rescheduling render timer, armed by the caller
     * @param running the flag flipped false on stop so the render timer exits
     * @param scheduler the kernel scheduler, used to enumerate the roster on the global thread and clear each
     *     viewer's tablist on its owner's region thread when the module stops
     */
    public record Wired(
            List<CommandRegistration> commands,
            List<Listener> listeners,
            TablistRenderer renderer,
            TablistRenderTask renderTask,
            AtomicBoolean running,
            Scheduler scheduler,
            Runnable reload) {

        public Wired {
            commands = List.copyOf(commands);
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(renderer, "renderer");
            Objects.requireNonNull(renderTask, "renderTask");
            Objects.requireNonNull(running, "running");
            Objects.requireNonNull(scheduler, "scheduler");
            Objects.requireNonNull(reload, "reload");
        }

        /** Arm the render timer. */
        public void startBackgroundWork() {
            renderTask.start();
        }

        /**
         * Stop the render timer and clear every active header/footer, list name/order/skin, and filler entry so a
         * disable/reload leaves no stale tablist and no orphaned filler row. {@link TablistRenderer#clear} removes the
         * filler entries it painted for each viewer by their tracked UUIDs. The roster is enumerated on the global
         * region thread (Folia forbids iterating {@code Bukkit.getOnlinePlayers()} off it) and each viewer's tablist
         * is cleared on its owner's entity thread.
         */
        public void stop() {
            running.set(false);
            scheduler.onGlobal(() -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    PlayerRef ref = BukkitRefs.toRef(player);
                    scheduler.onEntity(ref, () -> {
                        Player live = Bukkit.getPlayer(ref.uuid());
                        if (live != null && live.isOnline()) {
                            renderer.clear(live);
                        }
                    });
                }
            });
        }
    }
}

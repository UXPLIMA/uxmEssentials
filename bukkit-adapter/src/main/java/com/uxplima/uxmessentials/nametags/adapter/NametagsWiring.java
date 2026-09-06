package com.uxplima.uxmessentials.nametags.adapter;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.nametags.adapter.inbound.listener.NametagLifecycleListener;
import com.uxplima.uxmessentials.nametags.adapter.outbound.NametagRenderTask;
import com.uxplima.uxmessentials.nametags.adapter.outbound.PacketNametagPresenter;
import com.uxplima.uxmessentials.nametags.application.port.NametagVanish;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.AnimationRegistry;
import com.uxplima.uxmessentials.shared.adapter.outbound.team.PlayerTeamCoordinator;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmlib.nametag.NametagPackets;
import com.uxplima.uxmlib.nametag.NametagRenderer;
import com.uxplima.uxmlib.nametag.internal.NmsNametagPackets;
import com.uxplima.uxmlib.pipeline.ChannelResolver;
import com.uxplima.uxmlib.pipeline.PacketSender;
import com.uxplima.uxmlib.scheduler.PaperScheduler;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the nametags context's adapters over the injected kernel ports and the operator content under
 * {@code modules/nametags/config.conf}, and produces everything the plugin must register: the join/quit/world-change
 * lifecycle listener and the self-rescheduling reconcile/animation timer on the {@code Scheduler} port. This is the one
 * place the nametags context is wired.
 *
 * <p>The nametag is always-on for every eligible wearer when enabled. There is no per-player visibility toggle, so
 * the context publishes no command. It persists nothing: the formats are config-authored. Rendering goes through
 * uxmLib's packet {@link NametagRenderer}: a {@link ChannelResolver} → {@link PacketSender} → {@link NmsNametagPackets}
 * stack sends per-viewer spawn/metadata/remove bundles, and the lib owns a per-wearer refresh task (an entity timer)
 * that re-resolves text, diffs the viewer set, and applies line-of-sight fading. That lib task needs uxmLib's own
 * Folia-aware {@code Scheduler}, obtained as a {@link PaperScheduler} over the plugin exactly as the integration-wiring
 * obtains it elsewhere (the kernel {@code Scheduler} port drives the reconcile timer and the entity-thread hops). The
 * reconcile timer is stopped and every shown nametag removed on disable so a disable or reload tears down cleanly with
 * no orphan nametag.
 */
@NullMarked
public final class NametagsWiring {

    private static final String MODULE_DIR = "modules/nametags";

    private NametagsWiring() {}

    /**
     * Build the nametags adapters from {@code plugin} and {@code ctx}, ready to register. The shared
     * {@code teams} coordinator (built once in the bootstrap and also handed to the scoreboard wiring) hides
     * the vanilla above-head name while a custom nametag is live.
     */
    public static Wired wire(Plugin plugin, ModuleContext ctx, NametagVanish vanish, PlayerTeamCoordinator teams) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(vanish, "vanish");
        Objects.requireNonNull(teams, "teams");
        KernelPorts kernel = ctx.kernel();
        Path dir = plugin.getDataFolder().toPath().resolve(MODULE_DIR);
        NametagSettings settings = new NametagSettings(dir, kernel.log());
        AtomicBoolean running = new AtomicBoolean(true);

        // The animation registry holds the stateful uxmLib animators, so it is built once from the load-time catalog,
        // shared by the presenter (whose per-viewer text callback reads frames) and the reconcile task (which advances
        // the global clock once a tick).
        AnimationRegistry animations = new AnimationRegistry(settings.animations());

        // The packet stack: the channel resolver finds each viewer's Netty channel, the sender writes bundles to it,
        // and the NMS port builds the spawn/metadata/remove packets the lib renderer sends. The lib renderer owns the
        // per-wearer refresh loop on uxmLib's own Folia-aware scheduler, the same PaperScheduler the other contexts
        // use.
        NametagPackets packets = new NmsNametagPackets(new PacketSender(new ChannelResolver()));
        NametagRenderer libRenderer = new NametagRenderer(packets, new PaperScheduler(plugin));

        // Vanish is soft-coupled to the vanish module: bootstrap hands in the authority-reading gate (over the single
        // vanish store) when vanish is enabled, or NametagVanish.ALWAYS_VISIBLE ("everyone can see everyone") when it
        // is off, so no nametag-side branch is needed for the vanish-disabled case.
        // The presenter passes the same configured refresh interval into the lib's per-wearer refresh loop that drives
        // the reconcile/animation timer below, so a refresh-ticks change moves the lib's text/viewer/line-of-sight
        // cadence and the animation clock in lockstep. The lib loop reads the period once per show, and a
        // format/appearance change re-shows, so a reload's new cadence takes effect on the next re-show.
        PacketNametagPresenter presenter = new PacketNametagPresenter(
                settings::formats,
                libRenderer,
                animations,
                vanish,
                settings::refreshInterval,
                teams,
                settings::hideVanillaName);
        NametagRenderTask renderTask = new NametagRenderTask(
                kernel.scheduler(), presenter, animations, kernel.log(), settings::refreshInterval, running::get);
        Runnable reload = () -> {
            settings.reload();
            animations.replace(settings.animations());
            renderTask.refreshNow();
        };

        List<CommandRegistration> commands = List.of();
        List<Listener> listeners = List.of(new NametagLifecycleListener(presenter, kernel.scheduler()));
        return new Wired(commands, listeners, presenter, renderTask, running, reload);
    }

    /**
     * Everything the nametags module contributes once wired: the connection listener, the self-rescheduling reconcile
     * timer, and the {@code running} flag the timer observes. The presenter is held so {@link #stop()} can remove every
     * shown nametag. The command list is always empty, the nametag has no per-player toggle, but it is kept to mirror
     * the other contexts' {@code Wired} shape so the bootstrap wires every context the same way.
     *
     * @param commands the Brigadier command registrations to publish (always empty for nametags)
     * @param listeners the join/quit/world-change listener to register
     * @param presenter the per-wearer presenter, used to remove every nametag on stop
     * @param renderTask the self-rescheduling reconcile timer, armed by the caller
     * @param running the flag flipped false on stop so the reconcile timer exits
     */
    public record Wired(
            List<CommandRegistration> commands,
            List<Listener> listeners,
            PacketNametagPresenter presenter,
            NametagRenderTask renderTask,
            AtomicBoolean running,
            Runnable reload) {

        public Wired {
            commands = List.copyOf(commands);
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(presenter, "presenter");
            Objects.requireNonNull(renderTask, "renderTask");
            Objects.requireNonNull(running, "running");
            Objects.requireNonNull(reload, "reload");
        }

        /** Arm the reconcile timer. */
        public void startBackgroundWork() {
            renderTask.start();
        }

        /** Stop the reconcile timer and remove every shown nametag so a disable/reload leaves no orphan. */
        public void stop() {
            running.set(false);
            presenter.removeAll();
        }
    }
}

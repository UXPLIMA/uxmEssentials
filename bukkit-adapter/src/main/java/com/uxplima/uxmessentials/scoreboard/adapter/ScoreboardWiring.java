package com.uxplima.uxmessentials.scoreboard.adapter;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.scoreboard.adapter.inbound.command.ScoreboardCommand;
import com.uxplima.uxmessentials.scoreboard.adapter.inbound.gui.ScoreboardSettingsView;
import com.uxplima.uxmessentials.scoreboard.adapter.inbound.listener.ScoreboardConnectionListener;
import com.uxplima.uxmessentials.scoreboard.adapter.outbound.PdcScoreboardVisibilityStore;
import com.uxplima.uxmessentials.scoreboard.adapter.outbound.ScoreboardOwnership;
import com.uxplima.uxmessentials.scoreboard.adapter.outbound.ScoreboardRenderTask;
import com.uxplima.uxmessentials.scoreboard.adapter.outbound.ScoreboardRenderer;
import com.uxplima.uxmessentials.scoreboard.application.ToggleScoreboard;
import com.uxplima.uxmessentials.scoreboard.application.port.ScoreboardVisibilityStore;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.AnimationRegistry;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.packet.scoreboard.internal.NmsScoreboardPackets;
import com.uxplima.uxmlib.pipeline.ChannelResolver;
import com.uxplima.uxmlib.pipeline.PacketSender;
import org.jspecify.annotations.NullMarked;

/**
 * Constructs the scoreboard context's adapters and use case over the injected kernel ports and the operator content
 * under {@code modules/scoreboard/config.conf}, and produces everything the plugin must register: the single
 * {@code /scoreboard} (alias {@code /sb}) Brigadier command, the join/quit connection listener, and the
 * self-rescheduling render timer on the {@code Scheduler} port. This is the one place the scoreboard context is wired.
 *
 * <p>The context persists nothing: the per-player "hidden" bit is PDC-backed (survives relog) and the display content
 * is config-authored. The renderer sends modern scoreboard packets through uxmLib, keeping its objective isolated
 * from the server scoreboard and from nametag teams. The tablist header/footer is a separate module now, so this
 * context owns only the sidebar. The {@code /scoreboard} confirmations are {@code MessageKey}s through the
 * {@link Notifier}; sidebar content is raw operator MiniMessage, keeping parity-checked keys and unchecked operator
 * content apart. On stop the render timer is halted and every UXM-owned objective is removed cleanly.
 */
@NullMarked
public final class ScoreboardWiring {

    private static final String MODULE_DIR = "modules/scoreboard";

    private ScoreboardWiring() {}

    /** Build the scoreboard adapters and use case from {@code plugin} and {@code ctx}, ready to register. */
    public static Wired wire(Plugin plugin, ModuleContext ctx, GuiLayouts guiLayouts, Menus menus) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(guiLayouts, "guiLayouts");
        Objects.requireNonNull(menus, "menus");
        KernelPorts kernel = ctx.kernel();
        Path dir = plugin.getDataFolder().toPath().resolve(MODULE_DIR);
        ScoreboardSettings settings = new ScoreboardSettings(dir, kernel.log());
        AtomicBoolean running = new AtomicBoolean(true);

        ScoreboardVisibilityStore visibility = new PdcScoreboardVisibilityStore(plugin);
        // The animation registry is shared by the renderer and clock, and its catalog is replaced on reload.
        AnimationRegistry animations = new AnimationRegistry(settings.animations());
        ChannelResolver channels = new ChannelResolver();
        ScoreboardRenderer renderer = new ScoreboardRenderer(
                new NmsScoreboardPackets(new PacketSender(channels)), visibility, settings::boards, animations);
        ScoreboardOwnership ownership = new ScoreboardOwnership(renderer, kernel.scheduler(), kernel.log(), channels);
        Notifier notifier = new Notifier(kernel.messages(), kernel.messageSink());
        ToggleScoreboard toggle = new ToggleScoreboard(visibility, notifier, kernel.events());
        ScoreboardRenderTask renderTask = new ScoreboardRenderTask(
                kernel.scheduler(), renderer, animations, kernel.log(), settings::refreshInterval, running::get);

        // The settings panel reuses the SP0 GUI framework over the shared catalog and the data-folder layout loader.
        // It carries the single show/hide toggle the /scoreboard command flips (the board a viewer sees is resolved
        // automatically by condition + priority, so there is no board-picker to expose). The render loop reconciles
        // the live board on its next tick from the same PDC bit. /scoreboard gui and the /uxmess gui hub both open it.
        GuiText guiText = new GuiText(kernel.messages());
        ScoreboardSettingsView settingsView = new ScoreboardSettingsView(
                guiText, kernel.scheduler(), guiLayouts, kernel.messages(), visibility, toggle, menus);

        List<CommandRegistration> commands = List.of(new ScoreboardCommand(
                toggle, renderer, kernel.scheduler(), kernel.messages(), settingsView, visibility));
        List<Listener> listeners = List.of(new ScoreboardConnectionListener(renderer, kernel.scheduler(), ownership));
        Runnable reload = () -> {
            settings.reload();
            animations.replace(settings.animations());
            renderTask.refreshNow();
        };
        return new Wired(
                commands,
                listeners,
                renderer,
                renderTask,
                running,
                visibility,
                toggle,
                settingsView,
                kernel.scheduler(),
                ownership,
                reload);
    }

    /**
     * Everything the scoreboard module contributes once wired: the {@code /scoreboard} command, the connection
     * listener, the self-rescheduling render timer, and the {@code running} flag the timer observes. The renderer is
     * held so {@link #stop()} can tear down every active board.
     *
     * @param commands the Brigadier command registrations to publish
     * @param listeners the join/quit listener to register
     * @param renderer the per-player renderer, used to tear down boards on stop
     * @param renderTask the self-rescheduling render timer, armed by the caller
     * @param running the flag flipped false on stop so the render timer exits
     * @param visibility the per-player "hidden" preference store, exposed for the {@code scoreboard_*} PAPI seam
     * @param toggle the flip use case, shared by the command, the settings panel and the published write
     * @param settingsView the per-player settings panel registered on the {@code /uxmess gui} hub
     * @param scheduler the kernel scheduler, used to enumerate the roster on the global thread and clear each board
     *     on its owner's region thread when the module stops
     */
    public record Wired(
            List<CommandRegistration> commands,
            List<Listener> listeners,
            ScoreboardRenderer renderer,
            ScoreboardRenderTask renderTask,
            AtomicBoolean running,
            ScoreboardVisibilityStore visibility,
            ToggleScoreboard toggle,
            ScoreboardSettingsView settingsView,
            Scheduler scheduler,
            ScoreboardOwnership ownership,
            Runnable reload) {

        public Wired {
            commands = List.copyOf(commands);
            listeners = List.copyOf(listeners);
            Objects.requireNonNull(renderer, "renderer");
            Objects.requireNonNull(renderTask, "renderTask");
            Objects.requireNonNull(running, "running");
            Objects.requireNonNull(visibility, "visibility");
            Objects.requireNonNull(toggle, "toggle");
            Objects.requireNonNull(settingsView, "settingsView");
            Objects.requireNonNull(scheduler, "scheduler");
            Objects.requireNonNull(ownership, "ownership");
            Objects.requireNonNull(reload, "reload");
        }

        /** Arm the render timer. */
        public void startBackgroundWork() {
            scheduler.onGlobal(() -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    ownership.inject(player);
                }
            });
            renderTask.start();
        }

        /**
         * Stop the render timer and tear down every active board so a disable/reload leaves no stale display. The
         * roster is enumerated on the global region thread (Folia forbids iterating {@code Bukkit.getOnlinePlayers()}
         * off it) and each board is cleared on its owner's entity thread before its packet listener is removed.
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
                            ownership.eject(live);
                        }
                    });
                }
            });
        }
    }
}

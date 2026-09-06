package com.uxplima.uxmessentials.scoreboard.application;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ListenerFactory;
import com.uxplima.uxmessentials.shared.application.module.MigrationSet;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.jspecify.annotations.NullMarked;

/**
 * The scoreboard bounded context as a first-class {@link FeatureModule}: a per-player sidebar rendered from
 * operator-authored MiniMessage content under {@code modules/scoreboard/config.conf} through the placeholder
 * pipeline. It owns the single {@code /scoreboard} (alias {@code /sb}) per-player visibility toggle and the
 * self-rescheduling render timer on the {@code Scheduler} port that re-renders every viewer each configured refresh
 * interval; the render/join/quit machinery is Bukkit-facing and lands with the adapter wiring. The tablist
 * header/footer is a separate {@code tablist} context.
 *
 * <p><b>Ships disabled by default.</b> The sidebar is a surface a dedicated scoreboard plugin also draws, and two
 * of them fight over the same slot, so {@link #enabled(ConfigStore)} defaults to {@code false}. A fresh install
 * still bundles an example sidebar authored with the built-in {@code {player}}/{@code {online}}/{@code {world}}
 * tokens (no PlaceholderAPI required), so {@code modules.scoreboard.enabled = true} shows a working board straight
 * away, ready to brand. It persists nothing: the per-player "hidden" preference is PDC-backed
 * (survives relog) and the display content is config-authored, so the module owns no Flyway location.
 *
 * <p>The split between the plugin's own strings and operator content is owned here: the {@code /scoreboard}
 * shown/hidden confirmations are {@code ScoreboardMessageKey}s in both locale catalogs (parity-checked); the sidebar
 * title/lines are config-authored content rendered through MiniMessage and never parity-checked. The use case, the
 * render timer, the connection listener, the renderer, and the PDC visibility store are constructed in the adapter
 * wiring once the module has started; the lifecycle bookkeeping here keeps {@code stop()} honest, the render timer
 * observes the running flag and exits cleanly on disable.
 */
@NullMarked
public final class ScoreboardModule implements FeatureModule {

    private static final ModuleId ID = ModuleId.of("scoreboard");
    private static final Duration DRAIN_TIMEOUT = Duration.ofSeconds(5);

    private final AtomicInteger inFlight = new AtomicInteger();
    private volatile boolean running;

    @Override
    public ModuleId id() {
        return ID;
    }

    @Override
    public String configRoot() {
        return ID.configRoot();
    }

    @Override
    public List<CommandSpec> commands() {
        // The single /scoreboard (alias /sb) per-player visibility toggle. The display itself is rendered by the
        // adapter's render timer, not by a command.
        return ScoreboardCommandSurface.all();
    }

    @Override
    public List<ListenerFactory> listeners() {
        // The join/quit connection listener is Bukkit-facing and lands with the inbound adapter; a disabled or
        // not-yet-adapted module registers none here.
        return List.of();
    }

    @Override
    public List<MigrationSet> migrations() {
        // scoreboard persists nothing: the display content is config-authored and the per-player visibility bit is
        // PDC-backed, so the module owns no Flyway location.
        return List.of();
    }

    @Override
    public boolean enabled(ConfigStore config) {
        // The bundled configuration enables a working example. Missing configuration still fails safe to disabled.
        return config.getBoolean(configRoot() + ".enabled", false);
    }

    @Override
    public void start(ModuleContext ctx) {
        this.running = true;
        // The ToggleScoreboard use case, the render timer on the Scheduler port, the connection listener, the
        // packet renderer over uxmLib, and the PDC visibility store are constructed over
        // ctx.kernel() in the adapter wiring; the lifecycle bookkeeping (running flag, in-flight counter) is armed
        // here so stop() is already honest.
    }

    @Override
    public void stop() {
        this.running = false;
        awaitDrain();
    }

    /** True while the module is started; the render timer observes this and exits on stop. */
    public boolean isRunning() {
        return running;
    }

    private void awaitDrain() {
        long deadline = System.nanoTime() + DRAIN_TIMEOUT.toNanos();
        while (inFlight.get() > 0 && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
    }
}

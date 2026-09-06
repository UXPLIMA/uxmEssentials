package com.uxplima.uxmessentials.communication.application;

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
 * The communication bounded context as a first-class {@link FeatureModule}: it owns the connection-message
 * policies (the join/quit/death templates chosen by {@code ResolveJoinMessage} / {@code ResolveQuitMessage} /
 * {@code ResolveDeathMessage}), the rotating announcer on the {@code Scheduler} port ({@code NextAnnouncement}
 * honouring no-immediate-repeat and the min-players gate), the static {@code /broadcast} and
 * {@code /broadcasttoggle} commands, and the operator-configured info pages ({@code /rules}, {@code /motd},
 * {@code /info}) registered dynamically from the {@code InfoRegistry}.
 *
 * <p><b>Ships enabled by default.</b> Every content file ships inert. The join/quit/death channels default to
 * the vanilla message (DEFAULT mode), the first-join line and death info page are blank, and the announcer has no
 * announcements, so out of the box this context changes nothing visible even while enabled: no message is overridden,
 * no announcer line fires. An operator authors the templates/announcements they want and they take effect with no
 * further toggle. The {@link #enabled(ConfigStore)} gate therefore defaults to {@code true} (like the
 * landed contexts), and the four-way module guard keys off {@code modules.communication}.
 *
 * <p>The split between the plugin's own strings and operator content is owned here: {@code /broadcasttoggle}
 * confirmations, the missing-info-page error, and the announcer-reloaded notice are {@code CommunicationMessageKey}s
 * in both locale catalogs (parity-checked); the join/quit/death templates, announcer lines, and info-page bodies
 * are config-authored operator content in the module's content siblings, rendered through MiniMessage and never
 * parity-checked. The use cases, the announcer timer, the connection/death listeners, the opt-out store, and the
 * info-page commands are constructed in the adapter wiring once the module has started; the lifecycle bookkeeping
 * here keeps {@code stop()} honest: the announcer timer observes the running flag and exits cleanly on disable.
 */
@NullMarked
public final class CommunicationModule implements FeatureModule {

    private static final ModuleId ID = ModuleId.of("communication");
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
        // The static surface only: /broadcast and /broadcasttoggle. The operator-configured info-page commands
        // (/rules, /motd, …) are dynamic. Built from the config-derived InfoRegistry at start and registered by
        // the adapter, so they are not part of this fixed table.
        return CommunicationCommandSurface.staticCommands();
    }

    @Override
    public List<ListenerFactory> listeners() {
        // The join/quit/death connection listeners are Bukkit-facing and land with the inbound adapter; a
        // disabled or not-yet-adapted module registers none here.
        return List.of();
    }

    @Override
    public List<MigrationSet> migrations() {
        // communication persists nothing: the connection policies and the announcer schedule are config-authored
        // and the per-player broadcast opt-out is transient (in-memory/PDC), so the module owns no Flyway location.
        return List.of();
    }

    @Override
    public boolean enabled(ConfigStore config) {
        // The module ships ENABLED, but every content file ships inert (channels keep the vanilla message, the
        // announcer has no lines), so being on changes nothing until an operator authors templates. The default here
        // is true (the landed contexts default to on); an operator opts out via modules.conf.
        return config.getBoolean(configRoot() + ".enabled", true);
    }

    @Override
    public void start(ModuleContext ctx) {
        this.running = true;
        // The use cases, the announcer NextAnnouncement on the Scheduler port, the connection/death listeners,
        // the BroadcastOptOutStore, the config-derived InfoRegistry, and the dynamic info-page commands are
        // constructed over ctx.kernel() in the adapter wiring; the lifecycle bookkeeping (running flag, in-flight
        // counter) is armed here so stop() is already honest.
    }

    @Override
    public void stop() {
        this.running = false;
        awaitDrain();
    }

    /** True while the module is started; the announcer timer observes this and exits on stop. */
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

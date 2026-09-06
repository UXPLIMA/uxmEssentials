package com.uxplima.uxmessentials.servertweaks.application;

import java.util.List;

import com.uxplima.uxmessentials.shared.application.module.CommandSpec;
import com.uxplima.uxmessentials.shared.application.module.FeatureModule;
import com.uxplima.uxmessentials.shared.application.module.ListenerFactory;
import com.uxplima.uxmessentials.shared.application.module.MigrationSet;
import com.uxplima.uxmessentials.shared.application.module.ModuleContext;
import com.uxplima.uxmessentials.shared.application.module.ModuleId;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import org.jspecify.annotations.NullMarked;

/**
 * The server-tweaks bounded context as a first-class {@link FeatureModule}: a grab-bag of small, independent
 * server/infrastructure toggles, a custom F3 server brand, a console-spam filter, unsigned public chat
 * (no-chat-reports), and a SignedVelocity backend handshake. The module ships enabled so an operator can turn on any
 * single tweak without touching the others, but <b>every individual tweak defaults off</b>: being enabled changes
 * nothing until a tweak is switched on in {@code modules.servertweaks}.
 *
 * <p>The tweaks are Bukkit-facing side effects (a join-time plugin message for the brand, a Log4j2 filter on the
 * server logger, an unsigned-chat re-delivery listener, and a plugin-message channel for the proxy handshake), so they
 * land with the adapter wiring; the module publishes no command, registers no declarative listener, and runs no
 * migration here. A disabled module wires zero of them, per the {@code FeatureModule} contract, and the adapter's stop
 * hook detaches the log filter and unregisters the brand and SignedVelocity channels so a disable or reload leaves no
 * residual state.
 */
@NullMarked
public final class ServerTweaksModule implements FeatureModule {

    private static final ModuleId ID = ModuleId.of("servertweaks");

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
        // The tweaks are silent side effects (a brand plugin-message, a console log filter); no command is published.
        return List.of();
    }

    @Override
    public List<ListenerFactory> listeners() {
        // The F3-brand join listener is Bukkit-facing and gated per-tweak, so it lands through the adapter wiring; a
        // disabled or not-yet-adapted module registers none here.
        return List.of();
    }

    @Override
    public List<MigrationSet> migrations() {
        // servertweaks persists nothing: each tweak is a live side effect on the running server, so it owns no
        // Flyway location.
        return List.of();
    }

    @Override
    public boolean enabled(ConfigStore config) {
        // The module ships enabled so a single tweak can be turned on in isolation; every tweak itself defaults off.
        return config.getBoolean(configRoot() + ".enabled", true);
    }

    @Override
    public void start(ModuleContext ctx) {
        this.running = true;
        // The tweak handlers (the brand sender and the console-filter installer) are constructed over ctx in the
        // adapter wiring; the lifecycle flag is armed here so stop() is honest before any effect is applied.
    }

    @Override
    public void stop() {
        this.running = false;
    }

    /** True while the module is started; the adapter's effects observe this and unwind on stop. */
    public boolean isRunning() {
        return running;
    }
}

package com.uxplima.uxmessentials.commandcontrol.application;

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
 * The command-control bounded context as a first-class {@link FeatureModule}: PlHidePro / CommandWhitelist-parity
 * gating of which commands a player may run and see, built in with no separate plugin. Phase 1 is the command
 * whitelist/blacklist and the custom "unknown command" deny message; Phase 2 adds the tab-completion filter and the
 * plugin-hide (scrubbing disallowed and plugin-listing commands from the client command list, tab completion, and the
 * scrub-help output); Phase 3 adds the namespace-bypass block, so a {@code /minecraft:gamemode} escape is denied when
 * {@code /gamemode} is. The PlHidePro-parity extras layer on top: command-spam rate-limiting (kick/block/warn a player
 * who floods commands), the plugin-channel hider (strip non-allowed plugin-messaging channels from the client so mods
 * cannot fingerprint installed plugins), auto-lowercasing the base command label, and per-world overrides of the
 * command lists and hide behaviour. The gate and filters are client-agnostic - a Bedrock / Geyser player is gated like
 * a Java one - and run per backend, so a proxy-forwarded command is gated on arrival.
 *
 * <p><b>Ships enabled by default</b> but inert: the bundled config is a blacklist with empty lists and the plugin-hide
 * is off, so an enabled module gates and hides nothing until an operator names commands or turns the hide on. An
 * operator turns the whole feature off with {@code modules.commandcontrol.enabled = false}. It persists nothing, the
 * rule set and the hide policy are derived from config, so the module owns no Flyway location.
 *
 * <p>The gate and the visibility filter are Bukkit-facing listeners contributed through the adapter wiring (they need
 * the live player, the permission check, and the group lookup), so this module publishes no declarative command or
 * listener; a disabled module wires zero of them (the {@code FeatureModule} contract).
 */
@NullMarked
public final class CommandControlModule implements FeatureModule {

    private static final ModuleId ID = ModuleId.of("commandcontrol");

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
        // The gate is a listener, not a command; the /uxmess cc reload hook lands with the later phases.
        return List.of();
    }

    @Override
    public List<ListenerFactory> listeners() {
        // The PlayerCommandPreprocessEvent gate is Bukkit-facing and needs the group/permission lookups, so it is
        // contributed through the adapter wiring rather than this declarative list.
        return List.of();
    }

    @Override
    public List<MigrationSet> migrations() {
        // command-control persists nothing: the rule set is derived from config, so the module owns no Flyway location.
        return List.of();
    }

    @Override
    public boolean enabled(ConfigStore config) {
        return config.getBoolean(configRoot() + ".enabled", true);
    }

    @Override
    public void start(ModuleContext ctx) {
        this.running = true;
    }

    @Override
    public void stop() {
        this.running = false;
    }

    /** True while the module is started; the adapter's listener is registered only between start and stop. */
    public boolean isRunning() {
        return running;
    }
}

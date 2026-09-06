package com.uxplima.uxmessentials.bootstrap;

import java.util.logging.Level;

import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

import com.uxplima.uxmessentials.bootstrap.di.CloseableResources;
import com.uxplima.uxmessentials.bootstrap.di.PluginModule;
import com.uxplima.uxmessentials.poses.adapter.outbound.WorldGuardPoseFlagRegistrar;
import com.uxplima.uxmessentials.shared.adapter.outbound.permission.CatalogPermissions;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyleTags;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.ThemeFile;
import com.uxplima.uxmessentials.shared.adapter.outbound.worldguard.WorldGuardSetPwarpFlagRegistrar;
import com.uxplima.uxmessentials.worlds.adapter.outbound.WorldGeneratorResolver;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The thin {@link JavaPlugin} shell: wires the DI graph on enable, closes it on disable.
 *
 * <p>The plugin instance is never exposed via a static accessor. Services are constructed and
 * injected by {@link PluginModule}, which is the only holder of this reference. The {@code COMMANDS}
 * lifecycle handler publishes the already-module-filtered command set built during wiring, so a
 * disabled module's command literal never reaches the dispatcher.
 */
@NullMarked
public final class UxmEssentialsPlugin extends JavaPlugin {

    private @Nullable CloseableResources resources;
    private @Nullable CatalogPermissions permissions;

    /**
     * Registers our WorldGuard custom flags before any plugin is enabled. WorldGuard locks its flag registry the moment
     * it enables, so registration has to happen in the load phase, and a Paper
     * {@link io.papermc.paper.plugin.bootstrap.PluginBootstrap} runs even earlier, before WorldGuard has created that
     * registry, so {@code onLoad} is the correct hook. Each call is a silent no-op when WorldGuard is absent (it never
     * loads a WorldGuard class on a server without it), so both are safe to run unconditionally here, before the module
     * registry that decides whether a feature is enabled even exists.
     */
    @Override
    public void onLoad() {
        WorldGuardPoseFlagRegistrar.register(getServer(), getLogger());
        WorldGuardSetPwarpFlagRegistrar.register(getServer(), getLogger());
    }

    @Override
    public void onEnable() {
        getLogger().info("╔══════════════════════════════════════════════════════════╗");
        getLogger().info("║                                                          ║");
        getLogger().info("║   ██╗   ██╗██╗  ██╗███╗   ███╗                           ║");
        getLogger().info("║   ██║   ██║╚██╗██╔╝████╗ ████║                           ║");
        getLogger().info("║   ██║   ██║ ╚███╔╝ ██╔████╔██║                           ║");
        getLogger().info("║   ██║   ██║ ██╔██╗ ██║╚██╔╝██║                           ║");
        getLogger().info("║   ╚██████╔╝██╔╝ ██╗██║ ╚═╝ ██║                           ║");
        getLogger().info("║    ╚═════╝ ╚═╝  ╚═╝╚═╝     ╚═╝                           ║");
        getLogger().info("║                                                          ║");
        getLogger().info("║   Essentials v" + getPluginMeta().getVersion() + "                                      ║");
        getLogger().info("║   Author: uxplima                                        ║");
        getLogger().info("║                                                          ║");
        getLogger().info("╚══════════════════════════════════════════════════════════╝");

        long startTime = System.currentTimeMillis();

        getLogger().info("[1/5] Registering permissions...");
        // Every node the catalogue declares, handed to the server before anything can check one, so a permission
        // plugin sees the whole surface rather than the subset a hand-written file happened to list.
        CatalogPermissions permissions = new CatalogPermissions(getServer().getPluginManager(), getLogger());
        this.permissions = permissions;
        getLogger().info("       " + permissions.register() + " permission nodes registered.");

        getLogger().info("[2/5] Writing default resources...");
        // First-run side effect: drop the editable default config files next to the database so an operator has
        // something to configure. Existing files are never overwritten; an update only appends the settings it
        // added, so a new knob is visible in their file instead of only in the jar (see DefaultResources).
        DefaultResources.writeInto(
                getDataFolder().toPath(), getLogger(), getPluginMeta().getVersion());

        // The colours, before anything renders a line: the file the server shares with the other plugins it
        // runs of ours, then this plugin's own file over it. A server that wrote neither keeps the colours
        // this plugin ships, which is what every server has seen until now.
        try {
            StyleTags.use(ThemeFile.read(getDataFolder().toPath()));
        } catch (RuntimeException unreadableTheme) {
            getLogger().log(Level.WARNING, "using the shipped colours: " + unreadableTheme.getMessage());
        }

        getLogger().info("[3/5] Wiring core modules and persistence...");
        CloseableResources wired = PluginModule.wire(this);
        this.resources = wired;

        getLogger().info("[4/5] Registering command handlers...");
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            var registrar = event.registrar();
            // Guard each publish so one malformed command (a broken build() or a duplicate literal) is logged
            // and skipped instead of aborting the whole batch and leaving every later command unregistered.
            wired.commands().forEach(command -> {
                try {
                    registrar.register(command.build(), command.description(), command.aliases());
                } catch (RuntimeException failure) {
                    getLogger()
                            .log(
                                    Level.SEVERE,
                                    "failed to register command "
                                            + command.getClass().getSimpleName() + ", skipped",
                                    failure);
                }
            });
        });

        getLogger().info("[5/5] Registering listener hooks...");
        // The plugin enables at STARTUP (paper-plugin.yml) so the default world can reach
        // getDefaultWorldGenerator, which means everything above ran with no worlds loaded. This listener
        // releases the work the wiring held back until they exist (see WorldPhase).
        getServer().getPluginManager().registerEvents(new WorldPhaseListener(wired.worldPhase(), getLogger()), this);
        // Same isolation on the listener side: one listener that throws on registration must not stop the rest.
        wired.listeners().forEach(listener -> {
            try {
                getServer().getPluginManager().registerEvents(listener, this);
            } catch (RuntimeException failure) {
                getLogger()
                        .log(
                                Level.SEVERE,
                                "failed to register listener "
                                        + listener.getClass().getSimpleName() + ", skipped",
                                failure);
            }
        });

        // Initialize bStats Metrics
        int pluginId = 31811;
        new org.bstats.bukkit.Metrics(this, pluginId);

        long loadTime = System.currentTimeMillis() - startTime;
        getLogger().info("╔══════════════════════════════════════════════════════════╗");
        getLogger().info("║  UxmEssentials enabled successfully in " + loadTime + "ms!          ║");
        getLogger().info("╚══════════════════════════════════════════════════════════╝");
    }

    /**
     * Serves our built-in {@code uxmEssentials:void|flat} generators to any world configured with
     * {@code generator: uxmEssentials:<id>}, including the default world.
     *
     * <p>The default world is the case this hook exists for and the case that used to be impossible.
     * {@code CraftServer.getGenerator} refuses a generator whose plugin is not enabled yet, and the default
     * world is created before a {@code POSTWORLD} plugin enables, so the hook was simply never called and the
     * world generated as vanilla terrain with a SEVERE nobody could act on. The plugin therefore declares
     * {@code load: STARTUP}; {@link com.uxplima.uxmessentials.bootstrap.di.WorldPhase} carries the cost of that.
     *
     * <p>A null return means vanilla generation, so the two ways of getting there are logged rather than left
     * silent: an operator who asked for our generator and got terrain instead deserves to be told which of the
     * two happened.
     */
    @Override
    public @Nullable ChunkGenerator getDefaultWorldGenerator(String worldName, @Nullable String id) {
        CloseableResources wired = this.resources;
        WorldGeneratorResolver resolver = wired == null ? null : wired.worldGeneratorResolver();
        if (resolver == null) {
            getLogger()
                    .warning("World '" + worldName + "' asked for our generator, but the worlds module is off,"
                            + " so it will generate as normal terrain. Enable the worlds module in"
                            + " modules.conf, or drop the generator line for this world.");
            return null;
        }
        ChunkGenerator generator = resolveGenerator(resolver, id);
        if (generator == null) {
            getLogger()
                    .warning("World '" + worldName + "' asked for generator '" + id + "', which is not one of"
                            + " ours, so it will generate as normal terrain. The built-in ids are void and flat.");
        }
        return generator;
    }

    /** The pure resolve step behind {@link #getDefaultWorldGenerator}, factored out for unit testing. */
    static @Nullable ChunkGenerator resolveGenerator(@Nullable WorldGeneratorResolver resolver, @Nullable String id) {
        return resolver == null || id == null ? null : resolver.resolve(id).orElse(null);
    }

    @Override
    public void onDisable() {
        getLogger().info("Disabling UxmEssentials...");
        CloseableResources wired = this.resources;
        if (wired != null) {
            getLogger().info("Closing active modules and dependencies...");
            wired.close(); // stops every started module in reverse wiring order
            this.resources = null;
        }
        CatalogPermissions declared = this.permissions;
        if (declared != null) {
            declared.unregister();
            this.permissions = null;
        }
        getLogger().info("UxmEssentials has been disabled!");
    }
}

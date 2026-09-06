package com.uxplima.uxmessentials.bootstrap;

import org.bukkit.plugin.java.JavaPlugin;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.bootstrap.PluginProviderContext;

import org.jspecify.annotations.NullMarked;

/**
 * Runs before {@link UxmEssentialsPlugin} is instantiated.
 *
 * <p>Command registration cannot happen here: the DI graph, and thus the {@code ModuleRegistry}
 * that decides which contexts are enabled: is wired in {@code onEnable}. The {@code COMMANDS}
 * handler is therefore registered from {@link UxmEssentialsPlugin#onEnable()} against the plugin's
 * own lifecycle manager, which still fires before the first tick. Registry modification (custom
 * enchantments, trim materials) is the only work that belongs here, and there is none yet.
 */
@NullMarked
public final class UxmEssentialsBootstrap implements PluginBootstrap {

    @Override
    public void bootstrap(BootstrapContext context) {
        // No registry-entry-add handlers yet; itemworld custom content lands in a later phase.
    }

    @Override
    public JavaPlugin createPlugin(PluginProviderContext context) {
        return new UxmEssentialsPlugin();
    }
}

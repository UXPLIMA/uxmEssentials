package com.uxplima.uxmessentials.shared.adapter.outbound.hooks;

import java.util.Objects;

import org.bukkit.Server;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link PluginHook} for HeadDatabase: it integrates with the {@code HeadDatabase} plugin and resolves to a
 * {@link HeadQuery}. It names {@link HeadDatabaseService} only inside {@link #whenPresent}, and that service
 * reaches HeadDatabase purely by reflection; so on a server without HeadDatabase the service is never
 * constructed and the SDK is never loaded. {@code Hooks} hands callers the no-op {@link HeadQuery#ABSENT},
 * which carries no {@code me.arcaniax} type.
 *
 * <p>Presence here is stricter than plugin-enabled alone: HeadDatabase must be enabled <em>and</em> its
 * {@code HeadDatabaseAPI} class must be loadable, so a present-but-broken install degrades to the safe no-op
 * rather than to a real query that cannot reach the SDK.
 */
@NullMarked
public final class HeadDatabaseHook implements PluginHook<HeadQuery> {

    private static final String PLUGIN_NAME = "HeadDatabase";

    private final Logger log;

    public HeadDatabaseHook(Logger log) {
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public String pluginName() {
        return PLUGIN_NAME;
    }

    @Override
    public Class<HeadQuery> capability() {
        return HeadQuery.class;
    }

    @Override
    public HeadQuery whenAbsent() {
        return HeadQuery.ABSENT;
    }

    @Override
    public HeadQuery whenPresent(Server server) {
        Objects.requireNonNull(server, "server");
        // The single reference to the SDK-touching service, reached only past Hooks' present-guard.
        return new HeadDatabaseService(log);
    }

    @Override
    public boolean isPresent(Server server) {
        Objects.requireNonNull(server, "server");
        return server.getPluginManager().isPluginEnabled(PLUGIN_NAME) && HeadDatabaseService.headDatabaseLoadable();
    }
}

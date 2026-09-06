package com.uxplima.uxmessentials.shared.adapter.outbound.hooks;

import org.bukkit.Server;

import org.jspecify.annotations.NullMarked;

/**
 * The {@link PluginHook} for Vault's permission service: it integrates with the {@code Vault} plugin and
 * resolves to a {@link PermissionQuery}. It names {@link VaultPermissionService} only inside
 * {@link #whenPresent}, and that service is the single class importing {@code net.milkbowl.vault.permission};
 * so on a server without Vault the service is never constructed and the SDK is never loaded: {@code Hooks}
 * hands callers the no-op {@link PermissionQuery#ABSENT}.
 */
@NullMarked
public final class VaultPermissionHook implements PluginHook<PermissionQuery> {

    private static final String PLUGIN_NAME = "Vault";

    @Override
    public String pluginName() {
        return PLUGIN_NAME;
    }

    @Override
    public Class<PermissionQuery> capability() {
        return PermissionQuery.class;
    }

    @Override
    public PermissionQuery whenAbsent() {
        return PermissionQuery.ABSENT;
    }

    @Override
    public PermissionQuery whenPresent(Server server) {
        // The single reference to the SDK-touching service, reached only past Hooks' present-guard.
        return new VaultPermissionService(server);
    }
}

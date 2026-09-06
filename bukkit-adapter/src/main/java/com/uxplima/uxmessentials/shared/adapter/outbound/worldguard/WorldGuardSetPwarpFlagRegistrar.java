package com.uxplima.uxmessentials.shared.adapter.outbound.worldguard;

import java.lang.reflect.Constructor;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Server;

import org.jspecify.annotations.NullMarked;

/**
 * Registers the {@code set-pwarp} custom flag with WorldGuard's flag registry. WorldGuard locks that registry the
 * moment it enables, so registration must happen in the load phase, <em>before</em> any plugin is enabled
 * {@code UxmEssentialsPlugin.onLoad()} is the correct hook (a Paper {@code PluginBootstrap} runs before WorldGuard has
 * even created its registry, so it is too early). WorldGuard is a {@code load: BEFORE} soft-depend, so by our
 * {@code onLoad} its registry already exists.
 *
 * <p>Guarded so it is a silent no-op when WorldGuard is absent: the present-check on the plugin manager short-circuits
 * before any {@code Class.forName}, so a WorldGuard-less server loads none of the {@code com.sk89q} classes named here
 * only by string. Registration is best-effort. A flag already present (a plugin-manager reload re-running
 * {@code onLoad}) is skipped, and any reflective or linkage failure (a version bump moving the registry API, a
 * mismatched WorldGuard throwing {@code NoClassDefFoundError} mid-reflection) is logged once and degraded to a no-op,
 * leaving the gate to fail open. The flag defaults to ALLOW, so registering it is inert until an operator sets a
 * region DENY on it.
 */
@NullMarked
public final class WorldGuardSetPwarpFlagRegistrar {

    /** The custom flag name, shared with {@link BukkitWorldGuardFlags} which reads its region state. */
    static final String FLAG_NAME = "set-pwarp";

    private WorldGuardSetPwarpFlagRegistrar() {}

    /** Register the {@code set-pwarp} flag with WorldGuard if it is present; otherwise do nothing. */
    public static void register(Server server, Logger log) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(log, "log");
        if (!WorldGuardReflection.isInstalled(server)) {
            return;
        }
        try {
            registerFlag();
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            log.log(Level.WARNING, "Could not register the set-pwarp WorldGuard flag: " + failure, failure);
        }
    }

    /** Register the flag, skipping it when one of that name is already present so a re-run never conflicts. */
    private static void registerFlag() throws ReflectiveOperationException {
        Object registry = WorldGuardReflection.flagRegistry();
        Object existing = registry.getClass().getMethod("get", String.class).invoke(registry, FLAG_NAME);
        if (existing != null) {
            return;
        }
        Constructor<?> stateFlag = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag")
                .getConstructor(String.class, boolean.class);
        Object flag = stateFlag.newInstance(FLAG_NAME, true); // default ALLOW, inert until a region sets it DENY
        Class<?> flagType = Class.forName("com.sk89q.worldguard.protection.flags.Flag");
        registry.getClass().getMethod("register", flagType).invoke(registry, flag);
    }
}

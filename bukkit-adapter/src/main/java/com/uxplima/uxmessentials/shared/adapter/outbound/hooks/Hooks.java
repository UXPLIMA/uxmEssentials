package com.uxplima.uxmessentials.shared.adapter.outbound.hooks;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Server;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;

/**
 * The optional-plugin integration façade. Built once at bootstrap, it resolves each registered
 * {@link PluginHook} to either its real implementation (the target plugin is present and enabled) or its
 * no-op default (absent), and hands callers the typed capability by class.
 *
 * <p>Resolution happens exactly once, here, because plugin presence is stable for a server run, a plugin
 * cannot appear or vanish mid-run. Callers fetch a never-null capability with {@link #capability(Class)} and
 * use it unconditionally; the absent default makes every call a safe no-op without a presence check. A hook
 * whose {@link PluginHook#whenPresent} throws (an incompatible SDK surfacing as a {@code NoClassDefFoundError}
 * or similar) degrades to the no-op default rather than aborting bootstrap.
 *
 * <p>No static state: the resolved instance is constructor-injected wherever a feature needs an integration.
 */
@NullMarked
public final class Hooks {

    private final Map<Class<?>, Object> capabilities;

    private Hooks(Map<Class<?>, Object> capabilities) {
        this.capabilities = capabilities;
    }

    /** Resolve every hook against {@code server} once, binding each to its real or no-op capability. */
    public static Hooks resolve(Server server, Logger log, List<? extends PluginHook<?>> hooks) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(log, "log");
        Objects.requireNonNull(hooks, "hooks");
        Map<Class<?>, Object> resolved = new HashMap<>();
        for (PluginHook<?> hook : hooks) {
            bind(resolved, hook, server, log);
        }
        return new Hooks(Map.copyOf(resolved));
    }

    private static <T> void bind(Map<Class<?>, Object> into, PluginHook<T> hook, Server server, Logger log) {
        Class<T> capability = hook.capability();
        if (into.containsKey(capability)) {
            throw new IllegalStateException("two hooks claim capability " + capability.getName());
        }
        into.put(capability, resolveOne(hook, server, log));
    }

    private static <T> T resolveOne(PluginHook<T> hook, Server server, Logger log) {
        if (!hook.isPresent(server)) {
            return Objects.requireNonNull(hook.whenAbsent(), "whenAbsent");
        }
        try {
            T present = Objects.requireNonNull(hook.whenPresent(server), "whenPresent");
            log.info("event=hook_bound plugin={}", hook.pluginName());
            return present;
        } catch (Throwable failure) {
            // An incompatible or partially-shaded SDK surfaces here (NoClassDefFoundError, a linkage error, a
            // constructor throwing): degrade to the no-op default so a broken integration cannot abort bootstrap.
            log.warn("event=hook_bind_failed plugin={} reason={}", hook.pluginName(), failure);
            return Objects.requireNonNull(hook.whenAbsent(), "whenAbsent");
        }
    }

    /** The resolved capability for {@code type}; never null. Throws if no hook registered that capability. */
    public <T> T capability(Class<T> type) {
        Objects.requireNonNull(type, "type");
        Object capability = capabilities.get(type);
        if (capability == null) {
            throw new IllegalArgumentException("no hook registered for capability " + type.getName());
        }
        return type.cast(capability);
    }

    /** Whether a hook for {@code type} was registered (independent of whether its plugin is present). */
    public boolean provides(Class<?> type) {
        Objects.requireNonNull(type, "type");
        return capabilities.containsKey(type);
    }
}

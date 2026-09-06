package com.uxplima.uxmessentials.shared.adapter.outbound.hooks;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The real {@link HeadQuery}, backed by HeadDatabase and reached only from {@link HeadDatabaseHook#whenPresent},
 * past {@code Hooks}' present-guard. HeadDatabase is not a compile dependency: this class names it purely by
 * string class-name through reflection ({@code new HeadDatabaseAPI()}, then {@code getItemHead(String)}) so no
 * {@code me.arcaniax} type appears in any field or method signature and constructing this on a server without
 * HeadDatabase loads none of its classes.
 *
 * <p>The {@code HeadDatabaseAPI} instance and its {@code getItemHead} method are resolved once in the
 * constructor (both reachable because this is reached only past the present-guard); if either cannot be
 * resolved the query reports {@link #available()} false and every lookup is empty. Each lookup is guarded: any
 * {@link ReflectiveOperationException} or {@link RuntimeException} (HeadDatabase shifting method shape under a
 * version bump, or its API throwing an NPE while still loading) is logged once and degraded to empty, so a head
 * lookup never throws into a menu render.
 *
 * <p>HeadDatabase builds its head index asynchronously on startup, so {@code getItemHead} can return null for a
 * known id while that index is still loading; null is treated as empty, which is the correct plain-head
 * fallback.
 */
@NullMarked
final class HeadDatabaseService implements HeadQuery {

    static final String HEAD_DATABASE_API_CLASS = "me.arcaniax.hdb.api.HeadDatabaseAPI";

    private final Logger log;
    private final AtomicBoolean warned = new AtomicBoolean();

    @Nullable private final Object api;

    @Nullable private final Method getItemHead;

    HeadDatabaseService(Logger log) {
        this.log = Objects.requireNonNull(log, "log");
        Object instance = null;
        Method method = null;
        try {
            Class<?> apiType = Class.forName(HEAD_DATABASE_API_CLASS);
            instance = apiType.getConstructor().newInstance();
            method = apiType.getMethod("getItemHead", String.class);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            degrade(failure);
        }
        this.api = instance;
        this.getItemHead = method;
    }

    /** True when HeadDatabase's {@code HeadDatabaseAPI} class is loadable; the probe used by the hook's presence. */
    static boolean headDatabaseLoadable() {
        try {
            Class.forName(HEAD_DATABASE_API_CLASS);
            return true;
        } catch (ClassNotFoundException absent) {
            return false;
        }
    }

    @Override
    public boolean available() {
        return api != null && getItemHead != null;
    }

    @Override
    public Optional<ItemStack> head(@Nullable String id) {
        Object resolvedApi = api;
        Method method = getItemHead;
        if (id == null || id.isBlank() || resolvedApi == null || method == null) {
            return Optional.empty();
        }
        try {
            Object result = method.invoke(resolvedApi, id);
            // Null is the "not yet indexed / unknown id" answer and means plain-head fallback, never an error.
            return result instanceof ItemStack stack ? Optional.of(stack) : Optional.empty();
        } catch (ReflectiveOperationException | RuntimeException failure) {
            degrade(failure);
            return Optional.empty();
        }
    }

    private void degrade(Exception failure) {
        if (warned.compareAndSet(false, true)) {
            log.warn("event=head_database_lookup_failed reason={}", failure.toString());
        }
    }
}

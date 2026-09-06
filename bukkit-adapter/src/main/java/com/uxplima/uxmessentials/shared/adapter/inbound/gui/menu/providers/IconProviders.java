package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Server;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.outbound.hooks.HeadQuery;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.Nullable;

/**
 * The ordered chain of {@link IconProvider}s the renderer consults before falling back to a plain material.
 * {@link #resolve} hands the spec to each provider in turn and returns the first non-empty icon; an empty
 * result from every provider means no provider claimed the spec, so the renderer treats it as a material name.
 *
 * <p>Two shapes are wired at the composition root. {@link #defaults()} carries only the providers that need no
 * external plugin (the raw-entry stack provider, skull sources and viewer equipment) and is what the plain
 * {@code ItemRenderer(GuiText, PlaceholderRegistry)} constructor uses, so every existing call site gains those
 * for free.
 * {@link #withHeadDatabase(HeadQuery)} additionally chains the HeadDatabase provider, built from the Phase-0
 * {@link HeadQuery} hook; bootstrap uses it so {@code hdb:<id>} resolves when HeadDatabase is installed and
 * degrades to a plain head (the material fallback) when it is not. {@link #full(Server, Logger, HeadQuery)} is the
 * composition root's full chain: those same providers plus the six custom-item integrations (ItemsAdder,
 * Oraxen, Nexo, CraftEngine, MMOItems, ExecutableItems), each reached reflectively and degrading to the material
 * fallback when its plugin is absent.
 *
 * <p>The built-in chain is immutable, but the composition root may opt a chain into a live {@link
 * IconProviderRegistry} via {@link #withRuntime}: a plugin then registers its own {@link IconProvider} through the
 * public {@code MenuApi} and {@link #resolve} consults the runtime registry <em>after</em> every built-in, so a
 * registered provider adds new prefixes and can never shadow one of ours.
 */
public final class IconProviders {

    private final List<IconProvider> providers;

    /**
     * The runtime-mutable tail consulted after every built-in provider, or {@code null} when this chain is the
     * pure built-in one. Held by reference (not snapshotted), so a provider registered after this chain was built
     * is seen at the next {@link #resolve}.
     */
    private final @Nullable IconProviderRegistry runtime;

    /**
     * The chain in priority order. Constructed directly only by the factories below and by integrations that
     * extend the default set with their own provider; the list is defensively copied so the chain is immutable
     * once built. This form carries no runtime tail, opt into one with {@link #withRuntime}.
     */
    public IconProviders(List<IconProvider> providers) {
        this(providers, null);
    }

    private IconProviders(List<IconProvider> providers, @Nullable IconProviderRegistry runtime) {
        this.providers = List.copyOf(Objects.requireNonNull(providers, "providers"));
        this.runtime = runtime;
    }

    /**
     * A copy of this chain that additionally consults {@code runtime} after every built-in provider. The same
     * immutable built-in list is reused and a <em>live</em> reference to the mutable registry is carried, so a
     * plugin that registers a provider later, through the public {@code MenuApi}, is seen at resolve time
     * without rebuilding the chain. The composition root opts in; the {@link #defaults()}/{@link
     * #withHeadDatabase}/{@link #full} factories stay runtime-free.
     */
    public IconProviders withRuntime(IconProviderRegistry runtime) {
        Objects.requireNonNull(runtime, "runtime");
        return new IconProviders(this.providers, runtime);
    }

    /** Skull sources, viewer equipment, and the raw-entry stack provider: the providers that need no external plugin. */
    public static IconProviders defaults() {
        return new IconProviders(
                List.of(new EntryStackIconProvider(), new SkullIconProvider(), new EquipmentIconProvider()));
    }

    /**
     * The {@link #defaults()} chain plus the HeadDatabase provider over {@code headQuery}. When HeadDatabase is
     * absent the query is the no-op {@link HeadQuery#ABSENT}, so {@code hdb:<id>} resolves to empty and falls
     * through to the material fallback (a plain head/stone) rather than failing the render.
     */
    public static IconProviders withHeadDatabase(HeadQuery headQuery) {
        Objects.requireNonNull(headQuery, "headQuery");
        return new IconProviders(List.of(
                new EntryStackIconProvider(),
                new SkullIconProvider(),
                new EquipmentIconProvider(),
                new HeadDatabaseIconProvider(headQuery)));
    }

    /**
     * The full chain the composition root wires: the raw-entry stack provider, the skull and equipment providers,
     * the two native special sources (a serialized {@code b64:} stack and the {@code water_bottle}/{@code light:<n>}
     * keywords), then the six
     * custom-item providers (ItemsAdder, Oraxen, Nexo, CraftEngine, MMOItems, ExecutableItems), then the
     * HeadDatabase provider. The prefixes are
     * disjoint, so order is a readability choice rather than a routing one. A spec reaches exactly the one provider
     * that owns its prefix. The two native sources need no external plugin; each custom-item provider reaches its
     * plugin reflectively behind a present-guard, so on a server without that plugin its prefix resolves to empty and
     * the renderer falls back to the plain material.
     */
    public static IconProviders full(Server server, Logger log, HeadQuery headQuery) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(log, "log");
        Objects.requireNonNull(headQuery, "headQuery");
        return new IconProviders(List.of(
                new EntryStackIconProvider(),
                new SkullIconProvider(),
                new EquipmentIconProvider(),
                new SerializedStackIconProvider(),
                new SpecialItemIconProvider(),
                new ItemsAdderIconProvider(server, log),
                new OraxenIconProvider(server, log),
                new NexoIconProvider(server, log),
                new CraftEngineIconProvider(server, log),
                new MMOItemsIconProvider(server, log),
                new ExecutableItemsIconProvider(server, log),
                new HeadDatabaseIconProvider(headQuery)));
    }

    /**
     * The first icon any provider returns for {@code spec}, or {@link Optional#empty()} when no provider claims
     * it: in which case the renderer reads {@code spec} as a material name. The spec is the material string
     * with its {@code %placeholder%} already resolved, so {@code %head%} → {@code skull:Notch} reaches the
     * skull provider here. Every built-in provider is consulted first; only when none claims the spec is the
     * runtime registry (when this chain carries one) asked, so a runtime provider adds prefixes but never shadows
     * a built-in.
     */
    public Optional<ItemStack> resolve(String spec, MenuContext ctx) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(ctx, "ctx");
        for (IconProvider provider : providers) {
            Optional<ItemStack> icon = provider.icon(spec, ctx);
            if (icon.isPresent()) {
                return icon;
            }
        }
        return runtime != null ? runtime.resolve(spec, ctx) : Optional.empty();
    }
}

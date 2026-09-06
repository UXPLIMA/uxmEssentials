package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;

/**
 * The runtime-mutable tail of the icon-provider chain. The built-in {@link IconProviders} chain is immutable once
 * built, so a plugin that wants to teach the engine a new material-spec prefix at runtime registers its
 * {@link IconProvider} here, through the public {@code MenuApi}, rather than rebuilding the chain.
 * {@link IconProviders#withRuntime} carries a live reference to one of these and consults it <em>after</em> every
 * built-in provider, so a registered provider only ever <em>adds</em> a new prefix and can never shadow a built-in
 * ({@code skull:}, {@code hdb:}, an equipment keyword, …).
 *
 * <p>Registration happens at plugin-enable and reads happen on the render path, so a copy-on-write list is the
 * right trade: writes are rare, reads are lock-free. A provider registered later is seen at the next resolve,
 * because {@link IconProviders} holds this instance by reference rather than snapshotting its contents.
 *
 * <p>A registered {@link IconProvider#icon} runs on the viewer's own region thread, the render thread, exactly
 * like a built-in provider, so it must read only the viewer's state and never block.
 */
public final class IconProviderRegistry {

    private final CopyOnWriteArrayList<IconProvider> providers = new CopyOnWriteArrayList<>();

    /** Append a custom provider to the runtime tail; it is consulted after every built-in, in registration order. */
    public void register(IconProvider provider) {
        providers.add(Objects.requireNonNull(provider, "provider"));
    }

    /**
     * The first icon any registered provider returns for {@code spec}, or {@link Optional#empty()} when none of
     * them claims it: mirroring {@link IconProviders#resolve} over the runtime tail alone.
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
        return Optional.empty();
    }
}

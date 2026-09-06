/**
 * Icon providers: the seam that lets a menu item's {@code material} field name something richer than a Bukkit
 * material (a player head from any skull source, the viewer's own equipment, a HeadDatabase head) and still
 * fall through to the plain material lookup when no provider claims the spec.
 *
 * <p>An {@link com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers.IconProvider} inspects the
 * resolved material string and returns its base {@code ItemStack} when it recognises the spec, or
 * {@link java.util.Optional#empty()} so the next provider, or the renderer's material fallback, handles it.
 * {@link com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers.IconProviders} holds the ordered
 * chain and returns the first non-empty result. This is the same SPI the later item-plugin integrations
 * (ItemsAdder, Oraxen, Nexo, MMOItems) plug into: each ships one {@code IconProvider} that claims its own
 * prefix and leaves every other spec untouched.
 *
 * <p>Providers live in the Bukkit render layer, so they may build real {@code ItemStack}s, the pure spec and
 * eval packages stay Bukkit-free. A provider reads only the viewer's own state on the calling (render) thread,
 * so it never blocks the main thread and is safe under Folia.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers;

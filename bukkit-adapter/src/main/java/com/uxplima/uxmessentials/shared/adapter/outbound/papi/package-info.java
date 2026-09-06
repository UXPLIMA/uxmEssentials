/**
 * The PlaceholderAPI integration, a bukkit-adapter-only soft-depend. PlaceholderAPI is a {@code
 * compileOnly} dependency: every {@code me.clip.placeholderapi} symbol is touched only past a
 * plugin-present guard ({@code Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null}), so the
 * plugin runs fully without it installed.
 *
 * <p>The expansion never reaches into a context's domain. It queries each feature context through the
 * thin read seams in this package ({@code HomesPlaceholders}, {@code EconomyPlaceholders}, …), each of
 * which is an adapter over the context's existing read ports wired during bootstrap. A disabled (or not
 * yet landed) context contributes no seam, so its placeholders degrade to an empty/"-" value rather than
 * failing, the same soft-couple pattern the economy/jail/mute gates use.
 *
 * <p>PlaceholderAPI types are kept entirely out of {@code :core}: the read seams carry only {@code
 * shared.domain} value objects, and the resolver logic lives behind {@link
 * com.uxplima.uxmessentials.shared.adapter.outbound.papi.PlaceholderResolver}, which has no compile-time
 * dependency on PlaceholderAPI at all. The {@code UxmEssentialsExpansion} shell is the only class that
 * extends {@code PlaceholderExpansion}.
 */
@NullMarked
package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import org.jspecify.annotations.NullMarked;

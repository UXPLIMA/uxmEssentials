package com.uxplima.uxmessentials.tablist.adapter.outbound;

import java.util.Optional;

import com.uxplima.uxmlib.packet.tablist.TabSkin;
import org.jspecify.annotations.NullMarked;

/**
 * The seam between the {@link TablistSkinResolver} and Bukkit's profile API, so the resolver's online/offline/cache
 * logic is unit-testable against a fake without a live server. Two reads, split by their cost:
 *
 * <ul>
 *   <li>{@link #onlineTexture(String)} reads a currently-online player's texture from their live {@code PlayerProfile}
 *       no network, safe on any thread, so the resolver calls it inline on the render path;</li>
 *   <li>{@link #fetchTexture(String)} resolves an <em>offline</em> name through Mojang ({@code Bukkit.createProfile(name)}
 *       then {@code complete()}). It blocks on the network, so the resolver only ever calls it on the {@code async}
 *       scheduler, never on a tick thread.</li>
 * </ul>
 *
 * <p>Both return empty rather than throw on any miss (unknown name, no texture property, fetch failure) so the resolver
 * can fall back to the native no-skin path.
 */
@NullMarked
public interface MojangProfileSource {

    /** The online player {@code name}'s texture, or empty if they are offline or carry no texture property. No I/O. */
    Optional<TabSkin> onlineTexture(String name);

    /** Fetch the offline name {@code name}'s texture from Mojang. Blocks; call only off a tick thread. Empty on miss. */
    Optional<TabSkin> fetchTexture(String name);
}

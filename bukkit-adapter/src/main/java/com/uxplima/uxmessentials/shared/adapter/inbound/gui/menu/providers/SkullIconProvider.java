package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers;

import java.util.Locale;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmlib.item.ItemBuilder;
import com.uxplima.uxmlib.item.SkullData;

/**
 * Turns a skull-source spec into a player head. Three prefixes are recognised, case-insensitively:
 *
 * <ul>
 *   <li>{@code skull:<value>} / {@code head:<value>}. {@code <value>} is a player name, a UUID (dashed or
 *       undashed), a base64 texture, or a skin URL; {@link SkullData#parse} routes all four to the right head.
 *   <li>{@code skull:self} / {@code head:self}: the viewer's own head, by their UUID.
 *   <li>{@code basehead:<base64>}, a head from a raw base64 texture value (the {@code textures} payload).
 * </ul>
 *
 * <p>uxmLib applies every variant synchronously ({@code setOwningPlayer} for a name/UUID, an embedded textures
 * profile for a base64 value), so the render stays on the calling thread with no async hop. A blank value or a
 * parse failure yields {@link Optional#empty()} so the spec falls through to the material fallback rather than
 * leaving a blank head. The provider claims only these prefixes, never a bare material name.
 */
final class SkullIconProvider implements IconProvider {

    private static final String SKULL = "skull:";
    private static final String HEAD = "head:";
    private static final String BASEHEAD = "basehead:";
    private static final String SELF = "self";

    @Override
    public Optional<ItemStack> icon(String spec, MenuContext ctx) {
        String trimmed = spec.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith(BASEHEAD)) {
            return head(texture(trimmed.substring(BASEHEAD.length())));
        }
        if (lower.startsWith(SKULL)) {
            return head(parse(trimmed.substring(SKULL.length()), ctx));
        }
        if (lower.startsWith(HEAD)) {
            return head(parse(trimmed.substring(HEAD.length()), ctx));
        }
        return Optional.empty();
    }

    /** The skull source for a {@code skull:}/{@code head:} value: {@code self} is the viewer, else parse the value. */
    private Optional<SkullData> parse(String value, MenuContext ctx) {
        String rest = value.trim();
        if (rest.isBlank()) {
            return Optional.empty();
        }
        if (rest.equalsIgnoreCase(SELF)) {
            return Optional.of(SkullData.ofUuid(ctx.viewer().uuid()));
        }
        try {
            return Optional.of(SkullData.parse(rest));
        } catch (IllegalArgumentException unparseable) {
            // A blank or otherwise unusable skull value should fall through to the material fallback, not abort.
            return Optional.empty();
        }
    }

    /** A {@code basehead:} texture, treated as a raw base64 textures payload; blank falls through. */
    private Optional<SkullData> texture(String base64) {
        String rest = base64.trim();
        if (rest.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(SkullData.ofTexture(rest));
    }

    /** Build a {@link Material#PLAYER_HEAD} carrying {@code data}, or empty when there was no usable source. */
    private Optional<ItemStack> head(Optional<SkullData> data) {
        return data.map(
                skull -> ItemBuilder.of(Material.PLAYER_HEAD).skull(skull).build());
    }
}

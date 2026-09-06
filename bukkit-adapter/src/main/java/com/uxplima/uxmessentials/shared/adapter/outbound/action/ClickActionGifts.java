package com.uxplima.uxmessentials.shared.adapter.outbound.action;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Delivers the {@code GIVE} action's item to a viewer. The value is one of two shapes:
 *
 * <ul>
 *   <li><b>{@code <material>[:amount]}</b>, a Bukkit material name (case-insensitive) and an optional positive
 *       amount (default 1), the plain-reward shape.
 *   <li><b>{@code b64:<base64>}</b>. A full serialized item (enchantments, custom name, lore, custom model data),
 *       resolved through the injected {@code serializedResolver} (the owning context's serialized-item codec);
 *       this is what {@code add … give hand} stores, so a reward can carry NBT, not just a material.
 * </ul>
 *
 * <p>What does not fit the viewer's inventory drops at their feet rather than vanishing, so a full inventory never
 * silently eats the reward. An unknown material or a corrupt serialized token is logged and skipped, the runner
 * treats this as a fail-soft effect, not a gate, so the rest of the chain still runs.
 */
@NullMarked
final class ClickActionGifts {

    private final Function<String, Optional<ItemStack>> serializedResolver;
    private final Logger log;

    ClickActionGifts(Function<String, Optional<ItemStack>> serializedResolver, Logger log) {
        this.serializedResolver = Objects.requireNonNull(serializedResolver, "serializedResolver");
        this.log = Objects.requireNonNull(log, "log");
    }

    /** Give {@code value}'s item to {@code viewer}, dropping any overflow at their location. */
    void give(Player viewer, String value) {
        ItemStack item = parse(value);
        if (item == null) {
            log.warn("event=click_action_unknown_give value={}", value);
            return;
        }
        var overflow = viewer.getInventory().addItem(item);
        if (overflow.isEmpty()) {
            return;
        }
        Location at = Objects.requireNonNull(viewer.getLocation(), "viewer location");
        for (ItemStack leftover : overflow.values()) {
            viewer.getWorld().dropItemNaturally(at, leftover);
        }
    }

    /**
     * Parse the give value into an item: a {@code b64:} token deserializes its full item through the injected
     * serialized resolver, otherwise {@code <material>[:amount]} builds a plain stack. Returns {@code null}
     * when the material is unknown or the serialized token is corrupt, so the caller skips the give fail-soft.
     */
    private @Nullable ItemStack parse(String value) {
        String spec = value.strip();
        if (SerializedItems.isSerialized(spec)) {
            return serializedResolver.apply(spec).orElse(null);
        }
        int colon = spec.indexOf(':');
        String materialName = colon < 0 ? spec : spec.substring(0, colon);
        Material material = Material.matchMaterial(materialName.strip());
        if (material == null || !material.isItem()) {
            return null;
        }
        int amount = colon < 0 ? 1 : parseAmount(spec.substring(colon + 1));
        return new ItemStack(material, amount);
    }

    /** Parse the amount segment to a positive count, defaulting to 1 on anything non-positive or non-numeric. */
    private static int parseAmount(String raw) {
        try {
            int amount = Integer.parseInt(raw.strip().toLowerCase(Locale.ROOT));
            return amount > 0 ? amount : 1;
        } catch (NumberFormatException notANumber) {
            return 1;
        }
    }
}

package com.uxplima.uxmessentials.itemworld.domain;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * A validated attribute-modifier request for {@code /itemedit attribute add <attribute> <amount> [slot]}: a
 * normalised attribute id, a finite modifier amount, and an equipment-slot-group token.
 *
 * <p>The domain does not know Bukkit {@code Attribute} or {@code EquipmentSlotGroup}. It normalises the id to
 * the canonical lowercase {@code namespace:path} shape (stripping a legacy category prefix such as
 * {@code generic.} so {@code generic.attack_damage} and the modern flat {@code attack_damage} both resolve) and
 * lower-cases the slot token, and the adapter resolves both against the live registries. An {@code add_number}
 * operation is the editor's only mode, applied at the boundary. A blank id or a non-finite amount is rejected
 * here ({@link #of} returns empty) so the adapter renders the localized failure rather than handing a malformed
 * modifier to the item.
 *
 * @param attributeId the normalised {@code namespace:path} attribute id
 * @param amount the modifier amount (always finite)
 * @param slotGroup the lowercase equipment-slot-group token ({@code any}, {@code mainhand}, {@code head}, …)
 */
public record AttributeSpec(String attributeId, double amount, String slotGroup) {

    /** The slot group applied when the actor omits the optional slot argument. */
    public static final String DEFAULT_SLOT = "any";

    private static final String DEFAULT_NAMESPACE = "minecraft";

    public AttributeSpec {
        Objects.requireNonNull(attributeId, "attributeId");
        Objects.requireNonNull(slotGroup, "slotGroup");
        if (!Double.isFinite(amount)) {
            throw new IllegalArgumentException("amount must be finite: " + amount);
        }
    }

    /**
     * Normalise {@code rawAttribute} and {@code rawSlot} into a spec. Returns empty for a blank attribute id or a
     * non-finite {@code amount} (the adapter maps that to {@link ItemworldMessageKey#ITEMEDIT_ATTRIBUTE_UNKNOWN});
     * a blank or omitted slot defaults to {@link #DEFAULT_SLOT}.
     */
    public static Optional<AttributeSpec> of(String rawAttribute, double amount, @Nullable String rawSlot) {
        if (rawAttribute == null || rawAttribute.isBlank() || !Double.isFinite(amount)) {
            return Optional.empty();
        }
        String slot = rawSlot == null || rawSlot.isBlank()
                ? DEFAULT_SLOT
                : rawSlot.trim().toLowerCase(Locale.ROOT);
        return Optional.of(new AttributeSpec(normaliseId(rawAttribute), amount, slot));
    }

    private static String normaliseId(String raw) {
        String name = raw.trim().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        String flat = dot >= 0 ? name.substring(dot + 1) : name;
        return flat.indexOf(':') < 0 ? DEFAULT_NAMESPACE + ":" + flat : flat;
    }
}

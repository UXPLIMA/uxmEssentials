package com.uxplima.uxmessentials.npc.domain;

import java.util.Locale;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

/**
 * The six wearable slots a fake-player NPC's equipment can occupy. This is a pure domain enum. The slot is named
 * here without any {@code org.bukkit} reference, so the {@code Npc} aggregate and its equipment map stay
 * Bukkit-free. The adapter maps each constant onto the uxmLib packet slot when it renders, and {@code /npc equip}
 * parses an operator's slot word through {@link #parse}.
 */
public enum EquipmentSlot {

    /** The item held in the main hand. */
    MAINHAND,
    /** The item held in the off hand. */
    OFFHAND,
    /** The helmet slot. */
    HEAD,
    /** The chestplate slot. */
    CHEST,
    /** The leggings slot. */
    LEGS,
    /** The boots slot. */
    FEET;

    /** The slot for an operator's word ({@code mainhand}, {@code head}, ...), case-insensitively, or empty. */
    public static Optional<EquipmentSlot> parse(@Nullable String word) {
        if (word == null || word.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(EquipmentSlot.valueOf(word.strip().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException unknown) {
            return Optional.empty();
        }
    }
}

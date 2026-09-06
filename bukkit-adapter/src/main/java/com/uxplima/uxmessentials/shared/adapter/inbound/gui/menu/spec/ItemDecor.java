package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The cosmetic extras layered onto a rendered item beyond its material, name, and lore, stack amount, an
 * optional custom model data id, an enchant glow, the raw item-flag tokens the renderer maps to Bukkit
 * {@code ItemFlag}s, and the richer native {@link RichMeta} (enchantments, colours, potion/banner/trim data, …).
 * Flag tokens and every {@link RichMeta} value stay as strings/ints here so the pure model never references Bukkit.
 */
public record ItemDecor(int amount, Optional<Integer> modelData, boolean glow, List<String> flagTokens, RichMeta meta) {

    public ItemDecor {
        Objects.requireNonNull(modelData, "modelData");
        flagTokens = List.copyOf(Objects.requireNonNull(flagTokens, "flagTokens"));
        Objects.requireNonNull(meta, "meta");
    }

    /**
     * The original four-argument form, retained so every existing call site keeps compiling unchanged: it carries
     * no rich meta ({@link RichMeta#NONE}). Only the loader builds the five-argument canonical form when a spec's
     * {@code decor} block declares native metadata.
     */
    public ItemDecor(int amount, Optional<Integer> modelData, boolean glow, List<String> flagTokens) {
        this(amount, modelData, glow, flagTokens, RichMeta.NONE);
    }

    /** A copy with a new stack {@code amount}, every other field unchanged: the item editor's amount stepper. */
    public ItemDecor withAmount(int amount) {
        return new ItemDecor(amount, modelData, glow, flagTokens, meta);
    }

    /** A copy with a new custom-model-data override (or {@link Optional#empty()} to clear it), every other field kept. */
    public ItemDecor withModelData(Optional<Integer> modelData) {
        return new ItemDecor(amount, modelData, glow, flagTokens, meta);
    }

    /** A copy with the enchant glow turned on or off, every other field unchanged. */
    public ItemDecor withGlow(boolean glow) {
        return new ItemDecor(amount, modelData, glow, flagTokens, meta);
    }

    /** A copy carrying new native metadata, every other field unchanged: the item editor's tooltip toggle. */
    public ItemDecor withMeta(RichMeta meta) {
        return new ItemDecor(amount, modelData, glow, flagTokens, meta);
    }

    /** A copy carrying a new item-flag token list, every other field unchanged: the item editor's flag toggles. */
    public ItemDecor withFlagTokens(List<String> flagTokens) {
        return new ItemDecor(amount, modelData, glow, flagTokens, meta);
    }
}

package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The native item metadata an operator layers onto a menu item beyond {@link ItemDecor}'s amount, model data,
 * glow, and flags, enchantments, an unbreakable mark, a leather/potion colour, banner patterns, an armour trim,
 * durability, an item-model override, and the two placeholder-driven {@code dynamic*} overrides. Every value is
 * kept as a plain string, int, or list, never a Bukkit type, so this model stays in the pure, Bukkit-free spec
 * core. The renderer resolves each token against the live registries at draw time and silently skips anything
 * that does not resolve (an unknown enchant, an unsupported colour, a registry the runtime cannot model), exactly
 * the way the flag tokens already degrade rather than abort a render.
 *
 * <p>Token grammars an operator writes: an enchantment is {@code name:level}; a potion effect is
 * {@code effect:amplifier:durationTicks}; a banner pattern is {@code pattern:dyecolor}; a colour is a
 * {@code #RRGGBB} hex, an {@code r,g,b} triple, or a named dye colour. {@code dynamicAmount}/{@code dynamicModelData}
 * carry a {@code %token%} the renderer resolves to a number on each draw, overriding the static
 * {@link ItemDecor#amount()}/{@link ItemDecor#modelData()}; {@code dynamicGlow} is the same idea for the enchant
 * glow, resolved to a boolean so one list template can glint only the entry that is currently selected. The {@link DataComponents} block carries the common
 * native data-components (rarity, tooltip-style, the hide-tooltip/enchant-glint toggles, enchantability, attribute
 * modifiers, food, tool) the same fail-soft way.
 */
public record RichMeta(
        boolean unbreakable,
        List<String> enchantments,
        List<String> storedEnchantments,
        Optional<String> leatherColor,
        PotionSpec potion,
        List<String> bannerPatterns,
        Optional<TrimSpec> trim,
        Optional<Integer> damage,
        Optional<String> dynamicAmount,
        Optional<String> dynamicModelData,
        Optional<String> itemModel,
        DataComponents components,
        Optional<String> dynamicGlow) {

    /** The empty rich meta: the default an item carries when it declares no extra native metadata. */
    public static final RichMeta NONE = new RichMeta(
            false,
            List.of(),
            List.of(),
            Optional.empty(),
            PotionSpec.NONE,
            List.of(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            DataComponents.NONE,
            Optional.empty());

    public RichMeta {
        enchantments = List.copyOf(Objects.requireNonNull(enchantments, "enchantments"));
        storedEnchantments = List.copyOf(Objects.requireNonNull(storedEnchantments, "storedEnchantments"));
        Objects.requireNonNull(leatherColor, "leatherColor");
        Objects.requireNonNull(potion, "potion");
        bannerPatterns = List.copyOf(Objects.requireNonNull(bannerPatterns, "bannerPatterns"));
        Objects.requireNonNull(trim, "trim");
        Objects.requireNonNull(damage, "damage");
        Objects.requireNonNull(dynamicAmount, "dynamicAmount");
        Objects.requireNonNull(dynamicModelData, "dynamicModelData");
        Objects.requireNonNull(itemModel, "itemModel");
        Objects.requireNonNull(components, "components");
        Objects.requireNonNull(dynamicGlow, "dynamicGlow");
    }

    /**
     * The twelve-argument form, retained so every existing call site keeps compiling unchanged: it carries no
     * dynamic glow token. Only the loader builds the canonical form, when a spec's {@code glow} is a placeholder.
     */
    public RichMeta(
            boolean unbreakable,
            List<String> enchantments,
            List<String> storedEnchantments,
            Optional<String> leatherColor,
            PotionSpec potion,
            List<String> bannerPatterns,
            Optional<TrimSpec> trim,
            Optional<Integer> damage,
            Optional<String> dynamicAmount,
            Optional<String> dynamicModelData,
            Optional<String> itemModel,
            DataComponents components) {
        this(
                unbreakable,
                enchantments,
                storedEnchantments,
                leatherColor,
                potion,
                bannerPatterns,
                trim,
                damage,
                dynamicAmount,
                dynamicModelData,
                itemModel,
                components,
                Optional.empty());
    }

    /** A copy carrying new native data-components, every other value unchanged. */
    public RichMeta withComponents(DataComponents components) {
        return new RichMeta(
                unbreakable,
                enchantments,
                storedEnchantments,
                leatherColor,
                potion,
                bannerPatterns,
                trim,
                damage,
                dynamicAmount,
                dynamicModelData,
                itemModel,
                components,
                dynamicGlow);
    }

    /**
     * The original eleven-argument form, retained so every existing call site keeps compiling unchanged: it carries
     * no data-components ({@link DataComponents#NONE}). Only the loader builds the twelve-argument canonical form
     * when a spec's {@code decor} block declares any of them.
     */
    public RichMeta(
            boolean unbreakable,
            List<String> enchantments,
            List<String> storedEnchantments,
            Optional<String> leatherColor,
            PotionSpec potion,
            List<String> bannerPatterns,
            Optional<TrimSpec> trim,
            Optional<Integer> damage,
            Optional<String> dynamicAmount,
            Optional<String> dynamicModelData,
            Optional<String> itemModel) {
        this(
                unbreakable,
                enchantments,
                storedEnchantments,
                leatherColor,
                potion,
                bannerPatterns,
                trim,
                damage,
                dynamicAmount,
                dynamicModelData,
                itemModel,
                DataComponents.NONE);
    }

    /**
     * A potion's native metadata: an optional base potion type (a registry id such as {@code strength}), an
     * optional display colour, and any custom effects as {@code effect:amplifier:durationTicks} tokens.
     */
    public record PotionSpec(Optional<String> type, Optional<String> color, List<String> effects) {

        /** The empty potion spec: no base type, no colour, no custom effects. */
        public static final PotionSpec NONE = new PotionSpec(Optional.empty(), Optional.empty(), List.of());

        public PotionSpec {
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(color, "color");
            effects = List.copyOf(Objects.requireNonNull(effects, "effects"));
        }
    }

    /** An armour trim: a trim-material id ({@code diamond}) and a trim-pattern id ({@code sentry}). */
    public record TrimSpec(String material, String pattern) {

        public TrimSpec {
            Objects.requireNonNull(material, "material");
            Objects.requireNonNull(pattern, "pattern");
        }
    }
}

package com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.colour;

import java.util.List;

import org.bukkit.Material;

import com.uxplima.uxmessentials.shared.application.message.GuiMessageKey;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import org.jspecify.annotations.NullMarked;

/**
 * One entry in the colour picker's fixed palette: a standard Minecraft named colour (the 16 dye colours), its
 * RGB value, the catalog key carrying its display name, and the default stained-glass-pane material the picker
 * draws it with when the layout conf does not override the slot's material. The palette order matches the
 * canonical Minecraft dye order so an operator's {@code palette-slots} list maps positionally onto it.
 *
 * <p>The swatch carries only presentation data, an RGB triple, a name key, and a default icon. The picker packs
 * the RGB into an opaque ARGB int ({@code 0xFF} alpha) when a swatch is chosen; the property's setter is the
 * only place a domain write happens.
 */
@NullMarked
public enum ColourSwatch {
    WHITE(GuiMessageKey.COLOUR_WHITE, 0xFFFFFF, Material.WHITE_STAINED_GLASS_PANE),
    ORANGE(GuiMessageKey.COLOUR_ORANGE, 0xD87F33, Material.ORANGE_STAINED_GLASS_PANE),
    MAGENTA(GuiMessageKey.COLOUR_MAGENTA, 0xB24CD8, Material.MAGENTA_STAINED_GLASS_PANE),
    LIGHT_BLUE(GuiMessageKey.COLOUR_LIGHT_BLUE, 0x6699D8, Material.LIGHT_BLUE_STAINED_GLASS_PANE),
    YELLOW(GuiMessageKey.COLOUR_YELLOW, 0xE5E533, Material.YELLOW_STAINED_GLASS_PANE),
    LIME(GuiMessageKey.COLOUR_LIME, 0x7FCC19, Material.LIME_STAINED_GLASS_PANE),
    PINK(GuiMessageKey.COLOUR_PINK, 0xF27FA5, Material.PINK_STAINED_GLASS_PANE),
    GRAY(GuiMessageKey.COLOUR_GRAY, 0x4C4C4C, Material.GRAY_STAINED_GLASS_PANE),
    LIGHT_GRAY(GuiMessageKey.COLOUR_LIGHT_GRAY, 0x999999, Material.LIGHT_GRAY_STAINED_GLASS_PANE),
    CYAN(GuiMessageKey.COLOUR_CYAN, 0x4C7F99, Material.CYAN_STAINED_GLASS_PANE),
    PURPLE(GuiMessageKey.COLOUR_PURPLE, 0x7F3FB2, Material.PURPLE_STAINED_GLASS_PANE),
    BLUE(GuiMessageKey.COLOUR_BLUE, 0x334CB2, Material.BLUE_STAINED_GLASS_PANE),
    BROWN(GuiMessageKey.COLOUR_BROWN, 0x664C33, Material.BROWN_STAINED_GLASS_PANE),
    GREEN(GuiMessageKey.COLOUR_GREEN, 0x667F33, Material.GREEN_STAINED_GLASS_PANE),
    RED(GuiMessageKey.COLOUR_RED, 0x993333, Material.RED_STAINED_GLASS_PANE),
    BLACK(GuiMessageKey.COLOUR_BLACK, 0x191919, Material.BLACK_STAINED_GLASS_PANE);

    private static final int OPAQUE_ALPHA = 0xFF;

    private final GuiMessageKey nameKey;
    private final int rgb;
    private final Material defaultIcon;

    ColourSwatch(GuiMessageKey nameKey, int rgb, Material defaultIcon) {
        this.nameKey = nameKey;
        this.rgb = rgb;
        this.defaultIcon = defaultIcon;
    }

    /** The catalog key for this colour's localised display name. */
    public MessageKey nameKey() {
        return nameKey;
    }

    /** The opaque packed ARGB int this swatch selects (alpha forced to {@code 0xFF}). */
    public int argb() {
        return OPAQUE_ALPHA << 24 | rgb;
    }

    /** The default icon material the picker draws this swatch with when the layout supplies none. */
    public Material defaultIcon() {
        return defaultIcon;
    }

    /** The full palette in canonical Minecraft dye order. */
    public static List<ColourSwatch> palette() {
        return List.of(values());
    }
}

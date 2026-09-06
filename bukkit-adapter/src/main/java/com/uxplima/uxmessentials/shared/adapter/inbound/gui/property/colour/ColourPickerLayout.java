package com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.colour;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.Material;

import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * The geometry of the shared colour-picker sub-menu: the row count, the slots the 16-colour palette is drawn
 * into and the per-swatch material for each of those slots, the custom-hex / clear / back button slots and their
 * icons, and the background filler. Nothing is hardcoded. Every slot and material comes from
 * {@code modules/management/gui/colour-picker.conf} (operator copy on disk, then the bundled resource, then the
 * code default), the same disk-first fallback the editor sub-layouts use, so a typo never stops the picker
 * opening.
 *
 * <p>{@code paletteSlots} and {@code paletteIcons} are positional and equal length: the i-th palette swatch is
 * drawn into {@code paletteSlots[i]} with {@code paletteIcons[i]}. A swatch with no configured icon falls back
 * to its {@link ColourSwatch#defaultIcon()} stained-glass pane.
 *
 * @param rows the menu height in rows (1..6)
 * @param paletteSlots the slot each palette swatch is drawn into, in {@link ColourSwatch} order
 * @param paletteIcons the material each palette swatch is drawn with, positional with {@code paletteSlots}
 * @param customSlot the custom-hex button slot
 * @param customIcon the custom-hex button material
 * @param clearSlot the clear/default button slot
 * @param clearIcon the clear/default button material
 * @param backSlot the back button slot
 * @param backIcon the back button material
 * @param filler the background filler material
 */
@NullMarked
public record ColourPickerLayout(
        int rows,
        List<Integer> paletteSlots,
        List<Material> paletteIcons,
        int customSlot,
        Material customIcon,
        int clearSlot,
        Material clearIcon,
        int backSlot,
        Material backIcon,
        Material filler) {

    /** The shared module + conf name the picker layout ships and loads under. */
    public static final String MODULE = "management";

    public static final String NAME = "colour-picker";

    /** Slots per inventory row, the unit the window's capacity is counted in. */
    private static final int SLOTS_PER_ROW = 9;

    /**
     * The value a slot key reads as when Configurate cannot make a number of it. Chosen outside every possible
     * window so an unreadable value and an out-of-range one need only one check between them.
     */
    private static final int NOT_A_SLOT = Integer.MIN_VALUE;

    public ColourPickerLayout {
        paletteSlots = List.copyOf(Objects.requireNonNull(paletteSlots, "paletteSlots"));
        paletteIcons = List.copyOf(Objects.requireNonNull(paletteIcons, "paletteIcons"));
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("rows must be 1..6, was " + rows);
        }
        if (paletteSlots.isEmpty()) {
            throw new IllegalArgumentException("paletteSlots must not be empty");
        }
        if (paletteSlots.size() != paletteIcons.size()) {
            throw new IllegalArgumentException("paletteSlots and paletteIcons must be the same length");
        }
        Objects.requireNonNull(customIcon, "customIcon");
        Objects.requireNonNull(clearIcon, "clearIcon");
        Objects.requireNonNull(backIcon, "backIcon");
        Objects.requireNonNull(filler, "filler");
    }

    /** The built-in geometry used when no conf is present or a key is missing: one swatch per palette slot. */
    public static ColourPickerLayout codeDefault() {
        List<Integer> slots = List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29);
        List<Material> icons = new ArrayList<>();
        for (ColourSwatch swatch : ColourSwatch.palette()) {
            icons.add(swatch.defaultIcon());
        }
        return new ColourPickerLayout(
                6,
                slots,
                List.copyOf(icons),
                40,
                Material.ANVIL,
                42,
                Material.BARRIER,
                49,
                Material.ARROW,
                Material.BLACK_STAINED_GLASS_PANE);
    }

    /**
     * Resolve the picker layout, preferring an operator's on-disk edit, then the bundled resource, then the code
     * default. A missing file or an unparsable key logs and falls back, so the picker always opens.
     */
    public static ColourPickerLayout load(Path dataFolder, Logger log) {
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        ColourPickerLayout codeDefault = codeDefault();
        ConfigurationNode root = root(dataFolder, log);
        if (root == null) {
            return codeDefault;
        }
        int rows = clampRows(root.node("rows").getInt(codeDefault.rows()), codeDefault.rows(), log);
        List<Integer> slots = slotList(root.node("palette-slots"), codeDefault.paletteSlots(), rows, log);
        List<Material> icons = paletteIcons(root.node("palette-icons"), slots.size(), codeDefault, log);
        int customSlot = slot(root.node("custom-slot"), codeDefault.customSlot(), rows, "custom-slot", log);
        int clearSlot = slot(root.node("clear-slot"), codeDefault.clearSlot(), rows, "clear-slot", log);
        int backSlot = slot(root.node("back-slot"), codeDefault.backSlot(), rows, "back-slot", log);
        Material customIcon = material(root.node("custom-icon").getString(), codeDefault.customIcon(), log);
        Material clearIcon = material(root.node("clear-icon").getString(), codeDefault.clearIcon(), log);
        Material backIcon = material(root.node("back-icon").getString(), codeDefault.backIcon(), log);
        Material filler = material(root.node("filler").getString(), codeDefault.filler(), log);
        return new ColourPickerLayout(
                rows, slots, icons, customSlot, customIcon, clearSlot, clearIcon, backSlot, backIcon, filler);
    }

    /**
     * Resolve the palette icons so the list is always the same length as the slots: a configured material per
     * slot where present, each swatch's default stained-glass pane otherwise. A conf list shorter than the slots
     * simply leaves the tail at the swatch defaults.
     */
    private static List<Material> paletteIcons(
            ConfigurationNode node, int slotCount, ColourPickerLayout codeDefault, Logger log) {
        List<ColourSwatch> palette = ColourSwatch.palette();
        List<Material> icons = new ArrayList<>();
        List<? extends ConfigurationNode> configured = node.childrenList();
        for (int i = 0; i < slotCount; i++) {
            Material fallback = i < palette.size() ? palette.get(i).defaultIcon() : codeDefault.filler();
            String raw = i < configured.size() ? configured.get(i).getString() : null;
            icons.add(material(raw, fallback, log));
        }
        return icons;
    }

    private static @Nullable ConfigurationNode root(Path dataFolder, Logger log) {
        Path onDisk =
                dataFolder.resolve("modules").resolve(MODULE).resolve("gui").resolve(NAME + ".conf");
        HoconConfigurationLoader loader;
        String origin;
        if (Files.isRegularFile(onDisk)) {
            loader = HoconConfigurationLoader.builder().path(onDisk).build();
            origin = onDisk.toString();
        } else {
            String resource = "modules/" + MODULE + "/gui/" + NAME + ".conf";
            if (ColourPickerLayout.class.getClassLoader().getResource(resource) == null) {
                return null;
            }
            loader = HoconConfigurationLoader.builder()
                    .source(() -> openReader(resource))
                    .build();
            origin = resource;
        }
        try {
            return loader.load();
        } catch (ConfigurateException failure) {
            log.error("failed to load colour picker layout " + origin, failure);
            return null;
        }
    }

    private static int clampRows(int rows, int fallback, Logger log) {
        if (rows < 1 || rows > 6) {
            log.warn("colour picker rows {} out of range 1..6, using {}", rows, fallback);
            return fallback;
        }
        return rows;
    }

    private static Material material(@Nullable String raw, Material fallback, Logger log) {
        if (raw == null) {
            return fallback;
        }
        Material matched = Material.matchMaterial(raw);
        if (matched == null) {
            log.warn("colour picker material {} is unknown, using {}", raw, fallback);
            return fallback;
        }
        return matched;
    }

    /**
     * The palette slots at {@code node}, or the built-in list when the key is absent or any entry is not a slot
     * this window has. The list is positional against the palette icons, so a bad entry is not dropped on its
     * own: a hole would shift every swatch after it onto the wrong colour, which is a worse window than the
     * built-in one.
     *
     * <p>The written value is read off the node before the number is, because reading with a default leaves the
     * default on the node and the warning would then name the sentinel rather than the word an operator typed.
     *
     * <p>The check matters because an entry that is not a number reads as zero through Configurate, and zero is
     * a real slot. {@code palette-slots = ["left", "middle"]} therefore stacked two swatches on slot zero, lost
     * the other fourteen colours, and opened looking like somebody had meant it, with nothing logged. The rows
     * and materials next to it both said so when they were wrong; this one did not.
     */
    private static List<Integer> slotList(ConfigurationNode node, List<Integer> fallback, int rows, Logger log) {
        if (node.virtual() || node.empty()) {
            return fallback;
        }
        List<Integer> values = new ArrayList<>();
        for (ConfigurationNode child : node.childrenList()) {
            @Nullable Object written = child.raw();
            int value = child.getInt(NOT_A_SLOT);
            if (!inWindow(value, rows)) {
                log.warn(
                        "colour picker palette-slots entry {} is not a slot in a {}-row window, using the built-in"
                                + " palette",
                        written,
                        rows);
                return fallback;
            }
            values.add(value);
        }
        return values.isEmpty() ? fallback : values;
    }

    /**
     * One configured button slot, or {@code fallback} when the key is absent, is not a number, or names a slot
     * outside the window. Clamping an out-of-range value into the window is what this replaces: a negative slot
     * silently became slot zero and collided with whatever the palette had drawn there, and a slot past the end
     * was kept and simply never drawn.
     *
     * <p>Absence is answered before the read, not by the sentinel. An absent key and an unreadable one read
     * alike, and collapsing those two as well would warn at every operator who leaves an optional key out.
     */
    private static int slot(ConfigurationNode node, int fallback, int rows, String key, Logger log) {
        if (node.virtual()) {
            return fallback;
        }
        @Nullable Object written = node.raw();
        int value = node.getInt(NOT_A_SLOT);
        if (!inWindow(value, rows)) {
            log.warn("colour picker {} {} is not a slot in a {}-row window, using {}", key, written, rows, fallback);
            return fallback;
        }
        return value;
    }

    /**
     * Whether {@code slot} is drawable in a {@code rows}-row window. {@link #NOT_A_SLOT} is deliberately outside
     * every window, so a value that could not be read as a number and a value that is out of range take the same
     * path here and need no second check.
     */
    private static boolean inWindow(int slot, int rows) {
        return slot >= 0 && slot < rows * SLOTS_PER_ROW;
    }

    private static BufferedReader openReader(String resource) throws IOException {
        InputStream in = ColourPickerLayout.class.getClassLoader().getResourceAsStream(resource);
        if (in == null) {
            throw new java.io.FileNotFoundException(resource);
        }
        return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    }
}

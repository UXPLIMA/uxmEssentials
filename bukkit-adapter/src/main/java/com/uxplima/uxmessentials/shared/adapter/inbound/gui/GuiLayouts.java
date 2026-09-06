package com.uxplima.uxmessentials.shared.adapter.inbound.gui;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;

import com.uxplima.uxmessentials.homes.adapter.inbound.gui.HomeListLayout;
import com.uxplima.uxmessentials.homes.adapter.inbound.gui.IconSelectorLayout;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * Loads a {@link GuiLayout} for a menu, preferring an operator's edit on disk over the bundled default. The
 * resolution mirrors the message-catalog loader: {@code <dataFolder>/modules/<module>/gui/<name>.conf} on
 * disk is read first so an operator's edit takes effect, else the bundled classpath resource
 * {@code modules/<module>/gui/<name>.conf}, else the code default handed in by the caller. A malformed or
 * missing file never throws, it logs and falls back, so a typo in a layout file can never stop a menu from
 * opening.
 *
 * <p>The conf holds layout integers and {@link Material} names only, never localised text. Every material name
 * is resolved through {@link Material#matchMaterial} once here at load time, never on the menu's open path; an
 * unknown name falls back to the corresponding default material so a typo degrades gracefully.
 */
@NullMarked
public final class GuiLayouts {

    private final Path dataFolder;
    private final Logger log;

    public GuiLayouts(Path dataFolder, Logger log) {
        this.dataFolder = Objects.requireNonNull(dataFolder, "dataFolder");
        this.log = Objects.requireNonNull(log, "log");
    }

    /**
     * The plugin data folder these layouts resolve against, so a caller that loads a different disk-first/bundled
     * resource (such as a menu-engine spec) resolves it under the same root rather than threading the path again.
     */
    public Path dataFolder() {
        return dataFolder;
    }

    /**
     * Resolve the layout for {@code module}/{@code name}, falling back to {@code codeDefault} when no conf is
     * present or a conf cannot be parsed.
     */
    public GuiLayout load(String module, String name, GuiLayout codeDefault) {
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(codeDefault, "codeDefault");
        Path onDisk =
                dataFolder.resolve("modules").resolve(module).resolve("gui").resolve(name + ".conf");
        if (Files.isRegularFile(onDisk)) {
            return parse(HoconConfigurationLoader.builder().path(onDisk).build(), onDisk.toString(), codeDefault);
        }
        String resource = "modules/" + module + "/gui/" + name + ".conf";
        if (getClass().getClassLoader().getResource(resource) == null) {
            return codeDefault;
        }
        return parse(
                HoconConfigurationLoader.builder()
                        .source(() -> openReader(resource))
                        .build(),
                resource,
                codeDefault);
    }

    private GuiLayout parse(HoconConfigurationLoader loader, String origin, GuiLayout codeDefault) {
        ConfigurationNode root;
        try {
            root = loader.load();
        } catch (ConfigurateException failure) {
            log.error("failed to load gui layout " + origin, failure);
            return codeDefault;
        }
        int rows = clampRows(root.node("rows").getInt(codeDefault.rows()), codeDefault.rows());
        Material fallbackIcon = material(root.node("fallback-icon").getString(), codeDefault.fallbackIcon());
        Material navIcon = material(root.node("nav-icon").getString(), codeDefault.navIcon());
        int prevSlot = Math.max(0, root.node("prev-slot").getInt(codeDefault.prevSlot()));
        int nextSlot = Math.max(0, root.node("next-slot").getInt(codeDefault.nextSlot()));
        List<Integer> contentSlots = contentSlots(root, codeDefault.contentSlots());
        return new GuiLayout(rows, fallbackIcon, navIcon, prevSlot, nextSlot, contentSlots);
    }

    private int clampRows(int rows, int fallback) {
        if (rows < 1 || rows > 6) {
            log.warn("gui layout rows {} out of range 1..6, using {}", rows, fallback);
            return fallback;
        }
        return rows;
    }

    private Material material(@org.jspecify.annotations.Nullable String name, Material fallback) {
        if (name == null) {
            return fallback;
        }
        Material matched = Material.matchMaterial(name);
        if (matched == null) {
            log.warn("gui layout material {} is unknown, using {}", name, fallback);
            return fallback;
        }
        return matched;
    }

    private List<Integer> contentSlots(ConfigurationNode root, List<Integer> fallback) {
        ConfigurationNode node = root.node("content-slots");
        if (node.virtual() || node.empty()) {
            return fallback;
        }
        List<Integer> slots = new ArrayList<>();
        for (ConfigurationNode child : node.childrenList()) {
            slots.add(child.getInt());
        }
        return slots;
    }

    /**
     * Resolve a fixed-action menu layout, overriding the {@code codeDefault}'s rows, filler, and per-element
     * slot/material from the conf where present. Resolution mirrors {@link #load}; a missing file or unparsable
     * key falls back to the code default so a typo never stops a menu opening.
     */
    public FixedMenuLayout loadFixedMenu(String module, String name, FixedMenuLayout codeDefault) {
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(codeDefault, "codeDefault");
        Path onDisk =
                dataFolder.resolve("modules").resolve(module).resolve("gui").resolve(name + ".conf");
        if (Files.isRegularFile(onDisk)) {
            return parseFixedMenu(
                    HoconConfigurationLoader.builder().path(onDisk).build(), onDisk.toString(), codeDefault);
        }
        String resource = "modules/" + module + "/gui/" + name + ".conf";
        if (getClass().getClassLoader().getResource(resource) == null) {
            return codeDefault;
        }
        return parseFixedMenu(
                HoconConfigurationLoader.builder()
                        .source(() -> openReader(resource))
                        .build(),
                resource,
                codeDefault);
    }

    private FixedMenuLayout parseFixedMenu(
            HoconConfigurationLoader loader, String origin, FixedMenuLayout codeDefault) {
        ConfigurationNode root;
        try {
            root = loader.load();
        } catch (ConfigurateException failure) {
            log.error("failed to load fixed-menu gui layout " + origin, failure);
            return codeDefault;
        }
        int rows = clampRows(root.node("rows").getInt(codeDefault.rows()), codeDefault.rows());
        Material filler = material(root.node("filler-material").getString(), codeDefault.fillerMaterial());
        FixedMenuLayout.Builder builder = FixedMenuLayout.builder(rows, filler);
        ConfigurationNode slotsNode = root.node("slots");
        ConfigurationNode materialsNode = root.node("materials");
        for (Map.Entry<String, Integer> entry : codeDefault.slots().entrySet()) {
            String element = entry.getKey();
            int slot = slotsNode.node(element).getInt(entry.getValue());
            Material defaultMaterial = codeDefault.materials().get(element);
            if (defaultMaterial == null) {
                builder.slotOnly(element, slot);
            } else {
                builder.element(
                        element, slot, material(materialsNode.node(element).getString(), defaultMaterial));
            }
        }
        return builder.build();
    }

    /** Resolve the {@code /home} slot-grid layout, falling back to the code default when no conf parses. */
    public HomeListLayout loadHomeList(String module, String name, HomeListLayout codeDefault) {
        ConfigurationNode root = root(module, name);
        if (root == null) {
            return codeDefault;
        }
        int rows = clampRows(root.node("rows").getInt(codeDefault.rows()), codeDefault.rows());
        List<Integer> homeSlots = intList(root.node("home-slots"), codeDefault.homeSlots());
        Material fallbackIcon = material(root.node("fallback-icon").getString(), codeDefault.fallbackIcon());
        Material emptyIcon = material(root.node("empty-icon").getString(), codeDefault.emptyIcon());
        Material filler = material(root.node("filler").getString(), codeDefault.filler());
        int prevSlot = Math.max(0, root.node("prev-slot").getInt(codeDefault.prevSlot()));
        int nextSlot = Math.max(0, root.node("next-slot").getInt(codeDefault.nextSlot()));
        int pageInfoSlot = Math.max(0, root.node("page-info-slot").getInt(codeDefault.pageInfoSlot()));
        return new HomeListLayout(rows, homeSlots, fallbackIcon, emptyIcon, filler, prevSlot, nextSlot, pageInfoSlot);
    }

    /** Resolve the home-icon picker layout, falling back to the code default when no conf parses. */
    public IconSelectorLayout loadIconSelector(String module, String name, IconSelectorLayout codeDefault) {
        ConfigurationNode root = root(module, name);
        if (root == null) {
            return codeDefault;
        }
        int rows = clampRows(root.node("rows").getInt(codeDefault.rows()), codeDefault.rows());
        List<Material> icons = materialList(root.node("icons"), codeDefault.icons());
        Material navMaterial = material(root.node("nav-material").getString(), codeDefault.navMaterial());
        Material resetMaterial = material(root.node("reset-material").getString(), codeDefault.resetMaterial());
        int resetSlot = Math.max(0, root.node("reset-slot").getInt(codeDefault.resetSlot()));
        int prevSlot = Math.max(0, root.node("prev-slot").getInt(codeDefault.prevSlot()));
        int backSlot = Math.max(0, root.node("back-slot").getInt(codeDefault.backSlot()));
        int nextSlot = Math.max(0, root.node("next-slot").getInt(codeDefault.nextSlot()));
        return new IconSelectorLayout(rows, icons, navMaterial, resetMaterial, resetSlot, prevSlot, backSlot, nextSlot);
    }

    /**
     * Resolve a shared-framework {@link EntityListLayout} (the paginated management list), falling back to the
     * code default when no conf parses. Reads the {@link GuiLayout} geometry plus the list's own {@code filler},
     * its optional {@code create-slot}/{@code create-icon}, and the optional {@code action-slot}/{@code action-icon}
     * of the one side control a caller may wire.
     */
    public EntityListLayout loadEntityList(String module, String name, EntityListLayout codeDefault) {
        ConfigurationNode root = root(module, name);
        if (root == null) {
            return codeDefault;
        }
        GuiLayout base = parseBase(root, codeDefault.base());
        Material filler = material(root.node("filler").getString(), codeDefault.filler());
        java.util.OptionalInt createSlot = optionalSlot(root.node("create-slot"), codeDefault.createSlot());
        Material createIcon = material(root.node("create-icon").getString(), codeDefault.createIcon());
        java.util.OptionalInt actionSlot = optionalSlot(root.node("action-slot"), codeDefault.actionSlot());
        Material actionIcon = material(root.node("action-icon").getString(), codeDefault.actionIcon());
        return new EntityListLayout(base, filler, createSlot, createIcon, actionSlot, actionIcon);
    }

    /**
     * Resolve a shared-framework {@link EntityEditorLayout} (the property grid), falling back to the code
     * default when no conf parses. Reads the row count, the {@code property-slots} list, the {@code back-slot},
     * the optional {@code delete-slot}, and the back/delete/filler materials.
     */
    public EntityEditorLayout loadEntityEditor(String module, String name, EntityEditorLayout codeDefault) {
        ConfigurationNode root = root(module, name);
        if (root == null) {
            return codeDefault;
        }
        int rows = clampRows(root.node("rows").getInt(codeDefault.rows()), codeDefault.rows());
        List<Integer> propertySlots = intList(root.node("property-slots"), codeDefault.propertySlots());
        int backSlot = Math.max(0, root.node("back-slot").getInt(codeDefault.backSlot()));
        java.util.OptionalInt deleteSlot = optionalSlot(root.node("delete-slot"), codeDefault.deleteSlot());
        Material backIcon = material(root.node("back-icon").getString(), codeDefault.backIcon());
        Material deleteIcon = material(root.node("delete-icon").getString(), codeDefault.deleteIcon());
        Material filler = material(root.node("filler").getString(), codeDefault.filler());
        return new EntityEditorLayout(rows, propertySlots, backSlot, deleteSlot, backIcon, deleteIcon, filler);
    }

    /** Re-read the {@link GuiLayout} geometry off {@code root}, mirroring {@link #parse}, against a base default. */
    private GuiLayout parseBase(ConfigurationNode root, GuiLayout base) {
        int rows = clampRows(root.node("rows").getInt(base.rows()), base.rows());
        Material fallbackIcon = material(root.node("fallback-icon").getString(), base.fallbackIcon());
        Material navIcon = material(root.node("nav-icon").getString(), base.navIcon());
        int prevSlot = Math.max(0, root.node("prev-slot").getInt(base.prevSlot()));
        int nextSlot = Math.max(0, root.node("next-slot").getInt(base.nextSlot()));
        List<Integer> contentSlots = contentSlots(root, base.contentSlots());
        return new GuiLayout(rows, fallbackIcon, navIcon, prevSlot, nextSlot, contentSlots);
    }

    /**
     * Read an optional slot: a node value of zero-or-more makes the slot present, a negative value or an absent
     * node leaves it as {@code fallback}. This lets an operator omit a button (create/delete) by dropping its
     * slot from the conf, while a code default carrying a button keeps it unless the conf turns it off with a
     * negative value.
     */
    private java.util.OptionalInt optionalSlot(ConfigurationNode node, java.util.OptionalInt fallback) {
        if (node.virtual() || node.empty()) {
            return fallback;
        }
        int value = node.getInt(-1);
        return value < 0 ? java.util.OptionalInt.empty() : java.util.OptionalInt.of(value);
    }

    /**
     * Load the HOCON root for {@code module}/{@code name}, preferring an operator's on-disk edit over the
     * bundled resource. Returns {@code null} when neither exists or the file cannot be parsed, so the caller
     * falls back to its code default: a typo never stops a menu opening.
     */
    private @org.jspecify.annotations.Nullable ConfigurationNode root(String module, String name) {
        Objects.requireNonNull(module, "module");
        Objects.requireNonNull(name, "name");
        Path onDisk =
                dataFolder.resolve("modules").resolve(module).resolve("gui").resolve(name + ".conf");
        HoconConfigurationLoader loader;
        String origin;
        if (Files.isRegularFile(onDisk)) {
            loader = HoconConfigurationLoader.builder().path(onDisk).build();
            origin = onDisk.toString();
        } else {
            String resource = "modules/" + module + "/gui/" + name + ".conf";
            if (getClass().getClassLoader().getResource(resource) == null) {
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
            log.error("failed to load gui layout " + origin, failure);
            return null;
        }
    }

    private List<Integer> intList(ConfigurationNode node, List<Integer> fallback) {
        if (node.virtual() || node.empty()) {
            return fallback;
        }
        List<Integer> values = new ArrayList<>();
        for (ConfigurationNode child : node.childrenList()) {
            values.add(child.getInt());
        }
        return values.isEmpty() ? fallback : values;
    }

    private List<Material> materialList(ConfigurationNode node, List<Material> fallback) {
        if (node.virtual() || node.empty()) {
            return fallback;
        }
        List<Material> values = new ArrayList<>();
        for (ConfigurationNode child : node.childrenList()) {
            Material matched = Material.matchMaterial(child.getString(""));
            if (matched != null) {
                values.add(matched);
            } else {
                log.warn("gui layout icon {} is unknown, skipping", child.getString(""));
            }
        }
        return values.isEmpty() ? fallback : values;
    }

    private BufferedReader openReader(String resource) throws java.io.IOException {
        InputStream in = getClass().getClassLoader().getResourceAsStream(resource);
        if (in == null) {
            throw new java.io.FileNotFoundException(resource);
        }
        return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
    }
}

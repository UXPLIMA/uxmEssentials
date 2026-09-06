package com.uxplima.uxmessentials.staff.adapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.Material;

import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;

/**
 * Typed view over the {@code modules/staff/config.conf} subtree, read once at wire time from the module's
 * scoped {@link ConfigStore}. It carries the {@code vanish-on-enter} flag, the gadget hotbar layout (one
 * {@link GadgetSpec} per gadget. Its slot, material, and operator-authored MiniMessage display name), and the
 * staff-chat receive permission node. An unknown gadget material or an out-of-range slot is dropped with a
 * single warning rather than failing the wire, so a misconfigured gadget never strands the module.
 *
 * <p>The {@code mode-name} is the single default profile staff mode runs under for now; the model leaves room
 * for named profiles later, but one profile keeps the gadget hotbar simple and matches the shipped config.
 */
@NullMarked
public final class StaffSettings {

    /** The single default mode profile the gadget hotbar is laid out for. */
    public static final String DEFAULT_MODE = "default";

    private static final int HOTBAR_SLOTS = 9;
    private static final int DEFAULT_FOLLOW_INTERVAL_TICKS = 10;
    private static final Material DEFAULT_VANISH_MATERIAL = Material.SLIME_BALL;
    private static final Material DEFAULT_EXAMINE_MATERIAL = Material.BOOK;
    private static final Material DEFAULT_FREEZE_MATERIAL = Material.PACKED_ICE;
    private static final Material DEFAULT_COMPASS_MATERIAL = Material.COMPASS;
    private static final Material DEFAULT_FOLLOW_MATERIAL = Material.LEAD;
    private static final String DEFAULT_VANISH_NAME = "<accent>Vanish</accent>";
    private static final String DEFAULT_EXAMINE_NAME = "<accent>Examine</accent>";
    private static final String DEFAULT_FREEZE_NAME = "<accent>Freeze</accent>";
    private static final String DEFAULT_COMPASS_NAME = "<accent>Navigator</accent>";
    private static final String DEFAULT_FOLLOW_NAME = "<accent>Follow</accent>";
    private static final String DEFAULT_STAFF_CHAT_NODE = "uxmessentials.staff.chat";

    private final boolean vanishOnEnter;
    private final boolean flightOnEnter;
    private final boolean nightVisionOnEnter;
    private final List<GadgetSpec> gadgets;
    private final String staffChatNode;
    private final int followIntervalTicks;

    public StaffSettings(ConfigStore config, Logger log) {
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(log, "log");
        this.vanishOnEnter = config.getBoolean("vanish-on-enter", true);
        this.flightOnEnter = config.getBoolean("flight-on-enter", true);
        this.nightVisionOnEnter = config.getBoolean("night-vision-on-enter", true);
        this.staffChatNode = config.getString("staff-chat.receive-node", DEFAULT_STAFF_CHAT_NODE);
        this.followIntervalTicks = Math.max(1, config.getInt("follow.interval-ticks", DEFAULT_FOLLOW_INTERVAL_TICKS));
        this.gadgets = List.copyOf(parseGadgets(config, log));
    }

    /** Whether entering staff mode vanishes the staff member (soft-coupled to presence; no-op without it). */
    public boolean vanishOnEnter() {
        return vanishOnEnter;
    }

    /** Whether entering staff mode grants flight, reverting to the captured real allowance on exit. */
    public boolean flightOnEnter() {
        return flightOnEnter;
    }

    /** Whether entering staff mode grants night vision, cleared with the rest of the in-mode effects on exit. */
    public boolean nightVisionOnEnter() {
        return nightVisionOnEnter;
    }

    /** The configured gadget hotbar, in declaration order; empty when none are validly configured. */
    public List<GadgetSpec> gadgets() {
        return gadgets;
    }

    /** The permission node identifying the staff-chat audience the channel fans messages out to. */
    public String staffChatNode() {
        return staffChatNode;
    }

    /** How often (in ticks) the FOLLOW task re-teleports a following staff member onto their target. */
    public int followIntervalTicks() {
        return followIntervalTicks;
    }

    private static List<GadgetSpec> parseGadgets(ConfigStore config, Logger log) {
        List<GadgetSpec> specs = new ArrayList<>();
        parseGadget(config, log, StaffGadget.VANISH, "vanish", 0, DEFAULT_VANISH_MATERIAL, DEFAULT_VANISH_NAME)
                .ifPresent(specs::add);
        parseGadget(config, log, StaffGadget.EXAMINE, "examine", 1, DEFAULT_EXAMINE_MATERIAL, DEFAULT_EXAMINE_NAME)
                .ifPresent(specs::add);
        parseGadget(config, log, StaffGadget.FREEZE, "freeze", 2, DEFAULT_FREEZE_MATERIAL, DEFAULT_FREEZE_NAME)
                .ifPresent(specs::add);
        parseGadget(config, log, StaffGadget.COMPASS, "compass", 3, DEFAULT_COMPASS_MATERIAL, DEFAULT_COMPASS_NAME)
                .ifPresent(specs::add);
        parseGadget(config, log, StaffGadget.FOLLOW, "follow", 4, DEFAULT_FOLLOW_MATERIAL, DEFAULT_FOLLOW_NAME)
                .ifPresent(specs::add);
        return specs;
    }

    private static java.util.Optional<GadgetSpec> parseGadget(
            ConfigStore config,
            Logger log,
            StaffGadget gadget,
            String node,
            int defaultSlot,
            Material defaultMaterial,
            String defaultName) {
        String base = "gadgets." + node;
        if (!config.getBoolean(base + ".enabled", true)) {
            return java.util.Optional.empty();
        }
        int slot = config.getInt(base + ".slot", defaultSlot);
        if (slot < 0 || slot >= HOTBAR_SLOTS) {
            log.warn("staff gadget " + node + " has an out-of-range slot " + slot + " (0..8); dropping it");
            return java.util.Optional.empty();
        }
        Material material = parseMaterial(config.getString(base + ".material", ""), defaultMaterial, log, node);
        String name = config.getString(base + ".name", defaultName);
        return java.util.Optional.of(new GadgetSpec(gadget, slot, material, name));
    }

    private static Material parseMaterial(String raw, Material fallback, Logger log, String node) {
        if (raw.isBlank()) {
            return fallback;
        }
        Material matched = Material.matchMaterial(raw);
        if (matched == null || !matched.isItem()) {
            log.warn("staff gadget " + node + " has an unknown material '" + raw + "'; using " + fallback);
            return fallback;
        }
        return matched;
    }

    /**
     * One gadget's hotbar placement: the gadget kind, the hotbar slot ({@code 0..8}), the item material, and the
     * operator-authored MiniMessage display name. A pure value parsed once at wire time.
     *
     * @param gadget the gadget kind this slot carries
     * @param slot the hotbar slot, {@code 0..8}
     * @param material the item material
     * @param name the MiniMessage display name (operator content, not a {@code MessageKey})
     */
    public record GadgetSpec(StaffGadget gadget, int slot, Material material, String name) {

        public GadgetSpec {
            Objects.requireNonNull(gadget, "gadget");
            Objects.requireNonNull(material, "material");
            Objects.requireNonNull(name, "name");
            if (slot < 0 || slot >= HOTBAR_SLOTS) {
                throw new IllegalArgumentException("slot must be 0..8: " + slot);
            }
        }
    }
}

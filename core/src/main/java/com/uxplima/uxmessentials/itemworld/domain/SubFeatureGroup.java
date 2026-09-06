package com.uxplima.uxmessentials.itemworld.domain;

import java.util.Objects;

/**
 * The named sub-feature groups the itemworld surface splits into (docs/10-feature-modules.md §15.10).
 * The module is large enough that, on top of the module-level enable switch, each group is
 * independently disableable via its own {@code itemworld.conf} flag, and every command additionally
 * carries a per-command disable on top of its group.
 *
 * <p>Each constant carries the lowercase {@link #configKey() config key} an operator toggles under
 * {@code modules.itemworld.groups.<key>.enabled}. The key is the single source of truth used by the
 * module to resolve a command's enabled state and by the audit attribution; it mirrors the group rows
 * in the permissions reference.
 */
public enum SubFeatureGroup {

    /**
     * {@code /give /i /item /itemname /itemlore /itemflag /skull /more /repair /repairall /enchant /hat /itemdb
     * /unbreakable /disenchant /itemmodel /editsign /copyinv /endercopy}.
     */
    ITEM_UTILS("item-utils"),

    /** {@code /anvil /workbench /enderchest /grindstone /cartography /loom /smithingtable /stonecutter /furnace}. */
    WORKSTATIONS("workstations"),

    /** {@code /disposal /condense /enderclear}. */
    CLEANUP("cleanup"),

    /** {@code /powertool /powertooltoggle}. */
    POWERTOOL("powertool"),

    /** {@code /spawnmob /spawner /kill /butcher /killall /remove /unlimited}. */
    MOB_ENTITY("mob-entity"),

    /** {@code /time /weather} and the {@code /sun /rain /thunder /day /night} aliases. */
    TIME_WEATHER("time-weather"),

    /** {@code /lightning /fireball /kittycannon /antioch /beezooka /break /tree /bigtree /nuke}. */
    ADMIN_FUN("admin-fun");

    private final String configKey;

    SubFeatureGroup(String configKey) {
        this.configKey = configKey;
    }

    /** The kebab-case key under {@code modules.itemworld.groups.<key>} an operator toggles. */
    public String configKey() {
        return configKey;
    }

    /** Resolve a group by its {@link #configKey()}, throwing on an unknown key. */
    public static SubFeatureGroup byConfigKey(String key) {
        Objects.requireNonNull(key, "key");
        for (SubFeatureGroup group : values()) {
            if (group.configKey.equals(key)) {
                return group;
            }
        }
        throw new IllegalArgumentException("unknown itemworld sub-feature group: " + key);
    }
}

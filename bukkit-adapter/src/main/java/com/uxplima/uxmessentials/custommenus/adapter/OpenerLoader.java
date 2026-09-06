package com.uxplima.uxmessentials.custommenus.adapter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.bukkit.Material;

import com.uxplima.uxmessentials.custommenus.adapter.inbound.listener.OpenerSpec;
import com.uxplima.uxmessentials.custommenus.adapter.inbound.listener.OpenerSpec.GiveOnJoin;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;

/**
 * Reads the operator's {@code menus/openers.conf} into the list of {@link OpenerSpec}s the opener listeners run
 * from. The file declares an {@code openers = [ ... ]} array, one entry per opener. Each entry is parsed and
 * validated independently and, on any problem, logged and skipped rather than aborting the rest, one bad opener
 * never hides the others. An absent file is normal (openers are opt-in) and yields an empty list, exactly like the
 * {@link CustomMenuLoader}'s missing-directory case.
 *
 * <p>An opener naming a menu id that is not registered, or a material that does not resolve, is dropped with a
 * warning: the runtime interact listener also re-checks the menu is still registered, but validating here keeps a
 * typo from silently arming a dead opener item.
 *
 * <p>The same file carries the optional {@code swap-offhand-menu} root key: the menu a player's off-hand swap (F)
 * opens instead of swapping items. It is parsed alongside the openers into an {@link OpenerConfig}, dropped to empty
 * when blank or naming an unregistered menu, so a typo simply leaves the vanilla swap behaviour intact.
 */
public final class OpenerLoader {

    private final Logger log;

    public OpenerLoader(Logger log) {
        this.log = Objects.requireNonNull(log, "log");
    }

    /**
     * The opener-item openers plus the optional off-hand-swap menu, both read from {@code openers.conf} in one pass.
     * {@code swapMenu} is empty when the file names none, names a blank one, or names a menu that is not registered.
     */
    public record OpenerConfig(List<OpenerSpec> openers, Optional<String> swapMenu) {
        public OpenerConfig {
            openers = List.copyOf(openers);
            Objects.requireNonNull(swapMenu, "swapMenu");
        }

        /** The config for an absent, unreadable, or menu-less openers file: no openers and no swap menu. */
        static OpenerConfig empty() {
            return new OpenerConfig(List.of(), Optional.empty());
        }
    }

    /**
     * Parse and validate every opener in {@code openersFile}, keeping only those that name a menu in
     * {@code knownMenus} and a real material. A missing file yields an empty list; an unreadable or malformed file
     * is logged and also yields an empty list, so a broken openers config never blocks the menus themselves. This is
     * the openers-only view of {@link #loadConfig}, kept for callers that do not read the swap menu.
     */
    public List<OpenerSpec> loadFrom(Path openersFile, Set<String> knownMenus) {
        return loadConfig(openersFile, knownMenus).openers();
    }

    /**
     * Parse the openers and the optional {@code swap-offhand-menu} from {@code openersFile} in one read. A missing
     * file yields an {@linkplain OpenerConfig#empty() empty config}; an unreadable or malformed file is logged and
     * also yields an empty config, so a broken openers config never blocks the menus themselves.
     */
    public OpenerConfig loadConfig(Path openersFile, Set<String> knownMenus) {
        Objects.requireNonNull(openersFile, "openersFile");
        Objects.requireNonNull(knownMenus, "knownMenus");
        if (!Files.isRegularFile(openersFile)) {
            return OpenerConfig.empty();
        }
        try {
            ConfigurationNode root =
                    HoconConfigurationLoader.builder().path(openersFile).build().load();
            List<OpenerSpec> openers = new ArrayList<>();
            for (ConfigurationNode entry : root.node("openers").childrenList()) {
                parseOne(entry, knownMenus).ifPresent(openers::add);
            }
            log.info("loaded {} menu openers", openers.size());
            return new OpenerConfig(openers, parseSwapMenu(root, knownMenus));
        } catch (RuntimeException | java.io.IOException invalid) {
            log.warn("could not read menu openers from {} : {}", openersFile, String.valueOf(invalid.getMessage()));
            return OpenerConfig.empty();
        }
    }

    /**
     * The {@code swap-offhand-menu} the file names, or empty when it is absent, blank, or names a menu that is not in
     * {@code knownMenus}: an unregistered id is dropped with a warning so the vanilla off-hand swap keeps working.
     */
    private Optional<String> parseSwapMenu(ConfigurationNode root, Set<String> knownMenus) {
        String menu = root.node("swap-offhand-menu").getString("").strip();
        if (menu.isBlank()) {
            return Optional.empty();
        }
        if (!knownMenus.contains(menu)) {
            log.warn("swap-offhand-menu names an unknown menu {}; ignoring it", menu);
            return Optional.empty();
        }
        return Optional.of(menu);
    }

    /**
     * Parse one {@code openers = [...]} entry into an {@link OpenerSpec}, or empty when it must be skipped: a blank
     * or unknown menu id, or an item whose material does not resolve. The slot defaults to {@code -1} (no fixed
     * slot. The item is added to the first free slot on give) and give-on-join defaults to
     * {@link GiveOnJoin#NEVER}, so an opener with only a menu and an item is a valid click-to-open item.
     */
    private Optional<OpenerSpec> parseOne(ConfigurationNode entry, Set<String> knownMenus) {
        String menu = entry.node("menu").getString("").strip();
        if (menu.isBlank()) {
            log.warn("skipped a menu opener with no menu id");
            return Optional.empty();
        }
        if (!knownMenus.contains(menu)) {
            log.warn("skipped menu opener for unknown menu {}", menu);
            return Optional.empty();
        }
        ConfigurationNode itemNode = entry.node("item");
        String materialToken = itemNode.node("material").getString("");
        Material material = Material.matchMaterial(materialToken);
        if (material == null || material.isAir()) {
            log.warn("skipped menu opener for {} : unknown material '{}'", menu, materialToken);
            return Optional.empty();
        }
        OpenerSpec.Item item =
                new OpenerSpec.Item(material, itemNode.node("name").getString(""), stringList(itemNode.node("lore")));
        int slot = entry.node("slot").getInt(-1);
        return Optional.of(new OpenerSpec(menu, item, slot, giveOnJoin(entry.node("give-on-join"), menu)));
    }

    /** The give-on-join mode named by the node, defaulting to {@link GiveOnJoin#NEVER} with a warning when unknown. */
    private GiveOnJoin giveOnJoin(ConfigurationNode node, String menu) {
        String token = node.getString("never");
        return GiveOnJoin.parse(token).orElseGet(() -> {
            log.warn("menu opener for {} has unknown give-on-join '{}'; treating it as never", menu, token);
            return GiveOnJoin.NEVER;
        });
    }

    /** The string-list value at {@code node}, or an empty list when the node is absent or not a string list. */
    private static List<String> stringList(ConfigurationNode node) {
        if (node.virtual() || node.isNull()) {
            return List.of();
        }
        try {
            @Nullable List<String> values = node.getList(String.class);
            return values == null ? List.of() : List.copyOf(values);
        } catch (org.spongepowered.configurate.serialize.SerializationException malformed) {
            return List.of();
        }
    }
}

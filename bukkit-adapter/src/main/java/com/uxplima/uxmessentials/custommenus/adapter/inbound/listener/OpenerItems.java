package com.uxplima.uxmessentials.custommenus.adapter.inbound.listener;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmlib.item.ItemBuilder;
import com.uxplima.uxmlib.item.PdcFlag;

/**
 * Builds a menu opener {@link ItemStack} from an {@link OpenerSpec} and reads the menu id back off one. The item is
 * tagged in its persistent data with the target menu id under a single cached {@link NamespacedKey}, so the interact
 * listener can recognise an opener a player right-clicks without matching on material or name. An operator is free
 * to reskin the item. The {@link #build} / {@link #menuOf} pair is a clean round-trip: what is written by build is
 * exactly what menuOf reads.
 *
 * <p>It also owns the per-opener "already given" flag the {@link OpenerSpec.GiveOnJoin#FIRST} rule needs, stamped on
 * a player's own PDC under a per-menu key so a first-join item is handed out exactly once and survives a relog.
 *
 * <h2>NamespacedKey discipline</h2>
 * The opener tag key is built once in the constructor. The per-menu given-flag keys are open-ended (one per menu
 * id), so each is built on first use and cached in a {@link ConcurrentHashMap}. The CLAUDE.md rule forbids building
 * a {@link NamespacedKey} on a hot path, and an interact/join event is one. Both key families live under the plugin's
 * own namespace, with the menu-id segment folded to the legal {@code [a-z0-9._-]} alphabet.
 */
public final class OpenerItems {

    private static final String GIVEN_PREFIX = "opener-given-";

    private final Plugin plugin;
    private final NamespacedKey openerKey;
    private final ConcurrentHashMap<String, NamespacedKey> givenKeys = new ConcurrentHashMap<>();

    public OpenerItems(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.openerKey = new NamespacedKey(plugin, "menu-opener");
    }

    /**
     * Build the opener item described by {@code opener}: the material, the rendered name and lore (a blank name or
     * empty lore is simply left off), and the persistent tag carrying the target menu id.
     */
    public ItemStack build(OpenerSpec opener) {
        Objects.requireNonNull(opener, "opener");
        OpenerSpec.Item icon = opener.item();
        ItemBuilder builder = ItemBuilder.of(icon.material());
        if (!icon.name().isBlank()) {
            builder.name(StyledText.render(icon.name()));
        }
        if (!icon.lore().isEmpty()) {
            builder.lore(renderLore(icon.lore()));
        }
        builder.editPersistentData(pdc -> pdc.set(openerKey, PersistentDataType.STRING, opener.menu()));
        return builder.build();
    }

    /** The menu id {@code item} was tagged with, or empty when it carries no opener tag. */
    public Optional<String> menuOf(ItemStack item) {
        Objects.requireNonNull(item, "item");
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(meta.getPersistentDataContainer().get(openerKey, PersistentDataType.STRING));
    }

    /** Whether {@code player} has already been handed the first-join opener for {@code menuId}. */
    public boolean hasGiven(Player player, String menuId) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(menuId, "menuId");
        return PdcFlag.get(player.getPersistentDataContainer(), givenKey(menuId));
    }

    /** Record that {@code player} has now been handed the first-join opener for {@code menuId}. */
    public void markGiven(Player player, String menuId) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(menuId, "menuId");
        PdcFlag.set(player.getPersistentDataContainer(), givenKey(menuId), true);
    }

    private NamespacedKey givenKey(String menuId) {
        return givenKeys.computeIfAbsent(menuId, id -> new NamespacedKey(plugin, GIVEN_PREFIX + sanitize(id)));
    }

    private static List<Component> renderLore(List<String> lore) {
        return lore.stream().map(StyledText::render).toList();
    }

    /** A {@link NamespacedKey} value segment accepts only {@code [a-z0-9._-]}; fold anything else to {@code _}. */
    private static String sanitize(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            boolean legal = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.' || c == '_' || c == '-';
            out.append(legal ? c : '_');
        }
        return out.toString();
    }
}

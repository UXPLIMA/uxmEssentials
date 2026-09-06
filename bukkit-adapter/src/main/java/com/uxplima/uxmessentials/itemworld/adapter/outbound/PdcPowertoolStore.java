package com.uxplima.uxmessentials.itemworld.adapter.outbound;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.itemworld.domain.PowertoolBinding;
import org.jspecify.annotations.NullMarked;

/**
 * The transient powertool binding store: a held item's command lines stamped into the item's
 * {@link PersistentDataContainer} (PDC) rather than a table. This keeps the otherwise-stateless itemworld
 * surface free of persistence. The binding travels with the item the player holds and is gone when the item
 * is, exactly the standard powertool semantics (docs/10-feature-modules.md §15.10, the powertool group).
 *
 * <p>One {@link NamespacedKey} is created once in the constructor and reused, never on the interact hot path
 * ([CLAUDE.md] §NamespacedKey). Command lines are stored newline-joined under that key; reading splits them
 * back. {@link PowertoolBinding#isClear() Clearing} a binding removes the key so a freshly-stamped item carries
 * no residue. All methods must run on the item owner's region thread (they read/write a live {@link ItemStack}).
 */
@NullMarked
public final class PdcPowertoolStore {

    private static final String SEPARATOR = "\n";

    private final NamespacedKey key;

    public PdcPowertoolStore(Plugin plugin) {
        this.key = new NamespacedKey(Objects.requireNonNull(plugin, "plugin"), "powertool");
    }

    /** Apply {@code binding} to {@code item}: store its command lines, or clear the key when the binding is empty. */
    public void apply(ItemStack item, PowertoolBinding binding) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(binding, "binding");
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();
        if (binding.isClear()) {
            container.remove(key);
        } else {
            container.set(key, PersistentDataType.STRING, String.join(SEPARATOR, binding.commands()));
        }
        item.setItemMeta(meta);
    }

    /** The command lines bound to {@code item}, or empty when it carries no powertool binding. */
    public Optional<List<String>> commandsOf(ItemStack item) {
        Objects.requireNonNull(item, "item");
        if (!item.hasItemMeta()) {
            return Optional.empty();
        }
        String stored = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (stored == null || stored.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(List.of(stored.split(SEPARATOR)));
    }
}

package com.uxplima.uxmessentials.staff.adapter;

import java.util.Objects;
import java.util.Optional;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyleTags;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Builds the staff-mode gadget items and reads the gadget tag back off them. One {@link NamespacedKey} is
 * created once in the constructor and reused on every build and every interact read, never on a hot path
 * (CLAUDE.md §NamespacedKey). The tag carries the stable {@link StaffGadget#tag()} value (not the operator's
 * re-skinnable display name), so the right-click listener resolves the gadget regardless of how the item is
 * styled in config.
 *
 * <p>The display name is operator-authored MiniMessage content from {@code modules/staff/config.conf} (like the
 * nametag formats), parsed here once at build time with the shared {@link StyleTags} palette resolver so the
 * config can use the project tokens ({@code <accent>}, {@code <h:'…'>}, …); it is never resolved through the
 * {@code MessageKey} catalog. {@code ItemBuilder.name} defaults the name to upright (non-italic).
 */
@NullMarked
public final class StaffGadgetItems {

    private final NamespacedKey key;
    private final MiniMessage miniMessage;

    public StaffGadgetItems(Plugin plugin) {
        this.key = new NamespacedKey(Objects.requireNonNull(plugin, "plugin"), "staff-gadget");
        this.miniMessage = MiniMessage.miniMessage();
    }

    /** Build the hotbar item for {@code spec}, tagged with its gadget kind so a right-click resolves it. */
    public ItemStack build(StaffSettings.GadgetSpec spec) {
        Objects.requireNonNull(spec, "spec");
        Component name = miniMessage.deserialize(spec.name(), StyleTags.resolver());
        return ItemBuilder.of(spec.material())
                .name(name)
                .editPersistentData(container -> container.set(
                        key, PersistentDataType.STRING, spec.gadget().tag()))
                .build();
    }

    /** The gadget tagged on {@code item}, or empty when it carries no staff-gadget tag. */
    public Optional<StaffGadget> gadgetOf(@Nullable ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return Optional.empty();
        }
        String tag = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (tag == null || tag.isBlank()) {
            return Optional.empty();
        }
        return StaffGadget.fromTag(tag);
    }

    /** Whether {@code item} is any staff gadget: the drop/move guard's cheap predicate. */
    public boolean isGadget(@Nullable ItemStack item) {
        return gadgetOf(item).isPresent();
    }
}

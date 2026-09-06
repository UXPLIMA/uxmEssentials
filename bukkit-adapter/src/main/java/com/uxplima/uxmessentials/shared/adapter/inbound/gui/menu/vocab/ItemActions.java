package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.SerializedItems;
import com.uxplima.uxmessentials.shared.application.port.Logger;

/**
 * The item slice of the menu action vocabulary: the ways a click (or an operator's {@code /menu} spec) can put an
 * item into, take one out of, or overwrite a slot of the viewer's inventory. Registered once at startup into the
 * shared {@link MenuBindings} alongside {@link MenuVocabulary} and the other action packs, so a disk-loaded spec
 * resolves {@code give-item} / {@code take-item} / {@code set-item} the same way a code-registered feature menu does.
 *
 * <p>An item is named the same two ways everywhere here: a plain {@code <material> [amount]} (amount defaults to one),
 * or a {@code b64:}-prefixed fully-serialized stack decoded through the shared {@link SerializedItems} codec, the
 * same token an NPC equipment slot or a {@code /hologram action … give} produces, so an operator writes one grammar
 * across the plugin. The materials and amounts are operator config, so no {@code MessageKey} is involved; the only
 * text any action produces is a diagnostic line on the operator {@code log} when an argument cannot be resolved.
 *
 * <p>Every effect is fail-soft: a blank argument, an unknown material, an undecodable {@code b64:} token, a
 * malformed amount, or a {@code set-item} slot outside the inventory is a logged no-op rather than a throw, and
 * {@link #safe} turns anything a call unexpectedly throws into a logged no-op too. One bad effect must not abort the
 * rest of a chain. All of it runs on the viewer's entity thread, where a click already runs and where the inventory
 * is safe to touch; a give whose inventory is full drops the overflow into the world at the viewer's feet.
 */
public final class ItemActions {

    private ItemActions() {}

    /**
     * Register the item actions into {@code bindings}. {@code log} is the operator console logger a fail-soft or
     * unresolved action warns through. Left separate from {@link MenuVocabulary#registerActions} so that method's
     * existing call-sites stay untouched: the composition root calls both.
     */
    public static void register(MenuBindings bindings, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(log, "log");
        Consumer<MenuActionContext> give = safe("give-item", log, ctx -> giveItem(ctx, log));
        bindings.action("give-item", give);
        bindings.action("give", give);
        bindings.action("take-item", safe("take-item", log, ctx -> takeItem(ctx, log)));
        bindings.action("set-item", safe("set-item", log, ctx -> setItem(ctx, log)));
    }

    /**
     * Wrap {@code body} so any thrown {@link RuntimeException} becomes a one-line operator warning and a no-op rather
     * than escaping into the click dispatch. The same fail-soft contract the sibling action packs and the npc/
     * holograms click runner apply to their effect actions.
     */
    private static Consumer<MenuActionContext> safe(String action, Logger log, Consumer<MenuActionContext> body) {
        return ctx -> {
            try {
                body.accept(ctx);
            } catch (RuntimeException failure) {
                log.warn("event=menu_item_action_failed action={} value={}", action, ctx.arg());
            }
        };
    }

    /**
     * Give the viewer a {@code <material> [amount]} (or {@code b64:} serialized) item; a blank, unknown or undecodable
     * item is a fail-soft skip with an operator warning. Whatever will not fit is dropped into the world at the
     * viewer's feet rather than silently lost.
     */
    private static void giveItem(MenuActionContext ctx, Logger log) {
        Optional<ItemStack> item = resolveItem(ctx.arg());
        if (item.isEmpty()) {
            log.warn("event=menu_item_skipped action=give-item reason=unresolved_item value={}", ctx.arg());
            return;
        }
        give(ctx.player(), item.get());
    }

    /**
     * Add {@code item} to the viewer's inventory, dropping any stack that does not fit into the world at the viewer's
     * feet. Kept inline (rather than reusing the package-private hologram give helper in another package) so the
     * overflow rule lives with the action; {@link Inventory#addItem} returns the leftover keyed by the input index.
     */
    private static void give(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        for (ItemStack overflow : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), overflow);
        }
    }

    /**
     * Take up to {@code <amount>} (default one) of a {@code <material>} from the viewer's inventory. An unknown
     * material or a malformed amount is a fail-soft skip; {@link Inventory#removeItem} removes only up to the count it
     * is given, so taking more than the viewer holds simply empties the matching stacks.
     */
    private static void takeItem(MenuActionContext ctx, Logger log) {
        Optional<ItemArg> parsed = ItemArg.parse(ctx.arg());
        if (parsed.isEmpty()) {
            log.warn("event=menu_item_skipped action=take-item reason=malformed_item value={}", ctx.arg());
            return;
        }
        ItemArg arg = parsed.get();
        Material material = Material.matchMaterial(arg.material());
        if (material == null) {
            log.warn("event=menu_item_skipped action=take-item reason=unknown_material value={}", arg.material());
            return;
        }
        ctx.player().getInventory().removeItem(new ItemStack(material, arg.amount()));
    }

    /**
     * Overwrite slot {@code <slot>} of the viewer's inventory with a {@code <material> [amount]} (or {@code b64:}
     * serialized) item. A malformed argument, an unresolvable item, or a slot outside the inventory is a fail-soft
     * skip with an operator warning: the slot is left as it was.
     */
    private static void setItem(MenuActionContext ctx, Logger log) {
        Optional<SlotItem> parsed = SlotItem.parse(ctx.arg());
        if (parsed.isEmpty()) {
            log.warn("event=menu_item_skipped action=set-item reason=malformed_slot value={}", ctx.arg());
            return;
        }
        SlotItem slotItem = parsed.get();
        Optional<ItemStack> item = resolveItem(slotItem.item());
        if (item.isEmpty()) {
            log.warn("event=menu_item_skipped action=set-item reason=unresolved_item value={}", slotItem.item());
            return;
        }
        Inventory inventory = ctx.player().getInventory();
        if (slotItem.slot() < 0 || slotItem.slot() >= inventory.getSize()) {
            log.warn("event=menu_item_skipped action=set-item reason=slot_out_of_range slot={}", slotItem.slot());
            return;
        }
        inventory.setItem(slotItem.slot(), item.get());
    }

    /**
     * Resolve an item token into a stack: a {@code b64:} token decodes through {@link SerializedItems}, and anything
     * else parses as {@code <material> [amount]}. Empty when the token is blank, the material is unknown, the amount
     * is malformed, or the serialized payload is corrupt: every caller reads an empty result as a fail-soft skip.
     */
    private static Optional<ItemStack> resolveItem(String token) {
        String trimmed = token.strip();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        if (SerializedItems.isSerialized(trimmed)) {
            return SerializedItems.decode(trimmed);
        }
        return ItemArg.parse(trimmed).flatMap(arg -> {
            Material material = Material.matchMaterial(arg.material());
            return material == null ? Optional.empty() : Optional.of(new ItemStack(material, arg.amount()));
        });
    }

    /**
     * A parsed {@code <material> [amount]} item argument: the first whitespace-delimited token is the material name
     * and an optional second token is a positive whole amount defaulting to one. Parsing is Bukkit-free: it resolves
     * no {@link Material}, so plain-JUnit grammar tests can exercise it; a blank value, or a present amount that is
     * not a positive whole number, yields an empty result the actions treat as a fail-soft skip.
     */
    public record ItemArg(String material, int amount) {

        public ItemArg {
            Objects.requireNonNull(material, "material");
        }

        public static Optional<ItemArg> parse(String value) {
            Objects.requireNonNull(value, "value");
            String trimmed = value.strip();
            if (trimmed.isEmpty()) {
                return Optional.empty();
            }
            String[] parts = trimmed.split("\\s+", 2);
            if (parts.length == 1) {
                return Optional.of(new ItemArg(parts[0], 1));
            }
            return parseAmount(parts[1].strip()).map(amount -> new ItemArg(parts[0], amount));
        }

        /** The positive whole amount in {@code token}, or empty when it is not one: a malformed amount is fail-soft. */
        private static Optional<Integer> parseAmount(String token) {
            try {
                int amount = Integer.parseInt(token);
                return amount > 0 ? Optional.of(amount) : Optional.empty();
            } catch (NumberFormatException notANumber) {
                return Optional.empty();
            }
        }
    }

    /**
     * A parsed {@code <slot> <material> [amount]} set-item argument: the first whitespace-delimited token is the
     * destination slot and the rest is the item token ({@code <material> [amount]} or a {@code b64:} stack), kept
     * whole for {@link #resolveItem}. Parsing is Bukkit-free. It neither resolves a {@link Material} nor bounds-checks
     * the slot against a live inventory, so plain-JUnit grammar tests can exercise it; a missing item token or a
     * non-numeric slot yields an empty result the action treats as a fail-soft skip.
     */
    public record SlotItem(int slot, String item) {

        public SlotItem {
            Objects.requireNonNull(item, "item");
        }

        public static Optional<SlotItem> parse(String value) {
            Objects.requireNonNull(value, "value");
            String trimmed = value.strip();
            String[] parts = trimmed.split("\\s+", 2);
            if (parts.length < 2) {
                return Optional.empty();
            }
            try {
                return Optional.of(new SlotItem(Integer.parseInt(parts[0]), parts[1].strip()));
            } catch (NumberFormatException notANumber) {
                return Optional.empty();
            }
        }
    }
}

package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.util.Map;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.itemworld.domain.SubFeatureGroup;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /condense} (alias {@code /compact}): recipe-stack inventory items. Merge fragmented partial stacks of
 * the same item into full stacks, reclaiming slots. Stateless and ACL-thin: the whole-inventory scan groups
 * matching partial stacks and re-lays them out as the fewest full stacks plus one remainder, scheduled on the
 * player's region thread. The count of stacks condensed is reported through
 * {@link ItemworldMessageKey#CONDENSE_DONE}; an inventory with nothing to merge replies
 * {@link ItemworldMessageKey#CONDENSE_NOTHING}.
 */
@NullMarked
public final class CondenseCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.condense.use";

    public CondenseCommand(ItemworldServices services) {
        super(services, "condense", SubFeatureGroup.CLEANUP, "Recipe-stack inventory items.");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(literal())
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::run)
                .build();
    }

    @Override
    public String description() {
        return describe();
    }

    @Override
    public java.util.List<String> aliases() {
        return java.util.List.of("compact");
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        if (!enabled(ctx)) {
            return Command.SINGLE_SUCCESS;
        }
        Player player = player(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        services.kernel().scheduler().onEntity(ref(player), () -> {
            int condensed = condense(player.getInventory());
            if (condensed > 0) {
                reply(ctx, ItemworldMessageKey.CONDENSE_DONE, Map.of("count", String.valueOf(condensed)));
            } else {
                reply(ctx, ItemworldMessageKey.CONDENSE_NOTHING);
            }
        });
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Merge same-type partial stacks in the player's storage. Returns the number of partial (non-max) stacks
     * that were absorbed into another stack: the count surfaced to the player.
     */
    private static int condense(Inventory inventory) {
        ItemStack[] storage = inventory.getStorageContents();
        int condensed = 0;
        for (int i = 0; i < storage.length; i++) {
            ItemStack source = storage[i];
            if (source == null || source.getType().isAir() || source.getAmount() >= source.getMaxStackSize()) {
                continue;
            }
            condensed += absorbInto(storage, i);
        }
        inventory.setStorageContents(storage);
        return condensed;
    }

    private static int absorbInto(ItemStack[] storage, int targetIndex) {
        ItemStack target = storage[targetIndex];
        int merged = 0;
        for (int j = targetIndex + 1; j < storage.length; j++) {
            ItemStack other = storage[j];
            if (other == null || other.getType().isAir() || !other.isSimilar(target)) {
                continue;
            }
            int space = target.getMaxStackSize() - target.getAmount();
            if (space <= 0) {
                break;
            }
            int moved = Math.min(space, other.getAmount());
            target.setAmount(target.getAmount() + moved);
            int remaining = other.getAmount() - moved;
            if (remaining <= 0) {
                storage[j] = null;
                merged++;
            } else {
                other.setAmount(remaining);
            }
        }
        return merged;
    }
}

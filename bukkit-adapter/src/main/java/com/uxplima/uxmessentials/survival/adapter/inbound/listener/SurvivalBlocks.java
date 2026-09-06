package com.uxplima.uxmessentials.survival.adapter.inbound.listener;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import com.uxplima.uxmessentials.survival.domain.BlockPos;
import com.uxplima.uxmessentials.survival.domain.ConnectedBlockSearch;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The Bukkit-side mechanics both harvesting listeners share: run the pure {@link ConnectedBlockSearch} over a live
 * world, break the connected group, and apply the durability and hunger costs.
 *
 * <h2>Folia</h2>
 * The cascade breaks each connected block inline on the {@code BlockBreakEvent} thread. That thread already owns the
 * broken block's region, and the search is bounded ({@code max-blocks}) and grows only through immediate neighbours,
 * so every block it reaches sits in the same local neighbourhood the event thread owns, the region the task guidance
 * says is safe to mutate here without a scheduler hop. The one piece of work that must outlive the event, the
 * tree-feller sapling replant, is deferred through the {@code Scheduler} port by the listener that needs it.
 */
@NullMarked
final class SurvivalBlocks {

    private SurvivalBlocks() {}

    /**
     * The point a broken block's drops belong at: the centre of the block, not the corner {@link Block#getLocation()}
     * returns.
     *
     * <p>{@code World#dropItemNaturally} scatters the item around the point it is handed, {@code +-0.25} on each axis
     * and half an item's height down on Y, which is the same scatter vanilla's {@code Block#popResource} applies
     * around {@code pos + 0.5}. So the API expects the block centre. Handing it {@code block.getLocation()} instead
     * puts the item where four blocks meet and, on Y, below the broken block's own floor: it spawns embedded in the
     * solid neighbours, and the entity's push-out-of-block routine then flings it away or leaves it lodged in the
     * wall where the player never sees it. That is the whole difference between our drops and a vanilla break.
     */
    static Location dropPoint(Block block) {
        return block.getLocation().toCenterLocation();
    }

    /**
     * Whether {@code block} carries a block entity, which is this module's "do not take ownership of these drops"
     * test.
     *
     * <p>What a block entity holds (a chest's inventory, a furnace's smelt, a lectern's book, a campfire's food) is
     * dropped by the server as the block is removed, not by {@link Block#getDrops}. Both ways this module has of
     * taking a break's drops for itself throw that away: cancelling the event's drops discards the server's whole
     * capture list, and {@code setType(AIR)} clears the block entity before the removal that would have dropped its
     * contents. So a block entity is left to vanilla, whole, and the mechanics act on the plain blocks they are
     * meant for.
     *
     * <p>The state is read without a snapshot, so this is a type test on the live block rather than a copy of a
     * chest's inventory on every swing.
     */
    static boolean hasBlockEntity(Block block) {
        return block.getState(false) instanceof TileState;
    }

    /**
     * Break every block connected to {@code origin} that shares its material, up to the search's cap, leaving the
     * origin itself to the event's own break. Drops respect {@code tool} (enchantments, silk touch), and fall on the
     * ground as a natural break: the plain cascade with no auto-drops routing.
     *
     * @return how many blocks were broken, the origin excluded
     */
    static int breakConnected(ConnectedBlockSearch search, Block origin, ItemStack tool) {
        return breakConnected(search, origin, tool, null);
    }

    /**
     * Break the connected group as {@link #breakConnected(ConnectedBlockSearch, Block, ItemStack)} does, but when
     * {@code sink} is present route each broken block's drops through it instead of dropping them on the ground
     * this is how the tree-feller and veinminer cascades feed the auto-drops pipeline (auto-pickup / smelt / sell).
     * With a sink, the block is computed for its drops, cleared to air (no vanilla ground drop), and the sink is
     * handed the captured drops at that block's location; with none, the block breaks naturally. A block carrying a
     * block entity always breaks naturally, sink or not (see {@link #hasBlockEntity}).
     *
     * @return how many blocks were broken, the origin excluded
     */
    static int breakConnected(ConnectedBlockSearch search, Block origin, ItemStack tool, @Nullable CascadeSink sink) {
        return breakGroup(origin, connectedGroup(search, origin), tool, sink);
    }

    /**
     * The connected group of blocks sharing {@code origin}'s material, resolved over the live world with the search,
     * origin first. Split out from the break so a caller can inspect the group before committing to break it - the
     * tree-feller runs its natural-tree check on the group and only fells a group that is a grown tree.
     *
     * @return the connected coordinates, at most the search's cap, the origin first
     */
    static List<BlockPos> connectedGroup(ConnectedBlockSearch search, Block origin) {
        Material type = origin.getType();
        World world = origin.getWorld();
        BlockPos start = new BlockPos(origin.getX(), origin.getY(), origin.getZ());
        return search.from(
                start, pos -> world.getBlockAt(pos.x(), pos.y(), pos.z()).getType() == type);
    }

    /**
     * Break every coordinate in {@code group} except the origin (the vanilla event breaks that one). With a
     * {@code sink} each broken block is computed for its drops, cleared to air (no vanilla ground drop), and the sink
     * is handed the captured drops at that block's location; with none the block breaks naturally onto the ground.
     *
     * @return how many blocks were broken, the origin excluded
     */
    static int breakGroup(Block origin, List<BlockPos> group, ItemStack tool, @Nullable CascadeSink sink) {
        World world = origin.getWorld();
        BlockPos start = new BlockPos(origin.getX(), origin.getY(), origin.getZ());
        int broken = 0;
        for (BlockPos pos : group) {
            if (pos.equals(start)) {
                continue; // the origin is broken by the vanilla event itself; the cascade takes only the extras
            }
            Block block = world.getBlockAt(pos.x(), pos.y(), pos.z());
            if (sink == null || hasBlockEntity(block)) {
                // A natural break keeps the block entity alive through the removal, so a container caught in a
                // cascade spills its contents the way a hand-break does instead of losing them to the clear below.
                block.breakNaturally(tool);
            } else {
                List<ItemStack> drops = new ArrayList<>(block.getDrops(tool));
                Location where = dropPoint(block);
                block.setType(Material.AIR);
                sink.accept(where, drops);
            }
            broken++;
        }
        return broken;
    }

    /** Receives one cascade block's captured drops in place of the vanilla ground drop, at the block's location. */
    @FunctionalInterface
    interface CascadeSink {
        void accept(Location where, List<ItemStack> drops);
    }

    /** Add {@code damage} durability points to the player's main-hand tool, wearing it out when it runs out. */
    static void drainMainHand(Player player, int damage) {
        if (damage <= 0) {
            return;
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        int max = tool.getType().getMaxDurability();
        if (max <= 0 || !(tool.getItemMeta() instanceof Damageable meta)) {
            return; // an empty hand or a non-damageable tool takes no wear
        }
        int worn = meta.getDamage() + damage;
        if (worn >= max) {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            return;
        }
        meta.setDamage(worn);
        tool.setItemMeta((ItemMeta) meta);
        player.getInventory().setItemInMainHand(tool);
    }

    /** Add {@code perBlock} exhaustion for each of {@code blocks} broken, so a large harvest costs hunger. */
    static void addExhaustion(Player player, double perBlock, int blocks) {
        if (perBlock <= 0 || blocks <= 0) {
            return;
        }
        player.setExhaustion(player.getExhaustion() + (float) (perBlock * blocks));
    }

    /** Whether {@code material} is one of the six axe tools (tree-feller's {@code require-axe} gate). */
    static boolean isAxe(Material material) {
        return material.name().endsWith("_AXE");
    }
}

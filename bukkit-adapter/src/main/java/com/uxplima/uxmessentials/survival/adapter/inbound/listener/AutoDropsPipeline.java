package com.uxplima.uxmessentials.survival.adapter.inbound.listener;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.survival.adapter.outbound.AutoSellNotices;
import com.uxplima.uxmessentials.survival.adapter.outbound.PdcSurvivalToggles;
import com.uxplima.uxmessentials.survival.application.port.SurvivalSales;
import com.uxplima.uxmessentials.survival.domain.SellPrices;
import com.uxplima.uxmessentials.survival.domain.SmeltMap;
import org.jspecify.annotations.NullMarked;

/**
 * The composable core of the three break-drop auto-* mechanics (auto-smelt, then auto-sell, then auto-pickup) pulled
 * out of the {@link AutoDropsListener} so the same transform runs on any set of items a break produces, not only the
 * ones the vanilla {@code BlockBreakEvent} carries. The listener feeds it the origin block's drops; the tree-feller
 * and veinminer cascades feed it each extra block's drops so a felled trunk or a mined vein is picked up, smelted, and
 * sold exactly like the block the player broke by hand. Closing the gap where cascade blocks bypassed the pipeline
 * and spilled to the ground.
 *
 * <p>The order is fixed: a drop is smelted first (raw iron becomes an ingot), then the priced ones are sold for coin,
 * then whatever remains is routed into the player's inventory (overflowing to the ground when it is full). Each stage
 * is gated independently by its own config switch, per-player toggle, and use permission, resolved once per break by
 * {@link #stagesFor}, so a server that enables only auto-pickup gets exactly auto-pickup. Auto-sell removes a stack
 * from the set only once the wallet credit has actually succeeded, so a refused deposit never destroys the item: it
 * falls through to pickup or the ground. A completed sale is reported through {@link AutoSellNotices}: the sold stack
 * never reaches the inventory, so without the receipt it reads as a lost drop.
 *
 * <h2>Folia</h2>
 * The inventory, the world drops, and the wallet credit are all applied inline on the caller's thread, the region
 * owning the broken block, so no scheduler hop is needed, matching the harvesting and farm-assist listeners.
 */
@NullMarked
public final class AutoDropsPipeline {

    private static final String PICKUP_PERMISSION = "uxmessentials.survival.autopickup";
    private static final String SMELT_PERMISSION = "uxmessentials.survival.autosmelt";
    private static final String SELL_PERMISSION = "uxmessentials.survival.autosell";

    private final boolean pickupEnabled;
    private final boolean smeltEnabled;
    private final boolean sellEnabled;
    private final SmeltMap smeltMap;
    private final SellPrices sellPrices;
    private final Optional<SurvivalSales> sales;
    private final Optional<AutoSellNotices> notices;
    private final PdcSurvivalToggles toggles;

    public AutoDropsPipeline(
            boolean pickupEnabled,
            boolean smeltEnabled,
            SmeltMap smeltMap,
            boolean sellEnabled,
            SellPrices sellPrices,
            Optional<SurvivalSales> sales,
            Optional<AutoSellNotices> notices,
            PdcSurvivalToggles toggles) {
        this.pickupEnabled = pickupEnabled;
        this.smeltEnabled = smeltEnabled;
        this.smeltMap = Objects.requireNonNull(smeltMap, "smeltMap");
        this.sellEnabled = sellEnabled;
        this.sellPrices = Objects.requireNonNull(sellPrices, "sellPrices");
        this.sales = Objects.requireNonNull(sales, "sales");
        this.notices = Objects.requireNonNull(notices, "notices");
        this.toggles = Objects.requireNonNull(toggles, "toggles");
    }

    /** Which of the three stages are active for {@code player} on this break: enabled, permitted, and toggled on. */
    public Stages stagesFor(Player player) {
        Objects.requireNonNull(player, "player");
        boolean pickup =
                pickupEnabled && player.hasPermission(PICKUP_PERMISSION) && toggles.autoPickupActive(player, true);
        boolean smelt = smeltEnabled && player.hasPermission(SMELT_PERMISSION) && toggles.autoSmeltActive(player, true);
        boolean sell = sellEnabled
                && sales.isPresent()
                && player.hasPermission(SELL_PERMISSION)
                && toggles.autoSellActive(player, false); // opt-in: unlike pickup/smelt, autosell defaults off
        return new Stages(pickup, smelt, sell);
    }

    /**
     * Run the active stages over {@code drops} (a mutable list the caller owns) and deliver the result: smelt in
     * place, sell the priced stacks, then route the rest into the inventory or to the ground at {@code location}.
     */
    public void route(Player player, Location location, List<ItemStack> drops, Stages stages) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(drops, "drops");
        Objects.requireNonNull(stages, "stages");
        if (stages.smelt()) {
            drops.replaceAll(this::smelt);
        }
        if (stages.sell()) {
            sell(player, drops);
        }
        deliver(player, location, drops, stages.pickup());
    }

    /** The smelted counterpart of one drop (same amount), or the drop unchanged when it is not smeltable. */
    private ItemStack smelt(ItemStack item) {
        Optional<String> result = smeltMap.smelted(item.getType().name());
        if (result.isEmpty()) {
            return item;
        }
        Material material = Material.matchMaterial(result.get());
        return material == null ? item : new ItemStack(material, item.getAmount());
    }

    /** Credit the total value of the priced drops in one deposit, removing them only once the credit succeeds. */
    private void sell(Player player, List<ItemStack> drops) {
        List<ItemStack> sold = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (ItemStack item : drops) {
            Optional<BigDecimal> value = sellPrices.saleValue(item.getType().name(), item.getAmount());
            if (value.isPresent()) {
                total = total.add(value.get());
                sold.add(item);
            }
        }
        if (total.signum() > 0 && sales.orElseThrow().credit(BukkitRefs.toRef(player), total)) {
            drops.removeAll(sold);
            // The sale is what makes the item disappear, so the seller is told what it fetched; without the receipt
            // an autosold drop is indistinguishable from a lost one.
            BigDecimal paid = total;
            notices.ifPresent(notice -> notice.sold(player, sold, paid));
        }
    }

    /** Route the remaining drops: into the inventory when auto-pickup is active (ground overflow), else to the ground. */
    private void deliver(Player player, Location location, List<ItemStack> drops, boolean pickup) {
        if (drops.isEmpty()) {
            return;
        }
        World world = location.getWorld();
        for (ItemStack item : drops) {
            if (pickup) {
                Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
                overflow.values().forEach(left -> world.dropItemNaturally(location, left));
            } else {
                world.dropItemNaturally(location, item);
            }
        }
    }

    /**
     * Which stages fire for one break, resolved once so the cascade reuses the same decision the origin break made.
     *
     * @param pickup whether the surviving drops go into the inventory (ground overflow) rather than the ground
     * @param smelt whether smeltable drops are transformed to their smelted result first
     * @param sell whether priced drops are sold into the economy before routing
     */
    public record Stages(boolean pickup, boolean smelt, boolean sell) {

        /** Whether any stage is active: the cheap gate a caller checks before computing a block's drops. */
        public boolean anyActive() {
            return pickup || smelt || sell;
        }
    }
}

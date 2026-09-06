package com.uxplima.uxmessentials.trade.adapter.inbound.gui;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers.ContentClick;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers.ContentProvider;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ContentRegionSpec;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The viewer's own half of a trade window: the block of slots they place items into and take them back out of. It is
 * the only place in the window an item may move, and the rules that keep a trade honest live here: a movement is
 * refused once the trade has settled (so nothing can be pulled out from under a swap in flight) and refused for a
 * blacklisted material, and every accepted movement schedules the re-read that puts the new offer in front of the
 * other player and clears both confirmations.
 *
 * <p>The region is never repainted while the window is up: between a placement and the re-read that records it, the
 * stacks the viewer put down exist only in the window, so painting over them from the last recorded offer would
 * destroy them. What is still in the region when the window closes is handed back through {@link #readBack}, which
 * is the trade's single return path for a cancel.
 */
@NullMarked
final class TradeOfferContent implements ContentProvider {

    private final TradeView view;

    /** The materials refused into the window, resolved once from the module's {@code item-blacklist}. */
    private final Set<Material> blacklist;

    TradeOfferContent(TradeView view, List<Material> blacklist) {
        this.view = Objects.requireNonNull(view, "view");
        this.blacklist = blacklist.isEmpty() ? Set.of() : EnumSet.copyOf(blacklist);
    }

    @Override
    public List<@Nullable ItemStack> render(MenuContext ctx, ContentRegionSpec region) {
        return view.ownOffer(TradeWindow.holder(ctx));
    }

    @Override
    public boolean repaintsOnRedraw() {
        return false;
    }

    @Override
    public boolean allows(MenuContext ctx, ContentRegionSpec region, ContentClick click) {
        TradeHolder holder = TradeWindow.holder(ctx);
        if (!view.acceptsItems(holder)) {
            return false;
        }
        if (click.inserted().filter(this::isBlacklisted).isPresent()) {
            view.refuseBlacklisted(holder);
            return false;
        }
        // The move itself is vanilla's to perform, so the offer is re-read on the viewer's next tick, once the window
        // holds the result of this click.
        view.scheduleSync(holder);
        return true;
    }

    @Override
    public void readBack(MenuContext ctx, ContentRegionSpec region, List<@Nullable ItemStack> contents) {
        view.onWindowClosed(TradeWindow.holder(ctx), contents);
    }

    private boolean isBlacklisted(ItemStack stack) {
        return blacklist.contains(stack.getType());
    }
}

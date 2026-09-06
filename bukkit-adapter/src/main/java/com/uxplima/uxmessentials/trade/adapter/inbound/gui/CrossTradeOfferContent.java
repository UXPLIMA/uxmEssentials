package com.uxplima.uxmessentials.trade.adapter.inbound.gui;

import java.util.List;
import java.util.Objects;

import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers.ContentClick;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers.ContentProvider;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers.ContentRegions;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ContentRegionSpec;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The staked half of a cross-server trade window: the slots the local player fills before confirming. It opens empty
 * and is never repainted while it is up, because what is in it exists only in the window until the confirm reads it
 * into escrow. Movements are refused the moment the side is escrowed, so nothing can be pulled back out from under a
 * two-phase commit already in flight, and whatever is still there when the window closes is returned through
 * {@link #readBack}: the trade's single return path for an abort.
 */
@NullMarked
final class CrossTradeOfferContent implements ContentProvider {

    private final CrossServerTradeView view;
    private final int size;

    CrossTradeOfferContent(CrossServerTradeView view, int size) {
        this.view = Objects.requireNonNull(view, "view");
        this.size = size;
    }

    @Override
    public List<@Nullable ItemStack> render(MenuContext ctx, ContentRegionSpec region) {
        return ContentRegions.copies(null, size);
    }

    @Override
    public boolean repaintsOnRedraw() {
        return false;
    }

    @Override
    public boolean allows(MenuContext ctx, ContentRegionSpec region, ContentClick click) {
        return view.acceptsItems(CrossTradeWindow.holder(ctx));
    }

    @Override
    public void readBack(MenuContext ctx, ContentRegionSpec region, List<@Nullable ItemStack> contents) {
        view.onWindowClosed(CrossTradeWindow.holder(ctx), contents);
    }
}

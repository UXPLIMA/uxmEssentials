package com.uxplima.uxmessentials.trade.adapter.inbound.gui;

import java.util.List;
import java.util.Objects;

import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers.ContentProvider;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ContentRegionSpec;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The other player's half of a trade window: what they have staked, shown as display copies. The region is declared
 * read-only in the spec, so the engine cancels every gesture on it before this provider is consulted at all, which
 * is what makes the mirror unscammable rather than any check here. It repaints on every redraw, because it shows
 * something the trade owns rather than anything this viewer put down: when the other side stakes an item, the redraw
 * is how it appears here.
 */
@NullMarked
final class TradeMirrorContent implements ContentProvider {

    private final TradeView view;

    TradeMirrorContent(TradeView view) {
        this.view = Objects.requireNonNull(view, "view");
    }

    @Override
    public List<@Nullable ItemStack> render(MenuContext ctx, ContentRegionSpec region) {
        return view.mirroredOffer(TradeWindow.holder(ctx));
    }
}

package com.uxplima.uxmessentials.villagers.adapter.inbound.gui;

import java.util.List;
import java.util.Objects;

import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers.ContentClick;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers.ContentProvider;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ContentRegionSpec;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The buy and sell slots of a trade-manager window: seeded once from the villager's current trades, then owned by
 * the editor until the window closes, at which point the whole region is read back into a replacement trade set.
 *
 * <p>The stacks here are trade <em>templates</em>, not deposited items. The villager's recipes are replaced
 * wholesale on close, never appended, so an editor may put anything into a slot and take it back out again. The
 * region is never repainted after the first draw, because the window is what the editor is physically arranging:
 * repainting it from the villager's recipes would undo whatever they had just placed, and a toggle click redraws
 * the window on every flip.
 */
@NullMarked
final class VillagerTradeContent implements ContentProvider {

    private final VillagerManagerView view;
    private final int trades;

    VillagerTradeContent(VillagerManagerView view, int trades) {
        this.view = Objects.requireNonNull(view, "view");
        this.trades = trades;
    }

    @Override
    public List<@Nullable ItemStack> render(MenuContext ctx, ContentRegionSpec region) {
        return view.currentTrades(ctx.subject(VillagerManagerHolder.class), trades);
    }

    @Override
    public boolean repaintsOnRedraw() {
        return false;
    }

    @Override
    public boolean allows(MenuContext ctx, ContentRegionSpec region, ContentClick click) {
        return true;
    }

    @Override
    public void readBack(MenuContext ctx, ContentRegionSpec region, List<@Nullable ItemStack> contents) {
        view.onWindowClosed(ctx.subject(VillagerManagerHolder.class), contents);
    }
}

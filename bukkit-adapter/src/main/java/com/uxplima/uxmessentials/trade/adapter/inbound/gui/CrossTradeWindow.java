package com.uxplima.uxmessentials.trade.adapter.inbound.gui;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers.ContentRegions;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.trade.application.TradeMessageKey;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The cross-server trade window as the operator sees it: the spec in {@code modules/trade/gui/trade-cross.conf} plus
 * the bindings behind it. It reads like the same-server window but each side stakes alone: the counterpart's items
 * are on their own backend and arrive through the escrow, so this window has one content region, the viewer's own,
 * and no mirror to fill.
 */
@NullMarked
public final class CrossTradeWindow {

    static final String SPEC_ID = "trade-cross";
    static final String SPEC_RESOURCE = "modules/trade/gui/trade-cross.conf";
    static final String OFFER_REGION = "trade:cross-offer";

    /** The height the bundled spec is written for; a file that omits {@code rows} falls back to it. */
    private static final int ROWS = 6;

    private final Messages messages;
    private final Menus menus;
    private final MenuSpec spec;
    private final List<Integer> offerSlots;

    public CrossTradeWindow(Messages messages, Menus menus, Path dataFolder, Logger log) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.menus = Objects.requireNonNull(menus, "menus");
        this.spec = MenuSpecs.loadOrBundled(SPEC_RESOURCE, Objects.requireNonNull(dataFolder, "dataFolder"), ROWS, log);
        this.offerSlots = ContentRegions.slots(spec, OFFER_REGION, SPEC_RESOURCE);
    }

    /** How many item slots this side may stake. */
    int perSide() {
        return offerSlots.size();
    }

    /** The window slot the viewer's {@code k}-th staked stack sits in. */
    int offerSlot(int k) {
        return offerSlots.get(k);
    }

    /** Give the spec its behaviour and register it; called once at wiring time, with the view the confirm drives. */
    void register(MenuBindings bindings, CrossServerTradeView view) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(view, "view");
        bindings.placeholder(
                "trade_cross_title",
                ctx -> messages.resolve(
                        ctx.viewer(),
                        TradeMessageKey.TRADE_WINDOW_TITLE,
                        Map.of("player", holder(ctx).remote().name())));
        bindings.action("trade:cross-confirm", action -> view.confirm(action.subject(CrossTradeHolder.class)));
        bindings.content(OFFER_REGION, new CrossTradeOfferContent(view, perSide()));
        menus.registerSpec(SPEC_ID, spec);
    }

    /** Show this window to {@code holder}'s local player, carrying the holder as the menu's subject. */
    void open(CrossTradeHolder holder) {
        menus.open(holder.local(), SPEC_ID, holder);
    }

    /** The live window {@code viewer} has open, when it is still this one. Read on the viewer's own thread. */
    Optional<Inventory> live(PlayerRef viewer) {
        return menus.openWindow(viewer, SPEC_ID);
    }

    /** Read the staked items out of a live window, as a positional array of copies. */
    @Nullable ItemStack[] readOffer(Inventory inv) {
        return ContentRegions.read(inv, offerSlots);
    }

    /** Empty the staked region: the originals leave the window, so nothing is returned twice. */
    void clearOffer(Inventory inv) {
        ContentRegions.clear(inv, offerSlots);
    }

    /** The side of the trade the context's window renders; every binding here is about one holder. */
    static CrossTradeHolder holder(MenuContext ctx) {
        return ctx.subject(CrossTradeHolder.class);
    }
}

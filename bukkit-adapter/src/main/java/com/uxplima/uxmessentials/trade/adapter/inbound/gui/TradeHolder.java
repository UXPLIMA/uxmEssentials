package com.uxplima.uxmessentials.trade.adapter.inbound.gui;

import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.trade.domain.TradeId;
import com.uxplima.uxmessentials.trade.domain.TradeSide;
import org.jspecify.annotations.NullMarked;

/**
 * One participant's side of a trade, carried as the subject of their trade window. It says which trade the window
 * belongs to ({@link #tradeId}), which {@link TradeSide} it renders and edits, who is looking at it, and who they are
 * trading with. Enough for every binding on that window (a placeholder, a button's state, a click, the content
 * region's rules) to reach the one shared {@link TradeExchange} and act on the right half of it.
 *
 * <p>The window itself is the menu engine's; this holder never owns one. It is created when the trade opens and
 * outlives a re-open of the window (the money prompt closes and reopens it), so the one piece of per-viewer screen
 * state that must survive that round trip, which currency the money button is showing, lives here.
 */
@NullMarked
final class TradeHolder {

    private final TradeId tradeId;
    private final TradeSide side;
    private final PlayerRef viewer;
    private final PlayerRef counterpart;

    /**
     * Which allowed currency the single money button currently shows for this viewer. Our economy is multi-currency but
     * the AxTrade money slot is single, so a right-click advances this index and the money button re-renders the next
     * currency; it is per-viewer UI state, only ever touched on the viewer's own region thread.
     */
    private int selectedCurrency;

    TradeHolder(TradeId tradeId, TradeSide side, PlayerRef viewer, PlayerRef counterpart) {
        this.tradeId = Objects.requireNonNull(tradeId, "tradeId");
        this.side = Objects.requireNonNull(side, "side");
        this.viewer = Objects.requireNonNull(viewer, "viewer");
        this.counterpart = Objects.requireNonNull(counterpart, "counterpart");
    }

    /** The trade this view belongs to; the listener resolves the shared exchange from it. */
    TradeId tradeId() {
        return tradeId;
    }

    /** Which side of the trade this view renders and edits. */
    TradeSide side() {
        return side;
    }

    /** The player looking at this view; the render and every settlement are attributed to them. */
    PlayerRef viewer() {
        return viewer;
    }

    /** The player on the other side of this trade, whom the window's title names. */
    PlayerRef counterpart() {
        return counterpart;
    }

    /** The index of the currency the money button currently shows for this viewer. */
    int selectedCurrency() {
        return selectedCurrency;
    }

    /** Advance the selected currency, wrapped into {@code count} allowed currencies (a no-op when {@code count <= 1}). */
    void cycleCurrency(int count) {
        if (count > 1) {
            selectedCurrency = Math.floorMod(selectedCurrency + 1, count);
        }
    }
}

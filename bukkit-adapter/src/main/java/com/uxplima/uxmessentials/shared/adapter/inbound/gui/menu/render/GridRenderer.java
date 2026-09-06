package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render;

import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.GridSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.GridViewState;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuItemSpec;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Paints a {@link GridSpec}'s slot-grid canvas into an open inventory, the grid counterpart of {@link ListViewRenderer}.
 * The canvas mirrors the edited menu: the top rows are the content region, one cell per menu slot on the current page,
 * an item's preview where the menu holds one and the caller's empty placeholder where it does not, and the last row is
 * the control bar (previous/next pagination plus the caller's control buttons). A menu up to five rows fits in one page
 * whose content region is exactly its size; a six-row menu paginates, its {@code 54} slots split across two content
 * regions of {@code 45}, with the surplus cells drawn as inert blockers so a click can never land off the menu.
 *
 * <p>Each filled cell's preview is rendered through the engine's own {@link ItemRenderer} from the {@link MenuItemSpec}
 * the caller supplies, so a consumer outside the engine never touches a renderer. It hands specs, the engine draws
 * them. Every clickable cell (content, nav, control) is recorded onto the holder's {@link GridViewState} so the one
 * listener can route a later click back to the menu slot, page flip or button drawn there.
 */
@NullMarked
public final class GridRenderer {

    /** The tallest window a grid opens into: five content rows plus the control row, the pagination case. */
    private static final int MAX_WINDOW_ROWS = 6;

    private final ItemRenderer itemRenderer;

    public GridRenderer(ItemRenderer itemRenderer) {
        this.itemRenderer = Objects.requireNonNull(itemRenderer, "itemRenderer");
    }

    /**
     * The window height a grid over a menu of {@code menuRows} rows opens into: one taller than the menu so the last
     * row is the control bar, capped at six so a six-row menu paginates rather than overflowing a seventh row.
     */
    public static int windowRows(int menuRows) {
        return Math.min(menuRows + 1, MAX_WINDOW_ROWS);
    }

    /**
     * Fill {@code inv} with {@code spec}'s canvas at {@code page} for {@code viewer}, recording every clickable slot
     * onto {@code state}, and return the page actually drawn (clamped into range). The caller clears the grid's slot
     * routing before calling, so this only records the slots it paints.
     */
    public int populate(Inventory inv, GridSpec spec, GridViewState state, Player live, PlayerRef viewer, int page) {
        Objects.requireNonNull(inv, "inv");
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(live, "live");
        Objects.requireNonNull(viewer, "viewer");
        int contentRows = windowRows(spec.menuRows()) - 1;
        int pageSize = contentRows * GridSpec.COLUMNS;
        int menuSize = spec.menuRows() * GridSpec.COLUMNS;
        int pageCount = Math.max(1, ceilDiv(menuSize, pageSize));
        int clamped = Math.min(Math.max(0, page), pageCount - 1);
        paintContent(inv, spec, state, clamped, pageSize, menuSize, viewer);
        paintControls(inv, spec, state, contentRows, clamped, pageCount);
        return clamped;
    }

    /** Paint one content cell per canvas slot: an item preview, the empty placeholder, or an out-of-range blocker. */
    private void paintContent(
            Inventory inv, GridSpec spec, GridViewState state, int page, int pageSize, int menuSize, PlayerRef viewer) {
        Map<Integer, MenuItemSpec> content = spec.content().get();
        MenuContext ctx = MenuContext.of(viewer, null, page);
        for (int canvas = 0; canvas < pageSize; canvas++) {
            int menuSlot = canvas + page * pageSize;
            if (menuSlot >= menuSize) {
                inv.setItem(canvas, spec.blockerIcon());
                continue;
            }
            MenuItemSpec item = content.get(menuSlot);
            inv.setItem(canvas, item != null ? itemRenderer.render(item, ctx) : spec.emptyIcon());
            state.recordContent(canvas, menuSlot, item != null);
        }
    }

    /** Paint the control row: a blocker backdrop, the conditional prev/next buttons, then the caller's controls. */
    private void paintControls(
            Inventory inv, GridSpec spec, GridViewState state, int contentRows, int page, int pageCount) {
        int base = contentRows * GridSpec.COLUMNS;
        for (int column = 0; column < GridSpec.COLUMNS; column++) {
            inv.setItem(base + column, spec.blockerIcon());
        }
        OptionalInt prev = OptionalInt.empty();
        OptionalInt next = OptionalInt.empty();
        if (page > 0) {
            prev = OptionalInt.of(base + GridSpec.PREV_COLUMN);
            inv.setItem(prev.getAsInt(), spec.prevIcon());
        }
        if (page < pageCount - 1) {
            next = OptionalInt.of(base + GridSpec.NEXT_COLUMN);
            inv.setItem(next.getAsInt(), spec.nextIcon());
        }
        state.recordNav(prev, next);
        for (GridSpec.Control control : spec.controls()) {
            int slot = base + control.column();
            inv.setItem(slot, control.icon());
            state.recordControl(slot, control.onClick());
        }
    }

    /** Ceiling division of two positive ints: the page count of {@code total} cells across pages of {@code per}. */
    private static int ceilDiv(int total, int per) {
        return (total + per - 1) / per;
    }
}

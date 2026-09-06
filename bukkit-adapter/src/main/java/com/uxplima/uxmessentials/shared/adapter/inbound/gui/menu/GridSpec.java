package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuItemSpec;
import org.jspecify.annotations.NullMarked;

/**
 * The recipe the engine draws a slot-grid canvas from, the grid analog of an {@link EntityListSpec} or {@link EditorSpec},
 * opened through {@link Menus#openGrid}. It describes a canvas that mirrors an edited menu's slots: the engine sizes a
 * window one row taller than the menu (capped at six), paints the content rows with the caller's items and reserves the
 * last row for controls, paginating a six-row menu across two pages so the control row never collides with content.
 *
 * <p>Only the layout lives here; the editing behaviour is the {@link GridHandlers} handed alongside. The content is a
 * {@link Supplier} of {@code menuSlot -> MenuItemSpec}, re-read on every draw (the same discipline {@link EntityListSpec}
 * uses for its entities), so a caller mutating its edit model and calling {@link GridView#reRender} shows the change
 * without rebuilding the spec, and the engine renders each preview through its own {@code ItemRenderer}, so the
 * consumer never touches a renderer. The empty / blocker / nav / control icons are {@link ItemStack}s the caller
 * already built from its {@code MessageKey} catalog, so a grid opened through this spec carries no inline user-facing
 * literal of its own.
 */
@NullMarked
public record GridSpec(
        Component title,
        int menuRows,
        Supplier<Map<Integer, MenuItemSpec>> content,
        ItemStack emptyIcon,
        ItemStack blockerIcon,
        ItemStack prevIcon,
        ItemStack nextIcon,
        List<Control> controls) {

    /** Columns in one inventory row, the unit every slot on the canvas is counted in. */
    public static final int COLUMNS = 9;

    /**
     * The control-row column the previous-page button is drawn in. Declared on the contract rather than in the
     * renderer so the rule and its enforcement cannot drift apart: {@link Control} refuses this column, and the
     * renderer paints the button here, from the same constant.
     */
    public static final int PREV_COLUMN = 0;

    /** The control-row column the next-page button is drawn in, reserved on the same terms as {@link #PREV_COLUMN}. */
    public static final int NEXT_COLUMN = 8;

    public GridSpec {
        Objects.requireNonNull(title, "title");
        if (menuRows < 1 || menuRows > 6) {
            throw new IllegalArgumentException("menuRows must be 1..6, was " + menuRows);
        }
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(emptyIcon, "emptyIcon");
        Objects.requireNonNull(blockerIcon, "blockerIcon");
        Objects.requireNonNull(prevIcon, "prevIcon");
        Objects.requireNonNull(nextIcon, "nextIcon");
        controls = List.copyOf(Objects.requireNonNull(controls, "controls"));
    }

    /**
     * The first menu slot in {@code [0, menuRows*COLUMNS)} that no content item occupies on this draw, or empty when
     * canvas is full: where the engine appends an item shift-clicked out of the operator's inventory. The content
     * supplier is re-read here, so appending one item then re-rendering makes the next append land on the following
     * free slot. It scans only the chest slots, never the bottom-inventory range, so a shift-click always fills the
     * grid itself.
     */
    public OptionalInt firstEmptySlot() {
        Map<Integer, MenuItemSpec> filled = content.get();
        int capacity = menuRows * COLUMNS;
        for (int slot = 0; slot < capacity; slot++) {
            if (!filled.containsKey(slot)) {
                return OptionalInt.of(slot);
            }
        }
        return OptionalInt.empty();
    }

    /**
     * One button on the grid's bottom control row: the column {@code 1..7} it sits at within that row (the engine adds
     * the row's base slot so the caller stays window-height agnostic), the already-built icon to place there, and the
     * handler run with the live viewer when it is clicked. {@link #PREV_COLUMN} and {@link #NEXT_COLUMN} belong to the
     * engine's pagination buttons and are refused here.
     *
     * <p>They used to be documented as reserved and accepted anyway. The renderer paints the caller's controls after
     * the nav buttons, so a control in one of those columns covered the nav icon, while the click router asks about a
     * page flip first: the viewer saw one button and got a page turn. Only on a canvas tall enough to paginate, which
     * is the one nobody opens while testing.
     *
     * @param column the control-row column {@code 1..7} the button is drawn in
     * @param icon the prepared icon to place (name/lore already applied by the caller)
     * @param onClick invoked with the live viewer when the button is clicked
     */
    public record Control(int column, ItemStack icon, Consumer<Player> onClick) {

        public Control {
            if (column < 0 || column >= COLUMNS) {
                throw new IllegalArgumentException("column must be 0.." + (COLUMNS - 1) + ", was " + column);
            }
            if (column == PREV_COLUMN || column == NEXT_COLUMN) {
                throw new IllegalArgumentException(
                        "column " + column + " is reserved for the pagination buttons, a control uses 1..7");
            }
            Objects.requireNonNull(icon, "icon");
            Objects.requireNonNull(onClick, "onClick");
        }
    }
}

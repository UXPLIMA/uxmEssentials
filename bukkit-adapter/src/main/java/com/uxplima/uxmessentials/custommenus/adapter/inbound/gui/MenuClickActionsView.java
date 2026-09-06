package com.uxplima.uxmessentials.custommenus.adapter.inbound.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.custommenus.adapter.spec.MenuEditSession;
import com.uxplima.uxmessentials.custommenus.application.CustomMenusMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuSchema;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickKind;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ClickContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.EditableProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.SelectorButton;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * The per-gesture click-action builder opened from the item editor's "Click actions" row. Its {@link #row} is a
 * {@link MenuOpenerProperty} whose click opens a gesture-list selector, one button per {@link ClickKind}, each
 * carrying the count of actions bound to it, and selecting a gesture opens that gesture's action {@link
 * MenuRefListEditor.RefList} (add / edit / remove / reorder, ids chosen from the schema's action catalog). Everything
 * is an engine child window routed through the row's {@link ClickContext}, so the whole flow rides the one holder and
 * teardown; the gesture list's back returns to the item editor and a gesture's list backs to the gesture list.
 */
@NullMarked
public final class MenuClickActionsView {

    /** Every gesture, in the enum's declared order, offered as an addable slot even when it currently binds nothing. */
    private static final List<ClickKind> GESTURES = List.of(ClickKind.values());

    private static final int GESTURE_ROWS = 3;
    private static final List<Integer> GESTURE_SLOTS = List.of(10, 11, 12, 13, 14, 15, 16, 19, 20);
    private static final int BACK_SLOT = 22;

    private static final Material GESTURE_ICON = Material.LEVER;
    private static final Material BACK_ICON = Material.ARROW;
    private static final Material FILLER = Material.BLACK_STAINED_GLASS_PANE;

    private final GuiText guiText;
    private final MenuRefListEditor refList;
    private final Supplier<MenuSchema> schema;

    public MenuClickActionsView(GuiText guiText, MenuRefListEditor refList, Supplier<MenuSchema> schema) {
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.refList = Objects.requireNonNull(refList, "refList");
        this.schema = Objects.requireNonNull(schema, "schema");
    }

    /** The item editor's "Click actions" row: value lore is the total action count, a click opens the gesture list. */
    EditableProperty row(MenuEditSession session, String itemId) {
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(itemId, "itemId");
        return new MenuOpenerProperty(
                CustomMenusMessageKey.MENU_ACTION_EDITOR_CLICK_ACTIONS,
                GESTURE_ICON,
                viewer -> Integer.toString(totalActions(session, itemId)),
                context -> openGestures(context, session, itemId));
    }

    private void openGestures(ClickContext context, MenuEditSession session, String itemId) {
        List<SelectorButton> buttons = new ArrayList<>();
        for (int i = 0; i < GESTURES.size() && i < GESTURE_SLOTS.size(); i++) {
            ClickKind kind = GESTURES.get(i);
            buttons.add(SelectorButton.of(
                    GESTURE_SLOTS.get(i),
                    gestureIcon(context, session, itemId, kind),
                    () -> openGestureActions(context, session, itemId, kind)));
        }
        buttons.add(SelectorButton.of(BACK_SLOT, backIcon(context), context.reopen()));
        context.opener()
                .openSelector(
                        context.viewer(),
                        guiText.text(context.viewer(), CustomMenusMessageKey.MENU_ACTION_EDITOR_GESTURE_TITLE),
                        GESTURE_ROWS,
                        FILLER,
                        buttons);
    }

    private void openGestureActions(ClickContext context, MenuEditSession session, String itemId, ClickKind kind) {
        MenuRefListEditor.RefList list = new MenuRefListEditor.RefList(
                CustomMenusMessageKey.MENU_ACTION_EDITOR_ACTIONS_TITLE,
                Map.of("gesture", kind.name()),
                schema.get().actionIds(),
                () -> session.clickActions(itemId, kind),
                refs -> session.setClickActions(itemId, kind, refs),
                () -> openGestures(context, session, itemId));
        refList.open(context, list);
    }

    private int totalActions(MenuEditSession session, String itemId) {
        int total = 0;
        for (ClickKind kind : GESTURES) {
            total += session.clickActions(itemId, kind).size();
        }
        return total;
    }

    private ItemStack gestureIcon(ClickContext context, MenuEditSession session, String itemId, ClickKind kind) {
        int count = session.clickActions(itemId, kind).size();
        return ItemBuilder.of(GESTURE_ICON)
                .name(guiText.text(
                        context.viewer(),
                        CustomMenusMessageKey.MENU_ACTION_EDITOR_GESTURE,
                        Map.of("gesture", kind.name(), "count", Integer.toString(count))))
                .build();
    }

    private ItemStack backIcon(ClickContext context) {
        return ItemBuilder.of(BACK_ICON)
                .name(guiText.text(context.viewer(), CustomMenusMessageKey.MENU_ACTION_EDITOR_BACK))
                .build();
    }
}

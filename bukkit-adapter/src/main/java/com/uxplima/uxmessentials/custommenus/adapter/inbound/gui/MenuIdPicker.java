package com.uxplima.uxmessentials.custommenus.adapter.inbound.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.custommenus.application.CustomMenusMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ClickContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.SelectorButton;
import com.uxplima.uxmessentials.shared.application.message.GuiMessageKey;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * A paginated picker over the registered ids the {@link com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu
 * .binding.MenuSchema schema} exports, the action ids for a click gesture, the condition ids for a view requirement.
 * It opens as an engine selector child window (through the caller's {@link ClickContext} opener), one paper button per
 * id, prev/next buttons when the catalog spans more than one page, and a back button to the ref-list it was opened
 * from. Picking an id runs {@code onPick} once; nothing here mutates a session. The ref-list editor that opened it
 * owns the arg entry and the append.
 */
@NullMarked
final class MenuIdPicker {

    private static final int ROWS = 6;

    /** The 45 content slots (rows 0 to 4) an id button may occupy; the sixth row carries the nav and back controls. */
    private static final List<Integer> CONTENT_SLOTS = contentSlots();

    private static final int PREV_SLOT = 45;
    private static final int NEXT_SLOT = 53;
    private static final int BACK_SLOT = 49;

    private static final Material ID_ICON = Material.PAPER;
    private static final Material NAV_ICON = Material.ARROW;
    private static final Material BACK_ICON = Material.ARROW;
    private static final Material FILLER = Material.BLACK_STAINED_GLASS_PANE;

    private final GuiText guiText;

    MenuIdPicker(GuiText guiText) {
        this.guiText = Objects.requireNonNull(guiText, "guiText");
    }

    /** Open the picker over {@code ids} on its first page; {@code onPick} receives the chosen id, {@code back} returns. */
    void open(ClickContext context, MessageKey title, List<String> ids, Consumer<String> onPick, Runnable back) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(onPick, "onPick");
        Objects.requireNonNull(back, "back");
        openPage(context, title, List.copyOf(ids), onPick, back, 0);
    }

    private void openPage(
            ClickContext context,
            MessageKey title,
            List<String> ids,
            Consumer<String> onPick,
            Runnable back,
            int page) {
        int pageSize = CONTENT_SLOTS.size();
        int pages = Math.max(1, (ids.size() + pageSize - 1) / pageSize);
        int clamped = Math.max(0, Math.min(page, pages - 1));
        List<SelectorButton> buttons = new ArrayList<>();
        int start = clamped * pageSize;
        for (int i = 0; i < pageSize && start + i < ids.size(); i++) {
            String id = ids.get(start + i);
            buttons.add(SelectorButton.of(CONTENT_SLOTS.get(i), idIcon(context, id), () -> onPick.accept(id)));
        }
        addNav(context, title, ids, onPick, back, buttons, clamped, pages);
        buttons.add(SelectorButton.of(BACK_SLOT, backIcon(context), back));
        context.opener().openSelector(context.viewer(), guiText.text(context.viewer(), title), ROWS, FILLER, buttons);
    }

    private void addNav(
            ClickContext context,
            MessageKey title,
            List<String> ids,
            Consumer<String> onPick,
            Runnable back,
            List<SelectorButton> buttons,
            int page,
            int pages) {
        if (page > 0) {
            buttons.add(SelectorButton.of(
                    PREV_SLOT,
                    navIcon(context, GuiMessageKey.PAGE_PREVIOUS),
                    () -> openPage(context, title, ids, onPick, back, page - 1)));
        }
        if (page < pages - 1) {
            buttons.add(SelectorButton.of(
                    NEXT_SLOT,
                    navIcon(context, GuiMessageKey.PAGE_NEXT),
                    () -> openPage(context, title, ids, onPick, back, page + 1)));
        }
    }

    private ItemStack idIcon(ClickContext context, String id) {
        return ItemBuilder.of(ID_ICON)
                .name(guiText.text(
                        context.viewer(), CustomMenusMessageKey.MENU_ACTION_EDITOR_ID_NAME, Map.of("id", id)))
                .build();
    }

    private ItemStack navIcon(ClickContext context, MessageKey name) {
        return ItemBuilder.of(NAV_ICON)
                .name(guiText.text(context.viewer(), name))
                .build();
    }

    private ItemStack backIcon(ClickContext context) {
        return ItemBuilder.of(BACK_ICON)
                .name(guiText.text(context.viewer(), CustomMenusMessageKey.MENU_ACTION_EDITOR_BACK))
                .build();
    }

    private static List<Integer> contentSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < 45; slot++) {
            slots.add(slot);
        }
        return List.copyOf(slots);
    }
}

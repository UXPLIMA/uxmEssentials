package com.uxplima.uxmessentials.custommenus.adapter.inbound.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.custommenus.application.CustomMenusMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Ref;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ChildClickHandler;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ClickContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.SelectorButton;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.Tiles;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * The reusable editor of a {@code List<Ref>} the click-action and view-requirement builders share: an engine selector
 * child window listing one button per ref (drawn as its {@code id:value} token), gesture-aware like the shared
 * {@code ListProperty} (left/right reorder, shift-left edit, shift-right remove (confirm-gated)) plus an add button
 * and a back button. Add and edit route through the {@link MenuIdPicker} (choose a registered id) then a
 * {@link TextInput} for the value/arg, so every ref an author builds names an id the engine can actually resolve. Each
 * mutation rewrites the whole list through the caller's setter off the tick thread and re-opens the list so the change
 * shows; it holds no session logic, only the {@link RefList} the caller hands each open.
 */
@NullMarked
public final class MenuRefListEditor {

    private static final int ROWS = 6;

    /** The 45 entry slots (rows 0 to 4) a ref button may occupy; the sixth row carries the add and back controls. */
    private static final List<Integer> ENTRY_SLOTS = entrySlots();

    private static final int ADD_SLOT = 48;
    private static final int BACK_SLOT = 50;

    private static final Material ENTRY_ICON = Material.PAPER;
    private static final Material ADD_ICON = Material.LIME_DYE;
    private static final Material BACK_ICON = Material.ARROW;
    private static final Material FILLER = Material.BLACK_STAINED_GLASS_PANE;

    private final GuiText guiText;
    private final Scheduler scheduler;
    private final TextInput textInput;
    private final MenuIdPicker idPicker;
    private final String inputKey;

    public MenuRefListEditor(GuiText guiText, Scheduler scheduler, TextInput textInput, String inputKey) {
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
        this.idPicker = new MenuIdPicker(guiText);
        this.inputKey = Objects.requireNonNull(inputKey, "inputKey");
    }

    /** Open (or re-open) the ref-list child window for {@code list} through the caller's {@link ClickContext} opener. */
    void open(ClickContext context, RefList list) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(list, "list");
        List<Ref> refs = list.current().get();
        List<SelectorButton> buttons = new ArrayList<>();
        for (int i = 0; i < refs.size() && i < ENTRY_SLOTS.size(); i++) {
            buttons.add(entryButton(context, list, refs.get(i), i, ENTRY_SLOTS.get(i)));
        }
        buttons.add(SelectorButton.of(ADD_SLOT, addIcon(context), () -> promptAdd(context, list)));
        buttons.add(SelectorButton.of(BACK_SLOT, backIcon(context), list.back()));
        context.opener()
                .openSelector(
                        context.viewer(),
                        guiText.text(context.viewer(), list.title(), list.titleArgs()),
                        ROWS,
                        FILLER,
                        buttons);
    }

    private SelectorButton entryButton(ClickContext context, RefList list, Ref ref, int index, int slot) {
        ChildClickHandler handler = (rightClick, shiftClick) -> {
            if (shiftClick && !rightClick) {
                promptEdit(context, list, index);
            } else if (shiftClick) {
                confirmRemove(context, list, index);
            } else if (rightClick) {
                move(context, list, index, 1);
            } else {
                move(context, list, index, -1);
            }
        };
        return new SelectorButton(slot, refIcon(context, ref), handler);
    }

    private void promptAdd(ClickContext context, RefList list) {
        idPicker.open(
                context,
                CustomMenusMessageKey.MENU_ACTION_EDITOR_PICK_TITLE,
                list.catalogIds(),
                id -> promptArg(context, list, id, -1),
                () -> open(context, list));
    }

    private void promptEdit(ClickContext context, RefList list, int index) {
        idPicker.open(
                context,
                CustomMenusMessageKey.MENU_ACTION_EDITOR_PICK_TITLE,
                list.catalogIds(),
                id -> promptArg(context, list, id, index),
                () -> open(context, list));
    }

    private void promptArg(ClickContext context, RefList list, String id, int index) {
        textInput.prompt(
                context.player(),
                context.viewer(),
                InputRequest.of(inputKey, CustomMenusMessageKey.MENU_ACTION_EDITOR_ARG_PROMPT, Map.of("id", id)),
                arg -> applyRef(context, list, id, arg, index),
                () -> open(context, list));
    }

    /**
     * Build the ref for {@code id} with the typed {@code arg} and append it (index &lt; 0) or replace the entry at
     * {@code index}. A blank arg or a clear token ({@code -}, {@code none}, {@code clear}) yields a bare ref with no
     * value. Package-private so a view test can drive the pick-then-arg outcome without firing the live anvil.
     */
    void applyRef(ClickContext context, RefList list, String id, String arg, int index) {
        Ref ref = isClear(arg) ? new Ref(id, Map.of()) : new Ref(id, Map.of("value", arg.strip()));
        List<Ref> next = new ArrayList<>(list.current().get());
        if (index >= 0 && index < next.size()) {
            next.set(index, ref);
        } else {
            next.add(ref);
        }
        save(context, list, next);
    }

    private void confirmRemove(ClickContext context, RefList list, int index) {
        context.confirmOpener()
                .openConfirm(
                        context.viewer(),
                        guiText.text(context.viewer(), CustomMenusMessageKey.MENU_ACTION_EDITOR_REMOVE_CONFIRM),
                        () -> remove(context, list, index),
                        () -> open(context, list));
    }

    private void remove(ClickContext context, RefList list, int index) {
        List<Ref> next = new ArrayList<>(list.current().get());
        if (index >= 0 && index < next.size()) {
            next.remove(index);
            save(context, list, next);
        } else {
            open(context, list);
        }
    }

    private void move(ClickContext context, RefList list, int index, int direction) {
        List<Ref> next = new ArrayList<>(list.current().get());
        int target = index + direction;
        if (index >= 0 && index < next.size() && target >= 0 && target < next.size()) {
            next.add(target, next.remove(index));
            save(context, list, next);
        } else {
            open(context, list);
        }
    }

    private void save(ClickContext context, RefList list, List<Ref> next) {
        scheduler.async(() -> {
            list.setter().accept(List.copyOf(next));
            scheduler.onEntity(context.viewer(), () -> open(context, list));
        });
    }

    private ItemStack refIcon(ClickContext context, Ref ref) {
        return ItemBuilder.of(ENTRY_ICON)
                .name(Tiles.blankName())
                .lore(Tiles.titled(
                        guiText.text(
                                context.viewer(),
                                CustomMenusMessageKey.MENU_ACTION_EDITOR_REF_NAME,
                                Map.of("ref", refToken(ref))),
                        guiText.text(context.viewer(), CustomMenusMessageKey.MENU_ACTION_EDITOR_REF_HINTS)))
                .build();
    }

    private ItemStack addIcon(ClickContext context) {
        return ItemBuilder.of(ADD_ICON)
                .name(guiText.text(context.viewer(), CustomMenusMessageKey.MENU_ACTION_EDITOR_ADD))
                .build();
    }

    private ItemStack backIcon(ClickContext context) {
        return ItemBuilder.of(BACK_ICON)
                .name(guiText.text(context.viewer(), CustomMenusMessageKey.MENU_ACTION_EDITOR_BACK))
                .build();
    }

    /** The compact {@code id:value} token a ref is drawn as; a valueless ref shows its bare id. */
    private static String refToken(Ref ref) {
        String value = ref.value();
        return value.isEmpty() ? ref.id() : ref.id() + ":" + value;
    }

    private static boolean isClear(String raw) {
        String value = raw.strip();
        return value.isEmpty()
                || value.equals("-")
                || value.equalsIgnoreCase("none")
                || value.equalsIgnoreCase("clear");
    }

    private static List<Integer> entrySlots() {
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < 45; slot++) {
            slots.add(slot);
        }
        return List.copyOf(slots);
    }

    /**
     * One open of the ref-list editor: the catalog of ids the picker offers, the live read/write of the ref list, the
     * selector title (with its placeholders), and where the back button returns. The current/setter close over a
     * {@link com.uxplima.uxmessentials.custommenus.adapter.spec.MenuEditSession} gesture or view block, so the editor
     * itself stays free of the session.
     */
    record RefList(
            MessageKey title,
            Map<String, String> titleArgs,
            List<String> catalogIds,
            Supplier<List<Ref>> current,
            Consumer<List<Ref>> setter,
            Runnable back) {
        RefList {
            Objects.requireNonNull(title, "title");
            titleArgs = Map.copyOf(Objects.requireNonNull(titleArgs, "titleArgs"));
            catalogIds = List.copyOf(Objects.requireNonNull(catalogIds, "catalogIds"));
            Objects.requireNonNull(current, "current");
            Objects.requireNonNull(setter, "setter");
            Objects.requireNonNull(back, "back");
        }
    }
}

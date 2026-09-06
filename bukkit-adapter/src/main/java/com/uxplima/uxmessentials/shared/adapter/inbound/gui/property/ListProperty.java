package com.uxplima.uxmessentials.shared.adapter.inbound.gui.property;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.LayoutFit;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.Tiles;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * A property whose click opens a sub-menu for editing a list of string entries: add, remove (confirm-gated),
 * reorder, and edit each line. Backed by a single {@code List<String>} the caller reads and writes through a
 * use case (e.g. a hologram's text lines). The sub-menu draws one button per entry into the configured entry
 * slots: left-click moves the line up, right-click moves it down, shift-left-click edits the line through an
 * anvil, and shift-right-click removes it (confirm-gated). An add button opens an anvil for a new line. Each mutation
 * rewrites the whole list through the setter off the tick thread via the shared {@link Scheduler}, then
 * re-opens the sub-menu so the change shows.
 *
 * <p>Every label, hint, and the sub-menu title are catalog keys resolved through {@link GuiText}; the slots
 * and materials come from the caller (the editor layout conf), so nothing is hardcoded. The setter is the
 * module's existing application use case wrapped as a {@link Consumer}; this property holds no domain logic.
 *
 * <p>The sub-menu opens as an engine child window the one menu listener routes. Its entry/add/back buttons are
 * {@link SelectorButton}s and a removal gates through the context's {@link ConfirmOpener} confirm child, and each
 * mutation reopens the engine list, so the whole flow stays on a single holder and teardown.
 */
@NullMarked
public final class ListProperty implements EditableProperty {

    private final String inputKey;
    private final MessageKey label;
    private final Material icon;
    private final GuiText guiText;
    private final Supplier<List<String>> current;
    private final Consumer<List<String>> setter;
    private final ListPropertyText keys;
    private final ListPropertyLayout layout;
    private final TextInput textInput;
    private final Scheduler scheduler;

    public ListProperty(
            String inputKey,
            MessageKey label,
            Material icon,
            GuiText guiText,
            Supplier<List<String>> current,
            Consumer<List<String>> setter,
            ListPropertyText keys,
            ListPropertyLayout layout,
            TextInput textInput,
            Scheduler scheduler) {
        this.inputKey = Objects.requireNonNull(inputKey, "inputKey");
        this.label = Objects.requireNonNull(label, "label");
        this.icon = Objects.requireNonNull(icon, "icon");
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.current = Objects.requireNonNull(current, "current");
        this.setter = Objects.requireNonNull(setter, "setter");
        this.keys = Objects.requireNonNull(keys, "keys");
        this.layout = Objects.requireNonNull(layout, "layout");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public MessageKey label() {
        return label;
    }

    @Override
    public Material icon() {
        return icon;
    }

    @Override
    public String valueLore(PlayerRef viewer) {
        Objects.requireNonNull(viewer, "viewer");
        return Integer.toString(current.get().size());
    }

    @Override
    public void onClick(ClickContext context) {
        Objects.requireNonNull(context, "context");
        scheduler.onEntity(context.viewer(), () -> open(context));
    }

    /**
     * Open the list sub-menu as an engine child window: the per-entry/add/back buttons are handed to the engine opener
     * as {@link SelectorButton}s so the one menu listener routes them. The entry button is gesture-aware (left/right
     * move, shift-left edit, shift-right remove); the add and back buttons ignore the gesture. After any mutation the
     * list reopens itself through this same method, so a change shows; back reopens the parent editor via the context.
     */
    private void open(ClickContext context) {
        List<String> entries = current.get();
        List<Integer> slots = layout.entrySlots();
        List<SelectorButton> buttons = new ArrayList<>();
        int drawn = LayoutFit.drawable("list entry-slots", entries.size(), slots);
        for (int i = 0; i < drawn; i++) {
            buttons.add(engineEntryButton(context, entries.get(i), i, slots.get(i)));
        }
        buttons.add(SelectorButton.of(layout.addSlot(), addIcon(context), () -> add(context)));
        buttons.add(SelectorButton.of(
                layout.backSlot(), backIcon(context), () -> context.reopen().run()));
        context.opener()
                .openSelector(
                        context.viewer(),
                        guiText.text(context.viewer(), keys.title()),
                        layout.rows(),
                        layout.fillerIcon(),
                        buttons);
    }

    /** A gesture-aware engine entry button: shift-left edits, shift-right removes, left moves up, right moves down. */
    private SelectorButton engineEntryButton(ClickContext context, String entry, int index, int slot) {
        ItemStack icon = entryIcon(context, entry);
        ChildClickHandler handler = (rightClick, shiftClick) -> {
            if (shiftClick && !rightClick) {
                edit(context, index, entry);
            } else if (shiftClick) {
                confirmRemove(context, index);
            } else if (rightClick) {
                move(context, index, 1);
            } else {
                move(context, index, -1);
            }
        };
        return new SelectorButton(slot, icon, handler);
    }

    private ItemStack entryIcon(ClickContext context, String entry) {
        return ItemBuilder.of(layout.entryIcon())
                .name(Tiles.blankName())
                .lore(Tiles.titled(
                        guiText.text(context.viewer(), keys.entryName(), Map.of("entry", entry)),
                        guiText.text(context.viewer(), keys.entryHints())))
                .build();
    }

    private ItemStack addIcon(ClickContext context) {
        return ItemBuilder.of(layout.addIcon())
                .name(guiText.text(context.viewer(), keys.addName()))
                .build();
    }

    private ItemStack backIcon(ClickContext context) {
        return ItemBuilder.of(layout.backIcon())
                .name(guiText.text(context.viewer(), keys.backName()))
                .build();
    }

    private void add(ClickContext context) {
        textInput.prompt(
                context.player(),
                context.viewer(),
                InputRequest.of(inputKey, keys.addPrompt()),
                text -> applyAdd(context, text),
                () -> open(context));
    }

    /** Append a non-blank submitted line; a blank line reopens the list unchanged. Package-private for unit tests. */
    void applyAdd(ClickContext context, String text) {
        if (text.isBlank()) {
            open(context);
            return;
        }
        List<String> next = new ArrayList<>(current.get());
        next.add(text);
        save(context, next);
    }

    private void edit(ClickContext context, int index, String existing) {
        textInput.prompt(
                context.player(),
                context.viewer(),
                InputRequest.of(inputKey, keys.editPrompt(), Map.of("entry", existing)),
                text -> applyEdit(context, index, text),
                () -> open(context));
    }

    /** Replace entry {@code index} with a non-blank submitted line; otherwise reopen unchanged. Package-private for tests. */
    void applyEdit(ClickContext context, int index, String text) {
        List<String> next = new ArrayList<>(current.get());
        if (!text.isBlank() && index < next.size()) {
            next.set(index, text);
            save(context, next);
            return;
        }
        open(context);
    }

    /** Gate a removal behind an engine confirm child: confirming removes the entry and reopens the list, declining reopens. */
    private void confirmRemove(ClickContext context, int index) {
        context.confirmOpener()
                .openConfirm(
                        context.viewer(),
                        guiText.text(context.viewer(), keys.removeConfirm()),
                        () -> remove(context, index),
                        () -> reopen(context));
    }

    private void remove(ClickContext context, int index) {
        List<String> next = new ArrayList<>(current.get());
        if (index >= 0 && index < next.size()) {
            next.remove(index);
            save(context, next);
        } else {
            reopen(context);
        }
    }

    private void move(ClickContext context, int index, int direction) {
        List<String> next = new ArrayList<>(current.get());
        int target = index + direction;
        if (index >= 0 && index < next.size() && target >= 0 && target < next.size()) {
            String moved = next.remove(index);
            next.add(target, moved);
            save(context, next);
        } else {
            reopen(context);
        }
    }

    private void save(ClickContext context, List<String> next) {
        scheduler.async(() -> {
            setter.accept(List.copyOf(next));
            reopen(context);
        });
    }

    /** Reopen the list sub-menu on the viewer's entity thread. */
    private void reopen(ClickContext context) {
        scheduler.onEntity(context.viewer(), () -> open(context));
    }
}

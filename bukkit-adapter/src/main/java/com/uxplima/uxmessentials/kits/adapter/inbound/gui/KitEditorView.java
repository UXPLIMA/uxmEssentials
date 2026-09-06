package com.uxplima.uxmessentials.kits.adapter.inbound.gui;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.kits.application.KitEditor;
import com.uxplima.uxmessentials.kits.application.KitsMessageKey;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.kits.domain.KitItem;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.MenuTitles;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Opens {@code /kit editor} as an editable managed window seeded with the kit's current stacks at their
 * definition-order slots ({@link KitGuiLayout#seedEditable}), and on close overwrites the kit's items from the
 * window's final contents through the {@link KitEditor#save} use case. The window is a private six-row chest the
 * editor drags items into and out of freely; the kit's stored data and the editor's own live inventory are never
 * touched while it is open, so the round-trip is dupe-safe: an item moved inside the window is moved, an item the
 * editor brought in is theirs to bring, and on close the kit is overwritten wholesale (replace, never append)
 * there is no path that accumulates a stack twice. The non-item kit settings (cooldown, one-time, permission,
 * cost) are carried untouched from the {@link KitEditorHolder}.
 *
 * <p>{@link #open} touches the live player (it builds and opens an inventory in their screen), so the caller
 * schedules it on the editor's entity thread through the kernel {@link Scheduler}; the persist is dispatched
 * async off that same scheduler because it writes the kit's file. Every open window is tracked so a single save
 * claims it (whichever of the close handler or {@link #flushAll} (on module stop) reaches it first) and a
 * still-open window is never saved twice.
 */
@NullMarked
public final class KitEditorView {

    static final int ROWS = 6;

    private final Messages messages;
    private final KitEditor kitEditor;
    private final Scheduler scheduler;
    private final Set<KitEditorHolder> open = ConcurrentHashMap.newKeySet();

    public KitEditorView(Messages messages, KitEditor kitEditor, Scheduler scheduler) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.kitEditor = Objects.requireNonNull(kitEditor, "kitEditor");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /** Open the editable window for {@code kit} for {@code editor}, scheduled on the editor's entity thread. */
    public void open(Player player, PlayerRef editor, KitDefinition kit) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(editor, "editor");
        Objects.requireNonNull(kit, "kit");
        scheduler.onEntity(editor, () -> {
            Player live = Bukkit.getPlayer(editor.uuid());
            if (live != null && live.isOnline()) {
                openResolved(live, editor, kit);
            }
        });
    }

    /** Save every still-open editor window and forget it; called on module stop so no edit is lost on disable. */
    public void flushAll() {
        for (KitEditorHolder holder : Set.copyOf(open)) {
            if (open.remove(holder)) {
                persist(holder);
            }
        }
    }

    /** The number of editor windows currently open (the {@code open-guis=N} a doctor line could report). */
    public int openCount() {
        return open.size();
    }

    /** Claim {@code holder}'s window on close and persist its contents as the kit's new items. */
    void onClose(KitEditorHolder holder) {
        Objects.requireNonNull(holder, "holder");
        if (open.remove(holder)) {
            persist(holder);
        }
    }

    private void openResolved(Player player, PlayerRef editor, KitDefinition kit) {
        KitEditorHolder holder = new KitEditorHolder(editor, kit);
        Inventory menu = Bukkit.createInventory(holder, ROWS * KitGuiLayout.SLOTS_PER_ROW, title(editor, kit));
        holder.attach(menu);
        KitGuiLayout.seedEditable(menu, kit.items());
        open.add(holder);
        player.openInventory(menu);
    }

    private void persist(KitEditorHolder holder) {
        List<KitItem> items = KitGuiLayout.encode(holder.getInventory());
        KitDefinition replacement = holder.kit().withItems(items);
        scheduler.async(() -> kitEditor.save(holder.editor(), replacement));
    }

    private Component title(PlayerRef editor, KitDefinition kit) {
        String rendered = messages.resolve(
                editor,
                KitsMessageKey.KIT_EDITOR_GUI_TITLE,
                Map.of("kit", kit.id().value()));
        return MenuTitles.centre(StyledText.render(rendered));
    }
}

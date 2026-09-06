package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.EntityListSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.GridCaptureHandler;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.GridHandlers;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.GridSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.GridView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.api.event.MenuClickEvent;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ActionRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ConditionRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ContentProviderRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.PagedListSourceRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval.PageRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval.PagedResult;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers.ContentClick;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers.ContentProvider;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.EditorRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.GridRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.ListViewRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuItemMark;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.MenuRenderer;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render.RenderedSlot;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickBranch;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickKind;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ContentRegionSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Continuation;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ItemDragSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ItemRuleSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ItemType;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ListControlSyntax.SortDirection;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ListSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuItemSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Ref;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Requirement;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.RequirementSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ChildClickHandler;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ClickContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ConfirmOpener;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.EditableProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.SelectorOpener;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The single listener every open menu routes through. It recognises a menu window by its {@link MenuHolder}, which
 * carries all per-open state, so the engine keeps no player-keyed side map and nothing can leak when a player quits
 * mid-menu. A click is always cancelled (menus never let an item be taken), then routed: a pagination button
 * re-renders the same holder on a new page, while any other slot fires the actions its spec bound to the gesture.
 * Actions run through the {@link Scheduler} entity hop so feature use-cases land on the viewer's region thread, and
 * the live {@link Player} is resolved from the viewer's UUID at that point. A viewer who logged off in the gap is
 * simply skipped.
 *
 * <p>Closing the window (whether the player closed it or quit with it open) funnels through one
 * {@link #closeMenu} choke-point that stops the refresh task. Both close paths are idempotent, so a double close is
 * a harmless no-op.
 */
@NullMarked
public final class MenuListener implements Listener {

    /** Operator diagnostics for a paged list flip: an overflowing source, or a query that threw, logged, page kept. */
    private static final Logger LOG = Logger.getLogger(MenuListener.class.getName());

    private final MenuRenderer renderer;
    private final ActionRegistry actions;
    private final ConditionRegistry conditions;
    private final Scheduler scheduler;
    private final Plugin plugin;

    /**
     * The streaming list sources a page flip re-queries. A paged list holds no cached corpus to re-slice, only the
     * page on screen, so flipping it must ask the source for the next page, which this registry resolves by id.
     * Empty on every engine wired without one (a plain/spec-only test fixture and any production menu set with no
     * paged source): no menu on such an engine carries a paged view, so the flip path never reaches this registry and
     * a flip stays the synchronous cache re-slice it always was.
     */
    private final PagedListSourceRegistry pagedLists;

    /**
     * The editor renderer, present only on an engine wired for typed property editors. It is what a property's
     * reopen hook repaints the editor through; a spec-only engine leaves it null and never opens an editor, so the
     * editor branch is never reached.
     */
    @Nullable private final EditorRenderer editorRenderer;

    /**
     * The opener a property's click hook uses to show its picker as an engine child window, threaded into the
     * {@link ClickContext} on the editor path. Null on a spec-only engine and on an editor engine wired without it,
     * in which case a property that opens a picker falls back to its uxmLib selector: both runtimes coexist.
     */
    @Nullable private final SelectorOpener selectorOpener;

    /**
     * The opener a property's click hook uses to gate a destructive step behind an engine confirm child, threaded into
     * the {@link ClickContext} on the editor path alongside the selector opener. Null when the engine is wired without
     * it, in which case a property that confirms a removal falls back to its uxmLib {@code ConfirmMenu}.
     */
    @Nullable private final ConfirmOpener confirmOpener;

    /**
     * The text-prompt capability an {@code input:} step drives, or null on an engine wired without one (every menu
     * test fixture and the composition until the text-input seam is built). When null an {@code input:} step cannot
     * prompt, so it runs its cancel refs and abandons the continuation rather than dead-ending; a {@code confirm:}
     * step needs only {@link #confirmOpener} and is unaffected.
     */
    @Nullable private final MenuTextPrompt textPrompt;

    /**
     * The providers behind a menu's {@code content {}} regions. Null on an engine wired without any, every
     * spec-only test fixture. In which case a region refuses every click and hands nothing back on close, exactly
     * what an unregistered provider gets. Only production wiring, which holds the feature-populated registry,
     * passes it.
     */
    @Nullable private final ContentProviderRegistry contents;

    /** Re-paints an entity list's page on a nav click; stateless, so one instance serves every list this routes. */
    private final ListViewRenderer listViewRenderer = new ListViewRenderer();

    /**
     * The server-wide click-cooldown floor in milliseconds. A menu that sets its own {@code click-cooldown} overrides
     * this; a menu that sets none (or sets {@code 0}) inherits it. {@code 0} here as well means no throttling at all,
     * which is the default, so an engine wired without an operator floor behaves exactly as before.
     */
    private final long defaultClickCooldownMs;

    /**
     * The wall-clock source the anti-spam window measures against, injected so the cooldown is deterministically
     * testable. Production wires {@code System::currentTimeMillis}; a test hands a clock it advances by hand.
     */
    private final LongSupplier clock;

    public MenuListener(
            MenuRenderer renderer,
            ActionRegistry actions,
            ConditionRegistry conditions,
            Scheduler scheduler,
            Plugin plugin) {
        this(renderer, actions, conditions, scheduler, plugin, null, null, null);
    }

    public MenuListener(
            MenuRenderer renderer,
            ActionRegistry actions,
            ConditionRegistry conditions,
            Scheduler scheduler,
            Plugin plugin,
            @Nullable EditorRenderer editorRenderer) {
        this(renderer, actions, conditions, scheduler, plugin, editorRenderer, null, null);
    }

    public MenuListener(
            MenuRenderer renderer,
            ActionRegistry actions,
            ConditionRegistry conditions,
            Scheduler scheduler,
            Plugin plugin,
            @Nullable EditorRenderer editorRenderer,
            @Nullable SelectorOpener selectorOpener) {
        this(renderer, actions, conditions, scheduler, plugin, editorRenderer, selectorOpener, null);
    }

    public MenuListener(
            MenuRenderer renderer,
            ActionRegistry actions,
            ConditionRegistry conditions,
            Scheduler scheduler,
            Plugin plugin,
            @Nullable EditorRenderer editorRenderer,
            @Nullable SelectorOpener selectorOpener,
            @Nullable ConfirmOpener confirmOpener) {
        this(
                renderer,
                actions,
                conditions,
                scheduler,
                plugin,
                editorRenderer,
                selectorOpener,
                confirmOpener,
                0L,
                System::currentTimeMillis);
    }

    /**
     * The anti-spam-aware form carrying the cooldown floor and its clock, kept so every existing construction and test
     * (all of which predate paged sources) compiles unchanged. Delegates to the canonical constructor with an empty
     * {@link PagedListSourceRegistry}, so a listener built here can never flip a paged list. No menu it routes carries
     * a paged view, and a flip stays the synchronous cache re-slice it always was.
     */
    public MenuListener(
            MenuRenderer renderer,
            ActionRegistry actions,
            ConditionRegistry conditions,
            Scheduler scheduler,
            Plugin plugin,
            @Nullable EditorRenderer editorRenderer,
            @Nullable SelectorOpener selectorOpener,
            @Nullable ConfirmOpener confirmOpener,
            long defaultClickCooldownMs,
            LongSupplier clock) {
        this(
                renderer,
                actions,
                conditions,
                scheduler,
                plugin,
                editorRenderer,
                selectorOpener,
                confirmOpener,
                defaultClickCooldownMs,
                clock,
                new PagedListSourceRegistry(),
                null);
    }

    /**
     * The paged-source-aware form, kept so every construction and test that predates the text-input seam compiles
     * unchanged. Delegates to the canonical constructor with no text prompt, so a listener built here cannot drive an
     * {@code input:} step (it runs that step's cancel refs instead): no menu such a listener routes carries one.
     */
    public MenuListener(
            MenuRenderer renderer,
            ActionRegistry actions,
            ConditionRegistry conditions,
            Scheduler scheduler,
            Plugin plugin,
            @Nullable EditorRenderer editorRenderer,
            @Nullable SelectorOpener selectorOpener,
            @Nullable ConfirmOpener confirmOpener,
            long defaultClickCooldownMs,
            LongSupplier clock,
            PagedListSourceRegistry pagedLists) {
        this(
                renderer,
                actions,
                conditions,
                scheduler,
                plugin,
                editorRenderer,
                selectorOpener,
                confirmOpener,
                defaultClickCooldownMs,
                clock,
                pagedLists,
                null);
    }

    /**
     * The canonical constructor, carrying the anti-spam cooldown floor, its clock, the paged list sources a page flip
     * re-queries, and the text-prompt capability an {@code input:} step drives. Every shorter form above delegates here
     * with no floor, the real system clock, an empty paged registry and no text prompt, so every existing construction
     * and test is unchanged; only production wiring, which holds the feature-populated paged registry and the
     * text-input seam, passes them.
     */
    public MenuListener(
            MenuRenderer renderer,
            ActionRegistry actions,
            ConditionRegistry conditions,
            Scheduler scheduler,
            Plugin plugin,
            @Nullable EditorRenderer editorRenderer,
            @Nullable SelectorOpener selectorOpener,
            @Nullable ConfirmOpener confirmOpener,
            long defaultClickCooldownMs,
            LongSupplier clock,
            PagedListSourceRegistry pagedLists,
            @Nullable MenuTextPrompt textPrompt) {
        this(
                renderer,
                actions,
                conditions,
                scheduler,
                plugin,
                editorRenderer,
                selectorOpener,
                confirmOpener,
                defaultClickCooldownMs,
                clock,
                pagedLists,
                textPrompt,
                null);
    }

    /**
     * The canonical constructor, carrying on top of everything above the content-region providers a menu's live-item
     * slots are filled and policed by. Every shorter form delegates here with none, so a menu with no
     * {@code content {}} block, every menu before this seam existed, routes exactly as before.
     */
    public MenuListener(
            MenuRenderer renderer,
            ActionRegistry actions,
            ConditionRegistry conditions,
            Scheduler scheduler,
            Plugin plugin,
            @Nullable EditorRenderer editorRenderer,
            @Nullable SelectorOpener selectorOpener,
            @Nullable ConfirmOpener confirmOpener,
            long defaultClickCooldownMs,
            LongSupplier clock,
            PagedListSourceRegistry pagedLists,
            @Nullable MenuTextPrompt textPrompt,
            @Nullable ContentProviderRegistry contents) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
        this.actions = Objects.requireNonNull(actions, "actions");
        this.conditions = Objects.requireNonNull(conditions, "conditions");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.editorRenderer = editorRenderer;
        this.selectorOpener = selectorOpener;
        this.confirmOpener = confirmOpener;
        if (defaultClickCooldownMs < 0) {
            throw new IllegalArgumentException("defaultClickCooldownMs must be >= 0: " + defaultClickCooldownMs);
        }
        this.defaultClickCooldownMs = defaultClickCooldownMs;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.pagedLists = Objects.requireNonNull(pagedLists, "pagedLists");
        this.textPrompt = textPrompt;
        this.contents = contents;
    }

    /** Registers this listener with the server. Called once when the menu engine starts. */
    public void install() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /** Unregisters every handler this listener holds. Called on engine stop so a reload leaves none dangling. */
    public void uninstall() {
        HandlerList.unregisterAll(this);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) {
            return;
        }
        // A capture-enabled grid decides cancellation per action, so the operator can pull items off their own
        // inventory onto the canvas; every other menu, and a plain grid, blanket-cancels, so no item ever moves.
        GridViewState captureGrid = holder.gridView().orElse(null);
        if (captureGrid != null && captureEnabled(captureGrid) && event.getWhoClicked() instanceof Player) {
            handleCaptureGridClick(holder, captureGrid, event);
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }
        int slot = event.getRawSlot();
        ConfirmState confirm = holder.confirm().orElse(null);
        if (confirm != null) {
            handleConfirmClick(holder, confirm, slot, (Player) event.getWhoClicked());
            return;
        }
        SelectorState selector = holder.selector().orElse(null);
        if (selector != null) {
            handleSelectorClick(
                    holder, selector, slot, (Player) event.getWhoClicked(), event.isRightClick(), event.isShiftClick());
            return;
        }
        EditorState editor = holder.editor().orElse(null);
        if (editor != null) {
            handleEditorClick(holder, editor, slot, event.isRightClick(), event.isShiftClick());
            return;
        }
        ListViewState list = holder.listView().orElse(null);
        if (list != null) {
            handleListClick(holder, list, slot);
            return;
        }
        GridViewState grid = holder.gridView().orElse(null);
        if (grid != null) {
            handleGridClick(holder, grid, slot, kindOf(event.getClick()));
            return;
        }
        if (routeContentClick(holder, event)) {
            return;
        }
        Player clicker = (Player) event.getWhoClicked();
        holder.clickAt(slot).ifPresent(rs -> {
            if (!clickVetoed(holder, rs, slot, clicker)) {
                routeSpecClick(holder, rs, event);
            }
        });
    }

    /**
     * Route a drag gesture over a menu window. A drag that touches any slot of a capture-enabled grid's top canvas is
     * cancelled and each dragged content cell captures a copy of the held item, so the operator can paint a row by
     * dragging across it; a drag that stays entirely in their own inventory is left alone. Every other menu, and a
     * plain grid. Cancels a drag outright: a drag is not part of a menu's interaction model, and cancelling closes the
     * one gap the blanket click-cancel does not (an item cannot reach a menu's cursor anyway, so this changes nothing a
     * player could observe, it only fails safe).
     */
    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) {
            return;
        }
        GridViewState grid = holder.gridView().orElse(null);
        if (grid == null || !captureEnabled(grid) || !(event.getWhoClicked() instanceof Player)) {
            if (!dragAllowedInContent(holder, event)) {
                event.setCancelled(true);
            }
            return;
        }
        handleCaptureGridDrag(holder, grid, event);
    }

    /**
     * Route a click that lands on (or into) one of the menu's {@code content {}} regions, and report whether it was
     * handled here. The event arrives already cancelled, so every path that simply returns leaves the region as
     * untouchable as any chrome slot: that is the default for a region that is not editable, whose provider is not
     * registered, or whose provider refuses this one movement. Only an editable region whose provider says yes has
     * the cancel lifted, which is the single place an item may move inside a menu window.
     *
     * <p>A shift-click from the viewer's own inventory is handled rather than delegated, because vanilla would
     * scatter the stack across whatever top slots happen to be free, chrome gaps included, instead of into the
     * region. The engine therefore performs that insert itself, into the first free slot of a region that accepts it.
     *
     * <p>Two gestures are refused outright even in an editable region: a double-click, which vanilla resolves by
     * gathering matching stacks from the <em>whole</em> window (chrome tiles are not real items, so gathering them
     * would mint items), and a hotbar swap, whose source slot is outside the region the provider is answering about.
     */
    private boolean routeContentClick(MenuHolder holder, InventoryClickEvent event) {
        if (holder.spec().contents().isEmpty()) {
            return false;
        }
        int raw = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();
        if (raw >= 0 && raw < topSize) {
            ContentRegionSpec region = holder.spec().regionAt(raw).orElse(null);
            if (region == null) {
                return false;
            }
            handleContentClick(holder, region, event, raw);
            return true;
        }
        if (raw >= topSize && event.isShiftClick()) {
            handleContentShiftInsert(holder, event, topSize);
            return true;
        }
        return false;
    }

    /** Ask the region's provider about this one movement and lift the blanket cancel when it allows it. */
    private void handleContentClick(MenuHolder holder, ContentRegionSpec region, InventoryClickEvent event, int slot) {
        ContentProvider provider = contentProvider(region);
        if (!region.editable() || provider == null || refusedGesture(event)) {
            return;
        }
        ItemStack cursor = emptyToNull(event.getCursor());
        ItemStack current = emptyToNull(event.getCurrentItem());
        ContentClick.Kind kind;
        if (event.isShiftClick() || cursor == null) {
            kind = ContentClick.Kind.TAKE;
        } else if (current == null) {
            kind = ContentClick.Kind.INSERT;
        } else {
            kind = ContentClick.Kind.SWAP;
        }
        ContentClick click = new ContentClick(slot, region.indexOf(slot), kind, cursor, current);
        if (provider.allows(holder.ctx(), region, click)) {
            event.setCancelled(false);
        }
    }

    /**
     * Move a shift-clicked stack from the viewer's own inventory into the first free slot of an editable region that
     * accepts it. The event stays cancelled and the two inventories are written here instead, so the stack lands in
     * the region rather than wherever vanilla would have spread it. When no region accepts it, nothing moves.
     */
    private void handleContentShiftInsert(MenuHolder holder, InventoryClickEvent event, int topSize) {
        ItemStack moved = emptyToNull(event.getCurrentItem());
        if (moved == null) {
            return;
        }
        Inventory top = event.getView().getTopInventory();
        for (ContentRegionSpec region : holder.spec().contents().values()) {
            ContentProvider provider = contentProvider(region);
            if (!region.editable() || provider == null) {
                continue;
            }
            for (int index = 0; index < region.slots().slots().size(); index++) {
                int slot = region.slots().slots().get(index);
                if (slot >= topSize || emptyToNull(top.getItem(slot)) != null) {
                    continue;
                }
                ContentClick click = new ContentClick(slot, index, ContentClick.Kind.INSERT, moved, null);
                if (!provider.allows(holder.ctx(), region, click)) {
                    break;
                }
                top.setItem(slot, moved.clone());
                event.setCurrentItem(null);
                return;
            }
        }
    }

    /**
     * Whether a drag may go through: every top slot it touches must lie in an editable region whose provider accepts
     * the stack that would land there. A drag over anything else (chrome, a read-only region, a menu with no region
     * at all) is refused by the caller, which is the behaviour every menu had before content regions existed.
     */
    private boolean dragAllowedInContent(MenuHolder holder, InventoryDragEvent event) {
        if (holder.spec().contents().isEmpty() || !(event.getWhoClicked() instanceof Player)) {
            return false;
        }
        int topSize = event.getView().getTopInventory().getSize();
        for (Map.Entry<Integer, ItemStack> entry : event.getNewItems().entrySet()) {
            int slot = entry.getKey();
            if (slot >= topSize) {
                continue;
            }
            ContentRegionSpec region = holder.spec().regionAt(slot).orElse(null);
            ContentProvider provider = region == null ? null : contentProvider(region);
            if (region == null || !region.editable() || provider == null) {
                return false;
            }
            ContentClick click = new ContentClick(
                    slot,
                    region.indexOf(slot),
                    ContentClick.Kind.INSERT,
                    entry.getValue(),
                    emptyToNull(event.getView().getTopInventory().getItem(slot)));
            if (!provider.allows(holder.ctx(), region, click)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Hand each region's final contents back to its provider as the window closes, in the region's declared slot
     * order. This is the feature's one chance to return or persist what is left in a region it let the viewer fill;
     * the window is discarded right after. A menu with no region, or one whose providers are not registered, does
     * nothing here.
     */
    private void readBackContent(MenuHolder holder, Inventory inventory) {
        if (holder.spec().contents().isEmpty()) {
            return;
        }
        for (ContentRegionSpec region : holder.spec().contents().values()) {
            ContentProvider provider = contentProvider(region);
            if (provider == null) {
                continue;
            }
            List<@Nullable ItemStack> stacks = new ArrayList<>();
            for (int slot : region.slots().slots()) {
                stacks.add(slot < inventory.getSize() ? emptyToNull(inventory.getItem(slot)) : null);
            }
            provider.readBack(holder.ctx(), region, stacks);
        }
    }

    /** The provider registered for {@code region}, or null when the engine has none (a spec-only test engine). */
    @Nullable private ContentProvider contentProvider(ContentRegionSpec region) {
        return contents == null ? null : contents.get(region.id()).orElse(null);
    }

    /**
     * Whether the gesture reaches beyond the one slot the provider is being asked about, and so can never be let
     * through: a double-click gathers matching stacks from the whole window, and a hotbar swap or an off-hand swap
     * pulls from a slot outside the region.
     */
    private static boolean refusedGesture(InventoryClickEvent event) {
        return event.getClick() == ClickType.DOUBLE_CLICK
                || event.getClick() == ClickType.NUMBER_KEY
                || event.getClick() == ClickType.SWAP_OFFHAND
                || event.getAction() == InventoryAction.COLLECT_TO_CURSOR
                || event.getAction() == InventoryAction.UNKNOWN;
    }

    /** {@code null} for an absent or air stack, so "empty" is one condition everywhere in the content routing. */
    @Nullable private static ItemStack emptyToNull(@Nullable ItemStack stack) {
        return stack == null || stack.getType() == Material.AIR ? null : stack;
    }

    /**
     * Fire the public, cancellable {@link MenuClickEvent} for a spec-slot click and report whether a listener vetoed
     * it. A veto stops the whole click-handling (the item-drag flow, conditions, requirements and actions alike)
     * before any of it runs; the engine's own hook, fired at the click choke-point right before {@link #routeSpecClick}.
     * It is independent of the vanilla {@link InventoryClickEvent}, which is already cancelled either way so the item
     * never moves: cancelling this one does not un-cancel that one. The nav (next/previous/jump) branch lives inside
     * {@link #handleClick}, downstream of here, so this fires for a pagination-button click too; a listener that only
     * cares about real button clicks can read {@link MenuClickEvent#getItemId()}. Fires on the viewer's own region
     * thread the click already runs on.
     */
    private boolean clickVetoed(MenuHolder holder, RenderedSlot rs, int slot, Player viewer) {
        MenuClickEvent event = new MenuClickEvent(viewer, holder.specId(), slot, itemIdOf(holder, rs));
        Bukkit.getPluginManager().callEvent(event);
        return event.isCancelled();
    }

    /**
     * Best-effort stable id of the item spec that produced the clicked slot: the operator's own key for it in the
     * menu's {@code items {}} block, found by identity. A list cell renders the list's nested template, which is not a
     * top-level entry and so carries no such key. Such a click resolves to {@code null}, and the event still fires,
     * just without an id.
     */
    @Nullable private static String itemIdOf(MenuHolder holder, RenderedSlot rs) {
        for (Map.Entry<String, MenuItemSpec> entry : holder.spec().items().entrySet()) {
            if (entry.getValue() == rs.item()) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Route a click on a rendered spec slot. When the slot's item carries an item-drag binding and the viewer is
     * holding an item on their cursor, the click runs the drag flow. The cursor is captured, validated, and (on a
     * pass) fed to the item-drag actions rather than the ordinary click. An empty cursor, or a slot with no drag
     * binding, takes the normal click exactly as before, so a plain item behaves identically. The click is already
     * cancelled by the caller either way, so nothing the viewer holds is ever moved by vanilla.
     */
    private void routeSpecClick(MenuHolder holder, RenderedSlot rs, InventoryClickEvent event) {
        ItemStack cursor = event.getCursor();
        Optional<ItemDragSpec> drag = rs.item().itemDrag();
        if (drag.isPresent() && cursor != null && cursor.getType() != Material.AIR) {
            handleItemDrag(holder, rs, drag.get(), cursor, event);
        } else {
            handleClick(holder, rs, event.getClick());
        }
    }

    /**
     * Handle a click on an item-drag slot made while holding an item on the cursor. The click is already cancelled, so
     * vanilla never moves the item, no dupe. The cursor is validated against the item's rules; on a pass the drag
     * actions fire with the dropped item exposed as {@code %drag_material%}/{@code %drag_amount%}/{@code %drag_name%}
     * placeholders, and when the item consumes, the matched amount is removed from the cursor here on the viewer's own
     * entity thread (the thread this click runs on). A controlled, single-threaded mutation, never a vanilla move, so
     * it cannot dupe. A failed match leaves the cursor untouched: a silent deny, since a "wrong item" line would spam
     * a mis-drop. It dispatches actions, so it is throttled by the same anti-spam window a normal action click is.
     */
    private void handleItemDrag(
            MenuHolder holder, RenderedSlot rs, ItemDragSpec drag, ItemStack cursor, InventoryClickEvent event) {
        if (throttled(holder)) {
            return;
        }
        if (!matches(drag.rules(), cursor)) {
            return;
        }
        MenuContext base = dragContext(holder, rs, cursor);
        ClickKind kind = kindOf(event.getClick());
        for (Ref ref : drag.actions()) {
            runRef(holder, base, kind, ref);
        }
        if (drag.consume()) {
            consumeCursor(event, cursor, Math.max(1, drag.rules().minAmount()));
        }
    }

    /**
     * The context the drag actions run against: the open context (bound to the slot's list entry when it has one) with
     * the dropped item's {@code %drag_material%}/{@code %drag_amount%}/{@code %drag_name%} exposed as menu-local
     * placeholders, merged over the menu's own local block, so a drag action (and any message it emits) can reference
     * the item: the same local-placeholder channel the renderer resolves in text.
     */
    private MenuContext dragContext(MenuHolder holder, RenderedSlot rs, ItemStack cursor) {
        Map<String, String> locals = new LinkedHashMap<>(holder.ctx().localPlaceholders());
        locals.put("drag_material", cursor.getType().name());
        locals.put("drag_amount", String.valueOf(cursor.getAmount()));
        locals.put("drag_name", plainName(cursor));
        MenuContext base = rs.entry() == null ? holder.ctx() : holder.ctx().withEntry(rs.entry());
        return base.withLocalPlaceholders(locals);
    }

    /**
     * Whether the held cursor satisfies the item-drag rules: its material is on the whitelist (an empty whitelist
     * accepts any material), its stack size is at least the minimum (a minimum below one is read as one), and, when a
     * name filter is set: its display name contains the filter case-insensitively. The name is read best-effort from
     * the item's Adventure display name, falling back to the material name when the item is unnamed.
     */
    private boolean matches(ItemRuleSpec rules, ItemStack cursor) {
        if (!rules.materials().isEmpty()
                && !rules.materials().contains(cursor.getType().name())) {
            return false;
        }
        if (cursor.getAmount() < Math.max(1, rules.minAmount())) {
            return false;
        }
        if (rules.nameContains().isEmpty()) {
            return true;
        }
        return plainName(cursor)
                .toLowerCase(Locale.ROOT)
                .contains(rules.nameContains().toLowerCase(Locale.ROOT));
    }

    /** The cursor's plain-text display name when it has one, else its material name, the {@code %drag_name%} value. */
    private static String plainName(ItemStack cursor) {
        ItemMeta meta = cursor.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            Component name = meta.displayName();
            if (name != null) {
                return PlainTextComponentSerializer.plainText().serialize(name);
            }
        }
        return cursor.getType().name();
    }

    /**
     * Remove {@code amount} from the held cursor after a matched drag, clearing it to nothing when the stack empties.
     * A controlled write on the viewer's own entity thread through the click view, never a vanilla item move, so it
     * cannot dupe: the click itself stays cancelled.
     */
    private void consumeCursor(InventoryClickEvent event, ItemStack cursor, int amount) {
        int remaining = cursor.getAmount() - amount;
        if (remaining <= 0) {
            event.getView().setCursor(null);
            return;
        }
        ItemStack reduced = cursor.clone();
        reduced.setAmount(remaining);
        event.getView().setCursor(reduced);
    }

    /**
     * Route a click in a confirm window: the yes/no slot runs its decision exactly once. The single-fire guard on
     * the {@link ConfirmState} makes a stray second click in the same tick a no-op, and the window is closed before
     * the decision runs, mirroring uxmLib's {@code ConfirmMenu}, so a decision that opens another menu is not
     * clobbered by the close. The close funnels through the one {@code closeMenu}, so no second listener or teardown
     * path is introduced. A click on a non-button slot does nothing; the click is already cancelled.
     */
    private void handleConfirmClick(MenuHolder holder, ConfirmState confirm, int slot, Player clicker) {
        Runnable decision = confirm.decisionAt(slot).orElse(null);
        if (decision == null || !confirm.fire()) {
            return;
        }
        clicker.closeInventory();
        scheduler.onEntity(holder.ctx().viewer(), () -> {
            if (Bukkit.getPlayer(holder.ctx().viewer().uuid()) != null) {
                decision.run();
            }
        });
    }

    /**
     * Route a click in a selector window: the clicked button runs its handler exactly once, given the click gesture
     * so a list-entry button can branch (an option/add/back button ignores it). The single-fire guard on the
     * {@link SelectorState} makes a stray second click in the same tick a no-op, and the window is closed before the
     * handler runs, mirroring the confirm flow, so a handler that reopens the parent editor (or the list child after
     * a mutation) is not clobbered by the close. The close funnels through the one {@code closeMenu}, so no second
     * listener or teardown path is introduced. A click on a non-button slot does nothing; the click is already
     * cancelled. The handler itself (the property's async-setter-then-reopen loop) is enqueued on the viewer's entity
     * thread, matching every other menu hop, and skipped if the viewer logged off in the gap.
     */
    private void handleSelectorClick(
            MenuHolder holder,
            SelectorState selector,
            int slot,
            Player clicker,
            boolean rightClick,
            boolean shiftClick) {
        ChildClickHandler choice = selector.chooseAt(slot).orElse(null);
        if (choice == null || !selector.fire()) {
            return;
        }
        clicker.closeInventory();
        scheduler.onEntity(holder.ctx().viewer(), () -> {
            if (Bukkit.getPlayer(holder.ctx().viewer().uuid()) != null) {
                choice.onClick(rightClick, shiftClick);
            }
        });
    }

    /**
     * Route a click in an editor window: a property slot runs that property's {@link EditableProperty#onClick} with a
     * freshly built {@link ClickContext}, a plain button (back, delete) runs its recorded action. Both hop to the
     * viewer's entity thread first, the same hop a spec action takes, and re-resolve the live player there, so a
     * viewer who logged off in the gap is simply skipped and Bukkit is only ever touched on the owning thread. The
     * context's reopen hook repaints this editor in place, so the property's own setter-then-reopen loop redraws the
     * window the viewer is already looking at.
     */
    private void handleEditorClick(
            MenuHolder holder, EditorState editor, int slot, boolean rightClick, boolean shiftClick) {
        EditableProperty property = editor.propertyAt(slot).orElse(null);
        if (property != null) {
            runProperty(holder, property, rightClick, shiftClick);
            return;
        }
        editor.buttonAt(slot)
                .ifPresent(action -> scheduler.onEntity(holder.ctx().viewer(), () -> {
                    if (Bukkit.getPlayer(holder.ctx().viewer().uuid()) != null) {
                        action.run();
                    }
                }));
    }

    /** Hop to the viewer's entity thread, re-resolve the live player, and run one property's click there. */
    private void runProperty(MenuHolder holder, EditableProperty property, boolean rightClick, boolean shiftClick) {
        // A property click only reaches here when an editor is open, and an editor-capable listener is always wired
        // with both openers (the engine threads its own in); a missing one is a wiring error, surfaced here rather
        // than deep in the click context.
        SelectorOpener selector = Objects.requireNonNull(selectorOpener, "an editor listener needs a selector opener");
        ConfirmOpener confirm = Objects.requireNonNull(confirmOpener, "an editor listener needs a confirm opener");
        PlayerRef viewer = holder.ctx().viewer();
        scheduler.onEntity(viewer, () -> {
            Player live = Bukkit.getPlayer(viewer.uuid());
            if (live == null) {
                return;
            }
            Runnable reopen = () -> reRenderEditor(holder);
            property.onClick(new ClickContext(live, viewer, rightClick, shiftClick, reopen, selector, confirm));
        });
    }

    /** Repaint {@code holder}'s editor in place; a no-op when the engine was wired without editor support. */
    private void reRenderEditor(MenuHolder holder) {
        if (editorRenderer != null) {
            EditorRefresh.reRender(holder, editorRenderer, scheduler);
        }
    }

    /**
     * Route a click in an entity-list window: an entity icon runs the spec's {@code onSelect} for the entity drawn at
     * that slot on the current page, a previous/next nav button re-paginates the same holder in place, and a
     * create/action button runs its recorded handler. Each branch hops to the viewer's entity thread and re-resolves
     * the live player there, the same hop the editor and spec paths take, so a viewer who logged off in the gap is
     * simply skipped and Bukkit is only ever touched on the owning thread. A nav flip re-renders the live inventory
     * (no new window), so the one listener and one teardown keep owning it.
     */
    private void handleListClick(MenuHolder holder, ListViewState list, int slot) {
        if (list.isPrev(slot) || list.isNext(slot)) {
            int next = list.isPrev(slot)
                    ? Math.max(0, holder.ctx().page() - 1)
                    : holder.ctx().page() + 1;
            scheduler.onEntity(holder.ctx().viewer(), () -> repaginateList(holder, list, next));
            return;
        }
        Object entity = list.entityAt(slot).orElse(null);
        if (entity != null) {
            runOnList(holder, live -> ((EntityListSpec) list.spec()).onSelect().accept(live, entity));
            return;
        }
        list.buttonAt(slot).ifPresent(action -> runOnList(holder, ignored -> action.run()));
    }

    /** Hop to the viewer's entity thread, re-resolve the live player, and run one list handler there. */
    private void runOnList(MenuHolder holder, Consumer<Player> handler) {
        scheduler.onEntity(holder.ctx().viewer(), () -> {
            Player live = Bukkit.getPlayer(holder.ctx().viewer().uuid());
            if (live != null) {
                handler.accept(live);
            }
        });
    }

    /** Re-paint an open entity list one page over, but only if that window is still this holder's list window. */
    private void repaginateList(MenuHolder holder, ListViewState list, int page) {
        Player live = Bukkit.getPlayer(holder.ctx().viewer().uuid());
        if (live == null) {
            return;
        }
        if (!(live.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder h) || h != holder) {
            return;
        }
        list.clearSlots();
        int clamped = listViewRenderer.populate(
                holder.getInventory(),
                (EntityListSpec) list.spec(),
                list,
                live,
                holder.ctx().viewer(),
                page);
        holder.setCtx(holder.ctx().withPage(clamped));
    }

    /**
     * Route a click in a slot-grid canvas: a previous/next nav button re-paginates the same holder in place, a
     * control-bar button runs its recorded handler, and a content cell invokes the caller's {@link GridHandlers}
     * content handler with the menu slot it maps to and whether it held an item. Each branch hops to the viewer's
     * entity thread and re-resolves the live player there (the same hop the editor, list and spec paths take) so a
     * viewer who logged off in the gap is simply skipped and Bukkit is only ever touched on the owning thread. The
     * content handler is handed a {@link GridView} bound to this holder, so a place / move / clear that mutates the
     * caller's edit model can repaint the same window in place.
     */
    private void handleGridClick(MenuHolder holder, GridViewState grid, int slot, ClickKind kind) {
        if (grid.isPrev(slot) || grid.isNext(slot)) {
            int page = grid.isPrev(slot)
                    ? Math.max(0, holder.ctx().page() - 1)
                    : holder.ctx().page() + 1;
            scheduler.onEntity(holder.ctx().viewer(), () -> repaginateGrid(holder, grid, page));
            return;
        }
        Consumer<Player> control = grid.controlAt(slot).orElse(null);
        if (control != null) {
            runOnGrid(holder, control);
            return;
        }
        grid.contentAt(slot)
                .ifPresent(cell -> runOnGrid(
                        holder,
                        live -> ((GridHandlers) grid.handlers())
                                .onSlot()
                                .onClick(
                                        new HolderGridView(holder, grid), live, cell.menuSlot(), cell.filled(), kind)));
    }

    /** Hop to the viewer's entity thread, re-resolve the live player, and run one grid handler there. */
    private void runOnGrid(MenuHolder holder, Consumer<Player> handler) {
        scheduler.onEntity(holder.ctx().viewer(), () -> {
            Player live = Bukkit.getPlayer(holder.ctx().viewer().uuid());
            if (live != null) {
                handler.accept(live);
            }
        });
    }

    /** Re-paint an open grid at {@code page} (a page flip, or an in-place re-render), if still this holder's window. */
    private void repaginateGrid(MenuHolder holder, GridViewState grid, int page) {
        Player live = Bukkit.getPlayer(holder.ctx().viewer().uuid());
        if (live == null) {
            return;
        }
        if (!(live.getOpenInventory().getTopInventory().getHolder() instanceof MenuHolder h) || h != holder) {
            return;
        }
        grid.clearSlots();
        // Built at the use site, not the constructor, so a spec-only engine wired with a stub/mocked MenuRenderer never
        // dereferences its item renderer unless a grid is actually re-painted (which only the grid path ever triggers).
        int clamped = new GridRenderer(renderer.itemRenderer())
                .populate(
                        holder.getInventory(),
                        (GridSpec) grid.spec(),
                        grid,
                        live,
                        holder.ctx().viewer(),
                        page);
        holder.setCtx(holder.ctx().withPage(clamped));
    }

    /**
     * The {@link GridView} the engine binds to one open grid for the duration of a content-slot click: its
     * {@link #reRender} drives that holder's repaint through the same viewer's-entity-thread hop the page-flip path
     * uses, re-reading the grid's content supplier at the current page, so a place / move / clear that mutated the
     * caller's edit model shows without reopening the window. It captures only the holder and state; the live player is
     * re-resolved inside the repaint, so a viewer who logged off in the gap is simply skipped there.
     */
    private final class HolderGridView implements GridView {

        private final MenuHolder holder;
        private final GridViewState grid;

        HolderGridView(MenuHolder holder, GridViewState grid) {
            this.holder = holder;
            this.grid = grid;
        }

        @Override
        public void reRender() {
            scheduler.onEntity(
                    holder.ctx().viewer(),
                    () -> repaginateGrid(holder, grid, holder.ctx().page()));
        }
    }

    /** Whether {@code grid} carries a capture handler, i.e. accepts items dragged in from the viewer's inventory. */
    private static boolean captureEnabled(GridViewState grid) {
        return ((GridHandlers) grid.handlers()).onCapture() != null;
    }

    /**
     * Core invariant of the capture grid: the top canvas never accepts or yields a real item: every transfer touching
     * it is cancelled here, so "placing" only copies the held item's definition into the caller's edit model and
     * re-renders. The player's real items never move, so no dupe is possible. A click on the top canvas is always
     * cancelled and then either captures (a held item placed onto a content cell) or runs the ordinary editor gesture
     * ({@link #handleGridClick}); a click in the player's own bottom inventory stays free, except a shift-click appends
     * the item to the canvas and a double-click is swallowed so it cannot gather the top's display icons.
     */
    private void handleCaptureGridClick(MenuHolder holder, GridViewState grid, InventoryClickEvent event) {
        int topSize = event.getView().getTopInventory().getSize();
        int raw = event.getRawSlot();
        if (raw < 0) {
            // A click outside the whole window (dropping their own item): never the canvas's concern.
            return;
        }
        if (raw < topSize) {
            handleCaptureTopClick(holder, grid, event, raw);
        } else {
            handleCaptureBottomClick(holder, grid, event);
        }
    }

    /**
     * A click on the top canvas: always cancelled. A held item placed or swapped onto a content cell captures a copy of
     * it into that menu slot; anything else (an empty cursor, a pick-up, a nav / control / editor gesture) runs the
     * ordinary {@link #handleGridClick} path, so the existing place / move / clear / open-editor behaviour is unchanged.
     */
    private void handleCaptureTopClick(MenuHolder holder, GridViewState grid, InventoryClickEvent event, int raw) {
        event.setCancelled(true);
        GridViewState.ContentSlot cell = grid.contentAt(raw).orElse(null);
        ItemStack cursor = event.getCursor();
        if (cell != null && isPlaceOrSwap(event.getAction()) && isRealItem(cursor)) {
            capture(holder, grid, cell.menuSlot(), cursor.clone());
            return;
        }
        handleGridClick(holder, grid, raw, kindOf(event.getClick()));
    }

    /**
     * A click in the viewer's own bottom inventory. A shift-click ({@code MOVE_TO_OTHER_INVENTORY}) is cancelled and the
     * clicked item is appended to the first empty menu slot, the quick "send this to the canvas" gesture, and a
     * double-click ({@code COLLECT_TO_CURSOR}) is cancelled so it cannot rake the top's display icons onto the cursor.
     * Every other bottom click is left alone, so the operator freely picks up and rearranges their own items.
     */
    private void handleCaptureBottomClick(MenuHolder holder, GridViewState grid, InventoryClickEvent event) {
        InventoryAction action = event.getAction();
        if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            event.setCancelled(true);
            appendToFirstEmpty(holder, grid, event.getCurrentItem());
        } else if (action == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
        }
    }

    /** Capture {@code clicked} into the canvas's first empty menu slot, if it holds a real item and a slot is free. */
    private void appendToFirstEmpty(MenuHolder holder, GridViewState grid, @Nullable ItemStack clicked) {
        if (clicked == null || !isRealItem(clicked)) {
            return;
        }
        OptionalInt free = ((GridSpec) grid.spec()).firstEmptySlot();
        if (free.isPresent()) {
            capture(holder, grid, free.getAsInt(), clicked.clone());
        }
    }

    /**
     * A drag over a capture grid. When it touches any top-canvas slot the whole drag is cancelled (no real item is ever
     * split into the canvas) and every dragged content cell captures a copy of the held item; a drag that stays entirely
     * in the viewer's own inventory is left alone so they can spread their own stack there.
     */
    private void handleCaptureGridDrag(MenuHolder holder, GridViewState grid, InventoryDragEvent event) {
        int topSize = event.getView().getTopInventory().getSize();
        List<Integer> topSlots =
                event.getRawSlots().stream().filter(raw -> raw < topSize).toList();
        if (topSlots.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        ItemStack dragged = event.getOldCursor();
        if (!isRealItem(dragged)) {
            return;
        }
        for (int raw : topSlots) {
            grid.contentAt(raw).ifPresent(cell -> capture(holder, grid, cell.menuSlot(), dragged.clone()));
        }
    }

    /** Hand one capture to the grid's capture handler on the viewer's entity thread; the handler stamps and re-renders. */
    private void capture(MenuHolder holder, GridViewState grid, int menuSlot, ItemStack copy) {
        GridCaptureHandler onCapture = ((GridHandlers) grid.handlers()).onCapture();
        if (onCapture == null) {
            return;
        }
        runOnGrid(holder, live -> onCapture.capture(new HolderGridView(holder, grid), live, menuSlot, copy));
    }

    /** Whether {@code action} drops the cursor onto a slot: the gestures that mean "put this item here". */
    private static boolean isPlaceOrSwap(InventoryAction action) {
        return action == InventoryAction.PLACE_ONE
                || action == InventoryAction.PLACE_SOME
                || action == InventoryAction.PLACE_ALL
                || action == InventoryAction.SWAP_WITH_CURSOR;
    }

    /** Whether {@code item} is a real (non-null, non-air) stack worth capturing. */
    private static boolean isRealItem(@Nullable ItemStack item) {
        return item != null && item.getType() != Material.AIR;
    }

    private void handleClick(MenuHolder holder, RenderedSlot rs, ClickType click) {
        ItemType type = rs.item().type();
        if (type == ItemType.NEXT || type == ItemType.PREVIOUS || type == ItemType.JUMP) {
            MenuFeedback.page(holder);
            navigate(holder, type);
            return;
        }
        if (throttled(holder)) {
            return;
        }
        ClickKind kind = kindOf(click);
        MenuContext base = rs.entry() == null ? holder.ctx() : holder.ctx().withEntry(rs.entry());
        if (!clickConditionsPass(rs.item().click(), kind, base)) {
            return;
        }
        RequirementSpec requirement = rs.item().click().requirementFor(kind);
        if (!evaluateRequirements(holder, base, kind, requirement)) {
            Optional<ClickBranch> elseChain = rs.item().click().elseFor(kind);
            MenuFeedback.deny(holder);
            if (elseChain.isPresent()) {
                evaluateBranch(holder, base, kind, elseChain.get());
            } else {
                runEach(holder, base, kind, requirement.deny());
            }
            return;
        }
        List<Ref> actions = rs.item().click().actionsFor(kind);
        MenuFeedback.click(holder, actions);
        runChain(holder, base, kind, actions, 0);
    }

    /**
     * Walk a gesture's success actions as a continuation-aware chain: an ordinary ref dispatches inline through
     * {@link #runRef}, but a {@code input:}/{@code confirm:} step ({@link Ref#continuation()}) whose outcome only
     * arrives on a later callback stops the walk, taking ownership of the rest of the chain. An {@code input:} step
     * prompts and, on submit, re-enters this walk over the remaining refs with the typed line exposed as
     * {@code %input%}; a {@code confirm:} step opens the yes/no window and runs one of its two branches on the
     * decision. Every ordinary ref before the step still runs, so a chain like {@code [message, input:…, action]}
     * behaves as written. The {@code from} index lets the input-submit path resume mid-list without re-copying.
     *
     * <p>This continuation awareness is deliberately only on the success path. A {@code input:}/{@code confirm:} step
     * that appears inside an else-ladder, a deny list, or a per-requirement action list is unsupported: it falls
     * through to {@link #runRef}, hits the registered marker action, and is logged and skipped rather than splitting a
     * nested chain the engine cannot cleanly suspend.
     */
    private void runChain(MenuHolder holder, MenuContext base, ClickKind kind, List<Ref> refs, int from) {
        for (int i = from; i < refs.size(); i++) {
            Ref ref = refs.get(i);
            Optional<Continuation> continuation = ref.continuation();
            if (continuation.isPresent()) {
                beginContinuation(holder, base, kind, continuation.get(), refs.subList(i + 1, refs.size()));
                return;
            }
            runRef(holder, base, kind, ref);
        }
    }

    /** Dispatch one continuation step, handing it the refs that must run after it resolves. */
    private void beginContinuation(
            MenuHolder holder, MenuContext base, ClickKind kind, Continuation continuation, List<Ref> rest) {
        List<Ref> remaining = List.copyOf(rest);
        if (continuation instanceof Continuation.Input input) {
            promptInput(holder, base, kind, input, remaining);
        } else if (continuation instanceof Continuation.Confirm confirm) {
            openConfirm(holder, base, kind, confirm);
        }
    }

    /**
     * Run an {@code input:} step: resolve its prompt and pre-fill against the open context (the same {@code @key}/
     * {@code %token%} path an item name takes), then prompt the viewer. On submit the remaining refs run as a fresh
     * continuation with the typed line exposed as {@code %input%}; on cancel the step's own {@code deny} refs run and
     * the remaining refs are abandoned. The prompt already runs on the viewer's entity thread, and the text-prompt
     * seam delivers both callbacks back on it, so the continuation lands where it can touch the player. An engine wired
     * without a text prompt cannot prompt, so it runs the cancel refs rather than dead-ending.
     */
    private void promptInput(
            MenuHolder holder, MenuContext base, ClickKind kind, Continuation.Input input, List<Ref> remaining) {
        if (textPrompt == null) {
            LOG.warning("event=menu_input_unavailable key=" + input.key());
            runEach(holder, base, kind, input.onCancel());
            return;
        }
        Player live = Bukkit.getPlayer(base.viewer().uuid());
        if (live == null) {
            return;
        }
        Component prompt = renderer.itemRenderer().title(input.prompt(), base);
        String initial = input.defaultText().isBlank() ? null : renderer.plainText(input.defaultText(), base);
        textPrompt.prompt(
                live,
                input.key(),
                prompt,
                initial,
                text -> runChain(holder, withInput(base, text), kind, remaining, 0),
                () -> runEach(holder, base, kind, input.onCancel()));
    }

    /** A copy of {@code base} exposing the submitted line as the {@code %input%} menu-local placeholder. */
    private static MenuContext withInput(MenuContext base, String text) {
        Map<String, String> locals = new LinkedHashMap<>(base.localPlaceholders());
        locals.put("input", text);
        return base.withLocalPlaceholders(locals);
    }

    /**
     * Run a {@code confirm:} step: resolve its title against the open context, then open a yes/no window whose accept
     * runs the {@code yes} refs and whose decline runs the {@code no} refs, each through the same continuation-aware
     * walk, on the viewer's entity thread the confirm opener runs its decision on. Unlike {@code input:} it splits no
     * remaining chain: its two branches carry everything that follows either decision. An engine wired without a
     * confirm opener runs the {@code no} refs rather than dead-ending.
     */
    private void openConfirm(MenuHolder holder, MenuContext base, ClickKind kind, Continuation.Confirm confirm) {
        if (confirmOpener == null) {
            LOG.warning("event=menu_confirm_unavailable");
            runEach(holder, base, kind, confirm.onNo());
            return;
        }
        Component title = renderer.itemRenderer().title(confirm.title(), base);
        confirmOpener.openConfirm(
                base.viewer(),
                title,
                () -> runChain(holder, base, kind, confirm.onYes(), 0),
                () -> runChain(holder, base, kind, confirm.onNo(), 0));
    }

    /**
     * Whether this click lands inside the anti-spam window and should be swallowed. The effective cooldown is the
     * menu's own {@code click-cooldown} when it sets a positive one, otherwise the server-wide default; when both are
     * zero there is no throttle and every click passes. A click closer to the last passed click than the cooldown is
     * dropped silently (a "slow down" message would itself spam the chat, so nothing is sent) and only a passing
     * click advances the per-holder stamp, so the window is always measured from the last click that actually fired.
     * Runs on the viewer's own entity thread, so the read-then-stamp is single-threaded per holder and needs no lock.
     */
    private boolean throttled(MenuHolder holder) {
        long cooldown = holder.spec().clickCooldownMs() > 0 ? holder.spec().clickCooldownMs() : defaultClickCooldownMs;
        if (cooldown <= 0) {
            return false;
        }
        long now = clock.getAsLong();
        OptionalLong last = holder.lastClickMs();
        if (last.isPresent() && now - last.getAsLong() < cooldown) {
            return true;
        }
        holder.lastClickMs(now);
        return false;
    }

    /**
     * Walk one node of a gesture's else-chain: the fallback ladder tried once its main requirement block has already
     * failed. The rule mirrors an if / else-if / else ladder. If this branch's own requirement passes, its actions run
     * and the walk stops: the first satisfied branch wins. Otherwise the walk continues to the next {@code orElse}
     * branch; when there is none, this branch's own block {@code deny} runs, so the tail of a ladder still has a deny
     * arm. A terminal else parses with {@link RequirementSpec#NONE}, which always passes, so its actions run
     * unconditionally as the "otherwise" arm. Each branch's own per-requirement success/deny actions fire inside
     * {@link #evaluateRequirements} exactly as a top-level block's do, and every action routes through {@link #runEach}
     * so modifiers and the viewer's-entity-thread hop are honoured. The recursion depth is bounded by the config's
     * nesting, which the loader builds from a finite document.
     */
    private void evaluateBranch(MenuHolder holder, MenuContext base, ClickKind kind, ClickBranch branch) {
        if (evaluateRequirements(holder, base, kind, branch.requirement())) {
            runEach(holder, base, kind, branch.actions());
        } else if (branch.orElse().isPresent()) {
            evaluateBranch(holder, base, kind, branch.orElse().get());
        } else {
            runEach(holder, base, kind, branch.requirement().deny());
        }
    }

    /**
     * Evaluate {@code spec}'s block for this click, running each requirement's own success/deny actions as it goes and
     * reporting whether the block as a whole passes. The pass/fail rule is driven by {@code minimum}:
     *
     * <ul>
     *   <li>{@code minimum <= 0} (the default): every <em>mandatory</em> (non-optional) requirement must pass. An
     *       optional requirement failing does not fail the block, yet its per-requirement {@code deny} still runs.</li>
     *   <li>{@code minimum > 0}. At least N of <em>all</em> requirements (optional included) must pass.</li>
     * </ul>
     *
     * <p>With {@link RequirementSpec#stopAtSuccess()} and a positive {@code minimum}, the loop breaks the moment the
     * minimum is met, so later requirements are not evaluated and their per-requirement actions do not run. This is
     * backward-compatible with the slice-1 model: a block with no optional and no per-requirement actions yields
     * {@code !mandatoryFail} (all passed) at {@code minimum <= 0} and {@code passes >= min} at {@code minimum > 0},
     * exactly as the old pass tally did.
     */
    private boolean evaluateRequirements(MenuHolder holder, MenuContext base, ClickKind kind, RequirementSpec spec) {
        int passes = 0;
        boolean mandatoryFail = false;
        int min = spec.minimum();
        int cap = Math.min(min, spec.requirements().size());
        for (Requirement r : spec.requirements()) {
            if (evaluateOne(holder, base, kind, r)) {
                passes++;
            } else if (!r.optional()) {
                mandatoryFail = true;
            }
            // Both halves carry weight. Only a positive minimum lets a running tally settle the block early; an
            // AND block is decided by its last requirement, and cap is then the raw minimum, which a zero pass
            // count already meets. Without the first half the walk would stop after one requirement and the rest
            // of the gate would never be asked.
            if (spec.stopAtSuccess() && min > 0 && passes >= cap) {
                break;
            }
        }
        return min <= 0 ? !mandatoryFail : passes >= cap;
    }

    /**
     * Evaluate one requirement, run its per-requirement success or deny actions as a side effect, and report whether it
     * passed. The condition is resolved against the condition registry (so a valued token like {@code has-money:100}
     * reaches its handler with {@code value=100}, the same registry-aware split the action path takes) and then negated
     * when the requirement was written with a leading {@code !}. An unregistered condition evaluates {@code false}
     * fail-closed, so a wiring gap denies rather than silently granting. The condition's args have their
     * {@code %argument_<name>%} tokens expanded from the arguments the menu was opened with first, so a click
     * requirement can gate on a typed open-command's argument; an argument-less open takes the identity fast-path.
     * Both branches route their refs through {@link #runRef}, so per-requirement actions honour the same modifiers and
     * viewer's-entity-thread hop as the click's own actions.
     */
    private boolean evaluateOne(MenuHolder holder, MenuContext base, ClickKind kind, Requirement r) {
        Ref eff = r.condition().resolve(conditions::has);
        boolean result = conditions
                        .get(eff.id())
                        .map(p -> p.test(base, ActionArguments.resolve(eff.args(), base.arguments())))
                        .orElse(false)
                != r.inverted();
        runEach(holder, base, kind, result ? r.success() : r.deny());
        return result;
    }

    /** Run each ref in {@code refs} through {@link #runRef}, so a per-requirement or block deny list honours modifiers. */
    private void runEach(MenuHolder holder, MenuContext base, ClickKind kind, List<Ref> refs) {
        for (Ref ref : refs) {
            runRef(holder, base, kind, ref);
        }
    }

    /**
     * Whether every condition the spec bound to this gesture passes. Both the gesture's own conditions and the
     * shared {@link ClickKind#ANY} list must hold; an unregistered condition fails closed so a wiring gap blocks
     * the click rather than silently running it. An empty condition list always passes. Each ref is first resolved
     * against the condition registry, so a valued condition written {@code has-money:100} splits its head off and the
     * handler sees {@code value=100} in its args: the same registry-aware split the action path takes. Each ref's args
     * then have their {@code %argument_<name>%} tokens expanded from the arguments the menu was opened with, so a
     * per-gesture condition can read a typed open-command's argument; an argument-less open takes the identity fast-path.
     */
    private boolean clickConditionsPass(ClickSpec click, ClickKind kind, MenuContext ctx) {
        for (Ref ref : merged(click.conditions().get(kind), click.conditions().get(ClickKind.ANY))) {
            Ref eff = ref.resolve(conditions::has);
            if (!conditions
                    .get(eff.id())
                    .map(p -> p.test(ctx, ActionArguments.resolve(eff.args(), ctx.arguments())))
                    .orElse(false)) {
                return false;
            }
        }
        return true;
    }

    private static List<Ref> merged(@Nullable List<Ref> own, @Nullable List<Ref> shared) {
        List<Ref> all = new ArrayList<>();
        if (own != null) {
            all.addAll(own);
        }
        if (shared != null) {
            all.addAll(shared);
        }
        return all;
    }

    /**
     * Run one bound action ref, applying its per-action modifiers. A failed chance roll denies the action: its
     * {@code deny} fallback (if any) runs through this same path and the main action is skipped. An action that
     * survives the roll is dispatched now, or after its delay. Waited off-tick, then hopped back to the viewer's
     * entity thread. A ref with no modifiers (fires immediately, always, no fallback) takes the direct dispatch and
     * behaves exactly as the action loop did before modifiers existed.
     */
    private void runRef(MenuHolder holder, MenuContext base, ClickKind kind, Ref ref) {
        Ref effective = resolveEffective(ref);
        if (rolledDenied(effective)) {
            effective.deny().ifPresent(fallback -> runRef(holder, base, kind, fallback));
            return;
        }
        actions.get(effective.id()).ifPresent(handler -> {
            if (effective.delayTicks() > 0) {
                scheduler.asyncAfter(
                        Duration.ofMillis(effective.delayTicks() * 50L),
                        () -> dispatch(holder, base, kind, effective, handler));
            } else {
                dispatch(holder, base, kind, effective, handler);
            }
        });
    }

    /**
     * Resolve a bound ref to the effective ref the registry actually dispatches. {@link Ref#parse} is registry-blind:
     * it only splits an {@code id:value} token when the head is one of a few well-known generic prefixes, so a later
     * generic action written as {@code [give-money:100]} arrives here with the whole token as its id and would miss the
     * registry (a silent no-op). The authoritative split now lives on {@link Ref#resolve(java.util.function.Predicate)},
     * which this hands the action registry's {@code has}: a registered head re-splits (modifiers riding along), and a
     * feature ref ({@code economy:open-bank}) or an already-split generic ({@code sound:x}) is returned unchanged so its
     * dispatch stays byte-identical. The very same {@code Ref.resolve} gates the condition sites against the condition
     * registry, so a valued condition ({@code has-money:100}) reaches its handler the same way.
     */
    private Ref resolveEffective(Ref ref) {
        return ref.resolve(actions::has);
    }

    /**
     * Whether {@code ref}'s chance roll denied it. A full-chance ref never rolls (so it never denies); otherwise a
     * single {@code [0, 100)} draw decides, delegating the boundary to the pure {@link Ref#deniedAt} so the runtime
     * keeps no probability logic of its own.
     */
    private boolean rolledDenied(Ref ref) {
        if (ref.chance() >= 100.0) {
            return false;
        }
        return ref.deniedAt(ThreadLocalRandom.current().nextDouble(100.0));
    }

    /**
     * Hop to the viewer's entity thread, re-resolve the live player, and run one bound action there. Before the
     * action context is built, each of the ref's argument values has its {@code %argument_<name>%} tokens expanded
     * from the arguments the menu was opened with. The same first-class substitution rendered menu TEXT already
     * gets, so an action written {@code give-money:%argument_amount%} actually acts on the opened amount. A menu
     * opened without command arguments takes the identity fast-path and passes the ref's args through unchanged.
     */
    private void dispatch(
            MenuHolder holder, MenuContext base, ClickKind kind, Ref ref, Consumer<MenuActionContext> handler) {
        scheduler.onEntity(holder.ctx().viewer(), () -> {
            Player live = Bukkit.getPlayer(holder.ctx().viewer().uuid());
            if (live == null) {
                return;
            }
            Map<String, String> args = ActionArguments.resolve(ref.args(), base.arguments());
            // Expand the menu-local placeholders too. The open spec's own placeholders{} block, and the item-drag
            // flow's %drag_*% tokens, so an action reads the dropped item the same way the renderer reads it in text.
            args = ActionArguments.resolveLocals(args, base.localPlaceholders());
            handler.accept(new MenuActionContext(base, live, kind, args, new HolderControl(holder)));
        });
    }

    /**
     * The {@link MenuControl} the engine binds to one open window for the duration of a click: it drives that
     * holder's repaint through the same viewer's-entity-thread hop {@link #navigate} uses and the same
     * {@link #repaint}/{@link #repaginate} path a pagination click takes, so a menu-control action never touches the
     * inventory off the viewer's region thread and never opens a second window. It captures only the holder; the live
     * player is re-resolved inside each repaint, so a viewer who logged off in the gap is simply skipped there.
     */
    private final class HolderControl implements MenuControl {

        private final MenuHolder holder;

        HolderControl(MenuHolder holder) {
            this.holder = holder;
        }

        @Override
        public void refresh() {
            scheduler.onEntity(holder.ctx().viewer(), () -> repaint(holder));
        }

        @Override
        public void refreshSlot(int slot) {
            scheduler.onEntity(holder.ctx().viewer(), () -> refreshOneSlot(holder, slot));
        }

        @Override
        public void resetPagination() {
            scheduler.onEntity(holder.ctx().viewer(), () -> repaginate(holder, 0));
        }

        @Override
        public void sortList(String listId, SortDirection direction) {
            Objects.requireNonNull(listId, "listId");
            Objects.requireNonNull(direction, "direction");
            scheduler.onEntity(
                    holder.ctx().viewer(),
                    () -> applyListControl(holder, listId, state -> applySort(state, direction)));
        }

        @Override
        public void filterList(String listId, String key, String value) {
            Objects.requireNonNull(listId, "listId");
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");
            scheduler.onEntity(
                    holder.ctx().viewer(), () -> applyListControl(holder, listId, state -> state.filter(key, value)));
        }

        @Override
        public void searchList(String listId, String key) {
            Objects.requireNonNull(listId, "listId");
            Objects.requireNonNull(key, "key");
            scheduler.onEntity(holder.ctx().viewer(), () -> beginListSearch(holder, listId, key));
        }
    }

    /** Advance, step back, or reset {@code state}'s sort; each mutation zeroes the page, which the re-query then reads. */
    private static void applySort(ListQueryState state, SortDirection direction) {
        switch (direction) {
            case NEXT -> state.nextSort();
            case PREVIOUS -> state.previousSort();
            case RESET -> state.resetSort();
        }
    }

    /**
     * The one entry a {@code list-sort}/{@code list-filter}/{@code list-search} change takes into the paged re-query,
     * so the three controls and a page flip share the query, the in-flight flag, the thread hops and the render. Runs on
     * the viewer's entity thread. It finds the paged list the control names (an unknown id is a logged no-op, never a
     * crash), drops out for a plain list or one not shown as paged (neither can sort or filter at the source), then
     * unless a query is already in flight. Mutates the list's query state (which zeroes its page) and issues the same
     * off-thread query {@link #startPagedQuery} runs for a flip, targeting page zero because the changed sort or filter
     * makes any later page meaningless.
     */
    private void applyListControl(MenuHolder holder, String listId, Consumer<ListQueryState> mutation) {
        MenuItemSpec listItem = listItemBySource(holder.spec(), listId);
        if (listItem == null) {
            LOG.warning("event=list_control_unknown_list menu=" + holder.specId() + " id=" + listId);
            return;
        }
        ListSpec listSpec = listItem.list().orElseThrow();
        String sourceId = listSpec.source().id();
        PagedListView view = holder.ctx().pagedViews().get(sourceId);
        if (view == null) {
            return;
        }
        BiFunction<MenuContext, PageRequest, PagedResult<?>> source =
                pagedLists.get(sourceId).orElse(null);
        if (source == null || holder.pagedFlipInFlight()) {
            return;
        }
        ListQueryState state = holder.queryState(sourceId, listSpec.sorts());
        mutation.accept(state);
        startPagedQuery(holder, source, state, sourceId, view.size(), 0, listSpec.sorts());
    }

    /**
     * Open the {@code list-search} text prompt, reusing the same {@link MenuTextPrompt} seam an {@code input:} step
     * drives, and on submit store the typed line as the list's {@code key} filter through the shared
     * {@link #applyListControl} re-query; a cancel changes nothing. Runs on the viewer's entity thread, where the seam
     * delivers both callbacks. An engine wired without a prompt, or a list this menu does not carry, is a logged no-op.
     * The prompt carries no label of its own, the operator's per-key input mode supplies the surface, so no
     * player-facing text is produced here.
     */
    private void beginListSearch(MenuHolder holder, String listId, String key) {
        if (textPrompt == null) {
            LOG.warning("event=list_search_unavailable menu=" + holder.specId() + " id=" + listId);
            return;
        }
        if (listItemBySource(holder.spec(), listId) == null) {
            LOG.warning("event=list_control_unknown_list menu=" + holder.specId() + " id=" + listId);
            return;
        }
        Player live = Bukkit.getPlayer(holder.ctx().viewer().uuid());
        if (live == null) {
            return;
        }
        textPrompt.prompt(
                live,
                key,
                Component.empty(),
                null,
                text -> applyListControl(holder, listId, state -> state.filter(key, text)),
                () -> {});
    }

    /** The list-backed item whose source id is {@code sourceId}, or null when this menu carries no such list. */
    @Nullable private static MenuItemSpec listItemBySource(MenuSpec spec, String sourceId) {
        for (MenuItemSpec item : spec.items().values()) {
            if (item.list().isPresent() && item.list().get().source().id().equals(sourceId)) {
                return item;
            }
        }
        return null;
    }

    /**
     * Re-render a single slot of the holder's window. The engine's {@code populate} path is whole-inventory: it has
     * no per-slot renderer yet, so a correct partial redraw is a full {@link #repaint}: broader than the caller
     * asked, but idempotent (the same spec and cached lists are redrawn) and never wrong. An out-of-range slot is a
     * no-op, so a spec typo cannot repaint on every click. When per-slot rendering lands this narrows to the one slot.
     */
    private void refreshOneSlot(MenuHolder holder, int slot) {
        if (slot < 0 || slot >= holder.getInventory().getSize()) {
            return;
        }
        repaint(holder);
    }

    /** Re-render the same holder one page over (next/previous), clamped at zero; jump is a v1 no-op target. */
    private void navigate(MenuHolder holder, ItemType type) {
        int page = holder.ctx().page();
        int newPage =
                switch (type) {
                    case NEXT -> page + 1;
                    case PREVIOUS -> Math.max(0, page - 1);
                    default -> page;
                };
        scheduler.onEntity(holder.ctx().viewer(), () -> repaginate(holder, newPage));
    }

    private void repaginate(MenuHolder holder, int newPage) {
        if (flipPagedList(holder, newPage)) {
            return;
        }
        holder.setCtx(holder.ctx().withPage(newPage));
        repaint(holder);
    }

    /**
     * Flip a paged list one page over by re-querying its source, rather than re-slicing a cache it does not hold.
     * Runs on the viewer's entity thread. Returns {@code false} when the menu's list is a plain in-memory source (or
     * it has no list at all), so the caller takes the historic synchronous re-slice, byte for byte: the paged
     * registry is never consulted for such a menu, because a plain list is never given a {@link PagedListView}. For a
     * paged source it clamps to the reachable target page, builds the immutable {@link PageRequest} here, and hops to
     * {@link Scheduler#async} to fetch that page; the page on screen stays put until the new one lands. A second flip
     * while a query is in flight is dropped, not queued, so a mashed arrow issues one query at a time and a later page
     * can never be overwritten by an earlier one. A flip that cannot move, already at the first or last page, leaves
     * the current page up and issues no query, matching what {@link com.uxplima.uxmessentials.shared.adapter.inbound
     * .gui.menu.eval.Pagination} does for a plain list: it clamps, it never wraps.
     */
    private boolean flipPagedList(MenuHolder holder, int requestedPage) {
        MenuItemSpec listItem = firstListItem(holder.spec());
        if (listItem == null) {
            return false;
        }
        com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ListSpec listSpec =
                listItem.list().orElseThrow();
        String sourceId = listSpec.source().id();
        PagedListView view = holder.ctx().pagedViews().get(sourceId);
        if (view == null) {
            return false;
        }
        BiFunction<MenuContext, PageRequest, PagedResult<?>> source =
                pagedLists.get(sourceId).orElse(null);
        if (source == null || holder.pagedFlipInFlight()) {
            return true;
        }
        ListQueryState state = holder.queryState(sourceId, listSpec.sorts());
        int size = view.size();
        int target = Math.max(0, Math.min(requestedPage, state.pageCount(size) - 1));
        if (target != state.page()) {
            startPagedQuery(holder, source, state, sourceId, size, target, listSpec.sorts());
        }
        return true;
    }

    /**
     * Set the in-flight flag and hop off the entity thread to fetch {@code target}. Every value the async body reads is
     * built here, on the entity thread, and handed across immutable. The {@link PageRequest} from the list's live
     * query state, the query context, and the viewer, so the async lambda touches no holder field and the holder's
     * single-threaded contract holds.
     */
    private void startPagedQuery(
            MenuHolder holder,
            BiFunction<MenuContext, PageRequest, PagedResult<?>> source,
            ListQueryState state,
            String sourceId,
            int size,
            int target,
            List<String> sorts) {
        holder.pagedFlipInFlight(true);
        PageRequest request = state.request(size).atPage(target);
        MenuContext queryCtx = holder.ctx();
        PlayerRef viewer = holder.ctx().viewer();
        scheduler.async(() -> runPagedQuery(holder, source, queryCtx, request, viewer, sourceId, size, target, sorts));
    }

    /**
     * Off the viewer's entity thread: fetch the requested page, then hop back to render it. Reads and writes no holder
     * field here (every argument is an immutable value captured on the entity thread) so the holder's single-threaded
     * contract holds. A query that throws is logged with context and hops back only to clear the in-flight flag, so the
     * page already on screen stays put and the arrows are freed for the next click rather than wedged.
     */
    private void runPagedQuery(
            MenuHolder holder,
            BiFunction<MenuContext, PageRequest, PagedResult<?>> source,
            MenuContext queryCtx,
            PageRequest request,
            PlayerRef viewer,
            String sourceId,
            int size,
            int target,
            List<String> sorts) {
        List<Object> rows;
        long total;
        // Everything that can fail belongs inside the guard, not just the source call: a source handing back null, or a
        // page assembly that throws, would otherwise escape on this thread and strand the in-flight flag, leaving the
        // viewer's arrows dead for the rest of the session.
        try {
            PagedResult<?> result = source.apply(queryCtx, request);
            if (result == null) {
                throw new IllegalStateException("paged list source '" + sourceId + "' returned null");
            }
            rows = PagedListRows.combine(sourceId, size, result, LOG);
            total = result.totalCount();
        } catch (RuntimeException failure) {
            LOG.log(Level.WARNING, "event=paged_flip_failed id=" + sourceId + " page=" + target, failure);
            scheduler.onEntity(viewer, () -> holder.pagedFlipInFlight(false));
            return;
        }
        scheduler.onEntity(viewer, () -> completePagedFlip(holder, sourceId, sorts, target, size, total, rows));
    }

    /**
     * On the viewer's entity thread: settle the flip. The in-flight flag is cleared first, so a viewer who closed the
     * window while the query ran frees the arrows for their next session rather than wedging them, and a closed window
     * is then a clean early return, the render never runs against a dead holder and never throws. When the window is
     * still this holder's, the fetched page is committed: the list's query state records the new page and corpus total,
     * only this list's rows and paged view are swapped in (a plain list sharing the menu keeps its cached corpus), and
     * the window is repainted in place.
     */
    private void completePagedFlip(
            MenuHolder holder, String sourceId, List<String> sorts, int page, int size, long total, List<Object> rows) {
        holder.pagedFlipInFlight(false);
        Player live = Bukkit.getPlayer(holder.ctx().viewer().uuid());
        if (live == null) {
            return;
        }
        // The window may have closed while the query was in flight; a closed view has no top inventory to read a
        // holder from, so the null-guard is what makes the mid-query close a clean no-render return rather than a
        // throw.
        var top = live.getOpenInventory().getTopInventory();
        if (top == null || !(top.getHolder() instanceof MenuHolder h) || h != holder) {
            return;
        }
        ListQueryState state = holder.queryState(sourceId, sorts);
        state.page(page);
        state.recordTotal(total);
        Map<String, List<?>> lists = new HashMap<>(holder.resolvedLists());
        lists.put(sourceId, rows);
        holder.setResolvedLists(lists);
        Map<String, PagedListView> views = new HashMap<>(holder.ctx().pagedViews());
        views.put(sourceId, new PagedListView(page, total, size));
        holder.setCtx(holder.ctx().withPage(page).withPagedViews(views));
        repaint(holder);
    }

    /** The first list-backed item of {@code spec} in declaration order, the list the nav buttons page, or null. */
    @Nullable private static MenuItemSpec firstListItem(MenuSpec spec) {
        for (MenuItemSpec item : spec.items().values()) {
            if (item.list().isPresent()) {
                return item;
            }
        }
        return null;
    }

    /**
     * The one repaint path: redraw the holder's window in place at its current page, rebuilding the click routing
     * first so a stale slot can never be clicked. Both {@link #repaginate} (after moving the page) and a
     * {@code refresh} control action (at the current page) funnel through here, so there is a single place the engine
     * repaints a spec menu.
     */
    private void repaint(MenuHolder holder) {
        holder.clearClickMap();
        renderer.populate(
                holder.getInventory(), holder.spec(), holder.ctx(), holder::recordSlot, holder.resolvedLists(), false);
        if (holder.spec().bottomInventory()) {
            Player live = Bukkit.getPlayer(holder.ctx().viewer().uuid());
            if (live != null) {
                // Re-paint the bottom without re-snapshotting: the real items are held on the holder until close;
                // populateBottom clears the 36-slot canvas and redraws only the menu tiles.
                renderer.populateBottom(
                        live.getInventory(), holder.spec(), holder.ctx(), holder::recordSlot, holder.resolvedLists());
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof MenuHolder holder) {
            readBackContent(holder, event.getInventory());
            closeMenu(holder);
            restoreBottom(holder, event.getPlayer());
            // A preview window carries a close hook that steps the operator back to the grid editor; the reopen it runs
            // funnels through the scheduler, so it is deferred off this close event rather than opening a window inside
            // it. Every non-preview menu carries no hook, so this is a no-op for them.
            holder.fireCloseHook();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Inventory top = event.getPlayer().getOpenInventory().getTopInventory();
        if (top != null && top.getHolder() instanceof MenuHolder holder) {
            readBackContent(holder, top);
            closeMenu(holder);
            restoreBottomNow(holder, event.getPlayer());
        }
    }

    /**
     * Keep a bottom-inventory menu's tiles out of a death's drops while dropping the viewer's real items in their
     * place, so dying with such a menu open behaves exactly like an ordinary death. This handler lives on the menu
     * lifecycle listener (which already reads the open holder cleanly, as {@link #onQuit} does) rather than on a
     * separate router. When the dying player still has a bottom-inventory menu open, the marked tiles sit in their
     * real inventory and would otherwise drop, and could be picked up as a dupe, so it strips every marked tile from
     * the drops, adds the snapshot's real items so those drop as normal, and clears the snapshot so the paired
     * close-restore into the respawning player becomes a no-op. The drop is then the single disposition of the real
     * items. An ordinary menu (or none) carries no snapshot and is left untouched. Runs on the death event thread, the
     * player's own region thread, mutating only that death's drop list.
     */
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        InventoryHolder open =
                event.getEntity().getOpenInventory().getTopInventory().getHolder();
        if (!(open instanceof MenuHolder holder)) {
            return;
        }
        @Nullable ItemStack @Nullable [] snapshot = holder.bottomSnapshot();
        if (snapshot == null) {
            return;
        }
        event.getDrops().removeIf(MenuItemMark::isMarked);
        for (@Nullable ItemStack real : snapshot) {
            if (real != null) {
                event.getDrops().add(real);
            }
        }
        holder.setBottomSnapshot(null);
    }

    /**
     * The single teardown choke-point both close paths (the player closing the window and a quit with it open)
     * funnel through: it stops the refresh task so a timer can never re-render a closed window. Idempotent, so a
     * close that is immediately followed by a quit close is a harmless no-op.
     *
     * <p>Phase 2 adds generic close actions: this is where {@code spec.closeActions()} will be dispatched through
     * the {@link ActionRegistry}, branching on whether the menu was really closed versus reopened by navigation.
     * v1 has no generic action handlers, so teardown is the only work.
     */
    private void closeMenu(MenuHolder holder) {
        holder.cancelRefresh();
    }

    /**
     * Put the viewer's real bottom inventory back after a bottom-inventory menu closes. Mutating a player inventory
     * inside {@link InventoryCloseEvent} is a Paper gotcha, so the restore is deferred to the player's next tick on
     * their own region thread via the {@link Scheduler} port. The snapshot is nulled inside the deferred task, not at
     * schedule time, so a quit that restores synchronously ({@link #restoreBottomNow}) first wins the race and this
     * pass then no-ops, and a double close (a close immediately followed by a quit close) restores exactly once. The
     * deferred {@code setStorageContents} overwrites the whole 36-slot canvas with the real items, so it composes with
     * the anti-dupe close sweep regardless of order. The sweep may strip an escaped tile first, the restore then
     * overwrites the canvas either way. An ordinary menu carries no snapshot and returns immediately.
     */
    private void restoreBottom(MenuHolder holder, HumanEntity human) {
        if (holder.bottomSnapshot() == null || !(human instanceof Player player)) {
            return;
        }
        PlayerRef ref = new PlayerRef(player.getUniqueId(), player.getName());
        scheduler.onEntity(ref, () -> {
            @Nullable ItemStack @Nullable [] snapshot = holder.bottomSnapshot();
            if (snapshot != null) {
                holder.setBottomSnapshot(null);
                player.getInventory().setStorageContents(snapshot);
            }
        });
    }

    /**
     * Put the viewer's real bottom inventory back synchronously when they quit with a bottom-inventory menu open. A
     * deferred restore would run after the player is gone and their data saved, so on the quit path the menu tiles are
     * replaced with the real items now, on the quit event thread (the player's own region thread), before that save.
     * Nulling the snapshot makes the paired deferred close-restore a no-op. An ordinary menu carries no snapshot.
     */
    private void restoreBottomNow(MenuHolder holder, Player player) {
        @Nullable ItemStack @Nullable [] snapshot = holder.bottomSnapshot();
        if (snapshot != null) {
            holder.setBottomSnapshot(null);
            player.getInventory().setStorageContents(snapshot);
        }
    }

    private static ClickKind kindOf(ClickType click) {
        return switch (click) {
            case LEFT -> ClickKind.LEFT;
            case RIGHT -> ClickKind.RIGHT;
            case SHIFT_LEFT -> ClickKind.SHIFT_LEFT;
            case SHIFT_RIGHT -> ClickKind.SHIFT_RIGHT;
            case MIDDLE -> ClickKind.MIDDLE;
            case DROP -> ClickKind.DROP;
            case CONTROL_DROP -> ClickKind.CONTROL_DROP;
            case DOUBLE_CLICK -> ClickKind.DOUBLE_CLICK;
            default -> ClickKind.LEFT;
        };
    }
}

package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.render;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.BiConsumer;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.LayoutFit;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ConditionRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.ContentProviderRegistry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval.BottomSlots;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval.Pagination;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval.PriorityLayering;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers.ContentProvider;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.ActionArguments;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.PagedListView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ContentRegionSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ListSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuItemSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpec;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.Ref;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Lays a whole menu spec into an open inventory for one viewer. Static items are collapsed through
 * {@link PriorityLayering} (the visible, highest-priority item wins each slot) and rendered into place; a
 * list-backed item draws its entries from the {@code resolvedLists} the caller passes in and
 * {@link Pagination paginates} them across its content slots, stamping the list template once per entry. The
 * renderer never queries a list source itself. {@link com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus}
 * resolves every source off the viewer's region thread once and hands the cached result here, so a redraw (a page
 * flip, a refresh tick) re-renders from the cache and never blocks the region thread on a database. Every slot the
 * renderer fills is reported to {@code clickSink} as a {@link RenderedSlot} so the runtime can route a later click
 * back to the spec, and, for a list cell, to the live element that filled it. The renderer reads only the
 * conditions registry, the spec, and the resolved lists; it never names a feature.
 */
@NullMarked
public final class MenuRenderer {

    /** The width of every inventory row, the divisor that turns a bottom-inventory menu's rows into its chest top. */
    private static final int SLOTS_PER_ROW = 9;

    private final ItemRenderer itemRenderer;
    private final ConditionRegistry conditions;

    /**
     * The providers that fill a menu's {@code content {}} regions. Null on an engine wired without any, every
     * spec-only test fixture, in which case a region (if a spec even declares one) is left empty, exactly what an
     * unregistered provider gets. Only production wiring, which holds the feature-populated registry, passes it.
     */
    @Nullable private final ContentProviderRegistry contents;

    public MenuRenderer(ItemRenderer itemRenderer, ConditionRegistry conditions) {
        this(itemRenderer, conditions, null);
    }

    /** The canonical constructor, carrying the content-region providers a menu's live-item slots are filled by. */
    public MenuRenderer(
            ItemRenderer itemRenderer, ConditionRegistry conditions, @Nullable ContentProviderRegistry contents) {
        this.itemRenderer = Objects.requireNonNull(itemRenderer, "itemRenderer");
        this.conditions = Objects.requireNonNull(conditions, "conditions");
        this.contents = contents;
    }

    /**
     * The item renderer this menu renderer draws each icon through. The same collaborator the grid canvas needs to
     * render a slot preview. Exposed so the engine can build a {@link GridRenderer} from an already-wired
     * {@code MenuRenderer} without threading a second {@code ItemRenderer} through every engine constructor; it is
     * engine-internal (both live under {@code ..gui.menu..}), so no consumer outside the engine reaches it.
     */
    public ItemRenderer itemRenderer() {
        return itemRenderer;
    }

    /**
     * Resolve {@code spec}'s title for {@code ctx} through the same placeholder/catalog path an item name takes, so a
     * subject-driven title fills its {@code {token}} arguments from the open context. Delegates to the item renderer,
     * which already owns the placeholder registry and the catalog lookup, so no extra collaborator is threaded here.
     */
    public Component title(MenuSpec spec, MenuContext ctx) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(ctx, "ctx");
        return itemRenderer.title(spec.title(), ctx);
    }

    /**
     * The menu's title as plain text: the same title {@link #title} resolves, flattened of all formatting. A
     * Bedrock form title is a flat string, so the hybrid form renderer reads the title through here rather than as
     * a rich component.
     */
    public String titleText(MenuSpec spec, MenuContext ctx) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(ctx, "ctx");
        return PlainTextComponentSerializer.plainText().serialize(title(spec, ctx));
    }

    /**
     * The button label the hybrid form renderer shows in place of {@code item}. Its resolved display name as plain
     * text. Delegates to the item renderer, which owns the name resolution, so a form button reads the exact name a
     * chest icon would carry.
     */
    public String buttonText(MenuItemSpec item, MenuContext ctx) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(ctx, "ctx");
        return itemRenderer.buttonText(item, ctx);
    }

    /**
     * The resolved icon material spec for {@code item}, a material name or a {@code skull:}/{@code head:} value with
     * any {@code %token%} expanded: the hybrid form renderer reads to source a button's image. Delegates to the item
     * renderer, which owns the material resolution, so a form button sources its icon from the exact spec a chest icon
     * renders from.
     */
    public String materialSpec(MenuItemSpec item, MenuContext ctx) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(ctx, "ctx");
        return itemRenderer.materialSpec(item, ctx);
    }

    /**
     * A raw spec string resolved for {@code ctx} through the same {@code %token%}/{@code @key} path an item name takes,
     * flattened to plain text: a Bedrock CustomForm's title, intro content and widget labels/options are flat strings.
     * Delegates to the item renderer, which owns the resolution, so an operator string in a {@code bedrock {}} block
     * fills its tokens exactly as a menu title or item name would.
     */
    public String plainText(String raw, MenuContext ctx) {
        Objects.requireNonNull(raw, "raw");
        Objects.requireNonNull(ctx, "ctx");
        return itemRenderer.plainText(raw, ctx);
    }

    /**
     * A shared {@link MessageKey} resolved for {@code viewer} and flattened to plain text, a label (a confirm
     * window's yes/no) the hybrid form renderer needs as a flat string where the chest paints wordless wool.
     * Delegates to the item renderer, which owns the catalog lookup, so the label honours the viewer's locale exactly
     * as a menu title or item name does.
     */
    public String plainMessage(PlayerRef viewer, MessageKey key) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(key, "key");
        return itemRenderer.plainMessage(viewer, key);
    }

    /**
     * The actionable static items of {@code spec}, in ascending slot order. What the hybrid renderer turns into a
     * Bedrock SimpleForm's button list. Visibility is the same view-requirement rule the chest render uses (a hidden
     * or out-priority item is dropped, resolved through {@link PriorityLayering}), so a Bedrock viewer sees the same
     * items a Java viewer would. On top of that, only items that carry a click action become form buttons, a
     * decorative/filler item (the auto-filler, a blank border pane, any display-only item with no click) is omitted,
     * since a Bedrock button that does nothing on tap is meaningless; the Java chest still paints it. A menu whose
     * every item is decorative therefore yields an empty button list: the form still opens with just its title.
     * List-backed items are skipped here: this method returns only the static buttons, and the form path pages a
     * list's own entries into buttons after them.
     */
    public List<MenuItemSpec> visibleStaticItemsInSlotOrder(MenuSpec spec, MenuContext ctx) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(ctx, "ctx");
        List<MenuItemSpec> staticItems = new ArrayList<>();
        for (MenuItemSpec item : spec.items().values()) {
            if (item.list().isEmpty()) {
                staticItems.add(item);
            }
        }
        Map<Integer, MenuItemSpec> placed = PriorityLayering.resolve(staticItems, it -> viewPasses(it, ctx));
        List<MenuItemSpec> ordered = new ArrayList<>(placed.size());
        new TreeMap<>(placed).forEach((slot, item) -> {
            if (item.click().hasAnyAction()) {
                ordered.add(item);
            }
        });
        return ordered;
    }

    /**
     * Fills {@code inv} with the items {@code spec} resolves to for {@code ctx}'s viewer and page, reporting each
     * placed slot to {@code clickSink}. Static items are placed first, then list cells overwrite their own content
     * slots; a spec keeps the two slot ranges disjoint, so order only matters for code clarity here.
     */
    public void populate(
            Inventory inv,
            MenuSpec spec,
            MenuContext ctx,
            BiConsumer<Integer, RenderedSlot> clickSink,
            Map<String, List<?>> resolvedLists) {
        populate(inv, spec, ctx, clickSink, resolvedLists, true);
    }

    /**
     * The same fill, told whether this is the window's first paint or a redraw of one the viewer is already looking
     * at. Everything a spec declares is drawn identically either way; the flag only reaches the content regions, where
     * a region the viewer physically fills ({@link ContentProvider#repaintsOnRedraw()} {@code false}) is painted once
     * on the first pass and then left alone, because on a redraw the window holds items the model has not been told
     * about yet. Every caller that opens or previews a window passes {@code true}; the two redraw paths pass
     * {@code false}.
     */
    public void populate(
            Inventory inv,
            MenuSpec spec,
            MenuContext ctx,
            BiConsumer<Integer, RenderedSlot> clickSink,
            Map<String, List<?>> resolvedLists,
            boolean initialPaint) {
        Objects.requireNonNull(inv, "inv");
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(clickSink, "clickSink");
        Objects.requireNonNull(resolvedLists, "resolvedLists");
        List<MenuItemSpec> staticItems = new ArrayList<>();
        List<MenuItemSpec> listItems = new ArrayList<>();
        for (MenuItemSpec item : spec.items().values()) {
            (item.list().isPresent() ? listItems : staticItems).add(item);
        }
        Map<Integer, @Nullable ItemStack> held = initialPaint ? Map.of() : holdViewerFilledRegions(inv, spec);
        MenuContext staticCtx = pagedAwareStaticCtx(ctx, listItems, resolvedLists);
        Map<Integer, MenuItemSpec> placed = populateStatic(inv, staticItems, staticCtx, clickSink);
        for (MenuItemSpec listItem : listItems) {
            populateList(inv, listItem, ctx, staticCtx, placed, clickSink, resolvedLists);
        }
        populateContent(inv, spec, ctx, initialPaint);
        held.forEach(inv::setItem);
    }

    /**
     * Take aside what sits in the slots of every region the viewer physically fills, so a redraw can put it back
     * exactly as it was. The chrome underneath such a region (a window-wide filler is the usual thing) is redrawn on
     * every pass and would otherwise paint over the stacks a player has placed but the feature has not yet read back.
     * Empty slots are held too, as null entries, because a slot the viewer has just emptied must stay empty rather
     * than get a filler tile the feature would later read back as an item. Only ever called for a redraw, and only for
     * a region whose provider opts out of repainting, so an ordinary menu allocates nothing here.
     */
    private Map<Integer, @Nullable ItemStack> holdViewerFilledRegions(Inventory inv, MenuSpec spec) {
        Map<Integer, @Nullable ItemStack> held = new HashMap<>();
        for (ContentRegionSpec region : spec.contents().values()) {
            ContentProvider provider =
                    contents == null ? null : contents.get(region.id()).orElse(null);
            if (provider == null || provider.repaintsOnRedraw()) {
                continue;
            }
            for (int slot : region.slots().slots()) {
                if (fits(inv, slot)) {
                    held.put(slot, inv.getItem(slot));
                }
            }
        }
        return held;
    }

    /**
     * Fill each of {@code spec}'s content regions from its registered provider, last so a region always wins the
     * slots it declares over the chrome (a full-window filler is the usual thing underneath it). The slots are
     * cleared first, so a region whose provider is missing, or whose feature has nothing to show right now, leaves
     * genuinely empty slots rather than a stale tile the viewer could try to take. The engine records no click
     * routing for them: a click there is resolved against the region itself, not the spec's items.
     *
     * <p>A region whose provider does not repaint on a redraw is skipped entirely once the window is up, not even
     * cleared, because on a redraw its slots hold the viewer's own stacks, which only the feature's read-back may
     * dispose of.
     */
    private void populateContent(Inventory inv, MenuSpec spec, MenuContext ctx, boolean initialPaint) {
        for (ContentRegionSpec region : spec.contents().values()) {
            ContentProvider provider =
                    contents == null ? null : contents.get(region.id()).orElse(null);
            if (!initialPaint && provider != null && !provider.repaintsOnRedraw()) {
                continue;
            }
            List<Integer> slots = region.slots().slots();
            for (int slot : slots) {
                if (fits(inv, slot)) {
                    inv.setItem(slot, null);
                }
            }
            if (provider == null) {
                continue;
            }
            List<@Nullable ItemStack> painted = provider.render(ctx, region);
            int drawn = LayoutFit.drawable("content region " + region.id(), painted.size(), slots);
            for (int index = 0; index < drawn; index++) {
                int slot = slots.get(index);
                if (fits(inv, slot)) {
                    inv.setItem(slot, painted.get(index));
                }
            }
        }
    }

    /**
     * Paint a bottom-inventory menu's bottom items (those whose raw slot sits at or past the chest top) into the
     * viewer's own {@code playerInv}, mapping each raw slot to its player index and recording the <em>raw</em> slot so a
     * later click routes through the holder's click map exactly as a top slot does. The 36-slot canvas is cleared
     * first so a re-render leaves no stale tile behind; the viewer's real items are held in the holder snapshot, not
     * here. Static items only, resolved through the same priority/view layering the top uses, so a hidden or
     * out-priority bottom item is treated identically. A list-backed item pages across the chest top alone, never the
     * player inventory.
     */
    public void populateBottom(
            PlayerInventory playerInv,
            MenuSpec spec,
            MenuContext ctx,
            BiConsumer<Integer, RenderedSlot> clickSink,
            Map<String, List<?>> resolvedLists) {
        Objects.requireNonNull(playerInv, "playerInv");
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(ctx, "ctx");
        Objects.requireNonNull(clickSink, "clickSink");
        Objects.requireNonNull(resolvedLists, "resolvedLists");
        playerInv.setStorageContents(new ItemStack[BottomSlots.PLAYER_SLOTS]);
        int topSize = spec.rows() * SLOTS_PER_ROW;
        List<MenuItemSpec> staticItems = new ArrayList<>();
        List<MenuItemSpec> listItems = new ArrayList<>();
        for (MenuItemSpec item : spec.items().values()) {
            (item.list().isPresent() ? listItems : staticItems).add(item);
        }
        MenuContext staticCtx = pagedAwareStaticCtx(ctx, listItems, resolvedLists);
        Map<Integer, MenuItemSpec> placed = PriorityLayering.resolve(staticItems, it -> viewPasses(it, staticCtx));
        for (Map.Entry<Integer, MenuItemSpec> entry : placed.entrySet()) {
            int rawSlot = entry.getKey();
            if (rawSlot < topSize || rawSlot >= topSize + BottomSlots.PLAYER_SLOTS) {
                continue;
            }
            MenuItemSpec item = entry.getValue();
            playerInv.setItem(BottomSlots.rawToPlayerSlot(rawSlot, topSize), itemRenderer.render(item, staticCtx));
            clickSink.accept(rawSlot, new RenderedSlot(item, null));
        }
    }

    /**
     * The context a static item renders with, carrying the {@code %page%}/{@code %max_page%} its page indicator reads
     * computed once before static items draw so the indicator matches the count {@link #populateList} pages across. A
     * spec with no list item stays a single page. Only the first list item is consulted. A spec pairs one scrollable
     * list with its page controls, and the page count those controls report is that list's.
     *
     * <p>When that list is a paged source (its id is in {@link MenuContext#pagedViews()}), the page and count come from
     * its snapshot (the corpus total the source reported, not the length of the one rendered page) so a "Page x/y"
     * indicator is right even though the engine holds only the page it can see. An in-memory list keeps the historic
     * behaviour: it paginates its whole corpus in place to learn the count.
     */
    private MenuContext pagedAwareStaticCtx(
            MenuContext ctx, List<MenuItemSpec> listItems, Map<String, List<?>> resolvedLists) {
        if (listItems.isEmpty()) {
            return ctx.withPageCount(1);
        }
        MenuItemSpec listItem = listItems.get(0);
        PagedListView view =
                ctx.pagedViews().get(listItem.list().orElseThrow().source().id());
        if (view != null) {
            return ctx.withPage(view.page()).withPageCount(view.pageCount());
        }
        List<?> entries = entriesOf(listItem, resolvedLists);
        int count = Pagination.paginate(entries, listItem.slots().slots(), ctx.page())
                .pageCount();
        return ctx.withPageCount(count);
    }

    /** The pre-resolved entries backing {@code listItem}, or an empty list when its source resolved to nothing. */
    private List<?> entriesOf(MenuItemSpec listItem, Map<String, List<?>> resolvedLists) {
        ListSpec listSpec = listItem.list().orElseThrow();
        return resolvedLists.getOrDefault(listSpec.source().id(), List.of());
    }

    /** Resolve the static items to one-per-slot, render the survivors, and return the placement the lists layer over. */
    private Map<Integer, MenuItemSpec> populateStatic(
            Inventory inv,
            List<MenuItemSpec> staticItems,
            MenuContext ctx,
            BiConsumer<Integer, RenderedSlot> clickSink) {
        Map<Integer, MenuItemSpec> placed = PriorityLayering.resolve(staticItems, it -> viewPasses(it, ctx));
        for (Map.Entry<Integer, MenuItemSpec> entry : placed.entrySet()) {
            int slot = entry.getKey();
            if (!fits(inv, slot)) {
                continue;
            }
            MenuItemSpec item = entry.getValue();
            inv.setItem(slot, itemRenderer.render(item, ctx));
            clickSink.accept(slot, new RenderedSlot(item, null));
        }
        return placed;
    }

    /** Page one list item's pre-resolved entries across its content slots, stamping the template once per entry. */
    private void populateList(
            Inventory inv,
            MenuItemSpec item,
            MenuContext ctx,
            MenuContext staticCtx,
            Map<Integer, MenuItemSpec> staticPlacement,
            BiConsumer<Integer, RenderedSlot> clickSink,
            Map<String, List<?>> resolvedLists) {
        ListSpec listSpec = item.list().orElseThrow();
        List<?> entries = entriesOf(item, resolvedLists);
        List<Integer> contentSlots = item.slots().slots();
        // A paged source's rows are already the page the query returned, so they are laid out at local page zero;
        // slicing them again at the viewer's page index would show page zero of an already-paged later page. Pinned
        // rows are still among the entries, so the PinnedEntry path fixes them to their claimed slot as before.
        int renderPage = ctx.pagedViews().containsKey(listSpec.source().id()) ? 0 : ctx.page();
        @SuppressWarnings("unchecked") // a list source's element type is opaque to the engine; entries flow as Object
        Pagination.Page<Object> page = Pagination.paginate((List<Object>) entries, contentSlots, renderPage);
        // Clear every content slot this page does not fill, so a reused window (a page flip to a shorter page, a
        // refresh that dropped entries) leaves no stale list tile behind. A slot backed by a deliberately layered
        // decoration (a frame the author placed over the base backdrop, priority > 0) clears back to that decoration
        // rather than to a bare hole, so the frame shows through where the list runs short. The base backdrop itself
        // (priority 0) is what a list is meant to replace, so those slots still clear to empty, keeping the convention
        // that an engine list leaves its unfilled cells bare. The click record stays the static item's, already
        // stamped by populateStatic, so a click on a restored slot routes to it.
        for (int slot : contentSlots) {
            if (fits(inv, slot)) {
                MenuItemSpec beneath = staticPlacement.get(slot);
                if (beneath != null && beneath.priority() > 0) {
                    inv.setItem(slot, itemRenderer.render(beneath, staticCtx));
                } else {
                    inv.setItem(slot, null);
                }
            }
        }
        MenuItemSpec template = listSpec.template();
        for (Map.Entry<Integer, Object> placement : page.placements()) {
            int slot = placement.getKey();
            if (!fits(inv, slot)) {
                continue;
            }
            MenuContext entryCtx = ctx.withEntry(placement.getValue());
            inv.setItem(slot, itemRenderer.render(template, entryCtx));
            clickSink.accept(slot, new RenderedSlot(template, placement.getValue()));
        }
    }

    /**
     * Whether {@code slot} is addressable in {@code inv}. Every chest slot a validated spec declares fits by
     * construction, so this only ever excludes a slot that overflows a smaller non-chest window (say slot 8 in a
     * five-slot hopper), which is skipped rather than throwing an out-of-bounds error and blanking the menu.
     */
    private static boolean fits(Inventory inv, int slot) {
        return slot >= 0 && slot < inv.getSize();
    }

    /**
     * Whether an item's {@code view} requirement block passes for {@code ctx}. The block decides how its requirements
     * combine (an all-mandatory AND (the historic flat list), a minimum OR / N-of-M, or an inverted condition) and
     * this supplies the per-requirement outcome it asks for: the condition is resolved against the registry (so a
     * valued condition written {@code has-money:100} splits its head off and the handler sees {@code value=100}, the
     * same registry-aware split the click and action paths take), tested, and negated when the requirement carries a
     * leading {@code !}. An empty block is visible; an unregistered condition holds {@code false}, fail-closed, so a
     * wiring gap hides the item rather than silently showing it. The condition's args have their
     * {@code %argument_<name>%} tokens expanded from the arguments the menu was opened with first, so a view can gate on
     * a typed open-command's argument; an argument-less open takes the identity fast-path and is byte-identical.
     */
    private boolean viewPasses(MenuItemSpec item, MenuContext ctx) {
        return item.view().passes(requirement -> {
            Ref eff = requirement.condition().resolve(conditions::has);
            boolean present = conditions
                    .get(eff.id())
                    .map(p -> p.test(ctx, ActionArguments.resolve(eff.args(), ctx.arguments())))
                    .orElse(false);
            return present != requirement.inverted();
        });
    }
}

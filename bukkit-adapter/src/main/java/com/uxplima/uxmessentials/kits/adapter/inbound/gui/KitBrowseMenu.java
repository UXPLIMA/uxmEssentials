package com.uxplima.uxmessentials.kits.adapter.inbound.gui;

import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.kits.application.ClaimKit;
import com.uxplima.uxmessentials.kits.application.KitAccess;
import com.uxplima.uxmessentials.kits.application.KitsMessageKey;
import com.uxplima.uxmessentials.kits.application.port.KitCategoryRepository;
import com.uxplima.uxmessentials.kits.domain.KitCategory;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval.PinnedEntry;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Registers the read-only {@code /kit} browse menu with the menu engine and opens it. The same view the old
 * {@code KitMenuView} drew, now rendered off a spec: one tile per kit the viewer may claim, paged through the
 * content slots, with previous/next buttons on the bottom row. When categories are defined the tiles mix drill-in
 * category tiles (sorted by their slot) with the kits at the current tree level, exactly as the old view did;
 * clicking a category drills in, the back button steps up to the parent, a left click on a kit claims it through
 * the same {@link ClaimKit} use case the {@code /kit} command drives, and a right click opens the kit's bespoke
 * {@link KitPreviewView}. With no categories the legacy flat grid is the root level with no sub-categories, one
 * spec serves both modes.
 *
 * <p>The level's tiles, their full lore strings, and each category's pinned slot are resolved up front on the
 * viewer's entity thread (where {@link #open} is called from) and handed to the engine as the menu subject,
 * mirroring {@code WarpBrowseMenu}: the kit and category sets are warm in-memory reads and {@link KitIconRenderer}
 * resolves the per-tile name/material/lore against the viewer's permission, cooldown, stock and affordability state
 * through the {@link Messages}/{@link KitAccess} ports on that thread, so the {@code kits:browse} list source only
 * reads that subject and the engine never touches a port off-thread. Drilling re-snapshots at the child level under
 * the same spec; {@code kits:browse-back} re-snapshots at the parent.
 *
 * <p>A category configured to a content slot pins to it on every page: the uniform browse row implements
 * {@link PinnedEntry}, returning the category's slot for a pinned category and an out-of-range {@code -1} (so it
 * flows) for kits and unpinned categories. Exactly reproducing the old {@code openCategory} rule that fixed a
 * category to {@code cat.slot()} only when that slot fell inside the content grid.
 */
@NullMarked
public final class KitBrowseMenu {

    /** The engine spec id this menu registers and opens under. */
    public static final String SPEC_ID = "kit-browse";

    /** Disk-first then bundled, mirroring the GUI-layout loader, so an operator edit to the spec takes effect. */
    private static final String SPEC_RESOURCE = "modules/kits/gui/kit-browse.conf";

    /** A row that is not a pinned category flows through the scrolling content slots, never fixed to a slot. */
    private static final int FLOWS = -1;

    /** The kind a browse tile stands for, so one list source can mix drill-in categories and kit tiles. */
    enum Kind {
        CATEGORY,
        KIT
    }

    private final Menus menus;
    private final Scheduler scheduler;
    private final ClaimKit claimKit;
    private final Notifier notifier;
    private final KitCategoryRepository categoryRepository;
    private final KitAccess access;
    private final KitPreviewView preview;
    private final KitIconRenderer iconRenderer;

    public KitBrowseMenu(
            Menus menus,
            Scheduler scheduler,
            ClaimKit claimKit,
            Notifier notifier,
            KitCategoryRepository categoryRepository,
            KitAccess access,
            KitPreviewView preview,
            Messages messages,
            GuiLayout layout,
            Clock clock) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.claimKit = Objects.requireNonNull(claimKit, "claimKit");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.categoryRepository = Objects.requireNonNull(categoryRepository, "categoryRepository");
        this.access = Objects.requireNonNull(access, "access");
        this.preview = Objects.requireNonNull(preview, "preview");
        this.iconRenderer = new KitIconRenderer(
                Objects.requireNonNull(messages, "messages"),
                access,
                Objects.requireNonNull(layout, "layout"),
                MiniMessage.miniMessage(),
                Objects.requireNonNull(clock, "clock"));
    }

    /** Register the per-tile list source, the kind-branching placeholders, the click/back actions, and the spec. */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.list("kits:browse", ctx -> ctx.subject(BrowseLevel.class).entries());
        bindings.placeholder("kit_browse_icon", ctx -> rowOf(ctx).icon());
        bindings.placeholder("kit_browse_name", ctx -> rowOf(ctx).name());
        bindings.placeholder("kit_browse_lore", ctx -> rowOf(ctx).lore());
        bindings.action("kits:browse-click", this::click);
        bindings.action("kits:browse-back", this::back);
        // The back button shows only below the root, exactly as the old view only drew it when a category was open.
        bindings.condition(
                "kits:browse-has-parent",
                (ctx, args) -> ctx.subject(BrowseLevel.class).categoryId().isPresent());
        menus.registerSpec(SPEC_ID, MenuSpecs.loadOrBundled(SPEC_RESOURCE, dataFolder, 6, log));
    }

    /**
     * Open the browse menu listing {@code kits} for {@code viewer} at the root level, scheduled on the viewer's
     * entity thread. The level's tiles, lore, and pinned slots are resolved there off the warm kit/category sets
     * through {@link KitIconRenderer}, and handed to the engine as the subject, so the engine renders without a
     * port read of its own. With no categories defined the root level is the flat legacy grid.
     */
    public void open(Player player, PlayerRef viewer, List<KitDefinition> kits) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(kits, "kits");
        List<KitDefinition> snapshot = List.copyOf(kits);
        scheduler.onEntity(viewer, () -> menus.open(viewer, SPEC_ID, level(viewer, snapshot, Optional.empty())));
    }

    /** The tile the slot being rendered or clicked stands for. */
    private BrowseRow rowOf(MenuContext ctx) {
        return ctx.entry(BrowseRow.class);
    }

    /**
     * Resolve one browse level into its mixed, fully-rendered tiles: the sub-categories of {@code categoryId}
     * sorted by their slot, then the kits at that level sorted by priority, reproducing the old view's ordering.
     * With no categories defined this is just the kits, so the legacy flat grid and the category root are one path.
     */
    private BrowseLevel level(PlayerRef viewer, List<KitDefinition> kits, Optional<String> categoryId) {
        List<BrowseRow> rows = new ArrayList<>();
        for (KitCategory category : subCategories(categoryId)) {
            rows.add(categoryRow(viewer, category));
        }
        for (KitDefinition kit : levelKits(kits, categoryId)) {
            rows.add(kitRow(viewer, kit));
        }
        return new BrowseLevel(kits, categoryId, rows);
    }

    /** The categories whose parent is {@code categoryId}, sorted by their display slot like the old view. */
    private List<KitCategory> subCategories(Optional<String> categoryId) {
        return categoryRepository.all().stream()
                .filter(category -> category.parentCategoryId().equals(categoryId))
                .sorted(Comparator.comparingInt(KitCategory::slot))
                .toList();
    }

    /** The kits that belong to {@code categoryId} (the uncategorised ones at the root), highest priority first. */
    private static List<KitDefinition> levelKits(List<KitDefinition> kits, Optional<String> categoryId) {
        return kits.stream()
                .filter(kit -> kit.categoryId().equals(categoryId))
                .sorted(Comparator.comparingInt(KitDefinition::priority).reversed())
                .toList();
    }

    private BrowseRow categoryRow(PlayerRef viewer, KitCategory category) {
        return new BrowseRow(
                Kind.CATEGORY,
                category.id(),
                iconRenderer.categoryMaterialName(category),
                iconRenderer.categoryNameSource(viewer, category),
                iconRenderer.categoryLoreSource(viewer, category),
                category.slot());
    }

    private BrowseRow kitRow(PlayerRef viewer, KitDefinition base) {
        KitDefinition variant = iconRenderer.variantOf(viewer, base);
        return new BrowseRow(
                Kind.KIT,
                base.id().value(),
                iconRenderer.kitMaterialName(viewer, variant),
                iconRenderer.kitNameSource(viewer, variant),
                iconRenderer.kitLoreSource(viewer, variant),
                FLOWS);
    }

    /**
     * Click a tile: drill into a clicked category by re-opening the browse spec at that category's level; left-click
     * a kit to claim it through the same {@link ClaimKit} use case the old view used; right-click a kit to open its
     * bespoke preview. The clicked tile's identity comes from the bound entry, never from re-reading the icon.
     */
    private void click(MenuActionContext ctx) {
        BrowseLevel level = ctx.subject(BrowseLevel.class);
        BrowseRow row = ctx.entry(BrowseRow.class);
        if (row.kind() == Kind.CATEGORY) {
            PlayerRef viewer = ctx.viewer();
            menus.open(viewer, SPEC_ID, level(viewer, level.kits(), Optional.of(row.id())));
            return;
        }
        kitClick(ctx, level, row);
    }

    /** Route a kit tile click: a right click previews, anything else claims, matching the old view's gesture split. */
    private void kitClick(MenuActionContext ctx, BrowseLevel level, BrowseRow row) {
        Optional<KitDefinition> kit = findKit(level, row.id());
        if (kit.isEmpty()) {
            return;
        }
        switch (ctx.clickKind()) {
            case RIGHT, SHIFT_RIGHT -> previewKit(ctx.player(), ctx.viewer(), kit.get());
            default -> claim(ctx.player(), ctx.viewer(), kit.get());
        }
    }

    /** The kit behind a clicked row, found in the level's snapshot by id so a claim acts on the live definition. */
    private static Optional<KitDefinition> findKit(BrowseLevel level, String kitId) {
        return level.kits().stream()
                .filter(kit -> kit.id().value().equals(kitId))
                .findFirst();
    }

    /** Step up to the parent level: re-open the browse spec at the current level's parent category. */
    private void back(MenuActionContext ctx) {
        BrowseLevel level = ctx.subject(BrowseLevel.class);
        Optional<String> parentId =
                level.categoryId().flatMap(categoryRepository::find).flatMap(KitCategory::parentCategoryId);
        PlayerRef viewer = ctx.viewer();
        menus.open(viewer, SPEC_ID, level(viewer, level.kits(), parentId));
    }

    /**
     * Claim the clicked kit on the viewer's entity thread (the grant moves items into the live inventory).
     * {@link ClaimKit} gates the claim and sends the result message itself; the window closes only when the kit
     * opts into close-on-claim, otherwise it stays open so the player can claim again: the old view's behaviour.
     */
    private void claim(Player player, PlayerRef viewer, KitDefinition kit) {
        scheduler.onEntity(viewer, () -> {
            claimKit.claim(viewer, kit.id());
            if (kit.closeOnClaim()) {
                player.closeInventory();
            }
        });
    }

    /**
     * Open the read-only preview of the clicked kit for the viewer's rank, or deny it when the kit disables preview
     * (a mystery kit). The preview resolves and opens on the viewer's entity thread inside {@link KitPreviewView}.
     */
    private void previewKit(Player player, PlayerRef viewer, KitDefinition kit) {
        if (!kit.preview()) {
            notifier.send(
                    viewer,
                    KitsMessageKey.KIT_MENU_PREVIEW_DENIED,
                    Map.of("kit", kit.id().value()));
            return;
        }
        preview.open(player, viewer, access.resolveVariant(viewer, kit));
    }

    /**
     * The subject of an open browse level: the full kit snapshot, the current category id (empty at the root), and
     * the level's already-resolved mixed tiles. The list source reads {@link #entries()}; drilling and the back
     * action re-derive the next level from {@link #kits()} and {@link #categoryId()}, so no kit scan re-runs on a
     * click and the menu carries no port read once it opens.
     *
     * @param kits the full kit set the open was seeded with, carried so a drill can re-filter it per level
     * @param categoryId the category this level shows the children of, or empty at the root
     * @param entries the level's tiles (sub-categories then kits), each with its icon, name, lore, and pinned slot
     */
    public record BrowseLevel(List<KitDefinition> kits, Optional<String> categoryId, List<BrowseRow> entries) {

        public BrowseLevel {
            kits = List.copyOf(Objects.requireNonNull(kits, "kits"));
            Objects.requireNonNull(categoryId, "categoryId");
            entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        }
    }

    /**
     * One browse tile, fully resolved on the entity thread so the menu never reads a port again: its kind (a
     * drill-in category or a kit), the identity a click acts on (the category id or the kit id), the icon material
     * name, the rendered display name source, the full lore as {@code \n}-joined source lines, and the content slot
     * a configured category pins to.
     *
     * <p>{@link #pinnedSlot()} returns that slot for a pinned category and {@code -1} for a kit or an unpinned
     * category; the engine pins a row only when the slot is one of the list's content slots, so a category whose
     * configured slot falls outside the content grid flows like a kit: reproducing the old view's pin rule.
     *
     * @param kind whether this tile drills into a category or claims/previews a kit
     * @param id the clicked identity, the category id to drill into, or the kit id to claim
     * @param icon the icon material name
     * @param name the rendered display name source in the viewer's locale
     * @param lore the full lore, the catalog/override lines joined by {@code \n} for the engine to expand
     * @param pinnedSlot the content slot a configured category pins to, or {@code -1} to flow with the scroll
     */
    public record BrowseRow(Kind kind, String id, String icon, String name, String lore, int pinnedSlot)
            implements PinnedEntry {

        public BrowseRow {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(icon, "icon");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(lore, "lore");
        }
    }
}

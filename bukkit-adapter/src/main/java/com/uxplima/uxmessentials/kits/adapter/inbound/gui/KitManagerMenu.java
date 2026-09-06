package com.uxplima.uxmessentials.kits.adapter.inbound.gui;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.kits.adapter.outbound.KitItemCodec;
import com.uxplima.uxmessentials.kits.application.KitsMessageKey;
import com.uxplima.uxmessentials.kits.application.port.KitRepository;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Registers the admin kit manager list (opened by {@code /kit editor} with no kit name, or the kits-admin entry on
 * the management hub) with the menu engine and opens it. One icon per stored kit across the top five rows, paged when
 * there are more than fit, with a create, a manage-categories, a close, and the two nav buttons on the bottom row.
 *
 * <p>The grid is the {@code kits:manager} list source. The repository read happens on the viewer's entity thread at
 * open and the rows are handed to the engine as the menu subject, so the engine renders off that snapshot without a
 * repository read of its own, mirroring {@code WorldListMenu}. The {@code kit_manager_icon} / {@code kit_manager_name}
 * placeholders and the fixed catalog-key lore (cooldown, cost, permission, one-time, first-join, auto-equip, a spacer,
 * the edit hint) read the bound row, reproducing the old {@code KitManagerView}'s icon slot for slot.
 *
 * <p>A left click on a kit runs {@code kits:manager-edit}, opening that kit's still-bespoke {@link KitSettingsView}; the
 * create button runs {@code kits:manager-create}, the same name-prompt → empty-kit → settings flow the old create
 * button drove; the categories button runs {@code kits:manager-categories}, opening the still-bespoke
 * {@link KitCategoryManagerMenu}; the close button shuts the window. The list adds no domain logic of its own.
 */
@NullMarked
public final class KitManagerMenu {

    /** The engine spec id this menu registers and opens under. */
    public static final String SPEC_ID = "kit-manager";

    /** Disk-first then bundled, mirroring the GUI-layout loader, so an operator edit to the spec takes effect. */
    private static final String SPEC_RESOURCE = "modules/kits/gui/kit-manager.conf";

    private final Menus menus;
    private final Scheduler scheduler;
    private final KitRepository repository;
    private final Messages messages;
    private final KitSettingsView settingsView;
    private final KitCategoryManagerMenu categoryManagerView;
    private final BiConsumer<Player, PlayerRef> createPrompt;

    /**
     * @param createPrompt opens the name-prompt → empty-kit → settings flow for the viewer; reopening the manager on
     *     cancel is the caller's concern, exactly as the old manager's create button did
     */
    public KitManagerMenu(
            Menus menus,
            Scheduler scheduler,
            KitRepository repository,
            Messages messages,
            KitSettingsView settingsView,
            KitCategoryManagerMenu categoryManagerView,
            BiConsumer<Player, PlayerRef> createPrompt) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.settingsView = Objects.requireNonNull(settingsView, "settingsView");
        this.categoryManagerView = Objects.requireNonNull(categoryManagerView, "categoryManagerView");
        this.createPrompt = Objects.requireNonNull(createPrompt, "createPrompt");
    }

    /** Register the bindings the spec names and the spec itself; called once at kits wiring time. */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.list("kits:manager", ctx -> ctx.subject(KitGrid.class).rows());
        bindings.placeholder("kit_manager_icon", ctx -> material(rowOf(ctx)).name());
        bindings.placeholder("kit_manager_name", ctx -> name(ctx.viewer(), rowOf(ctx)));
        bindings.placeholder(
                "kit_manager_seconds", ctx -> Long.toString(rowOf(ctx).cooldownSeconds()));
        bindings.placeholder("kit_manager_cost", ctx -> cost(ctx.viewer(), rowOf(ctx)));
        bindings.placeholder(
                "kit_manager_permission", ctx -> flag(ctx.viewer(), rowOf(ctx).requiresPermission(), true));
        bindings.placeholder(
                "kit_manager_onetime", ctx -> flag(ctx.viewer(), rowOf(ctx).isOneTime(), false));
        bindings.placeholder(
                "kit_manager_firstjoin", ctx -> flag(ctx.viewer(), rowOf(ctx).firstJoin(), false));
        bindings.placeholder(
                "kit_manager_autoequip", ctx -> flag(ctx.viewer(), rowOf(ctx).autoEquip(), false));
        bindings.action("kits:manager-edit", this::edit);
        bindings.action("kits:manager-create", this::create);
        bindings.action("kits:manager-categories", this::categories);
        menus.registerSpec(SPEC_ID, MenuSpecs.loadOrBundled(SPEC_RESOURCE, dataFolder, 6, log));
    }

    /**
     * Open the kit manager for {@code viewer}. The repository read runs on the viewer's entity thread (where {@code open}
     * is called from), and the snapshot is handed to the engine as the subject so the engine renders without a read of
     * its own.
     */
    public void open(Player player, PlayerRef viewer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        scheduler.onEntity(viewer, () -> menus.open(viewer, SPEC_ID, snapshot()));
    }

    /** The current stored kits, read on the calling region thread, in repository order. */
    private KitGrid snapshot() {
        return new KitGrid(List.copyOf(repository.all()));
    }

    private KitDefinition rowOf(MenuContext ctx) {
        return ctx.entry(KitDefinition.class);
    }

    /** Left-click a kit icon: open that kit's bespoke settings editor on the viewer's entity thread. */
    private void edit(MenuActionContext ctx) {
        settingsView.open(ctx.player(), ctx.viewer(), ctx.entry(KitDefinition.class));
    }

    /** Left-click the create button: run the name-prompt → empty-kit → settings flow the old create button drove. */
    private void create(MenuActionContext ctx) {
        createPrompt.accept(ctx.player(), ctx.viewer());
    }

    /** Left-click the categories button: open the bespoke category manager, as the old slot-51 button did. */
    private void categories(MenuActionContext ctx) {
        categoryManagerView.open(ctx.player(), ctx.viewer());
    }

    /**
     * The kit icon's display name as a MiniMessage source the engine renders for the {@code %kit_manager_name%} name
     * line, reproducing the old view: the kit's own display name when it sets one, otherwise the
     * {@code kit.menu.entry.name} catalog line filled with the kit id. The engine renders this string through the same
     * styled MiniMessage parser the old view used, so the rendered label matches.
     */
    private String name(PlayerRef viewer, KitDefinition kit) {
        if (kit.display().name().isPresent()) {
            return kit.display().name().get();
        }
        return messages.resolve(
                viewer,
                KitsMessageKey.KIT_MENU_ENTRY_NAME,
                Map.of("kit", kit.id().value()));
    }

    /** The kit icon material, reproducing the old view: a valid per-kit override, else the first item's type, else CHEST. */
    private Material material(KitDefinition kit) {
        if (kit.display().material().isPresent()) {
            Material override =
                    Material.matchMaterial(kit.display().material().get().toUpperCase(java.util.Locale.ROOT));
            if (override != null && !override.isAir()) {
                return override;
            }
        }
        if (kit.items().isEmpty()) {
            return Material.CHEST;
        }
        Material type = KitItemCodec.decode(kit.items().get(0)).getType();
        return type.isAir() ? Material.CHEST : type;
    }

    private String cost(PlayerRef viewer, KitDefinition kit) {
        return kit.hasCost()
                ? kit.cost().amount().toPlainString()
                : messages.resolve(viewer, KitsMessageKey.KIT_EDITOR_VALUE_FREE, Map.of());
    }

    private String flag(PlayerRef viewer, boolean value, boolean requiredStyle) {
        MessageKey key;
        if (requiredStyle) {
            key = value ? KitsMessageKey.KIT_EDITOR_VALUE_REQUIRED : KitsMessageKey.KIT_EDITOR_VALUE_NONE;
        } else {
            key = value ? KitsMessageKey.KIT_EDITOR_VALUE_YES : KitsMessageKey.KIT_EDITOR_VALUE_NO;
        }
        return messages.resolve(viewer, key, Map.of());
    }

    /**
     * The subject of an open kit manager: the stored kits, snapshotted on the region thread before the open so the engine
     * renders without a repository read. The list source and the placeholders read this, so the menu carries no kit scan
     * of its own.
     *
     * @param rows the stored kits in repository order
     */
    public record KitGrid(List<KitDefinition> rows) {

        public KitGrid {
            rows = List.copyOf(Objects.requireNonNull(rows, "rows"));
        }
    }
}

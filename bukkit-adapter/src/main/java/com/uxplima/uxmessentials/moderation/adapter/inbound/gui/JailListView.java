package com.uxplima.uxmessentials.moderation.adapter.inbound.gui;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.moderation.adapter.ModerationServices;
import com.uxplima.uxmessentials.moderation.application.ModerationMessageKey;
import com.uxplima.uxmessentials.moderation.application.port.JailLocator;
import com.uxplima.uxmessentials.moderation.application.port.Sanctions;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.Tiles;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * The jail-management list (capability B of the {@code /jail} GUI): a config-driven, paginated grid of every
 * defined jail name (the config jails merged with the DB-backed {@code /setjail} jails) drawn through the shared
 * {@link EntityListView}, plus a "create jail" button. Clicking a jail opens the engine-rendered per-jail edit
 * screen offering re-anchor (save the jail at the staff member's current location), teleport, and delete; the
 * create button prompts for a name through the shared text-input seam and saves a new jail at the staff member's
 * current location.
 *
 * <p>The name union is a DB read, so the open resolves it off the tick thread and hops back to the viewer's
 * entity thread to render. Opening the edit screen resolves the jail's coordinates off-thread and hands them in
 * as the edit subject, so the engine render touches no port. Re-anchoring and creating both read the viewer's own
 * location <em>on the viewer's thread</em> (a region-local read) before delegating to the audited {@code SetJail}
 * use case; delete delegates to {@code DelJail}. The view holds no domain logic. It threads the existing use
 * cases the {@code /setjail} and {@code /jail del} commands take.
 */
@NullMarked
public final class JailListView {

    /** The engine spec id the per-jail edit screen registers and opens under. */
    public static final String EDIT_SPEC_ID = "moderation-jail-edit";

    private static final String EDIT_SPEC_RESOURCE = "modules/moderation/gui/moderation-jail-edit.conf";
    private static final int EDIT_ROWS = 3;
    private static final Material JAIL_ICON = Material.IRON_BARS;
    private static final String CREATE_KEY = "moderation.jail-create";

    private final Menus menus;
    private final GuiText guiText;
    private final Messages messages;
    private final Scheduler scheduler;
    private final ModerationServices services;
    private final Sanctions sanctions;
    private final JailLocator jailLocator;
    private final TextInput textInput;
    private final AtomicReference<List<String>> snapshot = new AtomicReference<>(List.of());
    private final EntityListView<String> view;

    public JailListView(
            Menus menus,
            GuiText guiText,
            Messages messages,
            Scheduler scheduler,
            ModerationServices services,
            Sanctions sanctions,
            JailLocator jailLocator,
            TextInput textInput,
            EntityListLayout layout) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.services = Objects.requireNonNull(services, "services");
        this.sanctions = Objects.requireNonNull(sanctions, "sanctions");
        this.jailLocator = Objects.requireNonNull(jailLocator, "jailLocator");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
        Objects.requireNonNull(layout, "layout");
        this.view = EntityListView.<String>builder()
                .menus(menus)
                .guiText(guiText)
                .scheduler(scheduler)
                .layout(layout)
                .title(ModerationMessageKey.MOD_GUI_JAIL_LIST_TITLE)
                .navNames(ModerationMessageKey.MOD_GUI_JAIL_LIST_PREV, ModerationMessageKey.MOD_GUI_JAIL_LIST_NEXT)
                .entities(snapshot::get)
                .iconRenderer(this::icon)
                .onSelect((player, name) -> openEdit(BukkitRefs.toRef(player), name))
                .onCreate(ModerationMessageKey.MOD_GUI_JAIL_LIST_CREATE, this::promptCreate)
                .build();
    }

    /**
     * Register the per-jail edit screen's subject placeholders and the re-anchor / teleport / delete / back
     * actions the spec names, and the spec itself. The jail list itself draws through the shared
     * {@link EntityListView} and needs no spec registration.
     */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.placeholder("mod_jail_edit_jail", ctx -> subject(ctx).name());
        bindings.placeholder("mod_jail_edit_coords", ctx -> subject(ctx).coords());
        bindings.action("moderation:jail-reanchor", this::reAnchor);
        bindings.action("moderation:jail-goto", this::goTo);
        bindings.action("moderation:jail-delete", this::delete);
        bindings.action("moderation:jail-back", ctx -> open(ctx.player(), ctx.viewer()));
        menus.registerSpec(EDIT_SPEC_ID, MenuSpecs.loadOrBundled(EDIT_SPEC_RESOURCE, dataFolder, EDIT_ROWS, log));
    }

    /** Resolve the jail-name union off-thread, then open the list on the viewer's entity thread. */
    public void open(Player player, PlayerRef viewer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        scheduler.async(() -> {
            snapshot.set(services.listJails().names());
            scheduler.onEntity(viewer, () -> view.open(player, viewer));
        });
    }

    private ItemStack icon(PlayerRef viewer, String name) {
        Map<String, String> placeholders = placeholders(viewer, name);
        return ItemBuilder.of(JAIL_ICON)
                .name(Tiles.blankName())
                .lore(Tiles.titled(
                        guiText.text(viewer, ModerationMessageKey.MOD_GUI_JAIL_LIST_ENTRY_NAME, placeholders),
                        guiText.text(viewer, ModerationMessageKey.MOD_GUI_JAIL_LIST_ENTRY_LORE, placeholders)))
                .build();
    }

    /**
     * The placeholder map every jail list item shares: the jail name and its location as a single
     * {@code world x, y, z} string. The coordinates are resolved store-first then config (the same precedence a
     * jailed player is sent to); a jail whose world cannot be resolved shows the localised "unknown" word so the
     * lore line is never blank.
     */
    private Map<String, String> placeholders(PlayerRef viewer, String name) {
        return Map.of("jail", name, "coords", coords(viewer, name));
    }

    private String coords(PlayerRef viewer, String name) {
        return jailLocator
                .locate(name)
                .map(at -> at.world() + " " + at.x() + ", " + at.y() + ", " + at.z())
                .orElseGet(
                        () -> messages.resolve(viewer, ModerationMessageKey.MOD_GUI_JAIL_LOCATION_UNKNOWN, Map.of()));
    }

    /** Open the engine edit screen: resolve the jail's coordinates off-thread, then hand them in as the subject. */
    private void openEdit(PlayerRef viewer, String name) {
        scheduler.async(() -> {
            String coords = coords(viewer, name);
            menus.open(viewer, EDIT_SPEC_ID, new JailEdit(name, coords));
        });
    }

    /** Re-anchor the jail at the viewer's current location, read here on the viewer's own region thread. */
    private void reAnchor(MenuActionContext ctx) {
        Player player = ctx.player();
        PlayerRef viewer = ctx.viewer();
        String name = ctx.subject(JailEdit.class).name();
        scheduler.onEntity(viewer, () -> {
            services.setJail().set(viewer, name, position(player));
            open(player, viewer);
        });
    }

    /**
     * Teleport the viewer to the jail, the inverse of re-anchor, a navigation action. Closes the screen first so
     * a rapid double-click cannot fire a second hop, then reuses {@link Sanctions#sendToJail} (which resolves the
     * jail store-first, falls back to config, and hops to the viewer's region thread before {@code teleportAsync}),
     * so the operator arrives exactly where a jailed player would. No confirm and no re-open: the viewer is
     * leaving for the jail.
     */
    private void goTo(MenuActionContext ctx) {
        ctx.player().closeInventory();
        sanctions.sendToJail(ctx.viewer(), ctx.subject(JailEdit.class).name());
    }

    private void delete(MenuActionContext ctx) {
        Player player = ctx.player();
        PlayerRef viewer = ctx.viewer();
        services.delJail().delete(viewer, ctx.subject(JailEdit.class).name());
        open(player, viewer);
    }

    /** Prompt for a new jail name; a submission saves it at the viewer's current location, a cancel reopens the list. */
    private void promptCreate(Player player) {
        PlayerRef viewer = BukkitRefs.toRef(player);
        InputRequest request = InputRequest.of(CREATE_KEY, ModerationMessageKey.MOD_GUI_JAIL_CREATE_PROMPT);
        textInput.prompt(
                player, viewer, request, text -> createOrReopen(player, viewer, text), () -> open(player, viewer));
    }

    /** A blank name reopens the list; otherwise the trimmed name creates a jail at the viewer's location. */
    private void createOrReopen(Player player, PlayerRef viewer, String text) {
        if (text.isBlank()) {
            open(player, viewer);
        } else {
            createJail(player, viewer, text.strip());
        }
    }

    /**
     * Save a new jail at the viewer's current location, read here on the viewer's own region thread.
     * Package-private so the create path is unit-tested without driving a live prompt submission.
     */
    void createJail(Player player, PlayerRef viewer, String name) {
        scheduler.onEntity(viewer, () -> {
            services.setJail().set(viewer, name, position(player));
            open(player, viewer);
        });
    }

    private JailEdit subject(MenuContext ctx) {
        return ctx.subject(JailEdit.class);
    }

    private static Position position(Player player) {
        return BukkitRefs.toPosition(Objects.requireNonNull(player.getLocation(), "player location"));
    }

    /**
     * The subject of an open per-jail edit screen: the jail name and its resolved display coordinates. The
     * re-anchor / teleport / delete lore read these directly, so the render touches no port; the coordinates are
     * resolved off the tick thread at open time, exactly as the list entries resolve them.
     *
     * @param name the jail being edited
     * @param coords the jail's location as a single {@code world x, y, z} string, or the localised "unknown" word
     */
    public record JailEdit(String name, String coords) {

        public JailEdit {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(coords, "coords");
        }
    }
}

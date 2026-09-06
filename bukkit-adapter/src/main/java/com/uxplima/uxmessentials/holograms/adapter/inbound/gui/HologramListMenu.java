package com.uxplima.uxmessentials.holograms.adapter.inbound.gui;

import java.nio.file.Path;
import java.util.Objects;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.holograms.adapter.HologramServices;
import com.uxplima.uxmessentials.holograms.application.HologramsMessageKey;
import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramLine;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.NullMarked;

/**
 * Registers the {@code /hologram} management list (opened by {@code /hologram} with no arguments, or the holograms
 * entry on the {@code /uxmess gui} hub) with the menu engine and opens it. A small class that registers the
 * per-hologram list source, the placeholders its entries need, the edit and create click actions, then loads the
 * {@code hologram-list} spec and hands it to {@link Menus}.
 *
 * <p>The grid is the {@code holograms:list} source. It reads the cached hologram registry directly (the warm
 * in-memory set, never the database, never the Bukkit API), so the engine may resolve it off the viewer's region
 * thread. Each hologram carries no item of its own, so the icon is a fixed {@code ARMOR_STAND} literal in the spec;
 * the {@code hologram_name} / {@code hologram_lines} / {@code hologram_world} / {@code hologram_x} / {@code
 * hologram_y} / {@code hologram_z} placeholders fill that hologram's name, line count, world, and rounded
 * coordinates into the catalog entry. A left click runs {@code holograms:edit}, which opens the still-bespoke
 * {@link HologramEditorView} for the clicked hologram; the create button runs {@code holograms:create}, which
 * prompts for a name and creates a fresh text hologram at the operator's feet through the same {@link
 * HologramServices#create()} use case {@code /hologram create} drives, so the list adds no domain logic of its own.
 */
@NullMarked
public final class HologramListMenu {

    /** The engine spec id this menu registers and opens under. */
    public static final String SPEC_ID = "hologram-list";

    /** Disk-first then bundled, mirroring the GUI-layout loader, so an operator edit to the spec takes effect. */
    private static final String SPEC_RESOURCE = "modules/holograms/gui/hologram-list.conf";

    private final Menus menus;
    private final Scheduler scheduler;
    private final HologramRepository repository;
    private final HologramServices services;
    private final TextInput textInput;
    private final HologramEditorView editor;

    public HologramListMenu(
            Menus menus,
            Scheduler scheduler,
            HologramRepository repository,
            HologramServices services,
            TextInput textInput,
            HologramEditorView editor) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.services = Objects.requireNonNull(services, "services");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
        this.editor = Objects.requireNonNull(editor, "editor");
    }

    /** Register the bindings the spec names and the spec itself; called once at holograms wiring time. */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.list("holograms:list", ctx -> repository.all());
        bindings.placeholder("hologram_name", ctx -> holoOf(ctx).name().value());
        bindings.placeholder(
                "hologram_lines", ctx -> Integer.toString(holoOf(ctx).lineCount()));
        bindings.placeholder(
                "hologram_world", ctx -> holoOf(ctx).location().world().name());
        bindings.placeholder(
                "hologram_x",
                ctx -> Long.toString(Math.round(holoOf(ctx).location().x())));
        bindings.placeholder(
                "hologram_y",
                ctx -> Long.toString(Math.round(holoOf(ctx).location().y())));
        bindings.placeholder(
                "hologram_z",
                ctx -> Long.toString(Math.round(holoOf(ctx).location().z())));
        bindings.action("holograms:edit", this::edit);
        bindings.action("holograms:create", this::create);
        menus.registerSpec(SPEC_ID, MenuSpecs.loadOrBundled(SPEC_RESOURCE, dataFolder, 6, log));
    }

    /** Open the hologram list for {@code viewer} (the live player resolved by the engine). */
    public void open(PlayerRef viewer) {
        Objects.requireNonNull(viewer, "viewer");
        menus.open(viewer, SPEC_ID, null);
    }

    /** The bound list element for the slot being rendered or clicked. */
    private Hologram holoOf(MenuContext ctx) {
        return ctx.entry(Hologram.class);
    }

    /** Left-click a hologram icon: open that hologram's bespoke property editor on the viewer's entity thread. */
    private void edit(MenuActionContext ctx) {
        editor.open(ctx.player(), ctx.viewer(), ctx.entry(Hologram.class));
    }

    /**
     * Left-click the create button: prompt for a name, then create a fresh text hologram at the operator's feet,
     * reopening the list on cancel: exactly what the old create button did.
     */
    private void create(MenuActionContext ctx) {
        Player player = ctx.player();
        PlayerRef viewer = ctx.viewer();
        textInput.prompt(
                player,
                viewer,
                InputRequest.of("hologram.create-name", HologramsMessageKey.HOLOGRAM_GUI_LIST_CREATE_PROMPT),
                text -> handleCreate(player, viewer, text),
                () -> open(viewer));
    }

    /**
     * Create the named text hologram at the operator's feet off the click thread, then reopen the list. A blank name
     * simply reopens the list. A new text hologram needs at least one non-blank line; the name reads as a sensible
     * placeholder the operator immediately edits in the line list.
     */
    private void handleCreate(Player player, PlayerRef viewer, String text) {
        if (text.isBlank()) {
            open(viewer);
            return;
        }
        Position at = BukkitRefs.toPosition(Objects.requireNonNull(player.getLocation(), "location"));
        HologramName name = HologramName.of(text);
        scheduler.async(() -> {
            services.create().create(viewer, name, at, HologramLine.of(name.value()));
            scheduler.onEntity(viewer, () -> open(viewer));
        });
    }
}

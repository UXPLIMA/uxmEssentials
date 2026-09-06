package com.uxplima.uxmessentials.npc.adapter.inbound.gui;

import java.nio.file.Path;
import java.util.Objects;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.npc.adapter.NpcServices;
import com.uxplima.uxmessentials.npc.adapter.outbound.BukkitNpcSkins;
import com.uxplima.uxmessentials.npc.application.NpcMessageKey;
import com.uxplima.uxmessentials.npc.application.port.NpcRepository;
import com.uxplima.uxmessentials.npc.domain.Npc;
import com.uxplima.uxmessentials.npc.domain.NpcName;
import com.uxplima.uxmessentials.npc.domain.NpcSkin;
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
 * Registers the {@code /npc} management list (opened by {@code /npc} with no arguments, or the npc entry on the
 * {@code /uxmess gui} hub) with the menu engine and opens it. A small class that registers the per-NPC list source,
 * the placeholders its entries need, the edit and create click actions, then loads the {@code npc-list} spec and
 * hands it to {@link Menus}.
 *
 * <p>The grid is the {@code npc:list} source. It reads the cached NPC registry directly (the warm in-memory set
 * behind the Caffeine cache, never the database, never the Bukkit API), so the engine may resolve it off the
 * viewer's region thread. Each NPC's icon is its type icon (a player head for the default fake player, that mob's
 * spawn egg otherwise) through the {@code npc_icon} placeholder; the {@code npc_name} / {@code npc_type} /
 * {@code npc_world} / {@code npc_x} / {@code npc_y} / {@code npc_z} placeholders fill that NPC's name, type, world,
 * and rounded coordinates into the catalog entry. A left click runs {@code npc:edit}, which opens the still-bespoke
 * {@link NpcEditorView} for the clicked NPC; the create button runs {@code npc:create}, which prompts for a name and
 * creates a fresh fake-player NPC at the operator's feet with their own skin through the same {@link
 * NpcServices#create()} use case {@code /npc create} drives, so the list adds no domain logic of its own.
 */
@NullMarked
public final class NpcListMenu {

    /** The engine spec id this menu registers and opens under. */
    public static final String SPEC_ID = "npc-list";

    /** Disk-first then bundled, mirroring the GUI-layout loader, so an operator edit to the spec takes effect. */
    private static final String SPEC_RESOURCE = "modules/npc/gui/npc-list.conf";

    private final Menus menus;
    private final Scheduler scheduler;
    private final NpcRepository repository;
    private final NpcServices services;
    private final TextInput textInput;
    private final NpcEditorView editor;

    public NpcListMenu(
            Menus menus,
            Scheduler scheduler,
            NpcRepository repository,
            NpcServices services,
            TextInput textInput,
            NpcEditorView editor) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.services = Objects.requireNonNull(services, "services");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
        this.editor = Objects.requireNonNull(editor, "editor");
    }

    /** Register the bindings the spec names and the spec itself; called once at npc wiring time. */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.list("npc:list", ctx -> repository.all());
        bindings.placeholder(
                "npc_icon", ctx -> NpcTypeIcon.iconFor(npcOf(ctx).entityType()).name());
        bindings.placeholder("npc_name", ctx -> npcOf(ctx).name().value());
        bindings.placeholder("npc_type", ctx -> npcOf(ctx).entityType());
        bindings.placeholder("npc_world", ctx -> npcOf(ctx).location().world().name());
        bindings.placeholder(
                "npc_x", ctx -> Long.toString(Math.round(npcOf(ctx).location().x())));
        bindings.placeholder(
                "npc_y", ctx -> Long.toString(Math.round(npcOf(ctx).location().y())));
        bindings.placeholder(
                "npc_z", ctx -> Long.toString(Math.round(npcOf(ctx).location().z())));
        bindings.action("npc:edit", this::edit);
        bindings.action("npc:create", this::create);
        menus.registerSpec(SPEC_ID, MenuSpecs.loadOrBundled(SPEC_RESOURCE, dataFolder, 6, log));
    }

    /** Open the NPC list for {@code viewer} (the live player resolved by the engine). */
    public void open(Player player, PlayerRef viewer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        menus.open(viewer, SPEC_ID, null);
    }

    /** The bound list element for the slot being rendered or clicked. */
    private Npc npcOf(MenuContext ctx) {
        return ctx.entry(Npc.class);
    }

    /** Left-click an NPC icon: open that NPC's bespoke property editor on the viewer's entity thread. */
    private void edit(MenuActionContext ctx) {
        editor.open(ctx.player(), ctx.viewer(), ctx.entry(Npc.class));
    }

    /**
     * Left-click the create button: prompt for a name, then create a fresh fake-player NPC at the operator's feet,
     * reopening the list on cancel: exactly what the old create button did.
     */
    private void create(MenuActionContext ctx) {
        Player player = ctx.player();
        PlayerRef viewer = ctx.viewer();
        textInput.prompt(
                player,
                viewer,
                InputRequest.of("npc.create-name", NpcMessageKey.NPC_GUI_LIST_CREATE_PROMPT),
                text -> handleCreate(player, viewer, text),
                () -> open(player, viewer));
    }

    /**
     * Create the named fake-player NPC at the operator's feet off the click thread, then reopen the list. A blank
     * name simply reopens the list. The NPC is created at the operator's feet with their own skin, exactly
     * {@code /npc create}.
     */
    private void handleCreate(Player player, PlayerRef viewer, String text) {
        if (text.isBlank()) {
            open(player, viewer);
            return;
        }
        Position at = BukkitRefs.toPosition(Objects.requireNonNull(player.getLocation(), "location"));
        NpcName name = NpcName.of(text);
        NpcSkin skin = BukkitNpcSkins.of(player).orElse(null);
        scheduler.async(() -> {
            services.create().create(viewer, name, at, skin);
            scheduler.onEntity(viewer, () -> open(player, viewer));
        });
    }
}

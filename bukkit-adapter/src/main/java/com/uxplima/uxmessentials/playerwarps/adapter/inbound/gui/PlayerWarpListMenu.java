package com.uxplima.uxmessentials.playerwarps.adapter.inbound.gui;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.playerwarps.application.PlayerwarpsMessageKey;
import com.uxplima.uxmessentials.playerwarps.application.SetPlayerWarp;
import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.domain.IconSpec;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.WarpAccess;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.NullMarked;

/**
 * Registers the {@code /pwarp} management list (opened by {@code /pwarp} with no arguments, or the player-warps
 * entry on the {@code /uxmess gui} hub) with the menu engine and opens it. A small class that registers the
 * per-warp list source, the placeholders its entries need, the edit and create click actions, then loads the
 * {@code playerwarp-list} spec and hands it to {@link Menus}.
 *
 * <p>The scope of the list depends on who opens it: a plain player sees only their own warps
 * ({@code repository.ownedBy}), an operator holding {@code uxmessentials.pwarp.gui} sees every player's
 * ({@code repository.all}). That decision reads the live {@link Permissions} port, which on Folia may only be
 * queried on the viewer's region thread, so {@link #open} resolves it there and hands the result to the engine
 * as the menu subject (the {@code EntityCountMenu} pre-compute pattern). The {@code playerwarps:list} source then
 * only reads that subject's flag and the database, touching no Bukkit API off-thread.
 *
 * <p>Each entry's icon is the warp's configured material (or {@code ENDER_PEARL} when unset or unknown) through
 * the {@code pwarp_icon} placeholder; the {@code pwarp_name} / {@code pwarp_owner} / {@code pwarp_world} /
 * {@code pwarp_x} / {@code pwarp_y} / {@code pwarp_z} / {@code pwarp_visibility} placeholders fill that warp's
 * name, owner, world, rounded coordinates, and visibility word into the catalog entry. A left click runs
 * {@code playerwarps:edit}, which opens the still-bespoke {@link PlayerWarpEditorView} for the clicked warp; the
 * create button runs {@code playerwarps:create}, which prompts for a name and creates a private warp at the
 * operator's feet through the same {@link SetPlayerWarp} use case {@code /setpwarp} drives, so the list adds no
 * domain logic of its own.
 */
@NullMarked
public final class PlayerWarpListMenu {

    /** The node an operator holds to manage every player's warps (and to open this list as an admin). */
    public static final String MANAGE_PERMISSION = "uxmessentials.pwarp.gui";

    /** The engine spec id this menu registers and opens under. */
    public static final String SPEC_ID = "playerwarp-list";

    /** Disk-first then bundled, mirroring the GUI-layout loader, so an operator edit to the spec takes effect. */
    private static final String SPEC_RESOURCE = "modules/playerwarps/gui/playerwarp-list.conf";

    /** The icon a warp with no configured (or an unparseable) material falls back to, matching the old list. */
    private static final Material FALLBACK_ICON = Material.ENDER_PEARL;

    private final Menus menus;
    private final Scheduler scheduler;
    private final Permissions permissions;
    private final Messages messages;
    private final PlayerWarpRepository repository;
    private final SetPlayerWarp setPlayerWarp;
    private final TextInput textInput;
    private final PlayerWarpEditorView editor;

    public PlayerWarpListMenu(
            Menus menus,
            Scheduler scheduler,
            Permissions permissions,
            Messages messages,
            PlayerWarpRepository repository,
            SetPlayerWarp setPlayerWarp,
            TextInput textInput,
            PlayerWarpEditorView editor) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.setPlayerWarp = Objects.requireNonNull(setPlayerWarp, "setPlayerWarp");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
        this.editor = Objects.requireNonNull(editor, "editor");
    }

    /** Register the bindings the spec names and the spec itself; called once at player-warps wiring time. */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.list("playerwarps:list", this::warps);
        bindings.placeholder("pwarp_icon", ctx -> iconMaterial(warpOf(ctx)).name());
        bindings.placeholder("pwarp_name", ctx -> warpOf(ctx).name().value());
        bindings.placeholder("pwarp_owner", ctx -> ownedOf(ctx).owner().name());
        bindings.placeholder(
                "pwarp_world", ctx -> warpOf(ctx).location().world().name());
        bindings.placeholder(
                "pwarp_x",
                ctx -> Long.toString(Math.round(warpOf(ctx).location().x())));
        bindings.placeholder(
                "pwarp_y",
                ctx -> Long.toString(Math.round(warpOf(ctx).location().y())));
        bindings.placeholder(
                "pwarp_z",
                ctx -> Long.toString(Math.round(warpOf(ctx).location().z())));
        bindings.placeholder("pwarp_visibility", this::visibilityWord);
        bindings.action("playerwarps:edit", this::edit);
        bindings.action("playerwarps:create", this::create);
        menus.registerSpec(SPEC_ID, MenuSpecs.loadOrBundled(SPEC_RESOURCE, dataFolder, 6, log));
    }

    /**
     * Open the warp list for {@code viewer} on their entity thread, scoped to what they may manage. The manage-node
     * check reads the Bukkit permission system, so it runs here on the region thread and is carried into the engine
     * as the subject; the off-thread list source only reads that flag and the database.
     */
    public void open(Player player, PlayerRef viewer) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        boolean managesAll = permissions.has(viewer, MANAGE_PERMISSION);
        menus.open(viewer, SPEC_ID, new PlayerWarpScope(managesAll));
    }

    /** Every warp the viewer may manage, read off the click thread: all owners' when they manage, else their own. */
    private List<OwnedWarp> warps(MenuContext ctx) {
        boolean managesAll = ctx.subject(PlayerWarpScope.class).managesAll();
        List<PlayerWarp> warps = managesAll ? repository.all() : repository.ownedBy(ctx.viewer());
        return warps.stream().map(warp -> new OwnedWarp(warp.owner(), warp)).toList();
    }

    /** The bound list element for the slot being rendered or clicked. */
    private OwnedWarp ownedOf(MenuContext ctx) {
        return ctx.entry(OwnedWarp.class);
    }

    private PlayerWarp warpOf(MenuContext ctx) {
        return ownedOf(ctx).warp();
    }

    /** The entry's icon: the warp's configured material, falling back to {@link #FALLBACK_ICON} as the old list did. */
    private Material iconMaterial(PlayerWarp warp) {
        return warp.icon().map(IconSpec::value).map(this::matchOrFallback).orElse(FALLBACK_ICON);
    }

    private Material matchOrFallback(String raw) {
        Material matched = Material.matchMaterial(raw);
        return matched != null ? matched : FALLBACK_ICON;
    }

    /** The localised public/private word for the bound entry, resolved for the viewer's locale. */
    private String visibilityWord(MenuContext ctx) {
        boolean isPublic = warpOf(ctx).access() == WarpAccess.PUBLIC;
        return messages.resolve(
                ctx.viewer(),
                isPublic ? PlayerwarpsMessageKey.PWARP_GUI_VALUE_PUBLIC : PlayerwarpsMessageKey.PWARP_GUI_VALUE_PRIVATE,
                Map.of());
    }

    /** Left-click a warp icon: open that warp's bespoke property editor on the viewer's entity thread. */
    private void edit(MenuActionContext ctx) {
        editor.open(ctx.player(), ctx.viewer(), ctx.entry(OwnedWarp.class));
    }

    /**
     * Left-click the create button: prompt for a name, then create a private warp at the operator's feet, reopening
     * the list on cancel: exactly what the old create button did.
     */
    private void create(MenuActionContext ctx) {
        Player player = ctx.player();
        PlayerRef viewer = ctx.viewer();
        textInput.prompt(
                player,
                viewer,
                InputRequest.of("playerwarp.create-name", PlayerwarpsMessageKey.PWARP_GUI_LIST_CREATE_PROMPT),
                text -> handleCreate(player, viewer, text),
                () -> open(player, viewer));
    }

    /**
     * Create the named warp at the operator's feet off the click thread, then reopen the list. A blank name simply
     * reopens the list. The warp belongs to the operator who opened it, at their feet, exactly {@code /setpwarp}.
     */
    private void handleCreate(Player player, PlayerRef viewer, String text) {
        if (text.isBlank()) {
            open(player, viewer);
            return;
        }
        Position at = BukkitRefs.toPosition(Objects.requireNonNull(player.getLocation(), "location"));
        PlayerWarpName name = PlayerWarpName.of(text);
        scheduler.async(() -> {
            setPlayerWarp.set(viewer, viewer.name(), name, at);
            scheduler.onEntity(viewer, () -> open(player, viewer));
        });
    }

    /**
     * The subject of an open warp list: whether the viewer manages every owner's warps. Resolved on the region
     * thread at the open site (the permission read may not run off-thread), so the list source branches on it
     * without touching the Bukkit API.
     *
     * @param managesAll {@code true} when the viewer holds {@link #MANAGE_PERMISSION} and so sees every player's warps
     */
    public record PlayerWarpScope(boolean managesAll) {}
}

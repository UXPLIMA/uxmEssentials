package com.uxplima.uxmessentials.playerwarps.adapter.inbound.gui;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;

import org.bukkit.Material;

import com.uxplima.uxmessentials.playerwarps.application.EditPlayerWarp;
import com.uxplima.uxmessentials.playerwarps.domain.IconSpec;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Registers and opens the warp-icon picker ({@code pwarp-icon}) the manage panel's icon button opens, the
 * player-warps twin of the home-icon picker. The subject warp is carried as the engine subject; a fixed material
 * palette is offered as a paged list, and a left click sets that material as the warp's browse icon through
 * {@link EditPlayerWarp#setIcon} before returning to the manage panel. The reset button clears the custom icon; the
 * back button returns without changing it. The palette is a static in-memory list, so its source touches no Bukkit API
 * and the engine may resolve it off the viewer's region thread; the set/clear writes hit the database, so they run
 * through the kernel {@link Scheduler} before the manage panel re-opens with the re-read warp.
 *
 * <p>The manage icon button is capability-gated on {@code EDIT_METADATA}, so only a role that may edit the warp's
 * metadata can reach this picker; the {@link EditPlayerWarp} write re-checks that authority itself, so the picker is
 * presentation only, exactly like every other manage-panel edit.
 */
@NullMarked
public final class PlayerWarpIconMenu {

    /** The engine spec id this menu registers and opens under. */
    public static final String SPEC_ID = "pwarp-icon";

    /** The palette list-source id the spec's grid binds and this menu registers. */
    public static final String PALETTE_SOURCE = "playerwarps:icon-palette";

    /** Disk-first then bundled, mirroring the sibling player-warps menus, so an operator edit to the spec takes effect. */
    private static final String SPEC_RESOURCE = "modules/playerwarps/gui/pwarp-icon.conf";

    /**
     * The materials offered as warp icons: a fixed palette that fits one content page. A player who wants a material
     * outside it can still type any material through {@code /pwarp icon <material>}; the picker covers the common set.
     */
    private static final List<Material> PALETTE = List.of(
            Material.GRASS_BLOCK,
            Material.STONE,
            Material.DIRT_PATH,
            Material.OAK_LOG,
            Material.OAK_PLANKS,
            Material.COBBLESTONE,
            Material.BRICKS,
            Material.BOOKSHELF,
            Material.CRAFTING_TABLE,
            Material.FURNACE,
            Material.CHEST,
            Material.ENDER_CHEST,
            Material.BARREL,
            Material.BEACON,
            Material.LODESTONE,
            Material.RESPAWN_ANCHOR,
            Material.ENCHANTING_TABLE,
            Material.ANVIL,
            Material.CAMPFIRE,
            Material.TORCH,
            Material.LANTERN,
            Material.SOUL_LANTERN,
            Material.GLOWSTONE,
            Material.SEA_LANTERN,
            Material.DIAMOND_BLOCK,
            Material.GOLD_BLOCK,
            Material.IRON_BLOCK,
            Material.EMERALD_BLOCK,
            Material.NETHERITE_BLOCK,
            Material.LAPIS_BLOCK,
            Material.REDSTONE_BLOCK,
            Material.COMPASS,
            Material.CLOCK,
            Material.MAP,
            Material.ENDER_PEARL,
            Material.NETHER_STAR,
            Material.ELYTRA,
            Material.DIAMOND_SWORD,
            Material.BOW,
            Material.SHIELD,
            Material.OAK_SAPLING,
            Material.CAKE);

    private final Menus menus;
    private final Scheduler scheduler;
    private final EditPlayerWarp editPlayerWarp;
    private final BiConsumer<PlayerRef, PlayerWarpName> manageOpener;

    public PlayerWarpIconMenu(
            Menus menus,
            Scheduler scheduler,
            EditPlayerWarp editPlayerWarp,
            BiConsumer<PlayerRef, PlayerWarpName> manageOpener) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.editPlayerWarp = Objects.requireNonNull(editPlayerWarp, "editPlayerWarp");
        this.manageOpener = Objects.requireNonNull(manageOpener, "manageOpener");
    }

    /** Register the palette source, the cell placeholder, the set/reset/back actions, and the spec. */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.list(PALETTE_SOURCE, ctx -> PALETTE);
        bindings.placeholder(
                "pwarp_icon_material", ctx -> ctx.entry(Material.class).name());
        bindings.action("playerwarps:icon-set", this::setIcon);
        bindings.action("playerwarps:icon-reset", this::resetIcon);
        bindings.action("playerwarps:icon-back", this::back);
        menus.registerSpec(SPEC_ID, MenuSpecs.loadOrBundled(SPEC_RESOURCE, dataFolder, 6, log));
    }

    /** Open the icon picker for the warp named {@code name}, on {@code viewer}'s entity thread. */
    public void open(PlayerRef viewer, PlayerWarpName name) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(name, "name");
        scheduler.onEntity(viewer, () -> menus.open(viewer, SPEC_ID, name));
    }

    /** Left-click a palette material: set the warp's icon to it off the tick thread, then reopen the manage panel. */
    private void setIcon(MenuActionContext ctx) {
        Material material = ctx.entry(Material.class);
        apply(ctx, Optional.of(IconSpec.of(material.name())));
    }

    /** Left-click the reset button: clear the warp's custom icon off the tick thread, then reopen the manage panel. */
    private void resetIcon(MenuActionContext ctx) {
        apply(ctx, Optional.empty());
    }

    private void apply(MenuActionContext ctx, Optional<IconSpec> icon) {
        PlayerRef viewer = ctx.viewer();
        PlayerWarpName name = ctx.subject(PlayerWarpName.class);
        scheduler.async(() -> {
            editPlayerWarp.setIcon(viewer, name, icon);
            manageOpener.accept(viewer, name);
        });
    }

    /** Left-click the back button: return to the manage panel without changing the icon. */
    private void back(MenuActionContext ctx) {
        manageOpener.accept(ctx.viewer(), ctx.subject(PlayerWarpName.class));
    }
}

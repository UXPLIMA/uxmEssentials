package com.uxplima.uxmessentials.homes.adapter.inbound.gui;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.homes.application.SetHomeIcon;
import com.uxplima.uxmessentials.homes.domain.Home;
import com.uxplima.uxmessentials.homes.domain.HomeIcon;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Registers the homes context's menus with the menu engine and opens them. A single coordinator that holds the
 * engine façade, the home use cases each menu drives, and the specs they render through, the shape the staff and
 * vault menus follow, generalised to the homes menu chain (the icon picker and the invited-players list both return
 * to a home's action menu).
 *
 * <p>Each menu carries the {@link Home} it is about as its engine subject. A click that opens another menu in the
 * chain re-opens it through {@link Menus} with the same home subject, so the bespoke {@code Runnable reopen}
 * threading the old views passed around disappears. The icon palette is a fixed configured list, so its source
 * touches no Bukkit API and the engine may resolve it off the viewer's region thread; the set/reset writes hit the
 * database, so they run through the kernel {@link Scheduler} before the action menu re-opens.
 */
@NullMarked
public final class HomeMenus {

    /** The engine spec ids the home menus register and open under. */
    public static final String ICON_SPEC_ID = "home-icon";

    private static final String ICON_RESOURCE = "modules/homes/gui/home-icon.conf";

    /**
     * Re-opens the home action menu ({@link HomeActionMenu}) for a clicked home, so the icon picker and the
     * invited-players list return to it through one shared seam without holding a reference to it directly (the menus
     * reference each other, so the seam is bound after construction).
     */
    @FunctionalInterface
    public interface ActionMenuOpener {
        void open(Player player, PlayerRef viewer, Home home);
    }

    private final Menus menus;
    private final Scheduler scheduler;
    private final SetHomeIcon setHomeIcon;
    private final IconSelectorLayout iconLayout;
    private final ActionMenuOpener actionMenu;

    public HomeMenus(
            Menus menus,
            Scheduler scheduler,
            SetHomeIcon setHomeIcon,
            IconSelectorLayout iconLayout,
            ActionMenuOpener actionMenu) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.setHomeIcon = Objects.requireNonNull(setHomeIcon, "setHomeIcon");
        this.iconLayout = Objects.requireNonNull(iconLayout, "iconLayout");
        this.actionMenu = Objects.requireNonNull(actionMenu, "actionMenu");
    }

    /** Register the bindings the home specs name and the specs themselves; called once at homes wiring time. */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.list("homes:icon-palette", ctx -> iconLayout.icons());
        bindings.placeholder("home_icon_name", ctx -> ctx.entry(Material.class).name());
        bindings.action("homes:set-icon", this::setIcon);
        bindings.action("homes:reset-icon", this::resetIcon);
        bindings.action("homes:icon-back", this::iconBack);
        menus.registerSpec(ICON_SPEC_ID, MenuSpecs.loadOrBundled(ICON_RESOURCE, dataFolder, 6, log));
    }

    /** Open the icon picker for {@code home}; the live player is resolved by the engine. */
    public void openIcons(PlayerRef viewer, Home home) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(home, "home");
        menus.open(viewer, ICON_SPEC_ID, home);
    }

    /** Left-click a palette material: set the home's icon to it, then return to the action menu. */
    private void setIcon(MenuActionContext ctx) {
        Material material = ctx.entry(Material.class);
        Home home = ctx.subject(Home.class);
        applyIcon(ctx, home, Optional.of(HomeIcon.of(material.name())));
    }

    /** Left-click the reset button: clear the home's custom icon, then return to the action menu. */
    private void resetIcon(MenuActionContext ctx) {
        Home home = ctx.subject(Home.class);
        applyIcon(ctx, home, Optional.empty());
    }

    private void applyIcon(MenuActionContext ctx, Home home, Optional<HomeIcon> icon) {
        Player player = ctx.player();
        PlayerRef viewer = ctx.viewer();
        scheduler.async(() -> {
            setHomeIcon.setIcon(home.owner(), home.slot(), icon);
            scheduler.onEntity(viewer, () -> actionMenu.open(player, viewer, home));
        });
    }

    /** Left-click the back button: return to the home action menu without changing the icon. */
    private void iconBack(MenuActionContext ctx) {
        actionMenu.open(ctx.player(), ctx.viewer(), ctx.subject(Home.class));
    }
}

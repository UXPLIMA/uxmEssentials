package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.OpenMenuInfo;

/**
 * The {@link MenuPlaceholders} seam over the live {@link Menus} façade. Every read maps a {@code UUID} onto the
 * façade's public {@link Menus#currentMenu} / {@link Menus#lastMenuId}. The two methods that read the engine's
 * open-window holder and its reopen history from behind the engine-internals fence, so this adapter itself never
 * touches an engine internal. The scalar reads all derive from the one {@code currentMenu} snapshot, so a single
 * placeholder request reads the live window at most once.
 */
public final class MenusMenuPlaceholders implements MenuPlaceholders {

    private final Menus menus;

    public MenusMenuPlaceholders(Menus menus) {
        this.menus = Objects.requireNonNull(menus, "menus");
    }

    @Override
    public boolean inMenu(UUID player) {
        return menus.currentMenu(player).isPresent();
    }

    @Override
    public Optional<String> openedMenu(UUID player) {
        return menus.currentMenu(player).map(OpenMenuInfo::specId);
    }

    @Override
    public Optional<String> lastMenu(UUID player) {
        return menus.lastMenuId(player);
    }

    @Override
    public OptionalInt page(UUID player) {
        return menus.currentMenu(player)
                .map(info -> OptionalInt.of(info.page()))
                .orElseGet(OptionalInt::empty);
    }

    @Override
    public OptionalInt rows(UUID player) {
        return menus.currentMenu(player)
                .map(info -> OptionalInt.of(info.rows()))
                .orElseGet(OptionalInt::empty);
    }

    @Override
    public Optional<String> argument(UUID player, String name) {
        // Optional.map dropping a null map value degrades an unknown argument to empty, which the resolver renders
        // as the dash: matching an argument that was present but absent from this open.
        return menus.currentMenu(player).map(info -> info.arguments().get(name));
    }
}

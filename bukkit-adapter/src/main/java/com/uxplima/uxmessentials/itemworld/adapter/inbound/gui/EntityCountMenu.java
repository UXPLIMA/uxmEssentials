package com.uxplima.uxmessentials.itemworld.adapter.inbound.gui;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Registers the {@code /entitycount} browse menu with the menu engine and opens it. A read-only paginated grid of
 * the nearby-entity tally, one icon per entity type sorted by count descending, the visual twin of the
 * {@code /entitycount} chat read-out. It registers the entry list source and the per-entry placeholders, then
 * loads the {@code itemworld-entitycount} spec and hands it to {@link Menus}.
 *
 * <p>The tally is read from the live world through the Bukkit API, which must happen on the actor's region thread,
 * not in an off-thread list source. So {@code EntityCountCommand} runs the region-bound scan on the actor's entity
 * thread and hands the already-sorted, already-resolved {@link Tally} list (each carrying the type key, the count,
 * and its spawn-egg material name) to {@link #open} as the menu subject; the {@code itemworld:entity-tally} list
 * source only reads that subject: it touches no Bukkit API. The {@code entity_icon} / {@code entity_type} /
 * {@code entity_count} / {@code entity_radius} placeholders read the bound entry to render each icon, and a click
 * does nothing (the chat read-out had no per-type action either). An empty scan opens the one-row empty-state
 * title instead of a grid.
 */
@NullMarked
public final class EntityCountMenu {

    /** The engine spec ids the tally grid and its empty-state title register and open under. */
    public static final String GRID_SPEC_ID = "itemworld-entitycount";

    public static final String EMPTY_SPEC_ID = "itemworld-entitycount-empty";

    private static final String GRID_RESOURCE = "modules/itemworld/gui/itemworld-entitycount.conf";
    private static final String EMPTY_RESOURCE = "modules/itemworld/gui/itemworld-entitycount-empty.conf";

    private final Menus menus;

    public EntityCountMenu(Menus menus) {
        this.menus = Objects.requireNonNull(menus, "menus");
    }

    /** Register the entry list source and per-entry placeholders the spec names and the two specs. */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.list("itemworld:entity-tally", this::entries);
        bindings.placeholder("entity_icon", ctx -> entry(ctx).iconMaterial());
        bindings.placeholder("entity_type", ctx -> entry(ctx).typeKey());
        bindings.placeholder("entity_count", ctx -> String.valueOf(entry(ctx).count()));
        bindings.placeholder("entity_radius", ctx -> String.valueOf(subject(ctx).radius()));
        menus.registerSpec(GRID_SPEC_ID, MenuSpecs.loadOrBundled(GRID_RESOURCE, dataFolder, 6, log));
        menus.registerSpec(EMPTY_SPEC_ID, MenuSpecs.loadOrBundled(EMPTY_RESOURCE, dataFolder, 6, log));
    }

    /**
     * Open the tally menu for {@code viewer} over the already-sorted, already-resolved {@code tally} taken at
     * {@code scanRadius}. The caller ran the region-bound scan and resolved each icon material; this only renders
     * it, so an empty tally opens the empty-state title.
     */
    public void open(PlayerRef viewer, List<Tally> tally, int scanRadius) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(tally, "tally");
        if (tally.isEmpty()) {
            menus.open(viewer, EMPTY_SPEC_ID, null);
            return;
        }
        menus.open(viewer, GRID_SPEC_ID, new TallyView(tally, scanRadius));
    }

    /** The grid entries, read straight from the pre-computed subject; touches no Bukkit API off-thread. */
    private List<Tally> entries(MenuContext ctx) {
        return subject(ctx).entries();
    }

    private TallyView subject(MenuContext ctx) {
        return ctx.subject(TallyView.class);
    }

    private Tally entry(MenuContext ctx) {
        return ctx.entry(Tally.class);
    }

    /**
     * The subject of an open tally grid: the already-sorted entries and the scan radius they were taken at. The
     * list source and the radius placeholder read this, so the menu carries no scan of its own.
     *
     * @param entries the tallied types, already sorted by count descending
     * @param radius the scan radius the tally was taken at, for the per-entry lore
     */
    public record TallyView(List<Tally> entries, int radius) {

        public TallyView {
            entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        }
    }

    /**
     * One tallied entity type: its registry key, how many of it the scan found, and the spawn-egg material name to
     * draw it with. All three are resolved on the actor's region thread by {@code EntityCountCommand} so the menu
     * never reads the Bukkit API.
     *
     * @param typeKey the entity type's registry key (e.g. {@code zombie})
     * @param count how many of this type the scan found
     * @param iconMaterial the material name to draw the icon with (the type's spawn egg, or a generic fallback)
     */
    public record Tally(String typeKey, int count, String iconMaterial) {

        public Tally {
            Objects.requireNonNull(typeKey, "typeKey");
            Objects.requireNonNull(iconMaterial, "iconMaterial");
        }
    }
}

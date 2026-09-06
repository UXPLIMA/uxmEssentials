package com.uxplima.uxmessentials.itemworld.adapter.inbound.gui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.bukkit.Material;

import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Registers the {@code /recipe} crafting-grid display with the menu engine and opens it. A read-only window: it
 * registers the per-slot material/name/lore placeholders the spec names, then loads the {@code itemworld-recipe}
 * spec and hands it to {@link Menus}. The menu opens with a {@link RecipeDisplay} subject carrying the nine
 * ingredient cells and the result material, so every placeholder reads that subject to render the right item.
 *
 * <p>The view holds no recipe logic. {@code RecipeCommand} resolves the recipe (its existing
 * {@code firstCraftingRecipe} + choice→material helpers) into a nine-cell ingredient grid (a {@code null} cell is
 * an empty slot) and a result material, then opens this menu over a {@link RecipeDisplay}; an item with no
 * crafting form opens {@link #openEmpty} instead, the one-row empty-state title. Each of the nine grid cells is a
 * fixed slot whose material, name, and lore come from {@code recipe_slot<i>_material} / {@code _name} /
 * {@code _lore} placeholders that branch on whether that cell is empty. An empty cell shows the glass filler with
 * the empty-slot label, a filled cell shows its ingredient with the ingredient lore.
 */
@NullMarked
public final class RecipeGridMenu {

    /** The engine spec ids the crafting grid and its empty-state title register and open under. */
    public static final String GRID_SPEC_ID = "itemworld-recipe";

    public static final String EMPTY_SPEC_ID = "itemworld-recipe-none";

    private static final String GRID_RESOURCE = "modules/itemworld/gui/itemworld-recipe.conf";
    private static final String EMPTY_RESOURCE = "modules/itemworld/gui/itemworld-recipe-none.conf";

    /** The nine grid cells, row-major top-left to bottom-right; matched by the spec's per-slot placeholders. */
    private static final int GRID_CELLS = 9;

    private final Menus menus;
    private final Messages messages;
    private final Material filler;

    public RecipeGridMenu(Menus menus, Messages messages, Material filler) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.filler = Objects.requireNonNull(filler, "filler");
    }

    /** Register the per-slot and result placeholders the spec names and the two specs; called once at wiring time. */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        for (int cell = 0; cell < GRID_CELLS; cell++) {
            int index = cell;
            bindings.placeholder("recipe_slot" + index + "_material", ctx -> cellMaterial(ctx, index));
            bindings.placeholder("recipe_slot" + index + "_name", ctx -> cellName(ctx, index));
            bindings.placeholder("recipe_slot" + index + "_lore", ctx -> cellLore(ctx, index));
        }
        bindings.placeholder(
                "recipe_result_material", ctx -> subject(ctx).result().name());
        bindings.placeholder("recipe_result_name", this::resultName);
        bindings.placeholder("recipe_result_lore", ctx -> resolve(ctx, ItemworldMessageKey.RECIPE_GUI_RESULT_LORE));
        menus.registerSpec(GRID_SPEC_ID, MenuSpecs.loadOrBundled(GRID_RESOURCE, dataFolder, 3, log));
        menus.registerSpec(EMPTY_SPEC_ID, MenuSpecs.loadOrBundled(EMPTY_RESOURCE, dataFolder, 3, log));
    }

    /**
     * Open the recipe grid for {@code viewer}: the nine {@code grid} cells (a {@code null} cell is empty) and the
     * {@code result} material. The {@code grid} must be exactly nine cells in row-major order (top-left to
     * bottom-right).
     */
    public void open(PlayerRef viewer, List<@Nullable Material> grid, Material result) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(result, "result");
        if (Objects.requireNonNull(grid, "grid").size() != GRID_CELLS) {
            throw new IllegalArgumentException("grid must have " + GRID_CELLS + " cells, was " + grid.size());
        }
        menus.open(viewer, GRID_SPEC_ID, new RecipeDisplay(grid, result));
    }

    /** Open the empty-state title for {@code viewer}: shown for an item with no crafting recipe. */
    public void openEmpty(PlayerRef viewer) {
        Objects.requireNonNull(viewer, "viewer");
        menus.open(viewer, EMPTY_SPEC_ID, null);
    }

    private RecipeDisplay subject(MenuContext ctx) {
        return ctx.subject(RecipeDisplay.class);
    }

    /** The cell's material name: its ingredient material, or the glass filler when the cell is empty. */
    private String cellMaterial(MenuContext ctx, int cell) {
        Material material = subject(ctx).cell(cell);
        return material == null || material.isAir() ? filler.name() : material.name();
    }

    /** The cell's name source: the empty-slot label for an empty cell, or empty so a filled cell keeps its default. */
    private String cellName(MenuContext ctx, int cell) {
        Material material = subject(ctx).cell(cell);
        return material == null || material.isAir() ? resolve(ctx, ItemworldMessageKey.RECIPE_GUI_EMPTY_NAME) : "";
    }

    /** The cell's lore source: the shared ingredient lore for a filled cell, or empty for an empty cell. */
    private String cellLore(MenuContext ctx, int cell) {
        Material material = subject(ctx).cell(cell);
        return material == null || material.isAir() ? "" : resolve(ctx, ItemworldMessageKey.RECIPE_GUI_INGREDIENT_LORE);
    }

    private String resultName(MenuContext ctx) {
        return messages.resolve(
                ctx.viewer(),
                ItemworldMessageKey.RECIPE_GUI_RESULT_NAME,
                Map.of("recipe_item", subject(ctx).result().getKey().getKey()));
    }

    private String resolve(MenuContext ctx, ItemworldMessageKey key) {
        return messages.resolve(ctx.viewer(), key, Map.of());
    }

    /**
     * The subject of an open recipe grid: the nine row-major ingredient cells (a {@code null} cell is an empty
     * slot) and the result material the recipe yields. The placeholders read this to render each grid cell and the
     * result slot, so the menu carries no recipe logic of its own.
     *
     * @param cells the nine row-major ingredient cells, a {@code null} cell being an empty slot
     * @param result the material the recipe crafts
     */
    public record RecipeDisplay(List<@Nullable Material> cells, Material result) {

        public RecipeDisplay {
            Objects.requireNonNull(result, "result");
            if (Objects.requireNonNull(cells, "cells").size() != GRID_CELLS) {
                throw new IllegalArgumentException("cells must have " + GRID_CELLS + " entries, was " + cells.size());
            }
            cells = java.util.Collections.unmodifiableList(new ArrayList<>(cells));
        }

        /** The material at row-major cell {@code index}, or {@code null} when that slot is empty. */
        public @Nullable Material cell(int index) {
            return cells.get(index);
        }
    }
}

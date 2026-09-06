package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.itemworld.adapter.inbound.gui.RecipeGridMenu;
import com.uxplima.uxmessentials.itemworld.adapter.outbound.BukkitItemResolver;
import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.itemworld.domain.ItemQuery;
import com.uxplima.uxmessentials.itemworld.domain.SubFeatureGroup;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * {@code /recipe [item]}: show an item's crafting recipe as text. The held item when called with no
 * argument, or a named item resolved against the registry. Read-only, so no audit and no region hop is
 * needed; the first shaped or shapeless crafting recipe is rendered through
 * {@link ItemworldMessageKey#RECIPE_SHAPED} / {@link ItemworldMessageKey#RECIPE_SHAPELESS}, and an item with
 * no crafting recipe replies {@link ItemworldMessageKey#RECIPE_NONE}. An unknown named item replies
 * {@link ItemworldMessageKey#UNKNOWN_ITEM}; an empty hand with no argument replies
 * {@link ItemworldMessageKey#NO_ITEM_IN_HAND}.
 *
 * <p>With its catalog {@code gui} flag on, bare {@code /recipe} (held item) and {@code /recipe <item>} open the
 * {@link RecipeGridMenu} crafting grid instead, the same first recipe, laid into a 3x3 ingredient grid with the
 * result in its own slot. The {@link #runGui} opener installed on the bare root by the {@code GuiRootBinding}
 * reuses the same recipe resolution, mapping each shape cell (or each shapeless ingredient, in order) to a
 * representative item; the empty-hand, unknown-item, and no-recipe replies are unchanged, with no-recipe opening
 * an empty-state title. With the flag off the bare root keeps the text output.
 */
@NullMarked
public final class RecipeCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.recipe.use";
    private static final int GRID_CELLS = 9;

    private final @Nullable RecipeGridMenu gridView;

    public RecipeCommand(ItemworldServices services) {
        this(services, null);
    }

    public RecipeCommand(ItemworldServices services, @Nullable RecipeGridMenu gridView) {
        super(services, "recipe", SubFeatureGroup.ITEM_UTILS, "Show an item's crafting recipe.");
        this.gridView = gridView;
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(literal())
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(ctx -> run(ctx, Optional.empty()))
                .then(Commands.argument("item", StringArgumentType.word())
                        .executes(ctx -> run(ctx, Optional.of(StringArgumentType.getString(ctx, "item")))))
                .build();
    }

    @Override
    public String description() {
        return describe();
    }

    /**
     * Bare {@code /recipe} opens the crafting-grid menu when the command's catalog {@code gui} flag is on; with it
     * off the bare root falls back to the text output. Empty when no view was wired (the GUI module is absent).
     */
    @Override
    public Optional<Command<CommandSourceStack>> guiRoot() {
        return gridView == null ? Optional.empty() : Optional.of(ctx -> runGui(ctx, Optional.empty()));
    }

    private int run(CommandContext<CommandSourceStack> ctx, Optional<String> named) {
        if (!enabled(ctx)) {
            return Command.SINGLE_SUCCESS;
        }
        Optional<Material> material = named.isPresent() ? namedItem(ctx, named.get()) : heldMaterial(ctx);
        material.ifPresent(found -> report(ctx, found));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Bare {@code /recipe} with gui on: resolve the item the same way the text path does, then open the grid. The
     * empty-hand / unknown-item replies are unchanged; an item with no crafting recipe opens an empty-state title.
     */
    private int runGui(CommandContext<CommandSourceStack> ctx, Optional<String> named) {
        RecipeGridMenu view = gridView;
        if (view == null) {
            return run(ctx, named);
        }
        if (!enabled(ctx)) {
            return Command.SINGLE_SUCCESS;
        }
        Player player = player(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        Optional<Material> material = named.isPresent() ? namedItem(ctx, named.get()) : heldMaterial(ctx);
        material.ifPresent(found -> openGrid(view, player, found));
        return Command.SINGLE_SUCCESS;
    }

    private void openGrid(RecipeGridMenu view, Player player, Material material) {
        Optional<Recipe> crafting = firstCraftingRecipe(material);
        if (crafting.isEmpty()) {
            view.openEmpty(ref(player));
            return;
        }
        view.open(ref(player), gridCells(crafting.get()), material);
    }

    private Optional<Material> namedItem(CommandContext<CommandSourceStack> ctx, String raw) {
        Optional<Material> material = ItemQuery.parse(raw).flatMap(BukkitItemResolver::material);
        if (material.isEmpty()) {
            reply(ctx, ItemworldMessageKey.UNKNOWN_ITEM, Map.of("item", raw));
        }
        return material;
    }

    private Optional<Material> heldMaterial(CommandContext<CommandSourceStack> ctx) {
        Player player = player(ctx);
        if (player == null) {
            return Optional.empty();
        }
        return heldItem(ctx, player).map(ItemStack::getType);
    }

    private void report(CommandContext<CommandSourceStack> ctx, Material material) {
        String item = material.getKey().toString();
        Optional<Recipe> crafting = firstCraftingRecipe(material);
        if (crafting.isEmpty()) {
            reply(ctx, ItemworldMessageKey.RECIPE_NONE, Map.of("item", item));
            return;
        }
        Recipe recipe = crafting.get();
        if (recipe instanceof ShapedRecipe shaped) {
            reply(ctx, ItemworldMessageKey.RECIPE_SHAPED, Map.of("item", item, "grid", grid(shaped)));
        } else if (recipe instanceof ShapelessRecipe shapeless) {
            reply(
                    ctx,
                    ItemworldMessageKey.RECIPE_SHAPELESS,
                    Map.of("item", item, "ingredients", ingredients(shapeless)));
        }
    }

    /** The first shaped or shapeless crafting recipe yielding {@code material}, ignoring non-crafting ones. */
    private static Optional<Recipe> firstCraftingRecipe(Material material) {
        return Bukkit.getServer().getRecipesFor(new ItemStack(material)).stream()
                .filter(recipe -> recipe instanceof ShapedRecipe || recipe instanceof ShapelessRecipe)
                .findFirst();
    }

    /**
     * The recipe as nine row-major grid cells (top-left to bottom-right); a {@code null} cell is an empty slot. A
     * shaped recipe maps each of its (up to 3x3) shape positions to a representative material; a shapeless recipe
     * fills the cells with its ingredients in declaration order. This is the GUI counterpart of {@link #grid} /
     * {@link #ingredients}, reusing the same {@link #choiceMaterial} resolution.
     */
    private static List<@Nullable Material> gridCells(Recipe recipe) {
        List<@Nullable Material> cells = new ArrayList<>(GRID_CELLS);
        for (int i = 0; i < GRID_CELLS; i++) {
            cells.add(null);
        }
        if (recipe instanceof ShapedRecipe shaped) {
            fillShaped(cells, shaped);
        } else if (recipe instanceof ShapelessRecipe shapeless) {
            fillShapeless(cells, shapeless);
        }
        return cells;
    }

    /** Map each shape position to its material, left-aligned at the top so a 2x2 recipe sits in the grid's corner. */
    private static void fillShaped(List<@Nullable Material> cells, ShapedRecipe recipe) {
        Map<Character, RecipeChoice> choices = recipe.getChoiceMap();
        String[] shape = recipe.getShape();
        for (int row = 0; row < shape.length && row < 3; row++) {
            String line = shape[row];
            for (int col = 0; col < line.length() && col < 3; col++) {
                cells.set(row * 3 + col, choiceMaterial(choices.get(line.charAt(col))));
            }
        }
    }

    /** Place the shapeless ingredients into the grid in declaration order, up to nine cells. */
    private static void fillShapeless(List<@Nullable Material> cells, ShapelessRecipe recipe) {
        List<RecipeChoice> choices = recipe.getChoiceList();
        for (int i = 0; i < choices.size() && i < GRID_CELLS; i++) {
            cells.set(i, choiceMaterial(choices.get(i)));
        }
    }

    /** The up-to-three shape rows, each char mapped to a short material name, joined by a slash. */
    private static String grid(ShapedRecipe recipe) {
        Map<Character, RecipeChoice> choices = recipe.getChoiceMap();
        return java.util.Arrays.stream(recipe.getShape())
                .map(row -> renderRow(row, choices))
                .collect(Collectors.joining(" / "));
    }

    private static String renderRow(String row, Map<Character, RecipeChoice> choices) {
        return row.chars().mapToObj(c -> choiceName(choices.get((char) c))).collect(Collectors.joining(", "));
    }

    /** The comma-joined ingredient material names of a shapeless recipe, in declaration order. */
    private static String ingredients(ShapelessRecipe recipe) {
        return recipe.getChoiceList().stream().map(RecipeCommand::choiceName).collect(Collectors.joining(", "));
    }

    /** A short material name for a slot's choice; {@code air} for an empty slot or an unrenderable choice. */
    private static String choiceName(@Nullable RecipeChoice choice) {
        Material material = choiceMaterial(choice);
        return material == null
                ? Material.AIR.getKey().getKey()
                : material.getKey().getKey();
    }

    /**
     * A representative {@link Material} for a slot's choice, or {@code null} for an empty slot (a space in the
     * shape) or an unrenderable choice. The first material of a material/exact choice is used, the same one the
     * text {@link #choiceName} prints, so the grid and the text never disagree about what fills a cell.
     */
    private static @Nullable Material choiceMaterial(@Nullable RecipeChoice choice) {
        if (choice instanceof RecipeChoice.MaterialChoice materials
                && !materials.getChoices().isEmpty()) {
            return materials.getChoices().get(0);
        }
        if (choice instanceof RecipeChoice.ExactChoice exact
                && !exact.getChoices().isEmpty()) {
            return exact.getChoices().get(0).getType();
        }
        return null;
    }
}

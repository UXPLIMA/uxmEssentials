package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.itemworld.adapter.inbound.gui.EntityCountMenu;
import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.itemworld.domain.SubFeatureGroup;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * {@code /entitycount [radius]}: tally the entities near the actor grouped by {@link EntityType}, for lag
 * diagnosis before {@code /butcher} or {@code /killall}. Distinct from {@code /near} (players), the purge
 * family (delete), and {@code /killall} (world-wide): this only reads. The scan is region-bound, so it runs on
 * the actor's region thread through the kernel {@code Scheduler}, and the radius is clamped to a sane maximum so
 * the bounded scan stays within the command's ms budget.
 *
 * <p>An empty area replies {@link ItemworldMessageKey#ENTITYCOUNT_NONE}; otherwise a
 * {@link ItemworldMessageKey#ENTITYCOUNT_HEADER} carrying the total is followed by one
 * {@link ItemworldMessageKey#ENTITYCOUNT_ENTRY} per type, ordered by count descending.
 *
 * <p>Both forms, bare {@code /entitycount} and {@code /entitycount <radius>}, open the
 * {@link EntityCountMenu} grid when a view is wired (the same tally, one spawn-egg icon per type sorted by
 * count), and otherwise print the chat listing. The bare root's gui-vs-chat choice is made externally: the
 * {@code GuiRootBinding} installs {@link #guiRoot} as the root executor only when the catalog {@code gui} flag is
 * on, and the flag defaults on. The {@code <radius>} child cannot see that flag at build time: its executor is
 * fixed when the tree is built, so it keys off the same view presence the opener does: it opens the grid when a
 * view is wired and chats when it is not. {@link #runGui} runs the same region-bound scan on the actor's entity
 * thread, then hands the sorted tally to the view; a console has no inventory and falls back to the chat
 * read-out. One consequence of keying off view presence rather than the per-command flag: a {@code gui=false}
 * override suppresses the bare root's grid (the opener is never installed) but not the {@code <radius>} child's,
 * which still opens the grid while a view is wired. That divergence is accepted because gui defaults on, so the
 * common case has both forms agree; the alternative. Threading the resolved flag down into every argument
 * executor: would reach across the registration chokepoint for no behaviour the default does not already give.
 */
@NullMarked
public final class EntityCountCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.entitycount.use";
    private static final int DEFAULT_RADIUS = 64;
    private static final int MAX_RADIUS = 256;

    private final @Nullable EntityCountMenu listView;

    public EntityCountCommand(ItemworldServices services) {
        this(services, null);
    }

    public EntityCountCommand(ItemworldServices services, @Nullable EntityCountMenu listView) {
        super(services, "entitycount", SubFeatureGroup.MOB_ENTITY, "Count nearby entities by type.");
        this.listView = listView;
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        // The bare root's executor is the chat read-out; the GuiRootBinding swaps in the gui opener when the
        // catalog flag is on. The <radius> child can't reach that flag, so it mirrors the bare root by view
        // presence: open the grid when a view is wired, chat when it is not.
        return Commands.literal(literal())
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(ctx -> run(ctx, DEFAULT_RADIUS))
                .then(Commands.argument("radius", IntegerArgumentType.integer(1))
                        .executes(ctx -> dispatch(ctx, IntegerArgumentType.getInteger(ctx, "radius"))))
                .build();
    }

    /** The {@code <radius>} child: open the grid when a view is wired, else chat, mirroring the bare root. */
    private int dispatch(CommandContext<CommandSourceStack> ctx, int requested) {
        return listView == null ? run(ctx, requested) : runGui(ctx, requested);
    }

    @Override
    public String description() {
        return describe();
    }

    /**
     * Bare {@code /entitycount} opens the tally menu when the command's catalog {@code gui} flag is on, scanning at
     * the default radius; with it off the bare root falls back to the chat read-out. Empty when no view was wired.
     */
    @Override
    public Optional<Command<CommandSourceStack>> guiRoot() {
        return listView == null ? Optional.empty() : Optional.of(ctx -> runGui(ctx, DEFAULT_RADIUS));
    }

    private int run(CommandContext<CommandSourceStack> ctx, int requested) {
        if (!enabled(ctx)) {
            return Command.SINGLE_SUCCESS;
        }
        Player player = player(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        int radius = Math.min(requested, MAX_RADIUS);
        services.kernel().scheduler().onEntity(ref(player), () -> count(ctx, player, radius));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Bare {@code /entitycount} with gui on: run the same region-bound scan on the actor's entity thread, then open
     * the grid. A console has no inventory, so it falls back to the chat read-out.
     */
    private int runGui(CommandContext<CommandSourceStack> ctx, int requested) {
        EntityCountMenu view = listView;
        if (view == null) {
            return run(ctx, requested);
        }
        if (!enabled(ctx)) {
            return Command.SINGLE_SUCCESS;
        }
        Player player = player(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        int radius = Math.min(requested, MAX_RADIUS);
        services.kernel().scheduler().onEntity(ref(player), () -> countGui(view, player, radius));
        return Command.SINGLE_SUCCESS;
    }

    private void count(CommandContext<CommandSourceStack> ctx, Player player, int radius) {
        Map<EntityType, Integer> tally = tally(player, radius);
        if (tally.isEmpty()) {
            reply(ctx, ItemworldMessageKey.ENTITYCOUNT_NONE, Map.of("radius", String.valueOf(radius)));
            return;
        }
        int total = tally.values().stream().mapToInt(Integer::intValue).sum();
        reply(
                ctx,
                ItemworldMessageKey.ENTITYCOUNT_HEADER,
                Map.of("total", String.valueOf(total), "radius", String.valueOf(radius)));
        tally.entrySet().stream()
                .sorted(Map.Entry.<EntityType, Integer>comparingByValue().reversed())
                .forEach(entry -> reply(
                        ctx,
                        ItemworldMessageKey.ENTITYCOUNT_ENTRY,
                        Map.of("type", entry.getKey().getKey().toString(), "count", String.valueOf(entry.getValue()))));
    }

    private void countGui(EntityCountMenu view, Player player, int radius) {
        // Runs on the actor's region thread (the scan reads the live world), so every per-type icon material is
        // resolved here too; the menu's list source only reads the pre-computed tally and touches no Bukkit API.
        List<EntityCountMenu.Tally> sorted = tally(player, radius).entrySet().stream()
                .sorted(Map.Entry.<EntityType, Integer>comparingByValue().reversed())
                .map(e -> new EntityCountMenu.Tally(
                        e.getKey().getKey().getKey(),
                        e.getValue(),
                        iconMaterial(e.getKey()).name()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        view.open(ref(player), sorted, radius);
    }

    /** Tally the entities within {@code radius} of {@code player} by type; runs on the actor's region thread. */
    private static Map<EntityType, Integer> tally(Player player, int radius) {
        Map<EntityType, Integer> tally = new EnumMap<>(EntityType.class);
        for (Entity nearby : player.getNearbyEntities(radius, radius, radius)) {
            tally.merge(nearby.getType(), 1, Integer::sum);
        }
        return tally;
    }

    /**
     * That type's spawn egg ({@code ZOMBIE_SPAWN_EGG}, …) where the registry has one, falling back to a generic: a
     * plain egg for a spawnable type that has no dedicated egg material, and paper for the non-spawnable
     * pseudo-types (item drops, projectiles, the markers). The {@code valueOf} is guarded so a type whose egg name
     * does not resolve never throws. Resolved on the actor's region thread with the rest of the tally.
     */
    private static Material iconMaterial(EntityType type) {
        try {
            return Material.valueOf(type.name() + "_SPAWN_EGG");
        } catch (IllegalArgumentException noEgg) {
            return type.isSpawnable() ? Material.EGG : Material.PAPER;
        }
    }
}

package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Ready-made, read-only sources every custom menu can page without a line of code. Two are server-global rosters:
 * {@code online-players} (each online player, auto-skinned when the template uses {@code %online_player_skull%}) and
 * {@code worlds} (each loaded world, with {@code %worlds_icon%} auto-picking an environment block). Two more reflect
 * the viewer's own storage as live tiles: {@code self-inventory} (the non-empty stacks in their inventory) and
 * {@code self-enderchest} (their ender chest), each entry being the actual {@link ItemStack} so a template's
 * {@code material = "entry"} draws the real item. Each source is a list handler plus the per-entry {@code %token%}
 * placeholders a template reads to draw one row, all registered once at startup into the shared {@link MenuBindings}
 *. A spec's {@code list { source = self-inventory, template { … } }} then resolves the same way a code-registered
 * feature source does.
 *
 * <p>The two storage sources are strictly read-only: they snapshot clones and never touch a target other than the
 * viewer, so they respect the engine's cancel-every-click invariant. A mutable take/give viewer is a separate,
 * deliberately non-engine concern (the {@code /invsee} / {@code /endersee} inventory leaves and the future
 * storage-menu click mechanics), never built here.
 *
 * <p>The Folia constraint shapes the whole design. A list source runs on an async thread. {@code Menus} resolves a
 * spec's lists off the tick thread, and there the entity/world API is off-limits: {@code player.getWorld()},
 * {@code getPing()}, {@code getGameMode()}, {@code world.getPlayers()} and a player's own {@code getInventory()} all
 * touch region state that is unsafe off the owning region thread. So the roster sources snapshot on the global region
 * thread and the storage sources snapshot on the <em>viewer's</em> entity region thread, each returning immutable
 * values (records, or defensively cloned stacks); nothing Bukkit-live crosses to the async thread, and the per-entry
 * placeholders read only the captured snapshot.
 */
public final class LiveDataSources {

    /** How long the async list-resolver waits for the global-thread snapshot before serving an empty roster. */
    private static final long SNAPSHOT_TIMEOUT_SECONDS = 2;

    private LiveDataSources() {}

    /**
     * Register the {@code online-players} and {@code worlds} sources and their per-entry placeholders into
     * {@code bindings}. Each source snapshots on the global region thread through {@code scheduler}, so the same
     * {@link Scheduler} the engine already opens menus on is passed here (a duplicate source/placeholder id throws
     * loudly, which is why these ids are registered exactly once).
     */
    public static void register(MenuBindings bindings, Scheduler scheduler) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(scheduler, "scheduler");
        bindings.list("online-players", ctx -> snapshot(scheduler, () -> onlinePlayers(ctx.viewer())));
        bindings.list("worlds", ctx -> snapshot(scheduler, LiveDataSources::loadedWorlds));
        bindings.list("self-inventory", ctx -> selfStorage(scheduler, ctx.viewer(), Player::getInventory));
        bindings.list("self-enderchest", ctx -> selfStorage(scheduler, ctx.viewer(), Player::getEnderChest));
        registerOnlinePlayerPlaceholders(bindings);
        registerWorldPlaceholders(bindings);
        registerStackPlaceholders(bindings);
    }

    /** The {@code %online_player_*%} placeholders, each reading one field off the bound player entry. */
    private static void registerOnlinePlayerPlaceholders(MenuBindings bindings) {
        bindings.placeholder("online_player_name", ctx -> online(ctx, OnlinePlayerEntry::name));
        bindings.placeholder(
                "online_player_uuid", ctx -> online(ctx, entry -> entry.uuid().toString()));
        bindings.placeholder("online_player_world", ctx -> online(ctx, OnlinePlayerEntry::world));
        bindings.placeholder("online_player_ping", ctx -> online(ctx, entry -> String.valueOf(entry.ping())));
        bindings.placeholder("online_player_gamemode", ctx -> online(ctx, OnlinePlayerEntry::gameMode));
        // A convenience so a template can write material = "%online_player_skull%" and get a UUID-skinned head with no
        // name lookup: it yields the skull:<uuid> form the skull icon provider turns into a PLAYER_HEAD.
        bindings.placeholder("online_player_skull", ctx -> online(ctx, entry -> "skull:" + entry.uuid()));
    }

    /** The {@code %worlds_*%} placeholders, each reading one field off the bound world entry. */
    private static void registerWorldPlaceholders(MenuBindings bindings) {
        bindings.placeholder("worlds_name", ctx -> world(ctx, WorldEntry::name));
        bindings.placeholder("worlds_environment", ctx -> world(ctx, WorldEntry::environment));
        bindings.placeholder("worlds_players", ctx -> world(ctx, entry -> String.valueOf(entry.players())));
        // A convenience so a template can write material = "%worlds_icon%" and auto-pick the environment's block.
        bindings.placeholder("worlds_icon", ctx -> world(ctx, entry -> environmentIcon(entry.environment())));
    }

    /** The {@code %entry_*%} placeholders, each reading one field off the bound item-stack entry. */
    private static void registerStackPlaceholders(MenuBindings bindings) {
        bindings.placeholder(
                "entry_type", ctx -> stack(ctx, item -> item.getType().name()));
        bindings.placeholder("entry_amount", ctx -> stack(ctx, item -> String.valueOf(item.getAmount())));
    }

    /**
     * Take a snapshot of live server state on the global region thread and return it to the async caller. When the
     * caller already owns the global thread (the deadlock guard, a global-thread invocation, or a test scheduler
     * that reports itself global), the supplier runs inline, because scheduling then blocking on the same thread
     * would deadlock. Otherwise the supplier runs on the global thread and this thread waits on a latch for it.
     *
     * <p>That wait is on the async list-resolution thread, never the main thread, so it is legal: the forbidden rule
     * is blocking the <em>main</em> thread, and this bounded off-tick wait keeps a hung global thread from stalling a
     * menu open forever: an empty roster is served on timeout or interruption instead. No {@code CompletableFuture}
     * is used; a bare {@link CountDownLatch} plus {@link AtomicReference} makes the hand-off unambiguous.
     */
    private static <T> List<T> snapshot(Scheduler scheduler, Supplier<List<T>> supplier) {
        if (scheduler.onGlobalThread()) {
            return supplier.get();
        }
        AtomicReference<List<T>> holder = new AtomicReference<>(List.of());
        CountDownLatch done = new CountDownLatch(1);
        scheduler.onGlobal(() -> {
            holder.set(supplier.get());
            done.countDown();
        });
        return awaitSnapshot(done, holder);
    }

    /**
     * Snapshot the viewer's own storage, the inventory or the ender chest {@code pick} selects, on the viewer's
     * entity region thread, where a player's inventory is safe to read. When the async caller already owns that
     * region (the deadlock guard, and the path a synchronous test scheduler takes) the read runs inline; otherwise
     * it hops onto the entity thread and this thread waits on a latch for the clones to come back.
     *
     * <p>The three-argument {@link Scheduler#onEntity(PlayerRef, Runnable, Runnable) onEntity} is used so a viewer
     * who has just logged off (a retired entity the scheduler silently drops) still releases the latch through the
     * {@code retired} callback: the wait then returns an empty list rather than hanging for the full timeout. As in
     * the roster snapshot, this wait is on the async list-resolution thread, never the main thread, so it is legal:
     * the forbidden rule is blocking the <em>main</em> thread. No {@code CompletableFuture} is used.
     */
    private static List<ItemStack> selfStorage(
            Scheduler scheduler, PlayerRef viewer, Function<Player, Inventory> pick) {
        if (scheduler.ownsEntity(viewer)) {
            return readStacks(viewer, pick);
        }
        AtomicReference<List<ItemStack>> holder = new AtomicReference<>(List.of());
        CountDownLatch done = new CountDownLatch(1);
        scheduler.onEntity(
                viewer,
                () -> {
                    holder.set(readStacks(viewer, pick));
                    done.countDown();
                },
                done::countDown);
        return awaitSnapshot(done, holder);
    }

    /** Wait for a scheduled snapshot to fill {@code holder}, serving an empty list on timeout or interruption. */
    private static <T> List<T> awaitSnapshot(CountDownLatch done, AtomicReference<List<T>> holder) {
        try {
            if (!done.await(SNAPSHOT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                return List.of();
            }
            return Objects.requireNonNullElse(holder.get(), List.of());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return List.of();
        }
    }

    /**
     * The viewer's non-empty stacks from the chosen inventory, each defensively cloned, in slot order. A null or AIR
     * slot is skipped so the rendered grid holds only real items, and an offline viewer yields an empty list. Runs on
     * the viewer's entity thread, so touching the inventory here is Folia-safe.
     */
    private static List<ItemStack> readStacks(PlayerRef viewer, Function<Player, Inventory> pick) {
        Player player = Bukkit.getPlayer(viewer.uuid());
        if (player == null) {
            return List.of();
        }
        List<ItemStack> stacks = new ArrayList<>();
        for (ItemStack item : pick.apply(player).getContents()) {
            if (item != null && !item.getType().isAir()) {
                stacks.add(item.clone());
            }
        }
        return stacks;
    }

    /**
     * One entry per online player the menu {@code viewer} may see, captured on the global thread where the entity
     * API is safe to touch. The roster is filtered through the viewer's {@code canSee} graph so a vanished player
     * the viewer cannot see never appears as a tile; a viewer who has gone offline between open and snapshot yields
     * the unfiltered roster (there is no live graph to consult, and the tiles reach nobody).
     */
    private static List<OnlinePlayerEntry> onlinePlayers(PlayerRef viewerRef) {
        Player viewer = Bukkit.getPlayer(viewerRef.uuid());
        List<OnlinePlayerEntry> entries = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (viewer != null && !viewer.canSee(player)) {
                continue;
            }
            entries.add(new OnlinePlayerEntry(
                    player.getName(),
                    player.getUniqueId(),
                    player.getWorld().getName(),
                    player.getPing(),
                    player.getGameMode().name()));
        }
        return entries;
    }

    /** One entry per loaded world, captured on the global thread where the world API is safe to touch. */
    private static List<WorldEntry> loadedWorlds() {
        List<WorldEntry> entries = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) {
            entries.add(new WorldEntry(
                    world.getName(),
                    world.getEnvironment().name(),
                    world.getPlayers().size(),
                    true));
        }
        return entries;
    }

    /** Read one field off the bound online-player entry, or empty when the placeholder is used off such a list. */
    private static String online(MenuContext ctx, Function<OnlinePlayerEntry, String> field) {
        return ctx.entry()
                .filter(OnlinePlayerEntry.class::isInstance)
                .map(OnlinePlayerEntry.class::cast)
                .map(field)
                .orElse("");
    }

    /** Read one field off the bound world entry, or empty when the placeholder is used off such a list. */
    private static String world(MenuContext ctx, Function<WorldEntry, String> field) {
        return ctx.entry()
                .filter(WorldEntry.class::isInstance)
                .map(WorldEntry.class::cast)
                .map(field)
                .orElse("");
    }

    /** Read one field off the bound item-stack entry, or empty when the placeholder is used off such a list. */
    private static String stack(MenuContext ctx, Function<ItemStack, String> field) {
        return ctx.entry()
                .filter(ItemStack.class::isInstance)
                .map(ItemStack.class::cast)
                .map(field)
                .orElse("");
    }

    /** The block material name standing in for a world's environment, defaulting to the overworld's grass. */
    static String environmentIcon(String environment) {
        return switch (environment) {
            case "NETHER" -> "NETHERRACK";
            case "THE_END" -> "END_STONE";
            default -> "GRASS_BLOCK";
        };
    }

    /** An immutable snapshot of one online player, safe to read off any thread once captured. */
    public record OnlinePlayerEntry(String name, UUID uuid, String world, int ping, String gameMode) {

        public OnlinePlayerEntry {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(uuid, "uuid");
            Objects.requireNonNull(world, "world");
            Objects.requireNonNull(gameMode, "gameMode");
        }
    }

    /** An immutable snapshot of one loaded world, safe to read off any thread once captured. */
    public record WorldEntry(String name, String environment, int players, boolean loaded) {

        public WorldEntry {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(environment, "environment");
        }
    }
}

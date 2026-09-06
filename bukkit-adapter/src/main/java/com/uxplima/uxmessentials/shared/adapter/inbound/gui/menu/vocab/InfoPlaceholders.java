package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A ready-made pack of built-in {@code %token%} placeholders any menu spec can use in its name, lore or title to
 * surface the viewer's live state. The real-world clock, the world's in-game time, the viewer's inventory occupancy
 * and their vanilla statistics: without a feature wiring a handler per token. Registered once at startup into the
 * shared {@link MenuBindings}, so a spec loaded from disk resolves these the same way a code-registered feature menu
 * would. A spec might read:
 *
 * <pre>{@code
 * name = "<gray>%server_date% %server_time%"
 * lore = ["<gray>Free slots: %empty_slots%/36", "<gray>Holding: %held_item% x%held_amount%",
 *         "<gray>Mob kills: %stat_MOB_KILLS%", "<gray>World: day %world_day% (%world_time_formatted%)"]
 * }</pre>
 *
 * <p>Fixed-key placeholders:
 *
 * <ul>
 *   <li>{@code %server_date%} / {@code %server_time%}, the real server clock as {@code yyyy-MM-dd} and
 *       {@code HH:mm:ss}. These read {@link LocalDate#now()} / {@link LocalTime#now()} and do not depend on the
 *       viewer, so they resolve even for an absent player.</li>
 *   <li>{@code %world_time%}. The viewer's world time of day in ticks (0 to 24000).</li>
 *   <li>{@code %world_time_formatted%}. That tick converted to the in-game 24-hour clock, {@code HH:mm}.</li>
 *   <li>{@code %world_day%}. The world's day number, {@code fullTime / 24000}.</li>
 *   <li>{@code %empty_slots%} / {@code %used_slots%}. The count of empty and occupied slots among the viewer's 36
 *       main storage slots (a slot is empty when it holds nothing or air).</li>
 *   <li>{@code %held_item%} / {@code %held_amount%}. The viewer's main-hand item's material name and stack size;
 *       an empty hand reads as {@code ""} and {@code 0} respectively.</li>
 * </ul>
 *
 * <p>Statistic family: {@code %stat_<NAME>%} resolves the viewer's vanilla {@link Statistic} whose enum name is the
 * token tail, e.g. {@code %stat_MOB_KILLS%}, {@code %stat_PLAY_ONE_MINUTE%}, {@code %stat_DEATHS%}. This is a prefix
 * fallback registered alongside the disjoint {@code papi_} ({@link PapiPlaceholders}) and {@code data_}/{@code meta_}
 * ({@link PlayerDataPlaceholders}) families. The registry consults each in order and the first that claims an id
 * wins, so the three coexist. Only <em>untyped</em> statistics are supported here: a name that is unknown or names a
 * typed statistic (one that needs a block/item/entity sub-parameter, e.g. {@code MINE_BLOCK}) fails soft to
 * {@code ""} rather than throwing.
 *
 * <p>Every viewer-state read resolves the online {@link Player} through {@link Bukkit#getPlayer}; an offline or absent
 * viewer reads as {@code ""}, and no resolver ever throws, so one placeholder can never abort a render. The reads are
 * entity-thread-safe where a menu renders: a placeholder resolves during render on the viewer's own region thread, so
 * reading that viewer's inventory, world time and statistics touches only state the render thread already owns. The
 * values are data (material names, counts, formatted times) not catalog message text, so no {@code MessageKey} is
 * involved, mirroring {@link PapiPlaceholders} and {@link PlayerDataPlaceholders}.
 */
@NullMarked
public final class InfoPlaceholders {

    /** The {@code stat_} prefix this pack's fallback claims; the remainder of the id is the {@link Statistic} name. */
    private static final String STAT_PREFIX = "stat_";

    private static final DateTimeFormatter SERVER_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT);

    private static final DateTimeFormatter SERVER_TIME = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT);

    /** Minecraft ticks per in-game day; a full day-night cycle spans this many ticks. */
    private static final long TICKS_PER_DAY = 24_000L;

    private InfoPlaceholders() {}

    /**
     * Register the info placeholders into {@code bindings}: the nine fixed-key handlers plus the {@code stat_} prefix
     * fallback. The fixed ids are collision-checked by the registry (a duplicate would throw), and the {@code stat_}
     * fallback sits beside the {@code papi_} and {@code data_}/{@code meta_} fallbacks on their disjoint prefixes.
     */
    public static void register(MenuBindings bindings) {
        Objects.requireNonNull(bindings, "bindings");
        bindings.placeholder(
                "server_date", ctx -> LocalDate.now(ZoneId.systemDefault()).format(SERVER_DATE));
        bindings.placeholder(
                "server_time", ctx -> LocalTime.now(ZoneId.systemDefault()).format(SERVER_TIME));
        bindings.placeholder("world_time", InfoPlaceholders::worldTime);
        bindings.placeholder("world_time_formatted", InfoPlaceholders::worldTimeFormatted);
        bindings.placeholder("world_day", InfoPlaceholders::worldDay);
        bindings.placeholder("empty_slots", InfoPlaceholders::emptySlots);
        bindings.placeholder("used_slots", InfoPlaceholders::usedSlots);
        bindings.placeholder("held_item", InfoPlaceholders::heldItem);
        bindings.placeholder("held_amount", InfoPlaceholders::heldAmount);
        bindings.placeholders().fallback(id -> id.startsWith(STAT_PREFIX), InfoPlaceholders::statValue);
    }

    /** The viewer's world time of day in ticks, or empty when the viewer is offline. */
    private static String worldTime(MenuContext ctx) {
        Player player = online(ctx);
        return player == null ? "" : String.valueOf(player.getWorld().getTime());
    }

    /** The viewer's world time of day rendered on the in-game 24-hour clock, or empty when the viewer is offline. */
    private static String worldTimeFormatted(MenuContext ctx) {
        Player player = online(ctx);
        return player == null ? "" : worldClock(player.getWorld().getTime());
    }

    /** The viewer's world's day number ({@code fullTime / 24000}), or empty when the viewer is offline. */
    private static String worldDay(MenuContext ctx) {
        Player player = online(ctx);
        return player == null ? "" : String.valueOf(player.getWorld().getFullTime() / TICKS_PER_DAY);
    }

    /** The number of empty slots among the viewer's 36 main storage slots, or empty when the viewer is offline. */
    private static String emptySlots(MenuContext ctx) {
        Player player = online(ctx);
        return player == null
                ? ""
                : String.valueOf(countEmpty(player.getInventory().getStorageContents()));
    }

    /** The number of occupied main storage slots (total slots minus empty), or empty when the viewer is offline. */
    private static String usedSlots(MenuContext ctx) {
        Player player = online(ctx);
        if (player == null) {
            return "";
        }
        ItemStack[] storage = player.getInventory().getStorageContents();
        return String.valueOf(storage.length - countEmpty(storage));
    }

    /** The viewer's main-hand material name, empty for an air/empty hand or an offline viewer. */
    private static String heldItem(MenuContext ctx) {
        Player player = online(ctx);
        if (player == null) {
            return "";
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        return held.getType() == Material.AIR ? "" : held.getType().name();
    }

    /** The viewer's main-hand stack size, {@code 0} for an air/empty hand and empty for an offline viewer. */
    private static String heldAmount(MenuContext ctx) {
        Player player = online(ctx);
        if (player == null) {
            return "";
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        return held.getType() == Material.AIR ? "0" : String.valueOf(held.getAmount());
    }

    /**
     * Resolve a claimed {@code stat_<NAME>} id to the viewer's statistic value. The name is parsed first, so an
     * unknown statistic short-circuits to empty before an online-player lookup is even attempted. A typed statistic
     * that needs a block/item/entity sub-parameter throws from {@code getStatistic} and is caught, so it too reads
     * empty rather than aborting the render.
     */
    private static String statValue(String id, MenuContext ctx) {
        Optional<Statistic> statistic = parseStatistic(id.substring(STAT_PREFIX.length()));
        if (statistic.isEmpty()) {
            return "";
        }
        Player player = online(ctx);
        if (player == null) {
            return "";
        }
        try {
            return String.valueOf(player.getStatistic(statistic.get()));
        } catch (IllegalArgumentException | IllegalStateException needsSubParameter) {
            // A typed statistic (e.g. MINE_BLOCK) needs a block/item/entity argument this untyped reader cannot give.
            return "";
        }
    }

    /** The {@link Statistic} named {@code name}, or empty when no such untyped enum constant exists. */
    private static Optional<Statistic> parseStatistic(String name) {
        try {
            return Optional.of(Statistic.valueOf(name));
        } catch (IllegalArgumentException unknown) {
            return Optional.empty();
        }
    }

    /** Count the empty slots (null or air) in a storage-contents snapshot. */
    private static int countEmpty(ItemStack[] storage) {
        int empty = 0;
        for (ItemStack stack : storage) {
            if (stack == null || stack.getType() == Material.AIR) {
                empty++;
            }
        }
        return empty;
    }

    /**
     * Convert a Minecraft world tick (0 to 24000) to the in-game 24-hour clock {@code HH:mm}. Tick 0 is dawn, so
     * {@code hour = (ticks/1000 + 6) % 24}; the sub-hour remainder scales into minutes as
     * {@code minute = (ticks % 1000) * 60 / 1000}. Pure and side-effect-free so the conversion is unit-testable
     * without a server (public for that reason, like the other pure grammar helpers in this package).
     */
    public static String worldClock(long ticks) {
        long hour = (ticks / 1000 + 6) % 24;
        long minute = (ticks % 1000) * 60 / 1000;
        return pad2(hour) + ":" + pad2(minute);
    }

    /** Two-digit zero-padded rendering of a 0 to 59/0 to 23 clock component. */
    private static String pad2(long value) {
        return value < 10 ? "0" + value : Long.toString(value);
    }

    /** The online {@link Player} for the viewer, or {@code null} when the viewer is offline or absent. */
    @Nullable private static Player online(MenuContext ctx) {
        return Bukkit.getPlayer(ctx.viewer().uuid());
    }
}

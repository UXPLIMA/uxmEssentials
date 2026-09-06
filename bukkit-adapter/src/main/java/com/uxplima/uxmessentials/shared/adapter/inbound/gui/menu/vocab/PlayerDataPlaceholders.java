package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab;

import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.eval.Expressions;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.outbound.meta.PlayerMeta;
import com.uxplima.uxmessentials.shared.application.port.PlayerDataStore;
import org.jspecify.annotations.NullMarked;

/**
 * The read side of the player-data vocabulary: placeholders that surface, in a spec's name/lore/title, exactly what
 * the Phase-2 {@code data-*}/{@code meta-*} actions write. Registered once at startup as a single placeholder
 * fallback over the two backing stores, it lets an operator display stored state. A coin balance, a saved choice, a
 * PDC rank: without a feature wiring a per-token handler.
 *
 * <ul>
 *   <li>{@code %data_value_<key>%}. The raw string stored under {@code key} in the durable, database-backed
 *       {@link PlayerDataStore} the {@code data-set}/{@code data-add} actions write; a missing key reads as empty,
 *       faithful to the store rather than leaking the token.</li>
 *   <li>{@code %data_number_<key>%}, the same store read as a number and formatted through the evaluator's number
 *       formatter, so an integral value reads as {@code 50} not {@code 50.0} and a missing or non-numeric value reads
 *       as {@code 0}. This is the math-friendly form a {@code {math: …}} block multiplies against.</li>
 *   <li>{@code %meta_value_<key>%}. The string stored under {@code key} in the online viewer's PDC through
 *       {@link PlayerMeta} (what {@code meta-set} writes); an offline viewer or a missing key reads as empty.</li>
 * </ul>
 *
 * <p>The keys are operator config and the values are player data, so no {@code MessageKey} is involved: this mirrors
 * {@link PapiPlaceholders} and {@link DataActions}. A resolver never throws: an unexpected state resolves to empty, so
 * one placeholder can never abort a render. Both reads are entity-thread-safe where render runs, a
 * {@link PlayerDataStore} read is a warmed cache hit and a {@link PlayerMeta} read touches the online viewer's PDC on
 * the entity thread the render already holds.
 */
@NullMarked
public final class PlayerDataPlaceholders {

    /** The three token prefixes this fallback claims; the remainder of each id is the operator's {@code <key>}. */
    private static final String DATA_VALUE = "data_value_";

    private static final String DATA_NUMBER = "data_number_";

    private static final String META_VALUE = "meta_value_";

    private PlayerDataPlaceholders() {}

    /**
     * Register the player-data placeholder fallback into {@code bindings}. {@code playerData} is the durable store the
     * {@code data_value_}/{@code data_number_} tokens read; {@code playerMeta} is the PDC accessor the
     * {@code meta_value_} tokens read on the online viewer. One fallback claims all three prefixes and routes
     * internally, so a {@code %data_value_coins%} spec both validates and resolves against the live viewer.
     */
    public static void register(MenuBindings bindings, PlayerDataStore playerData, PlayerMeta playerMeta) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(playerData, "playerData");
        Objects.requireNonNull(playerMeta, "playerMeta");
        bindings.placeholders()
                .fallback(PlayerDataPlaceholders::claims, (id, ctx) -> resolve(id, ctx, playerData, playerMeta));
    }

    /** Whether {@code id} is one of the three player-data token families this fallback owns. */
    private static boolean claims(String id) {
        return id.startsWith(DATA_VALUE) || id.startsWith(DATA_NUMBER) || id.startsWith(META_VALUE);
    }

    /**
     * Route a claimed id to its store, stripping the matched prefix to get the operator's {@code <key>}. An id that
     * somehow reaches here without matching a prefix (it cannot, since {@link #claims} gates the fallback) resolves to
     * empty rather than throwing: the resolver never aborts a render.
     */
    private static String resolve(String id, MenuContext ctx, PlayerDataStore playerData, PlayerMeta playerMeta) {
        if (id.startsWith(DATA_VALUE)) {
            return playerData
                    .get(ctx.viewer().uuid(), id.substring(DATA_VALUE.length()))
                    .orElse("");
        }
        if (id.startsWith(DATA_NUMBER)) {
            double value = playerData.number(ctx.viewer().uuid(), id.substring(DATA_NUMBER.length()), 0);
            return Expressions.format(value);
        }
        if (id.startsWith(META_VALUE)) {
            return metaValue(ctx, id.substring(META_VALUE.length()), playerMeta);
        }
        return "";
    }

    /** The viewer's PDC value under {@code key}, or empty when the viewer is offline or has no such meta. */
    private static String metaValue(MenuContext ctx, String key, PlayerMeta playerMeta) {
        Player player = Bukkit.getPlayer(ctx.viewer().uuid());
        if (player == null) {
            return "";
        }
        return playerMeta.get(player, key).orElse("");
    }
}

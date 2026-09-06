package com.uxplima.uxmessentials.tablist.adapter.outbound;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.tablist.adapter.outbound.FillerPainter.TextRenderer;
import com.uxplima.uxmessentials.tablist.domain.TablistFormat;
import com.uxplima.uxmessentials.tablist.domain.TablistLayout;
import com.uxplima.uxmessentials.tablist.domain.TablistSkinSource;
import com.uxplima.uxmlib.packet.tablist.TabEntry;
import com.uxplima.uxmlib.packet.tablist.TabListPackets;
import com.uxplima.uxmlib.packet.tablist.TabSkin;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Paints the real player's own tab-list row for a {@link TablistRenderer}'s selected format: the list name, the sort
 * order, and, the one thing native Paper cannot do, a custom-skin texture. Split out of the renderer so each class stays
 * a cohesive size: the renderer owns format selection, the header/footer, and the filler grid orchestration; this owns
 * everything that decides how the real player themselves appears in the tab.
 *
 * <p><strong>Two paths.</strong> A format with no {@link TablistFormat#skin() skin} takes the native path, the list name
 * via {@link Player#playerListName(Component)} and the order via {@link Player#setPlayerListOrder(int)}. A format with a
 * skin instead delivers the row (name + order + texture) to <em>every</em> viewer through uxmLib's
 * {@link TabListPackets#addOrUpdate}, re-adding the player's entry with the texture seated on the profile, because the
 * native setters cannot carry a texture. A switch between the two reverts the path it is leaving so neither lingers. A
 * skin source that resolves to no texture yet (an offline fetch in flight) falls back to the native path this tick; the
 * resolver fills its cache and a later tick repaints.
 *
 * <p><strong>Apply-only-on-change.</strong> The native setters and the skin packet re-send to the client on every call,
 * so each is applied only when its value changes: {@link #appliedNameFormat} / {@link #appliedOrder} hold the last
 * name-format <em>source</em> (the operator's intent, not the per-viewer rendered component, so a placeholder shifting
 * between ticks does not re-send) and order, and {@link #appliedSkin} holds the last skin tuple. A steady-state tick
 * re-applies none of them; all remembered values are dropped on {@link #resetRow} / {@link #forget} so a re-selected
 * format re-applies from scratch. Every method touches the live player, so the renderer must call this on the player's
 * region/entity thread.
 */
@NullMarked
final class RealPlayerRowPainter {

    private final TabListPackets packets;
    private final TablistSkinResolver skinResolver;
    private final TextRenderer textRenderer;
    private final LongSupplier tickSource;
    private final Supplier<? extends Collection<? extends Player>> viewers;
    private final Scheduler scheduler;

    /**
     * The last name-format source string applied to each player's list name, keyed by player UUID. An absent key means
     * no name format is applied (vanilla list name). A {@link ConcurrentHashMap} guards the connect-while-rendering race
     * and keeps the project's "every player-keyed map is concurrent" convention; every mutation otherwise runs on the
     * player's region/entity thread.
     */
    private final Map<UUID, String> appliedNameFormat = new ConcurrentHashMap<>();

    /** The last sort order applied to each player, keyed by player UUID. An absent key means no order is applied. */
    private final Map<UUID, Integer> appliedOrder = new ConcurrentHashMap<>();

    /**
     * The last skin row painted through packets for each player, keyed by player UUID. An absent key means the player is
     * on the native path (no skin). The tuple is the name source, order, and skin source so a tick that changes none of
     * them re-sends no packet, while a format switch, or an offline skin fetch completing, re-paints.
     */
    private final Map<UUID, AppliedSkin> appliedSkin = new ConcurrentHashMap<>();

    RealPlayerRowPainter(
            TabListPackets packets,
            TablistSkinResolver skinResolver,
            TextRenderer textRenderer,
            LongSupplier tickSource,
            Supplier<? extends Collection<? extends Player>> viewers,
            Scheduler scheduler) {
        this.packets = Objects.requireNonNull(packets, "packets");
        this.skinResolver = Objects.requireNonNull(skinResolver, "skinResolver");
        this.textRenderer = Objects.requireNonNull(textRenderer, "textRenderer");
        this.tickSource = Objects.requireNonNull(tickSource, "tickSource");
        this.viewers = Objects.requireNonNull(viewers, "viewers");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /**
     * Apply the name and order a selected format carries. A format with a {@link TablistFormat#skin() skin} delivers the
     * row (name + order + texture) through packets to every viewer instead of the native setters; a format with no skin
     * keeps the native list-name/order path. A switch between the two reverts the path it is leaving so neither lingers.
     */
    void applyRow(Player player, TablistFormat format, long tick) {
        applyRow(player, format, null, tick);
    }

    /**
     * The same row with the seat a roster group gave the player. A seated player wears the group's text as their list
     * name and the list order of the cell they sit in, so the layout draws them through their own tab entry instead of
     * a synthetic row beside it. An unseated player ({@code seat} is null) keeps the format's own name and order.
     */
    void applyRow(Player player, TablistFormat format, @Nullable RowSeat seat, long tick) {
        Optional<String> nameFormat = seat == null ? format.nameFormat() : Optional.of(seat.nameFormat());
        OptionalInt order = seat == null ? effectiveOrder(format) : OptionalInt.of(seat.order());
        Optional<TablistSkinSource> skinSource = format.skin();
        if (skinSource.isEmpty()) {
            // No skin: native path, and revert any skin entry previously painted for the player.
            revertSkin(player);
            applyNameFormat(player, nameFormat, tick);
            applyOrder(player, order);
            return;
        }
        applySkinRow(player, skinSource.get(), nameFormat, order, tick);
    }

    /**
     * Where a roster group seats one player: the group's text, which becomes that player's list name, and the list
     * order of the cell the group gave them. A seat replaces the format's own name format and sort order for that
     * player, because the cell they sit in is what the layout is about.
     */
    record RowSeat(String nameFormat, int order) {
        RowSeat {
            Objects.requireNonNull(nameFormat, "nameFormat");
        }
    }

    /**
     * Re-send every currently-skinned online player's packet entry to a single newly-joined {@code viewer}, so a late
     * joiner sees the custom skins the steady-state tick would not repaint for them. Native Paper replicates a player's
     * list name and order to every viewer including late joiners, so those need no handling; the packet skin path does
     * not: the server adds an already-online skinned player to the joiner's tab with that player's <em>real</em> profile.
     * For each skinned target still online (including the joining viewer themselves) the steady-state tuple is rebuilt
     * into a {@link TabEntry} and sent to the one joining viewer. A target whose skin is still resolving was never
     * recorded, so it is skipped; the next steady tick repaints it to all viewers once the texture lands.
     */
    void repaintSkinsFor(Player viewer) {
        UUID viewerId = viewer.getUniqueId();
        for (Map.Entry<UUID, AppliedSkin> painted : appliedSkin.entrySet()) {
            UUID targetId = painted.getKey();
            AppliedSkin skin = painted.getValue();
            // Rebuilding the entry reads the TARGET's live name and world (through the name renderer); on Folia those
            // belong to the target's region thread, not the joiner's. Hop to the target's entity thread to build the
            // TabEntry, then send the finished packet to the joiner from there: the channel send is thread-agnostic.
            scheduler.onEntity(new PlayerRef(targetId, skin.nameSource()), () -> {
                Player target = Bukkit.getPlayer(targetId);
                if (target != null && target.isOnline()) {
                    repaintSkinFor(viewerId, target, skin);
                }
            });
        }
    }

    /** Reset the player's vanilla list name/order/skin if this painter set any, and drop their tracking. */
    void resetRow(Player player) {
        revertSkin(player);
        resetNameAndOrder(player);
    }

    /**
     * Drop the player's name/order/skin tracking on quit without sending a revert packet. The connection is closing, so
     * a native reset to a dead channel is a no-op; just forgetting the tracking lets a relog re-paint from scratch.
     */
    void forget(Player player) {
        UUID uuid = player.getUniqueId();
        appliedSkin.remove(uuid);
        resetNameAndOrder(player);
    }

    /**
     * Rebuild {@code target}'s skin row from the tuple the steady state holds and send it to the one joining viewer.
     * Runs on the target's entity thread (the live name and world reads belong there); the viewer is re-resolved from
     * {@code viewerId} only to address the outbound channel, and the packet send itself is thread-agnostic.
     */
    private void repaintSkinFor(UUID viewerId, Player target, AppliedSkin painted) {
        Optional<TabSkin> skin = skinResolver.resolve(painted.skinSource());
        if (skin.isEmpty()) {
            // The texture has fallen out of cache since the paint; the next steady tick will repaint it to all viewers.
            return;
        }
        Player viewer = Bukkit.getPlayer(viewerId);
        if (viewer == null || !viewer.isOnline()) {
            // The joiner dropped between their join and this hop; nothing to repaint to a closed channel.
            return;
        }
        long tick = tickSource.getAsLong();
        Component name =
                painted.nameSource().isEmpty() ? displayName(target) : renderName(target, painted.nameSource(), tick);
        TabEntry entry = new TabEntry(target.getUniqueId(), name, painted.order(), skin.get(), target.getName());
        packets.send(viewer, packets.addOrUpdate(entry));
    }

    /**
     * The list order the real player themselves should sit at for this format. The format's explicit
     * {@link TablistFormat#sortOrder() sort order} always wins (so an operator's order is honoured even alongside a
     * filler grid); otherwise, when the format paints a {@link TablistLayout#isEmpty() filler grid}, the player is given
     * the {@link TablistLayout#realPlayerOrder() real-player order} so they sort above every filler into the early slots.
     * A format with neither yields an empty order: the vanilla path, untouched.
     */
    private static OptionalInt effectiveOrder(TablistFormat format) {
        if (format.sortOrder().isPresent()) {
            return format.sortOrder();
        }
        return format.layout().isEmpty()
                ? OptionalInt.empty()
                : OptionalInt.of(format.layout().realPlayerOrder());
    }

    /**
     * Paint {@code player}'s row through a packet carrying the resolved skin, sent to every viewer. Applied only when the
     * tuple (name source, order, skin source) changed for the player, so a steady-state tick re-sends nothing and a real
     * online player's entry is not re-added every tick. A skin source that resolves to no texture yet (an offline fetch
     * still in flight) falls back to the native path this tick: the resolver fills its cache and a later tick repaints.
     */
    private void applySkinRow(
            Player player,
            TablistSkinSource skinSource,
            Optional<String> nameFormat,
            OptionalInt sortOrder,
            long tick) {
        Optional<TabSkin> skin = skinResolver.resolve(skinSource);
        if (skin.isEmpty()) {
            // The texture is not available yet; take the native path so the name/order still apply with no skin.
            revertSkin(player);
            applyNameFormat(player, nameFormat, tick);
            applyOrder(player, sortOrder);
            return;
        }
        UUID uuid = player.getUniqueId();
        String nameSource = nameFormat.orElse("");
        int order = sortOrder.orElse(0);
        AppliedSkin desired = new AppliedSkin(nameSource, order, skinSource);
        if (desired.equals(appliedSkin.get(uuid))) {
            return;
        }
        // Taking ownership of the row through packets, so drop any native name/order set for the player.
        resetNameAndOrder(player);
        Component name =
                nameFormat.map(source -> renderName(player, source, tick)).orElse(displayName(player));
        broadcast(packets.addOrUpdate(new TabEntry(uuid, name, order, skin.get(), player.getName())));
        appliedSkin.put(uuid, desired);
    }

    /**
     * Reconcile the player's tab-list name with the selected format's {@link TablistFormat#nameFormat()}. Applies only
     * when the source string changed from the last value applied: a steady-state tick re-sends nothing (the setter
     * pushes a client update every call). An absent name format on a player who currently has one resets it to vanilla.
     * The tracking keys on the raw source, not the rendered name, so an {@code %anim_<name>%} token resolves to the frame
     * current when the operator last changed the format rather than re-sending the name every animation tick.
     */
    private void applyNameFormat(Player player, Optional<String> nameFormat, long tick) {
        UUID uuid = player.getUniqueId();
        if (nameFormat.isEmpty()) {
            if (appliedNameFormat.remove(uuid) != null) {
                player.playerListName(null);
            }
            return;
        }
        String source = nameFormat.get();
        if (source.equals(appliedNameFormat.get(uuid))) {
            return;
        }
        player.playerListName(renderName(player, source, tick));
        appliedNameFormat.put(uuid, source);
    }

    /**
     * Reconcile the player's tab-list sort order with the selected format's {@link TablistFormat#sortOrder()}. Applies
     * only when the order changed from the last value applied. An absent order on a player who currently has one resets
     * it to the vanilla default ({@code setPlayerListOrder(0)}).
     */
    private void applyOrder(Player player, OptionalInt sortOrder) {
        UUID uuid = player.getUniqueId();
        if (sortOrder.isEmpty()) {
            if (appliedOrder.remove(uuid) != null) {
                player.setPlayerListOrder(0);
            }
            return;
        }
        int order = sortOrder.getAsInt();
        if (Integer.valueOf(order).equals(appliedOrder.get(uuid))) {
            return;
        }
        player.setPlayerListOrder(order);
        appliedOrder.put(uuid, order);
    }

    /**
     * Revert a skin row previously painted for the player: re-add the entry once with the player's own real texture (read
     * inline from their live profile, no network) and the vanilla name/order, so the custom skin drops back to the real
     * skin without removing the server-owned entry. A no-op for a player not on the skin path.
     */
    private void revertSkin(Player player) {
        UUID uuid = player.getUniqueId();
        if (appliedSkin.remove(uuid) == null) {
            return;
        }
        TabSkin own = skinResolver
                .resolve(new TablistSkinSource.PlayerName(player.getName()))
                .orElse(null);
        broadcast(packets.addOrUpdate(new TabEntry(uuid, displayName(player), 0, own, player.getName())));
    }

    /** Reset the player's vanilla list name/order if this painter set either, and drop their tracking. */
    private void resetNameAndOrder(Player player) {
        UUID uuid = player.getUniqueId();
        if (appliedNameFormat.remove(uuid) != null) {
            player.playerListName(null);
        }
        if (appliedOrder.remove(uuid) != null) {
            player.setPlayerListOrder(0);
        }
    }

    /** Send a built packet to every current viewer. The same packet object is reused across viewers, never rebuilt. */
    private void broadcast(Object packet) {
        for (Player viewer : viewers.get()) {
            packets.send(viewer, packet);
        }
    }

    private Component renderName(Player player, String source, long tick) {
        // A name format renders through the same pipeline as the header/footer; the {player} convenience token is one
        // of
        // the built-in tokens resolved inside the shared renderer (along with {online}, {world}, …) off the live
        // player.
        return textRenderer.render(player, source, tick);
    }

    /** The player's plain name as a component: the fallback display when a skin row carries no name format. */
    private static Component displayName(Player player) {
        return Component.text(player.getName());
    }

    /**
     * The skin row last painted for a player: the name-format source, sort order, and skin source. Two paints with an
     * equal tuple are the same row, so the packet is not re-sent. This is what keeps a real online player's entry from
     * being re-added every refresh tick (the flicker guard). A change in any field re-paints.
     */
    private record AppliedSkin(String nameSource, int order, TablistSkinSource skinSource) {}
}

package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.playerstate.application.port.PlayerInfo;
import com.uxplima.uxmessentials.playerstate.application.port.PlayerStateStore;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@link PlayerstatePlaceholders} over the playerstate context's in-memory {@link PlayerStateStore} (for the
 * god toggle), the {@link PlayerInfo} port (for total play time), and the live Bukkit {@link Player} (for the
 * client-side state the snapshot does not persist). Every value is live session state, so an offline player
 * yields an empty snapshot.
 *
 * <p>PlaceholderAPI requests for an online player arrive on the main thread, so reading the live player here
 * is valid; an offline player resolves to {@code null} and the seam reports empty, which the resolver renders
 * as the dash. The god flag is read from the store rather than the player because Bukkit exposes no damage-
 * immunity flag: the context tracks it in its snapshot map.
 */
@NullMarked
public final class StorePlayerstatePlaceholders implements PlayerstatePlaceholders {

    private final PlayerStateStore store;
    private final PlayerInfo info;

    public StorePlayerstatePlaceholders(PlayerStateStore store, PlayerInfo info) {
        this.store = Objects.requireNonNull(store, "store");
        this.info = Objects.requireNonNull(info, "info");
    }

    @Override
    public Optional<Snapshot> snapshot(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        Player player = Bukkit.getPlayer(who.uuid());
        if (player == null || !player.isOnline()) {
            return Optional.empty();
        }
        return Optional.of(read(who, player));
    }

    private Snapshot read(PlayerRef who, Player player) {
        // Paper marks Player#getLocation() nullable (null only for an entity with no world, which a
        // connected player never is): assert it so NullAway is satisfied at the dereference.
        Location location = Objects.requireNonNull(player.getLocation(), "player location");
        World world = player.getWorld();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        return new Snapshot(
                player.getGameMode().name().toLowerCase(Locale.ROOT),
                player.getAllowFlight(),
                player.isFlying(),
                store.current(who).god(),
                player.getWalkSpeed(),
                player.getFlySpeed(),
                player.getHealth(),
                maxHealth(player),
                player.getFoodLevel(),
                player.getLevel(),
                player.getExp(),
                world.getName(),
                x,
                y,
                z,
                world.getBiome(x, y, z).getKey().getKey(),
                playtime(who));
    }

    private static double maxHealth(Player player) {
        var attribute = player.getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? player.getHealth() : attribute.getValue();
    }

    private Duration playtime(PlayerRef who) {
        return info.playtimeOf(who).orElse(Duration.ZERO);
    }
}

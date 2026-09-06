package com.uxplima.uxmessentials.migration.adapter;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.migration.convert.live.BalanceFeed;
import com.uxplima.uxmessentials.migration.convert.map.ImportedUser;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Reads live point balances out of the PlayerPoints plugin for the importer. Every offline player with a
 * positive points figure becomes a balance-only {@link ImportedUser} the writer seeds a wallet with under
 * the default currency, one point mapping to one unit.
 *
 * <p>PlayerPoints is not a compile dependency, so its API is reached reflectively, exactly as the old
 * {@code /eco migrate} path did. The read runs off the calling thread on the import executor. A reflective
 * failure (an API shape that has shifted, say) is logged once through the operator {@link Logger} and the
 * feed yields nothing rather than failing the run: it is never swallowed silently.
 */
@NullMarked
final class PlayerPointsBalanceFeed implements BalanceFeed {

    private static final String PLUGIN_NAME = "PlayerPoints";
    private static final String API_CLASS = "org.black_ixx.playerpoints.PlayerPoints";

    private final Plugin plugin;
    private final Logger log;

    PlayerPointsBalanceFeed(Plugin plugin, Logger log) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public boolean available() {
        return plugin.getServer().getPluginManager().isPluginEnabled(PLUGIN_NAME);
    }

    @Override
    public Stream<ImportedUser> users() {
        if (!available()) {
            return Stream.empty();
        }
        Lookup lookup;
        try {
            Object api = Class.forName(API_CLASS).getMethod("getAPI").invoke(null);
            Method look = api.getClass().getMethod("look", UUID.class);
            lookup = new Lookup(api, look);
        } catch (ReflectiveOperationException e) {
            log.warn("PlayerPoints import skipped: its API could not be reached: {}", e.toString());
            return Stream.empty();
        }
        return Arrays.stream(plugin.getServer().getOfflinePlayers())
                .map(op -> toUser(op, lookup))
                .flatMap(Optional::stream);
    }

    /** Maps one offline player to a balance-only user, or empty on no positive points or a per-player read failure. */
    private Optional<ImportedUser> toUser(OfflinePlayer op, Lookup lookup) {
        int points;
        try {
            points = (Integer) lookup.look().invoke(lookup.api(), op.getUniqueId());
        } catch (ReflectiveOperationException e) {
            log.warn("PlayerPoints lookup failed for {}: {}", op.getUniqueId(), e.toString());
            return Optional.empty();
        }
        if (points <= 0) {
            return Optional.empty();
        }
        PlayerRef owner = new PlayerRef(op.getUniqueId(), name(op));
        return Optional.of(new ImportedUser(owner, List.of(), Optional.of(BigDecimal.valueOf(points)), List.of()));
    }

    private static String name(OfflinePlayer op) {
        String name = op.getName();
        return name != null ? name : op.getUniqueId().toString();
    }

    /** The resolved PlayerPoints API handle and its {@code look(UUID)} method, bound once per stream. */
    private record Lookup(Object api, Method look) {}
}

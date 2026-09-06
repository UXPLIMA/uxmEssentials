package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.clip.placeholderapi.expansion.Relational;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The {@code %uxmessentials_<key>%} expansion. A thin PlaceholderAPI shell: it strips the implicit {@code
 * uxmessentials_} prefix PlaceholderAPI already removes, builds a {@link PlayerRef} from the requesting
 * {@link OfflinePlayer}, and delegates every key to the {@link PlaceholderResolver}, which carries the
 * resolution logic with no PlaceholderAPI dependency. This is the only class that extends {@code
 * PlaceholderExpansion}; it is constructed and registered solely in bootstrap, and only when PlaceholderAPI
 * is present.
 *
 * <p>It also implements PlaceholderAPI's {@code Relational} contract, which routes the {@code
 * %rel_uxmessentials_<key>%} form: the keys that read the relation between two players (whether one can see,
 * ignores or is trading with the other, and how far apart they stand) rather than one player's own state.
 *
 * <p>{@link #persist()} is {@code true} so the expansion survives a PlaceholderAPI internal reload, the
 * plugin owns its lifecycle and unregisters the expansion on its own disable, not when PlaceholderAPI
 * reloads its registry. An unknown key resolves to {@code null}, the PlaceholderAPI signal to leave the raw
 * token in place.
 */
@NullMarked
public final class UxmEssentialsExpansion extends PlaceholderExpansion implements Relational {

    private final PlaceholderResolver resolver;
    private final String version;

    public UxmEssentialsExpansion(PlaceholderResolver resolver, String version) {
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.version = Objects.requireNonNull(version, "version");
    }

    @Override
    public String getIdentifier() {
        return "uxmessentials";
    }

    @Override
    public String getAuthor() {
        return "UXPLIMA";
    }

    @Override
    public String getVersion() {
        return version;
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(@Nullable OfflinePlayer player, String params) {
        Objects.requireNonNull(params, "params");
        if (player == null) {
            return null;
        }
        PlayerRef who = refOf(player);
        boolean online = isOnline(player.getUniqueId());
        return resolver.resolve(who, online, params).orElse(null);
    }

    /**
     * The {@code %rel_uxmessentials_<key>%} form: {@code viewer} is the player the line is being rendered for and
     * {@code target} the player it is about. PlaceholderAPI supplies both only where a surface renders per viewer,
     * and passes either as null on a surface that does not, in which case the raw token is left in place.
     */
    @Override
    public @Nullable String onPlaceholderRequest(@Nullable Player viewer, @Nullable Player target, String params) {
        Objects.requireNonNull(params, "params");
        if (viewer == null || target == null) {
            return null;
        }
        return resolver.resolveRelational(refOf(viewer), refOf(target), params).orElse(null);
    }

    private static PlayerRef refOf(OfflinePlayer player) {
        String name = player.getName();
        return new PlayerRef(
                player.getUniqueId(), name != null ? name : player.getUniqueId().toString());
    }

    private static boolean isOnline(UUID uuid) {
        return Optional.ofNullable(Bukkit.getPlayer(uuid)).isPresent();
    }
}

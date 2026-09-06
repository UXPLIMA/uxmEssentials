package com.uxplima.uxmessentials.shared.adapter.outbound.message;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.application.port.LocaleStore;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link LocaleStore} implementation: a player's {@code /lang} override stamped in PDC under a single
 * pre-created key, so the choice survives a relog without a DB round-trip.
 *
 * <p>PDC is the default backing because a lost language preference is harmless. It does not need to
 * survive a world rollback the way an economy balance does (docs/13-i18n §7). The stored value is the
 * locale's language tag ({@code tr}, {@code en}); an empty container means no override and the resolver
 * falls back to the client locale.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>per-holder PDC</b>. Reads and writes go through the owning {@code Player}'s container;
 * the {@link NamespacedKey} is built once in the constructor, never on a hot path.
 */
@NullMarked
public final class PdcLocaleStore implements LocaleStore {

    private final NamespacedKey overrideKey;

    public PdcLocaleStore(Plugin plugin) {
        this.overrideKey = new NamespacedKey(Objects.requireNonNull(plugin, "plugin"), "lang-override");
    }

    @Override
    public Optional<Locale> override(PlayerRef player) {
        Objects.requireNonNull(player, "player");
        Player online = Bukkit.getPlayer(player.uuid());
        if (online == null) {
            return Optional.empty();
        }
        String tag = online.getPersistentDataContainer().get(overrideKey, PersistentDataType.STRING);
        if (tag == null || tag.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(Locale.forLanguageTag(tag));
    }

    @Override
    public void setOverride(PlayerRef player, Locale locale) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(locale, "locale");
        Player online = Bukkit.getPlayer(player.uuid());
        if (online != null) {
            online.getPersistentDataContainer().set(overrideKey, PersistentDataType.STRING, locale.toLanguageTag());
        }
    }

    @Override
    public void clearOverride(PlayerRef player) {
        Objects.requireNonNull(player, "player");
        Player online = Bukkit.getPlayer(player.uuid());
        if (online != null) {
            online.getPersistentDataContainer().remove(overrideKey);
        }
    }
}

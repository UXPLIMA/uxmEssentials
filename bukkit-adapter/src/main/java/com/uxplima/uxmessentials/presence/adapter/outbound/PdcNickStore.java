package com.uxplima.uxmessentials.presence.adapter.outbound;

import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.presence.application.port.NickStore;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link NickStore} implementation. A nick is transient per-holder preference that survives relog, the
 * same class as a cooldown stamp, so it is stored in PDC under a single pre-created key, never the
 * economy-grade DB. Setting both stamps the nick and applies the display name; clearing both removes the stamp
 * and restores the account name.
 *
 * <h2>Concurrency</h2>
 * Ownership: <b>per-holder PDC</b>. Every PDC read/write and every {@code displayName} application hops to the
 * affected player's owning entity/region thread through the injected {@link Scheduler} port, a {@code Player}
 * mutation is valid only on its owning thread on Folia. An offline player is a silent no-op. The
 * {@link NamespacedKey} is built once in the constructor, never on a hot path.
 */
@NullMarked
public final class PdcNickStore implements NickStore {

    private final NamespacedKey nickKey;
    private final Scheduler scheduler;

    public PdcNickStore(Plugin plugin, Scheduler scheduler) {
        this.nickKey = new NamespacedKey(Objects.requireNonNull(plugin, "plugin"), "display-nick");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public void setNick(PlayerRef who, String nick) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(nick, "nick");
        scheduler.onEntity(who, () -> applySet(who, nick));
    }

    @Override
    public void clearNick(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        scheduler.onEntity(who, () -> applyClear(who));
    }

    private void applySet(PlayerRef who, String nick) {
        Player player = Bukkit.getPlayer(who.uuid());
        if (player == null) {
            return;
        }
        player.getPersistentDataContainer().set(nickKey, PersistentDataType.STRING, nick);
        player.displayName(Component.text(nick));
    }

    private void applyClear(PlayerRef who) {
        Player player = Bukkit.getPlayer(who.uuid());
        if (player == null) {
            return;
        }
        player.getPersistentDataContainer().remove(nickKey);
        player.displayName(Component.text(player.getName()));
    }
}

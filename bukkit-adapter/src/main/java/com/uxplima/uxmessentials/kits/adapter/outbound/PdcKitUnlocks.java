package com.uxplima.uxmessentials.kits.adapter.outbound;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.kits.application.port.KitUnlockStore;
import com.uxplima.uxmessentials.kits.domain.KitId;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.item.PdcFlag;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link KitUnlockStore} implementation: a per-player buy-to-unlock flag stamped in PDC. Each kit a player
 * has bought the one-time unlock for is recorded as a {@code (byte) 1} under a per-kit key, so a later claim of
 * an {@code unlock-once} kit reads the mark and is granted free. An unlock is transient per-holder state that
 * may safely die with a world rollback (docs/03-paper-api §3.6), which is why it lives in PDC rather than the
 * database: mirroring the one-time-claim store {@link PdcKitClaims}.
 *
 * <h2>Concurrency</h2>
 * The per-kit {@link NamespacedKey}s are cached in a {@link ConcurrentHashMap} populated via
 * {@code computeIfAbsent}, so each key is built only once and never on a hot path. PDC reads and writes happen
 * on the player's region thread (the claim command thread), where Paper allows them. An offline player has no
 * PDC, so {@link #hasUnlocked} reads {@code false} and {@link #markUnlocked} no-ops.
 */
@NullMarked
public final class PdcKitUnlocks implements KitUnlockStore {

    private static final String UNLOCK_PREFIX = "kit-unlocked-";

    private final Plugin plugin;
    private final ConcurrentHashMap<String, NamespacedKey> keys = new ConcurrentHashMap<>();

    public PdcKitUnlocks(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    @Override
    public boolean hasUnlocked(PlayerRef who, KitId kit) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(kit, "kit");
        Player player = Bukkit.getPlayer(who.uuid());
        if (player == null) {
            return false;
        }
        return PdcFlag.has(player.getPersistentDataContainer(), keyFor(kit));
    }

    @Override
    public void markUnlocked(PlayerRef who, KitId kit) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(kit, "kit");
        Player player = Bukkit.getPlayer(who.uuid());
        if (player == null) {
            return;
        }
        PdcFlag.set(player.getPersistentDataContainer(), keyFor(kit), true);
    }

    private NamespacedKey keyFor(KitId kit) {
        return keys.computeIfAbsent(kit.value(), id -> new NamespacedKey(plugin, UNLOCK_PREFIX + sanitize(id)));
    }

    /** A NamespacedKey value segment accepts only {@code [a-z0-9._-]}; fold anything else to {@code _}. */
    private static String sanitize(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            boolean legal = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.' || c == '_' || c == '-';
            out.append(legal ? c : '_');
        }
        return out.toString();
    }
}

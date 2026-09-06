package com.uxplima.uxmessentials.vanish.adapter.outbound;

import java.util.Objects;
import java.util.OptionalInt;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vanish.application.port.VanishLevelResolver;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;
import com.uxplima.uxmessentials.vanish.domain.VanishLevels;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The Bukkit-backed {@link VanishLevelResolver}: it reads a player's PremiumVanish-style see and use levels from their
 * effective permissions. A numbered node {@code uxmessentials.vanish.use.level<N>} / {@code .see.level<N>} contributes
 * level {@code N}; the highest such node a player holds wins. The plain {@code .use} / {@code .see} node (no number)
 * counts as level 1, so with layered permissions off every vanished player is use level 1 and every {@code .see}
 * holder is see level 1, the flat behaviour. A player with no see node at all resolves to
 * {@link VanishLevels#NO_SEE_LEVEL} (0) and sees no vanished player.
 *
 * <p>Op and wildcard grants ({@code *}) are handled by probing an impossibly-high level node: only op or a wildcard
 * covering the family answers {@code true} for {@code base.level<probe>}, since an explicit numbered grant never covers
 * a level it does not name. Such a player resolves to {@link #WILDCARD_LEVEL}, high enough to clear every realistic
 * use level (an op sees every vanished player) and, for their own use level, to hide from every non-op viewer.
 *
 * <p>Numbered nodes are read from {@code getEffectivePermissions}, the same enumeration {@code BukkitPermissions} uses,
 * so LuckPerms-published nodes fold in for free. An offline player cannot be permission-checked, so use level falls
 * back to {@link VanishLevel#MIN_LEVEL} (a vanished player is always at least level 1) and see level to
 * {@code NO_SEE_LEVEL}, the conservative default.
 */
@NullMarked
public final class BukkitVanishLevelResolver implements VanishLevelResolver {

    private static final String USE_BASE = "uxmessentials.vanish.use";
    private static final String SEE_BASE = "uxmessentials.vanish.see";
    private static final String LEVEL_SEGMENT = ".level";

    /** A level nobody grants explicitly; only op or a family wildcard makes {@code base.level<probe>} resolve true. */
    private static final int WILDCARD_PROBE = 1_000_000;

    /** The level an op / wildcard holder resolves to: above every realistic numbered grant. */
    private static final int WILDCARD_LEVEL = 100_000;

    @Override
    public VanishLevel useLevel(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        return VanishLevel.of(resolve(who, USE_BASE, VanishLevel.MIN_LEVEL));
    }

    @Override
    public int seeLevel(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        return resolve(who, SEE_BASE, VanishLevels.NO_SEE_LEVEL);
    }

    private int resolve(PlayerRef who, String base, int noNodeDefault) {
        Player player = Bukkit.getPlayer(who.uuid());
        if (player == null) {
            return noNodeDefault;
        }
        if (player.hasPermission(base + LEVEL_SEGMENT + WILDCARD_PROBE)) {
            return WILDCARD_LEVEL;
        }
        int numbered = highestNumberedLevel(player, base + LEVEL_SEGMENT);
        if (numbered > 0) {
            return numbered;
        }
        return player.hasPermission(base) ? VanishLevel.MIN_LEVEL : noNodeDefault;
    }

    private static int highestNumberedLevel(Player player, String prefix) {
        int best = 0;
        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            if (!info.getValue()) {
                continue;
            }
            String node = info.getPermission();
            if (!node.startsWith(prefix)) {
                continue;
            }
            OptionalInt level = positiveSuffix(node.substring(prefix.length()));
            if (level.isPresent()) {
                best = Math.max(best, level.getAsInt());
            }
        }
        return best;
    }

    /** The positive integer a numbered node's trailing segment carries, or empty when it is not a plain number. */
    private static OptionalInt positiveSuffix(@Nullable String tail) {
        if (tail == null || tail.isEmpty()) {
            return OptionalInt.empty();
        }
        for (int i = 0; i < tail.length(); i++) {
            if (!Character.isDigit(tail.charAt(i))) {
                return OptionalInt.empty();
            }
        }
        try {
            int value = Integer.parseInt(tail);
            return value > 0 ? OptionalInt.of(value) : OptionalInt.empty();
        } catch (NumberFormatException notAnInt) {
            return OptionalInt.empty();
        }
    }
}

package com.uxplima.uxmessentials.vanish.domain;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * An immutable snapshot of who is vanished and at which {@link VanishLevel}. The vanish authority (the
 * {@code VanishStore} adapter) holds the live {@code ConcurrentHashMap<UUID, VanishLevel>}; this record is the pure
 * value a reader takes of it so the visibility rule can be evaluated without touching the mutable map or any Bukkit
 * type. The map holds only the currently-vanished players, so a snapshot is small (usually empty) and cheap to copy.
 *
 * <p>The visibility rule lives here as {@link #canSee}: a viewer sees a vanished target only when the viewer's
 * <em>see level</em> is at least the target's <em>use level</em> ({@link VanishLevels}). Both levels are resolved from
 * permissions by the adapter (it is not a domain concern) and passed in. The viewer's see level as the argument, the
 * target's use level read from this state. The simple case still holds: with layered permissions off a viewer with the
 * plain {@code .see} node resolves to see level 1 and every vanished player is use level 1, so {@code 1 >= 1} reveals;
 * a viewer with no see node resolves to see level 0, so {@code 0 >= 1} hides.
 *
 * @param vanished the currently-vanished players keyed by uuid, each at their level
 */
public record VanishState(Map<UUID, VanishLevel> vanished) {

    private static final VanishState EMPTY = new VanishState(Map.of());

    public VanishState {
        Objects.requireNonNull(vanished, "vanished");
        vanished = Map.copyOf(vanished);
    }

    /** The state with nobody vanished. */
    public static VanishState empty() {
        return EMPTY;
    }

    /** Whether {@code target} is currently vanished. */
    public boolean isVanished(UUID target) {
        Objects.requireNonNull(target, "target");
        return vanished.containsKey(target);
    }

    /** The level {@code target} is vanished at, or empty when they are not vanished. */
    public Optional<VanishLevel> levelOf(UUID target) {
        Objects.requireNonNull(target, "target");
        return Optional.ofNullable(vanished.get(target));
    }

    /** The set of currently-vanished players. */
    public Set<UUID> vanishedIds() {
        return vanished.keySet();
    }

    /** This state with {@code target} vanished at {@code level} (replacing any prior level). */
    public VanishState withVanished(UUID target, VanishLevel level) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(level, "level");
        java.util.Map<UUID, VanishLevel> next = new java.util.HashMap<>(vanished);
        next.put(target, level);
        return new VanishState(next);
    }

    /** This state with {@code target} revealed. */
    public VanishState withoutVanished(UUID target) {
        Objects.requireNonNull(target, "target");
        if (!vanished.containsKey(target)) {
            return this;
        }
        java.util.Map<UUID, VanishLevel> next = new java.util.HashMap<>(vanished);
        next.remove(target);
        return new VanishState(next);
    }

    /**
     * Whether {@code viewer} may see {@code target}. A player always sees themselves; a non-vanished target is seen
     * by everyone; a vanished target is seen only when {@code viewerSeeLevel} is at least the target's use level
     * (the {@link VanishLevels#sees} rule). {@code viewerSeeLevel} is the viewer's see level as resolved from their
     * permissions by the adapter; {@link VanishLevels#NO_SEE_LEVEL} (0) means "no vanish-see permission at all".
     */
    public boolean canSee(UUID viewer, UUID target, int viewerSeeLevel) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(target, "target");
        if (viewer.equals(target)) {
            return true;
        }
        VanishLevel targetUseLevel = vanished.get(target);
        if (targetUseLevel == null) {
            return true;
        }
        return VanishLevels.sees(viewerSeeLevel, targetUseLevel);
    }
}

package com.uxplima.uxmessentials.playerstate.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * The immutable per-player state the playerstate context owns and reconciles with the live Bukkit player:
 * the god (damage-immunity) and fly toggles, an optional pinned game mode, and the walk/fly speeds. Held in a
 * {@code ConcurrentHashMap<UUID, PlayerStateSnapshot>} and only ever replaced wholesale through {@code compute}
 *. Every mutator here returns a new snapshot rather than changing this one, so a reader on another thread
 * always sees a coherent value (docs/02-concurrency §the playerstate snapshot pattern).
 *
 * <p>Heal and feed are apply-once effects and leave no flag here. They act on the live player and are not
 * part of the persisted toggle state. The game mode is modelled as an {@link Optional} because most players
 * have no pinned mode (the context does not force one); a present value is what the reconciler re-applies on
 * respawn or relog.
 *
 * @param god whether the player is damage-immune ({@code /god})
 * @param fly whether flight is allowed ({@code /fly})
 * @param gameMode the pinned game mode, or empty when the context pins none
 * @param walkSpeed the walk speed on the operator scale
 * @param flySpeed the fly speed on the operator scale
 */
public record PlayerStateSnapshot(
        boolean god, boolean fly, Optional<GameModeRef> gameMode, SpeedValue walkSpeed, SpeedValue flySpeed) {

    public PlayerStateSnapshot {
        Objects.requireNonNull(gameMode, "gameMode");
        Objects.requireNonNull(walkSpeed, "walkSpeed");
        Objects.requireNonNull(flySpeed, "flySpeed");
    }

    /** The neutral starting state: no god, no fly, no pinned mode, default walk/fly speeds. */
    public static PlayerStateSnapshot initial() {
        return new PlayerStateSnapshot(false, false, Optional.empty(), SpeedValue.DEFAULT_WALK, SpeedValue.DEFAULT_FLY);
    }

    /** This state with god flipped to its opposite. */
    public PlayerStateSnapshot toggleGod() {
        return new PlayerStateSnapshot(!god, fly, gameMode, walkSpeed, flySpeed);
    }

    /** This state with fly flipped to its opposite. */
    public PlayerStateSnapshot toggleFly() {
        return new PlayerStateSnapshot(god, !fly, gameMode, walkSpeed, flySpeed);
    }

    /** This state with god set explicitly (used when a target's flag is set rather than toggled). */
    public PlayerStateSnapshot withGod(boolean value) {
        return new PlayerStateSnapshot(value, fly, gameMode, walkSpeed, flySpeed);
    }

    /** This state with fly set explicitly. */
    public PlayerStateSnapshot withFly(boolean value) {
        return new PlayerStateSnapshot(god, value, gameMode, walkSpeed, flySpeed);
    }

    /** This state pinned to {@code mode}. */
    public PlayerStateSnapshot withGameMode(GameModeRef mode) {
        return new PlayerStateSnapshot(
                god, fly, Optional.of(Objects.requireNonNull(mode, "mode")), walkSpeed, flySpeed);
    }

    /** This state with the walk speed replaced. */
    public PlayerStateSnapshot withWalkSpeed(SpeedValue value) {
        return new PlayerStateSnapshot(god, fly, gameMode, Objects.requireNonNull(value, "value"), flySpeed);
    }

    /** This state with the fly speed replaced. */
    public PlayerStateSnapshot withFlySpeed(SpeedValue value) {
        return new PlayerStateSnapshot(god, fly, gameMode, walkSpeed, Objects.requireNonNull(value, "value"));
    }
}

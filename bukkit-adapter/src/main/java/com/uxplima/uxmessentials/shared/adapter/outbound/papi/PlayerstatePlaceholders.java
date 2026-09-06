package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.time.Duration;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Read seam the expansion queries for the {@code playerstate_*} placeholders. It is an adapter over the
 * playerstate context's in-memory snapshot store (for the god toggle) and the live Bukkit player (for the
 * client-side state. Gamemode, flight, speed, health, food, experience, position, world and biome) wired
 * during bootstrap; when the playerstate module is disabled the seam is absent and the placeholders degrade.
 *
 * <p>Every placeholder reads live session state, so an offline player has nothing to report: the seam
 * returns an empty snapshot and the expansion renders the dash for it. The single read returns one immutable
 * {@link Snapshot} so a placeholder line that mixes several fields sees a coherent picture.
 */
public interface PlayerstatePlaceholders {

    /** A point-in-time view of {@code who}'s live state, or empty when they are offline. */
    Optional<Snapshot> snapshot(PlayerRef who);

    /**
     * The immutable player-state facts the placeholders read off the live player and the playerstate store.
     *
     * @param gamemode the lowercase game-mode name ({@code survival}, {@code creative}, ...)
     * @param flightAllowed whether flight is permitted ({@code allowFlight})
     * @param flying whether the player is currently airborne under flight ({@code isFlying})
     * @param god whether the player is damage-immune ({@code /god})
     * @param walkSpeed the live walk speed on the {@code -1..1} Bukkit scale
     * @param flySpeed the live fly speed on the {@code -1..1} Bukkit scale
     * @param health the player's current health
     * @param maxHealth the player's maximum health
     * @param food the player's food level
     * @param level the player's experience level
     * @param experienceProgress the fraction toward the next level ({@code 0..1})
     * @param world the name of the world the player is in
     * @param blockX the player's block x coordinate
     * @param blockY the player's block y coordinate
     * @param blockZ the player's block z coordinate
     * @param biome the lowercase biome key at the player's position ({@code plains}, {@code desert}, ...)
     * @param playtime the player's total time played
     */
    record Snapshot(
            String gamemode,
            boolean flightAllowed,
            boolean flying,
            boolean god,
            float walkSpeed,
            float flySpeed,
            double health,
            double maxHealth,
            int food,
            int level,
            float experienceProgress,
            String world,
            int blockX,
            int blockY,
            int blockZ,
            String biome,
            Duration playtime) {

        public Snapshot {
            java.util.Objects.requireNonNull(gamemode, "gamemode");
            java.util.Objects.requireNonNull(world, "world");
            java.util.Objects.requireNonNull(biome, "biome");
            java.util.Objects.requireNonNull(playtime, "playtime");
        }
    }
}

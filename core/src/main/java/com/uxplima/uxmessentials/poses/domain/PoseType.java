package com.uxplima.uxmessentials.poses.domain;

/**
 * The kinds of pose a player can hold, the full GSit-parity surface.
 *
 * <p>{@link #SIT} is sitting on a block or in place; {@link #PLAYER_SIT} is sitting on another player's shoulders
 * (the stacking pose, which is the only type that carries a {@code target}). {@link #LAY}, {@link #BELLYFLOP} and
 * {@link #SPIN} are the free poses, and {@link #CRAWL} is the client-side prone crawl. The value is stable: it
 * keys the per-player {@link PoseSession} and the placeholder surface, so a new pose is added at the end rather
 * than reordered.
 */
public enum PoseType {

    /** Sitting on a block or on the spot. */
    SIT,

    /** Sitting on another player (the stacking pose); the session carries the carrier as its target. */
    PLAYER_SIT,

    /** Lying on the back. */
    LAY,

    /** Lying face-down. */
    BELLYFLOP,

    /** Sitting and spinning in place. */
    SPIN,

    /** Crawling prone through a one-block gap. */
    CRAWL
}

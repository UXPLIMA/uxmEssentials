package com.uxplima.uxmessentials.teleport.domain;

/**
 * The kinds of target a {@link RespawnStep} can name in a per-world respawn chain. The chain is walked
 * in order and the first step that resolves to a valid location wins, so an operator composes respawn
 * behaviour declaratively (e.g. {@code tpr;spawn;home;bed;warp:hub;anchor}).
 *
 * <p>This maps the older "death management" vocabulary into the project's ubiquitous language:
 * the chain noun is {@code RespawnChain}, never "death management"; the legacy term is only the thing
 * we map <em>from</em>.
 */
public enum RespawnStepKind {

    /** Random teleport via the urgent queue path ({@code tpr}). */
    RANDOM,

    /** The resolved (possibly mirrored) world spawn ({@code spawn}). */
    SPAWN,

    /** The player's home, the default home for the world ({@code home}). */
    HOME,

    /** The player's last slept-in bed / respawn anchor as vanilla would ({@code bed}). */
    BED,

    /** A named warp; the {@link RespawnStep#argument()} carries the warp name ({@code warp:hub}). */
    WARP,

    /** A respawn anchor block in the world ({@code anchor}). */
    ANCHOR
}

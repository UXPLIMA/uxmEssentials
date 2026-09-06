package com.uxplima.uxmessentials.security.domain;

/**
 * How the client-brand guard treats a joining player's reported client brand (the {@code minecraft:brand} plugin
 * message, {@code "vanilla"}, {@code "fabric"}, a known cheat client, …). The mode chooses which side of the
 * configured brand list is the allowed side, or turns enforcement off entirely.
 *
 * <ul>
 *   <li>{@link #BLOCK_LIST}: the listed brands are denied; anything else is allowed. Use it to keep known cheat
 *       clients out.
 *   <li>{@link #ALLOW_LIST}: only the listed brands are allowed; anything else is denied. Use it to admit a fixed
 *       set of approved clients and reject the rest.
 *   <li>{@link #FLAG}. Nothing is ever denied; a brand on the list is flagged for staff (logged/notified) but the
 *       player still joins. Use it to observe before enforcing.
 * </ul>
 */
public enum ClientIdMode {

    /** Deny the listed brands; allow everything else. */
    BLOCK_LIST,

    /** Allow only the listed brands; deny everything else. */
    ALLOW_LIST,

    /** Never deny; flag a listed brand for staff and let the player join. */
    FLAG
}

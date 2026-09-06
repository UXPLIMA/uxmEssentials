/**
 * The worlds context's outbound ports: {@code WorldRepository} for durable world-metadata storage,
 * {@code WorldEngine}. The anti-corruption layer over Bukkit's world APIs and the world folder on
 * disk, and {@code PendingDeletionRegistry} for the short-lived staging of delete confirmations. The
 * application depends only on these interfaces; the jOOQ repository, the {@code WorldCreator}-driven
 * engine, and the in-memory confirmation registry implement them in the persistence and bukkit adapters.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.worlds.application.port;

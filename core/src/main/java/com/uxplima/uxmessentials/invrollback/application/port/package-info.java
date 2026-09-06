/**
 * The invrollback context's outbound ports.
 * {@link com.uxplima.uxmessentials.invrollback.application.port.SnapshotRepository} is durable, DB-backed
 * storage for inventory snapshots. A snapshot survives a world rollback (never PDC), the same hard invariant
 * the economy and vaults ledgers hold. The jOOQ adapter implements it in the persistence module; the use cases
 * depend only on the contract. Pure Java: no Bukkit, Paper, Kyori, jOOQ, or SLF4J.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.invrollback.application.port;

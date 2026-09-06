/**
 * Pure domain of the vanish bounded context: the immutable {@code VanishState} snapshot of who is vanished (keyed by
 * uuid, each at a {@code VanishLevel}) with the pure {@code canSee(viewer, target, viewerHasSee)} visibility rule, and
 * the {@code VanishLevel} tier the Phase-2 see/use layering builds on. Vanish is transient runtime state, the live
 * authority is an in-memory {@code ConcurrentHashMap<UUID, VanishLevel>} in the adapter, re-derived on join and never
 * persisted, and this package is the value a reader takes of it. No Bukkit, Paper, Kyori, or logging type appears
 * here; the model is built from value objects and the cross-cutting kernel primitives.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.vanish.domain;

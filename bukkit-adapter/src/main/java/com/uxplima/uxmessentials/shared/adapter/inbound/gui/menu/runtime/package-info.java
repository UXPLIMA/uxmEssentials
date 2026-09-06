/**
 * The Bukkit-facing runtime for the menu engine: the per-open context carried into bindings, the inventory
 * holder that owns all per-open state, and the click listener that drives them. Unlike {@code spec}/{@code eval},
 * this package may touch {@code org.bukkit}: it is where the pure model meets a live player and inventory.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime;

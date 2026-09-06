/**
 * The pure, Bukkit-free type model for the data-driven menu engine. These records mirror the shape of a menu's
 * HOCON spec (references, slot sets, item decoration, click handling, and the menu itself) so the loader can
 * parse a spec into immutable values that the renderer and runtime consume without ever touching the file
 * again. Nothing here imports {@code org.bukkit} or {@code net.minecraft}; that boundary is enforced so the
 * model stays unit-testable in isolation.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec;

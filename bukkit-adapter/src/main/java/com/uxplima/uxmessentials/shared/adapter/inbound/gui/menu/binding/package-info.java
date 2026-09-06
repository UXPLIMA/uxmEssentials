/**
 * The bridge between a menu's HOCON spec and the code that gives it behaviour. A spec only names behaviour by id
 * ({@code "close"}, {@code "warp:teleport"}, {@code "%balance%"}) and each feature registers the matching
 * handler once through the {@link
 * com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings} façade. The registries
 * kept here hold those handlers by id and let the engine resolve a ref at render or click time, while {@code validate}
 * lets startup reject a spec that names an id nobody registered before a player ever opens it.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding;

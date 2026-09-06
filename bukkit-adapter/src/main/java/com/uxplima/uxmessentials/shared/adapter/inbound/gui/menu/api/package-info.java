/**
 * The menu engine's public developer API: the stable surface another plugin compiles against to extend the engine.
 *
 * <p>{@link com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.api.MenuApi} is the registration façade a
 * plugin loads from the Bukkit {@link org.bukkit.plugin.ServicesManager} to teach the engine custom actions
 * (which cover custom buttons), requirements, placeholders and list sources, and to build a menu-styled item. The
 * {@code event} subpackage carries the cancellable open/click events a plugin listens to. Everything here is a
 * contract other plugins depend on, so a change is a breaking change, evolve additively.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.api;

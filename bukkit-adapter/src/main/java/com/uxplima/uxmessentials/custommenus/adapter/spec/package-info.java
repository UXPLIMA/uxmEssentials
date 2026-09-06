/**
 * The {@code custommenus} spec-service layer: the UI-free half of the in-game menu editor. {@code MenuSpecWriter}
 * serializes a parsed {@code MenuSpec} (and a menu's {@code command {}} block) back into HOCON, the exact inverse of
 * {@code MenuSpecLoader} plus {@code CustomMenuLoader.parseOpenCommand}, and {@code MenuSpecPersistence} validates a
 * spec against the registered {@code MenuBindings} and writes it to {@code menus/<name>.conf}. Both are Bukkit-free,
 * so an edit can be modelled, validated, and written back without ever touching an inventory.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.custommenus.adapter.spec;

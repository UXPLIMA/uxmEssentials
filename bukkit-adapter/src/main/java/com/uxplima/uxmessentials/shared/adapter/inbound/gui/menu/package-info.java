/**
 * The public surface of the data-driven menu engine. Outside this package tree only {@code Menus} (open a spec
 * for a viewer) and the {@code binding} façade (give a spec its behaviour) are referenced; the render and runtime
 * internals stay package-private to their own subpackages. A feature registers handlers through the façade and
 * opens a registered spec through {@link com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus}
 * it never names a render or runtime class directly.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu;

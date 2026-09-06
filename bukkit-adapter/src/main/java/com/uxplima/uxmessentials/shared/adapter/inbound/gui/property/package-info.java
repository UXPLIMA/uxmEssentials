/**
 * The reusable value editors of the shared management-GUI framework. An
 * {@link com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.EditableProperty} describes one button
 * in an {@link com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityEditorView}, its label, its
 * current-value lore, its icon, and what a click does, and the framework draws and routes it. The supplied
 * implementations cover the common edits: {@code ToggleProperty} cycles a boolean or small enum,
 * {@code TextProperty} captures a line through an anvil, {@code NumberProperty} steps an integer within
 * bounds, {@code EnumProperty} opens a selector sub-menu, and {@code ListProperty} edits a list of string
 * entries in its own sub-menu. Each takes a caller-supplied setter (the module's existing application use
 * case) and performs no domain logic itself; writes go off the tick thread through the shared
 * {@code Scheduler}, destructive steps are gated by a confirm, and every label and value resolves through the
 * message catalog.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.shared.adapter.inbound.gui.property;

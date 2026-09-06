/**
 * The custommenus context's inbound GUI layer: the {@code /menu editor} menu picker. A paginated engine list of the
 * loaded custom menus with create / duplicate / rename / delete and save, built entirely on the shared menu engine
 * (the {@code Menus} façade, {@code EntityListView} / {@code EntityEditorView}, {@code TextInput}) over the
 * {@code MenuEditorService} file CRUD, never a raw Bukkit inventory.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.custommenus.adapter.inbound.gui;

/**
 * The npc context's management GUI: a config-driven list of every stored NPC ({@link
 * com.uxplima.uxmessentials.npc.adapter.inbound.gui.NpcListMenu}, drawn through the shared menu engine) opening a
 * per-NPC property editor ({@link com.uxplima.uxmessentials.npc.adapter.inbound.gui.NpcEditorView}). Both write
 * every change through the existing npc application use cases. The GUI adds no domain logic, it is a thin inbound
 * adapter over the same use cases the {@code /npc} subcommands call. The list geometry lives in the engine spec
 * {@code modules/npc/gui/npc-list.conf}; the editor geometry and materials come from
 * {@code modules/npc/gui/npc-editor.conf}; all text from the {@code NpcMessageKey} catalog.
 */
@NullMarked
package com.uxplima.uxmessentials.npc.adapter.inbound.gui;

import org.jspecify.annotations.NullMarked;

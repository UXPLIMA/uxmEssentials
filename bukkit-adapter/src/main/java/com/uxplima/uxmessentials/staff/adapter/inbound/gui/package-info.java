/**
 * The staff context's inbound GUI, rendered through the shared menu engine: the
 * {@link com.uxplima.uxmessentials.staff.adapter.inbound.gui.StaffPlayerMenu} teleport pickers (the COMPASS
 * navigator and {@code /stafflist}) and the
 * {@link com.uxplima.uxmessentials.staff.adapter.inbound.gui.StaffExamineMenu} examine picker: paginated grids of
 * player heads that, on click, admin-teleport to or open the inventory of the clicked player. The candidate roster
 * is computed on the global region thread at the open site and passed in as the menu subject.
 */
@NullMarked
package com.uxplima.uxmessentials.staff.adapter.inbound.gui;

import org.jspecify.annotations.NullMarked;

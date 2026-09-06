/**
 * The staff context's outbound adapters: the Bukkit loadout capture/restore + gadget-hotbar swap
 * ({@link com.uxplima.uxmessentials.staff.adapter.outbound.BukkitStaffLoadoutCapture}, the only staff class
 * that touches a live player or item), the potion-effect codec, the in-memory active-staff store, and the three
 * soft-couple impls bridging to presence (vanish), playerstate (inspect), and messaging (staff chat), each
 * bound only when its source module is enabled.
 */
@NullMarked
package com.uxplima.uxmessentials.staff.adapter.outbound;

import org.jspecify.annotations.NullMarked;

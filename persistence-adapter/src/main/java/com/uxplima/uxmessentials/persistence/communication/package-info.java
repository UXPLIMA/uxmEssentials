/**
 * The communication context's persistence adapter: the jOOQ {@code AnnouncementStore} over the generated
 * {@code COMMUNICATION_ANNOUNCEMENT} table, the {@code AnnouncementRows} anti-corruption mapping, and the
 * {@code AnnouncementStores} factory the bukkit-adapter wires through. This is the editor-managed announcement set
 * the rotating announcer merges these (the enabled ones) with the file-managed {@code announcer.conf} set. The
 * lines are newline-joined into one TEXT cell and the channels a comma-separated list, since an announcement's
 * lines are always delivered together and never indexed individually.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.persistence.communication;

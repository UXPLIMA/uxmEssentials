/**
 * The teleport context's own outbound ports. The driven interfaces its use cases need beyond the
 * shared kernel ports. They cover the {@code /back} capture store, the spawn directory, the pre-warmed
 * RTP queue, the teleport executor that issues the region-aware async hop, the pending-request
 * registry, the per-player toggle/block flags, and the typed {@code teleport.conf} view. The
 * bukkit-adapter and persistence-adapter implement them; the application depends only on these
 * contracts, never on Bukkit.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.teleport.application.port;

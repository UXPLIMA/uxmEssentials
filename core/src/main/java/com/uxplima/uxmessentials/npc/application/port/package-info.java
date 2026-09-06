/**
 * The npc context's outbound ports: {@code NpcRepository} (durable, server-wide, name-keyed NPC storage with
 * every fact a first-class column) and {@code NpcView} (the render/despawn seam the use cases drive to keep the
 * in-world fake players in step with the stored model). Both are interfaces only: the adapters implement them.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.npc.application.port;

package com.uxplima.uxmessentials.npc.adapter.inbound.listener;

import java.util.Objects;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import com.uxplima.uxmessentials.npc.adapter.outbound.NpcRenderer;
import org.jspecify.annotations.NullMarked;

/**
 * Keeps each viewer's set of shown NPCs honest across connection lifecycle. On join the renderer shows every
 * in-range NPC to the joiner; on quit it forgets the viewer (dropping its shown-set so nothing leaks, the
 * client is already gone, so no remove packet is needed); on a world change it re-evaluates every NPC for the
 * mover so the ones in the new world appear and the ones left behind are removed. The renderer routes each
 * send onto the viewer's region thread, so this listener only needs to fan the events through.
 *
 * <p>Look-at-player runs on the renderer's own look loop and click interaction has its own listener; this one
 * owns only the spawn/forget lifecycle of the renderer's per-viewer tracking.
 */
@NullMarked
public final class NpcLifecycleListener implements Listener {

    private final NpcRenderer renderer;

    public NpcLifecycleListener(NpcRenderer renderer) {
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        renderer.showAllTo(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        renderer.forget(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        renderer.showAllTo(event.getPlayer());
    }
}

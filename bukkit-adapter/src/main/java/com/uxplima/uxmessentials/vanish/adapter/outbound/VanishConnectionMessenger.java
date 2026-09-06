package com.uxplima.uxmessentials.vanish.adapter.outbound;

import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vanish.application.VanishConfig;
import com.uxplima.uxmessentials.vanish.application.port.VanishLevelResolver;
import com.uxplima.uxmessentials.vanish.domain.VanishLevel;
import com.uxplima.uxmessentials.vanish.domain.VanishLevels;
import org.jspecify.annotations.NullMarked;

/**
 * Broadcasts the fake connection lines that sell the vanish illusion: a "left the game" line when a player vanishes and
 * a "joined the game" line when they reappear, so no viewer notices they merely turned invisible. The real join/quit
 * lines of a vanished player are suppressed by the {@code VanishLifecycleListener}; this class supplies the fake ones a
 * {@code /vanish} transition emits in their place.
 *
 * <p>The lines are operator MiniMessage content from {@link VanishConfig} ({@code {player}} substituted), never a
 * {@code MessageKey}: the same treatment the connection-message context gives real join/quit templates. Each viewer's
 * variant is chosen by whether they can see the vanishing player: a viewer whose see level clears the player's use
 * level (staff) gets the {@code *-staff} template, everyone else gets the public one; a blank template sends that group
 * nothing. The whole broadcast is a no-op when {@code fake-join-quit} is off.
 *
 * <p>The online roster is enumerated on the global region thread (Folia forbids reading {@code getOnlinePlayers()} off
 * it), and delivery to each viewer hops to their region thread inside the {@link MessageSink}. The vanishing player
 * themselves is skipped: they already know, and get their own {@code /vanish} confirmation.
 */
@NullMarked
public final class VanishConnectionMessenger {

    private static final String PLAYER_TOKEN = "{player}";

    private final Scheduler scheduler;
    private final MessageSink sink;
    private final VanishLevelResolver levels;
    private final VanishConfig config;

    public VanishConnectionMessenger(
            Scheduler scheduler, MessageSink sink, VanishLevelResolver levels, VanishConfig config) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.levels = Objects.requireNonNull(levels, "levels");
        this.config = Objects.requireNonNull(config, "config");
    }

    /** Broadcast the fake quit line when {@code who} vanishes. */
    public void announceVanish(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        broadcast(who, config.fakeQuitMessage(), config.fakeQuitMessageStaff());
    }

    /** Broadcast the fake join line when {@code who} reappears. */
    public void announceReappear(PlayerRef who) {
        Objects.requireNonNull(who, "who");
        broadcast(who, config.fakeJoinMessage(), config.fakeJoinMessageStaff());
    }

    private void broadcast(PlayerRef who, String publicTemplate, String staffTemplate) {
        if (!config.fakeJoinQuit()) {
            return;
        }
        VanishLevel useLevel = levels.useLevel(who);
        String toOthers = publicTemplate.replace(PLAYER_TOKEN, who.name());
        String toStaff = staffTemplate.replace(PLAYER_TOKEN, who.name());
        scheduler.onGlobal(() -> {
            for (Player online : Bukkit.getOnlinePlayers()) {
                PlayerRef viewer = BukkitRefs.toRef(online);
                if (viewer.equals(who)) {
                    continue;
                }
                boolean canSee = VanishLevels.sees(levels.seeLevel(viewer), useLevel);
                String line = canSee ? toStaff : toOthers;
                if (!line.isBlank()) {
                    sink.deliver(viewer, line);
                }
            }
        });
    }
}

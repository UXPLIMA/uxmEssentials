package com.uxplima.uxmessentials.staff.adapter.inbound.listener;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.bukkit.Server;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.staff.adapter.StaffGadget;
import com.uxplima.uxmessentials.staff.adapter.inbound.gui.StaffExamineMenu;
import com.uxplima.uxmessentials.staff.adapter.inbound.gui.StaffPlayerMenu;
import com.uxplima.uxmessentials.staff.adapter.outbound.StaffFollowService;
import com.uxplima.uxmessentials.staff.application.StaffMessageKey;
import com.uxplima.uxmessentials.staff.application.port.StaffFreeze;
import com.uxplima.uxmessentials.staff.application.port.StaffTeleport;
import com.uxplima.uxmessentials.staff.application.port.StaffVanish;
import org.jspecify.annotations.NullMarked;

/**
 * The gadget action table, split out of {@link StaffModeListener} so the listener stays focused on the Bukkit
 * event plumbing (resolve gadget, cancel, route) and this owns what each gadget does. Two entry points:
 * {@link #onAir} for an interact that hit no player (VANISH/EXAMINE/COMPASS act, FREEZE/FOLLOW report "look at a
 * player"), and {@link #onPlayer} for a right-click landing on a player entity (FREEZE/FOLLOW/COMPASS act,
 * VANISH/EXAMINE fall back to their air behaviour). Each soft-coupled port degrades on its {@code NONE} binding.
 *
 * <p>The FREEZE gadget delegates straight to the moderation freeze use case and shows no line of its own: that
 * use case already confirms to the actor (and tells an exempt target it cannot be frozen), exactly as
 * {@code /freeze} does, so the gadget would otherwise double-notify.
 */
@NullMarked
public final class StaffGadgetActions {

    private final StaffVanish vanish;
    private final StaffFreeze freeze;
    private final StaffTeleport teleport;
    private final StaffFollowService follow;
    private final StaffExamineMenu examineMenu;
    private final StaffPlayerMenu playerMenu;
    private final Scheduler scheduler;
    private final Server server;
    private final Notifier notifier;

    public StaffGadgetActions(
            StaffVanish vanish,
            StaffFreeze freeze,
            StaffTeleport teleport,
            StaffFollowService follow,
            StaffExamineMenu examineMenu,
            StaffPlayerMenu playerMenu,
            Scheduler scheduler,
            Server server,
            Notifier notifier) {
        this.vanish = Objects.requireNonNull(vanish, "vanish");
        this.freeze = Objects.requireNonNull(freeze, "freeze");
        this.teleport = Objects.requireNonNull(teleport, "teleport");
        this.follow = Objects.requireNonNull(follow, "follow");
        this.examineMenu = Objects.requireNonNull(examineMenu, "examineMenu");
        this.playerMenu = Objects.requireNonNull(playerMenu, "playerMenu");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.server = Objects.requireNonNull(server, "server");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** A gadget used while not looking at a player. */
    void onAir(StaffGadget gadget, Player player, PlayerRef who) {
        switch (gadget) {
            case VANISH -> vanish.setVanished(who, !player.isInvisible());
            case EXAMINE -> openExamine(who);
            case COMPASS -> openNavigator(player, who);
            case FREEZE, FOLLOW -> notifier.send(who, StaffMessageKey.STAFF_GADGET_NO_TARGET);
        }
    }

    /**
     * Open the COMPASS navigator: snapshot the visible online roster on the global region thread (iterating the
     * online list off it is illegal on Folia, and {@code canSee} is the looker's vanish-aware visibility), then hand
     * it to the menu engine, which builds and opens on the looker's entity thread. Mirrors the old picker's open.
     */
    private void openNavigator(Player looker, PlayerRef who) {
        scheduler.onGlobal(() -> {
            List<PlayerRef> roster = server.getOnlinePlayers().stream()
                    .filter(online -> !online.getUniqueId().equals(looker.getUniqueId()))
                    .filter(looker::canSee)
                    .map(BukkitRefs::toRef)
                    .collect(Collectors.toList());
            playerMenu.openNavigator(who, roster);
        });
    }

    /** A gadget right-clicked directly onto {@code targetPlayer}. */
    void onPlayer(StaffGadget gadget, Player actor, PlayerRef who, Player targetPlayer, PlayerRef targetRef) {
        switch (gadget) {
            case FREEZE -> freeze.toggle(who, targetRef);
            case FOLLOW -> follow(actor, who, targetPlayer);
            case COMPASS -> teleport(who, targetPlayer, targetRef);
            case VANISH -> vanish.setVanished(who, !actor.isInvisible());
            case EXAMINE -> openExamine(who);
        }
    }

    /**
     * Open the EXAMINE picker: snapshot the online roster on the global region thread (iterating the online list off
     * it is illegal on Folia), then hand it to the menu engine, which builds and opens on the looker's entity
     * thread. The picker lists every online player, as the old view did. Mirrors the old picker's open.
     */
    private void openExamine(PlayerRef who) {
        scheduler.onGlobal(() -> {
            List<PlayerRef> roster =
                    server.getOnlinePlayers().stream().map(BukkitRefs::toRef).collect(Collectors.toList());
            examineMenu.open(who, roster);
        });
    }

    private void follow(Player actor, PlayerRef who, Player targetPlayer) {
        if (actor.getUniqueId().equals(targetPlayer.getUniqueId())) {
            return; // a staff member cannot follow themselves; start nothing and say nothing
        }
        boolean started = follow.toggle(actor, targetPlayer);
        StaffMessageKey key = started ? StaffMessageKey.STAFF_FOLLOW_ON : StaffMessageKey.STAFF_FOLLOW_OFF;
        notifier.send(who, key, Map.of("target", targetPlayer.getName()));
    }

    private void teleport(PlayerRef who, Player targetPlayer, PlayerRef targetRef) {
        boolean ok = teleport.teleportTo(who, targetRef);
        StaffMessageKey key = ok ? StaffMessageKey.STAFF_TELEPORTED : StaffMessageKey.STAFF_TELEPORT_FAILED;
        notifier.send(who, key, Map.of("target", targetPlayer.getName()));
    }
}

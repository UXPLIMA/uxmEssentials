package com.uxplima.uxmessentials.kits.adapter.outbound;

import java.util.List;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.kits.application.port.KitActionRunner;
import com.uxplima.uxmessentials.kits.domain.KitAction;
import com.uxplima.uxmessentials.kits.domain.KitActionType;
import com.uxplima.uxmessentials.kits.domain.KitDefinition;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link KitActionRunner}: executes a kit's ordered, typed claim/deny actions, each on the thread its
 * effect requires. Player-targeted effects ({@code MESSAGE}, {@code ACTIONBAR}, {@code TITLE}, {@code SOUND},
 * {@code PARTICLE}, {@code FIREWORK}, {@code PLAYER_COMMAND}, {@code CLEAR_INVENTORY}) run on the recipient's
 * entity region thread; the
 * server-wide effects ({@code BROADCAST}, {@code CONSOLE_COMMAND}) run on the global region thread. Both hops
 * go through the injected {@link Scheduler} port, the legacy {@code BukkitScheduler} and {@code BukkitRunnable}
 * are never touched, so this stays Folia-correct.
 *
 * <p>{@code WAIT_TICKS} pauses the sequence: the remaining actions are re-scheduled after the stated tick count
 * through {@link Scheduler#asyncAfter} (ticks converted at the fixed 50&nbsp;ms cadence), which then hops back
 * onto the entity thread to resume: a tiny in-adapter sequencer rather than a repeating Bukkit task. The
 * sequencer always resolves the next thread from the upcoming action, so a {@code wait-ticks} between a
 * broadcast and a console command still lands each on its correct thread.
 *
 * <p>Every action is parsed defensively: a malformed sound/title/firework spec, an unknown particle, or a
 * non-numeric wait is logged through {@link Logger} and skipped, so a single typo never throws on the claim
 * path or aborts the rest of the sequence.
 */
@NullMarked
public final class BukkitKitActionRunner implements KitActionRunner {

    private static final long MILLIS_PER_TICK = 50L;

    private final Scheduler scheduler;
    private final KitActionEffects effects;
    private final Logger log;

    public BukkitKitActionRunner(Scheduler scheduler, Logger log) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.log = Objects.requireNonNull(log, "log");
        this.effects = new KitActionEffects(log);
    }

    @Override
    public void run(PlayerRef who, KitDefinition kit, List<KitAction> actions) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(kit, "kit");
        Objects.requireNonNull(actions, "actions");
        if (!actions.isEmpty()) {
            step(who, kit, List.copyOf(actions), 0);
        }
    }

    /** Run {@code actions} from {@code index} onward, hopping to each action's thread and honouring waits. */
    private void step(PlayerRef who, KitDefinition kit, List<KitAction> actions, int index) {
        if (index >= actions.size()) {
            return;
        }
        KitAction action = actions.get(index);
        if (action.type() == KitActionType.WAIT_TICKS) {
            waitThenContinue(who, kit, actions, index, action.value());
            return;
        }
        Runnable run = () -> {
            perform(who, kit, action);
            step(who, kit, actions, index + 1);
        };
        if (isGlobal(action.type())) {
            scheduler.onGlobal(run);
        } else {
            scheduler.onEntity(who, run);
        }
    }

    /** Schedule the remaining actions after the wait through the Scheduler port, then resume on the entity thread. */
    private void waitThenContinue(PlayerRef who, KitDefinition kit, List<KitAction> actions, int index, String raw) {
        long ticks = parseTicks(raw);
        if (ticks <= 0L) {
            step(who, kit, actions, index + 1);
            return;
        }
        scheduler.asyncAfter(
                java.time.Duration.ofMillis(ticks * MILLIS_PER_TICK),
                () -> scheduler.onEntity(who, () -> step(who, kit, actions, index + 1)));
    }

    /** Perform a single non-wait action on the current (already-correct) thread; never throws. */
    private void perform(PlayerRef who, KitDefinition kit, KitAction action) {
        try {
            switch (action.type()) {
                case CONSOLE_COMMAND -> dispatchConsole(who, kit, action.value());
                case BROADCAST -> effects.broadcast(substitute(action.value(), who, kit));
                default -> performForPlayer(who, kit, action);
            }
        } catch (RuntimeException failure) {
            log.warn("kit " + kit.id().value() + ": skipped " + action.type().token() + " action: "
                    + failure.getMessage());
        }
    }

    private void performForPlayer(PlayerRef who, KitDefinition kit, KitAction action) {
        Player player = Bukkit.getPlayer(who.uuid());
        if (player == null || !player.isOnline()) {
            return;
        }
        String value = substitute(action.value(), who, kit);
        switch (action.type()) {
            case MESSAGE -> effects.message(player, value);
            case ACTIONBAR -> effects.actionBar(player, value);
            case TITLE -> effects.title(player, value, kit.id().value());
            case SOUND -> effects.sound(player, value, kit.id().value());
            case PARTICLE -> effects.particle(player, value, kit.id().value());
            case FIREWORK -> effects.firework(player, value, kit.id().value());
            case PLAYER_COMMAND -> player.performCommand(value);
            case CLEAR_INVENTORY -> effects.clearInventory(player);
            default -> {
                // CONSOLE_COMMAND / BROADCAST / WAIT_TICKS are handled before this method; nothing to do.
            }
        }
    }

    private void dispatchConsole(PlayerRef who, KitDefinition kit, String value) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), substitute(value, who, kit));
    }

    private static boolean isGlobal(KitActionType type) {
        return type == KitActionType.BROADCAST || type == KitActionType.CONSOLE_COMMAND;
    }

    /** Substitute the standard claim tokens ({@code %player%}, {@code {player}}, and the kit id) in {@code raw}. */
    private static String substitute(String raw, PlayerRef who, KitDefinition kit) {
        String name = who.name();
        return raw.replace("%player%", name)
                .replace("{player}", name)
                .replace("%player_name%", name)
                .replace("%kit%", kit.id().value())
                .replace("{kit}", kit.id().value());
    }

    private long parseTicks(String raw) {
        try {
            return Long.parseLong(raw.strip());
        } catch (NumberFormatException notANumber) {
            log.warn("kit action wait-ticks value is not a number, skipping the wait: " + raw);
            return 0L;
        }
    }
}

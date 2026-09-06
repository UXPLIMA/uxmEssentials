package com.uxplima.uxmessentials.staff.adapter.inbound.command;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.staff.adapter.StaffServices;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /staffmode [player]} ({@code uxmessentials.staff.mode}): toggle staff mode. With no argument it
 * toggles the invoking player; with a {@code [player]} argument it toggles that online target (an admin flip).
 *
 * <p>The toggle is the item-loss-safe heart of staff mode and runs on the <i>target's entity region thread</i>
 * through the {@code Scheduler} port: the use case captures the real loadout, commits it to the DB-backed
 * repository, and only then swaps the live inventory for the gadget hotbar (the commit-before-swap invariant
 * owned by {@code EnterStaffMode}). Both the capture/swap (which touch the live entity) and the single-row
 * loadout upsert must see the same player on the same thread, so the whole use-case call runs on the entity
 * thread rather than splitting the durable commit away from the inventory work. Exit restores the real loadout
 * and deletes the row the same way.
 */
@NullMarked
public final class StaffModeCommand extends StaffCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.staff.mode";

    private final Scheduler scheduler;
    private final PlayerLookup players;
    private final BooleanSupplier running;

    public StaffModeCommand(
            StaffServices services,
            Messages messages,
            Scheduler scheduler,
            PlayerLookup players,
            BooleanSupplier running) {
        super(services, messages);
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.players = Objects.requireNonNull(players, "players");
        this.running = Objects.requireNonNull(running, "running");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("staffmode")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .then(CommandSuggestions.playerArgument("player").executes(this::toggleOther))
                .executes(this::toggleSelf)
                .build();
    }

    @Override
    public String description() {
        return "Toggle staff mode.";
    }

    private int toggleSelf(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        toggle(ref(sender));
        return Command.SINGLE_SUCCESS;
    }

    private int toggleOther(CommandContext<CommandSourceStack> ctx) {
        String name = ctx.getArgument("player", String.class);
        Optional<PlayerRef> target = players.findOnlineByName(name);
        if (target.isEmpty()) {
            feedback.send(ctx.getSource().getSender(), SharedMessageKey.COMMAND_UNKNOWN_PLAYER, Map.of("player", name));
            return 0;
        }
        toggle(target.get());
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Flip {@code who} between staff mode and not, on their entity region thread. The use cases are idempotent
     * on the live state: {@code enter} no-ops when already active, {@code exit} no-ops when not, so a double
     * toggle never double-captures or double-restores.
     */
    private void toggle(PlayerRef who) {
        if (!running.getAsBoolean()) {
            return;
        }
        scheduler.onEntity(who, () -> {
            if (services.store().isActive(who)) {
                services.exit().exit(who);
            } else {
                services.enter().enter(who);
            }
        });
    }
}

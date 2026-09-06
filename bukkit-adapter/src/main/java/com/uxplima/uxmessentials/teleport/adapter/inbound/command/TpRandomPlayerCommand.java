package com.uxplima.uxmessentials.teleport.adapter.inbound.command;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.teleport.adapter.TeleportRefs;
import com.uxplima.uxmessentials.teleport.adapter.TeleportServices;
import com.uxplima.uxmessentials.teleport.application.TeleportMessageKey;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /tprandomplayer} (alias {@code /tprp}): hop the invoking staff member to a random online player. A
 * roulette variant of {@code /tp} (instant, with no warmup or cooldown) that picks uniformly among the
 * players the sender can see (so a vanished staff member is never a target), excluding the sender. With no
 * such target it replies {@link TeleportMessageKey#TPRANDOM_NONE} and does nothing. Shares the staff
 * {@code uxmessentials.tp.use} node with {@code /tp}.
 */
@NullMarked
public final class TpRandomPlayerCommand extends TeleportCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.tp.use";

    public TpRandomPlayerCommand(TeleportServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("tprandomplayer")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::run)
                .build();
    }

    @Override
    public String description() {
        return "Teleport to a random online player.";
    }

    @Override
    public List<String> aliases() {
        return List.of("tprp");
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        List<Player> candidates = visibleOthers(sender);
        if (candidates.isEmpty()) {
            services.notifier().send(ref(sender), TeleportMessageKey.TPRANDOM_NONE);
            return Command.SINGLE_SUCCESS;
        }
        Player target = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        // Reading the chosen target's live location is a foreign-entity read: on Folia that position is owned by
        // the target's region thread, not the sender's. Resolve it on the target's entity thread, snapshot it to an
        // immutable Position, then hand it to the executor (which teleports the sender Folia-safely).
        PlayerRef actor = ref(sender);
        PlayerRef subject = ref(target);
        services.scheduler().onEntity(subject, () -> {
            Player live = sender.getServer().getPlayer(subject.uuid());
            if (live == null || !live.isOnline()) {
                return;
            }
            Position to = TeleportRefs.positionOf(live);
            services.executor().teleport(actor, Destination.at(to), TeleportKind.ADMIN);
            services.notifier().send(actor, TeleportMessageKey.TP_DONE);
        });
        return Command.SINGLE_SUCCESS;
    }

    private static List<Player> visibleOthers(Player sender) {
        List<Player> others = new ArrayList<>();
        for (Player online : sender.getServer().getOnlinePlayers()) {
            if (!online.getUniqueId().equals(sender.getUniqueId()) && sender.canSee(online)) {
                others.add(online);
            }
        }
        return others;
    }
}

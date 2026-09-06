package com.uxplima.uxmessentials.economy.adapter.inbound.command;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.economy.adapter.EconomyServices;
import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Handles {@code /deposit} to convert the banknote in the player's main hand back into virtual money. The
 * redemption itself, the dupe-safe remove-then-credit-then-restore-on-failure sequence and the per-token
 * in-flight guard shared with the right-click {@link
 * com.uxplima.uxmessentials.economy.adapter.inbound.listener.BanknoteListener}. Lives in the shared {@link
 * com.uxplima.uxmessentials.economy.adapter.inbound.listener.BanknoteRedeemer}; this command only locates the
 * held note and hops onto the holder's entity thread before delegating.
 */
@NullMarked
public final class DepositCommand extends EconomyCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.economy.deposit";

    public DepositCommand(EconomyServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("deposit")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::run)
                .build();
    }

    @Override
    public String description() {
        return "Deposit physical banknotes into virtual balance.";
    }

    private int run(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        deposit(sender, ref(sender));
        return Command.SINGLE_SUCCESS;
    }

    private void deposit(Player player, PlayerRef owner) {
        services.scheduler().onEntity(owner, () -> {
            ItemStack item = player.getInventory().getItemInMainHand();
            if (item.getType() != Material.PAPER || !services.banknoteRedeemer().isBanknote(item)) {
                services.notifier().send(owner, EconomyMessageKey.DEPOSIT_NO_BANKNOTE);
                return;
            }
            services.banknoteRedeemer().redeem(player, owner, item);
        });
    }
}

package com.uxplima.uxmessentials.economy.adapter.inbound.command;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.economy.adapter.EconomyServices;
import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /worth [item]}: report the configured sell value of an item before a player commits to {@code /sell}.
 * Called with no argument it prices the held item and its stack; called with a name it prices a single unit of
 * that material. The pricing read itself is the {@link com.uxplima.uxmessentials.economy.application.LookupWorth}
 * use case's job. This handler resolves the material at the boundary and runs the report off the tick thread,
 * the same off-tick shape {@link BalanceCommand} uses so a worth lookup never blocks the command.
 */
@NullMarked
public final class WorthCommand extends EconomyCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.economy.worth";

    public WorthCommand(EconomyServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("worth")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(ctx -> run(ctx, Optional.empty()))
                .then(itemArgument().executes(ctx -> run(ctx, Optional.of(StringArgumentType.getString(ctx, "item")))))
                .build();
    }

    @Override
    public String description() {
        return "Report a held item's configured sell value.";
    }

    private int run(CommandContext<CommandSourceStack> ctx, Optional<String> named) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        if (named.isPresent()) {
            Optional<Material> material = parseMaterial(named.get());
            if (material.isEmpty()) {
                services.notifier()
                        .send(ref(sender), EconomyMessageKey.WORTH_UNKNOWN_ITEM, Map.of("item", named.get()));
                return Command.SINGLE_SUCCESS;
            }
            reportNamed(sender, material.get());
            return Command.SINGLE_SUCCESS;
        }
        reportHeld(sender);
        return Command.SINGLE_SUCCESS;
    }

    private void reportNamed(Player sender, Material material) {
        String id = material.name().toLowerCase(Locale.ROOT);
        offTick(() -> services.lookupWorth().report(ref(sender), id, 1));
    }

    private void reportHeld(Player sender) {
        ItemStack held = sender.getInventory().getItemInMainHand();
        if (held.getType().isAir()) {
            services.notifier().send(ref(sender), EconomyMessageKey.WORTH_NO_ITEM_IN_HAND);
            return;
        }
        String id = held.getType().name().toLowerCase(Locale.ROOT);
        int amount = held.getAmount();
        offTick(() -> services.lookupWorth().report(ref(sender), id, amount));
    }

    private static Optional<Material> parseMaterial(String raw) {
        return Optional.ofNullable(Material.matchMaterial(raw));
    }
}

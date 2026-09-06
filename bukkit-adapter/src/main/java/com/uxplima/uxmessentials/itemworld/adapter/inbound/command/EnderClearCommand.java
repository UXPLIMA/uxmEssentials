package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices;
import com.uxplima.uxmessentials.itemworld.application.ItemworldMessageKey;
import com.uxplima.uxmessentials.itemworld.domain.SubFeatureGroup;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /enderclear [player]} (alias {@code /clearec}): empty an ender chest, your own, or another online
 * player's when a name is given. A cleanup verb that mirrors {@code /clearinventory} but targets
 * {@code Player#getEnderChest()}; a named target must resolve online (else
 * {@link ItemworldMessageKey#UNKNOWN_TARGET}).
 *
 * <p>Clearing the ender chest is entity-bound, so it runs on the subject's region thread through the kernel
 * {@code Scheduler}; the actor is told {@link ItemworldMessageKey#ENDERCLEAR_DONE} (self) or
 * {@link ItemworldMessageKey#ENDERCLEAR_DONE_OTHER} (target).
 */
@NullMarked
public final class EnderClearCommand extends ItemworldCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.enderclear.use";

    public EnderClearCommand(ItemworldServices services) {
        super(services, "enderclear", SubFeatureGroup.CLEANUP, "Clear an ender chest.");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal(literal())
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(ctx -> run(ctx, Optional.empty()))
                .then(CommandSuggestions.playerArgument("player")
                        .executes(ctx -> run(ctx, Optional.of(StringArgumentType.getString(ctx, "player")))))
                .build();
    }

    @Override
    public List<String> aliases() {
        return List.of("clearec");
    }

    @Override
    public String description() {
        return describe();
    }

    private int run(CommandContext<CommandSourceStack> ctx, Optional<String> name) {
        if (!enabled(ctx)) {
            return Command.SINGLE_SUCCESS;
        }
        Player self = player(ctx);
        if (self == null) {
            return Command.SINGLE_SUCCESS;
        }
        Player subject = name.map(n -> self.getServer().getPlayerExact(n)).orElse(self);
        if (subject == null) {
            reply(ctx, ItemworldMessageKey.UNKNOWN_TARGET, Map.of("player", name.orElse(self.getName())));
            return Command.SINGLE_SUCCESS;
        }
        clear(ctx, self, subject);
        return Command.SINGLE_SUCCESS;
    }

    private void clear(CommandContext<CommandSourceStack> ctx, Player self, Player subject) {
        boolean other = subject != self;
        String name = subject.getName();
        services.kernel().scheduler().onEntity(ref(subject), () -> {
            subject.getEnderChest().clear();
            if (other) {
                reply(ctx, ItemworldMessageKey.ENDERCLEAR_DONE_OTHER, Map.of("player", name));
            } else {
                reply(ctx, ItemworldMessageKey.ENDERCLEAR_DONE);
            }
        });
    }
}

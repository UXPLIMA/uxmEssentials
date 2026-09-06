package com.uxplima.uxmessentials.playerstate.adapter.inbound.command;

import java.util.List;
import java.util.Optional;

import org.bukkit.command.CommandSender;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.playerstate.adapter.PlayerStateServices;
import com.uxplima.uxmessentials.playerstate.domain.ExperienceChange;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /exp} (alias {@code /xp}, {@code uxmessentials.exp.use}): read or change a player's experience
 * {@code get}, {@code set <amount>}, {@code give <amount>}, {@code take <amount>}, or {@code reset}, with an
 * optional {@code levels}/{@code points} unit and an optional {@code [player]} target gated by the shared
 * {@code uxmessentials.exp.others} (or the cross-cutting {@code uxmessentials.playerstate.others}) node. The non-negative amount is bounded by Brigadier; the domain
 * {@link ExperienceChange} clamps the resulting total. The {@code SetExperience} use case owns the effect.
 */
@NullMarked
public final class ExperienceCommand extends PlayerstateCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.exp.use";

    public ExperienceCommand(PlayerStateServices services, Messages messages) {
        super(services, messages);
    }

    /** Targeting somebody else takes this node, or the cross-cutting playerstate one. */
    @Override
    String othersNode() {
        return "uxmessentials.exp.others";
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("exp")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(ctx -> run(ctx, ExperienceChange.get()))
                .then(amountVerb("set", ExperienceChange.Op.SET))
                .then(amountVerb("give", ExperienceChange.Op.GIVE))
                .then(amountVerb("take", ExperienceChange.Op.TAKE))
                .then(plainVerb("get", ExperienceChange.get()))
                .then(plainVerb("reset", ExperienceChange.reset()))
                .build();
    }

    @Override
    public List<String> aliases() {
        return List.of("xp");
    }

    @Override
    public String description() {
        return "Get or set a player's experience.";
    }

    private LiteralArgumentBuilder<CommandSourceStack> plainVerb(String literal, ExperienceChange change) {
        return Commands.literal(literal)
                .executes(ctx -> run(ctx, change))
                .then(Commands.argument("player", ArgumentTypes.player())
                        .suggests(CommandSuggestions.singlePlayerTarget())
                        .executes(ctx -> run(ctx, change)));
    }

    private LiteralArgumentBuilder<CommandSourceStack> amountVerb(String literal, ExperienceChange.Op op) {
        return Commands.literal(literal)
                .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                        .executes(ctx -> runAmount(ctx, op, ExperienceChange.Unit.POINTS))
                        .then(unitNode(op, ExperienceChange.Unit.LEVELS, "levels"))
                        .then(unitNode(op, ExperienceChange.Unit.POINTS, "points"))
                        .then(Commands.argument("player", ArgumentTypes.player())
                                .suggests(CommandSuggestions.singlePlayerTarget())
                                .executes(ctx -> runAmount(ctx, op, ExperienceChange.Unit.POINTS))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> unitNode(
            ExperienceChange.Op op, ExperienceChange.Unit unit, String literal) {
        return Commands.literal(literal)
                .executes(ctx -> runAmount(ctx, op, unit))
                .then(Commands.argument("player", ArgumentTypes.player())
                        .suggests(CommandSuggestions.singlePlayerTarget())
                        .executes(ctx -> runAmount(ctx, op, unit)));
    }

    private int runAmount(CommandContext<CommandSourceStack> ctx, ExperienceChange.Op op, ExperienceChange.Unit unit) {
        return run(ctx, new ExperienceChange(op, unit, ctx.getArgument("amount", Integer.class)));
    }

    private int run(CommandContext<CommandSourceStack> ctx, ExperienceChange change) {
        CommandSender sender = ctx.getSource().getSender();
        Optional<PlayerRef> target = resolveTarget(ctx, sender);
        if (target.isEmpty()) {
            return 0;
        }
        services.experience().applyFor(actor(ctx), target.get(), change);
        return Command.SINGLE_SUCCESS;
    }
}

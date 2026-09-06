package com.uxplima.uxmessentials.teleport.adapter.inbound.command;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.World;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.adapter.TeleportRefs;
import com.uxplima.uxmessentials.teleport.adapter.TeleportServices;
import com.uxplima.uxmessentials.teleport.application.TeleportMessageKey;
import com.uxplima.uxmessentials.teleport.domain.Destination;
import com.uxplima.uxmessentials.teleport.domain.TeleportKind;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The staff direct-teleport verbs ({@code /tp}, {@code /tphere}, {@code /tpo}, {@code /tpohere}) handled
 * without a warmup or cooldown (a staff hop is instant). {@link Pull#GO} moves the invoking player to the
 * target ({@code /tp}, {@code /tpo}); {@link Pull#BRING} moves the target to the invoking player
 * ({@code /tphere}, {@code /tpohere}). The {@code o} variants exist to override no-teleport flags; those
 * flags gate only {@code /tpa} requests, so the override variants share this handler and differ only in
 * the permission node.
 */
@NullMarked
public final class AdminTpCommand extends TeleportCommandSupport implements CommandRegistration {

    /** Whether the named player is the destination ({@code GO}) or is brought to the actor ({@code BRING}). */
    public enum Pull {
        GO,
        BRING
    }

    private final String literal;
    private final String permission;
    private final String description;
    private final Pull pull;

    public AdminTpCommand(
            TeleportServices services,
            Messages messages,
            String literal,
            String permission,
            String description,
            Pull pull) {
        super(services, messages);
        this.literal = Objects.requireNonNull(literal, "literal");
        this.permission = Objects.requireNonNull(permission, "permission");
        this.description = Objects.requireNonNull(description, "description");
        this.pull = Objects.requireNonNull(pull, "pull");
    }

    /** The node for the coordinate form of {@code /tp x y z [world]}, gated by the position node. */
    private static final String POSITION_PERMISSION = "uxmessentials.tp.position";

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        var player = Commands.argument("player", ArgumentTypes.player())
                .suggests(CommandSuggestions.singlePlayerTarget())
                .executes(this::run);
        if (pull == Pull.GO) {
            player.then(Commands.argument("destination", ArgumentTypes.player())
                    .suggests(CommandSuggestions.singlePlayerTarget())
                    .executes(this::runBetweenPlayers));
        }
        var root = Commands.literal(literal)
                .requires(src -> src.getSender().hasPermission(permission))
                .then(player);
        // The coordinate form mirrors /tppos and only makes sense for the GO variants (/tp, /tpo)
        // moving the actor to a raw point, so /tphere and /tpohere keep the player-only argument.
        if (pull == Pull.GO) {
            root.then(Commands.literal("at")
                    .requires(src -> src.getSender().hasPermission(POSITION_PERMISSION))
                    .then(Commands.argument("player", ArgumentTypes.player())
                            .suggests(CommandSuggestions.singlePlayerTarget())
                            .then(positionArguments(this::runTargetPosition))));
            root.then(Commands.argument("x", DoubleArgumentType.doubleArg())
                    .requires(src -> src.getSender().hasPermission(POSITION_PERMISSION))
                    .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                            .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                    .executes(ctx -> runPosition(ctx, null))
                                    .then(Commands.argument("world", StringArgumentType.word())
                                            .executes(ctx ->
                                                    runPosition(ctx, ctx.getArgument("world", String.class)))))));
        }
        return root.build();
    }

    @Override
    public String description() {
        return description;
    }

    private int run(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        Optional<Player> target = resolveTarget(ctx);
        if (target.isEmpty()) {
            return 0;
        }
        move(sender, target.get());
        services.notifier().send(ref(sender), TeleportMessageKey.TP_DONE);
        return Command.SINGLE_SUCCESS;
    }

    private void move(Player sender, Player target) {
        if (pull == Pull.GO) {
            // GO reads the TARGET's live location to send the actor there. On Folia the target's position is
            // owned by the target's region thread, not the sender's, so resolve it on the target's entity thread,
            // snapshot it to an immutable Position, and only then hand it to the executor (which teleports the
            // sender Folia-safely). BRING reads the sender's OWN location, which is region-local here.
            PlayerRef actor = ref(sender);
            PlayerRef subject = ref(target);
            services.scheduler().onEntity(subject, () -> {
                Player live = org.bukkit.Bukkit.getPlayer(subject.uuid());
                if (live != null && live.isOnline()) {
                    hop(actor, TeleportRefs.positionOf(live));
                }
            });
        } else {
            hop(ref(target), TeleportRefs.positionOf(sender));
        }
    }

    private void hop(PlayerRef who, Position to) {
        services.executor().teleport(who, Destination.at(to), TeleportKind.ADMIN);
    }

    /** {@code /tp <player> <destination>}: move an explicit subject, so console does not need a body. */
    private int runBetweenPlayers(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Optional<Player> subject = resolveTarget(ctx, "player");
        Optional<Player> destination = resolveTarget(ctx, "destination");
        if (subject.isEmpty() || destination.isEmpty()) {
            return 0;
        }
        PlayerRef subjectRef = ref(subject.get());
        PlayerRef destinationRef = ref(destination.get());
        PlayerRef feedbackActor = actor(ctx);
        services.scheduler().onEntity(destinationRef, () -> {
            Player live = org.bukkit.Bukkit.getPlayer(destinationRef.uuid());
            if (live != null && live.isOnline()) {
                hop(subjectRef, TeleportRefs.positionOf(live));
                services.notifier().send(feedbackActor, TeleportMessageKey.TP_DONE);
            }
        });
        return Command.SINGLE_SUCCESS;
    }

    /** {@code /tp at <player> <world> <x> <y> <z> [yaw pitch]} for deterministic console automation. */
    private int runTargetPosition(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        Optional<Player> subject = resolveTarget(ctx, "player");
        Position destination = explicitPosition(ctx);
        if (subject.isEmpty() || destination == null) {
            return 0;
        }
        hop(ref(subject.get()), destination);
        services.notifier().send(actor(ctx), TeleportMessageKey.TP_DONE);
        return Command.SINGLE_SUCCESS;
    }

    private int runPosition(CommandContext<CommandSourceStack> ctx, @Nullable String worldName) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        Optional<WorldRef> world = targetWorld(sender, worldName);
        if (world.isEmpty()) {
            services.notifier().send(ref(sender), TeleportMessageKey.SPAWN_UNRESOLVED);
            return 0;
        }
        org.bukkit.Location facing = TeleportRefs.location(sender);
        Position to = new Position(
                world.get(),
                ctx.getArgument("x", Double.class),
                ctx.getArgument("y", Double.class),
                ctx.getArgument("z", Double.class),
                facing.getYaw(),
                facing.getPitch());
        hop(ref(sender), to);
        services.notifier().send(ref(sender), TeleportMessageKey.TP_DONE);
        return Command.SINGLE_SUCCESS;
    }

    private Optional<WorldRef> targetWorld(Player sender, @Nullable String worldName) {
        if (worldName == null) {
            return Optional.of(BukkitRefs.toRef(sender.getWorld()));
        }
        World world = org.bukkit.Bukkit.getWorld(worldName);
        return world == null ? Optional.empty() : Optional.of(BukkitRefs.toRef(world));
    }

    // These verbs are deliberately single-target: GO moves the sender to one player and BRING moves one player
    // to the sender, so fanning out over @a is meaningless (you cannot send yourself to many places, and
    // bringing a whole selector is what /tpall already does). The argument stays ArgumentTypes.player(), which
    // rejects a multi-match selector at parse time, so a present value resolves to at most one player; a single
    // resolved element is taken. A selector like @p or a name still resolves the way it always has.
    private Optional<Player> resolveTarget(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return resolveTarget(ctx, "player");
    }

    private Optional<Player> resolveTarget(CommandContext<CommandSourceStack> ctx, String argument)
            throws CommandSyntaxException {
        PlayerSelectorArgumentResolver resolver = ctx.getArgument(argument, PlayerSelectorArgumentResolver.class);
        List<Player> resolved = resolver.resolve(ctx.getSource());
        return resolved.isEmpty() ? Optional.empty() : Optional.of(resolved.get(0));
    }
}

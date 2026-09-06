package com.uxplima.uxmessentials.homes.adapter.inbound.command;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.homes.adapter.HomeServices;
import com.uxplima.uxmessentials.homes.application.HomesMessageKey;
import com.uxplima.uxmessentials.homes.domain.HomeSet;
import com.uxplima.uxmessentials.homes.domain.HomeSlot;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /home}: the single entry point for everything a player does with homes, structured as a Brigadier
 * subcommand tree (the same idiom {@code /pwarp} uses).
 *
 * <ul>
 *   <li>{@code /home} (no args) opens the slot grid, teleport, create, delete, relocate, rename, re-icon all
 *       live inside it. Gated by {@code uxmessentials.home.use}.
 *   <li>{@code /home visit <player> [slot]} teleports to another player's home. Gated by
 *       {@code uxmessentials.home.visit}.
 *   <li>{@code /home invite <player> [slot]} / {@code /home uninvite <player> [slot]} grant and revoke access to
 *       one of your private homes. Both gated by {@code uxmessentials.home.invite}.
 *   <li>{@code /home admin <player> list|info <slot>|set [slot]|del <slot>|tp <slot>|clear} is full staff
 *       management over another player's homes. Gated by {@code uxmessentials.home.admin}.
 * </ul>
 *
 * <p>Per-subcommand permissions are enforced by Brigadier {@code .requires(...)} on each node, so only
 * {@code home} is a command-catalog id; the subcommands are literals inside the tree. Each subcommand keeps
 * its prior behaviour: targets resolve via the offline-capable
 * {@link com.uxplima.uxmessentials.shared.application.port.PlayerLookup#findByName} (only {@code admin tp}
 * needs the actor online), and repository I/O runs off the tick thread via the injected {@link Scheduler}.
 */
@NullMarked
public final class HomeCommand extends HomeCommandSupport implements CommandRegistration {

    private static final String USE_PERMISSION = "uxmessentials.home.use";
    private static final String VISIT_PERMISSION = "uxmessentials.home.visit";
    private static final String INVITE_PERMISSION = "uxmessentials.home.invite";
    private static final String ADMIN_PERMISSION = "uxmessentials.home.admin";

    private final Scheduler scheduler;

    public HomeCommand(HomeServices services, Messages messages, Scheduler scheduler) {
        super(services, messages);
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("home")
                .requires(src -> src.getSender().hasPermission(USE_PERMISSION))
                .executes(this::open)
                .then(targetSlotSub("visit", VISIT_PERMISSION, this::runVisit))
                .then(targetSlotSub("invite", INVITE_PERMISSION, this::runInvite))
                .then(targetSlotSub("uninvite", INVITE_PERMISSION, this::runUninvite))
                .then(adminSubtree())
                .build();
    }

    @Override
    public String description() {
        return "Open and manage your homes.";
    }

    /**
     * A {@code <verb> <player> [slot]} subcommand gated by {@code permission}: with no slot the action runs on
     * the owner's first home (slot index 0); with a 1-based slot it runs on the matching zero-based slot.
     */
    private LiteralArgumentBuilder<CommandSourceStack> targetSlotSub(
            String literal, String permission, SlotAction action) {
        return Commands.literal(literal)
                .requires(src -> src.getSender().hasPermission(permission))
                .then(CommandSuggestions.playerArgument("player")
                        .executes(ctx -> action.run(ctx, HomeSlot.of(0)))
                        .then(Commands.argument("slot", IntegerArgumentType.integer(1))
                                .executes(ctx -> action.run(ctx, slotArg(ctx)))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> adminSubtree() {
        return Commands.literal("admin")
                .requires(src -> src.getSender().hasPermission(ADMIN_PERMISSION))
                .then(CommandSuggestions.playerArgument("player")
                        .then(Commands.literal("list").executes(this::runAdminList))
                        .then(withSlot(Commands.literal("del"), this::runAdminDelete))
                        .then(withSlot(Commands.literal("tp"), this::runAdminTeleport))
                        .then(withOptionalSlot(
                                Commands.literal("set"), this::runAdminSetWithSlot, this::runAdminSetDefaultSlot))
                        .then(Commands.literal("clear").executes(this::runAdminClear))
                        .then(withSlot(Commands.literal("info"), this::runAdminInfo)));
    }

    private LiteralArgumentBuilder<CommandSourceStack> withSlot(
            LiteralArgumentBuilder<CommandSourceStack> verb, Command<CommandSourceStack> action) {
        RequiredArgumentBuilder<CommandSourceStack, Integer> slot =
                Commands.argument("slot", IntegerArgumentType.integer(1)).executes(action);
        return verb.then(slot);
    }

    /**
     * Builds a verb node that accepts an optional {@code <slot>} argument: with the slot argument it executes
     * {@code withSlot}, without it executes {@code withoutSlot}.
     */
    private LiteralArgumentBuilder<CommandSourceStack> withOptionalSlot(
            LiteralArgumentBuilder<CommandSourceStack> verb,
            Command<CommandSourceStack> withSlot,
            Command<CommandSourceStack> withoutSlot) {
        RequiredArgumentBuilder<CommandSourceStack, Integer> slot =
                Commands.argument("slot", IntegerArgumentType.integer(1)).executes(withSlot);
        return verb.executes(withoutSlot).then(slot);
    }

    private int open(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.homeList().open(ref(sender));
        return Command.SINGLE_SUCCESS;
    }

    private int runVisit(CommandContext<CommandSourceStack> ctx, HomeSlot slot) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef actor = ref(sender);
        String name = ctx.getArgument("player", String.class);
        Optional<PlayerRef> owner = services.players().findByName(name);
        if (owner.isEmpty()) {
            feedback.send(sender, HomesMessageKey.HOME_ADMIN_TARGET_UNKNOWN, Map.of("player", name));
            return Command.SINGLE_SUCCESS;
        }
        PlayerRef resolved = owner.get();
        scheduler.async(() -> services.visitHome().visit(actor, resolved, slot));
        return Command.SINGLE_SUCCESS;
    }

    private int runInvite(CommandContext<CommandSourceStack> ctx, HomeSlot slot) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef owner = ref(sender);
        String name = ctx.getArgument("player", String.class);
        Optional<PlayerRef> invited = services.players().findByName(name);
        if (invited.isEmpty()) {
            feedback.send(sender, HomesMessageKey.HOME_ADMIN_TARGET_UNKNOWN, Map.of("player", name));
            return Command.SINGLE_SUCCESS;
        }
        PlayerRef resolved = invited.get();
        scheduler.async(() -> services.inviteToHome().invite(owner, slot, resolved));
        return Command.SINGLE_SUCCESS;
    }

    private int runUninvite(CommandContext<CommandSourceStack> ctx, HomeSlot slot) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef owner = ref(sender);
        String name = ctx.getArgument("player", String.class);
        Optional<PlayerRef> invited = services.players().findByName(name);
        if (invited.isEmpty()) {
            feedback.send(sender, HomesMessageKey.HOME_ADMIN_TARGET_UNKNOWN, Map.of("player", name));
            return Command.SINGLE_SUCCESS;
        }
        PlayerRef resolved = invited.get();
        scheduler.async(() -> services.uninviteFromHome().uninvite(owner, slot, resolved));
        return Command.SINGLE_SUCCESS;
    }

    private int runAdminList(CommandContext<CommandSourceStack> ctx) {
        return dispatchOfflineTarget(ctx, target -> services.homeAdmin().list(refOf(ctx), target));
    }

    private int runAdminDelete(CommandContext<CommandSourceStack> ctx) {
        return dispatchOfflineTarget(ctx, target -> services.homeAdmin().delete(refOf(ctx), target, slotArg(ctx)));
    }

    private int runAdminTeleport(CommandContext<CommandSourceStack> ctx) {
        // tp requires the actor to be online: the actor teleports.
        return dispatchOnlineTarget(ctx, target -> services.homeAdmin().teleport(refOf(ctx), target, slotArg(ctx)));
    }

    private int runAdminSetWithSlot(CommandContext<CommandSourceStack> ctx) {
        return runAdminSet(ctx, slotArg(ctx));
    }

    /**
     * {@code admin set} without an explicit slot: load the target's set off-thread and use the first free slot
     * (index 0 when the set is empty, otherwise one past the highest occupied index). Because the load itself
     * is async, the slot is computed inside the async block after the load.
     */
    private int runAdminSetDefaultSlot(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        // Capture position on the entity thread before hopping off.
        Position at = BukkitRefs.toPosition(Objects.requireNonNull(sender.getLocation(), "location"));
        PlayerRef actor = ref(sender);
        String name = ctx.getArgument("player", String.class);
        Optional<PlayerRef> resolved = services.players().findByName(name);
        if (resolved.isEmpty()) {
            feedback.send(sender, HomesMessageKey.HOME_ADMIN_TARGET_UNKNOWN, Map.of("player", name));
            return Command.SINGLE_SUCCESS;
        }
        PlayerRef target = resolved.get();
        scheduler.async(() -> {
            HomeSet set = services.repository().load(target);
            // First slot index not occupied by the target, 0 when empty, otherwise max+1.
            java.util.OptionalInt maxOpt =
                    set.all().stream().mapToInt(h -> h.slot().index()).max();
            int nextIndex = maxOpt.isPresent() ? maxOpt.getAsInt() + 1 : 0;
            HomeSlot slot = HomeSlot.of(nextIndex);
            services.homeAdmin().setHome(actor, target, slot, at);
        });
        return Command.SINGLE_SUCCESS;
    }

    private int runAdminSet(CommandContext<CommandSourceStack> ctx, HomeSlot slot) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        // Capture position on the entity thread before hopping off.
        Position at = BukkitRefs.toPosition(Objects.requireNonNull(sender.getLocation(), "location"));
        PlayerRef actor = ref(sender);
        String name = ctx.getArgument("player", String.class);
        Optional<PlayerRef> resolved = services.players().findByName(name);
        if (resolved.isEmpty()) {
            feedback.send(sender, HomesMessageKey.HOME_ADMIN_TARGET_UNKNOWN, Map.of("player", name));
            return Command.SINGLE_SUCCESS;
        }
        PlayerRef target = resolved.get();
        scheduler.async(() -> services.homeAdmin().setHome(actor, target, slot, at));
        return Command.SINGLE_SUCCESS;
    }

    private int runAdminClear(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef actor = ref(sender);
        String name = ctx.getArgument("player", String.class);
        Optional<PlayerRef> resolved = services.players().findByName(name);
        if (resolved.isEmpty()) {
            feedback.send(sender, HomesMessageKey.HOME_ADMIN_TARGET_UNKNOWN, Map.of("player", name));
            return Command.SINGLE_SUCCESS;
        }
        PlayerRef target = resolved.get();
        scheduler.async(() -> services.homeAdmin().clearAll(actor, target));
        return Command.SINGLE_SUCCESS;
    }

    private int runAdminInfo(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef actor = ref(sender);
        HomeSlot slot = slotArg(ctx);
        String name = ctx.getArgument("player", String.class);
        Optional<PlayerRef> resolved = services.players().findByName(name);
        if (resolved.isEmpty()) {
            feedback.send(sender, HomesMessageKey.HOME_ADMIN_TARGET_UNKNOWN, Map.of("player", name));
            return Command.SINGLE_SUCCESS;
        }
        PlayerRef target = resolved.get();
        scheduler.async(() -> services.homeAdmin().info(actor, target, slot));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Resolve target via {@link com.uxplima.uxmessentials.shared.application.port.PlayerLookup#findByName}
     * (offline-capable) then execute {@code verb} on the async thread. Used by the admin verbs that do not
     * need the sender to be online at the destination.
     */
    private int dispatchOfflineTarget(
            CommandContext<CommandSourceStack> ctx, java.util.function.Consumer<PlayerRef> verb) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        String name = ctx.getArgument("player", String.class);
        Optional<PlayerRef> resolved = services.players().findByName(name);
        if (resolved.isEmpty()) {
            feedback.send(sender, HomesMessageKey.HOME_ADMIN_TARGET_UNKNOWN, Map.of("player", name));
            return Command.SINGLE_SUCCESS;
        }
        PlayerRef target = resolved.get();
        scheduler.async(() -> verb.accept(target));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Resolve target online-only (for verbs like {@code tp} where the actor is the one moving).
     */
    private int dispatchOnlineTarget(
            CommandContext<CommandSourceStack> ctx, java.util.function.Consumer<PlayerRef> verb) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        String name = ctx.getArgument("player", String.class);
        Optional<PlayerRef> target = services.players().findOnlineByName(name);
        if (target.isEmpty()) {
            feedback.send(sender, HomesMessageKey.HOME_ADMIN_TARGET_UNKNOWN, Map.of("player", name));
            return Command.SINGLE_SUCCESS;
        }
        scheduler.async(() -> verb.accept(target.get()));
        return Command.SINGLE_SUCCESS;
    }

    private PlayerRef refOf(CommandContext<CommandSourceStack> ctx) {
        return ref((Player) ctx.getSource().getSender());
    }

    private static HomeSlot slotArg(CommandContext<CommandSourceStack> ctx) {
        // Players and admins type the 1-based number the grid/list prints; the aggregate keys on the
        // zero-based slot.
        return HomeSlot.of(ctx.getArgument("slot", Integer.class) - 1);
    }

    /** One {@code <verb> <player> [slot]} handler, parameterised by the resolved zero-based slot. */
    @FunctionalInterface
    private interface SlotAction {
        int run(CommandContext<CommandSourceStack> ctx, HomeSlot slot);
    }
}

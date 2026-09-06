package com.uxplima.uxmessentials.playerwarps.adapter.inbound.command;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.playerwarps.adapter.PlayerWarpServices;
import com.uxplima.uxmessentials.playerwarps.application.PlayerwarpsMessageKey;
import com.uxplima.uxmessentials.playerwarps.domain.DisplayName;
import com.uxplima.uxmessentials.playerwarps.domain.IconSpec;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpError;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpId;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.WarpAccess;
import com.uxplima.uxmessentials.playerwarps.domain.WarpDescription;
import com.uxplima.uxmessentials.playerwarps.domain.WarpRole;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.warps.domain.WarpCost;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /pwarp}: the single entry point for everything a player does with their own player-warps, structured as a
 * Brigadier subcommand tree (the same idiom {@code /home} uses). With no arguments it opens the management GUI; a
 * bare {@code <name>} teleports; the folded-in verbs cover the whole self-service surface, create/move, archive,
 * rename, the metadata/access/price edits, the social verbs (rate, favourite), transfer, withdraw, info, and list.
 *
 * <p>Every subcommand is gated by its own {@code uxmessentials.pwarp.<verb>} node through Brigadier
 * {@code .requires(...)}, so only {@code pwarp} is a command-catalog id; the verbs are literals inside the tree.
 * The self-service verbs let anyone run them but the use case's {@code WarpAuthorization} decides by role which
 * warps they touch; the {@code members}/{@code ban}/{@code whitelist} people-management verbs work the same way,
 * while the operator {@code admin} group ({@code restore}/{@code purge}/{@code setowner}/{@code reload}) acts on any
 * warp by its surrogate id under a single {@code uxmessentials.pwarp.admin} node. That node <em>is</em> the
 * authorization, so those verbs skip the per-warp role gate, and the irreversible {@code purge} is confirm-gated.
 * Each handler resolves any tick-thread state. The actor's {@link PlayerRef}, and their {@link Position} for
 * {@code set}/{@code move}, then hands the repository I/O to the injected scheduler and returns immediately, so the
 * command thread never blocks. A malformed name is turned into a friendly notice, never a stack trace, and the
 * teleport password is threaded straight to the access gate: it is never echoed, logged, or tab-completed.
 */
@NullMarked
public final class PlayerWarpCommand extends PlayerWarpCommandSupport implements CommandRegistration {

    private static final String USE_PERMISSION = "uxmessentials.pwarp.use";
    private static final String PUBLIC_PERMISSION = "uxmessentials.pwarp.public";
    private static final String EDIT_PERMISSION = "uxmessentials.pwarp.edit";
    private static final String DELETE_PERMISSION = "uxmessentials.pwarp.delete";
    private static final String SET_PERMISSION = "uxmessentials.pwarp.set";
    private static final String MOVE_PERMISSION = "uxmessentials.pwarp.move";
    private static final String RENAME_PERMISSION = "uxmessentials.pwarp.rename";
    private static final String DISPLAYNAME_PERMISSION = "uxmessentials.pwarp.displayname";
    private static final String DESCRIPTION_PERMISSION = "uxmessentials.pwarp.description";
    private static final String ICON_PERMISSION = "uxmessentials.pwarp.icon";
    private static final String CATEGORY_PERMISSION = "uxmessentials.pwarp.category";
    private static final String ACCESS_PERMISSION = "uxmessentials.pwarp.access";
    private static final String PASSWORD_PERMISSION = "uxmessentials.pwarp.password";
    private static final String PRICE_PERMISSION = "uxmessentials.pwarp.price";
    private static final String INFO_PERMISSION = "uxmessentials.pwarp.info";
    private static final String LIST_PERMISSION = "uxmessentials.pwarp.list";
    private static final String FAVOURITE_PERMISSION = "uxmessentials.pwarp.favourite";
    private static final String RATE_PERMISSION = "uxmessentials.pwarp.rate";
    private static final String TRANSFER_PERMISSION = "uxmessentials.pwarp.transfer";
    private static final String SPONSOR_PERMISSION = "uxmessentials.pwarp.sponsor";
    private static final String WITHDRAW_PERMISSION = "uxmessentials.pwarp.withdraw";
    private static final String MEMBERS_PERMISSION = "uxmessentials.pwarp.members";
    private static final String BAN_PERMISSION = "uxmessentials.pwarp.ban";
    private static final String WHITELIST_PERMISSION = "uxmessentials.pwarp.whitelist";
    private static final String ADMIN_PERMISSION = "uxmessentials.pwarp.admin";

    /** A single {@code <number><unit>} ban duration token: {@code 7d}, {@code 12h}, {@code 30m}; anything else is a reason. */
    private static final Pattern BAN_DURATION = Pattern.compile("(\\d+)([smhdw])", Pattern.CASE_INSENSITIVE);

    public PlayerWarpCommand(PlayerWarpServices services, Messages messages) {
        super(services, messages);
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("pwarp")
                .requires(src -> src.getSender().hasPermission(USE_PERMISSION))
                .executes(this::openGui)
                .then(visibilitySubtree())
                .then(verb("edit", EDIT_PERMISSION).then(nameArg().executes(this::openPlayerWarpEditor)))
                .then(verb("del", DELETE_PERMISSION).then(nameArg().executes(this::runDelete)))
                .then(verb("set", SET_PERMISSION).then(nameArg().executes(this::runSet)))
                .then(verb("move", MOVE_PERMISSION).then(nameArg().executes(this::runMove)))
                .then(verb("rename", RENAME_PERMISSION)
                        .then(nameArg()
                                .then(Commands.argument("newName", StringArgumentType.word())
                                        .executes(this::runRename))))
                .then(textVerb("displayname", DISPLAYNAME_PERMISSION, "text", this::runDisplayName))
                .then(textVerb("description", DESCRIPTION_PERMISSION, "text", this::runDescription))
                .then(textVerb("icon", ICON_PERMISSION, "icon", this::runIcon))
                .then(textVerb("category", CATEGORY_PERMISSION, "categoryId", this::runCategory))
                .then(accessSubtree())
                .then(passwordSubtree())
                .then(priceSubtree())
                .then(verb("rate", RATE_PERMISSION)
                        .then(nameArg()
                                .then(Commands.argument("stars", IntegerArgumentType.integer(1, 5))
                                        .executes(this::runRate))))
                .then(verb("favourite", FAVOURITE_PERMISSION)
                        .then(nameArg().executes(ctx -> dispatch(ctx, services.favouritePlayerWarp()::favourite))))
                .then(verb("unfavourite", FAVOURITE_PERMISSION)
                        .then(nameArg().executes(ctx -> dispatch(ctx, services.favouritePlayerWarp()::unfavourite))))
                .then(verb("withdraw", WITHDRAW_PERMISSION)
                        .then(nameArg().executes(ctx -> dispatch(ctx, services.withdrawEarnings()::withdraw))))
                .then(verb("info", INFO_PERMISSION)
                        .then(nameArg().executes(ctx -> dispatch(ctx, services.listPlayerWarps()::info))))
                .then(verb("transfer", TRANSFER_PERMISSION)
                        .then(nameArg()
                                .then(CommandSuggestions.playerArgument("player")
                                        .executes(this::runTransfer))))
                .then(sponsorSubtree())
                .then(membersSubtree())
                .then(banSubtree())
                .then(verb("unban", BAN_PERMISSION)
                        .then(nameArg()
                                .then(CommandSuggestions.playerArgument("player")
                                        .executes(this::runUnban))))
                .then(whitelistSubtree())
                .then(adminSubtree())
                .then(listSubtree())
                .then(nameArg()
                        .executes(this::run)
                        .then(Commands.argument("password", StringArgumentType.greedyString())
                                .executes(this::runWithPassword)))
                .build();
    }

    @Override
    public String description() {
        return "Teleport to a player warp, edit your own warps, or manage their access and price.";
    }

    /**
     * {@code /pwarp} with no arguments: open the paged {@code pwarp-browse} warp grid directly, the way AxPlayerWarps
     * opens on {@code /pwarp}. It is the warp list itself, not a hub: one bounded page query off the tick thread, then
     * a paint on the player's entity thread, and a clean "no warps yet" placeholder when the server has none. The
     * {@code pwarp-categories} hub (category filters, quick scopes, sponsors) stays reachable from the browse's own
     * categories control.
     */
    private int openGui(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.browseView().open(sender, ref(sender));
        return Command.SINGLE_SUCCESS;
    }

    private int openPlayerWarpEditor(CommandContext<CommandSourceStack> ctx) {
        Target target = target(ctx);
        if (target == null) {
            return Command.SINGLE_SUCCESS;
        }
        String name = target.name().value();
        PlayerRef owner = target.who();
        // The existence check reads the database; run it off the tick thread, then bridge the open (or the
        // not-found feedback) back to the player's region thread. Names are globally unique now, so resolve by
        // name and confirm the sender owns it before editing.
        services.scheduler().async(() -> {
            boolean exists = services.repository()
                    .findByName(target.name())
                    .filter(warp -> warp.owner().uuid().equals(owner.uuid()))
                    .isPresent();
            onPlayer(owner, () -> {
                if (!exists) {
                    feedback.send(target.sender(), PlayerwarpsMessageKey.PWARP_NOT_FOUND, Map.of("warp", name));
                    return;
                }
                if (services.editorView() != null) {
                    services.editorView().open(target.sender(), owner, name, owner);
                }
            });
        });
        return Command.SINGLE_SUCCESS;
    }

    /** {@code /pwarp <name>}: teleport with no password (an empty password on a PASSWORD warp is a wrong attempt). */
    private int run(CommandContext<CommandSourceStack> ctx) {
        Target target = target(ctx);
        if (target == null) {
            return Command.SINGLE_SUCCESS;
        }
        // The use case reads the warp then delegates the hop to the teleport context; run the read off-thread.
        services.scheduler()
                .async(() -> services.usePlayerWarp().useFor(target.who(), target.name(), Optional.empty()));
        return Command.SINGLE_SUCCESS;
    }

    /** {@code /pwarp <name> <password>}: thread the entered password to the access gate for a PASSWORD warp. */
    private int runWithPassword(CommandContext<CommandSourceStack> ctx) {
        Target target = target(ctx);
        if (target == null) {
            return Command.SINGLE_SUCCESS;
        }
        // The password is handed straight to the gate's Optional overload and never echoed, logged, or completed.
        String password = ctx.getArgument("password", String.class);
        services.scheduler()
                .async(() -> services.usePlayerWarp().useFor(target.who(), target.name(), Optional.of(password)));
        return Command.SINGLE_SUCCESS;
    }

    /** {@code /pwarp del <name>}: archive the warp by default (recoverable); the read/write runs off-thread. */
    private int runDelete(CommandContext<CommandSourceStack> ctx) {
        return dispatch(ctx, services.archivePlayerWarp()::archive);
    }

    private int runSet(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        var name = warpName(sender, ctx.getArgument("name", String.class));
        if (name == null) {
            return Command.SINGLE_SUCCESS;
        }
        // Capture the position on the entity thread before hopping off; the find + count + save touch the database.
        PlayerRef who = ref(sender);
        String ownerName = sender.getName();
        Position at = position(sender);
        services.scheduler().async(() -> services.setPlayerWarp().set(who, ownerName, name, at));
        return Command.SINGLE_SUCCESS;
    }

    private int runMove(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        var name = warpName(sender, ctx.getArgument("name", String.class));
        if (name == null) {
            return Command.SINGLE_SUCCESS;
        }
        PlayerRef who = ref(sender);
        Position at = position(sender);
        services.scheduler().async(() -> services.editPlayerWarp().moveHere(who, name, at));
        return Command.SINGLE_SUCCESS;
    }

    private int runRename(CommandContext<CommandSourceStack> ctx) {
        Target target = target(ctx);
        if (target == null) {
            return Command.SINGLE_SUCCESS;
        }
        var newName = warpName(target.sender(), ctx.getArgument("newName", String.class));
        if (newName == null) {
            return Command.SINGLE_SUCCESS;
        }
        services.scheduler().async(() -> services.editPlayerWarp().rename(target.who(), target.name(), newName));
        return Command.SINGLE_SUCCESS;
    }

    private int runDisplayName(CommandContext<CommandSourceStack> ctx) {
        return editOptional(ctx, "text", DisplayName::of, services.editPlayerWarp()::setDisplayName);
    }

    private int runDescription(CommandContext<CommandSourceStack> ctx) {
        return editOptional(ctx, "text", WarpDescription::of, services.editPlayerWarp()::setDescription);
    }

    private int runIcon(CommandContext<CommandSourceStack> ctx) {
        return editOptional(ctx, "icon", IconSpec::of, services.editPlayerWarp()::setIcon);
    }

    private int runCategory(CommandContext<CommandSourceStack> ctx) {
        Target target = target(ctx);
        if (target == null) {
            return Command.SINGLE_SUCCESS;
        }
        Optional<String> value = optionalText(argOr(ctx, "categoryId"));
        services.scheduler().async(() -> services.editPlayerWarp().setCategory(target.who(), target.name(), value));
        return Command.SINGLE_SUCCESS;
    }

    private int runAccess(CommandContext<CommandSourceStack> ctx, WarpAccess access) {
        Target target = target(ctx);
        if (target == null) {
            return Command.SINGLE_SUCCESS;
        }
        services.scheduler().async(() -> services.editPlayerWarp().setAccess(target.who(), target.name(), access));
        return Command.SINGLE_SUCCESS;
    }

    private int runSetPassword(CommandContext<CommandSourceStack> ctx) {
        Target target = target(ctx);
        if (target == null) {
            return Command.SINGLE_SUCCESS;
        }
        // The password is handed straight to the edit use case and never echoed, logged, or tab-completed.
        String password = ctx.getArgument("password", String.class);
        services.scheduler().async(() -> services.editPlayerWarp().setPassword(target.who(), target.name(), password));
        return Command.SINGLE_SUCCESS;
    }

    private int runClearPassword(CommandContext<CommandSourceStack> ctx) {
        return dispatch(ctx, services.editPlayerWarp()::clearPassword);
    }

    private int runPrice(CommandContext<CommandSourceStack> ctx, Optional<String> currency) {
        Target target = target(ctx);
        if (target == null) {
            return Command.SINGLE_SUCCESS;
        }
        // The amount arg is bounded non-negative at parse (doubleArg(0.0)); WarpCost still guards the invariant.
        BigDecimal amount = BigDecimal.valueOf(ctx.getArgument("amount", Double.class));
        WarpCost price = currency.map(id -> WarpCost.of(amount, id)).orElseGet(() -> WarpCost.of(amount));
        services.scheduler().async(() -> services.editPlayerWarp().setPrice(target.who(), target.name(), price));
        return Command.SINGLE_SUCCESS;
    }

    private int runRate(CommandContext<CommandSourceStack> ctx) {
        Target target = target(ctx);
        if (target == null) {
            return Command.SINGLE_SUCCESS;
        }
        int stars = ctx.getArgument("stars", Integer.class);
        services.scheduler().async(() -> services.ratePlayerWarp().rate(target.who(), target.name(), stars));
        return Command.SINGLE_SUCCESS;
    }

    private int runTransfer(CommandContext<CommandSourceStack> ctx) {
        Target target = target(ctx);
        if (target == null) {
            return Command.SINGLE_SUCCESS;
        }
        String targetName = ctx.getArgument("player", String.class);
        // Resolve the (possibly offline) target profile off the tick thread; an unknown target bridges the
        // not-found notice back to the actor's region thread rather than transferring to nobody.
        services.scheduler()
                .async(() -> withPlayer(
                        target,
                        targetName,
                        newOwner -> services.transferPlayerWarp().transfer(target.who(), target.name(), newOwner)));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * {@code /pwarp sponsor <name> [days]}: buy a paid pinned browse slot. The verb literal is gated both on the
     * {@code uxmessentials.pwarp.sponsor} node and on the sponsor sub-group being enabled, so a disabled sub-group
     * offers no active branch. The optional {@code days} is bounded positive at parse; when omitted the configured
     * default term is used, and the use case clamps it to the configured maximum either way.
     */
    private LiteralArgumentBuilder<CommandSourceStack> sponsorSubtree() {
        return Commands.literal("sponsor")
                .requires(src ->
                        services.sponsorConfig().enabled() && src.getSender().hasPermission(SPONSOR_PERMISSION))
                .then(nameArg()
                        .executes(this::runSponsor)
                        .then(Commands.argument("days", IntegerArgumentType.integer(1))
                                .executes(this::runSponsor)));
    }

    private int runSponsor(CommandContext<CommandSourceStack> ctx) {
        Target target = target(ctx);
        if (target == null) {
            return Command.SINGLE_SUCCESS;
        }
        int days = daysOr(ctx, services.sponsorConfig().durationDays());
        services.scheduler().async(() -> services.buySponsorship().buy(target.who(), target.name(), days));
        return Command.SINGLE_SUCCESS;
    }

    /** The {@code days} argument when this branch supplied it, else {@code fallback} (the configured default term). */
    private static int daysOr(CommandContext<CommandSourceStack> ctx, int fallback) {
        try {
            return ctx.getArgument("days", Integer.class);
        } catch (IllegalArgumentException absent) {
            return fallback;
        }
    }

    // People-management verbs: members / ban / unban / whitelist. Each resolves the (possibly offline) target off
    // the tick thread through withPlayer, then drives its use case, whose WarpAuthorization gates the action by role.

    private LiteralArgumentBuilder<CommandSourceStack> membersSubtree() {
        return verb("members", MEMBERS_PERMISSION)
                .then(nameArg()
                        .then(Commands.literal("add").then(memberRoleArgs()))
                        .then(Commands.literal("remove")
                                .then(CommandSuggestions.playerArgument("player")
                                        .executes(this::runMemberRemove))));
    }

    /** The {@code add <player> <role>} tail: one role literal per grantable role (co-owner / manager, never owner). */
    private RequiredArgumentBuilder<CommandSourceStack, String> memberRoleArgs() {
        RequiredArgumentBuilder<CommandSourceStack, String> player = CommandSuggestions.playerArgument("player");
        for (WarpRole role : WarpRole.values()) {
            if (role != WarpRole.OWNER) {
                player.then(Commands.literal(roleToken(role)).executes(ctx -> runMemberAdd(ctx, role)));
            }
        }
        return player;
    }

    private int runMemberAdd(CommandContext<CommandSourceStack> ctx, WarpRole role) {
        Target target = target(ctx);
        if (target == null) {
            return Command.SINGLE_SUCCESS;
        }
        String playerName = ctx.getArgument("player", String.class);
        services.scheduler()
                .async(() -> withPlayer(
                        target,
                        playerName,
                        member -> services.manageMembers().addMember(target.who(), target.name(), member, role)));
        return Command.SINGLE_SUCCESS;
    }

    private int runMemberRemove(CommandContext<CommandSourceStack> ctx) {
        Target target = target(ctx);
        if (target == null) {
            return Command.SINGLE_SUCCESS;
        }
        String playerName = ctx.getArgument("player", String.class);
        services.scheduler()
                .async(() -> withPlayer(
                        target,
                        playerName,
                        member -> services.manageMembers().removeMember(target.who(), target.name(), member)));
        return Command.SINGLE_SUCCESS;
    }

    private LiteralArgumentBuilder<CommandSourceStack> banSubtree() {
        return verb("ban", BAN_PERMISSION)
                .then(nameArg()
                        .then(CommandSuggestions.playerArgument("player")
                                .executes(this::runBan)
                                .then(Commands.argument("duration", StringArgumentType.word())
                                        .executes(this::runBan)
                                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                                .executes(this::runBan)))));
    }

    private int runBan(CommandContext<CommandSourceStack> ctx) {
        Target target = target(ctx);
        if (target == null) {
            return Command.SINGLE_SUCCESS;
        }
        String playerName = ctx.getArgument("player", String.class);
        String durationToken = argOr(ctx, "duration");
        Optional<Duration> duration = durationToken.isBlank() ? Optional.empty() : parseBanDuration(durationToken);
        Optional<String> reason = banReason(durationToken, duration, argOr(ctx, "reason"));
        services.scheduler()
                .async(() -> withPlayer(
                        target,
                        playerName,
                        banned -> services.manageBans().ban(target.who(), target.name(), banned, duration, reason)));
        return Command.SINGLE_SUCCESS;
    }

    private int runUnban(CommandContext<CommandSourceStack> ctx) {
        Target target = target(ctx);
        if (target == null) {
            return Command.SINGLE_SUCCESS;
        }
        String playerName = ctx.getArgument("player", String.class);
        services.scheduler()
                .async(() -> withPlayer(
                        target,
                        playerName,
                        banned -> services.manageBans().unban(target.who(), target.name(), banned)));
        return Command.SINGLE_SUCCESS;
    }

    private LiteralArgumentBuilder<CommandSourceStack> whitelistSubtree() {
        return verb("whitelist", WHITELIST_PERMISSION)
                .then(nameArg()
                        .then(Commands.literal("add")
                                .then(CommandSuggestions.playerArgument("player")
                                        .executes(this::runWhitelistAdd)))
                        .then(Commands.literal("remove")
                                .then(CommandSuggestions.playerArgument("player")
                                        .executes(this::runWhitelistRemove))));
    }

    private int runWhitelistAdd(CommandContext<CommandSourceStack> ctx) {
        Target target = target(ctx);
        if (target == null) {
            return Command.SINGLE_SUCCESS;
        }
        String playerName = ctx.getArgument("player", String.class);
        services.scheduler()
                .async(() -> withPlayer(
                        target,
                        playerName,
                        guest -> services.manageWhitelist().whitelist(target.who(), target.name(), guest)));
        return Command.SINGLE_SUCCESS;
    }

    private int runWhitelistRemove(CommandContext<CommandSourceStack> ctx) {
        Target target = target(ctx);
        if (target == null) {
            return Command.SINGLE_SUCCESS;
        }
        String playerName = ctx.getArgument("player", String.class);
        services.scheduler()
                .async(() -> withPlayer(
                        target,
                        playerName,
                        guest -> services.manageWhitelist().unwhitelist(target.who(), target.name(), guest)));
        return Command.SINGLE_SUCCESS;
    }

    // Admin group: operator verbs on any warp by its surrogate id, gated by uxmessentials.pwarp.admin (op). The id
    // read runs off the tick thread; the operator-facing result bridges back to their region thread as feedback.

    private LiteralArgumentBuilder<CommandSourceStack> adminSubtree() {
        return verb("admin", ADMIN_PERMISSION)
                .then(Commands.literal("restore").then(idArg().executes(this::runAdminRestore)))
                .then(Commands.literal("purge").then(purgeIdArg()))
                .then(Commands.literal("delete").then(purgeIdArg()))
                .then(Commands.literal("setowner")
                        .then(idArg().then(CommandSuggestions.playerArgument("player")
                                .executes(this::runAdminSetOwner))))
                .then(Commands.literal("reload").executes(this::runAdminReload));
    }

    /** {@code <id>} for {@code purge}/{@code delete}: bare id warns; the {@code confirm} suffix performs the delete. */
    private RequiredArgumentBuilder<CommandSourceStack, Long> purgeIdArg() {
        return idArg().executes(this::runAdminPurgePrompt)
                .then(Commands.literal("confirm").executes(this::runAdminPurge));
    }

    private int runAdminRestore(CommandContext<CommandSourceStack> ctx) {
        return runAdminById(
                ctx, services.archivePlayerWarp()::adminRestore, PlayerwarpsMessageKey.PWARP_ADMIN_RESTORED);
    }

    /** First {@code purge}/{@code delete} invocation: warn the operator and require the {@code confirm} step (invariant 5). */
    private int runAdminPurgePrompt(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        long id = ctx.getArgument("id", Long.class);
        feedback.send(sender, PlayerwarpsMessageKey.PWARP_ADMIN_PURGE_CONFIRM, Map.of("id", Long.toString(id)));
        return Command.SINGLE_SUCCESS;
    }

    private int runAdminPurge(CommandContext<CommandSourceStack> ctx) {
        return runAdminById(
                ctx, services.archivePlayerWarp()::adminHardDelete, PlayerwarpsMessageKey.PWARP_ADMIN_PURGED);
    }

    private int runAdminSetOwner(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        long id = ctx.getArgument("id", Long.class);
        String playerName = ctx.getArgument("player", String.class);
        PlayerRef admin = actor(ctx);
        services.scheduler().async(() -> {
            Optional<PlayerRef> newOwner = services.players().findByName(playerName);
            if (newOwner.isEmpty()) {
                onPlayer(admin, () -> unknownPlayer(sender, playerName));
                return;
            }
            Result<Unit, PlayerWarpError> result =
                    services.transferPlayerWarp().adminSetOwner(PlayerWarpId.of(id), newOwner.get());
            Map<String, String> placeholders =
                    Map.of("id", Long.toString(id), "player", newOwner.get().name());
            onPlayer(
                    admin,
                    () -> feedback.send(
                            sender, adminKey(result, PlayerwarpsMessageKey.PWARP_ADMIN_SETOWNER), placeholders));
        });
        return Command.SINGLE_SUCCESS;
    }

    /**
     * {@code admin reload}: this is not the module-reload authority, that lives on the {@code /uxmess reload} path,
     * so point the operator there rather than reimplementing a hot-reload the services holder cannot reach.
     */
    private int runAdminReload(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        feedback.send(sender, PlayerwarpsMessageKey.PWARP_ADMIN_RELOAD_HINT, Map.of());
        return Command.SINGLE_SUCCESS;
    }

    /**
     * The shared shape of an admin-by-id verb: read the {@code id} on the tick thread, run {@code action} off it, and
     * bridge the operator-facing result back: {@code okKey} on success, the not-found notice on a stale id.
     */
    private int runAdminById(
            CommandContext<CommandSourceStack> ctx,
            Function<PlayerWarpId, Result<Unit, PlayerWarpError>> action,
            PlayerwarpsMessageKey okKey) {
        CommandSender sender = ctx.getSource().getSender();
        long id = ctx.getArgument("id", Long.class);
        PlayerRef admin = actor(ctx);
        Map<String, String> placeholders = Map.of("id", Long.toString(id));
        services.scheduler().async(() -> {
            Result<Unit, PlayerWarpError> result = action.apply(PlayerWarpId.of(id));
            onPlayer(admin, () -> feedback.send(sender, adminKey(result, okKey), placeholders));
        });
        return Command.SINGLE_SUCCESS;
    }

    private static PlayerwarpsMessageKey adminKey(Result<Unit, PlayerWarpError> result, PlayerwarpsMessageKey okKey) {
        return result.isOk() ? okKey : PlayerwarpsMessageKey.PWARP_ADMIN_NOT_FOUND;
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Long> idArg() {
        return Commands.argument("id", LongArgumentType.longArg(1));
    }

    /** The {@code /pwarp members add <player> <role>} literal token for a grantable role: {@code co-owner}, {@code manager}. */
    private static String roleToken(WarpRole role) {
        return role.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /**
     * Parse a single {@code <number><unit>} ban-duration token ({@code 7d}, {@code 12h}) into a positive
     * {@link Duration}, or {@link Optional#empty()} when the token is not a duration (which the caller reads as a
     * permanent ban whose token begins the reason instead).
     */
    private static Optional<Duration> parseBanDuration(String token) {
        Matcher matcher = BAN_DURATION.matcher(token);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        // A wildly out-of-range digit run (Long overflow, or a Duration that exceeds its own bounds) is not a valid
        // duration, treat it like any other non-duration token and let it fold into the ban reason, never throwing
        // out of the command handler.
        try {
            long amount = Long.parseLong(matcher.group(1));
            Duration span =
                    switch (Character.toLowerCase(matcher.group(2).charAt(0))) {
                        case 's' -> Duration.ofSeconds(amount);
                        case 'm' -> Duration.ofMinutes(amount);
                        case 'h' -> Duration.ofHours(amount);
                        case 'd' -> Duration.ofDays(amount);
                        case 'w' -> Duration.ofDays(Math.multiplyExact(amount, 7L));
                        default -> Duration.ZERO;
                    };
            return span.isZero() ? Optional.empty() : Optional.of(span);
        } catch (NumberFormatException | ArithmeticException overflow) {
            return Optional.empty();
        }
    }

    /**
     * The reason for a ban given the first optional token and the greedy remainder. When the first token was a
     * duration the reason is just the remainder; when it was not a duration the ban is permanent and that token
     * begins the reason, folded back in ahead of the remainder so nothing the operator typed is lost.
     */
    private static Optional<String> banReason(String durationToken, Optional<Duration> duration, String reasonText) {
        if (!durationToken.isBlank() && duration.isEmpty()) {
            String combined = reasonText.isBlank() ? durationToken : durationToken + " " + reasonText;
            return optionalText(combined);
        }
        return optionalText(reasonText);
    }

    private int runListOwn(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef viewer = ref(sender);
        // The owned-warp read hits the database; run it off the tick thread (the sink delivers on the region thread).
        services.scheduler().async(() -> services.listPlayerWarps().own(viewer));
        return Command.SINGLE_SUCCESS;
    }

    private int runListOther(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        PlayerRef viewer = actor(ctx);
        String ownerName = ctx.getArgument("player", String.class);
        services.scheduler().async(() -> {
            Optional<PlayerRef> owner = services.players().findByName(ownerName);
            if (owner.isEmpty()) {
                onPlayer(viewer, () -> unknownPlayer(sender, ownerName));
                return;
            }
            services.listPlayerWarps().publicOf(viewer, owner.get(), owner.get().name());
        });
        return Command.SINGLE_SUCCESS;
    }

    private LiteralArgumentBuilder<CommandSourceStack> visibilitySubtree() {
        return verb("visibility", PUBLIC_PERMISSION)
                .then(Commands.literal("public").then(nameArg().executes(ctx -> dispatch(ctx, this::makePublic))))
                .then(Commands.literal("private").then(nameArg().executes(ctx -> dispatch(ctx, this::makePrivate))));
    }

    private void makePublic(PlayerRef who, PlayerWarpName name) {
        services.visibility().setPublic(who, name);
    }

    private void makePrivate(PlayerRef who, PlayerWarpName name) {
        services.visibility().setPrivate(who, name);
    }

    private LiteralArgumentBuilder<CommandSourceStack> accessSubtree() {
        RequiredArgumentBuilder<CommandSourceStack, String> name = nameArg();
        for (WarpAccess access : WarpAccess.values()) {
            name.then(Commands.literal(access.name()).executes(ctx -> runAccess(ctx, access)));
        }
        return verb("access", ACCESS_PERMISSION).then(name);
    }

    private LiteralArgumentBuilder<CommandSourceStack> passwordSubtree() {
        return verb("password", PASSWORD_PERMISSION)
                .then(nameArg()
                        .then(Commands.literal("clear").executes(this::runClearPassword))
                        .then(Commands.argument("password", StringArgumentType.greedyString())
                                .executes(this::runSetPassword)));
    }

    private LiteralArgumentBuilder<CommandSourceStack> priceSubtree() {
        return verb("price", PRICE_PERMISSION)
                .then(nameArg()
                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.0))
                                .executes(ctx -> runPrice(ctx, Optional.empty()))
                                .then(Commands.argument("currency", StringArgumentType.word())
                                        .executes(ctx -> runPrice(
                                                ctx, Optional.of(ctx.getArgument("currency", String.class)))))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> listSubtree() {
        return verb("list", LIST_PERMISSION)
                .executes(this::runListOwn)
                .then(CommandSuggestions.playerArgument("player").executes(this::runListOther));
    }

    /**
     * A {@code <literal> <name> [text...]} metadata subcommand: the name node clears the value (no text) and the
     * greedy text node sets it, both routed to the same handler which reads the optional text with {@link #argOr}.
     */
    private LiteralArgumentBuilder<CommandSourceStack> textVerb(
            String literal, String permission, String argName, Command<CommandSourceStack> handler) {
        return verb(literal, permission)
                .then(nameArg()
                        .executes(handler)
                        .then(Commands.argument(argName, StringArgumentType.greedyString())
                                .executes(handler)));
    }

    /**
     * Apply a metadata edit that takes an {@code Optional<T>}: an absent, blank, or {@code "-"} text clears (empty);
     * any other text is built through {@code factory} and, when it validates, dispatched. A value the value object
     * rejects sends the invalid-value notice and runs nothing, so a bad argument is never a stack trace.
     */
    private <T> int editOptional(
            CommandContext<CommandSourceStack> ctx, String argName, Function<String, T> factory, MetadataEdit<T> edit) {
        Target target = target(ctx);
        if (target == null) {
            return Command.SINGLE_SUCCESS;
        }
        Optional<String> raw = optionalText(argOr(ctx, argName));
        if (raw.isEmpty()) {
            services.scheduler().async(() -> edit.apply(target.who(), target.name(), Optional.empty()));
            return Command.SINGLE_SUCCESS;
        }
        T value;
        try {
            value = factory.apply(raw.get());
        } catch (IllegalArgumentException invalid) {
            feedback.send(target.sender(), PlayerwarpsMessageKey.PWARP_INVALID_NAME, Map.of("value", raw.get()));
            return Command.SINGLE_SUCCESS;
        }
        services.scheduler().async(() -> edit.apply(target.who(), target.name(), Optional.of(value)));
        return Command.SINGLE_SUCCESS;
    }

    /** One metadata edit verb: set (or clear) a value object on a warp. The result is delivered by the use case. */
    @FunctionalInterface
    private interface MetadataEdit<T> {
        void apply(PlayerRef who, PlayerWarpName name, Optional<T> value);
    }
}

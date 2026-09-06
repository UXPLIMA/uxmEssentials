package com.uxplima.uxmessentials.vaults.adapter.inbound.command;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.vaults.adapter.VaultServices;
import com.uxplima.uxmessentials.vaults.application.VaultNotifier;
import com.uxplima.uxmessentials.vaults.application.VaultSummary;
import com.uxplima.uxmessentials.vaults.domain.Vault;
import com.uxplima.uxmessentials.vaults.domain.VaultError;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * {@code /vault}, {@code /vault <n>}, {@code /vault <player> [n]}, {@code /vault delete <n>}, and
 * {@code /vault delete <player> <n>} (docs/10-feature-modules.md §15.11). The no-argument form opens the
 * player's default vault when they own one (or allocates the first within quota) and lists their vault numbers
 * when several exist; {@code <n>} opens the Nth; {@code <player> [n]} is the audit-logged staff inspect
 * override, gated by {@code uxmessentials.vault.others} on that branch so a non-staff player never sees it.
 * {@code delete <n>} removes the caller's own vault (refunding the configured amount when economy is on);
 * {@code delete <player> <n>} is the audited staff override, gated by {@code uxmessentials.vault.admin.delete},
 * and pays no refund. Deletion is direct, like PlayerVaultsX it does not prompt for confirmation, and always
 * audited, so a destructive staff override is replayable from the audit channel.
 *
 * <p>{@code rename <n> [name]} and {@code icon <n> [material]} set a vault's presentation in the selector menu:
 * a name (or no name, to clear it) gated by {@code uxmessentials.vault.rename}, and an icon material, explicit
 * or the caller's held item, gated by {@code uxmessentials.vault.icon} and the {@code appearance.allow-custom-icon}
 * config switch. The name is length-checked against {@code appearance.max-name-length} and an icon material is
 * validated against the real material registry before the write, so a bad value is refused up front.
 *
 * <p>This handler maps the Bukkit source to the kernel value objects and hands off to the use cases. The GUI
 * open is entity-bound, so it is scheduled on the viewer's region thread through the kernel {@code Scheduler};
 * the delete touches the database, so it runs off the tick thread through {@link Scheduler#async}. The admin
 * inspect override targets an online player (the GUI opens on their entity); the admin delete accepts an
 * offline owner by name, since deleting a vault opens no window.
 */
@NullMarked
public final class VaultCommand implements CommandRegistration {

    private static final String USE = "uxmessentials.vault.use";
    private static final String OTHERS = "uxmessentials.vault.others";
    private static final String ADMIN_DELETE = "uxmessentials.vault.admin.delete";
    private static final String RENAME = "uxmessentials.vault.rename";
    private static final String ICON = "uxmessentials.vault.icon";
    private static final int DEFAULT_INDEX = 1;

    private final VaultServices services;
    private final VaultNotifier notifier;
    private final Scheduler scheduler;

    public VaultCommand(VaultServices services) {
        this.services = Objects.requireNonNull(services, "services");
        this.notifier = services.notifier();
        this.scheduler = services.kernel().scheduler();
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("vault")
                .requires(src -> src.getSender().hasPermission(USE))
                .executes(this::openDefaultOrList)
                .then(Commands.literal("info").executes(this::info))
                .then(Commands.literal("delete")
                        .then(Commands.argument("n", IntegerArgumentType.integer(1))
                                .executes(ctx -> deleteOwn(ctx, ctx.getArgument("n", Integer.class))))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .requires(src -> src.getSender().hasPermission(ADMIN_DELETE))
                                .suggests(onlinePlayerSuggestions())
                                .then(Commands.argument("idx", IntegerArgumentType.integer(1))
                                        .executes(ctx -> deleteOther(ctx, ctx.getArgument("idx", Integer.class))))))
                .then(Commands.literal("rename")
                        .requires(src -> src.getSender().hasPermission(RENAME))
                        .then(Commands.argument("n", IntegerArgumentType.integer(1))
                                .executes(ctx -> renameOwn(ctx, ctx.getArgument("n", Integer.class), null))
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .executes(ctx -> renameOwn(
                                                ctx,
                                                ctx.getArgument("n", Integer.class),
                                                StringArgumentType.getString(ctx, "name"))))))
                .then(Commands.literal("icon")
                        .requires(src -> src.getSender().hasPermission(ICON))
                        .then(Commands.argument("n", IntegerArgumentType.integer(1))
                                .executes(ctx -> iconHeld(ctx, ctx.getArgument("n", Integer.class)))
                                .then(Commands.argument("material", StringArgumentType.word())
                                        .executes(ctx -> iconNamed(
                                                ctx,
                                                ctx.getArgument("n", Integer.class),
                                                StringArgumentType.getString(ctx, "material"))))))
                .then(Commands.argument("n", IntegerArgumentType.integer(1))
                        .executes(ctx -> openOwn(ctx, ctx.getArgument("n", Integer.class))))
                .then(Commands.argument("player", ArgumentTypes.player())
                        .suggests(CommandSuggestions.singlePlayerTarget())
                        .requires(src -> src.getSender().hasPermission(OTHERS))
                        .executes(ctx -> openOther(ctx, DEFAULT_INDEX))
                        .then(Commands.argument("idx", IntegerArgumentType.integer(1))
                                .executes(ctx -> openOther(ctx, ctx.getArgument("idx", Integer.class)))))
                .build();
    }

    @Override
    public String description() {
        return "Open one of your vaults, delete a vault, or audit another player's vault.";
    }

    private int openDefaultOrList(CommandContext<CommandSourceStack> ctx) {
        Player player = playerOrReject(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        PlayerRef viewer = BukkitRefs.toRef(player);
        // The owned-vault index read is a database scan; run it off the tick thread, then decide on the
        // viewer's region thread which window to open. With several vaults owned, open the picker menu (or fall
        // back to the chat list when it is disabled); a single owned vault (or none) opens vault 1 directly.
        scheduler.async(() -> {
            List<Integer> owned = ownedIndices(viewer);
            scheduler.onEntity(viewer, () -> {
                if (owned.size() > 1) {
                    if (services.selectorEnabled()) {
                        services.selector().open(viewer);
                    } else {
                        notifier.list(viewer, owned);
                    }
                    return;
                }
                openOwn(player, viewer, owned.isEmpty() ? DEFAULT_INDEX : owned.get(0));
            });
        });
        return Command.SINGLE_SUCCESS;
    }

    /** The ascending one-based indices of {@code viewer}'s vaults, read from the summary listing. */
    private List<Integer> ownedIndices(PlayerRef viewer) {
        return services.listVaults().list(viewer).asValue().orElse(List.of()).stream()
                .map(VaultSummary::index)
                .toList();
    }

    private int info(CommandContext<CommandSourceStack> ctx) {
        Player player = playerOrReject(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        PlayerRef viewer = BukkitRefs.toRef(player);
        // The owned-count read scans the database; run it off the tick thread. The info notice is read-only and
        // delivered through the sink, which bridges to the viewer's region thread itself.
        scheduler.async(() -> {
            int owned = ownedIndices(viewer).size();
            notifier.showInfo(
                    viewer,
                    owned,
                    services.amountQuota().resolve(viewer),
                    services.sizeQuota().resolve(viewer));
        });
        return Command.SINGLE_SUCCESS;
    }

    private int openOwn(CommandContext<CommandSourceStack> ctx, int index) {
        Player player = playerOrReject(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        openOwn(player, BukkitRefs.toRef(player), index);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Resolve and open {@code viewer}'s vault {@code index}. The open reads the vault's contents from the
     * database, so the read runs off the tick thread; the window open (and any rejection) bridges back to the
     * viewer's region thread.
     */
    private void openOwn(Player player, PlayerRef viewer, int index) {
        scheduler.async(() -> {
            Result<Vault, VaultError> resolved = services.openVault().open(viewer, index);
            if (resolved.isErr()) {
                rejectOwn(viewer, index, resolved.errorOrThrow());
                return;
            }
            openWindow(player, viewer, viewer, resolved.orElseThrow());
        });
    }

    private int openOther(CommandContext<CommandSourceStack> ctx, int index) {
        Player staff = playerOrReject(ctx);
        if (staff == null) {
            return Command.SINGLE_SUCCESS;
        }
        PlayerRef actor = BukkitRefs.toRef(staff);
        Optional<Player> resolved = resolveTarget(ctx, actor);
        if (resolved.isEmpty()) {
            return Command.SINGLE_SUCCESS;
        }
        PlayerRef owner = BukkitRefs.toRef(resolved.get());
        // The admin open reads the owner's vault contents from the database; run it off the tick thread, then
        // bridge the window open back to the staff member's region thread (the staff member is the one viewing).
        scheduler.async(() -> {
            Vault vault = services.openAdminVault().open(actor, owner, index);
            openWindow(staff, actor, owner, vault);
            notifier.adminOpened(actor, owner, index);
        });
        return Command.SINGLE_SUCCESS;
    }

    private int deleteOwn(CommandContext<CommandSourceStack> ctx, int index) {
        Player player = playerOrReject(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        PlayerRef owner = BukkitRefs.toRef(player);
        // A DB write, run it off the tick thread; the use case notifies the player of the outcome itself.
        scheduler.async(() -> services.deleteVault().delete(owner, index));
        return Command.SINGLE_SUCCESS;
    }

    private int deleteOther(CommandContext<CommandSourceStack> ctx, int index) {
        Player staff = playerOrReject(ctx);
        if (staff == null) {
            return Command.SINGLE_SUCCESS;
        }
        PlayerRef actor = BukkitRefs.toRef(staff);
        String typed = StringArgumentType.getString(ctx, "player");
        Optional<PlayerRef> owner = services.kernel().playerLookup().findByName(typed);
        if (owner.isEmpty()) {
            notifier.unknownTarget(actor, typed);
            return Command.SINGLE_SUCCESS;
        }
        // A DB write, run it off the tick thread; the use case audits the override and notifies the actor.
        scheduler.async(() -> services.deleteVault().deleteOther(actor, owner.get(), index));
        return Command.SINGLE_SUCCESS;
    }

    private int renameOwn(CommandContext<CommandSourceStack> ctx, int index, @Nullable String rawName) {
        Player player = playerOrReject(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        PlayerRef owner = BukkitRefs.toRef(player);
        @Nullable String name = normalizeName(rawName);
        if (name != null && name.length() > services.maxNameLength()) {
            notifier.nameTooLong(owner, services.maxNameLength());
            return Command.SINGLE_SUCCESS;
        }
        // A DB write, run it off the tick thread; the use case notifies the player of the set/clear outcome.
        scheduler.async(() -> services.renameVault().rename(owner, index, name));
        return Command.SINGLE_SUCCESS;
    }

    private int iconNamed(CommandContext<CommandSourceStack> ctx, int index, String rawMaterial) {
        Player player = playerOrReject(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        PlayerRef owner = BukkitRefs.toRef(player);
        if (!services.allowCustomIcon()) {
            notifier.iconNotAllowed(owner);
            return Command.SINGLE_SUCCESS;
        }
        Material material = Material.matchMaterial(rawMaterial);
        if (material == null || material.isAir()) {
            notifier.unknownMaterial(owner, rawMaterial);
            return Command.SINGLE_SUCCESS;
        }
        applyIcon(owner, index, material);
        return Command.SINGLE_SUCCESS;
    }

    private int iconHeld(CommandContext<CommandSourceStack> ctx, int index) {
        Player player = playerOrReject(ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        PlayerRef owner = BukkitRefs.toRef(player);
        if (!services.allowCustomIcon()) {
            notifier.iconNotAllowed(owner);
            return Command.SINGLE_SUCCESS;
        }
        Material held = player.getInventory().getItemInMainHand().getType();
        if (held.isAir()) {
            notifier.iconNoHeldItem(owner);
            return Command.SINGLE_SUCCESS;
        }
        applyIcon(owner, index, held);
        return Command.SINGLE_SUCCESS;
    }

    private void applyIcon(PlayerRef owner, int index, Material material) {
        // A DB write, run it off the tick thread; the use case confirms the new icon to the player itself.
        scheduler.async(() -> services.setVaultIcon().setIcon(owner, index, material.name()));
    }

    /** Trim a rename argument; a blank (or absent) name clears the display name rather than setting it. */
    private static @Nullable String normalizeName(@Nullable String rawName) {
        if (rawName == null) {
            return null;
        }
        String trimmed = rawName.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Optional<Player> resolveTarget(CommandContext<CommandSourceStack> ctx, PlayerRef actor) {
        try {
            PlayerSelectorArgumentResolver resolver = ctx.getArgument("player", PlayerSelectorArgumentResolver.class);
            List<Player> matched = resolver.resolve(ctx.getSource());
            if (matched.isEmpty()) {
                notifier.unknownTarget(actor, typedTarget(ctx));
                return Optional.empty();
            }
            return Optional.of(matched.get(0));
        } catch (CommandSyntaxException unmatched) {
            notifier.unknownTarget(actor, typedTarget(ctx));
            return Optional.empty();
        }
    }

    private static String typedTarget(CommandContext<CommandSourceStack> ctx) {
        return ctx.getNodes().stream()
                .filter(node -> "player".equals(node.getNode().getName()))
                .findFirst()
                .map(node -> node.getRange().get(ctx.getInput()))
                .orElse("");
    }

    private static SuggestionProvider<CommandSourceStack> onlinePlayerSuggestions() {
        return (ctx, builder) -> {
            String prefix = builder.getRemainingLowerCase();
            for (Player online : Bukkit.getOnlinePlayers()) {
                if (online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    builder.suggest(online.getName());
                }
            }
            return builder.buildFuture();
        };
    }

    private void openWindow(Player player, PlayerRef viewer, PlayerRef owner, Vault vault) {
        scheduler.onEntity(viewer, () -> {
            services.view().open(player, viewer, owner, vault);
            if (viewer.uuid().equals(owner.uuid())) {
                notifier.opened(viewer, vault.index());
            }
        });
    }

    private void rejectOwn(PlayerRef viewer, int index, VaultError error) {
        switch (error) {
            case AMOUNT_EXCEEDED -> notifier.amountExceeded(viewer, index);
            case NONE_OWNED -> notifier.noneOwned(viewer);
            case CANNOT_AFFORD ->
                notifier.cannotAfford(
                        viewer, services.chargeSettings().costToCreate().toPlainString());
            case DELETE_UNKNOWN -> notifier.deleteUnknown(viewer, index);
            case VAULT_UNKNOWN -> notifier.renameUnknown(viewer, index);
        }
    }

    private @Nullable Player playerOrReject(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (sender instanceof Player player) {
            return player;
        }
        notifier.playersOnly(PlayerRef.system(sender.getName()));
        return null;
    }
}

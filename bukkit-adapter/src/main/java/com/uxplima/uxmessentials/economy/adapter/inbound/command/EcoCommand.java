package com.uxplima.uxmessentials.economy.adapter.inbound.command;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.economy.adapter.EconomyServices;
import com.uxplima.uxmessentials.economy.adapter.inbound.gui.EconomyAdminGuiViews;
import com.uxplima.uxmessentials.economy.application.EconomyMessageKey;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * {@code /eco give|take|set|reset <player> <amount> [currency]} and the bulk
 * {@code /eco giveall|giverandom|resetall [amount] [currency]} (alias {@code /economy},
 * docs/10-feature-modules.md §15.4). {@code /eco note give <player> <amount> [currency]} mints an admin-issued
 * banknote for a player without debiting anyone.
 */
@NullMarked
public final class EcoCommand extends EconomyCommandSupport implements CommandRegistration {

    private static final String BASE = "uxmessentials.economy.admin";

    private final BanknoteMinter minter;
    private final @Nullable EconomyAdminGuiViews adminGui;

    public EcoCommand(Plugin plugin, EconomyServices services, Messages messages) {
        this(plugin, services, messages, null);
    }

    public EcoCommand(
            Plugin plugin, EconomyServices services, Messages messages, @Nullable EconomyAdminGuiViews adminGui) {
        super(services, messages);
        this.minter = new BanknoteMinter(plugin, messages, services.banknoteStore());
        this.adminGui = adminGui;
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("eco")
                .requires(src -> src.getSender().hasPermission(BASE))
                .then(targetVerb("give", "give"))
                .then(targetVerb("take", "take"))
                .then(targetVerb("set", "set"))
                .then(resetVerb())
                .then(bulkAmountVerb("giveall", "bulk"))
                .then(bulkAmountVerb("giverandom", "bulk"))
                .then(resetAllVerb())
                .then(giveRandomRangeVerb())
                .then(historyVerb())
                .then(logsVerb())
                .then(noteVerb())
                .then(Commands.literal("backup")
                        .requires(src -> src.getSender().hasPermission(BASE + ".backup"))
                        .executes(this::runBackup))
                .then(Commands.literal("export")
                        .requires(src -> src.getSender().hasPermission(BASE + ".backup"))
                        .executes(this::runExport))
                .then(Commands.literal("restore")
                        .requires(src -> src.getSender().hasPermission(BASE + ".restore"))
                        .then(CommandSuggestions.playerArgument("player")
                                .then(Commands.argument("date", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            for (String date :
                                                    services.backupManager().getBackupDates()) {
                                                builder.suggest(date);
                                            }
                                            return builder.buildFuture();
                                        })
                                        .executes(this::runRestore))))
                .build();
    }

    @Override
    public List<String> aliases() {
        return List.of("economy");
    }

    @Override
    public String description() {
        return "Eco-admin balance mutations.";
    }

    /**
     * Bare {@code /eco} opens the admin economy GUI when GUIs are enabled (the {@code .requires(...)} admin gate
     * is carried by the rebind, so a non-admin still cannot open it). Absent the GUI stack, when economy GUIs
     * are off. This returns empty and bare {@code /eco} falls through to its usage text; every raw
     * {@code /eco give|take|set|reset …} subcommand is unaffected either way.
     */
    @Override
    public java.util.Optional<Command<CommandSourceStack>> guiRoot() {
        EconomyAdminGuiViews gui = adminGui;
        if (gui == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(ctx -> {
            if (ctx.getSource().getSender() instanceof Player sender) {
                gui.hub().open(sender, BukkitRefs.toRef(sender));
            }
            return Command.SINGLE_SUCCESS;
        });
    }

    private LiteralArgumentBuilder<CommandSourceStack> targetVerb(String literal, String node) {
        return Commands.literal(literal)
                .requires(src -> src.getSender().hasPermission(BASE + "." + node))
                .executes(ctx -> usage(ctx, "eco " + literal, "<player> <amount> [currency]", "Economy " + literal))
                .then(CommandSuggestions.playerArgument("player")
                        .executes(ctx ->
                                usage(ctx, "eco " + literal, "<player> <amount> [currency]", "Economy " + literal))
                        .then(Commands.argument("amount", StringArgumentType.word())
                                .executes(ctx -> runTarget(ctx, literal))
                                .then(currencyArgument().executes(ctx -> runTarget(ctx, literal)))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> resetVerb() {
        return Commands.literal("reset")
                .requires(src -> src.getSender().hasPermission(BASE + ".set"))
                .then(CommandSuggestions.playerArgument("player")
                        .executes(ctx -> runReset(ctx))
                        .then(currencyArgument().executes(ctx -> runReset(ctx))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> bulkAmountVerb(String literal, String node) {
        return Commands.literal(literal)
                .requires(src -> src.getSender().hasPermission(BASE + "." + node))
                .then(Commands.argument("amount", StringArgumentType.word())
                        .executes(ctx -> runBulk(ctx, literal))
                        .then(currencyArgument().executes(ctx -> runBulk(ctx, literal))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> resetAllVerb() {
        return Commands.literal("resetall")
                .requires(src -> src.getSender().hasPermission(BASE + ".bulk"))
                .then(Commands.literal("--confirm")
                        .executes(ctx -> runResetAll(ctx))
                        .then(currencyArgument().executes(ctx -> runResetAll(ctx))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> giveRandomRangeVerb() {
        return Commands.literal("give-random")
                .requires(src -> src.getSender().hasPermission(BASE + ".give"))
                .then(CommandSuggestions.playerArgument("player")
                        .then(Commands.argument("min", StringArgumentType.word())
                                .then(Commands.argument("max", StringArgumentType.word())
                                        .executes(this::runGiveRandomRange)
                                        .then(currencyArgument().executes(this::runGiveRandomRange)))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> historyVerb() {
        return Commands.literal("history")
                .requires(src -> src.getSender().hasPermission(BASE + ".history"))
                .executes(this::runHistorySelf)
                .then(CommandSuggestions.playerArgument("player").executes(this::runHistoryTarget));
    }

    private LiteralArgumentBuilder<CommandSourceStack> logsVerb() {
        return Commands.literal("logs")
                .requires(src -> src.getSender().hasPermission(BASE + ".logs"))
                .executes(this::runLogs);
    }

    private LiteralArgumentBuilder<CommandSourceStack> noteVerb() {
        return Commands.literal("note")
                .requires(src -> src.getSender().hasPermission(BASE + ".note"))
                .then(Commands.literal("give")
                        .then(CommandSuggestions.playerArgument("player")
                                .then(Commands.argument("amount", StringArgumentType.word())
                                        .executes(this::runNoteGive)
                                        .then(currencyArgument().executes(this::runNoteGive)))));
    }

    private int runTarget(CommandContext<CommandSourceStack> ctx, String verb) {
        Resolved resolved = resolveTargeted(ctx);
        if (resolved == null) {
            return Command.SINGLE_SUCCESS;
        }
        offTick(() -> dispatchTarget(verb, resolved.actor, resolved.target, resolved.amount));
        return Command.SINGLE_SUCCESS;
    }

    private void dispatchTarget(String verb, PlayerRef actor, PlayerRef target, Money money) {
        switch (verb) {
            case "give" -> services.ecoAdmin().give(actor, target, money);
            case "take" -> services.ecoAdmin().take(actor, target, money);
            case "set" -> services.ecoAdmin().set(actor, target, money);
            default -> throw new IllegalStateException("unknown eco verb: " + verb);
        }
    }

    /**
     * Resolve an admin command's named target to a {@link PlayerRef}, online first and then from the server's
     * offline profile cache, so {@code /eco give|take|set|reset} can act on a player who is not currently online
     * (the use cases materialise the offline row with {@code ensureOwner}). The cached lookup is non-blocking;
     * an unknown name yields empty and the caller reports unknown-player.
     */
    private Optional<PlayerRef> resolveAnyTarget(String name) {
        Optional<PlayerRef> online = services.players().findOnlineByName(name);
        if (online.isPresent()) {
            return online;
        }
        org.bukkit.OfflinePlayer offline = org.bukkit.Bukkit.getOfflinePlayerIfCached(name);
        return offline != null && offline.getName() != null
                ? Optional.of(new PlayerRef(offline.getUniqueId(), offline.getName()))
                : Optional.empty();
    }

    private int runExport(CommandContext<CommandSourceStack> ctx) {
        PlayerRef actor = actor(ctx);
        offTick(() -> {
            try {
                String file = services.backupManager().exportCsv();
                services.notifier()
                        .send(actor, EconomyMessageKey.ECO_ADMIN_EXPORT_CREATED, java.util.Map.of("file", file));
            } catch (Exception e) {
                services.notifier()
                        .send(
                                actor,
                                EconomyMessageKey.ECO_ADMIN_BACKUP_FAILED,
                                java.util.Map.of("error", String.valueOf(e.getMessage())));
            }
        });
        return Command.SINGLE_SUCCESS;
    }

    private int runReset(CommandContext<CommandSourceStack> ctx) {
        PlayerRef actor = actor(ctx);
        Optional<Currency> currency = currency(ctx);
        if (currency.isEmpty()) {
            rejectUnknownCurrency(actor);
            return Command.SINGLE_SUCCESS;
        }
        String targetName = ctx.getArgument("player", String.class);
        offTick(() -> resolveAndReset(actor, targetName, currency.get()));
        return Command.SINGLE_SUCCESS;
    }

    private void resolveAndReset(PlayerRef actor, String targetName, Currency currency) {
        Optional<PlayerRef> target = resolveAnyTarget(targetName);
        if (target.isEmpty()) {
            rejectUnknownTarget(actor, targetName);
            return;
        }
        services.ecoAdmin().reset(actor, target.get(), currency);
    }

    private int runBulk(CommandContext<CommandSourceStack> ctx, String verb) {
        PlayerRef actor = actor(ctx);
        Optional<Currency> currency = currency(ctx);
        if (currency.isEmpty()) {
            rejectUnknownCurrency(actor);
            return Command.SINGLE_SUCCESS;
        }
        Optional<Money> amount = amount(ctx.getArgument("amount", String.class), currency.get(), actor);
        if (amount.isEmpty()) {
            return Command.SINGLE_SUCCESS;
        }
        Money money = amount.get();
        List<PlayerRef> online = EcoTargets.online();
        offTick(() -> dispatchBulk(verb, actor, money, online));
        return Command.SINGLE_SUCCESS;
    }

    private void dispatchBulk(String verb, PlayerRef actor, Money money, List<PlayerRef> online) {
        if ("giveall".equals(verb)) {
            services.ecoAdmin().giveAll(actor, online, money);
            return;
        }
        EcoTargets.randomOnline(online).ifPresent(chosen -> services.ecoAdmin().giveRandom(actor, chosen, money));
    }

    private int runResetAll(CommandContext<CommandSourceStack> ctx) {
        PlayerRef actor = actor(ctx);
        Optional<Currency> currency = currency(ctx);
        if (currency.isEmpty()) {
            rejectUnknownCurrency(actor);
            return Command.SINGLE_SUCCESS;
        }
        Currency resolved = currency.get();
        List<PlayerRef> online = EcoTargets.online();
        offTick(() -> services.ecoAdmin().resetAll(actor, online, resolved));
        return Command.SINGLE_SUCCESS;
    }

    private int runGiveRandomRange(CommandContext<CommandSourceStack> ctx) {
        PlayerRef actor = actor(ctx);
        Optional<Currency> currency = currency(ctx);
        if (currency.isEmpty()) {
            rejectUnknownCurrency(actor);
            return Command.SINGLE_SUCCESS;
        }
        Optional<Money> minAmount = amount(ctx.getArgument("min", String.class), currency.get(), actor);
        Optional<Money> maxAmount = amount(ctx.getArgument("max", String.class), currency.get(), actor);
        if (minAmount.isEmpty() || maxAmount.isEmpty()) {
            return Command.SINGLE_SUCCESS;
        }
        String targetName = ctx.getArgument("player", String.class);
        offTick(() -> resolveAndGiveRandomRange(actor, targetName, minAmount.get(), maxAmount.get()));
        return Command.SINGLE_SUCCESS;
    }

    private void resolveAndGiveRandomRange(PlayerRef actor, String targetName, Money min, Money max) {
        Optional<PlayerRef> target = resolveAnyTarget(targetName);
        if (target.isEmpty()) {
            rejectUnknownTarget(actor, targetName);
            return;
        }
        services.ecoAdmin().giveRandomRange(actor, target.get(), min, max);
    }

    private int runHistorySelf(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.historyView().open(ref(sender), sender.getUniqueId(), sender.getName());
        return Command.SINGLE_SUCCESS;
    }

    private int runHistoryTarget(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        String targetName = ctx.getArgument("player", String.class);
        Optional<PlayerRef> target = resolveAnyTarget(targetName);
        if (target.isEmpty()) {
            rejectUnknownTarget(ref(sender), targetName);
            return Command.SINGLE_SUCCESS;
        }
        services.historyView()
                .open(ref(sender), target.get().uuid(), target.get().name());
        return Command.SINGLE_SUCCESS;
    }

    private int runLogs(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        services.historyView().open(ref(sender), null, "Global");
        return Command.SINGLE_SUCCESS;
    }

    private int runNoteGive(CommandContext<CommandSourceStack> ctx) {
        PlayerRef actor = actor(ctx);
        Optional<Currency> currency = currency(ctx);
        if (currency.isEmpty()) {
            rejectUnknownCurrency(actor);
            return Command.SINGLE_SUCCESS;
        }
        Optional<Money> amount = amount(ctx.getArgument("amount", String.class), currency.get(), actor);
        if (amount.isEmpty()) {
            return Command.SINGLE_SUCCESS;
        }
        String targetName = ctx.getArgument("player", String.class);
        offTick(() -> resolveAndGiveNote(actor, targetName, amount.get()));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Resolve the named recipient and hand them an admin-issued banknote of {@code amount} with no debit. The
     * recipient must be online to receive the item; an unknown name and an offline player are each reported to
     * the issuer. The note is minted off the tick thread (it registers the dupe token in the store) and the item
     * is delivered on the recipient's entity thread, dropping any overflow at their feet.
     */
    private void resolveAndGiveNote(PlayerRef actor, String targetName, Money amount) {
        Optional<PlayerRef> target = resolveAnyTarget(targetName);
        if (target.isEmpty()) {
            rejectUnknownTarget(actor, targetName);
            return;
        }
        Player online = org.bukkit.Bukkit.getPlayer(target.get().uuid());
        if (online == null) {
            services.notifier()
                    .send(
                            actor,
                            EconomyMessageKey.ECO_ADMIN_NOTE_OFFLINE,
                            Map.of("player", target.get().name()));
            return;
        }
        ItemStack banknote = minter.mint(target.get(), amount);
        services.scheduler().onEntity(target.get(), () -> deliverNote(actor, target.get(), online, banknote, amount));
    }

    private void deliverNote(PlayerRef actor, PlayerRef target, Player online, ItemStack banknote, Money amount) {
        Map<Integer, ItemStack> remaining = online.getInventory().addItem(banknote);
        for (ItemStack left : remaining.values()) {
            online.getWorld().dropItemNaturally(online.getLocation(), left);
        }
        services.notifier()
                .send(
                        actor,
                        EconomyMessageKey.ECO_ADMIN_NOTE_GIVEN,
                        Map.of(
                                "player",
                                target.name(),
                                "amount",
                                services.notifier().amount(amount)));
    }

    private @org.jspecify.annotations.Nullable Resolved resolveTargeted(CommandContext<CommandSourceStack> ctx) {
        PlayerRef actor = actor(ctx);
        Optional<Currency> currency = currency(ctx);
        if (currency.isEmpty()) {
            rejectUnknownCurrency(actor);
            return null;
        }
        Optional<Money> amount = amount(ctx.getArgument("amount", String.class), currency.get(), actor);
        if (amount.isEmpty()) {
            return null;
        }
        Optional<PlayerRef> target = resolveAnyTarget(ctx.getArgument("player", String.class));
        if (target.isEmpty()) {
            rejectUnknownTarget(actor, ctx.getArgument("player", String.class));
            return null;
        }
        return new Resolved(actor, target.get(), amount.get());
    }

    private int runBackup(CommandContext<CommandSourceStack> ctx) {
        PlayerRef actor = actor(ctx);
        offTick(() -> {
            try {
                String timestamp = services.backupManager().backupAll();
                services.notifier()
                        .send(
                                actor,
                                EconomyMessageKey.ECO_ADMIN_BACKUP_CREATED,
                                java.util.Map.of("timestamp", timestamp));
            } catch (Exception e) {
                services.notifier()
                        .send(
                                actor,
                                EconomyMessageKey.ECO_ADMIN_BACKUP_FAILED,
                                java.util.Map.of("error", String.valueOf(e.getMessage())));
            }
        });
        return Command.SINGLE_SUCCESS;
    }

    private int runRestore(CommandContext<CommandSourceStack> ctx) {
        PlayerRef actor = actor(ctx);
        String targetName = ctx.getArgument("player", String.class);
        String date = ctx.getArgument("date", String.class);
        offTick(() -> {
            Optional<PlayerRef> target = services.players().findByName(targetName);
            if (target.isEmpty()) {
                services.notifier()
                        .send(
                                actor,
                                EconomyMessageKey.ECO_ADMIN_RESTORE_TARGET_UNKNOWN,
                                java.util.Map.of("player", targetName));
                return;
            }
            try {
                boolean success = services.backupManager().restorePlayer(target.get(), date);
                if (success) {
                    services.notifier()
                            .send(
                                    actor,
                                    EconomyMessageKey.ECO_ADMIN_RESTORE_SUCCESS,
                                    java.util.Map.of("player", targetName, "date", date));
                } else {
                    services.notifier().send(actor, EconomyMessageKey.ECO_ADMIN_RESTORE_NOT_FOUND);
                }
            } catch (Exception e) {
                services.notifier()
                        .send(
                                actor,
                                EconomyMessageKey.ECO_ADMIN_RESTORE_FAILED,
                                java.util.Map.of("error", String.valueOf(e.getMessage())));
            }
        });
        return Command.SINGLE_SUCCESS;
    }

    private record Resolved(PlayerRef actor, PlayerRef target, Money amount) {}
}

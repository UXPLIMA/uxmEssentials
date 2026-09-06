package com.uxplima.uxmessentials.trade.adapter.inbound.command;

import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandFeedback;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.SharedMessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.trade.adapter.inbound.gui.CrossServerTradeView;
import com.uxplima.uxmessentials.trade.adapter.inbound.gui.TradeSessions;
import com.uxplima.uxmessentials.trade.application.PendingTradeRequest;
import com.uxplima.uxmessentials.trade.application.TradeConfig;
import com.uxplima.uxmessentials.trade.application.TradeCooldown;
import com.uxplima.uxmessentials.trade.application.TradeMessageKey;
import com.uxplima.uxmessentials.trade.application.TradeRequests;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * {@code /trade <player>} opens a trade request; {@code /trade accept|deny [player]} resolves one. A request is held in
 * the in-memory {@link TradeRequests} book (expiring after the configured window) and only opens the dual-inventory
 * window, through the injected opener, once the target accepts and both players are still online, unblocked, and
 * within range. The send is gated on the self-check, the busy-check for either side, the configured request distance,
 * and the per-player {@link TradeCooldown}; every refusal maps to a {@link TradeMessageKey} line. Gated on
 * {@code uxmessentials.trade.use}, which ships {@code true}.
 *
 * <p>The command only orchestrates: the pending-request and cooldown decisions are the pure application classes' job,
 * the window mechanics the view's. Its handlers take resolved {@link Player}s so the flow is driven directly by tests.
 */
@NullMarked
public final class TradeCommand implements CommandRegistration {

    /** The permission every player holds by default so they can start a trade. */
    public static final String PERMISSION = "uxmessentials.trade.use";

    private final TradeRequests requests;
    private final TradeCooldown cooldown;
    private final TradeConfig config;
    private final TradeSessions sessions;
    private final BiConsumer<Player, Player> opener;
    private final CommandFeedback feedback;

    /** The cross-server view, present only when cross-server trading is wired; {@code null} means local-only. */
    private final @Nullable CrossServerTradeView crossServer;

    public TradeCommand(
            TradeRequests requests,
            TradeCooldown cooldown,
            TradeConfig config,
            TradeSessions sessions,
            BiConsumer<Player, Player> opener,
            Messages messages,
            @Nullable CrossServerTradeView crossServer) {
        this.requests = Objects.requireNonNull(requests, "requests");
        this.cooldown = Objects.requireNonNull(cooldown, "cooldown");
        this.config = Objects.requireNonNull(config, "config");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.opener = Objects.requireNonNull(opener, "opener");
        this.feedback = new CommandFeedback(Objects.requireNonNull(messages, "messages"));
        this.crossServer = crossServer;
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("trade")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::usage)
                .then(resolutionNode("accept", this::accept))
                .then(resolutionNode("deny", this::deny))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(CommandSuggestions.onlinePlayers())
                        .executes(this::runSend))
                .build();
    }

    @Override
    public String description() {
        return "/trade <player> to request a trade; /trade accept|deny [player] to answer one.";
    }

    private com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> resolutionNode(
            String literal, BiConsumer<Player, @Nullable String> handler) {
        return Commands.literal(literal)
                .executes(ctx -> runResolution(ctx, handler, null))
                .then(Commands.argument("player", StringArgumentType.word())
                        .suggests(this::suggestPendingRequesters)
                        .executes(ctx -> runResolution(ctx, handler, StringArgumentType.getString(ctx, "player"))));
    }

    /** Complete {@code /trade accept|deny <name>} against the live requesters awaiting the invoking player's answer. */
    private java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions>
            suggestPendingRequesters(
                    CommandContext<CommandSourceStack> ctx,
                    com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        if (ctx.getSource().getSender() instanceof Player player) {
            String prefix = builder.getRemaining().toLowerCase(java.util.Locale.ROOT);
            for (String name : requests.pendingRequesterNames(player.getUniqueId())) {
                if (name.toLowerCase(java.util.Locale.ROOT).startsWith(prefix)) {
                    builder.suggest(name);
                }
            }
        }
        return builder.buildFuture();
    }

    private int runResolution(
            CommandContext<CommandSourceStack> ctx,
            BiConsumer<Player, @Nullable String> handler,
            @Nullable String requesterName) {
        Player sender = playerOrNull(ctx);
        if (sender == null) {
            return 0;
        }
        handler.accept(sender, requesterName);
        return Command.SINGLE_SUCCESS;
    }

    private int runSend(CommandContext<CommandSourceStack> ctx) {
        Player sender = playerOrNull(ctx);
        if (sender == null) {
            return 0;
        }
        String name = StringArgumentType.getString(ctx, "player");
        Player local = Bukkit.getPlayerExact(name);
        if (local != null) {
            send(sender, local);
        } else {
            sendRemote(sender, name);
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Open a cross-server trade request to a player who is not on this backend. Refused when cross-server trading is
     * off (no view wired), and gated on the same self / busy / cooldown checks the local send applies; the target's
     * backend answers over the bus.
     */
    void sendRemote(Player sender, String name) {
        if (crossServer == null) {
            notify(sender, TradeMessageKey.TRADE_CROSS_SERVER_DISABLED, Map.of());
        } else if (sender.getName().equalsIgnoreCase(name)) {
            notify(sender, TradeMessageKey.TRADE_CANNOT_TRADE_SELF, Map.of());
        } else if (sessions.isTrading(sender.getUniqueId()) || crossServer.isTrading(sender.getUniqueId())) {
            notify(sender, TradeMessageKey.TRADE_ALREADY_TRADING, Map.of());
        } else {
            long remaining = cooldown.remainingSeconds(sender.getUniqueId());
            if (remaining > 0) {
                notify(sender, TradeMessageKey.TRADE_ON_COOLDOWN, Map.of("seconds", Long.toString(remaining)));
                return;
            }
            crossServer.invite(sender, name);
            cooldown.stamp(sender.getUniqueId());
        }
    }

    /** Open a request from {@code sender} to {@code target}, gating on self / busy / distance / cooldown. */
    void send(Player sender, Player target) {
        if (sender.getUniqueId().equals(target.getUniqueId())) {
            notify(sender, TradeMessageKey.TRADE_CANNOT_TRADE_SELF, Map.of());
        } else if (sessions.isTrading(sender.getUniqueId())) {
            notify(sender, TradeMessageKey.TRADE_ALREADY_TRADING, Map.of());
        } else if (sessions.isTrading(target.getUniqueId())) {
            notify(sender, TradeMessageKey.TRADE_TARGET_BUSY, Map.of("player", target.getName()));
        } else if (!withinRequestDistance(sender, target)) {
            notify(sender, TradeMessageKey.TRADE_TOO_FAR, Map.of("player", target.getName()));
        } else {
            long remaining = cooldown.remainingSeconds(sender.getUniqueId());
            if (remaining > 0) {
                notify(sender, TradeMessageKey.TRADE_ON_COOLDOWN, Map.of("seconds", Long.toString(remaining)));
                return;
            }
            requests.submit(ref(sender), ref(target));
            cooldown.stamp(sender.getUniqueId());
            notify(sender, TradeMessageKey.TRADE_REQUEST_SENT, Map.of("player", target.getName()));
            notify(target, TradeMessageKey.TRADE_REQUEST_RECEIVED, Map.of("player", sender.getName()));
        }
    }

    /** Accept a pending request (the newest, or the one from {@code requesterName}) and open the window. */
    void accept(Player sender, @Nullable String requesterName) {
        if (sessions.isTrading(sender.getUniqueId())) {
            notify(sender, TradeMessageKey.TRADE_ALREADY_TRADING, Map.of());
            return;
        }
        TradeRequests.Match match = requests.resolve(sender.getUniqueId(), requesterName);
        switch (match.status()) {
            case NONE -> notify(sender, TradeMessageKey.TRADE_NO_REQUEST, Map.of());
            case EXPIRED -> notifyExpired(sender, requireRequest(match));
            case MATCHED -> openAccepted(sender, requireRequest(match));
        }
    }

    /** Deny a pending request; the requester keeps nothing staked (nothing is opened until accept). */
    void deny(Player sender, @Nullable String requesterName) {
        TradeRequests.Match match = requests.resolve(sender.getUniqueId(), requesterName);
        switch (match.status()) {
            case NONE -> notify(sender, TradeMessageKey.TRADE_NO_REQUEST, Map.of());
            case EXPIRED -> notifyExpired(sender, requireRequest(match));
            case MATCHED ->
                notify(
                        sender,
                        TradeMessageKey.TRADE_DENIED,
                        Map.of("player", requireRequest(match).requester().name()));
        }
    }

    private void openAccepted(Player sender, PendingTradeRequest request) {
        PlayerRef requester = request.requester();
        Player requesterPlayer = Bukkit.getPlayer(requester.uuid());
        if (requesterPlayer == null || !requesterPlayer.isOnline()) {
            notifyExpired(sender, request);
        } else if (sessions.isTrading(requester.uuid())) {
            notify(sender, TradeMessageKey.TRADE_TARGET_BUSY, Map.of("player", requester.name()));
        } else if (!withinRequestDistance(requesterPlayer, sender)) {
            notify(sender, TradeMessageKey.TRADE_TOO_FAR, Map.of("player", requester.name()));
        } else {
            notify(sender, TradeMessageKey.TRADE_ACCEPTED, Map.of("player", requester.name()));
            opener.accept(requesterPlayer, sender);
        }
    }

    /** Whether {@code a} and {@code b} are close enough to trade: within the limit and same world, or no limit set. */
    private boolean withinRequestDistance(Player a, Player b) {
        if (!config.hasDistanceLimit()) {
            return true;
        }
        Location from = Objects.requireNonNull(a.getLocation(), "sender location");
        Location to = Objects.requireNonNull(b.getLocation(), "target location");
        World world = from.getWorld();
        if (world == null || !world.equals(to.getWorld())) {
            return false;
        }
        double limit = config.requestDistance();
        return from.distanceSquared(to) <= limit * limit;
    }

    private void notifyExpired(Player sender, PendingTradeRequest request) {
        notify(
                sender,
                TradeMessageKey.TRADE_REQUEST_EXPIRED,
                Map.of("player", request.requester().name()));
    }

    private void notify(Player player, MessageKey key, Map<String, String> placeholders) {
        feedback.send(player, key, placeholders);
    }

    private int usage(CommandContext<CommandSourceStack> ctx) {
        feedback.send(ctx.getSource().getSender(), TradeMessageKey.TRADE_USAGE);
        return Command.SINGLE_SUCCESS;
    }

    private @Nullable Player playerOrNull(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (sender instanceof Player player) {
            return player;
        }
        feedback.send(sender, SharedMessageKey.COMMAND_PLAYERS_ONLY);
        return null;
    }

    private static PendingTradeRequest requireRequest(TradeRequests.Match match) {
        return Objects.requireNonNull(match.request(), "a matched or expired request carries its request");
    }

    private static PlayerRef ref(Player player) {
        return new PlayerRef(player.getUniqueId(), player.getName());
    }
}

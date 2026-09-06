package com.uxplima.uxmessentials.teleport.adapter.inbound.command;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.bukkit.entity.Player;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.WorldRef;
import com.uxplima.uxmessentials.teleport.adapter.TeleportServices;
import com.uxplima.uxmessentials.teleport.adapter.inbound.gui.RtpMenu;
import com.uxplima.uxmessentials.teleport.application.TeleportMessageKey;
import org.jspecify.annotations.NullMarked;

/**
 * {@code /rtp} (alias {@code /wild}): served O(1) from the pre-warmed per-world safe-location queue. The
 * bare command reads the player's current world, sends the searching notice, and hands the background path to
 * {@link com.uxplima.uxmessentials.teleport.application.ResolveRtp}; the requester never waits on a chunk
 * load: the queue is polled and a refill is fired asynchronously.
 *
 * <p>The bare {@code /rtp} follows the {@code rtp.command-opens-gui} config toggle (default {@code true}): on it opens
 * the menu-engine world picker; off it random-teleports the sender within their current world. {@code /rtp gui} (gated
 * {@code uxmessentials.rtp.gui}) always opens the picker regardless of the toggle, and {@code /rtp biome <biome>}
 * (gated {@code uxmessentials.rtp.biome}) targets a specific biome through
 * {@link com.uxplima.uxmessentials.teleport.application.ResolveBiomeRtp}.
 *
 * <p>{@code /rtp <target>} carries a single greedy word that is disambiguated at execution: an online player name
 * (with {@code uxmessentials.rtp.others}) forces <em>that player</em> to random-teleport within their own world; a
 * loaded world name random-teleports the sender within that world; anything else is reported as an unknown target.
 * {@code biome} and {@code gui} stay explicit literals, so Brigadier matches them first and the {@code target}
 * argument is the fallback: the suggestions offer both online players and loaded world names.
 */
@NullMarked
public final class RtpCommand extends TeleportCommandSupport implements CommandRegistration {

    private static final String PERMISSION = "uxmessentials.rtp.use";
    static final String BIOME_PERMISSION = "uxmessentials.rtp.biome";
    static final String OTHERS_PERMISSION = "uxmessentials.rtp.others";
    static final String GUI_PERMISSION = "uxmessentials.rtp.gui";

    private final RtpMenu menu;

    /** When true, a bare {@code /rtp} opens the picker; when false it random-teleports in place. */
    private final boolean openGuiOnBare;

    public RtpCommand(TeleportServices services, Messages messages, RtpMenu menu, boolean openGuiOnBare) {
        super(services, messages);
        this.menu = Objects.requireNonNull(menu, "menu");
        this.openGuiOnBare = openGuiOnBare;
    }

    @Override
    public LiteralCommandNode<CommandSourceStack> build() {
        return Commands.literal("rtp")
                .requires(src -> src.getSender().hasPermission(PERMISSION))
                .executes(this::runBare)
                .then(Commands.literal("gui")
                        .requires(src -> src.getSender().hasPermission(GUI_PERMISSION))
                        .executes(this::openGui))
                .then(Commands.literal("biome")
                        .requires(src -> src.getSender().hasPermission(BIOME_PERMISSION))
                        .then(Commands.argument("biome", StringArgumentType.word())
                                .suggests(this::suggestBiomes)
                                .executes(this::runBiome)))
                .then(Commands.argument("target", StringArgumentType.word())
                        .suggests(this::suggestTargets)
                        .executes(this::runTarget))
                .build();
    }

    @Override
    public String description() {
        return "Randomly teleport within the world.";
    }

    @Override
    public List<String> aliases() {
        return List.of("wild");
    }

    private int runBare(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        bare(sender);
        return Command.SINGLE_SUCCESS;
    }

    /**
     * The bare {@code /rtp} behaviour: open the world-picker GUI when {@code command-opens-gui} is set (the default),
     * otherwise random-teleport {@code sender} within their current world.
     */
    void bare(Player sender) {
        if (openGuiOnBare) {
            menu.open(ref(sender));
        } else {
            rtpHere(sender);
        }
    }

    /** Random-teleport {@code sender} within their current world through the pre-warmed pool. */
    private void rtpHere(Player sender) {
        PlayerRef who = ref(sender);
        WorldRef world = BukkitRefs.toRef(sender.getWorld());
        services.notifier().send(who, TeleportMessageKey.RTP_SEARCHING);
        services.resolveRtp().background(who, world);
    }

    private int openGui(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        menu.open(ref(sender));
        return Command.SINGLE_SUCCESS;
    }

    private int runTarget(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        route(sender, ctx.getArgument("target", String.class));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Disambiguate {@code raw}: an online player (with {@code uxmessentials.rtp.others}) is forced to random-teleport
     * within their own world; a loaded world name random-teleports the sender there; anything else is reported. A
     * staff force routes the <em>target</em> through the resolver: the issuer is never charged.
     */
    void route(Player sender, String raw) {
        PlayerRef who = ref(sender);
        Player targetPlayer = sender.getServer().getPlayerExact(raw);
        if (targetPlayer != null && sender.hasPermission(OTHERS_PERMISSION)) {
            PlayerRef target = ref(targetPlayer);
            services.notifier().send(target, TeleportMessageKey.RTP_SEARCHING);
            services.resolveRtp().background(target, BukkitRefs.toRef(targetPlayer.getWorld()));
            return;
        }
        Optional<WorldRef> world = services.worlds().findByName(raw);
        if (world.isPresent()) {
            services.notifier().send(who, TeleportMessageKey.RTP_SEARCHING);
            services.resolveRtp().background(who, world.get());
            return;
        }
        services.notifier().send(who, TeleportMessageKey.RTP_UNKNOWN_TARGET, Map.of("target", raw));
    }

    private int runBiome(CommandContext<CommandSourceStack> ctx) {
        Player sender = player(ctx);
        if (sender == null) {
            return 0;
        }
        PlayerRef who = ref(sender);
        WorldRef world = BukkitRefs.toRef(sender.getWorld());
        String biome = ctx.getArgument("biome", String.class);
        // The use case resolves the biome key (an unknown one is reported), gates cost/cooldown, sends the
        // searching notice, and runs the async biome-targeted search: the command only forwards the raw key.
        services.resolveBiomeRtp().targeted(who, world, biome);
        return Command.SINGLE_SUCCESS;
    }

    private CompletableFuture<Suggestions> suggestBiomes(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (String key : services.biomeCatalog().keys()) {
            if (key.startsWith(prefix)) {
                builder.suggest(key);
            }
        }
        return builder.buildFuture();
    }

    /** Offer both online players and loaded world names for the {@code <target>} argument. */
    private CompletableFuture<Suggestions> suggestTargets(
            CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String prefix = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (Player online : ctx.getSource().getSender().getServer().getOnlinePlayers()) {
            if (online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                builder.suggest(online.getName());
            }
        }
        for (org.bukkit.World world : ctx.getSource().getSender().getServer().getWorlds()) {
            if (world.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                builder.suggest(world.getName());
            }
        }
        return builder.buildFuture();
    }
}

package com.uxplima.uxmessentials.ranks.adapter.outbound;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.playerstate.application.port.PlaytimeRepository;
import com.uxplima.uxmessentials.ranks.application.CurrentRank;
import com.uxplima.uxmessentials.ranks.application.port.RankEconomy;
import com.uxplima.uxmessentials.ranks.application.port.RankRequirementEvaluator;
import com.uxplima.uxmessentials.ranks.domain.RankRequirement;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.PlaceholderApiSupport;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Permissions;
import com.uxplima.uxmessentials.shared.display.ConditionContext;
import com.uxplima.uxmessentials.shared.display.ConditionParser;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The single {@link RankRequirementEvaluator} implementation, dispatching each {@link RankRequirement} to the
 * seam that can answer it: a money check reuses the ranks {@link RankEconomy} balance read, a permission check
 * the shared {@link Permissions} port, a placeholder comparison the shared display-condition model (the same one
 * the scoreboard/tablist filters use) resolved through the PlaceholderAPI bridge, and an item check the live
 * player's inventory. Two kinds the shared systems do not cover are added here: <b>playtime</b> against the
 * DB-backed {@link PlaytimeRepository}, and <b>previous-rank</b> against the player's stored rank resolved by
 * {@link CurrentRank}.
 *
 * <p>Every unresolvable condition fails closed, an offline player for an inventory or placeholder check, a
 * malformed amount, an unknown material, or a money requirement on a server with no economy, so a rankup never
 * slips through on a condition that could not be verified.
 */
@NullMarked
public final class BukkitRankRequirementEvaluator implements RankRequirementEvaluator {

    private final Server server;
    private final Permissions permissions;
    private final PlaytimeRepository playtime;
    private final CurrentRank currentRank;
    private final Optional<RankEconomy> economy;
    private final Logger log;

    public BukkitRankRequirementEvaluator(
            Server server,
            Permissions permissions,
            PlaytimeRepository playtime,
            CurrentRank currentRank,
            Optional<RankEconomy> economy,
            Logger log) {
        this.server = Objects.requireNonNull(server, "server");
        this.permissions = Objects.requireNonNull(permissions, "permissions");
        this.playtime = Objects.requireNonNull(playtime, "playtime");
        this.currentRank = Objects.requireNonNull(currentRank, "currentRank");
        this.economy = Objects.requireNonNull(economy, "economy");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public boolean passes(PlayerRef who, RankRequirement requirement) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(requirement, "requirement");
        return switch (requirement.type()) {
            case MONEY -> hasMoney(who, requirement.value());
            case PLAYTIME -> hasPlaytime(who, requirement.value());
            case PERMISSION -> permissions.has(who, requirement.value());
            case PREVIOUS_RANK -> isOnRank(who, requirement.value());
            case PLACEHOLDER -> placeholderPasses(who, requirement.value());
            case ITEM -> hasItem(who, requirement.value());
        };
    }

    private boolean hasMoney(PlayerRef who, String value) {
        Optional<BigDecimal> amount = parseAmount(value);
        if (amount.isEmpty() || economy.isEmpty()) {
            return false;
        }
        return economy.get().canAfford(who, amount.get(), "default");
    }

    private boolean hasPlaytime(PlayerRef who, String value) {
        long required;
        try {
            required = Long.parseLong(value.strip());
        } catch (NumberFormatException malformed) {
            log.warn("event=rank_requirement_skipped type=playtime reason=not_a_number value={}", value);
            return false;
        }
        long tracked = playtime.summaryOf(who.uuid(), LocalDate.now(ZoneId.systemDefault()))
                .totalCombined()
                .toSeconds();
        return tracked >= required;
    }

    private boolean isOnRank(PlayerRef who, String value) {
        return currentRank
                .of(who.uuid())
                .map(standing -> standing.rank().id().value().equalsIgnoreCase(value.strip()))
                .orElse(false);
    }

    private boolean placeholderPasses(PlayerRef who, String value) {
        Player player = server.getPlayer(who.uuid());
        if (player == null) {
            return false;
        }
        ConditionContext ctx = new ConditionContext(
                player::hasPermission,
                player.getWorld().getName(),
                player.getGameMode().name(),
                PlaceholderApiSupport.messageBridge(who.uuid()));
        return ConditionParser.parse(value).matches(ctx);
    }

    private boolean hasItem(PlayerRef who, String value) {
        Player player = server.getPlayer(who.uuid());
        if (player == null) {
            return false;
        }
        String[] parts = value.strip().split("\\s+", 2);
        Material material = Material.matchMaterial(parts[0].toUpperCase(Locale.ROOT));
        if (material == null || !material.isItem()) {
            log.warn("event=rank_requirement_skipped type=item reason=unknown_material value={}", value);
            return false;
        }
        return player.getInventory().contains(material, amountOrOne(parts));
    }

    private int amountOrOne(String[] parts) {
        if (parts.length < 2) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(parts[1].strip()));
        } catch (NumberFormatException malformed) {
            return 1;
        }
    }

    private Optional<BigDecimal> parseAmount(String value) {
        try {
            return Optional.of(new BigDecimal(value.strip()));
        } catch (NumberFormatException malformed) {
            log.warn("event=rank_requirement_skipped type=money reason=not_a_number value={}", value);
            return Optional.empty();
        }
    }
}

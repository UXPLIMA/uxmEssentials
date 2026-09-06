package com.uxplima.uxmessentials.ranks.adapter.outbound;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Server;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.ranks.application.port.RankActionRunner;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.ClickActionValueCheck;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.ClickActionRunner;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.action.ClickAction;
import com.uxplima.uxmessentials.shared.domain.action.ClickActionType;
import com.uxplima.uxmessentials.shared.domain.action.ClickTrigger;
import org.jspecify.annotations.NullMarked;

/**
 * Bridges the ranks context's {@link RankActionRunner} port to the shared {@link ClickActionRunner} the npc and
 * hologram contexts already run their interaction chains through. A rank's raw action line is
 * {@code <type> <value…>}. The same {@code type value} shape those contexts use, minus the click trigger, since
 * a rankup is not a click, so each line is parsed into a {@link ClickAction} carrying {@link ClickTrigger#ANY}
 * and the chain is run once as if the player had triggered it. A line whose type word is unknown, or that carries
 * no value, is logged and skipped so one operator typo never aborts the rest of the rank's actions.
 *
 * <p>Rankup runs on the player's own region thread (the {@code /rankup} command thread), which is where the
 * shared runner's Bukkit effects are safe, so the chain runs inline with no region hop. When the player is not
 * online (an autorank promotion of an offline player in a later phase) there is no viewer to act on, so the
 * call is a no-op.
 */
@NullMarked
public final class BukkitRankActionRunner implements RankActionRunner {

    private final Server server;
    private final ClickActionRunner runner;
    private final Logger log;

    public BukkitRankActionRunner(Server server, ClickActionRunner runner, Logger log) {
        this.server = Objects.requireNonNull(server, "server");
        this.runner = Objects.requireNonNull(runner, "runner");
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public void run(PlayerRef who, List<String> actionLines) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(actionLines, "actionLines");
        if (actionLines.isEmpty()) {
            return;
        }
        Player player = server.getPlayer(who.uuid());
        if (player == null) {
            return;
        }
        List<ClickAction> actions = parse(actionLines);
        if (!actions.isEmpty()) {
            runner.run(player, actions, true);
        }
    }

    private List<ClickAction> parse(List<String> actionLines) {
        List<ClickAction> actions = new ArrayList<>(actionLines.size());
        for (String line : actionLines) {
            parseLine(line).ifPresent(actions::add);
        }
        return actions;
    }

    private Optional<ClickAction> parseLine(String line) {
        String[] parts = line.strip().split("\\s+", 2);
        if (parts.length < 2) {
            log.warn("event=rank_action_skipped reason=no_value line={}", line);
            return Optional.empty();
        }
        Optional<ClickActionType> type = ClickActionValueCheck.parseType(parts[0]);
        if (type.isEmpty()) {
            log.warn("event=rank_action_skipped reason=unknown_type line={}", line);
            return Optional.empty();
        }
        return Optional.of(new ClickAction(ClickTrigger.ANY, type.get(), parts[1]));
    }
}

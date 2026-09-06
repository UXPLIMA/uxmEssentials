package com.uxplima.uxmessentials.playerwarps.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.uxplima.uxmessentials.playerwarps.adapter.PlayerWarpServices;
import com.uxplima.uxmessentials.playerwarps.adapter.inbound.command.PlayerWarpCommand;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.playerwarps.domain.ReservedWarpNames;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/**
 * Pins the reserved-name invariant from the command side: every top-level {@code /pwarp} subcommand literal, the
 * ones that compete with the bare {@code <name>} teleport argument for the first token. Must be a reserved warp
 * name in {@link ReservedWarpNames}, so a warp can never be created under a name a verb literal would shadow. A new
 * subcommand added to the tree without reserving its token fails this guard. It reads only tracked sources (the
 * command builder and the {@code :core} reserved set), so it is CI-safe and never touches {@code docs/} or the
 * server runtime.
 */
class PlayerWarpReservedNameDriftTest {

    @Test
    void everyTopLevelSubcommandLiteralIsAReservedWarpName() {
        List<String> literals = topLevelLiterals();

        assertThat(literals)
                .as("the /pwarp tree must register subcommand literals")
                .isNotEmpty();
        for (String literal : literals) {
            PlayerWarpName asName;
            try {
                asName = PlayerWarpName.of(literal);
            } catch (IllegalArgumentException notAWarpNameShape) {
                // A literal that is not even a valid warp-name shape can never shadow a warp, so it need not be
                // reserved; only reservable (name-shaped) literals must appear in the set.
                continue;
            }
            assertThat(ReservedWarpNames.isReserved(asName))
                    .as("subcommand literal '%s' must be a reserved warp name so it cannot become unreachable", literal)
                    .isTrue();
        }
    }

    private static List<String> topLevelLiterals() {
        LiteralCommandNode<CommandSourceStack> pwarp =
                new PlayerWarpCommand(mock(PlayerWarpServices.class), new KeyMessages()).build();
        return pwarp.getChildren().stream()
                .filter(child -> child instanceof LiteralCommandNode)
                .map(CommandNode::getName)
                .toList();
    }

    private static final class KeyMessages implements Messages {
        @Override
        public String resolve(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
            return key.key();
        }
    }
}

package com.uxplima.uxmessentials.scoreboard.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

import com.uxplima.uxmessentials.shared.display.ConditionContext;
import com.uxplima.uxmessentials.shared.display.DisplayCondition;
import org.junit.jupiter.api.Test;

class SidebarConfigTest {

    private static ConditionContext ctx(Predicate<String> hasPermission, String world, String gamemode) {
        Function<String, String> resolve = s -> Map.<String, String>of().getOrDefault(s, s);
        return new ConditionContext(hasPermission, world, gamemode, resolve);
    }

    private static DisplayContent content(String line) {
        return new DisplayContent(Optional.of("<gold>Server"), List.of(line), true, Duration.ofSeconds(1L), Set.of());
    }

    private static SidebarBoard board(String name, DisplayCondition condition, int priority) {
        return new SidebarBoard(name, content(name), condition, priority);
    }

    @Test
    void emptyConfigSelectsNothing() {
        assertThat(SidebarConfig.empty().select(ctx(n -> true, "world", "SURVIVAL")))
                .isEmpty();
    }

    @Test
    void noMatchingBoardSelectsNothing() {
        SidebarConfig config = new SidebarConfig(List.of(
                board("staff", new DisplayCondition.Permission("uxmessentials.staff"), 10),
                board("vip", new DisplayCondition.Permission("uxmessentials.vip"), 5)));

        assertThat(config.select(ctx(n -> false, "world", "SURVIVAL"))).isEmpty();
    }

    @Test
    void highestPriorityMatchingBoardWins() {
        SidebarConfig config = new SidebarConfig(List.of(
                board("default", DisplayCondition.always(), 0),
                board("staff", new DisplayCondition.Permission("uxmessentials.staff"), 10)));

        Optional<SidebarBoard> selected = config.select(ctx("uxmessentials.staff"::equals, "world", "SURVIVAL"));

        assertThat(selected).isPresent();
        assertThat(selected.get().name()).isEqualTo("staff");
    }

    @Test
    void aLowerPriorityBoardWinsWhenTheHigherOneDoesNotMatch() {
        SidebarConfig config = new SidebarConfig(List.of(
                board("default", DisplayCondition.always(), 0),
                board("staff", new DisplayCondition.Permission("uxmessentials.staff"), 10)));

        // No staff permission: the always-true default at priority 0 is the only match.
        Optional<SidebarBoard> selected = config.select(ctx(n -> false, "world", "SURVIVAL"));

        assertThat(selected).isPresent();
        assertThat(selected.get().name()).isEqualTo("default");
    }

    @Test
    void priorityTiesAreBrokenByConfigOrderFirstWins() {
        // Both match (always), both priority 5: the one declared first wins.
        SidebarConfig config = new SidebarConfig(
                List.of(board("first", DisplayCondition.always(), 5), board("second", DisplayCondition.always(), 5)));

        Optional<SidebarBoard> selected = config.select(ctx(n -> true, "world", "SURVIVAL"));

        assertThat(selected).isPresent();
        assertThat(selected.get().name()).isEqualTo("first");
    }

    @Test
    void defensivelyCopiesItsBoards() {
        java.util.List<SidebarBoard> mutable =
                new java.util.ArrayList<>(List.of(board("default", DisplayCondition.always(), 0)));
        SidebarConfig config = new SidebarConfig(mutable);

        mutable.add(board("sneaky", DisplayCondition.always(), 100));

        assertThat(config.boards()).hasSize(1);
    }
}

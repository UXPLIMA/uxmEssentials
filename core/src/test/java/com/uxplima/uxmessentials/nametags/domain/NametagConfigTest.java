package com.uxplima.uxmessentials.nametags.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import com.uxplima.uxmessentials.shared.display.ConditionContext;
import com.uxplima.uxmessentials.shared.display.DisplayCondition;
import org.junit.jupiter.api.Test;

class NametagConfigTest {

    private static ConditionContext ctx(Predicate<String> hasPermission, String world, String gamemode) {
        Function<String, String> resolve = s -> Map.<String, String>of().getOrDefault(s, s);
        return new ConditionContext(hasPermission, world, gamemode, resolve);
    }

    private static NametagFormat format(String name, DisplayCondition condition, int priority) {
        return new NametagFormat(
                name,
                condition,
                priority,
                List.of("<gold>" + name),
                NametagAppearance.defaults(),
                NametagVisibility.alwaysVisible());
    }

    @Test
    void emptyConfigSelectsNothing() {
        assertThat(NametagConfig.empty().select(ctx(n -> true, "world", "SURVIVAL")))
                .isEmpty();
    }

    @Test
    void noMatchingFormatSelectsNothing() {
        NametagConfig config = new NametagConfig(List.of(
                format("staff", new DisplayCondition.Permission("uxmessentials.staff"), 10),
                format("vip", new DisplayCondition.Permission("uxmessentials.vip"), 5)));

        assertThat(config.select(ctx(n -> false, "world", "SURVIVAL"))).isEmpty();
    }

    @Test
    void highestPriorityMatchingFormatWins() {
        NametagConfig config = new NametagConfig(List.of(
                format("default", DisplayCondition.always(), 0),
                format("staff", new DisplayCondition.Permission("uxmessentials.staff"), 10)));

        Optional<NametagFormat> selected = config.select(ctx("uxmessentials.staff"::equals, "world", "SURVIVAL"));

        assertThat(selected).isPresent();
        assertThat(selected.get().name()).isEqualTo("staff");
    }

    @Test
    void aLowerPriorityFormatWinsWhenTheHigherOneDoesNotMatch() {
        NametagConfig config = new NametagConfig(List.of(
                format("default", DisplayCondition.always(), 0),
                format("staff", new DisplayCondition.Permission("uxmessentials.staff"), 10)));

        // No staff permission: the always-true default at priority 0 is the only match.
        Optional<NametagFormat> selected = config.select(ctx(n -> false, "world", "SURVIVAL"));

        assertThat(selected).isPresent();
        assertThat(selected.get().name()).isEqualTo("default");
    }

    @Test
    void priorityTiesAreBrokenByListOrderFirstWins() {
        // Both match (always), both priority 5, the format earlier in the list (the codec sorts alphabetically) wins.
        NametagConfig config = new NametagConfig(
                List.of(format("alpha", DisplayCondition.always(), 5), format("beta", DisplayCondition.always(), 5)));

        Optional<NametagFormat> selected = config.select(ctx(n -> true, "world", "SURVIVAL"));

        assertThat(selected).isPresent();
        assertThat(selected.get().name()).isEqualTo("alpha");
    }

    @Test
    void defensivelyCopiesItsFormats() {
        java.util.List<NametagFormat> mutable =
                new java.util.ArrayList<>(List.of(format("default", DisplayCondition.always(), 0)));
        NametagConfig config = new NametagConfig(mutable);

        mutable.add(format("sneaky", DisplayCondition.always(), 100));

        assertThat(config.formats()).hasSize(1);
    }
}

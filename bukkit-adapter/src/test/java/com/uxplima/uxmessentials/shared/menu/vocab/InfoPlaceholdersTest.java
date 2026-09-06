package com.uxplima.uxmessentials.shared.menu.vocab;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab.InfoPlaceholders;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/**
 * The Bukkit-free part of the info-placeholder pack. {@link InfoPlaceholders#worldClock(long)} is a pure Minecraft
 * tick-to-clock conversion exercised across the day, and the {@code stat_} fallback's fail-soft is checked for an
 * unparseable statistic name. That path short-circuits on the bad name before it ever looks up an online player, so
 * it resolves to empty with no server. The live inventory / world / statistic reads need a real player and are
 * covered by {@link com.uxplima.uxmessentials.shared.menu.InfoPlaceholderGoldenTest}.
 */
class InfoPlaceholdersTest {

    @Test
    void worldClockConvertsTicksToTheInGameTwentyFourHourClock() {
        // hour = (ticks/1000 + 6) % 24, minute = (ticks % 1000) * 60 / 1000, each zero-padded.
        assertThat(InfoPlaceholders.worldClock(0)).isEqualTo("06:00"); // dawn: (0+6)%24 = 6
        assertThat(InfoPlaceholders.worldClock(6000)).isEqualTo("12:00"); // noon: (6+6)%24 = 12
        assertThat(InfoPlaceholders.worldClock(18000)).isEqualTo("00:00"); // midnight: (18+6)%24 = 0
        assertThat(InfoPlaceholders.worldClock(23000)).isEqualTo("05:00"); // pre-dawn: (23+6)%24 = 5
    }

    @Test
    void worldClockCarriesTheSubHourTicksIntoMinutes() {
        // 500 ticks past the hour mark = half of the 1000-tick hour = 30 minutes.
        assertThat(InfoPlaceholders.worldClock(500)).isEqualTo("06:30");
        assertThat(InfoPlaceholders.worldClock(6500)).isEqualTo("12:30");
    }

    @Test
    void anUnknownStatisticNameResolvesToEmptyWithoutTouchingAnOnlinePlayer() {
        MenuBindings bindings = new MenuBindings();
        InfoPlaceholders.register(bindings);
        MenuContext ctx = MenuContext.of(new PlayerRef(UUID.randomUUID(), "Ghost"), null, 0);

        // A bad enum name is rejected by the parse step before any Bukkit.getPlayer lookup, so no server is needed.
        assertThat(bindings.placeholders().resolve("stat_NOT_A_REAL_STATISTIC", ctx))
                .contains("");
    }

    @Test
    void theStatFamilyIsClaimedForValidation() {
        MenuBindings bindings = new MenuBindings();
        InfoPlaceholders.register(bindings);

        // A %stat_<NAME>% token must count as known so MenuBindings.validate accepts a spec that uses one; the live
        // read of a valid statistic needs a real player and is exercised in the golden.
        assertThat(bindings.placeholders().has("stat_MOB_KILLS")).isTrue();
    }
}

package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.CurrencyId;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import com.uxplima.uxmessentials.vote.domain.VotePeriod;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * The placeholder resolution logic, exercised behind the {@link PlaceholderResolver} seam against fakes of
 * the context read seams, no live PlaceholderAPI and no Bukkit. It proves each placeholder maps to the
 * right read, that an unknown key resolves to {@code empty} (the raw-token signal), that a disabled context
 * degrades its placeholders to the empty/"-" default, and that the offline guard suppresses the
 * session-only presence placeholders.
 */
class PlaceholderResolverTest {

    private static final PlayerRef ALICE = new PlayerRef(UUID.randomUUID(), "Alice");
    private static final PlayerRef BOB = new PlayerRef(UUID.randomUUID(), "Bob");
    private static final Currency COINS = Currency.builder(CurrencyId.of("coins"))
            .symbol("$")
            .plural("coins")
            .format("#,##0.00")
            .build();
    private static final Currency GEMS = Currency.builder(CurrencyId.of("gems"))
            .symbol("g")
            .plural("gems")
            .format("#,##0.00")
            .build();

    @Test
    void unknownKeyResolvesEmptySoTheRawTokenStays() {
        PlaceholderResolver resolver =
                new PlaceholderResolver(PlaceholderContexts.builder().build());

        assertThat(resolver.resolve(ALICE, true, "not_a_placeholder")).isEmpty();
    }

    @Test
    void homesPlaceholdersReadCountLimitAndRemaining() {
        PlaceholderResolver resolver = resolverWith(
                PlaceholderContexts.builder().homes(fakeHomes(2, 5)).build());

        assertThat(resolver.resolve(ALICE, true, "homes_count")).contains("2");
        assertThat(resolver.resolve(ALICE, true, "homes_limit")).contains("5");
        assertThat(resolver.resolve(ALICE, true, "homes_left")).contains("3");
    }

    @Test
    void unlimitedHomeLimitRendersTheInfinityMarker() {
        PlaceholderResolver resolver = resolverWith(
                PlaceholderContexts.builder().homes(fakeHomes(7, -1)).build());

        assertThat(resolver.resolve(ALICE, true, "homes_limit")).contains("∞");
        assertThat(resolver.resolve(ALICE, true, "homes_left")).contains("∞");
    }

    @Test
    void homesListJoinsNamesAndDashesWhenEmpty() {
        List<HomesPlaceholders.HomeView> homes = List.of(
                new HomesPlaceholders.HomeView("base", "world", 10, 64, -20),
                new HomesPlaceholders.HomeView("mine", "world_nether", 1, 30, 2));
        PlaceholderResolver withHomes = resolverWith(
                PlaceholderContexts.builder().homes(fakeHomes(2, 5, homes)).build());
        PlaceholderResolver noHomes = resolverWith(
                PlaceholderContexts.builder().homes(fakeHomes(0, 5, List.of())).build());

        assertThat(withHomes.resolve(ALICE, true, "homes_list")).contains("base, mine");
        assertThat(noHomes.resolve(ALICE, true, "homes_list")).contains("-");
    }

    @Test
    void indexedHomeReadsNameWorldAndCoordinates() {
        List<HomesPlaceholders.HomeView> homes = List.of(
                new HomesPlaceholders.HomeView("base", "world", 10, 64, -20),
                new HomesPlaceholders.HomeView("mine", "world_nether", 1, 30, 2));
        PlaceholderResolver resolver = resolverWith(
                PlaceholderContexts.builder().homes(fakeHomes(2, 5, homes)).build());

        assertThat(resolver.resolve(ALICE, true, "homes_1")).contains("base");
        assertThat(resolver.resolve(ALICE, true, "homes_1_world")).contains("world");
        assertThat(resolver.resolve(ALICE, true, "homes_1_x")).contains("10");
        assertThat(resolver.resolve(ALICE, true, "homes_1_y")).contains("64");
        assertThat(resolver.resolve(ALICE, true, "homes_1_z")).contains("-20");
        assertThat(resolver.resolve(ALICE, true, "homes_2_world")).contains("world_nether");
    }

    @Test
    void indexedHomeDashesOutOfRangeAndUnparseable() {
        List<HomesPlaceholders.HomeView> homes = List.of(new HomesPlaceholders.HomeView("base", "world", 0, 0, 0));
        PlaceholderResolver resolver = resolverWith(
                PlaceholderContexts.builder().homes(fakeHomes(1, 5, homes)).build());

        assertThat(resolver.resolve(ALICE, true, "homes_3")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "homes_0")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "homes_abc")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "homes_1_unknown")).contains("-");
    }

    @Test
    void homeExistsReportsYesOrNoByLabel() {
        List<HomesPlaceholders.HomeView> homes = List.of(new HomesPlaceholders.HomeView("Base", "world", 0, 0, 0));
        PlaceholderResolver resolver = resolverWith(
                PlaceholderContexts.builder().homes(fakeHomes(1, 5, homes)).build());

        assertThat(resolver.resolve(ALICE, true, "homes_exists_base")).contains("yes");
        assertThat(resolver.resolve(ALICE, true, "homes_exists_mine")).contains("no");
    }

    @Test
    void economyPlaceholdersReadBalanceFormattedAndPosition() {
        FakeEconomy economy =
                new FakeEconomy().balance(ALICE, "1234.5").formatted("$1.23K").position(4);
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().economy(economy).build());

        assertThat(resolver.resolve(ALICE, true, "balance")).contains("1234.50");
        assertThat(resolver.resolve(ALICE, true, "balance_formatted")).contains("$1.23K");
        assertThat(resolver.resolve(ALICE, true, "baltop_position")).contains("4");
        // The economy_-prefixed aliases resolve the same scalars.
        assertThat(resolver.resolve(ALICE, true, "economy_balance")).contains("1234.50");
        assertThat(resolver.resolve(ALICE, true, "economy_balance_formatted")).contains("$1.23K");
        assertThat(resolver.resolve(ALICE, true, "economy_baltop_position")).contains("4");
    }

    @Test
    void unrankedBaltopPositionDegradesToDash() {
        FakeEconomy economy = new FakeEconomy().position(-1);
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().economy(economy).build());

        assertThat(resolver.resolve(ALICE, true, "baltop_position")).contains("-");
    }

    @Test
    void economyCompactAndShortRenderTheAbbreviatedBalance() {
        FakeEconomy economy = new FakeEconomy().balance(ALICE, "1234500").compactValue("$1.23M");
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().economy(economy).build());

        assertThat(resolver.resolve(ALICE, true, "economy_balance_compact")).contains("$1.23M");
        assertThat(resolver.resolve(ALICE, true, "economy_balance_short")).contains("$1.23M");
    }

    @Test
    void economyCurrencyNameAndSymbolReadTheDefaultCurrency() {
        FakeEconomy economy = new FakeEconomy().currency(COINS);
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().economy(economy).build());

        assertThat(resolver.resolve(ALICE, true, "economy_currency_name")).contains("coins");
        assertThat(resolver.resolve(ALICE, true, "economy_currency_symbol")).contains("$");
    }

    @Test
    void perCurrencyBalanceResolvesTheNamedCurrencyAndDashesTheUnknown() {
        FakeEconomy economy = new FakeEconomy().currency(GEMS).balance(ALICE, GEMS, "50");
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().economy(economy).build());

        assertThat(resolver.resolve(ALICE, true, "economy_balance_gems")).contains("50.00");
        assertThat(resolver.resolve(ALICE, true, "economy_balance_formatted_gems"))
                .contains("g50.00");
        assertThat(resolver.resolve(ALICE, true, "economy_balance_doubloons")).contains("-");
    }

    @Test
    void indexedBaltopReadsRowFieldsAndDashesOutOfRange() {
        FakeEconomy economy = new FakeEconomy()
                .currency(COINS)
                .baltopRow(COINS, 1, new BaltopRow(BOB, Money.of(COINS, new BigDecimal("9000"))));
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().economy(economy).build());

        assertThat(resolver.resolve(ALICE, true, "economy_baltop_1_name")).contains("Bob");
        assertThat(resolver.resolve(ALICE, true, "economy_baltop_1_amount")).contains("9000.00");
        assertThat(resolver.resolve(ALICE, true, "economy_baltop_1_formatted")).contains("$9,000.00");
        assertThat(resolver.resolve(ALICE, true, "economy_baltop_1_uuid"))
                .contains(BOB.uuid().toString());
        assertThat(resolver.resolve(ALICE, true, "economy_baltop_2_name")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "economy_baltop_x_name")).contains("-");
    }

    @Test
    void perCurrencyIndexedBaltopReadsTheNamedCurrencyLeaderboard() {
        FakeEconomy economy = new FakeEconomy()
                .currency(GEMS)
                .baltopRow(GEMS, 1, new BaltopRow(BOB, Money.of(GEMS, new BigDecimal("12"))));
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().economy(economy).build());

        assertThat(resolver.resolve(ALICE, true, "economy_baltop_gems_1_name")).contains("Bob");
        assertThat(resolver.resolve(ALICE, true, "economy_baltop_gems_1_amount"))
                .contains("12.00");
        assertThat(resolver.resolve(ALICE, true, "economy_baltop_doubloons_1_name"))
                .contains("-");
    }

    @Test
    void presencePlaceholdersReadAfkDurationAndVanish() {
        PresencePlaceholders presence = presenceSeam(
                new PresencePlaceholders.Snapshot(true, Duration.ofSeconds(90), Optional.of("lunch"), true, "Ace"));
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().presence(presence).build());

        assertThat(resolver.resolve(ALICE, true, "afk")).contains("yes");
        assertThat(resolver.resolve(ALICE, true, "afk_duration")).contains("1m30s");
        assertThat(resolver.resolve(ALICE, true, "vanished")).contains("yes");
    }

    @Test
    void presencePrefixReadsNicknameRealnameReasonAndStatus() {
        PresencePlaceholders presence = presenceSeam(
                new PresencePlaceholders.Snapshot(true, Duration.ofSeconds(90), Optional.of("lunch"), true, "Ace"));
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().presence(presence).build());

        assertThat(resolver.resolve(ALICE, true, "presence_nickname")).contains("Ace");
        assertThat(resolver.resolve(ALICE, true, "presence_realname")).contains("Alice");
        assertThat(resolver.resolve(ALICE, true, "presence_afk")).contains("yes");
        assertThat(resolver.resolve(ALICE, true, "presence_afk_since")).contains("1m30s");
        assertThat(resolver.resolve(ALICE, true, "presence_afk_reason")).contains("lunch");
        assertThat(resolver.resolve(ALICE, true, "presence_vanished")).contains("yes");
    }

    @Test
    void presenceAfkReasonIsDashWithoutAReason() {
        PresencePlaceholders presence = presenceSeam(
                new PresencePlaceholders.Snapshot(true, Duration.ofSeconds(5), Optional.empty(), false, "Alice"));
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().presence(presence).build());

        assertThat(resolver.resolve(ALICE, true, "presence_afk_reason")).contains("-");
    }

    @Test
    void presencePlaceholdersDegradeWhenOffline() {
        PresencePlaceholders presence = presenceSeam(
                new PresencePlaceholders.Snapshot(true, Duration.ofSeconds(90), Optional.of("afk"), true, "Ace"));
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().presence(presence).build());

        assertThat(resolver.resolve(ALICE, false, "afk")).contains("-");
        assertThat(resolver.resolve(ALICE, false, "afk_duration")).contains("-");
        assertThat(resolver.resolve(ALICE, false, "presence_nickname")).contains("-");
        assertThat(resolver.resolve(ALICE, false, "presence_realname")).contains("-");
    }

    @Test
    void afkDurationIsDashWhenNotAfk() {
        PresencePlaceholders presence =
                presenceSeam(new PresencePlaceholders.Snapshot(false, Duration.ZERO, Optional.empty(), false, "Alice"));
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().presence(presence).build());

        assertThat(resolver.resolve(ALICE, true, "afk")).contains("no");
        assertThat(resolver.resolve(ALICE, true, "afk_duration")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "presence_afk_reason")).contains("-");
    }

    @Test
    void kitCooldownReadsRemainingAndIsDashForUnknownKit() {
        FakeKits kits = new FakeKits().cooldown("daily", Duration.ofSeconds(3_661));
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().kits(kits).build());

        assertThat(resolver.resolve(ALICE, true, "kit_cooldown_daily")).contains("1h1m1s");
        assertThat(resolver.resolve(ALICE, true, "kit_cooldown_daily_formatted"))
                .contains("1h1m1s");
        assertThat(resolver.resolve(ALICE, true, "kit_cooldown_ghost")).contains("-");
    }

    @Test
    void readyKitRendersZeroSeconds() {
        FakeKits kits = new FakeKits().cooldown("daily", Duration.ZERO);
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().kits(kits).build());

        assertThat(resolver.resolve(ALICE, true, "kit_cooldown_daily")).contains("0s");
    }

    @Test
    void kitAvailableAndHasReadThroughTheSeam() {
        FakeKits kits = new FakeKits()
                .cooldown("daily", Duration.ZERO)
                .available("daily", true)
                .hasPermission("daily", true)
                .available("vip", false)
                .hasPermission("vip", false);
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().kits(kits).build());

        assertThat(resolver.resolve(ALICE, true, "kit_available_daily")).contains("yes");
        assertThat(resolver.resolve(ALICE, true, "kit_has_daily")).contains("yes");
        assertThat(resolver.resolve(ALICE, true, "kit_available_vip")).contains("no");
        assertThat(resolver.resolve(ALICE, true, "kit_has_vip")).contains("no");
        // An unknown kit degrades to the dash, never a misleading "no".
        assertThat(resolver.resolve(ALICE, true, "kit_available_ghost")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "kit_has_ghost")).contains("-");
    }

    @Test
    void kitCostRendersFreeOrTheAmountAndDashesUnknown() {
        FakeKits kits = new FakeKits()
                .cooldown("daily", Duration.ZERO)
                .cost("daily", new BigDecimal("0"))
                .cooldown("crate", Duration.ZERO)
                .cost("crate", new BigDecimal("250"));
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().kits(kits).build());

        assertThat(resolver.resolve(ALICE, true, "kit_cost_daily")).contains("free");
        assertThat(resolver.resolve(ALICE, true, "kit_cost_crate")).contains("250");
        assertThat(resolver.resolve(ALICE, true, "kit_cost_ghost")).contains("-");
    }

    @Test
    void kitClaimsLeftRendersUnlimitedAndTheRemainingCount() {
        FakeKits kits = new FakeKits()
                .cooldown("daily", Duration.ZERO)
                .claimsLeft("daily", -1)
                .cooldown("starter", Duration.ZERO)
                .claimsLeft("starter", 1)
                .cooldown("welcome", Duration.ZERO)
                .claimsLeft("welcome", 0);
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().kits(kits).build());

        assertThat(resolver.resolve(ALICE, true, "kit_claims_left_daily")).contains("∞");
        assertThat(resolver.resolve(ALICE, true, "kit_claims_left_starter")).contains("1");
        assertThat(resolver.resolve(ALICE, true, "kit_claims_left_welcome")).contains("0");
        assertThat(resolver.resolve(ALICE, true, "kit_claims_left_ghost")).contains("-");
    }

    @Test
    void kitsListJoinsUsableIdsAndDashesWhenNone() {
        FakeKits withKits = new FakeKits().usable("daily", "starter");
        FakeKits noKits = new FakeKits();
        PlaceholderResolver withResolver =
                resolverWith(PlaceholderContexts.builder().kits(withKits).build());
        PlaceholderResolver noResolver =
                resolverWith(PlaceholderContexts.builder().kits(noKits).build());

        assertThat(withResolver.resolve(ALICE, true, "kits_list")).contains("daily, starter");
        assertThat(noResolver.resolve(ALICE, true, "kits_list")).contains("-");
    }

    @Test
    void kitFamilyDegradesWhenModuleIsDisabled() {
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().build());

        assertThat(resolver.resolve(ALICE, true, "kit_cooldown_daily")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "kit_available_daily")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "kit_cost_daily")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "kits_list")).contains("-");
        // An unknown kit_ tail still resolves through the branch to the dash, never the raw token.
        assertThat(resolver.resolve(ALICE, true, "kit_unknown")).contains("-");
    }

    @Test
    void warpsCountAndListReadThroughTheSeam() {
        FakeWarps warps = new FakeWarps()
                .visible("spawn", warpView("world", 0, 64, 0, 12, "Admin", "0"))
                .visible("shop", warpView("world", 100, 70, -50, 4, "Admin", "250"));
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().warps(warps).build());

        assertThat(resolver.resolve(ALICE, true, "warps_count")).contains("2");
        assertThat(resolver.resolve(ALICE, true, "warps_list")).contains("spawn, shop");
    }

    @Test
    void warpsListAndCountDashWhenNoneVisible() {
        PlaceholderResolver resolver = resolverWith(
                PlaceholderContexts.builder().warps(new FakeWarps()).build());

        assertThat(resolver.resolve(ALICE, true, "warps_count")).contains("0");
        assertThat(resolver.resolve(ALICE, true, "warps_list")).contains("-");
    }

    @Test
    void perWarpFieldsReadWorldCoordinatesVisitsOwnerAndCost() {
        FakeWarps warps = new FakeWarps().visible("shop", warpView("world_nether", 100, 70, -50, 4, "Admin", "250"));
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().warps(warps).build());

        assertThat(resolver.resolve(ALICE, true, "warp_shop_world")).contains("world_nether");
        assertThat(resolver.resolve(ALICE, true, "warp_shop_x")).contains("100");
        assertThat(resolver.resolve(ALICE, true, "warp_shop_y")).contains("70");
        assertThat(resolver.resolve(ALICE, true, "warp_shop_z")).contains("-50");
        assertThat(resolver.resolve(ALICE, true, "warp_shop_visits")).contains("4");
        assertThat(resolver.resolve(ALICE, true, "warp_shop_owner")).contains("Admin");
        assertThat(resolver.resolve(ALICE, true, "warp_shop_cost")).contains("250");
    }

    @Test
    void perWarpFieldHandlesNamesWithUnderscores() {
        FakeWarps warps = new FakeWarps().visible("pvp_arena", warpView("world", 1, 2, 3, 0, "Admin", "0"));
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().warps(warps).build());

        assertThat(resolver.resolve(ALICE, true, "warp_pvp_arena_world")).contains("world");
        assertThat(resolver.resolve(ALICE, true, "warp_pvp_arena_x")).contains("1");
    }

    @Test
    void perWarpFieldDashesUnknownHiddenAndMalformed() {
        FakeWarps warps = new FakeWarps().visible("spawn", warpView("world", 0, 64, 0, 0, "Admin", "0"));
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().warps(warps).build());

        // A warp the player cannot see (not in the visible set) degrades to the dash, never leaking its data.
        assertThat(resolver.resolve(ALICE, true, "warp_secret_world")).contains("-");
        // An unknown field on a visible warp, and a tail with no field segment, both degrade to the dash.
        assertThat(resolver.resolve(ALICE, true, "warp_spawn_unknown")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "warp_spawn")).contains("-");
    }

    @Test
    void warpsDegradeWhenModuleIsDisabled() {
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().build());

        assertThat(resolver.resolve(ALICE, true, "warps_count")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "warps_list")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "warp_spawn_world")).contains("-");
        // An unknown warps_ tail still resolves through the branch to the dash, never the raw token.
        assertThat(resolver.resolve(ALICE, true, "warps_unknown")).contains("-");
    }

    @Test
    void vaultsCountMaxLeftAndSizeReadThroughTheSeam() {
        PlaceholderResolver resolver = resolverWith(
                PlaceholderContexts.builder().vaults(fakeVaults(2, 5, 6)).build());

        assertThat(resolver.resolve(ALICE, true, "vaults_count")).contains("2");
        assertThat(resolver.resolve(ALICE, true, "vaults_max")).contains("5");
        assertThat(resolver.resolve(ALICE, true, "vaults_left")).contains("3");
        assertThat(resolver.resolve(ALICE, true, "vaults_size")).contains("6");
    }

    @Test
    void unlimitedVaultMaxRendersTheInfinityMarker() {
        PlaceholderResolver resolver = resolverWith(
                PlaceholderContexts.builder().vaults(fakeVaults(7, -1, 3)).build());

        assertThat(resolver.resolve(ALICE, true, "vaults_max")).contains("∞");
        assertThat(resolver.resolve(ALICE, true, "vaults_left")).contains("∞");
        assertThat(resolver.resolve(ALICE, true, "vaults_size")).contains("3");
    }

    @Test
    void unknownVaultsTailDegradesToDash() {
        PlaceholderResolver resolver = resolverWith(
                PlaceholderContexts.builder().vaults(fakeVaults(1, 5, 6)).build());

        assertThat(resolver.resolve(ALICE, true, "vaults_unknown")).contains("-");
    }

    @Test
    void worldsPlaceholdersReadServerWideCountsAndDefaultWorld() {
        WorldsPlaceholders worlds = fakeWorlds(5, 3, Optional.of("world"), 8);
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().worlds(worlds).build());

        // Every worlds_ key is server-wide, so it reads identically for any requester, online or offline.
        assertThat(resolver.resolve(ALICE, true, "worlds_managed_count")).contains("5");
        assertThat(resolver.resolve(ALICE, true, "worlds_loaded_count")).contains("3");
        assertThat(resolver.resolve(ALICE, true, "worlds_default")).contains("world");
        assertThat(resolver.resolve(ALICE, true, "worlds_default_players")).contains("8");
        assertThat(resolver.resolve(BOB, false, "worlds_managed_count")).contains("5");
    }

    @Test
    void worldsDefaultDashesWhenNoDefaultWorld() {
        WorldsPlaceholders worlds = fakeWorlds(0, 0, Optional.empty(), 0);
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().worlds(worlds).build());

        assertThat(resolver.resolve(ALICE, true, "worlds_default")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "worlds_default_players")).contains("0");
    }

    @Test
    void unknownWorldsTailDegradesToDash() {
        WorldsPlaceholders worlds = fakeWorlds(1, 1, Optional.of("world"), 0);
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().worlds(worlds).build());

        assertThat(resolver.resolve(ALICE, true, "worlds_zzz")).contains("-");
    }

    @Test
    void worldsDegradeWhenModuleIsDisabled() {
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().build());

        assertThat(resolver.resolve(ALICE, true, "worlds_managed_count")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "worlds_default")).contains("-");
        // An unknown worlds_ tail still resolves through the branch to the dash, never the raw token.
        assertThat(resolver.resolve(ALICE, true, "worlds_unknown")).contains("-");
    }

    @Test
    void playerwarpsCountLimitLeftAndListReadThroughTheSeam() {
        FakePlayerwarps warps = new FakePlayerwarps(2, 5)
                .owned("base", playerWarpView("base", "Alice", "world", 10, 64, -20, 7))
                .owned("mine", playerWarpView("mine", "Alice", "world_nether", 1, 30, 2, 0));
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().playerwarps(warps).build());

        assertThat(resolver.resolve(ALICE, true, "playerwarps_count")).contains("2");
        assertThat(resolver.resolve(ALICE, true, "playerwarps_limit")).contains("5");
        assertThat(resolver.resolve(ALICE, true, "playerwarps_left")).contains("3");
        assertThat(resolver.resolve(ALICE, true, "playerwarps_list")).contains("base, mine");
    }

    @Test
    void unlimitedPlayerwarpLimitRendersTheInfinityMarker() {
        PlaceholderResolver resolver = resolverWith(PlaceholderContexts.builder()
                .playerwarps(new FakePlayerwarps(7, -1))
                .build());

        assertThat(resolver.resolve(ALICE, true, "playerwarps_limit")).contains("∞");
        assertThat(resolver.resolve(ALICE, true, "playerwarps_left")).contains("∞");
    }

    @Test
    void playerwarpsListDashesWhenNoneOwned() {
        PlaceholderResolver resolver = resolverWith(PlaceholderContexts.builder()
                .playerwarps(new FakePlayerwarps(0, 5))
                .build());

        assertThat(resolver.resolve(ALICE, true, "playerwarps_count")).contains("0");
        assertThat(resolver.resolve(ALICE, true, "playerwarps_list")).contains("-");
    }

    @Test
    void perPlayerwarpFieldsReadOwnerWorldCoordinatesAndVisits() {
        FakePlayerwarps warps = new FakePlayerwarps(1, 5)
                .owned("shop", playerWarpView("shop", "Alice", "world_nether", 100, 70, -50, 4));
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().playerwarps(warps).build());

        assertThat(resolver.resolve(ALICE, true, "playerwarp_shop_owner")).contains("Alice");
        assertThat(resolver.resolve(ALICE, true, "playerwarp_shop_world")).contains("world_nether");
        assertThat(resolver.resolve(ALICE, true, "playerwarp_shop_x")).contains("100");
        assertThat(resolver.resolve(ALICE, true, "playerwarp_shop_y")).contains("70");
        assertThat(resolver.resolve(ALICE, true, "playerwarp_shop_z")).contains("-50");
        assertThat(resolver.resolve(ALICE, true, "playerwarp_shop_visits")).contains("4");
    }

    @Test
    void perPlayerwarpFieldHandlesNamesWithUnderscores() {
        FakePlayerwarps warps =
                new FakePlayerwarps(1, 5).owned("pvp_arena", playerWarpView("pvp_arena", "Alice", "world", 1, 2, 3, 0));
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().playerwarps(warps).build());

        assertThat(resolver.resolve(ALICE, true, "playerwarp_pvp_arena_world")).contains("world");
        assertThat(resolver.resolve(ALICE, true, "playerwarp_pvp_arena_x")).contains("1");
    }

    @Test
    void perPlayerwarpFieldDashesUnknownAndMalformed() {
        FakePlayerwarps warps =
                new FakePlayerwarps(1, 5).owned("base", playerWarpView("base", "Alice", "world", 0, 64, 0, 0));
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().playerwarps(warps).build());

        // A warp the player does not own degrades to the dash, never leaking another owner's data.
        assertThat(resolver.resolve(ALICE, true, "playerwarp_secret_world")).contains("-");
        // An unknown field on an owned warp, and a tail with no field segment, both degrade to the dash.
        assertThat(resolver.resolve(ALICE, true, "playerwarp_base_unknown")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "playerwarp_base")).contains("-");
    }

    @Test
    void playerwarpsDegradeWhenModuleIsDisabled() {
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().build());

        assertThat(resolver.resolve(ALICE, true, "playerwarps_count")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "playerwarps_list")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "playerwarp_base_world")).contains("-");
        // An unknown playerwarps_ tail still resolves through the branch to the dash, never the raw token.
        assertThat(resolver.resolve(ALICE, true, "playerwarps_unknown")).contains("-");
    }

    @Test
    void moderationPlaceholdersReadMutedAndJailed() {
        FakeModeration moderation = new FakeModeration().muted(true).jailed(false);
        PlaceholderResolver resolver = resolverWith(
                PlaceholderContexts.builder().moderation(moderation).build());

        assertThat(resolver.resolve(ALICE, true, "muted")).contains("yes");
        assertThat(resolver.resolve(ALICE, true, "jailed")).contains("no");
    }

    @Test
    void moderationBanPlaceholdersReadReasonRemainingAndIssuer() {
        FakeModeration moderation = new FakeModeration()
                .ban(new ModerationPlaceholders.SanctionView(Optional.of(Duration.ofSeconds(90)), "griefing", "Mod"));
        PlaceholderResolver resolver = resolverWith(
                PlaceholderContexts.builder().moderation(moderation).build());

        assertThat(resolver.resolve(ALICE, true, "moderation_banned")).contains("yes");
        assertThat(resolver.resolve(ALICE, true, "moderation_ban_reason")).contains("griefing");
        assertThat(resolver.resolve(ALICE, true, "moderation_ban_issuer")).contains("Mod");
        assertThat(resolver.resolve(ALICE, true, "moderation_ban_remaining")).contains("90");
        assertThat(resolver.resolve(ALICE, true, "moderation_ban_remaining_formatted"))
                .contains("1m30s");
    }

    @Test
    void moderationPermanentBanRendersPermanentRemaining() {
        FakeModeration moderation = new FakeModeration()
                .ban(new ModerationPlaceholders.SanctionView(Optional.empty(), "cheating", "Console"));
        PlaceholderResolver resolver = resolverWith(
                PlaceholderContexts.builder().moderation(moderation).build());

        assertThat(resolver.resolve(ALICE, true, "moderation_banned")).contains("yes");
        assertThat(resolver.resolve(ALICE, true, "moderation_ban_remaining")).contains("permanent");
        assertThat(resolver.resolve(ALICE, true, "moderation_ban_remaining_formatted"))
                .contains("permanent");
    }

    @Test
    void moderationMutePlaceholdersReadReasonRemainingAndIssuer() {
        FakeModeration moderation = new FakeModeration()
                .mute(new ModerationPlaceholders.SanctionView(Optional.of(Duration.ofMinutes(2)), "spam", "Helper"));
        PlaceholderResolver resolver = resolverWith(
                PlaceholderContexts.builder().moderation(moderation).build());

        assertThat(resolver.resolve(ALICE, true, "muted")).contains("yes");
        assertThat(resolver.resolve(ALICE, true, "moderation_mute_reason")).contains("spam");
        assertThat(resolver.resolve(ALICE, true, "moderation_mute_issuer")).contains("Helper");
        assertThat(resolver.resolve(ALICE, true, "moderation_mute_remaining")).contains("120");
        assertThat(resolver.resolve(ALICE, true, "moderation_mute_remaining_formatted"))
                .contains("2m");
    }

    @Test
    void moderationJailPlaceholdersReadTheCellReasonIssuerAndWait() {
        FakeModeration moderation = new FakeModeration()
                .jail(new ModerationPlaceholders.JailView(
                        "cells", Optional.of(Duration.ofMinutes(30)), true, "chat abuse", "Admin"));
        PlaceholderResolver resolver = resolverWith(
                PlaceholderContexts.builder().moderation(moderation).build());

        assertThat(resolver.resolve(ALICE, true, "moderation_jailed")).contains("yes");
        assertThat(resolver.resolve(ALICE, true, "moderation_jail_name")).contains("cells");
        assertThat(resolver.resolve(ALICE, true, "moderation_jail_reason")).contains("chat abuse");
        assertThat(resolver.resolve(ALICE, true, "moderation_jail_issuer")).contains("Admin");
        assertThat(resolver.resolve(ALICE, true, "moderation_jail_remaining")).contains("1800");
        assertThat(resolver.resolve(ALICE, true, "moderation_jail_remaining_formatted"))
                .contains("30m");
        assertThat(resolver.resolve(ALICE, true, "moderation_jail_online_only")).contains("yes");
    }

    @Test
    void aPermanentJailRendersPermanentAndAWallClockOneIsNotOnlineOnly() {
        FakeModeration moderation = new FakeModeration()
                .jail(new ModerationPlaceholders.JailView("cells", Optional.empty(), false, "-", "Console"));
        PlaceholderResolver resolver = resolverWith(
                PlaceholderContexts.builder().moderation(moderation).build());

        assertThat(resolver.resolve(ALICE, true, "moderation_jail_remaining")).contains("permanent");
        assertThat(resolver.resolve(ALICE, true, "moderation_jail_online_only")).contains("no");
    }

    @Test
    void theJailDetailKeysDashWhenNoJailHoldsThePlayer() {
        PlaceholderResolver resolver = resolverWith(
                PlaceholderContexts.builder().moderation(new FakeModeration()).build());

        assertThat(resolver.resolve(ALICE, true, "moderation_jail_name")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "moderation_jail_remaining")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "moderation_jail_online_only")).contains("no");
        assertThat(resolverWith(PlaceholderContexts.builder().build()).resolve(ALICE, true, "moderation_jail_name"))
                .contains("-");
    }

    @Test
    void moderationFrozenAndWarnsReadThroughTheSeam() {
        FakeModeration moderation = new FakeModeration().frozen(true).warns(3);
        PlaceholderResolver resolver = resolverWith(
                PlaceholderContexts.builder().moderation(moderation).build());

        assertThat(resolver.resolve(ALICE, true, "moderation_frozen")).contains("yes");
        assertThat(resolver.resolve(ALICE, true, "moderation_warns")).contains("3");
    }

    @Test
    void moderationDetailKeysDashWhenNotSanctioned() {
        FakeModeration moderation = new FakeModeration();
        PlaceholderResolver resolver = resolverWith(
                PlaceholderContexts.builder().moderation(moderation).build());

        assertThat(resolver.resolve(ALICE, true, "moderation_banned")).contains("no");
        assertThat(resolver.resolve(ALICE, true, "moderation_ban_reason")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "moderation_ban_remaining")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "moderation_mute_reason")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "moderation_mute_remaining")).contains("-");
    }

    @Test
    void moderationFamilyDegradesWhenModuleIsDisabled() {
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().build());

        // A disabled moderation module means "no one is sanctioned" for the booleans, the dash for details.
        assertThat(resolver.resolve(ALICE, true, "moderation_banned")).contains("no");
        assertThat(resolver.resolve(ALICE, true, "moderation_frozen")).contains("no");
        assertThat(resolver.resolve(ALICE, true, "moderation_warns")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "moderation_ban_reason")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "moderation_mute_remaining")).contains("-");
    }

    @Test
    void teleportCooldownAndWarmupRenderRawAndFormattedRemaining() {
        FakeTeleport teleport =
                new FakeTeleport().cooldown(Duration.ofSeconds(90)).warmup(Duration.ofSeconds(3));
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().teleport(teleport).build());

        assertThat(resolver.resolve(ALICE, true, "teleport_cooldown_remaining")).contains("90");
        assertThat(resolver.resolve(ALICE, true, "teleport_cooldown_remaining_formatted"))
                .contains("1m30s");
        assertThat(resolver.resolve(ALICE, true, "teleport_warmup_remaining")).contains("3");
        assertThat(resolver.resolve(ALICE, true, "teleport_warmup_remaining_formatted"))
                .contains("3s");
    }

    @Test
    void teleportCooldownAndWarmupRenderZeroWhenNothingInFlight() {
        FakeTeleport teleport = new FakeTeleport();
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().teleport(teleport).build());

        assertThat(resolver.resolve(ALICE, true, "teleport_cooldown_remaining")).contains("0");
        assertThat(resolver.resolve(ALICE, true, "teleport_cooldown_remaining_formatted"))
                .contains("0s");
        assertThat(resolver.resolve(ALICE, true, "teleport_warmup_remaining")).contains("0");
        assertThat(resolver.resolve(ALICE, true, "teleport_warmup_remaining_formatted"))
                .contains("0s");
    }

    @Test
    void teleportBackPlaceholdersReadCaptureAndDashWhenNone() {
        FakeTeleport withBack = new FakeTeleport().back(new TeleportPlaceholders.BackView("world_nether", 10, 64, -20));
        FakeTeleport noBack = new FakeTeleport();
        PlaceholderResolver withResolver =
                resolverWith(PlaceholderContexts.builder().teleport(withBack).build());
        PlaceholderResolver noResolver =
                resolverWith(PlaceholderContexts.builder().teleport(noBack).build());

        assertThat(withResolver.resolve(ALICE, true, "teleport_back_available")).contains("yes");
        assertThat(withResolver.resolve(ALICE, true, "teleport_back_world")).contains("world_nether");
        assertThat(withResolver.resolve(ALICE, true, "teleport_back_x")).contains("10");
        assertThat(withResolver.resolve(ALICE, true, "teleport_back_y")).contains("64");
        assertThat(withResolver.resolve(ALICE, true, "teleport_back_z")).contains("-20");
        assertThat(noResolver.resolve(ALICE, true, "teleport_back_available")).contains("no");
        assertThat(noResolver.resolve(ALICE, true, "teleport_back_world")).contains("-");
        assertThat(noResolver.resolve(ALICE, true, "teleport_back_x")).contains("-");
    }

    @Test
    void teleportRequestPlaceholdersReadIncomingOutgoingAndAccepting() {
        FakeTeleport teleport = new FakeTeleport().incoming(2).outgoing(true).accepting(false);
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().teleport(teleport).build());

        assertThat(resolver.resolve(ALICE, true, "teleport_tpa_incoming")).contains("2");
        assertThat(resolver.resolve(ALICE, true, "teleport_tpa_pending")).contains("yes");
        assertThat(resolver.resolve(ALICE, true, "teleport_accepting")).contains("no");
    }

    @Test
    void teleportSessionPlaceholdersDegradeOfflineAndWhenDisabled() {
        FakeTeleport teleport = new FakeTeleport()
                .warmup(Duration.ofSeconds(3))
                .incoming(1)
                .outgoing(true)
                .accepting(true);
        PlaceholderResolver withSeam =
                resolverWith(PlaceholderContexts.builder().teleport(teleport).build());
        PlaceholderResolver noSeam = resolverWith(PlaceholderContexts.builder().build());

        // Offline: the session-only warmup/request/accept keys cannot be queried, so they degrade to the dash.
        assertThat(withSeam.resolve(ALICE, false, "teleport_warmup_remaining")).contains("-");
        assertThat(withSeam.resolve(ALICE, false, "teleport_tpa_incoming")).contains("-");
        assertThat(withSeam.resolve(ALICE, false, "teleport_tpa_pending")).contains("-");
        assertThat(withSeam.resolve(ALICE, false, "teleport_accepting")).contains("-");
        // Disabled module: every teleport key degrades to the dash.
        assertThat(noSeam.resolve(ALICE, true, "teleport_cooldown_remaining")).contains("-");
        assertThat(noSeam.resolve(ALICE, true, "teleport_back_available")).contains("-");
        // An unknown teleport_ tail still resolves through the branch to the dash, never the raw token.
        assertThat(withSeam.resolve(ALICE, true, "teleport_unknown")).contains("-");
    }

    @Test
    void votePlaceholdersReadPeriodicTotals() {
        VotePlaceholders vote = new VotePlaceholders() {
            @Override
            public long countFor(PlayerRef who, VotePeriod period) {
                return switch (period) {
                    case ALLTIME -> 100L;
                    case DAILY -> 3L;
                    case WEEKLY -> 14L;
                    case MONTHLY -> 42L;
                };
            }

            @Override
            public int partyCount() {
                return 18;
            }

            @Override
            public int partyThreshold() {
                return 25;
            }
        };
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().vote(vote).build());

        assertThat(resolver.resolve(ALICE, true, "votes_alltime")).contains("100");
        assertThat(resolver.resolve(ALICE, true, "votes_daily")).contains("3");
        assertThat(resolver.resolve(ALICE, true, "votes_weekly")).contains("14");
        assertThat(resolver.resolve(ALICE, true, "votes_monthly")).contains("42");
        assertThat(resolver.resolve(ALICE, true, "voteparty_current")).contains("18");
        assertThat(resolver.resolve(ALICE, true, "voteparty_required")).contains("25");
        assertThat(resolver.resolve(ALICE, true, "voteparty_remaining")).contains("7");
    }

    @Test
    void votePlaceholdersDegradeWhenModuleIsDisabled() {
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().build());

        assertThat(resolver.resolve(ALICE, true, "votes_alltime")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "votes_monthly")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "voteparty_current")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "voteparty_remaining")).contains("-");
    }

    @Test
    void unknownVotesPeriodDegradesToDash() {
        VotePlaceholders vote = new VotePlaceholders() {
            @Override
            public long countFor(PlayerRef who, VotePeriod period) {
                return 5L;
            }

            @Override
            public int partyCount() {
                return 0;
            }

            @Override
            public int partyThreshold() {
                return 25;
            }
        };
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().vote(vote).build());

        assertThat(resolver.resolve(ALICE, true, "votes_unknown")).contains("-");
    }

    @Test
    void messagingPlaceholdersReadMailReplyToggleSpyAndIgnore() {
        FakeMessaging messaging = new FakeMessaging()
                .unread(3)
                .total(8)
                .replyTarget("Bob")
                .accepting(false)
                .spying(true)
                .ignoring(2);
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().messaging(messaging).build());

        assertThat(resolver.resolve(ALICE, true, "messaging_mail_unread")).contains("3");
        assertThat(resolver.resolve(ALICE, true, "messaging_mail_total")).contains("8");
        assertThat(resolver.resolve(ALICE, true, "messaging_reply_target")).contains("Bob");
        assertThat(resolver.resolve(ALICE, true, "messaging_msgtoggle")).contains("no");
        assertThat(resolver.resolve(ALICE, true, "messaging_socialspy")).contains("yes");
        assertThat(resolver.resolve(ALICE, true, "messaging_ignoring_count")).contains("2");
    }

    @Test
    void messagingReplyTargetDashesWhenNoConversation() {
        FakeMessaging messaging = new FakeMessaging().accepting(true);
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().messaging(messaging).build());

        assertThat(resolver.resolve(ALICE, true, "messaging_reply_target")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "messaging_msgtoggle")).contains("yes");
    }

    @Test
    void messagingSessionKeysDashOfflineButDurableKeysStillRead() {
        FakeMessaging messaging = new FakeMessaging()
                .unread(4)
                .total(4)
                .ignoring(1)
                .replyTarget("Bob")
                .accepting(true)
                .spying(true);
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().messaging(messaging).build());

        // The DB-backed mail and ignore reads answer for an offline player.
        assertThat(resolver.resolve(ALICE, false, "messaging_mail_unread")).contains("4");
        assertThat(resolver.resolve(ALICE, false, "messaging_mail_total")).contains("4");
        assertThat(resolver.resolve(ALICE, false, "messaging_ignoring_count")).contains("1");
        // The session-only reads degrade to the dash for an offline player.
        assertThat(resolver.resolve(ALICE, false, "messaging_reply_target")).contains("-");
        assertThat(resolver.resolve(ALICE, false, "messaging_msgtoggle")).contains("-");
        assertThat(resolver.resolve(ALICE, false, "messaging_socialspy")).contains("-");
    }

    @Test
    void messagingDegradesWhenModuleIsDisabled() {
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().build());

        assertThat(resolver.resolve(ALICE, true, "messaging_mail_unread")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "messaging_msgtoggle")).contains("-");
        // An unknown messaging_ tail still resolves through the branch to the dash, never the raw token.
        assertThat(resolver.resolve(ALICE, true, "messaging_unknown")).contains("-");
    }

    @Test
    void staffPlaceholdersReadModeAndOnlineCount() {
        FakeStaff staff = new FakeStaff().mode(ALICE, true).onlineCount(3);
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().staff(staff).build());

        assertThat(resolver.resolve(ALICE, true, "staff_mode")).contains("yes");
        assertThat(resolver.resolve(BOB, true, "staff_mode")).contains("no");
        assertThat(resolver.resolve(ALICE, true, "staff_online")).contains("3");
        assertThat(resolver.resolve(ALICE, true, "staff_count")).contains("3");
    }

    @Test
    void staffModeDegradesOfflineButOnlineCountStillReads() {
        FakeStaff staff = new FakeStaff().mode(ALICE, true).onlineCount(2);
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().staff(staff).build());

        // staff_mode is session-only: an offline requester holds no marker, so it reads "no".
        assertThat(resolver.resolve(ALICE, false, "staff_mode")).contains("no");
        // The server-wide roster count does not depend on the requester's session, so it still answers.
        assertThat(resolver.resolve(ALICE, false, "staff_online")).contains("2");
    }

    @Test
    void staffDegradesWhenModuleIsDisabled() {
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().build());

        assertThat(resolver.resolve(ALICE, true, "staff_mode")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "staff_online")).contains("-");
        // An unknown staff_ tail still resolves through the branch to the dash, never the raw token.
        assertThat(resolver.resolve(ALICE, true, "staff_unknown")).contains("-");
    }

    @Test
    void discordlinkPlaceholdersReadLinkedAndId() {
        FakeDiscordlink linked = new FakeDiscordlink().link(ALICE, "123456789012345678");
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().discordlink(linked).build());

        assertThat(resolver.resolve(ALICE, true, "discordlink_linked")).contains("yes");
        assertThat(resolver.resolve(ALICE, true, "discordlink_id")).contains("123456789012345678");
        // An unlinked account reports no and dashes the id, both readable offline since the binding is DB-backed.
        assertThat(resolver.resolve(BOB, false, "discordlink_linked")).contains("no");
        assertThat(resolver.resolve(BOB, false, "discordlink_id")).contains("-");
        // The linked account reads identically for an offline requester.
        assertThat(resolver.resolve(ALICE, false, "discordlink_linked")).contains("yes");
        assertThat(resolver.resolve(ALICE, false, "discordlink_id")).contains("123456789012345678");
    }

    @Test
    void discordlinkDegradesWhenModuleIsDisabled() {
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().build());

        assertThat(resolver.resolve(ALICE, true, "discordlink_linked")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "discordlink_id")).contains("-");
        // An unknown discordlink_ tail still resolves through the branch to the dash, never the raw token.
        assertThat(resolver.resolve(ALICE, true, "discordlink_unknown")).contains("-");
    }

    @Test
    void hologramsCountReadsTheServerWideTotal() {
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().holograms(() -> 4).build());

        // The count is server-wide, so it reads identically for any requester, online or offline.
        assertThat(resolver.resolve(ALICE, true, "holograms_count")).contains("4");
        assertThat(resolver.resolve(BOB, false, "holograms_count")).contains("4");
    }

    @Test
    void hologramsDegradesWhenModuleIsDisabled() {
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().build());

        assertThat(resolver.resolve(ALICE, true, "holograms_count")).contains("-");
        // An unknown holograms_ tail still resolves through the branch to the dash, never the raw token.
        assertThat(resolver.resolve(ALICE, true, "holograms_unknown")).contains("-");
    }

    @Test
    void scoreboardVisiblePlaceholderReadsTheShownState() {
        FakeScoreboard shown = new FakeScoreboard().visible(ALICE, true);
        FakeScoreboard hidden = new FakeScoreboard().visible(ALICE, false);
        PlaceholderResolver shownResolver =
                resolverWith(PlaceholderContexts.builder().scoreboard(shown).build());
        PlaceholderResolver hiddenResolver =
                resolverWith(PlaceholderContexts.builder().scoreboard(hidden).build());

        assertThat(shownResolver.resolve(ALICE, true, "scoreboard_visible")).contains("yes");
        assertThat(hiddenResolver.resolve(ALICE, true, "scoreboard_visible")).contains("no");
    }

    @Test
    void scoreboardVisibleDegradesOfflineSinceTheBoardIsSessionScoped() {
        FakeScoreboard shown = new FakeScoreboard().visible(ALICE, true);
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().scoreboard(shown).build());

        // The board belongs to a live player, so an offline requester has nothing to show.
        assertThat(resolver.resolve(ALICE, false, "scoreboard_visible")).contains("-");
    }

    @Test
    void theHudFamiliesNameTheFormatEachModuleIsDrawingThePlayerFrom() {
        FakeScoreboard board = new FakeScoreboard().visible(ALICE, true).board(ALICE, "vip");
        PlaceholderResolver resolver = resolverWith(PlaceholderContexts.builder()
                .scoreboard(board)
                .tablist(who -> who.equals(ALICE) ? Optional.of("staff") : Optional.empty())
                .nametags(who -> who.equals(ALICE) ? Optional.of("admins") : Optional.empty())
                .build());

        assertThat(resolver.resolve(ALICE, true, "scoreboard_board")).contains("vip");
        assertThat(resolver.resolve(ALICE, true, "tablist_format")).contains("staff");
        assertThat(resolver.resolve(ALICE, true, "tablist_shown")).contains("yes");
        assertThat(resolver.resolve(ALICE, true, "nametags_format")).contains("admins");
        assertThat(resolver.resolve(ALICE, true, "nametags_shown")).contains("yes");
        // Matching no format is not an error: the name dashes and "shown" says no, which is what a player with a
        // bare tab and no nametag actually has.
        assertThat(resolver.resolve(BOB, true, "tablist_format")).contains("-");
        assertThat(resolver.resolve(BOB, true, "tablist_shown")).contains("no");
        assertThat(resolver.resolve(BOB, true, "nametags_shown")).contains("no");
        // The HUD is a live surface, so an offline requester has none of it.
        assertThat(resolver.resolve(ALICE, false, "tablist_format")).contains("-");
        assertThat(resolver.resolve(ALICE, false, "nametags_format")).contains("-");
    }

    @Test
    void theVillagerFollowCountReadsPerPlayer() {
        PlaceholderResolver resolver = resolverWith(PlaceholderContexts.builder()
                .villagers(who -> who.equals(ALICE) ? 2 : 0)
                .build());

        assertThat(resolver.resolve(ALICE, true, "villagers_following")).contains("2");
        assertThat(resolver.resolve(ALICE, true, "villagers_has_follower")).contains("yes");
        assertThat(resolver.resolve(BOB, true, "villagers_following")).contains("0");
        assertThat(resolver.resolve(BOB, true, "villagers_has_follower")).contains("no");
    }

    @Test
    void theServerBrandReadsWhatTheClientIsTold() {
        PlaceholderResolver branded = resolverWith(PlaceholderContexts.builder()
                .serverTweaks(() -> Optional.of("uxmEssentials"))
                .build());
        PlaceholderResolver stock = resolverWith(
                PlaceholderContexts.builder().serverTweaks(Optional::empty).build());

        assertThat(branded.resolve(ALICE, true, "servertweaks_brand")).contains("uxmEssentials");
        // The tweak switched off means the server's own brand stands, and the key says so rather than inventing one.
        assertThat(stock.resolve(ALICE, true, "servertweaks_brand")).contains("-");
    }

    @Test
    void theCommandCheckFamilyAnswersForTheCommandNamedInTheKey() {
        PlaceholderResolver resolver = resolverWith(PlaceholderContexts.builder()
                .commandControl((who, command) -> !command.equals("gamemode"))
                .build());

        assertThat(resolver.resolve(ALICE, true, "commandcontrol_allowed_home")).contains("yes");
        assertThat(resolver.resolve(ALICE, true, "commandcontrol_allowed_gamemode"))
                .contains("no");
        // A family read with nothing after the prefix names no command, so there is nothing to answer about.
        assertThat(resolver.resolve(ALICE, true, "commandcontrol_allowed_")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "commandcontrol_something_else"))
                .contains("-");
    }

    @Test
    void theRollbackKeysReadTheLastCaptureThisEnableTook() {
        Instant captured = Instant.now().minus(Duration.ofMinutes(5));
        PlaceholderResolver resolver = resolverWith(PlaceholderContexts.builder()
                .invrollback(new InvrollbackPlaceholders() {
                    @Override
                    public Optional<Instant> lastCapture(PlayerRef who) {
                        return who.equals(ALICE) ? Optional.of(captured) : Optional.empty();
                    }

                    @Override
                    public Optional<String> lastCause(PlayerRef who) {
                        return who.equals(ALICE) ? Optional.of("death") : Optional.empty();
                    }
                })
                .build());

        assertThat(resolver.resolve(ALICE, true, "invrollback_last_capture")).contains("5m");
        assertThat(resolver.resolve(ALICE, true, "invrollback_last_cause")).contains("death");
        assertThat(resolver.resolve(ALICE, true, "invrollback_captured")).contains("yes");
        // Nothing captured since the restart reads the dash rather than a zero that would look like "just now".
        assertThat(resolver.resolve(BOB, true, "invrollback_last_capture")).contains("-");
        assertThat(resolver.resolve(BOB, true, "invrollback_captured")).contains("no");
    }

    @Test
    void scoreboardDegradesWhenModuleIsDisabled() {
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().build());

        assertThat(resolver.resolve(ALICE, true, "scoreboard_visible")).contains("-");
        // An unknown scoreboard_ tail still resolves through the branch to the dash, never the raw token.
        assertThat(resolver.resolve(ALICE, true, "scoreboard_unknown")).contains("-");
    }

    @Test
    void communicationPlaceholdersReadChatLockAndBroadcastSubscription() {
        FakeCommunication open = new FakeCommunication().chatEnabled(true).receivesBroadcasts(ALICE, true);
        FakeCommunication locked = new FakeCommunication().chatEnabled(false).receivesBroadcasts(ALICE, false);
        PlaceholderResolver openResolver =
                resolverWith(PlaceholderContexts.builder().communication(open).build());
        PlaceholderResolver lockedResolver =
                resolverWith(PlaceholderContexts.builder().communication(locked).build());

        assertThat(openResolver.resolve(ALICE, true, "communication_chat_enabled"))
                .contains("yes");
        assertThat(openResolver.resolve(ALICE, true, "communication_broadcasts"))
                .contains("yes");
        assertThat(lockedResolver.resolve(ALICE, true, "communication_chat_enabled"))
                .contains("no");
        assertThat(lockedResolver.resolve(ALICE, true, "communication_broadcasts"))
                .contains("no");
    }

    @Test
    void communicationBroadcastsDegradeOfflineButChatEnabledStillReads() {
        FakeCommunication open = new FakeCommunication().chatEnabled(true).receivesBroadcasts(ALICE, true);
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().communication(open).build());

        // The chat lock is server-wide, so it answers for an offline requester too.
        assertThat(resolver.resolve(ALICE, false, "communication_chat_enabled")).contains("yes");
        // The per-player subscription is session-meaningful, so it degrades to the dash when offline.
        assertThat(resolver.resolve(ALICE, false, "communication_broadcasts")).contains("-");
    }

    @Test
    void communicationDegradesWhenModuleIsDisabled() {
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().build());

        assertThat(resolver.resolve(ALICE, true, "communication_chat_enabled")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "communication_broadcasts")).contains("-");
        // An unknown communication_ tail still resolves through the branch to the dash, never the raw token.
        assertThat(resolver.resolve(ALICE, true, "communication_unknown")).contains("-");
    }

    @Test
    void disabledContextsDegradeToTheirEmptyDefault() {
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().build());

        assertThat(resolver.resolve(ALICE, true, "homes_count")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "balance")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "afk")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "kit_cooldown_daily")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "vaults_count")).contains("-");
        // A disabled moderation module means "no one is sanctioned", not the dash.
        assertThat(resolver.resolve(ALICE, true, "muted")).contains("no");
        assertThat(resolver.resolve(ALICE, true, "jailed")).contains("no");
    }

    @Test
    void playerstatePlaceholdersReadLiveState() {
        PlayerstatePlaceholders.Snapshot snapshot = new PlayerstatePlaceholders.Snapshot(
                "creative",
                true,
                false,
                true,
                0.2f,
                0.1f,
                18.0,
                20.0,
                17,
                30,
                0.25f,
                "world",
                100,
                64,
                -200,
                "plains",
                Duration.ofHours(5).plusMinutes(30));
        PlaceholderResolver resolver = resolverWith(PlaceholderContexts.builder()
                .playerstate(who -> Optional.of(snapshot))
                .build());

        assertThat(resolver.resolve(ALICE, true, "playerstate_gamemode")).contains("creative");
        assertThat(resolver.resolve(ALICE, true, "playerstate_fly")).contains("yes");
        assertThat(resolver.resolve(ALICE, true, "playerstate_flying")).contains("no");
        assertThat(resolver.resolve(ALICE, true, "playerstate_god")).contains("yes");
        assertThat(resolver.resolve(ALICE, true, "playerstate_health")).contains("18");
        assertThat(resolver.resolve(ALICE, true, "playerstate_max_health")).contains("20");
        assertThat(resolver.resolve(ALICE, true, "playerstate_food")).contains("17");
        assertThat(resolver.resolve(ALICE, true, "playerstate_level")).contains("30");
        assertThat(resolver.resolve(ALICE, true, "playerstate_xp")).contains("0.25");
        assertThat(resolver.resolve(ALICE, true, "playerstate_world")).contains("world");
        assertThat(resolver.resolve(ALICE, true, "playerstate_x")).contains("100");
        assertThat(resolver.resolve(ALICE, true, "playerstate_y")).contains("64");
        assertThat(resolver.resolve(ALICE, true, "playerstate_z")).contains("-200");
        assertThat(resolver.resolve(ALICE, true, "playerstate_biome")).contains("plains");
        assertThat(resolver.resolve(ALICE, true, "playerstate_playtime")).contains("5");
        assertThat(resolver.resolve(ALICE, true, "playerstate_playtime_formatted"))
                .contains("5h30m");
    }

    @Test
    void playerstateSpeedFollowsWhetherTheyAreFlying() {
        PlayerstatePlaceholders.Snapshot walking = new PlayerstatePlaceholders.Snapshot(
                "survival",
                false,
                false,
                false,
                0.2f,
                0.1f,
                20.0,
                20.0,
                20,
                0,
                0f,
                "world",
                0,
                0,
                0,
                "plains",
                Duration.ZERO);
        PlayerstatePlaceholders.Snapshot flying = new PlayerstatePlaceholders.Snapshot(
                "survival",
                true,
                true,
                false,
                0.2f,
                0.1f,
                20.0,
                20.0,
                20,
                0,
                0f,
                "world",
                0,
                0,
                0,
                "plains",
                Duration.ZERO);

        assertThat(resolverWith(PlaceholderContexts.builder()
                                .playerstate(who -> Optional.of(walking))
                                .build())
                        .resolve(ALICE, true, "playerstate_speed"))
                .contains("0.2");
        assertThat(resolverWith(PlaceholderContexts.builder()
                                .playerstate(who -> Optional.of(flying))
                                .build())
                        .resolve(ALICE, true, "playerstate_speed"))
                .contains("0.1");
    }

    @Test
    void playerstatePlaceholdersDegradeOfflineAndWhenDisabled() {
        PlayerstatePlaceholders empty = who -> Optional.empty();
        PlaceholderResolver withSeam =
                resolverWith(PlaceholderContexts.builder().playerstate(empty).build());
        PlaceholderResolver noSeam = resolverWith(PlaceholderContexts.builder().build());

        // Offline: the seam reports no snapshot, so every key is the dash.
        assertThat(withSeam.resolve(ALICE, true, "playerstate_gamemode")).contains("-");
        // The offline guard short-circuits before the seam is even consulted.
        assertThat(withSeam.resolve(ALICE, false, "playerstate_health")).contains("-");
        // Disabled module: no seam at all.
        assertThat(noSeam.resolve(ALICE, true, "playerstate_world")).contains("-");
        assertThat(noSeam.resolve(ALICE, true, "playerstate_unknown")).contains("-");
    }

    @Test
    void serverMetricsReadGlobalsIgnoringTheRequester() {
        FakeServerMetrics metrics = new FakeServerMetrics()
                .online(12)
                .maxPlayers(50)
                .version("1.21.11")
                .uptime(Duration.ofHours(1).plusMinutes(30))
                .tps(19.97, 19.5, 18.2)
                .ram(2048, 4096, 1500)
                .worldPlayers("world", 8);
        PlaceholderResolver resolver = resolverWith(
                PlaceholderContexts.builder().serverMetrics(metrics).build());

        // The requesting player and online flag are irrelevant: every value is server-wide.
        assertThat(resolver.resolve(ALICE, false, "server_online")).contains("12");
        assertThat(resolver.resolve(BOB, true, "server_max_players")).contains("50");
        assertThat(resolver.resolve(ALICE, true, "server_version")).contains("1.21.11");
        assertThat(resolver.resolve(ALICE, true, "server_uptime")).contains("90");
        assertThat(resolver.resolve(ALICE, true, "server_uptime_formatted")).contains("1h30m");
        assertThat(resolver.resolve(ALICE, true, "server_ram_used")).contains("2048");
        assertThat(resolver.resolve(ALICE, true, "server_ram_max")).contains("4096");
        assertThat(resolver.resolve(ALICE, true, "server_ram_free")).contains("1500");
        assertThat(resolver.resolve(ALICE, true, "server_world_players_world")).contains("8");
    }

    @Test
    void serverTpsReadsEachWindowClampedAndTrimmed() {
        // The 1-minute window over-reports above 20 on a fresh server; it is clamped to the 20.0 ceiling.
        FakeServerMetrics metrics = new FakeServerMetrics().tps(20.04, 19.5, 15.0);
        PlaceholderResolver resolver = resolverWith(
                PlaceholderContexts.builder().serverMetrics(metrics).build());

        assertThat(resolver.resolve(ALICE, true, "server_tps")).contains("20");
        assertThat(resolver.resolve(ALICE, true, "server_tps_5m")).contains("19.5");
        assertThat(resolver.resolve(ALICE, true, "server_tps_15m")).contains("15");
    }

    @Test
    void serverTpsColoredWrapsTheRateInGreenYellowOrRed() {
        PlaceholderResolver healthy = resolverWith(PlaceholderContexts.builder()
                .serverMetrics(new FakeServerMetrics().tps(19.9, 19.9, 19.9))
                .build());
        PlaceholderResolver strained = resolverWith(PlaceholderContexts.builder()
                .serverMetrics(new FakeServerMetrics().tps(16.0, 16.0, 16.0))
                .build());
        PlaceholderResolver lagging = resolverWith(PlaceholderContexts.builder()
                .serverMetrics(new FakeServerMetrics().tps(9.0, 9.0, 9.0))
                .build());

        assertThat(healthy.resolve(ALICE, true, "server_tps_colored")).contains("<green>19.9</green>");
        assertThat(strained.resolve(ALICE, true, "server_tps_colored")).contains("<yellow>16</yellow>");
        assertThat(lagging.resolve(ALICE, true, "server_tps_colored")).contains("<red>9</red>");
    }

    @Test
    void serverWorldPlayersDashesUnknownWorldAndBlankName() {
        FakeServerMetrics metrics = new FakeServerMetrics().worldPlayers("world", 4);
        PlaceholderResolver resolver = resolverWith(
                PlaceholderContexts.builder().serverMetrics(metrics).build());

        assertThat(resolver.resolve(ALICE, true, "server_world_players_world")).contains("4");
        assertThat(resolver.resolve(ALICE, true, "server_world_players_void")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "server_world_players_")).contains("-");
    }

    @Test
    void serverWorldClockAndWeatherReadOneNamedWorld() {
        FakeServerMetrics metrics = new FakeServerMetrics()
                .worldSky("world", new ServerMetricsPlaceholders.WorldSky(18_000L, false, false))
                .worldSky("world_nether", new ServerMetricsPlaceholders.WorldSky(0L, true, true));
        PlaceholderResolver resolver = resolverWith(
                PlaceholderContexts.builder().serverMetrics(metrics).build());

        assertThat(resolver.resolve(ALICE, true, "server_world_time_world")).contains("18000");
        assertThat(resolver.resolve(ALICE, true, "server_world_time_formatted_world"))
                .contains("00:00");
        assertThat(resolver.resolve(ALICE, true, "server_world_weather_world")).contains("clear");
        assertThat(resolver.resolve(ALICE, true, "server_world_weather_world_nether"))
                .contains("thunder");
        assertThat(resolver.resolve(ALICE, true, "server_world_time_void")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "server_world_weather_")).contains("-");
    }

    @Test
    void serverMetricsDegradeWhenSeamAbsentButUnknownKeyStaysRaw() {
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().build());

        // No seam wired: every server_ key degrades to the dash rather than the raw token.
        assertThat(resolver.resolve(ALICE, true, "server_online")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "server_tps")).contains("-");
        // An unknown server_ tail still resolves through the branch to the dash, never the raw token.
        assertThat(resolver.resolve(ALICE, true, "server_unknown")).contains("-");
    }

    @Test
    void ranksPlaceholdersResolveCurrentNextAndPrestige() {
        PlaceholderResolver resolver = resolverWith(PlaceholderContexts.builder()
                .ranks(who -> Optional.of(new RanksPlaceholders.Standing(
                        "Citizen", Optional.of("VIP"), 2, 2, 5, OptionalLong.of(2_500L))))
                .build());

        assertThat(resolver.resolve(ALICE, true, "rank")).contains("Citizen");
        assertThat(resolver.resolve(ALICE, true, "rank_next")).contains("VIP");
        assertThat(resolver.resolve(ALICE, true, "prestige")).contains("2");
        assertThat(resolver.resolve(ALICE, true, "rank_position")).contains("2");
        assertThat(resolver.resolve(ALICE, true, "rank_total")).contains("5");
        assertThat(resolver.resolve(ALICE, true, "rank_progress")).contains("40");
        assertThat(resolver.resolve(ALICE, true, "rank_next_cost")).contains("2500");
    }

    @Test
    void rankNextReadsTheMaxMarkerAtTheTopRank() {
        PlaceholderResolver resolver = resolverWith(PlaceholderContexts.builder()
                .ranks(who -> Optional.of(
                        new RanksPlaceholders.Standing("VIP", Optional.empty(), 0, 5, 5, OptionalLong.empty())))
                .build());

        assertThat(resolver.resolve(ALICE, true, "rank")).contains("VIP");
        assertThat(resolver.resolve(ALICE, true, "rank_next")).contains("max");
        assertThat(resolver.resolve(ALICE, true, "rank_next_cost")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "rank_progress")).contains("100");
    }

    @Test
    void ranksPlaceholdersDegradeToTheDashWhenTheModuleIsOff() {
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().build());

        assertThat(resolver.resolve(ALICE, true, "rank")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "rank_next")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "prestige")).contains("-");
    }

    private static PresencePlaceholders presenceSeam(PresencePlaceholders.Snapshot snapshot) {
        return who -> Optional.of(snapshot);
    }

    @Test
    void theOtherPlayerFormAnswersAboutTheNamedPlayer() {
        PlaceholderResolver resolver = resolverWith(PlaceholderContexts.builder()
                .players(lookupOf(BOB))
                .homes(fakeHomes(4, 9))
                .build());

        assertThat(resolver.resolve(ALICE, true, "p_bob_homes_count")).contains("4");
    }

    @Test
    void theOtherPlayerFormCutsTheNameWhereTheCatalogueSaysTheKeyBegins() {
        // A player name may carry underscores, so the split cannot simply be the first one.
        PlayerRef underscored = new PlayerRef(UUID.randomUUID(), "not_ch");
        PlaceholderResolver resolver = resolverWith(PlaceholderContexts.builder()
                .players(lookupOf(underscored))
                .homes(fakeHomes(2, 9))
                .build());

        assertThat(resolver.resolve(ALICE, true, "p_not_ch_homes_count")).contains("2");
    }

    @Test
    void theOtherPlayerFormDashesWhenTheNameOrTheKeyIsUnknown() {
        PlaceholderResolver resolver = resolverWith(PlaceholderContexts.builder()
                .players(lookupOf(BOB))
                .homes(fakeHomes(4, 9))
                .build());

        assertThat(resolver.resolve(ALICE, true, "p_nobody_homes_count")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "p_bob_not_a_placeholder")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "p_bob")).contains("-");
    }

    @Test
    void theOtherPlayerFormDoesNotNestIntoItself() {
        PlaceholderResolver resolver = resolverWith(PlaceholderContexts.builder()
                .players(lookupOf(BOB))
                .homes(fakeHomes(4, 9))
                .build());

        assertThat(resolver.resolve(ALICE, true, "p_bob_p_bob_homes_count")).contains("-");
    }

    @Test
    void theOtherPlayerFormDashesWithNoLookupWired() {
        PlaceholderResolver resolver = resolverWith(
                PlaceholderContexts.builder().homes(fakeHomes(4, 9)).build());

        assertThat(resolver.resolve(ALICE, true, "p_bob_homes_count")).contains("-");
    }

    @Test
    void theAccountKeysAnswerForAnOfflinePlayerToo() {
        FakePlayerFacts facts = new FakePlayerFacts()
                .account(new PlayerFactsPlaceholders.Account(
                        Optional.of(Instant.parse("2024-03-01T10:15:00Z")),
                        Optional.of(Instant.parse("2026-08-11T21:00:00Z")),
                        Duration.ofHours(30).plusMinutes(90),
                        true));
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().playerFacts(facts).build());

        assertThat(resolver.resolve(ALICE, false, "player_playtime")).contains("31");
        assertThat(resolver.resolve(ALICE, false, "player_playtime_minutes")).contains("1890");
        assertThat(resolver.resolve(ALICE, false, "player_playtime_formatted")).contains("1d7h30m");
        assertThat(resolver.resolve(ALICE, false, "player_banned")).contains("yes");
        assertThat(resolver.resolve(ALICE, false, "player_first_join")).isPresent();
        assertThat(resolver.resolve(ALICE, false, "player_last_seen_date")).isPresent();
    }

    @Test
    void theIdentityKeysReadWhoThePlayerIsToTheServer() {
        FakePlayerFacts facts = new FakePlayerFacts()
                .identity(new PlayerFactsPlaceholders.Identity(
                        "Alice",
                        "[VIP] Alice",
                        "0b5f3f42-0000-0000-0000-000000000001",
                        Optional.of("203.0.113.7"),
                        "en_us",
                        "survival",
                        true,
                        true,
                        0.1f,
                        0.2f,
                        Optional.of(new PlayerFactsPlaceholders.Position("world", 100.5, 64.0, -33.9)),
                        Optional.of(new PlayerFactsPlaceholders.Position("world", 0.0, 70.0, 0.0))));
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().playerFacts(facts).build());

        assertThat(resolver.resolve(ALICE, true, "player_name")).contains("Alice");
        assertThat(resolver.resolve(ALICE, true, "player_display_name")).contains("[VIP] Alice");
        assertThat(resolver.resolve(ALICE, true, "player_ip")).contains("203.0.113.7");
        assertThat(resolver.resolve(ALICE, true, "player_locale")).contains("en_us");
        assertThat(resolver.resolve(ALICE, true, "player_gamemode")).contains("survival");
        assertThat(resolver.resolve(ALICE, true, "player_flying")).contains("yes");
        assertThat(resolver.resolve(ALICE, true, "player_fly_speed")).contains("0.1");
        assertThat(resolver.resolve(ALICE, true, "player_has_bed")).contains("yes");
        assertThat(resolver.resolve(ALICE, true, "player_bed")).contains("world 100 64 -34");
        assertThat(resolver.resolve(ALICE, true, "player_compass")).contains("world 0 70 0");
    }

    @Test
    void theVitalKeysRenderTheBodyAndItsShare() {
        FakePlayerFacts facts = new FakePlayerFacts()
                .vitals(new PlayerFactsPlaceholders.Vitals(15.0, 20.0, 18, 4.5f, 200, 300, 12.0, 4.0, true));
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().playerFacts(facts).build());

        assertThat(resolver.resolve(ALICE, true, "player_health")).contains("15");
        assertThat(resolver.resolve(ALICE, true, "player_health_max")).contains("20");
        assertThat(resolver.resolve(ALICE, true, "player_health_percent")).contains("75");
        assertThat(resolver.resolve(ALICE, true, "player_food")).contains("18");
        assertThat(resolver.resolve(ALICE, true, "player_saturation")).contains("4.5");
        assertThat(resolver.resolve(ALICE, true, "player_air")).contains("200");
        assertThat(resolver.resolve(ALICE, true, "player_air_max")).contains("300");
        assertThat(resolver.resolve(ALICE, true, "player_armor")).contains("12");
        assertThat(resolver.resolve(ALICE, true, "player_absorption")).contains("4");
        assertThat(resolver.resolve(ALICE, true, "player_burning")).contains("yes");
    }

    @Test
    void aHealthPercentOfNothingIsZeroRatherThanADivisionByZero() {
        FakePlayerFacts facts = new FakePlayerFacts()
                .vitals(new PlayerFactsPlaceholders.Vitals(0.0, 0.0, 0, 0f, 0, 0, 0.0, 0.0, false));
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().playerFacts(facts).build());

        assertThat(resolver.resolve(ALICE, true, "player_health_percent")).contains("0");
    }

    @Test
    void thePositionKeysRenderBlocksExactCoordinatesAndTheFacing() {
        FakePlayerFacts facts = new FakePlayerFacts()
                .where(new PlayerFactsPlaceholders.Where(
                        "world", "normal", 100.75, 64.0, -33.25, 90f, -12.5f, "plains", "grass_block", 15));
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().playerFacts(facts).build());

        assertThat(resolver.resolve(ALICE, true, "player_x")).contains("100");
        assertThat(resolver.resolve(ALICE, true, "player_z")).contains("-34");
        assertThat(resolver.resolve(ALICE, true, "player_x_exact")).contains("100.75");
        assertThat(resolver.resolve(ALICE, true, "player_pitch")).contains("-12.5");
        assertThat(resolver.resolve(ALICE, true, "player_biome")).contains("plains");
        assertThat(resolver.resolve(ALICE, true, "player_block_below")).contains("grass_block");
        assertThat(resolver.resolve(ALICE, true, "player_light")).contains("15");
        assertThat(resolver.resolve(ALICE, true, "player_world_environment")).contains("normal");
        assertThat(resolver.resolve(ALICE, true, "player_location")).contains("world 100 64 -34");
    }

    @Test
    void everyEighthOfATurnHasItsOwnNameAndTheTurnWrapsAround() {
        FakePlayerFacts facts = new FakePlayerFacts();
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().playerFacts(facts).build());

        assertThat(facing(resolver, facts, 0f)).contains("south");
        assertThat(facing(resolver, facts, 45f)).contains("south_west");
        assertThat(facing(resolver, facts, 180f)).contains("north");
        assertThat(facing(resolver, facts, -90f)).contains("east");
        assertThat(facing(resolver, facts, 359f)).contains("south");
    }

    private static Optional<String> facing(PlaceholderResolver resolver, FakePlayerFacts facts, float yaw) {
        facts.where(new PlayerFactsPlaceholders.Where("world", "normal", 0, 0, 0, yaw, 0f, "plains", "stone", 0));
        return resolver.resolve(ALICE, true, "player_direction");
    }

    @Test
    void aStatisticIsReadWholeOrSplitIntoItsQualifier() {
        FakePlayerFacts facts = new FakePlayerFacts()
                .statistic("deaths", 7L)
                .statistic("player_kills", 3L)
                .statistic("mine_block_diamond_ore", 42L);
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().playerFacts(facts).build());

        assertThat(resolver.resolve(ALICE, true, "stat_deaths")).contains("7");
        assertThat(resolver.resolve(ALICE, true, "stat_mine_block_diamond_ore")).contains("42");
        assertThat(resolver.resolve(ALICE, true, "player_deaths")).contains("7");
        assertThat(resolver.resolve(ALICE, true, "player_kills")).contains("3");
        assertThat(resolver.resolve(ALICE, true, "stat_nothing_like_this")).contains("-");
    }

    @Test
    void theServerKeysNameTheServerAndCountItsWorlds() {
        FakeServerMetrics metrics =
                new FakeServerMetrics().named("Survival", "Welcome home", 3).worldCounts("world", 812, 441);
        PlaceholderResolver resolver = resolverWith(
                PlaceholderContexts.builder().serverMetrics(metrics).build());

        assertThat(resolver.resolve(ALICE, true, "server_name")).contains("Survival");
        assertThat(resolver.resolve(ALICE, true, "server_motd")).contains("Welcome home");
        assertThat(resolver.resolve(ALICE, true, "server_worlds")).contains("3");
        assertThat(resolver.resolve(ALICE, true, "server_world_entities_world")).contains("812");
        assertThat(resolver.resolve(ALICE, true, "server_world_chunks_world")).contains("441");
        assertThat(resolver.resolve(ALICE, true, "server_world_entities_nether"))
                .contains("-");
        assertThat(resolver.resolve(ALICE, true, "server_time")).isPresent();
        assertThat(resolver.resolve(ALICE, true, "server_date")).isPresent();
    }

    @Test
    void theSessionKeysReadTheDashWhileThePlayerIsOffline() {
        FakePlayerFacts facts = new FakePlayerFacts()
                .account(new PlayerFactsPlaceholders.Account(Optional.empty(), Optional.empty(), Duration.ZERO, false));
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().playerFacts(facts).build());

        assertThat(resolver.resolve(ALICE, false, "player_ping")).contains("-");
        assertThat(resolver.resolve(ALICE, false, "player_world")).contains("-");
        assertThat(resolver.resolve(ALICE, false, "player_first_join")).contains("-");
    }

    @Test
    void theSessionKeysRenderTheLiveSession() {
        FakePlayerFacts facts = new FakePlayerFacts()
                .session(new PlayerFactsPlaceholders.Session(
                        42, true, false, true, "world_nether", 6_000L, true, false, 30, 1_395, 62, 0.25f));
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().playerFacts(facts).build());

        assertThat(resolver.resolve(ALICE, true, "player_ping")).contains("42");
        assertThat(resolver.resolve(ALICE, true, "player_sneaking")).contains("yes");
        assertThat(resolver.resolve(ALICE, true, "player_sprinting")).contains("no");
        assertThat(resolver.resolve(ALICE, true, "player_op")).contains("yes");
        assertThat(resolver.resolve(ALICE, true, "player_world")).contains("world_nether");
        assertThat(resolver.resolve(ALICE, true, "player_world_time")).contains("6000");
        assertThat(resolver.resolve(ALICE, true, "player_world_time_formatted")).contains("12:00");
        assertThat(resolver.resolve(ALICE, true, "player_world_weather")).contains("rain");
        assertThat(resolver.resolve(ALICE, true, "player_level")).contains("30");
        assertThat(resolver.resolve(ALICE, true, "player_exp_total")).contains("1395");
        assertThat(resolver.resolve(ALICE, true, "player_exp_to_next")).contains("62");
        assertThat(resolver.resolve(ALICE, true, "player_exp_percent")).contains("25");
    }

    @Test
    void aThunderstormOutranksTheRainFlag() {
        FakePlayerFacts facts = new FakePlayerFacts()
                .session(new PlayerFactsPlaceholders.Session(
                        1, false, false, false, "world", 0L, true, true, 0, 0, 7, 0f));
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().playerFacts(facts).build());

        assertThat(resolver.resolve(ALICE, true, "player_world_weather")).contains("thunder");
        assertThat(resolver.resolve(ALICE, true, "player_world_time_formatted")).contains("06:00");
    }

    @Test
    void theHeldItemKeysReadEachHandSeparately() {
        FakePlayerFacts facts = new FakePlayerFacts()
                .held(
                        PlayerFactsPlaceholders.Hand.MAIN,
                        new PlayerFactsPlaceholders.HeldItem(
                                "diamond_pickaxe",
                                "Spitzhacke",
                                1,
                                31,
                                1_561,
                                List.of("efficiency 5", "unbreaking 3"),
                                List.of("Mine faster"),
                                OptionalInt.of(7)))
                .held(
                        PlayerFactsPlaceholders.Hand.OFF,
                        new PlayerFactsPlaceholders.HeldItem(
                                "torch", "torch", 16, 0, 0, List.of(), List.of(), OptionalInt.empty()));
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().playerFacts(facts).build());

        assertThat(resolver.resolve(ALICE, true, "hand_type")).contains("diamond_pickaxe");
        assertThat(resolver.resolve(ALICE, true, "hand_name")).contains("Spitzhacke");
        assertThat(resolver.resolve(ALICE, true, "hand_durability")).contains("1530");
        assertThat(resolver.resolve(ALICE, true, "hand_durability_max")).contains("1561");
        assertThat(resolver.resolve(ALICE, true, "hand_enchants")).contains("efficiency 5, unbreaking 3");
        assertThat(resolver.resolve(ALICE, true, "hand_enchants_count")).contains("2");
        assertThat(resolver.resolve(ALICE, true, "hand_lore")).contains("Mine faster");
        assertThat(resolver.resolve(ALICE, true, "hand_model")).contains("7");
        assertThat(resolver.resolve(ALICE, true, "offhand_amount")).contains("16");
        assertThat(resolver.resolve(ALICE, true, "offhand_enchants")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "offhand_model")).contains("-");
    }

    @Test
    void anEmptyHandAndAnUnknownMaterialBothDash() {
        PlaceholderResolver resolver = resolverWith(
                PlaceholderContexts.builder().playerFacts(new FakePlayerFacts()).build());

        assertThat(resolver.resolve(ALICE, true, "hand_type")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "itemcount_not_a_material")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "itemcount_")).contains("-");
    }

    @Test
    void theItemCountKeyCountsWhatThePlayerCarries() {
        PlaceholderResolver resolver = resolverWith(PlaceholderContexts.builder()
                .playerFacts(new FakePlayerFacts().itemCount("diamond", 12))
                .build());

        assertThat(resolver.resolve(ALICE, true, "itemcount_diamond")).contains("12");
    }

    @Test
    void thePlayerFactsDashWithNoSeamWired() {
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().build());

        assertThat(resolver.resolve(ALICE, true, "player_ping")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "hand_type")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "itemcount_diamond")).contains("-");
    }

    @Test
    void theOtherPlayerFormReachesThePlayerFactsToo() {
        PlaceholderResolver resolver = resolverWith(PlaceholderContexts.builder()
                .players(lookupOf(BOB))
                .playerFacts(new FakePlayerFacts()
                        .session(new PlayerFactsPlaceholders.Session(
                                77, false, false, false, "world", 0L, false, false, 1, 2, 3, 0f)))
                .build());

        assertThat(resolver.resolve(ALICE, true, "p_bob_player_ping")).contains("77");
    }

    @Test
    void theGenericCooldownFamilyReadsTheLabelTheOperatorChose() {
        PlaceholderResolver resolver = resolverWith(PlaceholderContexts.builder()
                .cooldowns(cooldownsHolding("daily_reward", Duration.ofMinutes(90)))
                .build());

        assertThat(resolver.resolve(ALICE, true, "cooldown_daily_reward")).contains("5400");
        assertThat(resolver.resolve(ALICE, true, "cooldown_daily_reward_formatted"))
                .contains("1h30m");
        assertThat(resolver.resolve(ALICE, true, "cooldown_active_daily_reward"))
                .contains("yes");
    }

    @Test
    void anOpenCooldownReadsZeroRatherThanTheDash() {
        PlaceholderResolver resolver = resolverWith(PlaceholderContexts.builder()
                .cooldowns(cooldownsHolding("daily_reward", Duration.ofMinutes(90)))
                .build());

        assertThat(resolver.resolve(ALICE, true, "cooldown_something_else")).contains("0");
        assertThat(resolver.resolve(ALICE, true, "cooldown_something_else_formatted"))
                .contains("0s");
        assertThat(resolver.resolve(ALICE, true, "cooldown_active_something_else"))
                .contains("no");
    }

    @Test
    void theCooldownFamilyDashesWithNoGateWiredOrNoLabelGiven() {
        assertThat(resolverWith(PlaceholderContexts.builder().build()).resolve(ALICE, true, "cooldown_daily"))
                .contains("-");
        PlaceholderResolver resolver = resolverWith(PlaceholderContexts.builder()
                .cooldowns(cooldownsHolding("daily", Duration.ofSeconds(5)))
                .build());
        assertThat(resolver.resolve(ALICE, true, "cooldown_")).contains("-");
        assertThat(resolver.resolve(ALICE, true, "cooldown_active_")).contains("-");
    }

    @Test
    void theFormattingHelpersRenderTheirOwnInput() {
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().build());

        assertThat(resolver.resolve(ALICE, true, "format_number_1234567")).contains("1,234,567");
        assertThat(resolver.resolve(ALICE, true, "format_compact_1234567")).contains("1.23M");
        assertThat(resolver.resolve(ALICE, true, "format_time_3725")).contains("1h2m5s");
        assertThat(resolver.resolve(ALICE, true, "progressbar_5_10_10")).contains("█████░░░░░");
        assertThat(resolver.resolve(ALICE, true, "format_number_soon")).contains("-");
    }

    @Test
    void theRelationalKeysReadOneSideAgainstTheOther() {
        PlaceholderResolver resolver = resolverWith(PlaceholderContexts.builder()
                .messaging(new FakeMessaging().ignores(BOB))
                .visibility((viewer, target) -> target.equals(BOB))
                .trade(tradingPair(ALICE, BOB))
                .playerFacts(
                        new FakePlayerFacts().standing(ALICE, "world", 0, 64, 0).standing(BOB, "world", 3, 64, 4))
                .build());

        assertThat(resolver.resolveRelational(ALICE, BOB, "ignoring")).contains("yes");
        assertThat(resolver.resolveRelational(BOB, ALICE, "ignoring")).contains("no");
        assertThat(resolver.resolveRelational(BOB, ALICE, "ignored_by")).contains("yes");
        assertThat(resolver.resolveRelational(ALICE, BOB, "cansee")).contains("no");
        assertThat(resolver.resolveRelational(ALICE, BOB, "hidden")).contains("yes");
        assertThat(resolver.resolveRelational(BOB, ALICE, "cansee")).contains("yes");
        assertThat(resolver.resolveRelational(ALICE, BOB, "trading")).contains("yes");
        assertThat(resolver.resolveRelational(ALICE, BOB, "same_world")).contains("yes");
        assertThat(resolver.resolveRelational(ALICE, BOB, "distance")).contains("5");
    }

    @Test
    void distanceAcrossWorldsAndForAnOfflinePlayerReadsTheDash() {
        PlaceholderResolver resolver = resolverWith(PlaceholderContexts.builder()
                .playerFacts(new FakePlayerFacts()
                        .standing(ALICE, "world", 0, 64, 0)
                        .standing(BOB, "world_nether", 0, 64, 0))
                .build());

        assertThat(resolver.resolveRelational(ALICE, BOB, "distance")).contains("-");
        assertThat(resolver.resolveRelational(ALICE, BOB, "same_world")).contains("no");
        PlayerRef offline = new PlayerRef(UUID.randomUUID(), "Ghost");
        assertThat(resolver.resolveRelational(ALICE, offline, "distance")).contains("-");
        assertThat(resolver.resolveRelational(ALICE, offline, "same_world")).contains("no");
    }

    @Test
    void theRelationalKeysDegradeWithEveryModuleOff() {
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().build());

        // Nobody is hidden, nobody is ignoring, nobody is trading: the same answers a disabled module gives.
        assertThat(resolver.resolveRelational(ALICE, BOB, "cansee")).contains("yes");
        assertThat(resolver.resolveRelational(ALICE, BOB, "hidden")).contains("no");
        assertThat(resolver.resolveRelational(ALICE, BOB, "ignoring")).contains("no");
        assertThat(resolver.resolveRelational(ALICE, BOB, "trading")).contains("no");
        assertThat(resolver.resolveRelational(ALICE, BOB, "distance")).contains("-");
    }

    @Test
    void anUnknownRelationalKeyLeavesTheRawToken() {
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().build());

        assertThat(resolver.resolveRelational(ALICE, BOB, "nothing_like_this")).isEmpty();
        // A one-player key is not answered through the relational form, and the other way round.
        assertThat(resolver.resolveRelational(ALICE, BOB, "server_online")).isEmpty();
        assertThat(resolver.resolve(ALICE, true, "cansee")).isEmpty();
    }

    @Test
    void aSurvivalMechanicIsReadPerPlayerAndPerServerSeparately() {
        PlaceholderResolver resolver = resolverWith(PlaceholderContexts.builder()
                .survival(new SurvivalPlaceholders() {
                    @Override
                    public boolean active(PlayerRef who, Mechanic mechanic) {
                        return mechanic == Mechanic.AUTO_PICKUP;
                    }

                    @Override
                    public boolean enabled(Mechanic mechanic) {
                        return mechanic != Mechanic.AUTO_SELL;
                    }
                })
                .build());

        assertThat(resolver.resolve(ALICE, true, "survival_autopickup")).contains("yes");
        assertThat(resolver.resolve(ALICE, true, "survival_veinminer")).contains("no");
        // The switch a player left on reads on even where the server no longer runs the mechanic: the pair is
        // what lets a HUD explain why nothing is happening.
        assertThat(resolver.resolve(ALICE, true, "survival_autosell_enabled")).contains("no");
        assertThat(resolver.resolve(ALICE, true, "survival_treefeller_enabled")).contains("yes");
        assertThat(resolver.resolve(ALICE, true, "survival_nothing_like_this")).contains("-");
    }

    @Test
    void theItemworldKeysReadTheHeldBindingAndTheTwoSwitches() {
        PlaceholderResolver resolver = resolverWith(PlaceholderContexts.builder()
                .itemworld(new ItemworldPlaceholders() {
                    @Override
                    public List<String> powertool(PlayerRef who) {
                        return who.equals(ALICE) ? List.of("spawn", "kit tools") : List.of();
                    }

                    @Override
                    public boolean powertoolEnabled(PlayerRef who) {
                        return true;
                    }

                    @Override
                    public boolean unlimitedPlacement(PlayerRef who) {
                        return false;
                    }
                })
                .build());

        assertThat(resolver.resolve(ALICE, true, "itemworld_powertool")).contains("spawn, kit tools");
        assertThat(resolver.resolve(ALICE, true, "itemworld_powertool_count")).contains("2");
        assertThat(resolver.resolve(ALICE, true, "itemworld_powertool_bound")).contains("yes");
        assertThat(resolver.resolve(ALICE, true, "itemworld_powertool_enabled")).contains("yes");
        assertThat(resolver.resolve(ALICE, true, "itemworld_unlimited")).contains("no");
        assertThat(resolver.resolve(BOB, true, "itemworld_powertool")).contains("-");
        assertThat(resolver.resolve(BOB, true, "itemworld_powertool_bound")).contains("no");
    }

    @Test
    void theNpcQuotaKeysCountWhatIsLeftAndSayUnlimitedWhenThereIsNoCap() {
        PlaceholderResolver capped = resolverWith(PlaceholderContexts.builder()
                .npc(npcSeam(12, 3, OptionalInt.of(5)))
                .build());

        assertThat(capped.resolve(ALICE, true, "npc_total")).contains("12");
        assertThat(capped.resolve(ALICE, true, "npc_owned")).contains("3");
        assertThat(capped.resolve(ALICE, true, "npc_limit")).contains("5");
        assertThat(capped.resolve(ALICE, true, "npc_remaining")).contains("2");

        PlaceholderResolver uncapped = resolverWith(PlaceholderContexts.builder()
                .npc(npcSeam(12, 9, OptionalInt.empty()))
                .build());

        assertThat(uncapped.resolve(ALICE, true, "npc_limit")).contains("unlimited");
        assertThat(uncapped.resolve(ALICE, true, "npc_remaining")).contains("unlimited");
    }

    @Test
    void anOverQuotaOwnerHasNoneRemainingRatherThanANegativeCount() {
        PlaceholderResolver resolver = resolverWith(PlaceholderContexts.builder()
                .npc(npcSeam(9, 7, OptionalInt.of(5)))
                .build());

        assertThat(resolver.resolve(ALICE, true, "npc_remaining")).contains("0");
    }

    @Test
    void theRegionKeysNameTheRegionThePlayerStandsInAndItsRoster() {
        PlaceholderResolver resolver = resolverWith(PlaceholderContexts.builder()
                .regions(new RegionsPlaceholders() {
                    @Override
                    public boolean available() {
                        return true;
                    }

                    @Override
                    public Optional<Standing> standingIn(PlayerRef who) {
                        return who.equals(ALICE)
                                ? Optional.of(new Standing("spawn", 7, List.of("Alice"), List.of("Bob", "Cara")))
                                : Optional.empty();
                    }

                    @Override
                    public int coveringCount(PlayerRef who) {
                        return who.equals(ALICE) ? 2 : 0;
                    }

                    @Override
                    public int worldCount(PlayerRef who) {
                        return 14;
                    }
                })
                .build());

        assertThat(resolver.resolve(ALICE, true, "regions_available")).contains("yes");
        assertThat(resolver.resolve(ALICE, true, "regions_inside")).contains("yes");
        assertThat(resolver.resolve(ALICE, true, "regions_here")).contains("spawn");
        assertThat(resolver.resolve(ALICE, true, "regions_here_priority")).contains("7");
        assertThat(resolver.resolve(ALICE, true, "regions_here_owners")).contains("Alice");
        assertThat(resolver.resolve(ALICE, true, "regions_here_members")).contains("Bob, Cara");
        assertThat(resolver.resolve(ALICE, true, "regions_count")).contains("2");
        assertThat(resolver.resolve(ALICE, true, "regions_world_count")).contains("14");
        // Standing in open land is not an error: the name dashes and "inside" says no.
        assertThat(resolver.resolve(BOB, true, "regions_inside")).contains("no");
        assertThat(resolver.resolve(BOB, true, "regions_here")).contains("-");
    }

    @Test
    void theSecurityKeysReadTheLiveChallengeAndNothingAboutEnrolment() {
        PlaceholderResolver resolver = resolverWith(PlaceholderContexts.builder()
                .security(new SecurityPlaceholders() {
                    @Override
                    public boolean verifying(PlayerRef who) {
                        return who.equals(ALICE);
                    }

                    @Override
                    public boolean enforced() {
                        return true;
                    }
                })
                .build());

        assertThat(resolver.resolve(ALICE, true, "security_verifying")).contains("yes");
        assertThat(resolver.resolve(BOB, true, "security_verifying")).contains("no");
        assertThat(resolver.resolve(ALICE, true, "security_enforced")).contains("yes");
        assertThat(resolver.resolve(ALICE, true, "security_enrolled")).contains("-");
    }

    @Test
    void theModuleFamilyAnswersForEveryIdAndDashesWithNothingWired() {
        PlaceholderResolver wired = resolverWith(PlaceholderContexts.builder()
                .modules(id -> id.equals("homes") || id.equals("economy"))
                .build());

        assertThat(wired.resolve(ALICE, true, "module_homes")).contains("yes");
        assertThat(wired.resolve(ALICE, true, "module_economy")).contains("yes");
        assertThat(wired.resolve(ALICE, true, "module_vaults")).contains("no");
        assertThat(wired.resolve(ALICE, true, "module_notamodule")).contains("no");

        PlaceholderResolver bare = resolverWith(PlaceholderContexts.builder().build());
        assertThat(bare.resolve(ALICE, true, "module_homes")).contains("-");
    }

    @Test
    void everySilentModuleFamilyDashesWithItsModuleOff() {
        PlaceholderResolver resolver =
                resolverWith(PlaceholderContexts.builder().build());

        for (String key : List.of(
                "survival_autopickup", "itemworld_powertool", "npc_total", "regions_here", "security_verifying")) {
            assertThat(resolver.resolve(ALICE, true, key))
                    .as("%s must dash rather than leave the raw token", key)
                    .contains("-");
        }
    }

    /** An NPC seam with a fixed population, a fixed owned count, and the quota handed to it. */
    private static NpcPlaceholders npcSeam(int total, int owned, OptionalInt limit) {
        return new NpcPlaceholders() {
            @Override
            public int total() {
                return total;
            }

            @Override
            public int owned(PlayerRef who) {
                return owned;
            }

            @Override
            public OptionalInt limit(PlayerRef who) {
                return limit;
            }
        };
    }

    /** A trade registry holding exactly one live exchange, between the two players handed to it. */
    private static TradePlaceholders tradingPair(PlayerRef one, PlayerRef other) {
        return new TradePlaceholders() {
            @Override
            public boolean isTrading(PlayerRef who) {
                return who.equals(one) || who.equals(other);
            }

            @Override
            public boolean isTradingWith(PlayerRef first, PlayerRef second) {
                return isTrading(first) && isTrading(second) && !first.equals(second);
            }
        };
    }

    /** A gate that holds exactly one label for exactly one wait, and calls every other label open. */
    private static Cooldowns cooldownsHolding(String label, Duration left) {
        return new Cooldowns() {
            @Override
            public Result<Unit, Duration> check(PlayerRef who, CooldownKind kind) {
                return Result.ok();
            }

            @Override
            public void stamp(PlayerRef who, CooldownKind kind) {
                throw new AssertionError("reading a placeholder must never stamp a cooldown");
            }

            @Override
            public Result<Unit, Duration> checkLabel(PlayerRef who, String asked) {
                return label.equals(asked) ? Result.err(left) : Result.ok();
            }

            @Override
            public void stampLabel(PlayerRef who, String asked) {
                throw new AssertionError("reading a placeholder must never stamp a cooldown");
            }
        };
    }

    /** A player-facts seam that answers with exactly what the test seeded, for every player. */
    private static final class FakePlayerFacts implements PlayerFactsPlaceholders {

        private final java.util.Map<Hand, HeldItem> hands = new java.util.EnumMap<>(Hand.class);
        private final java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        private final java.util.Map<UUID, Position> positions = new java.util.HashMap<>();
        private final java.util.Map<String, Long> statistics = new java.util.HashMap<>();
        private @Nullable Session session;
        private @Nullable Account account;
        private @Nullable Identity identity;
        private @Nullable Vitals vitals;
        private @Nullable Where where;

        FakePlayerFacts session(Session live) {
            this.session = live;
            return this;
        }

        FakePlayerFacts account(Account stored) {
            this.account = stored;
            return this;
        }

        FakePlayerFacts held(Hand hand, HeldItem item) {
            hands.put(hand, item);
            return this;
        }

        FakePlayerFacts itemCount(String material, int held) {
            counts.put(material, held);
            return this;
        }

        FakePlayerFacts identity(Identity who) {
            this.identity = who;
            return this;
        }

        FakePlayerFacts vitals(Vitals body) {
            this.vitals = body;
            return this;
        }

        FakePlayerFacts where(Where place) {
            this.where = place;
            return this;
        }

        FakePlayerFacts statistic(String name, long value) {
            statistics.put(name, value);
            return this;
        }

        FakePlayerFacts standing(PlayerRef who, String world, double x, double y, double z) {
            positions.put(who.uuid(), new Position(world, x, y, z));
            return this;
        }

        @Override
        public Optional<Session> session(PlayerRef who) {
            return Optional.ofNullable(session);
        }

        @Override
        public Optional<Account> account(PlayerRef who) {
            return Optional.ofNullable(account);
        }

        @Override
        public Optional<HeldItem> held(PlayerRef who, Hand hand) {
            return Optional.ofNullable(hands.get(hand));
        }

        @Override
        public OptionalInt itemCount(PlayerRef who, String material) {
            Integer held = counts.get(material);
            return held == null ? OptionalInt.empty() : OptionalInt.of(held);
        }

        @Override
        public Optional<Position> position(PlayerRef who) {
            return Optional.ofNullable(positions.get(who.uuid()));
        }

        @Override
        public Optional<Identity> identity(PlayerRef who) {
            return Optional.ofNullable(identity);
        }

        @Override
        public Optional<Vitals> vitals(PlayerRef who) {
            return Optional.ofNullable(vitals);
        }

        @Override
        public Optional<Where> where(PlayerRef who) {
            return Optional.ofNullable(where);
        }

        @Override
        public java.util.OptionalLong statistic(PlayerRef who, String statistic, String qualifier) {
            Long counted = statistics.get(qualifier.isEmpty() ? statistic : statistic + "_" + qualifier);
            return counted == null ? java.util.OptionalLong.empty() : java.util.OptionalLong.of(counted);
        }
    }

    /** A lookup that knows exactly the players handed to it, by name, and calls each of them online. */
    private static PlayerLookup lookupOf(PlayerRef... known) {
        List<PlayerRef> players = List.of(known);
        return new PlayerLookup() {
            @Override
            public Optional<PlayerRef> findOnlineByName(String name) {
                return players.stream()
                        .filter(player -> player.name().equalsIgnoreCase(name))
                        .findFirst();
            }

            @Override
            public Optional<PlayerRef> findByUuid(UUID uuid) {
                return players.stream()
                        .filter(player -> player.uuid().equals(uuid))
                        .findFirst();
            }

            @Override
            public boolean isOnline(UUID uuid) {
                return findByUuid(uuid).isPresent();
            }
        };
    }

    private static PlaceholderResolver resolverWith(PlaceholderContexts contexts) {
        return new PlaceholderResolver(contexts);
    }

    /** A configurable {@link EconomyPlaceholders} fake: every read returns the value the test seeded. */
    private static final class FakeEconomy implements EconomyPlaceholders {

        private final java.util.Map<PlayerRef, Money> defaultBalances = new java.util.HashMap<>();
        private final java.util.Map<String, Money> currencyBalances = new java.util.HashMap<>();
        private final java.util.Map<String, Currency> currencies = new java.util.HashMap<>();
        private final java.util.Map<String, BaltopRow> rows = new java.util.HashMap<>();
        private Currency defaultCurrency = COINS;
        private String formatted = "$0.00";
        private String compact = "$0.00";
        private int position = -1;

        FakeEconomy balance(PlayerRef who, String amount) {
            defaultBalances.put(who, Money.of(COINS, new BigDecimal(amount)));
            return this;
        }

        FakeEconomy balance(PlayerRef who, Currency currency, String amount) {
            currencyBalances.put(key(who, currency), Money.of(currency, new BigDecimal(amount)));
            return this;
        }

        FakeEconomy currency(Currency currency) {
            currencies.put(currency.id().value(), currency);
            this.defaultCurrency = currency;
            return this;
        }

        FakeEconomy formatted(String value) {
            this.formatted = value;
            return this;
        }

        FakeEconomy compactValue(String value) {
            this.compact = value;
            return this;
        }

        FakeEconomy position(int value) {
            this.position = value;
            return this;
        }

        FakeEconomy baltopRow(Currency currency, int rank, BaltopRow row) {
            rows.put(currency.id().value() + "#" + rank, row);
            return this;
        }

        @Override
        public Money balance(PlayerRef who) {
            return defaultBalances.getOrDefault(who, Money.zero(COINS));
        }

        @Override
        public String formatted(PlayerRef who) {
            return formatted;
        }

        @Override
        public String compact(PlayerRef who) {
            return compact;
        }

        @Override
        public OptionalInt baltopPosition(PlayerRef who) {
            return position >= 1 ? OptionalInt.of(position) : OptionalInt.empty();
        }

        @Override
        public Optional<Currency> currency(String currencyId) {
            return Optional.ofNullable(currencies.get(currencyId.toLowerCase(java.util.Locale.ROOT)));
        }

        @Override
        public Currency defaultCurrency() {
            return defaultCurrency;
        }

        @Override
        public Money balance(PlayerRef who, Currency currency) {
            return currencyBalances.getOrDefault(key(who, currency), Money.zero(currency));
        }

        @Override
        public Optional<BaltopRow> baltopRow(Currency currency, int rank) {
            return Optional.ofNullable(rows.get(currency.id().value() + "#" + rank));
        }

        private static String key(PlayerRef who, Currency currency) {
            return who.uuid() + "#" + currency.id().value();
        }
    }

    /** A configurable {@link TeleportPlaceholders} fake: every read returns the value the test seeded. */
    private static final class FakeTeleport implements TeleportPlaceholders {

        private Optional<Duration> cooldown = Optional.empty();
        private Optional<Duration> warmup = Optional.empty();
        private Optional<BackView> back = Optional.empty();
        private int incoming;
        private boolean outgoing;
        private boolean accepting;

        FakeTeleport cooldown(Duration remaining) {
            this.cooldown = Optional.of(remaining);
            return this;
        }

        FakeTeleport warmup(Duration remaining) {
            this.warmup = Optional.of(remaining);
            return this;
        }

        FakeTeleport back(BackView view) {
            this.back = Optional.of(view);
            return this;
        }

        FakeTeleport incoming(int count) {
            this.incoming = count;
            return this;
        }

        FakeTeleport outgoing(boolean pending) {
            this.outgoing = pending;
            return this;
        }

        FakeTeleport accepting(boolean value) {
            this.accepting = value;
            return this;
        }

        @Override
        public Optional<Duration> cooldownRemaining(PlayerRef who) {
            return cooldown;
        }

        @Override
        public Optional<Duration> warmupRemaining(PlayerRef who) {
            return warmup;
        }

        @Override
        public Optional<BackView> backLocation(PlayerRef who) {
            return back;
        }

        @Override
        public int incomingRequests(PlayerRef who) {
            return incoming;
        }

        @Override
        public boolean hasOutgoingRequest(PlayerRef who) {
            return outgoing;
        }

        @Override
        public boolean acceptingRequests(PlayerRef who) {
            return accepting;
        }
    }

    /** A configurable {@link ModerationPlaceholders} fake: every read returns the value the test seeded. */
    private static final class FakeModeration implements ModerationPlaceholders {

        private boolean muted;
        private boolean jailed;
        private boolean frozen;
        private int warns;
        private Optional<SanctionView> ban = Optional.empty();
        private Optional<SanctionView> mute = Optional.empty();
        private Optional<JailView> jail = Optional.empty();

        FakeModeration muted(boolean value) {
            this.muted = value;
            return this;
        }

        FakeModeration jailed(boolean value) {
            this.jailed = value;
            return this;
        }

        FakeModeration frozen(boolean value) {
            this.frozen = value;
            return this;
        }

        FakeModeration warns(int count) {
            this.warns = count;
            return this;
        }

        FakeModeration ban(SanctionView view) {
            this.ban = Optional.of(view);
            return this;
        }

        FakeModeration mute(SanctionView view) {
            this.mute = Optional.of(view);
            this.muted = true;
            return this;
        }

        FakeModeration jail(JailView view) {
            this.jail = Optional.of(view);
            this.jailed = true;
            return this;
        }

        @Override
        public boolean isMuted(PlayerRef who) {
            return muted;
        }

        @Override
        public boolean isJailed(PlayerRef who) {
            return jailed;
        }

        @Override
        public boolean isFrozen(PlayerRef who) {
            return frozen;
        }

        @Override
        public int warnCount(PlayerRef who) {
            return warns;
        }

        @Override
        public Optional<SanctionView> activeBan(PlayerRef who) {
            return ban;
        }

        @Override
        public Optional<SanctionView> activeMute(PlayerRef who) {
            return mute;
        }

        @Override
        public Optional<JailView> activeJail(PlayerRef who) {
            return jail;
        }
    }

    /** A configurable {@link KitsPlaceholders} fake: every read returns the value the test seeded, else empty. */
    private static final class FakeKits implements KitsPlaceholders {

        private final java.util.Map<String, Duration> cooldowns = new java.util.HashMap<>();
        private final java.util.Map<String, Boolean> available = new java.util.HashMap<>();
        private final java.util.Map<String, Boolean> permission = new java.util.HashMap<>();
        private final java.util.Map<String, BigDecimal> costs = new java.util.HashMap<>();
        private final java.util.Map<String, Integer> claimsLeft = new java.util.HashMap<>();
        private List<String> usableIds = List.of();

        FakeKits cooldown(String kitId, Duration remaining) {
            cooldowns.put(kitId, remaining);
            return this;
        }

        FakeKits available(String kitId, boolean value) {
            available.put(kitId, value);
            return this;
        }

        FakeKits hasPermission(String kitId, boolean value) {
            permission.put(kitId, value);
            return this;
        }

        FakeKits cost(String kitId, BigDecimal amount) {
            costs.put(kitId, amount);
            return this;
        }

        FakeKits claimsLeft(String kitId, int value) {
            claimsLeft.put(kitId, value);
            return this;
        }

        FakeKits usable(String... ids) {
            this.usableIds = List.of(ids);
            return this;
        }

        @Override
        public Optional<Duration> cooldownRemaining(PlayerRef who, String kitId) {
            return Optional.ofNullable(cooldowns.get(kitId));
        }

        @Override
        public Optional<Boolean> available(PlayerRef who, String kitId) {
            return Optional.ofNullable(available.get(kitId));
        }

        @Override
        public Optional<Boolean> hasPermission(PlayerRef who, String kitId) {
            return Optional.ofNullable(permission.get(kitId));
        }

        @Override
        public Optional<BigDecimal> cost(String kitId) {
            return Optional.ofNullable(costs.get(kitId));
        }

        @Override
        public Optional<Integer> claimsLeft(PlayerRef who, String kitId) {
            return Optional.ofNullable(claimsLeft.get(kitId));
        }

        @Override
        public List<String> usableIds(PlayerRef who) {
            return usableIds;
        }
    }

    /** A configurable {@link WarpsPlaceholders} fake: only the seeded (visible) warps are counted/listed/found. */
    private static final class FakeWarps implements WarpsPlaceholders {

        private final java.util.LinkedHashMap<String, WarpView> visible = new java.util.LinkedHashMap<>();

        FakeWarps visible(String name, WarpView view) {
            visible.put(name, view);
            return this;
        }

        @Override
        public int count(PlayerRef who) {
            return visible.size();
        }

        @Override
        public List<String> accessibleNames(PlayerRef who) {
            return List.copyOf(visible.keySet());
        }

        @Override
        public Optional<WarpView> find(PlayerRef who, String name) {
            return Optional.ofNullable(visible.get(name.toLowerCase(java.util.Locale.ROOT)));
        }
    }

    private static WarpsPlaceholders.WarpView warpView(
            String world, int x, int y, int z, long visits, String owner, String cost) {
        return new WarpsPlaceholders.WarpView(world, x, y, z, visits, owner, new BigDecimal(cost));
    }

    /** A configurable {@link PlayerwarpsPlaceholders} fake: only the seeded (owned) warps are counted/listed/found. */
    private static final class FakePlayerwarps implements PlayerwarpsPlaceholders {

        private final java.util.LinkedHashMap<String, PlayerWarpView> owned = new java.util.LinkedHashMap<>();
        private final int count;
        private final int limit;

        FakePlayerwarps(int count, int limit) {
            this.count = count;
            this.limit = limit;
        }

        FakePlayerwarps owned(String name, PlayerWarpView view) {
            owned.put(name, view);
            return this;
        }

        @Override
        public int count(PlayerRef who) {
            return count;
        }

        @Override
        public int limit(PlayerRef who) {
            return limit;
        }

        @Override
        public List<PlayerWarpView> list(PlayerRef who) {
            return List.copyOf(owned.values());
        }

        @Override
        public Optional<PlayerWarpView> find(PlayerRef who, String name) {
            return Optional.ofNullable(owned.get(name.toLowerCase(java.util.Locale.ROOT)));
        }
    }

    private static PlayerwarpsPlaceholders.PlayerWarpView playerWarpView(
            String name, String owner, String world, int x, int y, int z, long visits) {
        return new PlayerwarpsPlaceholders.PlayerWarpView(name, owner, world, x, y, z, visits);
    }

    /** A configurable {@link MessagingPlaceholders} fake: every read returns the value the test seeded. */
    private static final class FakeMessaging implements MessagingPlaceholders {

        private long unread;
        private long total;
        private Optional<String> replyTarget = Optional.empty();
        private boolean accepting = true;
        private boolean spying;
        private int ignoring;
        private final java.util.Set<UUID> ignored = new java.util.HashSet<>();

        FakeMessaging ignores(PlayerRef other) {
            ignored.add(other.uuid());
            this.ignoring = ignored.size();
            return this;
        }

        FakeMessaging unread(long value) {
            this.unread = value;
            return this;
        }

        FakeMessaging total(long value) {
            this.total = value;
            return this;
        }

        FakeMessaging replyTarget(String name) {
            this.replyTarget = Optional.of(name);
            return this;
        }

        FakeMessaging accepting(boolean value) {
            this.accepting = value;
            return this;
        }

        FakeMessaging spying(boolean value) {
            this.spying = value;
            return this;
        }

        FakeMessaging ignoring(int value) {
            this.ignoring = value;
            return this;
        }

        @Override
        public long unreadMail(PlayerRef who) {
            return unread;
        }

        @Override
        public long totalMail(PlayerRef who) {
            return total;
        }

        @Override
        public Optional<String> replyTarget(PlayerRef who) {
            return replyTarget;
        }

        @Override
        public boolean acceptingMessages(PlayerRef who) {
            return accepting;
        }

        @Override
        public boolean socialSpy(PlayerRef who) {
            return spying;
        }

        @Override
        public int ignoringCount(PlayerRef who) {
            return ignoring;
        }

        @Override
        public boolean ignores(PlayerRef owner, PlayerRef other) {
            return ignored.contains(other.uuid());
        }
    }

    /** A configurable {@link StaffPlaceholders} fake: every read returns the value the test seeded. */
    private static final class FakeStaff implements StaffPlaceholders {

        private final java.util.Set<PlayerRef> inMode = new java.util.HashSet<>();
        private int onlineCount;

        FakeStaff mode(PlayerRef who, boolean value) {
            if (value) {
                inMode.add(who);
            } else {
                inMode.remove(who);
            }
            return this;
        }

        FakeStaff onlineCount(int value) {
            this.onlineCount = value;
            return this;
        }

        @Override
        public boolean inStaffMode(PlayerRef who) {
            return inMode.contains(who);
        }

        @Override
        public int onlineStaffCount() {
            return onlineCount;
        }
    }

    /** A configurable {@link ScoreboardPlaceholders} fake: every read returns the value the test seeded. */
    private static final class FakeScoreboard implements ScoreboardPlaceholders {

        private final java.util.Set<PlayerRef> shown = new java.util.HashSet<>();
        private final java.util.Map<PlayerRef, String> boards = new java.util.HashMap<>();

        FakeScoreboard visible(PlayerRef who, boolean value) {
            if (value) {
                shown.add(who);
            } else {
                shown.remove(who);
            }
            return this;
        }

        FakeScoreboard board(PlayerRef who, String board) {
            boards.put(who, board);
            return this;
        }

        @Override
        public boolean visible(PlayerRef who) {
            return shown.contains(who);
        }

        @Override
        public Optional<String> board(PlayerRef who) {
            return Optional.ofNullable(boards.get(who));
        }
    }

    /** A configurable {@link DiscordlinkPlaceholders} fake: every read returns the value the test seeded. */
    private static final class FakeDiscordlink implements DiscordlinkPlaceholders {

        private final java.util.Map<PlayerRef, String> ids = new java.util.HashMap<>();

        FakeDiscordlink link(PlayerRef who, String discordId) {
            ids.put(who, discordId);
            return this;
        }

        @Override
        public boolean linked(PlayerRef who) {
            return ids.containsKey(who);
        }

        @Override
        public Optional<String> discordId(PlayerRef who) {
            return Optional.ofNullable(ids.get(who));
        }
    }

    /** A configurable {@link CommunicationPlaceholders} fake: every read returns the value the test seeded. */
    private static final class FakeCommunication implements CommunicationPlaceholders {

        private final java.util.Set<PlayerRef> receiving = new java.util.HashSet<>();
        private boolean chatEnabled = true;

        FakeCommunication chatEnabled(boolean value) {
            this.chatEnabled = value;
            return this;
        }

        FakeCommunication receivesBroadcasts(PlayerRef who, boolean value) {
            if (value) {
                receiving.add(who);
            } else {
                receiving.remove(who);
            }
            return this;
        }

        @Override
        public boolean chatEnabled() {
            return chatEnabled;
        }

        @Override
        public boolean receivesBroadcasts(PlayerRef who) {
            return receiving.contains(who);
        }
    }

    private static WorldsPlaceholders fakeWorlds(
            int managed, int loaded, Optional<String> defaultWorld, int defaultWorldPlayers) {
        return new WorldsPlaceholders() {
            @Override
            public int managedCount() {
                return managed;
            }

            @Override
            public int loadedCount() {
                return loaded;
            }

            @Override
            public Optional<String> defaultWorld() {
                return defaultWorld;
            }

            @Override
            public int defaultWorldPlayers() {
                return defaultWorldPlayers;
            }
        };
    }

    private static VaultsPlaceholders fakeVaults(int count, int max, int size) {
        return new VaultsPlaceholders() {
            @Override
            public int count(PlayerRef who) {
                return count;
            }

            @Override
            public int max(PlayerRef who) {
                return max;
            }

            @Override
            public int size(PlayerRef who) {
                return size;
            }
        };
    }

    /** A configurable {@link ServerMetricsPlaceholders} fake: every read returns the value the test seeded. */
    private static final class FakeServerMetrics implements ServerMetricsPlaceholders {

        private final java.util.Map<String, Integer> worldPlayers = new java.util.HashMap<>();
        private final java.util.Map<String, WorldSky> skies = new java.util.HashMap<>();
        private int online;
        private int maxPlayers;
        private String version = "1.21.11";
        private Duration uptime = Duration.ZERO;
        private double[] tps = {20.0, 20.0, 20.0};
        private final java.util.Map<String, Integer> worldEntities = new java.util.HashMap<>();
        private final java.util.Map<String, Integer> worldChunks = new java.util.HashMap<>();
        private long ramUsed;
        private long ramMax;
        private long ramFree;
        private String name = "uxm";
        private String motd = "A Minecraft Server";
        private int worlds;

        FakeServerMetrics online(int value) {
            this.online = value;
            return this;
        }

        FakeServerMetrics maxPlayers(int value) {
            this.maxPlayers = value;
            return this;
        }

        FakeServerMetrics version(String value) {
            this.version = value;
            return this;
        }

        FakeServerMetrics uptime(Duration value) {
            this.uptime = value;
            return this;
        }

        FakeServerMetrics tps(double m1, double m5, double m15) {
            this.tps = new double[] {m1, m5, m15};
            return this;
        }

        FakeServerMetrics ram(long used, long max, long free) {
            this.ramUsed = used;
            this.ramMax = max;
            this.ramFree = free;
            return this;
        }

        FakeServerMetrics worldPlayers(String world, int count) {
            this.worldPlayers.put(world, count);
            return this;
        }

        FakeServerMetrics named(String value, String message, int loaded) {
            this.name = value;
            this.motd = message;
            this.worlds = loaded;
            return this;
        }

        FakeServerMetrics worldCounts(String world, int entities, int chunks) {
            this.worldEntities.put(world, entities);
            this.worldChunks.put(world, chunks);
            return this;
        }

        FakeServerMetrics worldSky(String world, WorldSky sky) {
            this.skies.put(world, sky);
            return this;
        }

        @Override
        public int onlinePlayers() {
            return online;
        }

        @Override
        public int maxPlayers() {
            return maxPlayers;
        }

        @Override
        public String minecraftVersion() {
            return version;
        }

        @Override
        public Duration uptime() {
            return uptime;
        }

        @Override
        public double[] tps() {
            return tps.clone();
        }

        @Override
        public long ramUsedMb() {
            return ramUsed;
        }

        @Override
        public long ramMaxMb() {
            return ramMax;
        }

        @Override
        public long ramFreeMb() {
            return ramFree;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String motd() {
            return motd;
        }

        @Override
        public int worlds() {
            return worlds;
        }

        @Override
        public OptionalInt worldEntities(String world) {
            Integer count = worldEntities.get(world);
            return count == null ? OptionalInt.empty() : OptionalInt.of(count);
        }

        @Override
        public OptionalInt worldChunks(String world) {
            Integer count = worldChunks.get(world);
            return count == null ? OptionalInt.empty() : OptionalInt.of(count);
        }

        @Override
        public OptionalInt worldPlayers(String world) {
            Integer count = worldPlayers.get(world);
            return count == null ? OptionalInt.empty() : OptionalInt.of(count);
        }

        @Override
        public Optional<WorldSky> worldSky(String world) {
            return Optional.ofNullable(skies.get(world));
        }
    }

    private static HomesPlaceholders fakeHomes(int count, int limit) {
        return fakeHomes(count, limit, List.of());
    }

    private static HomesPlaceholders fakeHomes(int count, int limit, List<HomesPlaceholders.HomeView> homes) {
        return new HomesPlaceholders() {
            @Override
            public int count(PlayerRef who) {
                return count;
            }

            @Override
            public int limit(PlayerRef who) {
                return limit;
            }

            @Override
            public List<HomesPlaceholders.HomeView> list(PlayerRef who) {
                return homes;
            }
        };
    }
}

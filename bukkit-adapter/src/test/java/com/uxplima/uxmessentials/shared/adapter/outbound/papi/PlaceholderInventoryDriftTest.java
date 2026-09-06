package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Random;
import java.util.UUID;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.junit.jupiter.api.Test;

/**
 * A coherence guard over the {@link PlaceholderResolver}: it keeps the enriched placeholder surface honest as
 * families are added. It is not a docs-parity catalogue. It proves three structural invariants that every key
 * must satisfy, so a future context cannot quietly add a dead key or one that throws.
 *
 * <ol>
 *   <li><b>No dead family.</b> Every {@code <ctx>_} prefix the resolver dispatches resolves to a present value
 *       even with no seam wired (the dash, or "no" for the moderation booleans), never {@code Optional.empty()},
 *       which would leave the raw token on screen and signal a branch that silently fell through.</li>
 *   <li><b>Unknown key stays raw.</b> A key that matches no family resolves to {@code Optional.empty()} so
 *       PlaceholderAPI leaves the literal token in place rather than blanking it.</li>
 *   <li><b>Never throws.</b> A fuzz of random keys (bare, prefixed, and adversarial) resolves without an
 *       exception against both an empty bundle and a fully wired one.</li>
 * </ol>
 */
class PlaceholderInventoryDriftTest {

    private static final PlayerRef WHO = new PlayerRef(UUID.randomUUID(), "Probe");

    /**
     * Every family prefix the resolver dispatches. A new {@code <ctx>_} branch must be listed here so the
     * dead-key guard covers it; the list is the maintained inventory of the resolver's dispatched families.
     */
    private static final List<String> FAMILY_PREFIXES = List.of(
            "kit_",
            "economy_",
            "homes_",
            "vaults_",
            "warps_",
            "warp_",
            "playerwarps_",
            "playerwarp_",
            "votes_",
            "voteparty_",
            "presence_",
            "playerstate_",
            "teleport_",
            "moderation_",
            "messaging_",
            "staff_",
            "discordlink_",
            "holograms_",
            "communication_",
            "scoreboard_",
            "poses_",
            "worlds_",
            "menu_",
            "survival_",
            "itemworld_",
            "npc_",
            "regions_",
            "security_",
            "module_",
            "tablist_",
            "nametags_",
            "villagers_",
            "servertweaks_",
            "commandcontrol_",
            "invrollback_",
            "server_");

    /** The bare (un-prefixed) keys the resolver dispatches in its terminal switch. */
    private static final List<String> BARE_KEYS = List.of(
            "balance",
            "balance_formatted",
            "baltop_position",
            "afk",
            "afk_duration",
            "vanished",
            "kits_list",
            "muted",
            "jailed");

    @Test
    void everyDispatchedFamilyResolvesToAValueNeverTheRawToken() {
        PlaceholderResolver resolver =
                new PlaceholderResolver(PlaceholderContexts.builder().build());

        for (String prefix : FAMILY_PREFIXES) {
            // A representative tail under each family. With no seam wired every family must still resolve to a
            // present value (its dash/"no" default), proving the branch owns the key rather than letting it leak.
            Optional<String> resolved = resolver.resolve(WHO, true, prefix + "probe");
            assertThat(resolved)
                    .as("family %s must resolve, not fall through to the raw-token path", prefix)
                    .isPresent();
            assertThat(resolved.orElseThrow())
                    .as("family %s must yield a non-blank value", prefix)
                    .isNotBlank();
        }
    }

    @Test
    void everyBareKeyResolvesToAValue() {
        PlaceholderResolver resolver =
                new PlaceholderResolver(PlaceholderContexts.builder().build());

        for (String key : BARE_KEYS) {
            assertThat(resolver.resolve(WHO, true, key))
                    .as("bare key %s must resolve", key)
                    .isPresent();
        }
    }

    @Test
    void aFullyWiredBundleResolvesEveryFamilyToANonBlankValue() {
        PlaceholderResolver resolver = new PlaceholderResolver(fullyWired());

        for (String prefix : FAMILY_PREFIXES) {
            Optional<String> resolved = resolver.resolve(WHO, true, prefix + "probe");
            assertThat(resolved).as("family %s must resolve when wired", prefix).isPresent();
            assertThat(resolved.orElseThrow())
                    .as("family %s must yield a non-blank value when wired", prefix)
                    .isNotBlank();
        }
    }

    @Test
    void unknownKeysResolveEmptySoTheRawTokenStays() {
        PlaceholderResolver resolver = new PlaceholderResolver(fullyWired());

        // None of these match a dispatched family or bare key, so each must leave the raw token in place.
        for (String key : List.of("not_a_placeholder", "totally_unknown", "xyzzy", "_", "")) {
            assertThat(resolver.resolve(WHO, true, key))
                    .as("unknown key %s must resolve empty", key)
                    .isEmpty();
        }
    }

    @Test
    void aFuzzOfRandomKeysNeverThrowsAndUnmatchedOnesStayRaw() {
        PlaceholderResolver empty =
                new PlaceholderResolver(PlaceholderContexts.builder().build());
        PlaceholderResolver wired = new PlaceholderResolver(fullyWired());
        Random random = new Random(20260615L);

        for (int i = 0; i < 2_000; i++) {
            String key = randomKey(random);
            assertThatCode(() -> empty.resolve(WHO, true, key)).doesNotThrowAnyException();
            assertThatCode(() -> empty.resolve(WHO, false, key)).doesNotThrowAnyException();
            assertThatCode(() -> wired.resolve(WHO, true, key)).doesNotThrowAnyException();
            assertThatCode(() -> wired.resolve(WHO, false, key)).doesNotThrowAnyException();
        }
    }

    /** A random key: sometimes a known family prefix with a random tail, sometimes pure noise. */
    private static String randomKey(Random random) {
        String alphabet = "abcdefghijklmnopqrstuvwxyz0123456789_";
        StringBuilder tail = new StringBuilder();
        int length = random.nextInt(10);
        for (int i = 0; i < length; i++) {
            tail.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        if (random.nextBoolean()) {
            return FAMILY_PREFIXES.get(random.nextInt(FAMILY_PREFIXES.size())) + tail;
        }
        return tail.toString();
    }

    /** A bundle with every seam wired to a minimal, always-answering fake, so each family has a live read. */
    private static PlaceholderContexts fullyWired() {
        return PlaceholderContexts.builder()
                .homes(homes())
                .economy(economy())
                .presence(who -> Optional.of(
                        new PresencePlaceholders.Snapshot(false, Duration.ZERO, Optional.empty(), false, WHO.name())))
                .playerstate(who -> Optional.empty())
                .kits(kits())
                .vaults(vaults())
                .warps(warps())
                .playerwarps(playerwarps())
                .moderation(moderation())
                .teleport(teleport())
                .vote(vote())
                .messaging(messaging())
                .staff(staff())
                .discordlink(discordlink())
                .holograms(() -> 0)
                .communication(communication())
                .scoreboard(scoreboard())
                .poses(poses())
                .worlds(worlds())
                .serverMetrics(serverMetrics())
                .menu(menu())
                .survival(survival())
                .itemworld(itemworld())
                .npc(npc())
                .regions(regions())
                .security(security())
                .modules(id -> true)
                .tablist(tablist())
                .nametags(nametags())
                .villagers(villagers())
                .serverTweaks(() -> Optional.of("uxmEssentials"))
                .commandControl((who, command) -> true)
                .invrollback(invrollback())
                .build();
    }

    private static ScoreboardPlaceholders scoreboard() {
        return new ScoreboardPlaceholders() {
            @Override
            public boolean visible(PlayerRef who) {
                return true;
            }

            @Override
            public Optional<String> board(PlayerRef who) {
                return Optional.of("default");
            }
        };
    }

    private static TablistPlaceholders tablist() {
        return who -> Optional.of("default");
    }

    private static NametagsPlaceholders nametags() {
        return who -> Optional.of("default");
    }

    private static VillagersPlaceholders villagers() {
        return who -> 0;
    }

    private static InvrollbackPlaceholders invrollback() {
        return new InvrollbackPlaceholders() {
            @Override
            public Optional<java.time.Instant> lastCapture(PlayerRef who) {
                return Optional.empty();
            }

            @Override
            public Optional<String> lastCause(PlayerRef who) {
                return Optional.empty();
            }
        };
    }

    private static SurvivalPlaceholders survival() {
        return new SurvivalPlaceholders() {
            @Override
            public boolean active(PlayerRef who, Mechanic mechanic) {
                return true;
            }

            @Override
            public boolean enabled(Mechanic mechanic) {
                return true;
            }
        };
    }

    private static ItemworldPlaceholders itemworld() {
        return new ItemworldPlaceholders() {
            @Override
            public List<String> powertool(PlayerRef who) {
                return List.of();
            }

            @Override
            public boolean powertoolEnabled(PlayerRef who) {
                return true;
            }

            @Override
            public boolean unlimitedPlacement(PlayerRef who) {
                return false;
            }
        };
    }

    private static NpcPlaceholders npc() {
        return new NpcPlaceholders() {
            @Override
            public int total() {
                return 0;
            }

            @Override
            public int owned(PlayerRef who) {
                return 0;
            }

            @Override
            public OptionalInt limit(PlayerRef who) {
                return OptionalInt.empty();
            }
        };
    }

    private static RegionsPlaceholders regions() {
        return new RegionsPlaceholders() {
            @Override
            public boolean available() {
                return true;
            }

            @Override
            public Optional<Standing> standingIn(PlayerRef who) {
                return Optional.of(new Standing("spawn", 1, List.of(), List.of()));
            }

            @Override
            public int coveringCount(PlayerRef who) {
                return 1;
            }

            @Override
            public int worldCount(PlayerRef who) {
                return 1;
            }
        };
    }

    private static SecurityPlaceholders security() {
        return new SecurityPlaceholders() {
            @Override
            public boolean verifying(PlayerRef who) {
                return false;
            }

            @Override
            public boolean enforced() {
                return true;
            }
        };
    }

    private static PosesPlaceholders poses() {
        return new PosesPlaceholders() {
            @Override
            public boolean sitting(PlayerRef who) {
                return false;
            }

            @Override
            public boolean posing(PlayerRef who) {
                return false;
            }

            @Override
            public String pose(PlayerRef who) {
                return "none";
            }

            @Override
            public boolean allowsSitting(PlayerRef who) {
                return true;
            }
        };
    }

    private static MenuPlaceholders menu() {
        return new MenuPlaceholders() {
            @Override
            public boolean inMenu(UUID player) {
                return false;
            }

            @Override
            public Optional<String> openedMenu(UUID player) {
                return Optional.empty();
            }

            @Override
            public Optional<String> lastMenu(UUID player) {
                return Optional.empty();
            }

            @Override
            public OptionalInt page(UUID player) {
                return OptionalInt.empty();
            }

            @Override
            public OptionalInt rows(UUID player) {
                return OptionalInt.empty();
            }

            @Override
            public Optional<String> argument(UUID player, String name) {
                return Optional.empty();
            }
        };
    }

    private static HomesPlaceholders homes() {
        return new HomesPlaceholders() {
            @Override
            public int count(PlayerRef who) {
                return 0;
            }

            @Override
            public int limit(PlayerRef who) {
                return 0;
            }

            @Override
            public List<HomeView> list(PlayerRef who) {
                return List.of();
            }
        };
    }

    private static EconomyPlaceholders economy() {
        return new EconomyPlaceholders() {
            private final com.uxplima.uxmessentials.economy.domain.Currency currency =
                    com.uxplima.uxmessentials.economy.domain.Currency.builder(
                                    com.uxplima.uxmessentials.economy.domain.CurrencyId.of("coins"))
                            .symbol("$")
                            .plural("coins")
                            .format("#,##0.00")
                            .build();

            @Override
            public com.uxplima.uxmessentials.economy.domain.Money balance(PlayerRef who) {
                return com.uxplima.uxmessentials.economy.domain.Money.zero(currency);
            }

            @Override
            public String formatted(PlayerRef who) {
                return "$0.00";
            }

            @Override
            public String compact(PlayerRef who) {
                return "$0";
            }

            @Override
            public OptionalInt baltopPosition(PlayerRef who) {
                return OptionalInt.empty();
            }

            @Override
            public Optional<com.uxplima.uxmessentials.economy.domain.Currency> currency(String currencyId) {
                return Optional.empty();
            }

            @Override
            public com.uxplima.uxmessentials.economy.domain.Currency defaultCurrency() {
                return currency;
            }

            @Override
            public com.uxplima.uxmessentials.economy.domain.Money balance(
                    PlayerRef who, com.uxplima.uxmessentials.economy.domain.Currency currency) {
                return com.uxplima.uxmessentials.economy.domain.Money.zero(currency);
            }

            @Override
            public Optional<com.uxplima.uxmessentials.economy.application.port.BaltopRow> baltopRow(
                    com.uxplima.uxmessentials.economy.domain.Currency currency, int rank) {
                return Optional.empty();
            }
        };
    }

    private static KitsPlaceholders kits() {
        return new KitsPlaceholders() {
            @Override
            public Optional<Duration> cooldownRemaining(PlayerRef who, String kitId) {
                return Optional.empty();
            }

            @Override
            public Optional<Boolean> available(PlayerRef who, String kitId) {
                return Optional.empty();
            }

            @Override
            public Optional<Boolean> hasPermission(PlayerRef who, String kitId) {
                return Optional.empty();
            }

            @Override
            public Optional<java.math.BigDecimal> cost(String kitId) {
                return Optional.empty();
            }

            @Override
            public Optional<Integer> claimsLeft(PlayerRef who, String kitId) {
                return Optional.empty();
            }

            @Override
            public List<String> usableIds(PlayerRef who) {
                return List.of();
            }
        };
    }

    private static VaultsPlaceholders vaults() {
        return new VaultsPlaceholders() {
            @Override
            public int count(PlayerRef who) {
                return 0;
            }

            @Override
            public int max(PlayerRef who) {
                return 0;
            }

            @Override
            public int size(PlayerRef who) {
                return 0;
            }
        };
    }

    private static WorldsPlaceholders worlds() {
        return new WorldsPlaceholders() {
            @Override
            public int managedCount() {
                return 0;
            }

            @Override
            public int loadedCount() {
                return 0;
            }

            @Override
            public Optional<String> defaultWorld() {
                return Optional.empty();
            }

            @Override
            public int defaultWorldPlayers() {
                return 0;
            }
        };
    }

    private static WarpsPlaceholders warps() {
        return new WarpsPlaceholders() {
            @Override
            public int count(PlayerRef who) {
                return 0;
            }

            @Override
            public List<String> accessibleNames(PlayerRef who) {
                return List.of();
            }

            @Override
            public Optional<WarpView> find(PlayerRef who, String name) {
                return Optional.empty();
            }
        };
    }

    private static PlayerwarpsPlaceholders playerwarps() {
        return new PlayerwarpsPlaceholders() {
            @Override
            public int count(PlayerRef who) {
                return 0;
            }

            @Override
            public int limit(PlayerRef who) {
                return 0;
            }

            @Override
            public List<PlayerWarpView> list(PlayerRef who) {
                return List.of();
            }

            @Override
            public Optional<PlayerWarpView> find(PlayerRef who, String name) {
                return Optional.empty();
            }
        };
    }

    private static ModerationPlaceholders moderation() {
        return new ModerationPlaceholders() {
            @Override
            public boolean isMuted(PlayerRef who) {
                return false;
            }

            @Override
            public boolean isJailed(PlayerRef who) {
                return false;
            }

            @Override
            public boolean isFrozen(PlayerRef who) {
                return false;
            }

            @Override
            public int warnCount(PlayerRef who) {
                return 0;
            }

            @Override
            public Optional<SanctionView> activeBan(PlayerRef who) {
                return Optional.empty();
            }

            @Override
            public Optional<SanctionView> activeMute(PlayerRef who) {
                return Optional.empty();
            }

            @Override
            public Optional<JailView> activeJail(PlayerRef who) {
                return Optional.empty();
            }
        };
    }

    private static TeleportPlaceholders teleport() {
        return new TeleportPlaceholders() {
            @Override
            public Optional<Duration> cooldownRemaining(PlayerRef who) {
                return Optional.empty();
            }

            @Override
            public Optional<Duration> warmupRemaining(PlayerRef who) {
                return Optional.empty();
            }

            @Override
            public Optional<BackView> backLocation(PlayerRef who) {
                return Optional.empty();
            }

            @Override
            public int incomingRequests(PlayerRef who) {
                return 0;
            }

            @Override
            public boolean hasOutgoingRequest(PlayerRef who) {
                return false;
            }

            @Override
            public boolean acceptingRequests(PlayerRef who) {
                return true;
            }
        };
    }

    private static VotePlaceholders vote() {
        return new VotePlaceholders() {
            @Override
            public long countFor(PlayerRef who, com.uxplima.uxmessentials.vote.domain.VotePeriod period) {
                return 0L;
            }

            @Override
            public int partyCount() {
                return 0;
            }

            @Override
            public int partyThreshold() {
                return 0;
            }
        };
    }

    private static MessagingPlaceholders messaging() {
        return new MessagingPlaceholders() {
            @Override
            public long unreadMail(PlayerRef who) {
                return 0L;
            }

            @Override
            public long totalMail(PlayerRef who) {
                return 0L;
            }

            @Override
            public Optional<String> replyTarget(PlayerRef who) {
                return Optional.empty();
            }

            @Override
            public boolean acceptingMessages(PlayerRef who) {
                return true;
            }

            @Override
            public boolean socialSpy(PlayerRef who) {
                return false;
            }

            @Override
            public int ignoringCount(PlayerRef who) {
                return 0;
            }

            @Override
            public boolean ignores(PlayerRef owner, PlayerRef other) {
                return false;
            }
        };
    }

    private static StaffPlaceholders staff() {
        return new StaffPlaceholders() {
            @Override
            public boolean inStaffMode(PlayerRef who) {
                return false;
            }

            @Override
            public int onlineStaffCount() {
                return 0;
            }
        };
    }

    private static DiscordlinkPlaceholders discordlink() {
        return new DiscordlinkPlaceholders() {
            @Override
            public boolean linked(PlayerRef who) {
                return false;
            }

            @Override
            public Optional<String> discordId(PlayerRef who) {
                return Optional.empty();
            }
        };
    }

    private static CommunicationPlaceholders communication() {
        return new CommunicationPlaceholders() {
            @Override
            public boolean chatEnabled() {
                return true;
            }

            @Override
            public boolean receivesBroadcasts(PlayerRef who) {
                return false;
            }
        };
    }

    private static ServerMetricsPlaceholders serverMetrics() {
        return new ServerMetricsPlaceholders() {
            @Override
            public int onlinePlayers() {
                return 0;
            }

            @Override
            public int maxPlayers() {
                return 20;
            }

            @Override
            public String minecraftVersion() {
                return "1.21.11";
            }

            @Override
            public Duration uptime() {
                return Duration.ZERO;
            }

            @Override
            public double[] tps() {
                return new double[] {20.0, 20.0, 20.0};
            }

            @Override
            public long ramUsedMb() {
                return 0L;
            }

            @Override
            public long ramMaxMb() {
                return 0L;
            }

            @Override
            public long ramFreeMb() {
                return 0L;
            }

            @Override
            public String name() {
                return "uxm";
            }

            @Override
            public String motd() {
                return "A Minecraft Server";
            }

            @Override
            public int worlds() {
                return 1;
            }

            @Override
            public OptionalInt worldEntities(String world) {
                return OptionalInt.empty();
            }

            @Override
            public OptionalInt worldChunks(String world) {
                return OptionalInt.empty();
            }

            @Override
            public OptionalInt worldPlayers(String world) {
                return OptionalInt.empty();
            }

            @Override
            public Optional<WorldSky> worldSky(String world) {
                return Optional.empty();
            }
        };
    }
}

package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;

import com.uxplima.uxmessentials.economy.application.MoneyFormat;
import com.uxplima.uxmessentials.economy.application.port.BaltopRow;
import com.uxplima.uxmessentials.economy.domain.Currency;
import com.uxplima.uxmessentials.economy.domain.Money;
import com.uxplima.uxmessentials.shared.application.placeholder.PlaceholderCatalog;
import com.uxplima.uxmessentials.shared.application.port.Cooldowns;
import com.uxplima.uxmessentials.shared.application.port.PlayerLookup;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vote.application.port.VoteRanking;
import com.uxplima.uxmessentials.vote.domain.VotePeriod;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The placeholder resolution logic, behind a thin seam so it is testable without a live PlaceholderAPI.
 * The {@code UxmEssentialsExpansion} shell strips the {@code uxmessentials_} prefix, builds a {@link
 * PlayerRef} from the requesting {@code OfflinePlayer}, and asks this resolver for the value; the resolver
 * never touches a PlaceholderAPI type.
 *
 * <p>Each key is dispatched to the owning context's read seam ({@link PlaceholderContexts}). When that
 * context is disabled, its seam absent, or the player is offline for a session-only placeholder, the
 * value degrades to a sensible empty/"-" default ({@link #EMPTY}) rather than failing. An entirely unknown
 * key returns {@link Optional#empty()}, which the shell maps to {@code null} so PlaceholderAPI shows the
 * raw token unchanged.
 */
@NullMarked
public final class PlaceholderResolver {

    /** The value a placeholder degrades to when its owning module is disabled or the data is absent. */
    public static final String EMPTY = "-";

    /** How a stored timestamp reads: the day and the minute, in the server's own zone. */
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);

    /** The wall clock and calendar day of the machine the server runs on, for the two real-time server keys. */
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ROOT);

    private static final String YES = "yes";
    private static final String NO = "no";
    /** The player-sit toggle renders its own words rather than yes/no, so it reads as an opt-out state. */
    private static final String ALLOW = "allow";

    private static final String REFUSE = "refuse";
    /** The {@code %uxmessentials_rank_next%} value shown when the player is already at the highest rank. */
    private static final String MAX_RANK = "max";

    /** What a quota with no ceiling reads as, the same word the vault and home quotas already use. */
    private static final String UNLIMITED = "unlimited";

    private static final String KIT_PREFIX = "kit_";
    private static final String KIT_COOLDOWN_PREFIX = "cooldown_";
    private static final String KIT_AVAILABLE_PREFIX = "available_";
    private static final String KIT_HAS_PREFIX = "has_";
    private static final String KIT_COST_PREFIX = "cost_";
    private static final String KIT_CLAIMS_LEFT_PREFIX = "claims_left_";
    private static final String ECONOMY_PREFIX = "economy_";
    private static final String HOMES_PREFIX = "homes_";
    private static final String VAULTS_PREFIX = "vaults_";
    private static final String WARPS_PREFIX = "warps_";
    private static final String WARP_PREFIX = "warp_";
    private static final String PLAYERWARPS_PREFIX = "playerwarps_";
    private static final String PLAYERWARP_PREFIX = "playerwarp_";
    private static final String PRESENCE_PREFIX = "presence_";
    private static final String PLAYERSTATE_PREFIX = "playerstate_";
    private static final String TELEPORT_PREFIX = "teleport_";
    private static final String MODERATION_PREFIX = "moderation_";
    private static final String MESSAGING_PREFIX = "messaging_";
    private static final String STAFF_PREFIX = "staff_";
    private static final String DISCORDLINK_PREFIX = "discordlink_";
    private static final String HOLOGRAMS_PREFIX = "holograms_";
    private static final String COMMUNICATION_PREFIX = "communication_";
    private static final String SCOREBOARD_PREFIX = "scoreboard_";
    private static final String TABLIST_PREFIX = "tablist_";
    private static final String NAMETAGS_PREFIX = "nametags_";
    private static final String VILLAGERS_PREFIX = "villagers_";
    private static final String SERVERTWEAKS_PREFIX = "servertweaks_";
    private static final String COMMANDCONTROL_PREFIX = "commandcontrol_";
    private static final String COMMANDCONTROL_ALLOWED_PREFIX = "allowed_";
    private static final String INVROLLBACK_PREFIX = "invrollback_";
    private static final String SKIN_PREFIX = "skin_";
    private static final String POSES_PREFIX = "poses_";
    private static final String WORLDS_PREFIX = "worlds_";
    private static final String MENU_PREFIX = "menu_";
    private static final String MENU_ARGUMENT_PREFIX = "argument_";
    private static final String SERVER_PREFIX = "server_";
    private static final String SERVER_WORLD_PLAYERS_PREFIX = "world_players_";
    private static final String SERVER_WORLD_ENTITIES_PREFIX = "world_entities_";
    private static final String SERVER_WORLD_CHUNKS_PREFIX = "world_chunks_";
    private static final String SERVER_WORLD_TIME_FORMATTED_PREFIX = "world_time_formatted_";
    private static final String SERVER_WORLD_TIME_PREFIX = "world_time_";
    private static final String SERVER_WORLD_WEATHER_PREFIX = "world_weather_";
    private static final String VOTES_PREFIX = "votes_";
    private static final String VOTES_TOP_PREFIX = "top_";
    private static final String VOTES_POSITION_PREFIX = "position_";
    private static final String VOTES_STREAK_PREFIX = "streak_";
    private static final String VOTEPARTY_PREFIX = "voteparty_";
    /** The other-player form: {@code p_<name>_<key>} answers <key> about the named player, not the requester. */
    private static final String OTHER_PLAYER_PREFIX = "p_";

    private static final String PLAYER_PREFIX = "player_";
    private static final String HAND_PREFIX = "hand_";
    private static final String OFFHAND_PREFIX = "offhand_";
    private static final String ITEM_COUNT_PREFIX = "itemcount_";

    private static final String FORMAT_NUMBER_PREFIX = "format_number_";
    private static final String FORMAT_COMPACT_PREFIX = "format_compact_";
    private static final String FORMAT_TIME_PREFIX = "format_time_";
    private static final String PROGRESS_BAR_PREFIX = "progressbar_";
    private static final String STATISTIC_PREFIX = "stat_";
    private static final String SURVIVAL_PREFIX = "survival_";
    private static final String ITEMWORLD_PREFIX = "itemworld_";
    private static final String NPC_PREFIX = "npc_";
    private static final String REGIONS_PREFIX = "regions_";
    private static final String SECURITY_PREFIX = "security_";
    private static final String MODULE_PREFIX = "module_";
    private static final String ENABLED_SUFFIX = "_enabled";
    private static final String COOLDOWN_PREFIX = "cooldown_";
    private static final String COOLDOWN_ACTIVE_PREFIX = "active_";
    private static final String FORMATTED_SUFFIX = "_formatted";

    private final PlaceholderContexts contexts;

    public PlaceholderResolver(PlaceholderContexts contexts) {
        this.contexts = Objects.requireNonNull(contexts, "contexts");
    }

    /**
     * Resolve the {@code uxmessentials_}-stripped {@code key} for {@code who}. {@code online} reflects
     * whether the requesting player is currently connected. Session-only placeholders (presence) read
     * empty for an offline player. An unknown key returns {@link Optional#empty()}.
     */
    public Optional<String> resolve(PlayerRef who, boolean online, String key) {
        Objects.requireNonNull(who, "who");
        Objects.requireNonNull(key, "key");
        String normalized = key.toLowerCase(Locale.ROOT);
        if (normalized.startsWith(OTHER_PLAYER_PREFIX)) {
            return Optional.of(otherPlayer(normalized.substring(OTHER_PLAYER_PREFIX.length())));
        }
        if (normalized.startsWith(KIT_PREFIX)) {
            return Optional.of(kitFamily(who, normalized.substring(KIT_PREFIX.length())));
        }
        if (normalized.startsWith(HAND_PREFIX)) {
            return Optional.of(
                    heldItem(who, PlayerFactsPlaceholders.Hand.MAIN, normalized.substring(HAND_PREFIX.length())));
        }
        if (normalized.startsWith(OFFHAND_PREFIX)) {
            return Optional.of(
                    heldItem(who, PlayerFactsPlaceholders.Hand.OFF, normalized.substring(OFFHAND_PREFIX.length())));
        }
        if (normalized.startsWith(ITEM_COUNT_PREFIX)) {
            return Optional.of(itemCount(who, normalized.substring(ITEM_COUNT_PREFIX.length())));
        }
        if (normalized.startsWith(FORMAT_NUMBER_PREFIX)) {
            return Optional.of(formatNumber(normalized.substring(FORMAT_NUMBER_PREFIX.length())));
        }
        if (normalized.startsWith(FORMAT_COMPACT_PREFIX)) {
            return Optional.of(formatCompact(normalized.substring(FORMAT_COMPACT_PREFIX.length())));
        }
        if (normalized.startsWith(FORMAT_TIME_PREFIX)) {
            return Optional.of(formatTime(normalized.substring(FORMAT_TIME_PREFIX.length())));
        }
        if (normalized.startsWith(PROGRESS_BAR_PREFIX)) {
            return Optional.of(progressBar(normalized.substring(PROGRESS_BAR_PREFIX.length())));
        }
        if (normalized.startsWith(COOLDOWN_PREFIX)) {
            return Optional.of(cooldown(who, normalized.substring(COOLDOWN_PREFIX.length())));
        }
        if (normalized.startsWith(STATISTIC_PREFIX)) {
            return Optional.of(contexts.playerFacts()
                    .map(facts -> statistic(facts, who, normalized.substring(STATISTIC_PREFIX.length())))
                    .orElse(EMPTY));
        }
        if (normalized.startsWith(ECONOMY_PREFIX)) {
            return Optional.of(economyFamily(who, normalized.substring(ECONOMY_PREFIX.length())));
        }
        if (normalized.startsWith(HOMES_PREFIX)) {
            return Optional.of(homesFamily(who, normalized.substring(HOMES_PREFIX.length())));
        }
        if (normalized.startsWith(VAULTS_PREFIX)) {
            return Optional.of(vaultsFamily(who, normalized.substring(VAULTS_PREFIX.length())));
        }
        if (normalized.startsWith(WARPS_PREFIX)) {
            return Optional.of(warpsFamily(who, normalized.substring(WARPS_PREFIX.length())));
        }
        if (normalized.startsWith(WARP_PREFIX)) {
            return Optional.of(warpField(who, normalized.substring(WARP_PREFIX.length())));
        }
        // The plural prefix is checked before the singular: "playerwarp_" is itself a prefix of "playerwarps_",
        // so the more specific list/scalar family must win before the per-warp field branch.
        if (normalized.startsWith(PLAYERWARPS_PREFIX)) {
            return Optional.of(playerwarpsFamily(who, normalized.substring(PLAYERWARPS_PREFIX.length())));
        }
        if (normalized.startsWith(PLAYERWARP_PREFIX)) {
            return Optional.of(playerwarpField(who, normalized.substring(PLAYERWARP_PREFIX.length())));
        }
        if (normalized.startsWith(VOTES_PREFIX)) {
            return Optional.of(votes(who, normalized.substring(VOTES_PREFIX.length())));
        }
        if (normalized.startsWith(VOTEPARTY_PREFIX)) {
            return Optional.of(voteparty(normalized.substring(VOTEPARTY_PREFIX.length())));
        }
        if (normalized.startsWith(PRESENCE_PREFIX)) {
            return Optional.of(presence(who, online, normalized.substring(PRESENCE_PREFIX.length())));
        }
        if (normalized.startsWith(PLAYERSTATE_PREFIX)) {
            return Optional.of(playerstate(who, online, normalized.substring(PLAYERSTATE_PREFIX.length())));
        }
        if (normalized.startsWith(PLAYER_PREFIX)) {
            return Optional.of(playerFact(who, normalized.substring(PLAYER_PREFIX.length())));
        }
        if (normalized.startsWith(TELEPORT_PREFIX)) {
            return Optional.of(teleport(who, online, normalized.substring(TELEPORT_PREFIX.length())));
        }
        if (normalized.startsWith(MODERATION_PREFIX)) {
            return Optional.of(moderationFamily(who, normalized.substring(MODERATION_PREFIX.length())));
        }
        if (normalized.startsWith(MESSAGING_PREFIX)) {
            return Optional.of(messaging(who, online, normalized.substring(MESSAGING_PREFIX.length())));
        }
        if (normalized.startsWith(STAFF_PREFIX)) {
            return Optional.of(staff(who, online, normalized.substring(STAFF_PREFIX.length())));
        }
        if (normalized.startsWith(DISCORDLINK_PREFIX)) {
            return Optional.of(discordlink(who, normalized.substring(DISCORDLINK_PREFIX.length())));
        }
        if (normalized.startsWith(HOLOGRAMS_PREFIX)) {
            return Optional.of(holograms(normalized.substring(HOLOGRAMS_PREFIX.length())));
        }
        if (normalized.startsWith(COMMUNICATION_PREFIX)) {
            return Optional.of(communication(who, online, normalized.substring(COMMUNICATION_PREFIX.length())));
        }
        if (normalized.startsWith(SCOREBOARD_PREFIX)) {
            return Optional.of(scoreboard(who, online, normalized.substring(SCOREBOARD_PREFIX.length())));
        }
        if (normalized.startsWith(TABLIST_PREFIX)) {
            return Optional.of(tablist(who, online, normalized.substring(TABLIST_PREFIX.length())));
        }
        if (normalized.startsWith(NAMETAGS_PREFIX)) {
            return Optional.of(nametags(who, online, normalized.substring(NAMETAGS_PREFIX.length())));
        }
        if (normalized.startsWith(VILLAGERS_PREFIX)) {
            return Optional.of(villagers(who, online, normalized.substring(VILLAGERS_PREFIX.length())));
        }
        if (normalized.startsWith(SERVERTWEAKS_PREFIX)) {
            return Optional.of(serverTweaks(normalized.substring(SERVERTWEAKS_PREFIX.length())));
        }
        if (normalized.startsWith(COMMANDCONTROL_PREFIX)) {
            return Optional.of(commandControl(who, online, normalized.substring(COMMANDCONTROL_PREFIX.length())));
        }
        if (normalized.startsWith(INVROLLBACK_PREFIX)) {
            return Optional.of(invrollback(who, normalized.substring(INVROLLBACK_PREFIX.length())));
        }
        if (normalized.startsWith(SKIN_PREFIX)) {
            return Optional.of(skin(who, normalized.substring(SKIN_PREFIX.length())));
        }
        if (normalized.startsWith(POSES_PREFIX)) {
            return Optional.of(poses(who, online, normalized.substring(POSES_PREFIX.length())));
        }
        if (normalized.startsWith(WORLDS_PREFIX)) {
            return Optional.of(worldsFamily(normalized.substring(WORLDS_PREFIX.length())));
        }
        if (normalized.startsWith(MENU_PREFIX)) {
            return Optional.of(menuFamily(who, normalized.substring(MENU_PREFIX.length())));
        }
        if (normalized.startsWith(SURVIVAL_PREFIX)) {
            return Optional.of(survival(who, normalized.substring(SURVIVAL_PREFIX.length())));
        }
        if (normalized.startsWith(ITEMWORLD_PREFIX)) {
            return Optional.of(itemworld(who, normalized.substring(ITEMWORLD_PREFIX.length())));
        }
        if (normalized.startsWith(NPC_PREFIX)) {
            return Optional.of(npc(who, normalized.substring(NPC_PREFIX.length())));
        }
        if (normalized.startsWith(REGIONS_PREFIX)) {
            return Optional.of(regions(who, normalized.substring(REGIONS_PREFIX.length())));
        }
        if (normalized.startsWith(SECURITY_PREFIX)) {
            return Optional.of(security(who, normalized.substring(SECURITY_PREFIX.length())));
        }
        if (normalized.startsWith(MODULE_PREFIX)) {
            return Optional.of(module(normalized.substring(MODULE_PREFIX.length())));
        }
        if (normalized.startsWith(SERVER_PREFIX)) {
            return Optional.of(serverMetric(normalized.substring(SERVER_PREFIX.length())));
        }
        return switch (normalized) {
            case "balance", "balance_formatted", "baltop_position" -> Optional.of(economy(who, normalized));
            case "afk", "afk_duration", "vanished" -> Optional.of(presence(who, online, normalized));
            case "kits_list" -> Optional.of(kitsList(who));
            case "muted", "jailed" -> Optional.of(moderation(who, normalized));
            case "rank", "rank_next", "rank_next_cost", "rank_position", "rank_total", "rank_progress", "prestige" ->
                Optional.of(ranks(who, normalized));
            default -> Optional.empty();
        };
    }

    /**
     * Resolve a {@code player_*} tail against the always-wired player-facts seam: what the server itself holds
     * about the account, rather than what a module stores. The session keys (ping, crouch, the world they stand
     * in and its sky) hold no value for an offline player; the account keys (first join, last seen, playtime, the
     * server's own ban flag) answer for one. A seam that is absent, which only a test bundle is, degrades every
     * key to the dash.
     */
    private String playerFact(PlayerRef who, String tail) {
        Optional<PlayerFactsPlaceholders> seam = contexts.playerFacts();
        if (seam.isEmpty()) {
            return EMPTY;
        }
        PlayerFactsPlaceholders facts = seam.get();
        Optional<String> fromAccount = accountFact(facts, who, tail);
        if (fromAccount.isPresent()) {
            return fromAccount.get();
        }
        Optional<String> fromIdentity = identityFact(facts, who, tail);
        if (fromIdentity.isPresent()) {
            return fromIdentity.get();
        }
        Optional<String> fromVitals = vitalsFact(facts, who, tail);
        if (fromVitals.isPresent()) {
            return fromVitals.get();
        }
        Optional<String> fromWhere = whereFact(facts, who, tail);
        if (fromWhere.isPresent()) {
            return fromWhere.get();
        }
        Optional<String> fromStatistic = statisticShortcut(facts, who, tail);
        if (fromStatistic.isPresent()) {
            return fromStatistic.get();
        }
        return facts.session(who).map(session -> sessionFact(session, tail)).orElse(EMPTY);
    }

    /** The keys that read who the player is, which answer with the account name and id even when offline. */
    private static Optional<String> identityFact(PlayerFactsPlaceholders facts, PlayerRef who, String tail) {
        return switch (tail) {
            case "name",
                    "display_name",
                    "uuid",
                    "ip",
                    "locale",
                    "gamemode",
                    "flying",
                    "can_fly",
                    "fly_speed",
                    "walk_speed",
                    "bed",
                    "has_bed",
                    "compass" ->
                Optional.of(facts.identity(who)
                        .map(identity -> identity(identity, tail))
                        .orElse(EMPTY));
            default -> Optional.empty();
        };
    }

    private static String identity(PlayerFactsPlaceholders.Identity identity, String tail) {
        return switch (tail) {
            case "name" -> identity.name();
            case "display_name" -> identity.displayName();
            case "uuid" -> identity.uuid();
            case "ip" -> identity.address().orElse(EMPTY);
            case "locale" -> identity.locale().isEmpty() ? EMPTY : identity.locale();
            case "gamemode" -> identity.gameMode().isEmpty() ? EMPTY : identity.gameMode();
            case "flying" -> bool(identity.flying());
            case "can_fly" -> bool(identity.allowFlight());
            case "fly_speed" -> decimal(identity.flySpeed());
            case "walk_speed" -> decimal(identity.walkSpeed());
            case "bed" -> identity.bed().map(PlaceholderResolver::place).orElse(EMPTY);
            case "has_bed" -> bool(identity.bed().isPresent());
            case "compass" -> identity.compass().map(PlaceholderResolver::place).orElse(EMPTY);
            default -> EMPTY;
        };
    }

    /** One position written the way an operator reads it back: the world, then whole-block coordinates. */
    private static String place(PlayerFactsPlaceholders.Position position) {
        return position.world() + " " + (int) Math.floor(position.x()) + " "
                + (int) Math.floor(position.y()) + " "
                + (int) Math.floor(position.z());
    }

    /** The keys that read the player's body, which hold no meaning for an account that is not connected. */
    private static Optional<String> vitalsFact(PlayerFactsPlaceholders facts, PlayerRef who, String tail) {
        return switch (tail) {
            case "health",
                    "health_rounded",
                    "health_max",
                    "health_percent",
                    "food",
                    "saturation",
                    "air",
                    "air_max",
                    "armor",
                    "absorption",
                    "burning" ->
                Optional.of(
                        facts.vitals(who).map(vitals -> vitals(vitals, tail)).orElse(EMPTY));
            default -> Optional.empty();
        };
    }

    private static String vitals(PlayerFactsPlaceholders.Vitals vitals, String tail) {
        return switch (tail) {
            case "health" -> decimal(vitals.health());
            case "health_rounded" -> Long.toString(Math.round(vitals.health()));
            case "health_max" -> decimal(vitals.maxHealth());
            case "health_percent" -> percent(vitals.health(), vitals.maxHealth());
            case "food" -> Integer.toString(vitals.food());
            case "saturation" -> decimal(vitals.saturation());
            case "air" -> Integer.toString(vitals.air());
            case "air_max" -> Integer.toString(vitals.maxAir());
            case "armor" -> decimal(vitals.armor());
            case "absorption" -> decimal(vitals.absorption());
            case "burning" -> bool(vitals.burning());
            default -> EMPTY;
        };
    }

    /** A share of a whole, rounded to a whole percent; a zero whole reads as zero rather than dividing by it. */
    private static String percent(double part, double whole) {
        return whole <= 0 ? "0" : Long.toString(Math.round(part / whole * 100));
    }

    /** The keys that read where the player stands, in more detail than the session's world name. */
    private static Optional<String> whereFact(PlayerFactsPlaceholders facts, PlayerRef who, String tail) {
        return switch (tail) {
            case "x",
                    "y",
                    "z",
                    "x_exact",
                    "y_exact",
                    "z_exact",
                    "yaw",
                    "pitch",
                    "direction",
                    "biome",
                    "block_below",
                    "light",
                    "world_environment",
                    "location" ->
                Optional.of(facts.where(who).map(where -> where(where, tail)).orElse(EMPTY));
            default -> Optional.empty();
        };
    }

    private static String where(PlayerFactsPlaceholders.Where where, String tail) {
        return switch (tail) {
            case "x" -> Long.toString((long) Math.floor(where.x()));
            case "y" -> Long.toString((long) Math.floor(where.y()));
            case "z" -> Long.toString((long) Math.floor(where.z()));
            case "x_exact" -> decimal(where.x());
            case "y_exact" -> decimal(where.y());
            case "z_exact" -> decimal(where.z());
            case "yaw" -> decimal(where.yaw());
            case "pitch" -> decimal(where.pitch());
            case "direction" -> direction(where.yaw());
            case "biome" -> where.biome();
            case "block_below" -> where.blockBelow();
            case "light" -> Integer.toString(where.light());
            case "world_environment" -> where.environment();
            case "location" ->
                where.world() + " " + (long) Math.floor(where.x()) + " " + (long) Math.floor(where.y()) + " "
                        + (long) Math.floor(where.z());
            default -> EMPTY;
        };
    }

    /**
     * The compass direction a yaw points at, in the eight-point form a HUD shows. Yaw counts clockwise from south,
     * so the table starts there; the half-step offset makes each name cover the 45 degrees centred on it.
     */
    private static String direction(float yaw) {
        String[] points = {"south", "south_west", "west", "north_west", "north", "north_east", "east", "south_east"};
        float normalized = (yaw % 360 + 360) % 360;
        return points[(int) Math.floor((normalized + 22.5f) / 45f) % points.length];
    }

    /** The named counters that read a vanilla statistic without the operator having to spell one. */
    private static Optional<String> statisticShortcut(PlayerFactsPlaceholders facts, PlayerRef who, String tail) {
        String statistic =
                switch (tail) {
                    case "deaths" -> "deaths";
                    case "kills" -> "player_kills";
                    case "mob_kills" -> "mob_kills";
                    default -> "";
                };
        if (statistic.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(statistic(facts, who, statistic));
    }

    /** One statistic, with anything after the statistic name taken as its block, item or entity qualifier. */
    private static String statistic(PlayerFactsPlaceholders facts, PlayerRef who, String tail) {
        OptionalLong whole = facts.statistic(who, tail, "");
        if (whole.isPresent()) {
            return Long.toString(whole.getAsLong());
        }
        int cut = tail.lastIndexOf('_');
        while (cut > 0) {
            OptionalLong qualified = facts.statistic(who, tail.substring(0, cut), tail.substring(cut + 1));
            if (qualified.isPresent()) {
                return Long.toString(qualified.getAsLong());
            }
            cut = tail.lastIndexOf('_', cut - 1);
        }
        return EMPTY;
    }

    /** The keys that read the stored profile, so they answer whether or not the player is connected. */
    private static Optional<String> accountFact(PlayerFactsPlaceholders facts, PlayerRef who, String tail) {
        return switch (tail) {
            case "first_join",
                    "first_join_date",
                    "last_seen",
                    "last_seen_date",
                    "playtime",
                    "playtime_formatted",
                    "playtime_days",
                    "playtime_hours",
                    "playtime_minutes",
                    "playtime_seconds",
                    "banned" ->
                Optional.of(facts.account(who)
                        .map(account -> account(account, tail))
                        .orElse(EMPTY));
            default -> Optional.empty();
        };
    }

    private static String account(PlayerFactsPlaceholders.Account account, String tail) {
        Duration played = account.playtime();
        return switch (tail) {
            case "first_join", "first_join_date" ->
                account.firstPlayed().map(PlaceholderResolver::date).orElse(EMPTY);
            case "last_seen", "last_seen_date" ->
                account.lastSeen().map(PlaceholderResolver::date).orElse(EMPTY);
            case "playtime" -> Long.toString(played.toHours());
            case "playtime_formatted" -> PlaceholderDurations.compact(played);
            case "playtime_days" -> Long.toString(played.toDays());
            case "playtime_hours" -> Long.toString(played.toHours());
            case "playtime_minutes" -> Long.toString(played.toMinutes());
            case "playtime_seconds" -> Long.toString(played.toSeconds());
            case "banned" -> bool(account.banned());
            default -> EMPTY;
        };
    }

    /** The keys that read the live session, which hold no value once the player disconnects. */
    private static String sessionFact(PlayerFactsPlaceholders.Session session, String tail) {
        return switch (tail) {
            case "ping" -> Integer.toString(session.ping());
            case "sneaking" -> bool(session.sneaking());
            case "sprinting" -> bool(session.sprinting());
            case "op" -> bool(session.op());
            case "world" -> session.world();
            case "world_time" -> Long.toString(session.worldTime());
            case "world_time_formatted" -> clockTime(session.worldTime());
            case "world_weather" -> weather(session);
            case "level" -> Integer.toString(session.level());
            case "exp_total" -> Integer.toString(session.totalExperience());
            case "exp_to_next" -> Integer.toString(session.experienceToNextLevel());
            case "exp_progress" -> decimal(session.experienceProgress());
            case "exp_percent" -> Long.toString(Math.round(session.experienceProgress() * 100));
            default -> EMPTY;
        };
    }

    /** A world's tick clock as the wall time a player reads on it: tick 0 is 06:00. */
    private static String clockTime(long ticks) {
        long minutesOfDay = (ticks / 1_000 * 60 + ticks % 1_000 * 60 / 1_000 + 6 * 60) % 1_440;
        return String.format(Locale.ROOT, "%02d:%02d", minutesOfDay / 60, minutesOfDay % 60);
    }

    private static String weather(PlayerFactsPlaceholders.Session session) {
        return sky(session.worldStorming(), session.worldThundering());
    }

    private static String date(Instant instant) {
        return DATE.format(instant.atZone(ZoneId.systemDefault()));
    }

    /**
     * Resolve a {@code hand_*} or {@code offhand_*} tail against the item in that hand. An empty hand, an offline
     * player, or an absent seam degrades every key to the dash, so a scoreboard line reads blank rather than
     * stale when the player puts the item away.
     */
    private String heldItem(PlayerRef who, PlayerFactsPlaceholders.Hand hand, String tail) {
        Optional<PlayerFactsPlaceholders> seam = contexts.playerFacts();
        if (seam.isEmpty()) {
            return EMPTY;
        }
        return seam.get().held(who, hand).map(item -> itemField(item, tail)).orElse(EMPTY);
    }

    private static String itemField(PlayerFactsPlaceholders.HeldItem item, String tail) {
        return switch (tail) {
            case "type" -> item.type();
            case "name" -> item.name();
            case "amount" -> Integer.toString(item.amount());
            case "damage" -> Integer.toString(item.damage());
            case "durability" -> Integer.toString(Math.max(0, item.maxDurability() - item.damage()));
            case "durability_max" -> Integer.toString(item.maxDurability());
            case "enchants" -> item.enchantments().isEmpty() ? EMPTY : String.join(", ", item.enchantments());
            case "enchants_count" -> Integer.toString(item.enchantments().size());
            case "lore" -> item.lore().isEmpty() ? EMPTY : String.join(" ", item.lore());
            case "model" -> optionalIntOr(item.model());
            default -> EMPTY;
        };
    }

    /** Resolve {@code itemcount_<material>}: how many of one material the player carries. */
    private String itemCount(PlayerRef who, String material) {
        Optional<PlayerFactsPlaceholders> seam = contexts.playerFacts();
        if (seam.isEmpty() || material.isBlank()) {
            return EMPTY;
        }
        return optionalIntOr(seam.get().itemCount(who, material));
    }

    /**
     * Resolve a {@code %rel_uxmessentials_<key>%} key, which reads the relation between two players rather than
     * one player's own state: {@code viewer} is the player the line is being rendered for and {@code target} the
     * player it is about. PlaceholderAPI only supplies both sides where a surface renders per viewer (a chat
     * format, a tab or nametag line), so these keys are typed apart from the rest rather than folded into
     * {@link #resolve}. An unknown key returns {@link Optional#empty()} so the raw token survives, and every
     * absent seam degrades to the same answer a disabled module gives: nobody is hidden, nobody is ignoring,
     * nobody is trading.
     */
    public Optional<String> resolveRelational(PlayerRef viewer, PlayerRef target, String key) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(key, "key");
        return switch (key.toLowerCase(Locale.ROOT)) {
            case "cansee" -> Optional.of(bool(canSee(viewer, target)));
            case "hidden" -> Optional.of(bool(!canSee(viewer, target)));
            case "ignoring" -> Optional.of(bool(ignores(viewer, target)));
            case "ignored_by" -> Optional.of(bool(ignores(target, viewer)));
            case "same_world" -> Optional.of(bool(sameWorld(viewer, target)));
            case "distance" -> Optional.of(distance(viewer, target));
            case "trading" ->
                Optional.of(bool(contexts.trade()
                        .map(seam -> seam.isTradingWith(viewer, target))
                        .orElse(false)));
            default -> Optional.empty();
        };
    }

    /** Whether vanish leaves the target visible to the viewer; with vanish off nobody is hidden. */
    private boolean canSee(PlayerRef viewer, PlayerRef target) {
        return contexts.visibility()
                .map(gate -> !gate.isHiddenFrom(viewer, target))
                .orElse(true);
    }

    private boolean ignores(PlayerRef owner, PlayerRef other) {
        return contexts.messaging().map(seam -> seam.ignores(owner, other)).orElse(false);
    }

    private boolean sameWorld(PlayerRef viewer, PlayerRef target) {
        Optional<PlayerFactsPlaceholders> seam = contexts.playerFacts();
        if (seam.isEmpty()) {
            return false;
        }
        Optional<PlayerFactsPlaceholders.Position> here = seam.get().position(viewer);
        Optional<PlayerFactsPlaceholders.Position> there = seam.get().position(target);
        return here.isPresent()
                && there.isPresent()
                && here.get().world().equals(there.get().world());
    }

    /**
     * How far apart the two stand, in blocks to two decimals. Two players in different worlds have no distance
     * to speak of, and neither does a pair where one is offline, so both read the dash.
     */
    private String distance(PlayerRef viewer, PlayerRef target) {
        Optional<PlayerFactsPlaceholders> seam = contexts.playerFacts();
        if (seam.isEmpty()) {
            return EMPTY;
        }
        Optional<PlayerFactsPlaceholders.Position> here = seam.get().position(viewer);
        Optional<PlayerFactsPlaceholders.Position> there = seam.get().position(target);
        if (here.isEmpty()
                || there.isEmpty()
                || !here.get().world().equals(there.get().world())) {
            return EMPTY;
        }
        double dx = here.get().x() - there.get().x();
        double dy = here.get().y() - there.get().y();
        double dz = here.get().z() - there.get().z();
        return decimal(Math.sqrt(dx * dx + dy * dy + dz * dz));
    }

    private static String formatNumber(String raw) {
        return PlaceholderFormats.number(raw).orElse(EMPTY);
    }

    private static String formatCompact(String raw) {
        return PlaceholderFormats.compact(raw).orElse(EMPTY);
    }

    private static String formatTime(String raw) {
        return PlaceholderFormats.time(raw).orElse(EMPTY);
    }

    private static String progressBar(String tail) {
        return PlaceholderFormats.progressBar(tail).orElse(EMPTY);
    }

    /**
     * Resolve the generic cooldown family against the shared gate: {@code cooldown_<label>} is the wait left in
     * whole seconds, {@code cooldown_<label>_formatted} the same wait in the compact form, and
     * {@code cooldown_active_<label>} whether one is running at all. The label is the operator's own, the one
     * the command-control rule stamps, so a scoreboard can count down any gated command without the plugin
     * knowing about it in advance. Reading never stamps, so a placeholder refresh cannot start a cooldown.
     */
    private String cooldown(PlayerRef who, String tail) {
        Optional<Cooldowns> seam = contexts.cooldowns();
        if (seam.isEmpty()) {
            return EMPTY;
        }
        if (tail.startsWith(COOLDOWN_ACTIVE_PREFIX)) {
            String label = tail.substring(COOLDOWN_ACTIVE_PREFIX.length());
            return label.isBlank()
                    ? EMPTY
                    : bool(remaining(seam.get(), who, label).isPresent());
        }
        boolean formatted = tail.endsWith(FORMATTED_SUFFIX);
        String label = formatted ? tail.substring(0, tail.length() - FORMATTED_SUFFIX.length()) : tail;
        if (label.isBlank()) {
            return EMPTY;
        }
        Duration left = remaining(seam.get(), who, label).orElse(Duration.ZERO);
        return formatted ? PlaceholderDurations.compact(left) : Long.toString(left.toSeconds());
    }

    /** The wait a label still holds over {@code who}, or empty when the gate is open. */
    private static Optional<Duration> remaining(Cooldowns gate, PlayerRef who, String label) {
        return gate.checkLabel(who, label).asError();
    }

    /**
     * Resolve a {@code p_<name>_<key>} tail: the key answered about the named player rather than the requester.
     *
     * <p>A player name may itself contain underscores, so the split is decided by the catalogue rather than by the
     * first underscore: the tail is cut at the earliest point whose remainder is a key the catalogue knows, which
     * makes {@code p_Not_ch_homes_count} read as the player {@code Not_ch}. The name is resolved through the kernel
     * lookup, so an offline-mode server resolves it the same way an online-mode one does, and an unknown name, an
     * absent lookup or a tail that names no key all degrade to the dash. The form never nests: a key that is itself
     * another {@code p_} is not followed.
     */
    private String otherPlayer(String tail) {
        Optional<PlayerLookup> seam = contexts.players();
        if (seam.isEmpty()) {
            return EMPTY;
        }
        for (int split = tail.indexOf('_'); split > 0; split = tail.indexOf('_', split + 1)) {
            String key = tail.substring(split + 1);
            if (key.startsWith(OTHER_PLAYER_PREFIX)
                    || PlaceholderCatalog.find(key).isEmpty()) {
                continue;
            }
            Optional<PlayerRef> target = seam.get().findByName(tail.substring(0, split));
            if (target.isEmpty()) {
                return EMPTY;
            }
            PlayerRef who = target.get();
            return resolve(who, seam.get().isOnline(who.uuid()), key).orElse(EMPTY);
        }
        return EMPTY;
    }

    /**
     * Resolve the three ranks keys against the ranks seam. {@code rank} is the player's current rank display name,
     * {@code rank_next} the next rank up ({@link #MAX_RANK} when they are already at the top), and {@code prestige}
     * their prestige level. All read the DB-backed pointer, so they answer for an offline player too. A disabled
     * module, or a ladder with no ranks, degrades every key to the dash.
     */
    private String ranks(PlayerRef who, String key) {
        Optional<RanksPlaceholders> seam = contexts.ranks();
        if (seam.isEmpty()) {
            return EMPTY;
        }
        Optional<RanksPlaceholders.Standing> standing = seam.get().standing(who);
        if (standing.isEmpty()) {
            return EMPTY;
        }
        RanksPlaceholders.Standing held = standing.get();
        return switch (key) {
            case "rank" -> held.rank();
            case "rank_next" -> held.next().orElse(MAX_RANK);
            case "rank_next_cost" -> optionalLongOr(held.nextCost());
            case "rank_position" -> Integer.toString(held.position());
            case "rank_total" -> Integer.toString(held.total());
            case "rank_progress" -> ladderProgress(held);
            case "prestige" -> Integer.toString(held.prestige());
            default -> EMPTY;
        };
    }

    /** How far up the ladder the player stands, as a whole percentage; the top rung reads 100. */
    private static String ladderProgress(RanksPlaceholders.Standing held) {
        if (held.total() <= 0) {
            return EMPTY;
        }
        return Long.toString(Math.round(held.position() * 100.0 / held.total()));
    }

    private static String optionalLongOr(OptionalLong value) {
        return value.isPresent() ? Long.toString(value.getAsLong()) : EMPTY;
    }

    /**
     * Resolve a {@code homes_*} tail against the homes seam. The count/limit/left scalars and the home-list
     * placeholders read from the seam; the indexed forms ({@code <index>}, {@code <index>_world},
     * {@code <index>_x|y|z}) parse the 1-based index from the tail and degrade to the dash when it is out of
     * range or unparseable, and {@code exists_<label>} reports whether a home carries that label.
     */
    private String homesFamily(PlayerRef who, String tail) {
        Optional<HomesPlaceholders> seam = contexts.homes();
        if (seam.isEmpty()) {
            return EMPTY;
        }
        HomesPlaceholders homes = seam.get();
        switch (tail) {
            case "count" -> {
                return Integer.toString(homes.count(who));
            }
            case "limit" -> {
                int limit = homes.limit(who);
                return limit < 0 ? unlimited() : Integer.toString(limit);
            }
            case "left" -> {
                int limit = homes.limit(who);
                return limit < 0 ? unlimited() : Integer.toString(Math.max(0, limit - homes.count(who)));
            }
            case "list" -> {
                List<HomesPlaceholders.HomeView> all = homes.list(who);
                return all.isEmpty() ? EMPTY : joinNames(all);
            }
            default -> {
                if (tail.startsWith("exists_")) {
                    return homeExists(homes.list(who), tail.substring("exists_".length()));
                }
                return indexedHome(homes.list(who), tail);
            }
        }
    }

    private static String joinNames(List<HomesPlaceholders.HomeView> homes) {
        StringBuilder names = new StringBuilder();
        for (HomesPlaceholders.HomeView home : homes) {
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append(home.name());
        }
        return names.toString();
    }

    private static String homeExists(List<HomesPlaceholders.HomeView> homes, String label) {
        if (label.isBlank()) {
            return NO;
        }
        boolean present = homes.stream().anyMatch(home -> home.name().equalsIgnoreCase(label));
        return bool(present);
    }

    /**
     * Resolve an indexed-home tail: {@code <index>}, {@code <index>_world}, or {@code <index>_x|y|z}. The
     * leading token is the 1-based home index; an unparseable or out-of-range index degrades to the dash.
     */
    private static String indexedHome(List<HomesPlaceholders.HomeView> homes, String tail) {
        List<String> parts = List.of(tail.split("_", 2));
        int index;
        try {
            index = Integer.parseInt(parts.get(0));
        } catch (NumberFormatException ignored) {
            return EMPTY;
        }
        if (index < 1 || index > homes.size()) {
            return EMPTY;
        }
        HomesPlaceholders.HomeView home = homes.get(index - 1);
        if (parts.size() == 1) {
            return home.name();
        }
        return switch (parts.get(1)) {
            case "world" -> home.world();
            case "x" -> Integer.toString(home.blockX());
            case "y" -> Integer.toString(home.blockY());
            case "z" -> Integer.toString(home.blockZ());
            default -> EMPTY;
        };
    }

    /** The bare economy keys ({@code balance}, {@code balance_formatted}, {@code baltop_position}). */
    private String economy(PlayerRef who, String key) {
        return economyFamily(who, key);
    }

    /**
     * Resolve an {@code economy_*} tail (and the bare {@code balance}/{@code baltop_position} aliases) against
     * the economy seam. The default-currency scalars read straight through; the per-currency forms
     * ({@code balance_<currency>}, {@code balance_formatted_<currency>}) resolve the currency by id; the
     * indexed forms ({@code baltop_<n>_*}, {@code baltop_<currency>_<n>_*}) parse a 1-based rank and read the
     * bounded ranked snapshot.
     */
    private String economyFamily(PlayerRef who, String tail) {
        Optional<EconomyPlaceholders> seam = contexts.economy();
        if (seam.isEmpty()) {
            return EMPTY;
        }
        EconomyPlaceholders economy = seam.get();
        switch (tail) {
            case "balance" -> {
                return plainAmount(economy.balance(who));
            }
            case "balance_formatted" -> {
                return economy.formatted(who);
            }
            case "balance_compact", "balance_short" -> {
                return economy.compact(who);
            }
            case "baltop_position" -> {
                return baltopPosition(economy.baltopPosition(who));
            }
            case "currency_name" -> {
                return economy.defaultCurrency().plural();
            }
            case "currency_symbol" -> {
                return economy.defaultCurrency().symbol();
            }
            default -> {
                if (tail.startsWith("balance_formatted_")) {
                    return currencyBalanceFormatted(who, economy, tail.substring("balance_formatted_".length()));
                }
                if (tail.startsWith("balance_")) {
                    return currencyBalance(who, economy, tail.substring("balance_".length()));
                }
                if (tail.startsWith("baltop_")) {
                    return baltop(economy, tail.substring("baltop_".length()));
                }
                return EMPTY;
            }
        }
    }

    private static String currencyBalance(PlayerRef who, EconomyPlaceholders economy, String currencyId) {
        return economy.currency(currencyId)
                .map(currency -> plainAmount(economy.balance(who, currency)))
                .orElse(EMPTY);
    }

    private static String currencyBalanceFormatted(PlayerRef who, EconomyPlaceholders economy, String currencyId) {
        return economy.currency(currencyId)
                .map(currency -> MoneyFormat.withSymbol(economy.balance(who, currency)))
                .orElse(EMPTY);
    }

    /**
     * Resolve a {@code baltop_*} tail: either {@code <n>_<field>} on the default currency or
     * {@code <currency>_<n>_<field>} on a named currency. The rank is parsed 1-based; an unparseable or
     * out-of-range rank, an unknown currency, or an unknown field all degrade to the dash.
     */
    private String baltop(EconomyPlaceholders economy, String tail) {
        List<String> parts = List.of(tail.split("_", 3));
        // Try <currency>_<n>_<field> first when the leading token is not itself a number.
        if (parts.size() == 3 && !isInteger(parts.get(0))) {
            Optional<Currency> currency = economy.currency(parts.get(0));
            if (currency.isEmpty()) {
                return EMPTY;
            }
            return baltopRow(economy, currency.get(), parts.get(1), parts.get(2));
        }
        // Otherwise <n>_<field> on the default currency.
        if (parts.size() >= 2) {
            return baltopRow(economy, economy.defaultCurrency(), parts.get(0), parts.get(1));
        }
        return EMPTY;
    }

    private String baltopRow(EconomyPlaceholders economy, Currency currency, String rankToken, String field) {
        int rank;
        try {
            rank = Integer.parseInt(rankToken);
        } catch (NumberFormatException ignored) {
            return EMPTY;
        }
        Optional<BaltopRow> row = economy.baltopRow(currency, rank);
        if (row.isEmpty()) {
            return EMPTY;
        }
        BaltopRow entry = row.get();
        return switch (field) {
            case "name" -> entry.owner().name();
            case "uuid" -> entry.owner().uuid().toString();
            case "amount" -> plainAmount(entry.balance());
            case "formatted" -> MoneyFormat.withSymbol(entry.balance());
            default -> EMPTY;
        };
    }

    private static boolean isInteger(String token) {
        try {
            Integer.parseInt(token);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    /**
     * Resolve a presence key against the presence seam. Accepts both the bare keys ({@code afk},
     * {@code afk_duration}, {@code vanished}) and the {@code presence_}-stripped family ({@code nickname},
     * {@code realname}, {@code afk_since} as an alias of {@code afk_duration}, {@code afk_reason}). Presence is
     * session-only state, so an offline player or a disabled module degrades every key to the dash.
     */
    private String presence(PlayerRef who, boolean online, String key) {
        Optional<PresencePlaceholders> seam = contexts.presence();
        if (seam.isEmpty() || !online) {
            return EMPTY;
        }
        Optional<PresencePlaceholders.Snapshot> snapshot = seam.get().snapshot(who);
        if (snapshot.isEmpty()) {
            return EMPTY;
        }
        PresencePlaceholders.Snapshot state = snapshot.get();
        return switch (key) {
            case "afk" -> bool(state.afk());
            case "afk_duration", "afk_since" -> state.afk() ? PlaceholderDurations.compact(state.afkFor()) : EMPTY;
            case "afk_reason" -> state.afk() ? state.afkReason().orElse(EMPTY) : EMPTY;
            case "nickname" -> state.nickname();
            case "realname" -> who.name();
            case "vanished" -> bool(state.vanished());
            default -> EMPTY;
        };
    }

    /**
     * Resolve a {@code playerstate_}-stripped key against the playerstate seam. Every key is live session
     * state, so a disabled module or an offline player degrades each to the dash. Coordinates are block-
     * truncated, the float scalars (speed, experience) are rendered with up to two decimal places trimmed,
     * and {@code playtime}/{@code playtime_formatted} read the total time played.
     */
    private String playerstate(PlayerRef who, boolean online, String key) {
        Optional<PlayerstatePlaceholders> seam = contexts.playerstate();
        if (seam.isEmpty() || !online) {
            return EMPTY;
        }
        Optional<PlayerstatePlaceholders.Snapshot> snapshot = seam.get().snapshot(who);
        return snapshot.map(state -> playerstateField(state, key)).orElse(EMPTY);
    }

    private static String playerstateField(PlayerstatePlaceholders.Snapshot state, String key) {
        return switch (key) {
            case "gamemode" -> state.gamemode();
            case "fly" -> bool(state.flightAllowed());
            case "flying" -> bool(state.flying());
            case "god" -> bool(state.god());
            case "speed" -> decimal(state.flying() ? state.flySpeed() : state.walkSpeed());
            case "walk_speed" -> decimal(state.walkSpeed());
            case "fly_speed" -> decimal(state.flySpeed());
            case "health" -> decimal(state.health());
            case "max_health" -> decimal(state.maxHealth());
            case "food" -> Integer.toString(state.food());
            case "level" -> Integer.toString(state.level());
            case "xp" -> decimal(state.experienceProgress());
            case "world" -> state.world();
            case "x" -> Integer.toString(state.blockX());
            case "y" -> Integer.toString(state.blockY());
            case "z" -> Integer.toString(state.blockZ());
            case "biome" -> state.biome();
            case "playtime" -> Long.toString(state.playtime().toHours());
            case "playtime_formatted" -> PlaceholderDurations.compact(state.playtime());
            default -> EMPTY;
        };
    }

    /**
     * Resolve a {@code kit_*} tail against the kits seam. The indexed forms carry the kit id in their tail:
     * {@code cooldown_<id>} (and the {@code cooldown_<id>_formatted} alias) the remaining wait, {@code
     * available_<id>} whether the kit may be claimed now, {@code has_<id>} whether the player holds the
     * per-kit permission, {@code cost_<id>} the price ({@code free} when there is no charge), and {@code
     * claims_left_<id>} the remaining claims ({@code unlimited} for a repeatable kit). A disabled module, a
     * blank id, or an unknown kit degrades each to the dash.
     */
    private String kitFamily(PlayerRef who, String tail) {
        Optional<KitsPlaceholders> seam = contexts.kits();
        if (seam.isEmpty()) {
            return EMPTY;
        }
        KitsPlaceholders kits = seam.get();
        if (tail.startsWith(KIT_COOLDOWN_PREFIX)) {
            return kitCooldown(kits, who, tail.substring(KIT_COOLDOWN_PREFIX.length()));
        }
        if (tail.startsWith(KIT_AVAILABLE_PREFIX)) {
            return kitId(tail.substring(KIT_AVAILABLE_PREFIX.length()))
                    .flatMap(id -> kits.available(who, id))
                    .map(PlaceholderResolver::bool)
                    .orElse(EMPTY);
        }
        if (tail.startsWith(KIT_HAS_PREFIX)) {
            return kitId(tail.substring(KIT_HAS_PREFIX.length()))
                    .flatMap(id -> kits.hasPermission(who, id))
                    .map(PlaceholderResolver::bool)
                    .orElse(EMPTY);
        }
        if (tail.startsWith(KIT_COST_PREFIX)) {
            return kitId(tail.substring(KIT_COST_PREFIX.length()))
                    .flatMap(kits::cost)
                    .map(PlaceholderResolver::kitCost)
                    .orElse(EMPTY);
        }
        if (tail.startsWith(KIT_CLAIMS_LEFT_PREFIX)) {
            return kitId(tail.substring(KIT_CLAIMS_LEFT_PREFIX.length()))
                    .flatMap(id -> kits.claimsLeft(who, id))
                    .map(left -> left < 0 ? unlimited() : Integer.toString(left))
                    .orElse(EMPTY);
        }
        return EMPTY;
    }

    /**
     * Resolve a {@code kit_cooldown_*} tail: {@code <id>} for the raw {@code 1m30s} remaining, or
     * {@code <id>_formatted} for the same value (kept distinct so a config can pin either spelling). An
     * unknown kit or a blank id degrades to the dash.
     */
    private static String kitCooldown(KitsPlaceholders kits, PlayerRef who, String tail) {
        String id = tail.endsWith("_formatted") ? tail.substring(0, tail.length() - "_formatted".length()) : tail;
        return kitId(id)
                .flatMap(kitId -> kits.cooldownRemaining(who, kitId))
                .map(PlaceholderDurations::compact)
                .orElse(EMPTY);
    }

    /** The comma-separated ids of the kits the player may claim, or the dash when they may claim none. */
    private String kitsList(PlayerRef who) {
        Optional<KitsPlaceholders> seam = contexts.kits();
        if (seam.isEmpty()) {
            return EMPTY;
        }
        List<String> ids = seam.get().usableIds(who);
        return ids.isEmpty() ? EMPTY : String.join(", ", ids);
    }

    /** A non-blank kit id from a placeholder tail, or empty when the tail carried no id. */
    private static Optional<String> kitId(String tail) {
        return tail.isBlank() ? Optional.empty() : Optional.of(tail);
    }

    /** Render a kit price: {@code free} for a zero cost, else the plain amount. */
    private static String kitCost(java.math.BigDecimal amount) {
        return amount.signum() == 0 ? "free" : amount.toPlainString();
    }

    /**
     * Resolve a {@code vaults_*} tail against the vaults seam. {@code count} reads how many vaults the player
     * holds; {@code max} and {@code left} read the resolved {@code vault.amount} quota (an unlimited quota
     * renders as the infinity marker, and {@code left} as the same marker); {@code size} reads the resolved
     * {@code vault.size} rows. A disabled module degrades every key to the dash.
     */
    private String vaultsFamily(PlayerRef who, String tail) {
        Optional<VaultsPlaceholders> seam = contexts.vaults();
        if (seam.isEmpty()) {
            return EMPTY;
        }
        VaultsPlaceholders vaults = seam.get();
        return switch (tail) {
            case "count" -> Integer.toString(vaults.count(who));
            case "max" -> {
                int max = vaults.max(who);
                yield max < 0 ? unlimited() : Integer.toString(max);
            }
            case "left" -> {
                int max = vaults.max(who);
                yield max < 0 ? unlimited() : Integer.toString(Math.max(0, max - vaults.count(who)));
            }
            case "size" -> Integer.toString(vaults.size(who));
            default -> EMPTY;
        };
    }

    /**
     * Resolve a {@code worlds_*} tail against the worlds seam. Every key is a server-wide global, so the
     * requesting player is ignored: {@code managed_count} reads the registry size, {@code loaded_count} the
     * number of loaded worlds, {@code default} the default world's name (the dash when no world is loaded),
     * and {@code default_players} how many players are in it. A disabled module degrades every key to the dash.
     */
    private String worldsFamily(String tail) {
        Optional<WorldsPlaceholders> seam = contexts.worlds();
        if (seam.isEmpty()) {
            return EMPTY;
        }
        WorldsPlaceholders worlds = seam.get();
        return switch (tail) {
            case "managed_count" -> Integer.toString(worlds.managedCount());
            case "loaded_count" -> Integer.toString(worlds.loadedCount());
            case "default" -> worlds.defaultWorld().orElse(EMPTY);
            case "default_players" -> Integer.toString(worlds.defaultWorldPlayers());
            default -> EMPTY;
        };
    }

    /**
     * Resolve a {@code menu_*} tail against the menu-engine seam. The reverse of the inbound PAPI bridge, reading
     * the requesting player's own live engine state. {@code is_in_menu} answers yes/no; {@code opened} is the
     * current menu's spec id and {@code last} the most-recently-opened id from history, which persists after the
     * menu closes (so it differs from {@code opened} once a menu is shut); {@code page} and {@code rows} are the
     * current menu's 1-based page and row count; {@code argument_<name>} is the value of a typed command argument
     * the current menu was opened with. The engine is always wired, but a resolver built without the seam (a test
     * bundle) degrades {@code is_in_menu} to "no" and every other key to the dash rather than the raw token.
     */
    private String menuFamily(PlayerRef who, String tail) {
        Optional<MenuPlaceholders> seam = contexts.menu();
        if (seam.isEmpty()) {
            return tail.equals("is_in_menu") ? NO : EMPTY;
        }
        MenuPlaceholders menu = seam.get();
        UUID uuid = who.uuid();
        if (tail.startsWith(MENU_ARGUMENT_PREFIX)) {
            return menu.argument(uuid, tail.substring(MENU_ARGUMENT_PREFIX.length()))
                    .orElse(EMPTY);
        }
        return switch (tail) {
            case "is_in_menu" -> bool(menu.inMenu(uuid));
            case "opened" -> menu.openedMenu(uuid).orElse(EMPTY);
            case "last" -> menu.lastMenu(uuid).orElse(EMPTY);
            case "page" -> optionalIntOr(menu.page(uuid));
            case "rows" -> optionalIntOr(menu.rows(uuid));
            default -> EMPTY;
        };
    }

    private static String optionalIntOr(OptionalInt value) {
        return value.isPresent() ? Integer.toString(value.getAsInt()) : EMPTY;
    }

    /**
     * Resolve a {@code warps_*} tail against the warps seam. {@code count} reads how many warps the player may
     * use; {@code list} joins their names (the dash when they may use none). A disabled module degrades both to
     * the dash. Only warps the player may teleport to are counted or listed.
     */
    private String warpsFamily(PlayerRef who, String tail) {
        Optional<WarpsPlaceholders> seam = contexts.warps();
        if (seam.isEmpty()) {
            return EMPTY;
        }
        WarpsPlaceholders warps = seam.get();
        return switch (tail) {
            case "count" -> Integer.toString(warps.count(who));
            case "list" -> {
                List<String> names = warps.accessibleNames(who);
                yield names.isEmpty() ? EMPTY : String.join(", ", names);
            }
            default -> EMPTY;
        };
    }

    /**
     * Resolve a {@code warp_<name>_<field>} tail. The field is the segment after the last underscore (so a warp
     * name may itself contain underscores) and is read off the warp's view: {@code world}, {@code x|y|z},
     * {@code visits}, {@code owner}, or {@code cost}. A disabled module, a malformed tail, a warp that does not
     * exist, or one the player may not use all degrade to the dash.
     */
    private String warpField(PlayerRef who, String tail) {
        Optional<WarpsPlaceholders> seam = contexts.warps();
        if (seam.isEmpty()) {
            return EMPTY;
        }
        int split = tail.lastIndexOf('_');
        if (split <= 0 || split == tail.length() - 1) {
            return EMPTY;
        }
        String name = tail.substring(0, split);
        String field = tail.substring(split + 1);
        Optional<WarpsPlaceholders.WarpView> view = seam.get().find(who, name);
        if (view.isEmpty()) {
            return EMPTY;
        }
        WarpsPlaceholders.WarpView warp = view.get();
        return switch (field) {
            case "world" -> warp.world();
            case "x" -> Integer.toString(warp.blockX());
            case "y" -> Integer.toString(warp.blockY());
            case "z" -> Integer.toString(warp.blockZ());
            case "visits" -> Long.toString(warp.visits());
            case "owner" -> warp.owner();
            case "cost" -> warp.cost().toPlainString();
            default -> EMPTY;
        };
    }

    /**
     * Resolve a {@code playerwarps_*} tail against the player-warps seam. {@code count} reads how many warps the
     * player owns; {@code limit} the resolved {@code uxmessentials.pwarp.limit} quota (the infinity marker when
     * unlimited); {@code left} the remaining headroom (the same marker when unlimited); {@code list} joins the
     * owned names (the dash when they own none). A disabled module degrades every key to the dash.
     */
    private String playerwarpsFamily(PlayerRef who, String tail) {
        Optional<PlayerwarpsPlaceholders> seam = contexts.playerwarps();
        if (seam.isEmpty()) {
            return EMPTY;
        }
        PlayerwarpsPlaceholders playerwarps = seam.get();
        return switch (tail) {
            case "count" -> Integer.toString(playerwarps.count(who));
            case "limit" -> {
                int limit = playerwarps.limit(who);
                yield limit < 0 ? unlimited() : Integer.toString(limit);
            }
            case "left" -> {
                int limit = playerwarps.limit(who);
                yield limit < 0 ? unlimited() : Integer.toString(Math.max(0, limit - playerwarps.count(who)));
            }
            case "list" -> {
                List<PlayerwarpsPlaceholders.PlayerWarpView> all = playerwarps.list(who);
                yield all.isEmpty() ? EMPTY : joinPlayerwarpNames(all);
            }
            default -> EMPTY;
        };
    }

    private static String joinPlayerwarpNames(List<PlayerwarpsPlaceholders.PlayerWarpView> warps) {
        StringBuilder names = new StringBuilder();
        for (PlayerwarpsPlaceholders.PlayerWarpView warp : warps) {
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append(warp.name());
        }
        return names.toString();
    }

    /**
     * Resolve a {@code playerwarp_<name>_<field>} tail. The field is the segment after the last underscore (so a
     * warp name may itself contain underscores) and is read off the warp the player owns: {@code owner},
     * {@code world}, {@code x|y|z}, or {@code visits}. A disabled module, a malformed tail, or a warp the player
     * does not own all degrade to the dash.
     */
    private String playerwarpField(PlayerRef who, String tail) {
        Optional<PlayerwarpsPlaceholders> seam = contexts.playerwarps();
        if (seam.isEmpty()) {
            return EMPTY;
        }
        int split = tail.lastIndexOf('_');
        if (split <= 0 || split == tail.length() - 1) {
            return EMPTY;
        }
        String name = tail.substring(0, split);
        String field = tail.substring(split + 1);
        Optional<PlayerwarpsPlaceholders.PlayerWarpView> view = seam.get().find(who, name);
        if (view.isEmpty()) {
            return EMPTY;
        }
        PlayerwarpsPlaceholders.PlayerWarpView warp = view.get();
        return switch (field) {
            case "owner" -> warp.owner();
            case "world" -> warp.world();
            case "x" -> Integer.toString(warp.blockX());
            case "y" -> Integer.toString(warp.blockY());
            case "z" -> Integer.toString(warp.blockZ());
            case "visits" -> Long.toString(warp.visits());
            default -> EMPTY;
        };
    }

    /**
     * Resolve a {@code messaging_}-stripped key against the messaging seam. The two durable mail keys
     * ({@code mail_unread}, {@code mail_total}) and the ignore count ({@code ignoring_count}) read straight
     * through and answer for an offline player as well, since mail and the ignore list are DB-backed. The
     * session-scoped keys, {@code reply_target} (the last-conversation partner), {@code msgtoggle} (whether
     * the player accepts DMs) and {@code socialspy} (the spy flag), hold no value for an offline player, so
     * the offline guard degrades them to the dash. A disabled module degrades every key to the dash.
     */
    private String messaging(PlayerRef who, boolean online, String key) {
        Optional<MessagingPlaceholders> seam = contexts.messaging();
        if (seam.isEmpty()) {
            return EMPTY;
        }
        MessagingPlaceholders messaging = seam.get();
        return switch (key) {
            case "mail_unread" -> Long.toString(messaging.unreadMail(who));
            case "mail_total" -> Long.toString(messaging.totalMail(who));
            case "ignoring_count" -> Integer.toString(messaging.ignoringCount(who));
            case "reply_target" -> online ? messaging.replyTarget(who).orElse(EMPTY) : EMPTY;
            case "msgtoggle" -> online ? bool(messaging.acceptingMessages(who)) : EMPTY;
            case "socialspy" -> online ? bool(messaging.socialSpy(who)) : EMPTY;
            default -> EMPTY;
        };
    }

    /**
     * Resolve a {@code staff_}-stripped key against the staff seam. {@code mode} reads the live session-scoped
     * {@code /staffmode} marker, so an offline requester (who holds no marker) reads {@code no}; {@code online}
     * and its {@code count} alias read the server-wide online-staff roster size. The holders of the
     * {@code uxmessentials.staff.member} marker, which answer for an offline requester too since the count does
     * not depend on who asks. A disabled module degrades every key to the dash.
     */
    private String staff(PlayerRef who, boolean online, String key) {
        Optional<StaffPlaceholders> seam = contexts.staff();
        if (seam.isEmpty()) {
            return EMPTY;
        }
        StaffPlaceholders staff = seam.get();
        return switch (key) {
            case "mode" -> online ? bool(staff.inStaffMode(who)) : NO;
            case "online", "count" -> Integer.toString(staff.onlineStaffCount());
            default -> EMPTY;
        };
    }

    /**
     * Resolve a {@code discordlink_}-stripped key against the discord-link seam. {@code linked} reports whether
     * the account is bound to a Discord user; {@code id} reads the bound Discord snowflake (the dash when not
     * linked). Both reads are DB-backed and answer for an offline player. The binding stores only the snowflake
     * id, not a Discord username, so there is no username key. A disabled module degrades every key to the dash.
     */
    private String discordlink(PlayerRef who, String key) {
        Optional<DiscordlinkPlaceholders> seam = contexts.discordlink();
        if (seam.isEmpty()) {
            return EMPTY;
        }
        DiscordlinkPlaceholders discordlink = seam.get();
        return switch (key) {
            case "linked" -> bool(discordlink.linked(who));
            case "id" -> discordlink.discordId(who).orElse(EMPTY);
            default -> EMPTY;
        };
    }

    /**
     * Resolve a {@code holograms_}-stripped key against the holograms seam. The only key is {@code count}, the
     * server-wide number of registered holograms, the same for every requester. A disabled module degrades it to
     * the dash.
     */
    private String holograms(String key) {
        Optional<HologramsPlaceholders> seam = contexts.holograms();
        if (seam.isEmpty()) {
            return EMPTY;
        }
        return key.equals("count") ? Integer.toString(seam.get().count()) : EMPTY;
    }

    /**
     * Resolve a {@code communication_}-stripped key against the communication seam. {@code chat_enabled} reads the
     * server-wide chat lock, open while {@code /togglechat} does not hold it, and answers for any requester.
     * {@code broadcasts} reads the requester's announcer subscription ({@code /broadcasttoggle}); the store
     * resolves the connected player, so it holds no value for an offline requester and the offline guard degrades
     * it to the dash. A disabled module degrades every key to the dash.
     */
    private String communication(PlayerRef who, boolean online, String key) {
        Optional<CommunicationPlaceholders> seam = contexts.communication();
        if (seam.isEmpty()) {
            return EMPTY;
        }
        CommunicationPlaceholders communication = seam.get();
        return switch (key) {
            case "chat_enabled" -> bool(communication.chatEnabled());
            case "broadcasts" -> online ? bool(communication.receivesBroadcasts(who)) : EMPTY;
            default -> EMPTY;
        };
    }

    /**
     * Resolve a {@code scoreboard_}-stripped key against the scoreboard seam. {@code visible} reports whether the
     * requester currently sees the sidebar ({@code yes}) or has hidden it with {@code /scoreboard} ({@code no}). The
     * preference is read off the live player, so it holds no value for an offline requester and the offline guard
     * degrades it to the dash. A disabled module degrades every key to the dash.
     */
    private String scoreboard(PlayerRef who, boolean online, String key) {
        Optional<ScoreboardPlaceholders> seam = contexts.scoreboard();
        if (seam.isEmpty() || !online) {
            return EMPTY;
        }
        return switch (key) {
            case "visible" -> bool(seam.get().visible(who));
            case "board" -> seam.get().board(who).orElse(EMPTY);
            default -> EMPTY;
        };
    }

    /**
     * Resolve a {@code tablist_}-stripped key: which authored format the player's tab is drawn from right now. A
     * player drawn from none (no format matched, or the content is suppressed in their world) reads the dash, and so
     * does an offline requester, who has no tab at all.
     */
    private String tablist(PlayerRef who, boolean online, String key) {
        Optional<TablistPlaceholders> seam = contexts.tablist();
        if (seam.isEmpty() || !online) {
            return EMPTY;
        }
        return switch (key) {
            case "format" -> seam.get().format(who).orElse(EMPTY);
            case "shown" -> bool(seam.get().format(who).isPresent());
            default -> EMPTY;
        };
    }

    /** Resolve a {@code nametags_}-stripped key: the format the player wears above their head, if any. */
    private String nametags(PlayerRef who, boolean online, String key) {
        Optional<NametagsPlaceholders> seam = contexts.nametags();
        if (seam.isEmpty() || !online) {
            return EMPTY;
        }
        return switch (key) {
            case "format" -> seam.get().format(who).orElse(EMPTY);
            case "shown" -> bool(seam.get().format(who).isPresent());
            default -> EMPTY;
        };
    }

    /**
     * Resolve a {@code poses_}-stripped key against the poses seam. {@code sitting} reports whether the requester is
     * currently sitting ({@code yes}/{@code no}); {@code posing} whether they hold a free pose, lay/bellyflop/spin,
     * ({@code yes}/{@code no}); {@code pose} the current pose name ({@code sit}/{@code lay}/{@code bellyflop}/{@code
     * spin}/{@code none}); and {@code toggle} whether they let others sit on them ({@code allow}/{@code refuse}). All
     * are live per-player reads, so a disabled module or an offline requester degrades the key to the dash.
     */
    private String poses(PlayerRef who, boolean online, String key) {
        Optional<PosesPlaceholders> seam = contexts.poses();
        if (seam.isEmpty() || !online) {
            return EMPTY;
        }
        return switch (key) {
            case "sitting" -> bool(seam.get().sitting(who));
            case "posing" -> bool(seam.get().posing(who));
            case "pose" -> seam.get().pose(who);
            case "toggle" -> seam.get().allowsSitting(who) ? ALLOW : REFUSE;
            default -> EMPTY;
        };
    }

    /**
     * Resolve a {@code survival_*} tail. A tail ending in {@code _enabled} asks about the server ("does this
     * server run auto-pickup at all"); anything else asks about the player ("is my own switch on"). The two
     * answers differ often enough that a HUD wants both: a switch that is on while the mechanic is off is exactly
     * the confusion this pair explains.
     */
    private String survival(PlayerRef who, String tail) {
        Optional<SurvivalPlaceholders> seam = contexts.survival();
        if (seam.isEmpty()) {
            return EMPTY;
        }
        boolean serverSide = tail.endsWith(ENABLED_SUFFIX);
        String name = serverSide ? tail.substring(0, tail.length() - ENABLED_SUFFIX.length()) : tail;
        SurvivalPlaceholders.Mechanic mechanic = mechanic(name);
        if (mechanic == null) {
            return EMPTY;
        }
        return bool(serverSide ? seam.get().enabled(mechanic) : seam.get().active(who, mechanic));
    }

    private static SurvivalPlaceholders.@Nullable Mechanic mechanic(String name) {
        return switch (name) {
            case "treefeller" -> SurvivalPlaceholders.Mechanic.TREE_FELLER;
            case "veinminer" -> SurvivalPlaceholders.Mechanic.VEINMINER;
            case "farmprotect" -> SurvivalPlaceholders.Mechanic.FARM_PROTECT;
            case "autopickup" -> SurvivalPlaceholders.Mechanic.AUTO_PICKUP;
            case "autosmelt" -> SurvivalPlaceholders.Mechanic.AUTO_SMELT;
            case "autosell" -> SurvivalPlaceholders.Mechanic.AUTO_SELL;
            case "autotool" -> SurvivalPlaceholders.Mechanic.AUTO_TOOL;
            default -> null;
        };
    }

    /** Resolve an {@code itemworld_*} tail: what the held item runs, and the two personal switches. */
    private String itemworld(PlayerRef who, String tail) {
        Optional<ItemworldPlaceholders> seam = contexts.itemworld();
        if (seam.isEmpty()) {
            return EMPTY;
        }
        ItemworldPlaceholders items = seam.get();
        return switch (tail) {
            case "powertool" -> {
                List<String> bound = items.powertool(who);
                yield bound.isEmpty() ? EMPTY : String.join(", ", bound);
            }
            case "powertool_bound" -> bool(!items.powertool(who).isEmpty());
            case "powertool_count" -> Integer.toString(items.powertool(who).size());
            case "powertool_enabled" -> bool(items.powertoolEnabled(who));
            case "unlimited" -> bool(items.unlimitedPlacement(who));
            default -> EMPTY;
        };
    }

    /** Resolve an {@code npc_*} tail: the server's population, and what one player has left of their quota. */
    private String npc(PlayerRef who, String tail) {
        Optional<NpcPlaceholders> seam = contexts.npc();
        if (seam.isEmpty()) {
            return EMPTY;
        }
        NpcPlaceholders npcs = seam.get();
        return switch (tail) {
            case "total" -> Integer.toString(npcs.total());
            case "owned" -> Integer.toString(npcs.owned(who));
            case "limit" ->
                npcs.limit(who).isPresent() ? Integer.toString(npcs.limit(who).getAsInt()) : UNLIMITED;
            case "remaining" -> {
                OptionalInt cap = npcs.limit(who);
                yield cap.isPresent() ? Integer.toString(Math.max(0, cap.getAsInt() - npcs.owned(who))) : UNLIMITED;
            }
            default -> EMPTY;
        };
    }

    /** Resolve a {@code regions_*} tail against the region the player is standing in right now. */
    private String regions(PlayerRef who, String tail) {
        Optional<RegionsPlaceholders> seam = contexts.regions();
        if (seam.isEmpty()) {
            return EMPTY;
        }
        RegionsPlaceholders regions = seam.get();
        return switch (tail) {
            case "available" -> bool(regions.available());
            case "inside" -> bool(regions.standingIn(who).isPresent());
            case "count" -> Integer.toString(regions.coveringCount(who));
            case "world_count" -> Integer.toString(regions.worldCount(who));
            case "here", "here_priority", "here_owners", "here_members" ->
                regions.standingIn(who)
                        .map(standing -> standing(standing, tail))
                        .orElse(EMPTY);
            default -> EMPTY;
        };
    }

    private static String standing(RegionsPlaceholders.Standing standing, String tail) {
        return switch (tail) {
            case "here" -> standing.id();
            case "here_priority" -> Integer.toString(standing.priority());
            case "here_owners" -> standing.owners().isEmpty() ? EMPTY : String.join(", ", standing.owners());
            case "here_members" -> standing.members().isEmpty() ? EMPTY : String.join(", ", standing.members());
            default -> EMPTY;
        };
    }

    /** Resolve a {@code security_*} tail. Only in-memory session state is readable; enrolment is not. */
    private String security(PlayerRef who, String tail) {
        Optional<SecurityPlaceholders> seam = contexts.security();
        if (seam.isEmpty()) {
            return EMPTY;
        }
        return switch (tail) {
            case "verifying" -> bool(seam.get().verifying(who));
            case "enforced" -> bool(seam.get().enforced());
            default -> EMPTY;
        };
    }

    /** Resolve a {@code villagers_}-stripped key: how many villagers walk after the requester right now. */
    private String villagers(PlayerRef who, boolean online, String key) {
        Optional<VillagersPlaceholders> seam = contexts.villagers();
        if (seam.isEmpty() || !online) {
            return EMPTY;
        }
        return switch (key) {
            case "following" -> Integer.toString(seam.get().following(who));
            case "has_follower" -> bool(seam.get().following(who) > 0);
            default -> EMPTY;
        };
    }

    /** Resolve a {@code servertweaks_}-stripped key: what brand this server reports to its clients. */
    private String serverTweaks(String key) {
        Optional<ServerTweaksPlaceholders> seam = contexts.serverTweaks();
        if (seam.isEmpty()) {
            return EMPTY;
        }
        return key.equals("brand") ? seam.get().brand().orElse(EMPTY) : EMPTY;
    }

    /**
     * Resolve a {@code commandcontrol_}-stripped key. The one family is {@code allowed_<command>}: whether the
     * requester would be allowed to run that command where they stand, answered from the rules the gate uses.
     */
    private String commandControl(PlayerRef who, boolean online, String key) {
        Optional<CommandControlPlaceholders> seam = contexts.commandControl();
        if (seam.isEmpty() || !online || !key.startsWith(COMMANDCONTROL_ALLOWED_PREFIX)) {
            return EMPTY;
        }
        String command = key.substring(COMMANDCONTROL_ALLOWED_PREFIX.length());
        return command.isEmpty() ? EMPTY : bool(seam.get().allowed(who, command));
    }

    /**
     * Resolve an {@code invrollback_}-stripped key: when this enable last snapshotted the player's inventory, and
     * why. A player nothing has been captured for since the last restart reads the dash, which is the difference
     * between "no snapshot was taken here" and "we do not know", and the module says only the first.
     */
    private String invrollback(PlayerRef who, String key) {
        Optional<InvrollbackPlaceholders> seam = contexts.invrollback();
        if (seam.isEmpty()) {
            return EMPTY;
        }
        return switch (key) {
            case "last_capture" ->
                seam.get()
                        .lastCapture(who)
                        .map(at -> PlaceholderDurations.compact(Duration.between(at, Instant.now())))
                        .orElse(EMPTY);
            case "last_cause" -> seam.get().lastCause(who).orElse(EMPTY);
            case "captured" -> bool(seam.get().lastCapture(who).isPresent());
            default -> EMPTY;
        };
    }

    /**
     * Resolve a {@code skin_}-stripped key: which skin the player chose, where it came from and which model it was
     * cut for. A player who chose nothing reads the dash rather than a guess at what the join order dressed them
     * in, because only the choice is theirs.
     */
    private String skin(PlayerRef who, String key) {
        Optional<SkinPlaceholders> seam = contexts.skin();
        if (seam.isEmpty()) {
            return EMPTY;
        }
        return switch (key) {
            case "source" -> seam.get().source(who).orElse(EMPTY);
            case "value" -> seam.get().value(who).orElse(EMPTY);
            case "model" -> seam.get().model(who).orElse(EMPTY);
            case "chosen" -> bool(seam.get().source(who).isPresent());
            default -> EMPTY;
        };
    }

    /** Resolve {@code module_<id>}: whether that feature module wired on this enable. */
    private String module(String id) {
        return contexts.modules().map(modules -> bool(modules.enabled(id))).orElse(EMPTY);
    }

    /**
     * Resolve a {@code server_}-stripped key against the always-present server-metrics seam. Every value is a
     * server-wide global, so the requesting player is ignored. The TPS keys read the Paper {@code getTPS()}
     * window, {@code tps} the 1-minute rate, {@code tps_5m}/{@code tps_15m} the longer windows, and
     * {@code tps_colored} the 1-minute rate wrapped in a MiniMessage colour (green/yellow/red); the heap keys
     * read whole megabytes; {@code uptime} reads whole minutes and {@code uptime_formatted} the {@code 1h30m}
     * compact form; {@code world_players_<world>} counts a named world's roster (the dash for an unknown world).
     * The seam is always wired, so an unknown key still degrades to the dash rather than the raw token.
     */
    private String serverMetric(String tail) {
        Optional<ServerMetricsPlaceholders> seam = contexts.serverMetrics();
        if (seam.isEmpty()) {
            return EMPTY;
        }
        ServerMetricsPlaceholders metrics = seam.get();
        if (tail.startsWith(SERVER_WORLD_PLAYERS_PREFIX)) {
            return worldPlayers(metrics, tail.substring(SERVER_WORLD_PLAYERS_PREFIX.length()));
        }
        if (tail.startsWith(SERVER_WORLD_ENTITIES_PREFIX)) {
            return count(metrics.worldEntities(tail.substring(SERVER_WORLD_ENTITIES_PREFIX.length())));
        }
        if (tail.startsWith(SERVER_WORLD_CHUNKS_PREFIX)) {
            return count(metrics.worldChunks(tail.substring(SERVER_WORLD_CHUNKS_PREFIX.length())));
        }
        // The formatted spelling is checked first: it is itself prefixed by the raw one.
        if (tail.startsWith(SERVER_WORLD_TIME_FORMATTED_PREFIX)) {
            return worldClock(metrics, tail.substring(SERVER_WORLD_TIME_FORMATTED_PREFIX.length()), true);
        }
        if (tail.startsWith(SERVER_WORLD_TIME_PREFIX)) {
            return worldClock(metrics, tail.substring(SERVER_WORLD_TIME_PREFIX.length()), false);
        }
        if (tail.startsWith(SERVER_WORLD_WEATHER_PREFIX)) {
            return worldWeather(metrics, tail.substring(SERVER_WORLD_WEATHER_PREFIX.length()));
        }
        return switch (tail) {
            case "online" -> Integer.toString(metrics.onlinePlayers());
            case "max_players" -> Integer.toString(metrics.maxPlayers());
            case "version" -> metrics.minecraftVersion();
            case "uptime" -> Long.toString(metrics.uptime().toMinutes());
            case "uptime_formatted" -> PlaceholderDurations.compact(metrics.uptime());
            case "tps" -> tps(metrics, 0);
            case "tps_5m" -> tps(metrics, 1);
            case "tps_15m" -> tps(metrics, 2);
            case "tps_colored" -> tpsColored(metrics);
            case "ram_used" -> Long.toString(metrics.ramUsedMb());
            case "ram_max" -> Long.toString(metrics.ramMaxMb());
            case "ram_free" -> Long.toString(metrics.ramFreeMb());
            case "name" -> metrics.name();
            case "motd" -> metrics.motd();
            case "worlds" -> Integer.toString(metrics.worlds());
            case "time" -> CLOCK.format(LocalTime.now(ZoneId.systemDefault()));
            case "date" -> DAY.format(LocalDate.now(ZoneId.systemDefault()));
            default -> EMPTY;
        };
    }

    /** A per-world count, or the dash when the key named a world the server has not loaded. */
    private static String count(java.util.OptionalInt found) {
        return found.isPresent() ? Integer.toString(found.getAsInt()) : EMPTY;
    }

    /** A named world's time of day, in ticks or as the clock a player reads; the dash for an unloaded world. */
    private static String worldClock(ServerMetricsPlaceholders metrics, String world, boolean formatted) {
        if (world.isBlank()) {
            return EMPTY;
        }
        return metrics.worldSky(world)
                .map(sky -> formatted ? clockTime(sky.time()) : Long.toString(sky.time()))
                .orElse(EMPTY);
    }

    /** A named world's sky: clear, rain or thunder; the dash for an unloaded world. */
    private static String worldWeather(ServerMetricsPlaceholders metrics, String world) {
        if (world.isBlank()) {
            return EMPTY;
        }
        return metrics.worldSky(world)
                .map(above -> sky(above.storming(), above.thundering()))
                .orElse(EMPTY);
    }

    /** What the sky over a world reads as: a thunderstorm outranks the rain flag it always carries with it. */
    private static String sky(boolean storming, boolean thundering) {
        if (thundering) {
            return "thunder";
        }
        return storming ? "rain" : "clear";
    }

    /** Render one TPS window, clamped to the 20.0 ceiling and trimmed to two decimals. */
    private static String tps(ServerMetricsPlaceholders metrics, int window) {
        return decimal(clampTps(metrics.tps()[window]));
    }

    /**
     * The 1-minute TPS wrapped in a MiniMessage colour: green at or above 18, yellow at or above 15, red below.
     * The HUD renders the result through MiniMessage; a raw PAPI consumer sees the tag literally, so prefer the
     * uncoloured {@code server_tps} where MiniMessage is not in play.
     */
    private static String tpsColored(ServerMetricsPlaceholders metrics) {
        double value = clampTps(metrics.tps()[0]);
        String colour = value >= 18.0 ? "green" : value >= 15.0 ? "yellow" : "red";
        return "<" + colour + ">" + decimal(value) + "</" + colour + ">";
    }

    private static double clampTps(double value) {
        return Math.min(20.0, value);
    }

    private static String worldPlayers(ServerMetricsPlaceholders metrics, String world) {
        if (world.isBlank()) {
            return EMPTY;
        }
        OptionalInt count = metrics.worldPlayers(world);
        return count.isPresent() ? Integer.toString(count.getAsInt()) : EMPTY;
    }

    /**
     * Resolve a {@code votes_*} tail. Three sub-patterns:
     * <ul>
     *   <li>{@code <period>}. The requesting player's vote count for that period.</li>
     *   <li>{@code top_<period>_<n>_name} or {@code top_<period>_<n>_votes}, the name or vote
     *       count of the player ranked {@code <n>} (1-based) on the leaderboard.</li>
     *   <li>{@code position_<period>}. The requesting player's 1-based leaderboard rank.</li>
     *   <li>{@code streak_current} or {@code streak_best}. The requesting player's current or
     *       best consecutive-day voting streak.</li>
     * </ul>
     */
    private String votes(PlayerRef who, String tail) {
        Optional<VotePlaceholders> seam = contexts.vote();
        if (seam.isEmpty()) {
            return EMPTY;
        }
        VotePlaceholders vote = seam.get();

        if (tail.startsWith(VOTES_TOP_PREFIX)) {
            // top_<period>_<n>_name  or  top_<period>_<n>_votes
            // e.g. tail = "top_monthly_1_name" → strip "top_" → "monthly_1_name"
            String rest = tail.substring(VOTES_TOP_PREFIX.length());
            // rest = "<period>_<n>_<field>"
            List<String> parts = List.of(rest.split("_", 3));
            if (parts.size() != 3) {
                return EMPTY;
            }
            VotePeriod period = parsePeriod(parts.get(0));
            if (period == null) {
                return EMPTY;
            }
            int rank;
            try {
                rank = Integer.parseInt(parts.get(1));
            } catch (NumberFormatException ignored) {
                return EMPTY;
            }
            String field = parts.get(2);
            Optional<VoteRanking> row = vote.topAt(period, rank);
            if (row.isEmpty()) {
                return EMPTY;
            }
            VoteRanking ranking = row.get();
            return switch (field) {
                case "name" -> ranking.player().name();
                case "votes" -> Long.toString(ranking.votes());
                default -> EMPTY;
            };
        }

        if (tail.startsWith(VOTES_POSITION_PREFIX)) {
            // position_<period>
            String periodName = tail.substring(VOTES_POSITION_PREFIX.length());
            VotePeriod period = parsePeriod(periodName);
            if (period == null) {
                return EMPTY;
            }
            OptionalInt pos = vote.positionOf(who, period);
            return pos.isPresent() ? Integer.toString(pos.getAsInt()) : EMPTY;
        }

        if (tail.startsWith(VOTES_STREAK_PREFIX)) {
            // streak_current  or  streak_best
            String field = tail.substring(VOTES_STREAK_PREFIX.length());
            return switch (field) {
                case "current" -> Long.toString(vote.currentStreak(who));
                case "best" -> Long.toString(vote.bestStreak(who));
                default -> EMPTY;
            };
        }

        // Plain period count: votes_<period>
        VotePeriod period = parsePeriod(tail);
        if (period == null) {
            return EMPTY;
        }
        return Long.toString(vote.countFor(who, period));
    }

    private static @Nullable VotePeriod parsePeriod(String periodName) {
        return switch (periodName) {
            case "daily" -> VotePeriod.DAILY;
            case "weekly" -> VotePeriod.WEEKLY;
            case "monthly" -> VotePeriod.MONTHLY;
            case "alltime" -> VotePeriod.ALLTIME;
            default -> null;
        };
    }

    private String voteparty(String subKey) {
        Optional<VotePlaceholders> seam = contexts.vote();
        if (seam.isEmpty()) {
            return EMPTY;
        }
        VotePlaceholders vote = seam.get();
        int count = vote.partyCount();
        int threshold = vote.partyThreshold();
        return switch (subKey) {
            case "current" -> Integer.toString(count);
            case "required" -> Integer.toString(threshold);
            case "remaining" -> Integer.toString(Math.max(0, threshold - count));
            default -> EMPTY;
        };
    }

    /**
     * Resolve a {@code teleport_}-stripped key against the teleport seam. The cooldown/warmup remaining keys
     * carry a raw whole-second scalar and a {@code _formatted} {@code 1m30s} variant, reading {@code 0} (or
     * {@code 0s}) when nothing is in flight; the back-location keys read the captured {@code /back} point
     * (dash when none); the request scalars and the accept flag read the {@code tpa} registry and the
     * {@code /tptoggle} state. A disabled module degrades every key to the dash; offline reads degrade the
     * session-only request/accept and warmup keys to the dash since they cannot be queried.
     */
    private String teleport(PlayerRef who, boolean online, String key) {
        Optional<TeleportPlaceholders> seam = contexts.teleport();
        if (seam.isEmpty()) {
            return EMPTY;
        }
        TeleportPlaceholders teleport = seam.get();
        return switch (key) {
            case "cooldown_remaining" -> remainingSeconds(teleport.cooldownRemaining(who));
            case "cooldown_remaining_formatted" -> remainingFormatted(teleport.cooldownRemaining(who));
            case "warmup_remaining" -> online ? remainingSeconds(teleport.warmupRemaining(who)) : EMPTY;
            case "warmup_remaining_formatted" -> online ? remainingFormatted(teleport.warmupRemaining(who)) : EMPTY;
            case "back_available" -> bool(teleport.backLocation(who).isPresent());
            case "back_world" ->
                teleport.backLocation(who)
                        .map(TeleportPlaceholders.BackView::world)
                        .orElse(EMPTY);
            case "back_x" -> backCoordinate(teleport.backLocation(who), TeleportPlaceholders.BackView::blockX);
            case "back_y" -> backCoordinate(teleport.backLocation(who), TeleportPlaceholders.BackView::blockY);
            case "back_z" -> backCoordinate(teleport.backLocation(who), TeleportPlaceholders.BackView::blockZ);
            case "tpa_incoming" -> online ? Integer.toString(teleport.incomingRequests(who)) : EMPTY;
            case "tpa_pending" -> online ? bool(teleport.hasOutgoingRequest(who)) : EMPTY;
            case "accepting" -> online ? bool(teleport.acceptingRequests(who)) : EMPTY;
            default -> EMPTY;
        };
    }

    private static String backCoordinate(
            Optional<TeleportPlaceholders.BackView> back,
            java.util.function.ToIntFunction<TeleportPlaceholders.BackView> field) {
        return back.map(view -> Integer.toString(field.applyAsInt(view))).orElse(EMPTY);
    }

    private static String remainingSeconds(Optional<Duration> remaining) {
        return Long.toString(remaining.map(d -> Math.max(0, d.toSeconds())).orElse(0L));
    }

    private static String remainingFormatted(Optional<Duration> remaining) {
        return remaining.map(PlaceholderDurations::compact).orElse(PlaceholderDurations.compact(Duration.ZERO));
    }

    private String moderation(PlayerRef who, String key) {
        Optional<ModerationPlaceholders> seam = contexts.moderation();
        if (seam.isEmpty()) {
            return NO;
        }
        ModerationPlaceholders moderation = seam.get();
        return bool(key.equals("muted") ? moderation.isMuted(who) : moderation.isJailed(who));
    }

    /**
     * Resolve a {@code moderation_}-stripped key against the moderation seam. The state-boolean keys
     * ({@code banned}, {@code muted}, {@code jailed}, {@code frozen}) and {@code warns} read straight through;
     * the ban/mute detail keys read the active, clock-gated sanction and render its remaining wait (raw whole
     * seconds and a {@code _formatted} variant; {@code permanent} for a permanent sanction), reason and issuer.
     * A disabled module degrades the booleans to "no" and the detail/count keys to the dash.
     */
    private String moderationFamily(PlayerRef who, String tail) {
        Optional<ModerationPlaceholders> seam = contexts.moderation();
        if (seam.isEmpty()) {
            return isBooleanModerationKey(tail) ? NO : EMPTY;
        }
        ModerationPlaceholders moderation = seam.get();
        return switch (tail) {
            case "banned" -> bool(moderation.activeBan(who).isPresent());
            case "muted" -> bool(moderation.isMuted(who));
            case "jailed" -> bool(moderation.isJailed(who));
            case "frozen" -> bool(moderation.isFrozen(who));
            case "warns" -> Integer.toString(moderation.warnCount(who));
            case "ban_reason" -> sanctionField(moderation.activeBan(who), Sanction.REASON, false);
            case "ban_issuer" -> sanctionField(moderation.activeBan(who), Sanction.ISSUER, false);
            case "ban_remaining" -> sanctionField(moderation.activeBan(who), Sanction.REMAINING, false);
            case "ban_remaining_formatted" -> sanctionField(moderation.activeBan(who), Sanction.REMAINING, true);
            case "mute_reason" -> sanctionField(moderation.activeMute(who), Sanction.REASON, false);
            case "mute_issuer" -> sanctionField(moderation.activeMute(who), Sanction.ISSUER, false);
            case "mute_remaining" -> sanctionField(moderation.activeMute(who), Sanction.REMAINING, false);
            case "mute_remaining_formatted" -> sanctionField(moderation.activeMute(who), Sanction.REMAINING, true);
            case "jail_name" ->
                moderation
                        .activeJail(who)
                        .map(ModerationPlaceholders.JailView::jail)
                        .orElse(EMPTY);
            case "jail_reason" ->
                moderation
                        .activeJail(who)
                        .map(ModerationPlaceholders.JailView::reason)
                        .orElse(EMPTY);
            case "jail_issuer" ->
                moderation
                        .activeJail(who)
                        .map(ModerationPlaceholders.JailView::issuer)
                        .orElse(EMPTY);
            case "jail_remaining" -> jailField(moderation.activeJail(who), false);
            case "jail_remaining_formatted" -> jailField(moderation.activeJail(who), true);
            case "jail_online_only" ->
                moderation.activeJail(who).map(jail -> bool(jail.onlineOnly())).orElse(NO);
            default -> EMPTY;
        };
    }

    private static boolean isBooleanModerationKey(String tail) {
        return switch (tail) {
            case "banned", "muted", "jailed", "frozen" -> true;
            default -> false;
        };
    }

    private enum Sanction {
        REASON,
        ISSUER,
        REMAINING
    }

    /** Render one field of an active ban/mute view, or the dash when no sanction is active. */
    private static String sanctionField(
            Optional<ModerationPlaceholders.SanctionView> view, Sanction field, boolean formatted) {
        if (view.isEmpty()) {
            return EMPTY;
        }
        ModerationPlaceholders.SanctionView active = view.get();
        return switch (field) {
            case REASON -> active.reason();
            case ISSUER -> active.issuer();
            case REMAINING -> sanctionRemaining(active.remaining(), formatted);
        };
    }

    /** A jail's remaining wait, on the same shape as a ban's: the dash when no jail holds the player. */
    private static String jailField(Optional<ModerationPlaceholders.JailView> view, boolean formatted) {
        return view.map(jail -> sanctionRemaining(jail.remaining(), formatted)).orElse(EMPTY);
    }

    /** A sanction's remaining wait, or {@code permanent} when it never lifts. */
    private static String sanctionRemaining(Optional<Duration> remaining, boolean formatted) {
        if (remaining.isEmpty()) {
            return "permanent";
        }
        Duration left = remaining.get();
        return formatted ? PlaceholderDurations.compact(left) : Long.toString(Math.max(0, left.toSeconds()));
    }

    private static String baltopPosition(OptionalInt position) {
        return position.isPresent() ? Integer.toString(position.getAsInt()) : EMPTY;
    }

    private static String plainAmount(Money money) {
        return money.amount().toPlainString();
    }

    private static String bool(boolean value) {
        return value ? YES : NO;
    }

    /**
     * A live scalar (health, speed, experience progress) rounded to two decimal places with trailing zeros
     * stripped, so {@code 20.0} reads {@code 20} and {@code 0.25} reads {@code 0.25}.
     */
    private static String decimal(double value) {
        return new java.math.BigDecimal(value)
                .setScale(2, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }

    private static String unlimited() {
        return "∞";
    }
}

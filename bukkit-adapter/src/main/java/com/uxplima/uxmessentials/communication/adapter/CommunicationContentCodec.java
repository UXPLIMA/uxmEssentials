package com.uxplima.uxmessentials.communication.adapter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.uxplima.uxmessentials.communication.domain.AdvancementNoticeConfig;
import com.uxplima.uxmessentials.communication.domain.Announcement;
import com.uxplima.uxmessentials.communication.domain.AnnouncerConfig;
import com.uxplima.uxmessentials.communication.domain.ChatFormatPolicy;
import com.uxplima.uxmessentials.communication.domain.DeathCause;
import com.uxplima.uxmessentials.communication.domain.DeathCausePolicies;
import com.uxplima.uxmessentials.communication.domain.InfoPage;
import com.uxplima.uxmessentials.communication.domain.JoinGroupPolicies;
import com.uxplima.uxmessentials.communication.domain.MessagePolicy;
import com.uxplima.uxmessentials.communication.domain.Ordering;
import com.uxplima.uxmessentials.communication.domain.PolicyMode;
import com.uxplima.uxmessentials.shared.adapter.outbound.hud.ChannelDisplay;
import com.uxplima.uxmessentials.shared.display.BroadcastChannel;
import com.uxplima.uxmessentials.shared.display.ConditionParser;
import com.uxplima.uxmessentials.shared.display.DisplayCondition;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.ConfigurationNode;

/**
 * Parses the merged communication-content Configurate tree into immutable {@link CommunicationContent}. The keys
 * are authored across the {@code join-quit.conf}, {@code announcer.conf}, and {@code info-pages.conf} siblings under
 * {@code modules/communication/} and merged at the root before this reads them, so the combined HOCON shape is
 *
 * <pre>{@code
 * join  { mode = CUSTOM, ordering = SEQUENTIAL, templates = [ "<welcome {player}>" ] }
 * quit  { mode = DEFAULT }
 * death { mode = DISABLE }
 * first-join = "<welcome our newest member {player}!>"   # optional, broadcast on first-ever join
 * death-info-page = "rules"                              # optional, shown to a dying player
 * announcer {
 *   default-interval-seconds = 300, min-players = 1, ordering = RANDOM
 *   announcements = [
 *     { id = "tips", lines = [ "<tip one>" ], channels = [ "CHAT" ], condition = "", sound = "", centered = false }
 *     { id = "vip", lines = [ "<vip tip>" ], channels = [ "TITLE" ], condition = "permission:uxmessentials.vip",
 *       interval-seconds = 600, sound = "entity.player.levelup" }
 *   ]
 * }
 * info-pages { rules = [ "<line one>", "<line two>" ], motd = [ "<welcome>" ] }
 * }</pre>
 *
 * <p>Every value is operator content rendered through MiniMessage later, never a {@code MessageKey}. A
 * {@code mode}/{@code ordering} token is matched case-insensitively and falls back to the inert default when
 * unknown, so a typo degrades to "leave vanilla / sequential" rather than failing the load. A {@code CUSTOM}
 * channel with no templates falls back to {@code DEFAULT} so the domain invariant (a custom policy declares at
 * least one template) is never violated by an empty config block.
 *
 * <p>The announcer parses straight into the rich {@link AnnouncerConfig}: each entry under {@code announcements}
 * becomes one {@link Announcement} carrying its channels, a {@link DisplayCondition} parsed by
 * {@link ConditionParser} (a blank condition is "always", an unparseable one fails safe to "never" per the parser
 * contract), an optional per-announcement interval override, an optional sound key, and a centered flag. Unknown
 * channels are skipped (an empty result defaults to {@code CHAT}). <b>Back-compat:</b> when no {@code announcements}
 * list is present, the legacy flat {@code lines = [ ... ]} (with the legacy {@code interval-seconds}) maps to one
 * unconditional {@code "default"} announcement on the {@code CHAT} channel, so an un-migrated file keeps working.
 */
@NullMarked
final class CommunicationContentCodec {

    private static final long DEFAULT_INTERVAL_SECONDS = 300L;
    private static final int DEFAULT_TITLE_FADE_IN_MS = 500;
    private static final int DEFAULT_TITLE_STAY_MS = 2000;
    private static final int DEFAULT_TITLE_FADE_OUT_MS = 500;
    private static final int DEFAULT_BOSS_BAR_SECONDS = 4;

    private CommunicationContentCodec() {}

    /** Parse {@code root} into content; an empty or virtual root yields fully inert content. */
    static CommunicationContent read(ConfigurationNode root) {
        ConfigurationNode announcer = root.node("announcer");
        return new CommunicationContent(
                joinPolicies(root.node("join")),
                policy(root.node("quit")),
                deathPolicies(root.node("death")),
                announcer(announcer),
                display(announcer.node("display")),
                advancements(root.node("advancements")),
                firstJoin(root.node("first-join")),
                strings(root.node("motd")),
                optionalString(root.node("death-info-page")).map(name -> name.toLowerCase(Locale.ROOT)),
                // Default true so a fresh install, and an upgraded one whose file predates the key, greets joining
                // players with the motd; an operator who wants silence sets motd-on-join = false.
                root.node("motd-on-join").getBoolean(true),
                infoPages(root.node("info-pages")),
                chat(root.node("chat")));
    }

    /**
     * Parse the {@code chat} block into a {@link ChatFormatPolicy}. An absent block (no {@code chat.conf}) yields
     * {@link ChatFormatPolicy#disabled()}, so a server without the file keeps vanilla chat. When present the policy
     * defaults {@code enabled} to {@code false}, the operator opts in explicitly, and falls back to the shipped
     * near-vanilla format when the {@code format} key is blank, so an enabled-but-empty block still renders. The
     * {@code group-formats} map drops blank values, and the two name-decoration keys default to empty (no hover /
     * no click).
     */
    private static ChatFormatPolicy chat(ConfigurationNode node) {
        if (node.virtual()) {
            return ChatFormatPolicy.disabled();
        }
        String format = node.node("format").getString("");
        if (format.isBlank()) {
            format = ChatFormatPolicy.vanillaFormat();
        }
        return new ChatFormatPolicy(
                node.node("enabled").getBoolean(false),
                format,
                stringMap(node.node("group-formats")),
                node.node("allow-player-format").getBoolean(false),
                node.node("name-hover").getString(""),
                node.node("name-click").getString(""));
    }

    /** A key→value string map (e.g. {@code group-formats}); a blank value drops that entry. */
    private static Map<String, String> stringMap(ConfigurationNode node) {
        Map<String, String> map = new LinkedHashMap<>();
        node.childrenMap().forEach((key, child) -> {
            String value = child.getString("");
            if (!value.isBlank()) {
                map.put(String.valueOf(key), value);
            }
        });
        return Map.copyOf(map);
    }

    /**
     * Parse the {@code advancements} block into an {@link AdvancementNoticeConfig}. The feature ships off
     * ({@code enabled = false}); recipes are excluded and only announce-to-chat advancements considered by default,
     * matching vanilla. The allow/deny lists and the per-advancement template map are read leniently: blank entries
     * are dropped, and the domain record lowercases every key. Channels are parsed by the shared {@link #channels}
     * helper (unknown channels skipped, empty defaults to {@code CHAT}); a blank sound key stays absent.
     */
    private static AdvancementNoticeConfig advancements(ConfigurationNode node) {
        if (node.virtual()) {
            return AdvancementNoticeConfig.disabled();
        }
        return new AdvancementNoticeConfig(
                node.node("enabled").getBoolean(false),
                node.node("exclude-recipes").getBoolean(true),
                node.node("only-announceable").getBoolean(true),
                Set.copyOf(strings(node.node("allow"))),
                Set.copyOf(strings(node.node("deny"))),
                node.node("template").getString(""),
                perAdvancementTemplates(node.node("per-advancement")),
                channels(node.node("channels")),
                optionalString(node.node("sound")));
    }

    /** The per-advancement template overrides, a key→template map; a blank template value drops that entry. */
    private static Map<String, String> perAdvancementTemplates(ConfigurationNode node) {
        Map<String, String> overrides = new LinkedHashMap<>();
        node.childrenMap().forEach((key, child) -> {
            String template = child.getString("");
            if (!template.isBlank()) {
                overrides.put(String.valueOf(key), template);
            }
        });
        return Map.copyOf(overrides);
    }

    /*
     * Parse the optional announcer.display block. The title fade/stay/fade timing and the boss-bar colour,
     * overlay, and visible-seconds the TITLE/SUBTITLE/BOSS_BAR channels render with. Every field defaults to
     * the same values the vote broadcaster uses, and colour/overlay names are tolerant of typos.
     */
    /** The default announcer display timing, used for inert content and as each field's fallback. */
    static ChannelDisplay defaultDisplay() {
        return new ChannelDisplay(
                DEFAULT_TITLE_FADE_IN_MS,
                DEFAULT_TITLE_STAY_MS,
                DEFAULT_TITLE_FADE_OUT_MS,
                ChannelDisplay.color("PURPLE"),
                ChannelDisplay.overlay("PROGRESS"),
                DEFAULT_BOSS_BAR_SECONDS);
    }

    private static ChannelDisplay display(ConfigurationNode node) {
        ConfigurationNode title = node.node("title");
        ConfigurationNode bossBar = node.node("boss-bar");
        return new ChannelDisplay(
                Math.max(0, title.node("fade-in-ms").getInt(DEFAULT_TITLE_FADE_IN_MS)),
                Math.max(0, title.node("stay-ms").getInt(DEFAULT_TITLE_STAY_MS)),
                Math.max(0, title.node("fade-out-ms").getInt(DEFAULT_TITLE_FADE_OUT_MS)),
                ChannelDisplay.color(bossBar.node("color").getString("PURPLE")),
                ChannelDisplay.overlay(bossBar.node("overlay").getString("PROGRESS")),
                Math.max(1, bossBar.node("seconds").getInt(DEFAULT_BOSS_BAR_SECONDS)));
    }

    /**
     * Parse the {@code join} block into a {@link JoinGroupPolicies}. The block itself is the default policy (parsed
     * by {@link #policy}), and each entry under a nested {@code groups} map is a per-group override, a bare template
     * list keyed by a permission group name. An empty template list is skipped, so that group falls through to the
     * default; the block's own {@code ordering} governs how a per-group list rotates, keeping a group override
     * consistent with the default. A file with no {@code groups} map yields the single-default table, the
     * pre-per-group behaviour.
     */
    private static JoinGroupPolicies joinPolicies(ConfigurationNode node) {
        MessagePolicy defaultPolicy = policy(node);
        Ordering ordering = enumToken(node.node("ordering").getString(""), Ordering.class, Ordering.SEQUENTIAL);
        Map<String, MessagePolicy> byGroup = new LinkedHashMap<>();
        node.node("groups")
                .childrenMap()
                .forEach((key, child) ->
                        groupOverride(child, ordering).ifPresent(policy -> byGroup.put(String.valueOf(key), policy)));
        return new JoinGroupPolicies(byGroup, defaultPolicy);
    }

    /** A per-group override from a bare template list; empty when no usable template is authored. */
    private static Optional<MessagePolicy> groupOverride(ConfigurationNode node, Ordering ordering) {
        List<String> templates = strings(node);
        return templates.isEmpty() ? Optional.empty() : Optional.of(MessagePolicy.custom(ordering, templates));
    }

    /**
     * Parse the {@code first-join} node into the welcome {@link MessagePolicy} broadcast on a player's first-ever
     * join. A block ({@code first-join { mode = CUSTOM, templates = [ ... ] }}) parses through {@link #policy}; the
     * legacy bare-string form ({@code first-join = "welcome {player}"}) maps to a single-template CUSTOM policy so an
     * un-migrated file keeps working. An absent or blank value yields {@link MessagePolicy#vanilla()}, no welcome, so
     * the join falls through to the ordinary per-group line.
     */
    private static MessagePolicy firstJoin(ConfigurationNode node) {
        if (node.isMap()) {
            return policy(node);
        }
        String legacy = node.getString("");
        return legacy.isBlank() ? MessagePolicy.vanilla() : MessagePolicy.custom(Ordering.SEQUENTIAL, List.of(legacy));
    }

    private static MessagePolicy policy(ConfigurationNode node) {
        PolicyMode mode = enumToken(node.node("mode").getString(""), PolicyMode.class, PolicyMode.DEFAULT);
        if (mode == PolicyMode.DISABLE) {
            return MessagePolicy.disabled();
        }
        List<String> templates = strings(node.node("templates"));
        if (mode != PolicyMode.CUSTOM || templates.isEmpty()) {
            return MessagePolicy.vanilla();
        }
        Ordering ordering = enumToken(node.node("ordering").getString(""), Ordering.class, Ordering.SEQUENTIAL);
        return MessagePolicy.custom(ordering, templates);
    }

    /**
     * Parse the {@code death} block into a {@link DeathCausePolicies}. The block itself is the default policy (its
     * policy mode, ordering, and templates, parsed by {@link #policy}), and each entry under a nested
     * {@code causes} map is a per-cause override: a bare template list keyed by a {@link DeathCause} name. An
     * unknown cause key or an empty template list is skipped, so the cause falls through to the default; the block's
     * own {@code ordering} governs how a per-cause list rotates, keeping a cause override consistent with the
     * default. A file with no {@code causes} map yields the single-default table, the pre-per-cause behaviour.
     */
    private static DeathCausePolicies deathPolicies(ConfigurationNode node) {
        MessagePolicy defaultPolicy = policy(node);
        Ordering ordering = enumToken(node.node("ordering").getString(""), Ordering.class, Ordering.SEQUENTIAL);
        Map<DeathCause, MessagePolicy> byCause = new EnumMap<>(DeathCause.class);
        node.node("causes")
                .childrenMap()
                .forEach((key, child) -> deathCause(String.valueOf(key))
                        .ifPresent(cause ->
                                causeOverride(child, ordering).ifPresent(policy -> byCause.put(cause, policy))));
        return new DeathCausePolicies(byCause, defaultPolicy);
    }

    /** A per-cause override from a bare template list; empty when no usable template is authored. */
    private static Optional<MessagePolicy> causeOverride(ConfigurationNode node, Ordering ordering) {
        List<String> templates = strings(node);
        return templates.isEmpty() ? Optional.empty() : Optional.of(MessagePolicy.custom(ordering, templates));
    }

    /** Match a {@code causes} key to a {@link DeathCause} case-insensitively; an unknown key is dropped. */
    private static Optional<DeathCause> deathCause(String token) {
        if (token.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(DeathCause.valueOf(token.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException unknown) {
            return Optional.empty();
        }
    }

    /**
     * Parse the {@code announcer} node into an {@link AnnouncerConfig}. An {@code announcements} list is the rich
     * form (one {@link Announcement} per entry); its absence falls back to the legacy flat {@code lines} form,
     * which maps to one unconditional {@code "default"} CHAT announcement. An announcer with no usable announcement
     * yields {@link AnnouncerConfig#empty()} so it never fires.
     */
    private static AnnouncerConfig announcer(ConfigurationNode node) {
        Duration defaultInterval = defaultInterval(node);
        int minPlayers = Math.max(0, node.node("min-players").getInt(0));
        Ordering ordering = enumToken(node.node("ordering").getString(""), Ordering.class, Ordering.SEQUENTIAL);
        List<Announcement> announcements = announcements(node);
        if (announcements.isEmpty()) {
            return AnnouncerConfig.empty();
        }
        return new AnnouncerConfig(defaultInterval, minPlayers, ordering, announcements);
    }

    /**
     * The config-wide default interval: the new {@code default-interval-seconds} key, falling back to the legacy
     * {@code interval-seconds} key, then to the built-in default. Clamped to at least one second so the positive
     * interval invariant holds.
     */
    private static Duration defaultInterval(ConfigurationNode node) {
        long legacy = node.node("interval-seconds").getLong(DEFAULT_INTERVAL_SECONDS);
        long seconds = node.node("default-interval-seconds").getLong(legacy);
        return Duration.ofSeconds(Math.max(1L, seconds));
    }

    /**
     * The announcements: the rich {@code announcements} list when present, otherwise the legacy flat {@code lines}
     * bridged to one unconditional CHAT {@code "default"} announcement. An empty list (no lines, no announcements)
     * means the announcer is inert.
     */
    private static List<Announcement> announcements(ConfigurationNode node) {
        ConfigurationNode list = node.node("announcements");
        if (list.isList()) {
            return parseAnnouncements(list);
        }
        List<String> lines = strings(node.node("lines"));
        if (lines.isEmpty()) {
            return List.of();
        }
        return List.of(new Announcement(
                "default",
                lines,
                DisplayCondition.always(),
                Optional.empty(),
                Set.of(BroadcastChannel.CHAT),
                Optional.empty(),
                false));
    }

    private static List<Announcement> parseAnnouncements(ConfigurationNode list) {
        List<Announcement> parsed = new ArrayList<>();
        int index = 0;
        for (ConfigurationNode entry : list.childrenList()) {
            index++;
            Optional<Announcement> announcement = announcement(entry, index);
            announcement.ifPresent(parsed::add);
        }
        return List.copyOf(parsed);
    }

    /**
     * Parse one announcement entry. Lines are required. An entry with no usable line is dropped rather than
     * failing the load. The id defaults to {@code announcement-<n>} when blank; the condition is parsed fail-safe
     * by {@link ConditionParser}; an unknown channel is skipped (an empty set defaults to {@code CHAT} in the
     * domain record); a non-positive interval override is ignored; a blank sound key is left absent.
     */
    private static Optional<Announcement> announcement(ConfigurationNode entry, int index) {
        List<String> lines = strings(entry.node("lines"));
        if (lines.isEmpty()) {
            return Optional.empty();
        }
        String id = entry.node("id").getString("").trim();
        if (id.isEmpty()) {
            id = "announcement-" + index;
        }
        DisplayCondition condition =
                ConditionParser.parse(entry.node("condition").getString(""));
        Set<BroadcastChannel> channels = channels(entry.node("channels"));
        Optional<Duration> override = intervalOverride(entry.node("interval-seconds"));
        Optional<String> sound = optionalString(entry.node("sound"));
        boolean centered = entry.node("centered").getBoolean(false);
        return Optional.of(new Announcement(id, lines, condition, override, channels, sound, centered));
    }

    private static Set<BroadcastChannel> channels(ConfigurationNode node) {
        if (node.virtual() || !node.isList()) {
            return Set.of(BroadcastChannel.CHAT);
        }
        Set<BroadcastChannel> channels = new LinkedHashSet<>();
        for (ConfigurationNode child : node.childrenList()) {
            @Nullable String raw = child.getString();
            if (raw == null || raw.isBlank()) {
                continue;
            }
            try {
                channels.add(BroadcastChannel.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException unknown) {
                // Skip an unknown channel rather than failing the whole announcement; CHAT is the default.
            }
        }
        return channels.isEmpty() ? Set.of(BroadcastChannel.CHAT) : Set.copyOf(channels);
    }

    private static Optional<Duration> intervalOverride(ConfigurationNode node) {
        long seconds = node.getLong(0L);
        return seconds > 0 ? Optional.of(Duration.ofSeconds(seconds)) : Optional.empty();
    }

    private static List<InfoPage> infoPages(ConfigurationNode node) {
        List<InfoPage> pages = new ArrayList<>();
        node.childrenMap()
                .forEach((key, child) -> infoPage(String.valueOf(key), child).ifPresent(pages::add));
        return List.copyOf(pages);
    }

    /**
     * Parse one info-page entry. A bare list ({@code rules = [ "a", "b" ]}) is the body with the default page size;
     * a section ({@code info { lines = [ ... ], page-size = 10 }}) carries an explicit page size. An entry with no
     * body lines is dropped so it registers no command.
     */
    private static Optional<InfoPage> infoPage(String name, ConfigurationNode node) {
        List<String> lines = node.isList() ? strings(node) : strings(node.node("lines"));
        if (lines.isEmpty()) {
            return Optional.empty();
        }
        int pageSize = clampPageSize(node.node("page-size").getInt(InfoPage.DEFAULT_PAGE_SIZE));
        return Optional.of(InfoPage.of(name, lines, pageSize));
    }

    private static int clampPageSize(int requested) {
        if (requested < 1) {
            return 1;
        }
        return Math.min(requested, InfoPage.MAX_PAGE_SIZE);
    }

    private static List<String> strings(ConfigurationNode node) {
        if (node.virtual() || !node.isList()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (ConfigurationNode child : node.childrenList()) {
            String value = child.getString();
            if (value != null && !value.isBlank()) {
                values.add(value);
            }
        }
        return List.copyOf(values);
    }

    private static Optional<String> optionalString(ConfigurationNode node) {
        String value = node.getString("");
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private static <E extends Enum<E>> E enumToken(String token, Class<E> type, E fallback) {
        if (token.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, token.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return fallback;
        }
    }
}

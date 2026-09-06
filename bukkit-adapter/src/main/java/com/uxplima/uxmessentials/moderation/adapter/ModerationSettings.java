package com.uxplima.uxmessentials.moderation.adapter;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.uxplima.uxmessentials.moderation.domain.AddressStrictness;
import com.uxplima.uxmessentials.moderation.domain.PunishmentTemplate;
import com.uxplima.uxmessentials.moderation.domain.SanctionDuration;
import com.uxplima.uxmessentials.moderation.domain.WarnEscalation;
import com.uxplima.uxmessentials.moderation.domain.WarnEscalation.EscalationAction;
import com.uxplima.uxmessentials.moderation.domain.WarnEscalation.EscalationStep;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;

/**
 * Typed view over the {@code moderation.conf} subtree: the configured jails (name → world/coordinates) and
 * the per-jail countdown mode. A jail referenced by {@code /jail <player> <jail>} must exist here, and the
 * countdown mode decides whether a timed sentence is online-only (the default) or wall-clock
 * ({@code jail-countdown}). Read once at wire time from the module's scoped config.
 *
 * <p>Because the config port is flat path-based, the set of jails is a {@code jails} string list and each
 * jail's location lives under {@code jail.<name>.{world,x,y,z}}. A jail with no configured world resolves to
 * empty so the adapter falls back to spawn rather than teleporting into a missing world.
 */
@NullMarked
public final class ModerationSettings {

    /** Chat-adjacent commands a muted player is blocked from running unless an operator overrides the list. */
    private static final List<String> DEFAULT_BLOCKED_COMMANDS =
            List.of("me", "msg", "tell", "w", "whisper", "mail", "helpop", "r", "reply");

    /** The default escalation ladder shipped in the config: tempmute on the 3rd warning, tempban on the 5th. */
    private static final List<String> DEFAULT_ESCALATION = List.of("3:tempmute:1h", "5:tempban:1d");

    private final ConfigStore config;
    private final List<String> jails;
    private final boolean wallClockDefault;
    private final Set<String> mutedBlockedCommands;
    private final WarnEscalation warnEscalation;
    private final boolean silentByDefault;
    private final AddressStrictness addressStrictness;
    private final boolean censorIpAddresses;
    private final Map<String, PunishmentTemplate> templates;
    private final boolean discordNotify;

    public ModerationSettings(ConfigStore config, Logger log) {
        this.config = Objects.requireNonNull(config, "config");
        Objects.requireNonNull(log, "log");
        this.jails = config.getStringList("jails", List.of()).stream()
                .map(name -> name.toLowerCase(Locale.ROOT))
                .toList();
        this.wallClockDefault = "wall-clock".equalsIgnoreCase(config.getString("jail-countdown", "online-only"));
        this.mutedBlockedCommands = config.getStringList("muted-blocked-commands", DEFAULT_BLOCKED_COMMANDS).stream()
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
        this.warnEscalation = parseEscalation(config.getStringList("warnings.actions", DEFAULT_ESCALATION), log);
        this.silentByDefault = config.getBoolean("broadcast.silent-by-default", false);
        this.addressStrictness = AddressStrictness.parse(config.getString("address-strictness", "NORMAL"));
        this.censorIpAddresses = config.getBoolean("censor-ip-addresses", false);
        this.templates = parseTemplates(config, log);
        this.discordNotify = config.getBoolean("discord-notify", false);
    }

    /** The command labels a muted player may not run (the mute-bypass guard list), case-folded. */
    public Set<String> mutedBlockedCommands() {
        return mutedBlockedCommands;
    }

    /** The configured warn-escalation ladder ({@code count:action:duration} rungs), parsed at wire time. */
    public WarnEscalation warnEscalation() {
        return warnEscalation;
    }

    /** Whether a sanction's broadcast is suppressed by default (the {@code -s} flag's default state). */
    public boolean silentByDefault() {
        return silentByDefault;
    }

    /**
     * How far a UUID ban reaches across the target's known addresses. {@code NORMAL} (the default) bans the
     * one account; {@code STRICT} also IP-bans every address the target has connected from (opt-in: it
     * broadens IP retention and catches anyone sharing the connection).
     */
    public AddressStrictness addressStrictness() {
        return addressStrictness;
    }

    /** Whether {@code /alts} and {@code /seenip} mask the addresses they render (privacy; off by default). */
    public boolean censorIpAddresses() {
        return censorIpAddresses;
    }

    /** The configured {@code /punish} templates (name → preset reason + optional duration), parsed at wire time. */
    public Map<String, PunishmentTemplate> templates() {
        return templates;
    }

    /**
     * Whether each successful punishment additionally emits a formatted "who punished whom" line on the shared
     * audit channel the optional Discord bridge forwards. Off by default; a no-op when no bridge is present.
     */
    public boolean discordNotify() {
        return discordNotify;
    }

    /**
     * Parse the {@code templates} block into name → {@link PunishmentTemplate}. A template needs a non-blank
     * {@code reason}; its {@code duration} is optional (omit for a permanent ban) and, when present, must parse
     * to a positive span. A template with a blank reason or a malformed duration is skipped with a log warning
     * rather than failing the whole module, mirroring the warn-escalation ladder.
     */
    private static Map<String, PunishmentTemplate> parseTemplates(ConfigStore config, Logger log) {
        Map<String, PunishmentTemplate> parsed = new LinkedHashMap<>();
        for (String name : config.getKeys("templates")) {
            parseTemplate(config, name, log).ifPresent(template -> parsed.put(name, template));
        }
        return Map.copyOf(parsed);
    }

    private static Optional<PunishmentTemplate> parseTemplate(ConfigStore config, String name, Logger log) {
        String reason = config.getString("templates." + name + ".reason", "").trim();
        if (reason.isBlank()) {
            log.warn("Ignoring punishment template '{}' (missing a reason)", name);
            return Optional.empty();
        }
        String rawDuration =
                config.getString("templates." + name + ".duration", "").trim();
        SanctionDuration.Parsed duration = SanctionDuration.parse(rawDuration);
        if (duration.malformed()) {
            log.warn("Ignoring punishment template '{}' (invalid duration '{}')", name, rawDuration);
            return Optional.empty();
        }
        return Optional.of(new PunishmentTemplate(name, reason, duration.duration()));
    }

    /**
     * Parse each configured {@code count:action:duration} rung into an {@link EscalationStep}, skipping (with a
     * warning) any malformed entry rather than failing the whole module. {@code action} is case-insensitive and
     * one of mute/tempmute/kick/tempban/ban; {@code duration} is required for the timed actions
     * ({@code tempmute}, {@code tempban}) and ignored for the rest.
     */
    private static WarnEscalation parseEscalation(List<String> raw, Logger log) {
        List<EscalationStep> steps = new ArrayList<>();
        for (String entry : raw) {
            parseStep(entry, log).ifPresent(steps::add);
        }
        return steps.isEmpty() ? WarnEscalation.NONE : new WarnEscalation(steps);
    }

    private static Optional<EscalationStep> parseStep(String entry, Logger log) {
        String[] parts = entry.split(":", 3);
        if (parts.length < 2) {
            log.warn("Ignoring malformed warn-escalation rung '{}' (expected count:action[:duration])", entry);
            return Optional.empty();
        }
        Optional<Integer> count = parseCount(parts[0]);
        Optional<EscalationAction> action = parseAction(parts[1]);
        if (count.isEmpty() || action.isEmpty()) {
            log.warn("Ignoring warn-escalation rung '{}' (bad count or action)", entry);
            return Optional.empty();
        }
        boolean timed = action.get() == EscalationAction.TEMPMUTE || action.get() == EscalationAction.TEMPBAN;
        Optional<Duration> duration = parseDuration(parts.length == 3 ? parts[2] : "");
        if (timed && duration.isEmpty()) {
            log.warn("Ignoring warn-escalation rung '{}' ({} needs a duration)", entry, parts[1]);
            return Optional.empty();
        }
        return Optional.of(new EscalationStep(count.get(), action.get(), timed ? duration : Optional.empty()));
    }

    private static Optional<Integer> parseCount(String raw) {
        try {
            int count = Integer.parseInt(raw.trim());
            return count >= 1 ? Optional.of(count) : Optional.empty();
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
    }

    private static Optional<EscalationAction> parseAction(String raw) {
        try {
            return Optional.of(EscalationAction.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException unknownAction) {
            return Optional.empty();
        }
    }

    private static Optional<Duration> parseDuration(String raw) {
        if (raw.isBlank()) {
            return Optional.empty();
        }
        return SanctionDuration.parse(raw.trim()).duration();
    }

    /** The configured jail names, sorted, for {@code /jails} to list (already lower-cased and immutable). */
    public List<String> jailNames() {
        return jails;
    }

    /** True when {@code jail} is a configured jail name. */
    public boolean hasJail(String jail) {
        return jails.contains(jail.toLowerCase(Locale.ROOT));
    }

    /** Whether {@code jail} counts down on wall-clock time (per its own override, else the module default). */
    public boolean isWallClock(String jail) {
        String key = jail.toLowerCase(Locale.ROOT);
        String mode = config.getString("jail." + key + ".countdown", wallClockDefault ? "wall-clock" : "online-only");
        return "wall-clock".equalsIgnoreCase(mode);
    }

    /** The configured location of {@code jail}, or empty when its world is not set. */
    public Optional<JailLocation> location(String jail) {
        String key = jail.toLowerCase(Locale.ROOT);
        String world = config.getString("jail." + key + ".world", "");
        if (world.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new JailLocation(
                world,
                config.getDouble("jail." + key + ".x", 0.0),
                config.getDouble("jail." + key + ".y", 64.0),
                config.getDouble("jail." + key + ".z", 0.0)));
    }

    /** A resolved jail location: the world name and the coordinates to teleport a jailed player to. */
    public record JailLocation(String world, double x, double y, double z) {

        public JailLocation {
            Objects.requireNonNull(world, "world");
        }
    }
}

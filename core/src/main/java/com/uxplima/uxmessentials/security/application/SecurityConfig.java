package com.uxplima.uxmessentials.security.application;

import java.time.Duration;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import com.uxplima.uxmessentials.security.domain.AltLimitPolicy;
import com.uxplima.uxmessentials.security.domain.ClientIdMode;
import com.uxplima.uxmessentials.security.domain.ClientPolicy;
import com.uxplima.uxmessentials.security.domain.FreezeRestriction;
import com.uxplima.uxmessentials.security.domain.LockoutPolicy;
import com.uxplima.uxmessentials.security.domain.PinPolicy;
import com.uxplima.uxmessentials.security.domain.ReauthPolicy;
import com.uxplima.uxmessentials.security.domain.RevokedAccess;
import com.uxplima.uxmessentials.security.domain.SafetyNet;
import com.uxplima.uxmessentials.security.domain.SpectatorPolicy;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;

/**
 * The typed, immutable view of {@code modules/security/config.conf} for Phase 1: the module enable gate and the
 * two-factor tunables (which factors are offered, the authenticator issuer label, the verification window, and the
 * PIN length policy). Resolved once from the module's scoped {@link ConfigStore} on start and, per the atomic-reload
 * rule, swapped whole on reload, so a command dispatched mid-reload sees one coherent snapshot.
 *
 * <p>The HOCON keys are kebab-case under a {@code two-factor { … }} block; the record components are the camelCase
 * views the application reads. Every knob carries the default the bundled config ships, so an operator who deletes a
 * line falls back to the shipped value. The op-protection and IP/alt-guard blocks land with the later phases.
 *
 * @param enabled the module enable gate ({@code enabled}, default {@code true})
 * @param twoFactor the two-factor enrolment settings ({@code two-factor.*})
 * @param joinVerification the join-verification (freeze + keypad) settings ({@code join-verification.*})
 * @param opProtection the op-command re-authentication settings ({@code op-protection.*})
 * @param ipGuard the same-IP alt-detection settings ({@code ip-guard.*})
 * @param clientId the client-brand guard settings ({@code client-id.*})
 */
public record SecurityConfig(
        boolean enabled,
        TwoFactorSettings twoFactor,
        JoinVerification joinVerification,
        OpProtection opProtection,
        IpGuard ipGuard,
        ClientId clientId,
        Feedback feedback) {

    public SecurityConfig {
        Objects.requireNonNull(twoFactor, "twoFactor");
        Objects.requireNonNull(joinVerification, "joinVerification");
        Objects.requireNonNull(opProtection, "opProtection");
        Objects.requireNonNull(ipGuard, "ipGuard");
        Objects.requireNonNull(clientId, "clientId");
        Objects.requireNonNull(feedback, "feedback");
    }

    /** Resolve the security config from the module's scoped {@link ConfigStore} ({@code modules.security}). */
    public static SecurityConfig from(ConfigStore config) {
        Objects.requireNonNull(config, "config");
        return new SecurityConfig(
                config.getBoolean("enabled", true),
                TwoFactorSettings.from(config),
                JoinVerification.from(config),
                OpProtection.from(config),
                IpGuard.from(config),
                ClientId.from(config),
                Feedback.from(config));
    }

    /**
     * The two-factor enrolment tunables ({@code two-factor.*}): the master switch, which of the two factors a player
     * may enrol (TOTP authenticator and/or PIN), the issuer label shown in the authenticator app, the ± time-step
     * tolerance a submitted code is checked within, and the PIN length policy. The numbers are validated here so a
     * nonsensical value never reaches the domain.
     *
     * @param enabled whether two-factor enrolment is offered at all ({@code two-factor.enabled}, default true)
     * @param totp whether the TOTP authenticator factor is offered ({@code two-factor.totp}, default true)
     * @param pin whether the PIN factor is offered ({@code two-factor.pin}, default true)
     * @param issuer the issuer label in the authenticator app ({@code two-factor.issuer}, default "uxmEssentials")
     * @param codeWindow the ± time-steps of tolerance when checking a code ({@code two-factor.code-window}, default 1)
     * @param pinPolicy the PIN policy built from {@code two-factor.pin-min-length}/{@code pin-max-length} and
     *     {@code two-factor.blocked-pins}
     */
    public record TwoFactorSettings(
            boolean enabled, boolean totp, boolean pin, String issuer, int codeWindow, PinPolicy pinPolicy) {

        /** The default authenticator issuer label. */
        private static final String DEFAULT_ISSUER = "uxmEssentials";

        /** The default ± time-step tolerance: one step (±30s) absorbs modest clock skew. */
        private static final int DEFAULT_WINDOW = 1;

        private static final int DEFAULT_PIN_MIN = 6;
        private static final int DEFAULT_PIN_MAX = 8;

        /**
         * The PINs refused out of the box: the runs, the repeats and the two dates everybody picks. Length rules
         * cannot catch these, and they are the whole of a lazy attacker's first guesses.
         */
        private static final List<String> DEFAULT_BLOCKED_PINS = List.of(
                "0000", "1111", "2222", "3333", "4444", "5555", "6666", "7777", "8888", "9999", "1234", "4321", "12345",
                "123456", "654321", "1212", "2580", "0852", "1122", "2000", "1990", "2024", "2025");

        /** The hard ceiling on the accepted window, so a misconfigured value cannot widen the factor open. */
        private static final int MAX_WINDOW = 5;

        public TwoFactorSettings {
            Objects.requireNonNull(issuer, "issuer");
            Objects.requireNonNull(pinPolicy, "pinPolicy");
            if (issuer.isBlank()) {
                throw new IllegalArgumentException("two-factor issuer must not be blank");
            }
            if (codeWindow < 0 || codeWindow > MAX_WINDOW) {
                throw new IllegalArgumentException("two-factor code-window must be between 0 and " + MAX_WINDOW);
            }
        }

        /** Resolve the two-factor settings from the module's scoped {@link ConfigStore} ({@code modules.security}). */
        public static TwoFactorSettings from(ConfigStore config) {
            Objects.requireNonNull(config, "config");
            int min = Math.max(1, config.getInt("two-factor.pin-min-length", DEFAULT_PIN_MIN));
            int max = Math.max(min, config.getInt("two-factor.pin-max-length", DEFAULT_PIN_MAX));
            Set<String> blocked = Set.copyOf(config.getStringList("two-factor.blocked-pins", DEFAULT_BLOCKED_PINS));
            return new TwoFactorSettings(
                    config.getBoolean("two-factor.enabled", true),
                    config.getBoolean("two-factor.totp", true),
                    config.getBoolean("two-factor.pin", true),
                    config.getString("two-factor.issuer", DEFAULT_ISSUER),
                    Math.min(MAX_WINDOW, Math.max(0, config.getInt("two-factor.code-window", DEFAULT_WINDOW))),
                    new PinPolicy(min, max, blocked));
        }
    }

    /**
     * The join-verification tunables ({@code join-verification.*}): whether an enrolled player is frozen and made to
     * prove a factor on join at all, whether a successful verification trusts the device so the next join skips the
     * prompt (and for how long), how many failures before the player is kicked, and how long that kick locks them out.
     * The numbers are validated here so a nonsensical value never reaches the keypad.
     *
     * @param enabled whether the join freeze is active ({@code join-verification.enabled}, default true)
     * @param trustDevices whether a verified device is remembered ({@code join-verification.trust-devices}, default true)
     * @param trustDuration how long a trusted device skips the prompt ({@code join-verification.trust-duration-hours})
     * @param maxAttempts failures allowed before a lockout ({@code join-verification.max-attempts}, default 3)
     * @param lockout how long a locked-out player is kicked for ({@code join-verification.lockout-seconds}, default 300)
     * @param restrictions what a frozen player is stopped from doing ({@code join-verification.restrictions.*})
     * @param spectator what to do with a frozen spectator ({@code join-verification.spectator-mode}, default adventure)
     * @param safetyNet what happens when the decision itself fails ({@code join-verification.safety-net}, default kick)
     * @param entryTimeout how long a frozen player has to verify, zero for no limit
     *     ({@code join-verification.entry-timeout-seconds}, default 60)
     * @param lockoutBans whether a lockout is also a real tempban on the server's own ban list
     *     ({@code join-verification.lockout-bans}, default true)
     * @param lockoutBanReason the reason recorded against that ban ({@code join-verification.lockout-ban-reason})
     * @param holdingArea where a verifying player is held, {@code world,x,y,z[,yaw,pitch]}, blank to leave them put
     *     ({@code join-verification.holding-area})
     * @param transferTo the proxy backend a verified player is sent to, blank to leave them here
     *     ({@code join-verification.transfer-to})
     * @param waitForLoginPlugin whether a detected login plugin's own authentication runs first
     *     ({@code join-verification.wait-for-login-plugin}, default true)
     * @param revokedAccess what happens to an online player whose factor staff have just cleared
     *     ({@code join-verification.on-access-revoked}, default reverify)
     */
    public record JoinVerification(
            boolean enabled,
            boolean trustDevices,
            Duration trustDuration,
            int maxAttempts,
            Duration lockout,
            Set<FreezeRestriction> restrictions,
            SpectatorPolicy spectator,
            SafetyNet safetyNet,
            Duration entryTimeout,
            boolean lockoutBans,
            String lockoutBanReason,
            String holdingArea,
            String transferTo,
            boolean waitForLoginPlugin,
            RevokedAccess revokedAccess) {

        private static final int DEFAULT_TRUST_HOURS = 24;
        private static final int DEFAULT_MAX_ATTEMPTS = 3;
        private static final int DEFAULT_LOCKOUT_SECONDS = 300;
        private static final int DEFAULT_ENTRY_TIMEOUT_SECONDS = 60;
        private static final String DEFAULT_LOCKOUT_REASON = "Too many failed verification attempts";

        public JoinVerification {
            Objects.requireNonNull(trustDuration, "trustDuration");
            Objects.requireNonNull(lockout, "lockout");
            Objects.requireNonNull(restrictions, "restrictions");
            Objects.requireNonNull(spectator, "spectator");
            Objects.requireNonNull(safetyNet, "safetyNet");
            Objects.requireNonNull(entryTimeout, "entryTimeout");
            Objects.requireNonNull(lockoutBanReason, "lockoutBanReason");
            Objects.requireNonNull(holdingArea, "holdingArea");
            Objects.requireNonNull(transferTo, "transferTo");
            Objects.requireNonNull(revokedAccess, "revokedAccess");
            if (entryTimeout.isNegative()) {
                throw new IllegalArgumentException("join-verification entry-timeout must not be negative");
            }
            if (maxAttempts < 1) {
                throw new IllegalArgumentException("join-verification max-attempts must be at least 1: " + maxAttempts);
            }
            if (trustDuration.isNegative()) {
                throw new IllegalArgumentException("join-verification trust-duration must not be negative");
            }
            if (lockout.isNegative()) {
                throw new IllegalArgumentException("join-verification lockout must not be negative");
            }
            restrictions =
                    restrictions.isEmpty() ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(restrictions));
        }

        /** Resolve the join-verification settings from the module's scoped {@link ConfigStore}. */
        public static JoinVerification from(ConfigStore config) {
            Objects.requireNonNull(config, "config");
            int trustHours = Math.max(0, config.getInt("join-verification.trust-duration-hours", DEFAULT_TRUST_HOURS));
            int maxAttempts = Math.max(1, config.getInt("join-verification.max-attempts", DEFAULT_MAX_ATTEMPTS));
            int lockoutSeconds =
                    Math.max(0, config.getInt("join-verification.lockout-seconds", DEFAULT_LOCKOUT_SECONDS));
            return new JoinVerification(
                    config.getBoolean("join-verification.enabled", true),
                    config.getBoolean("join-verification.trust-devices", true),
                    Duration.ofHours(trustHours),
                    maxAttempts,
                    Duration.ofSeconds(lockoutSeconds),
                    restrictionsFrom(config),
                    SpectatorPolicy.parse(config.getString("join-verification.spectator-mode", "adventure")),
                    SafetyNet.parse(config.getString("join-verification.safety-net", "kick")),
                    Duration.ofSeconds(Math.max(
                            0,
                            config.getInt("join-verification.entry-timeout-seconds", DEFAULT_ENTRY_TIMEOUT_SECONDS))),
                    config.getBoolean("join-verification.lockout-bans", true),
                    config.getString("join-verification.lockout-ban-reason", DEFAULT_LOCKOUT_REASON),
                    config.getString("join-verification.holding-area", ""),
                    config.getString("join-verification.transfer-to", ""),
                    config.getBoolean("join-verification.wait-for-login-plugin", true),
                    RevokedAccess.parse(config.getString("join-verification.on-access-revoked", "reverify")));
        }

        /** Whether a frozen player is kicked after a time limit rather than left at the keypad indefinitely. */
        public boolean hasEntryTimeout() {
            return !entryTimeout.isZero();
        }

        /** Whether a verifying player is moved to a holding area for the length of the freeze. */
        public boolean hasHoldingArea() {
            return !holdingArea.isBlank();
        }

        /** Whether a verified player is handed to another backend rather than left where they logged in. */
        public boolean hasTransferTarget() {
            return !transferTo.isBlank();
        }

        /** Whether a frozen player is stopped from {@code restriction}. */
        public boolean restricts(FreezeRestriction restriction) {
            return restrictions.contains(Objects.requireNonNull(restriction, "restriction"));
        }

        /**
         * Every restriction whose {@code restrictions.<key>} flag is on. All of them ship on, so an operator opts
         * out one line at a time rather than having to list the whole set to get the safe default.
         */
        private static Set<FreezeRestriction> restrictionsFrom(ConfigStore config) {
            EnumSet<FreezeRestriction> on = EnumSet.noneOf(FreezeRestriction.class);
            for (FreezeRestriction restriction : FreezeRestriction.values()) {
                if (config.getBoolean("join-verification.restrictions." + restriction.configKey(), true)) {
                    on.add(restriction);
                }
            }
            return on;
        }

        /** The pure lockout decision this config drives: reused by the keypad to judge a failed attempt. */
        public LockoutPolicy lockoutPolicy() {
            return new LockoutPolicy(maxAttempts);
        }
    }

    /**
     * The op-command protection tunables ({@code op-protection.*}): whether dangerous commands are gated at all, the
     * list of command roots that demand a recent second-factor proof, and how long a verification counts as recent.
     * Only players who have enrolled a factor are gated, a re-auth needs something to prove against, and a holder of
     * {@code uxmessentials.security.bypass} is exempt. The command list is matched leniently (leading slash, namespace
     * prefix and arguments are ignored) so an operator listing {@code op} catches {@code /op Steve} and
     * {@code minecraft:op} alike.
     *
     * @param enabled whether op-command protection is active ({@code op-protection.enabled}, default true)
     * @param protectedCommands the command roots that demand a recent verification ({@code op-protection.protected-commands})
     * @param reauthWindow how long a verification counts as recent ({@code op-protection.reauth-window-seconds}, default 60)
     */
    public record OpProtection(boolean enabled, List<String> protectedCommands, Duration reauthWindow) {

        /** The default re-auth window: a verification in the last minute counts as recent. */
        private static final int DEFAULT_WINDOW_SECONDS = 60;

        /** The default protected set: the op/dangerous vanilla commands an operator most wants behind a re-auth. */
        private static final List<String> DEFAULT_COMMANDS = List.of(
                "op", "deop", "stop", "restart", "reload", "gamemode", "ban", "ban-ip", "pardon", "kick", "whitelist");

        public OpProtection {
            Objects.requireNonNull(protectedCommands, "protectedCommands");
            Objects.requireNonNull(reauthWindow, "reauthWindow");
            if (reauthWindow.isNegative()) {
                throw new IllegalArgumentException("op-protection reauth-window must not be negative: " + reauthWindow);
            }
            protectedCommands = List.copyOf(protectedCommands);
        }

        /** Resolve the op-protection settings from the module's scoped {@link ConfigStore}. */
        public static OpProtection from(ConfigStore config) {
            Objects.requireNonNull(config, "config");
            int windowSeconds =
                    Math.max(0, config.getInt("op-protection.reauth-window-seconds", DEFAULT_WINDOW_SECONDS));
            return new OpProtection(
                    config.getBoolean("op-protection.enabled", true),
                    config.getStringList("op-protection.protected-commands", DEFAULT_COMMANDS),
                    Duration.ofSeconds(windowSeconds));
        }

        /** The pure re-auth decision this config drives: the guard feeds it each protected command and the last verify. */
        public ReauthPolicy policy() {
            return new ReauthPolicy(Set.copyOf(protectedCommands), reauthWindow);
        }
    }

    /**
     * The IP/alt-guard tunables ({@code ip-guard.*}): whether the guard records a joining player's (hashed) IP and
     * checks for alts at all, the greatest number of distinct accounts allowed on one IP (0 = no cap, only observe),
     * and whether staff are notified when a joining player shares an IP with other accounts. No GeoIP, the guard
     * only ever holds one-way IP tokens.
     *
     * @param enabled whether the IP/alt guard is active ({@code ip-guard.enabled}, default true)
     * @param maxAccountsPerIp the same-IP account cap, 0 for no cap ({@code ip-guard.max-accounts-per-ip}, default 0)
     * @param notifyStaff whether staff are told when a join shares an IP ({@code ip-guard.notify-staff}, default true)
     */
    public record IpGuard(boolean enabled, int maxAccountsPerIp, boolean notifyStaff) {

        private static final int DEFAULT_MAX_ACCOUNTS = 0;

        /** Resolve the IP/alt-guard settings from the module's scoped {@link ConfigStore}. */
        public static IpGuard from(ConfigStore config) {
            Objects.requireNonNull(config, "config");
            return new IpGuard(
                    config.getBoolean("ip-guard.enabled", true),
                    Math.max(0, config.getInt("ip-guard.max-accounts-per-ip", DEFAULT_MAX_ACCOUNTS)),
                    config.getBoolean("ip-guard.notify-staff", true));
        }

        /** The pure same-IP account-cap decision this config drives. */
        public AltLimitPolicy limitPolicy() {
            return new AltLimitPolicy(maxAccountsPerIp);
        }
    }

    /**
     * How verification talks back to the player beyond chat ({@code feedback.*}): the title over the keypad and the
     * sound each step makes. Chat is a poor channel here, because the player has a window open and their chat is
     * scrolled away behind it, so the title says where they are already looking why the server is not responding.
     *
     * <p>Every sound is a namespaced key rather than an enum constant, so a resource-pack sound works as well as a
     * vanilla one and neither a rename between game versions nor an operator's typo can stop the module loading: an
     * unusable key plays nothing and logs a line. A blank key is an explicit "play nothing here".
     *
     * @param titles whether the keypad and outcome titles are shown ({@code feedback.titles}, default true)
     * @param promptSound played when the keypad opens ({@code feedback.sounds.prompt})
     * @param keySound played on each digit tap ({@code feedback.sounds.key})
     * @param successSound played when a proof is accepted ({@code feedback.sounds.success})
     * @param failureSound played when a proof is rejected ({@code feedback.sounds.failure})
     */
    public record Feedback(
            boolean titles, String promptSound, String keySound, String successSound, String failureSound) {

        private static final String DEFAULT_PROMPT = "block.note_block.pling";
        private static final String DEFAULT_KEY = "ui.button.click";
        private static final String DEFAULT_SUCCESS = "entity.player.levelup";
        private static final String DEFAULT_FAILURE = "entity.villager.no";

        public Feedback {
            Objects.requireNonNull(promptSound, "promptSound");
            Objects.requireNonNull(keySound, "keySound");
            Objects.requireNonNull(successSound, "successSound");
            Objects.requireNonNull(failureSound, "failureSound");
        }

        /** Resolve the feedback settings from the module's scoped {@link ConfigStore}. */
        public static Feedback from(ConfigStore config) {
            Objects.requireNonNull(config, "config");
            return new Feedback(
                    config.getBoolean("feedback.titles", true),
                    config.getString("feedback.sounds.prompt", DEFAULT_PROMPT),
                    config.getString("feedback.sounds.key", DEFAULT_KEY),
                    config.getString("feedback.sounds.success", DEFAULT_SUCCESS),
                    config.getString("feedback.sounds.failure", DEFAULT_FAILURE));
        }
    }

    /**
     * The client-brand-guard tunables ({@code client-id.*}): whether the guard reads a joining player's client brand
     * at all, which side of the brand list is the allowed side (or the flag-only observe mode), and the brand list
     * itself. The mode string is parsed leniently and falls back to {@link ClientIdMode#FLAG}, the safe observe
     * mode that never kicks, so a typo never starts denying joins.
     *
     * @param enabled whether the client-brand guard is active ({@code client-id.enabled}, default true)
     * @param mode which side of {@code brands} is allowed ({@code client-id.mode}: block-list|allow-list|flag)
     * @param brands the configured brand list ({@code client-id.brands}, default empty)
     */
    public record ClientId(boolean enabled, ClientIdMode mode, List<String> brands) {

        public ClientId {
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(brands, "brands");
            brands = List.copyOf(brands);
        }

        /** Resolve the client-brand-guard settings from the module's scoped {@link ConfigStore}. */
        public static ClientId from(ConfigStore config) {
            Objects.requireNonNull(config, "config");
            return new ClientId(
                    config.getBoolean("client-id.enabled", true),
                    parseMode(config.getString("client-id.mode", "flag")),
                    config.getStringList("client-id.brands", List.of()));
        }

        /** The pure allow/deny decision this config drives, feeding it each joiner's reported brand. */
        public ClientPolicy policy() {
            return new ClientPolicy(mode, Set.copyOf(brands));
        }

        private static ClientIdMode parseMode(String raw) {
            return switch (raw.strip().toLowerCase(Locale.ROOT)) {
                case "block-list", "blocklist", "block" -> ClientIdMode.BLOCK_LIST;
                case "allow-list", "allowlist", "allow", "whitelist" -> ClientIdMode.ALLOW_LIST;
                default -> ClientIdMode.FLAG;
            };
        }
    }
}

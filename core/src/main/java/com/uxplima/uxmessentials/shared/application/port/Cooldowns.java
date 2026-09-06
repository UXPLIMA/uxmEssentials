package com.uxplima.uxmessentials.shared.application.port;

import java.time.Duration;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;
import org.jspecify.annotations.Nullable;

/**
 * Outbound port for per-player cooldown gates. One cooldown is a "ready-at" timestamp stamped per
 * holder; the adapter keeps the transient stamp in PDC under a pre-created key. The quota (how long a
 * cooldown lasts, whether it can be bypassed) is resolved from numbered permission nodes via the
 * {@link Permissions} port, using the {@code min}-reducer: the lowest matching
 * {@code uxmessentials.<feature>.cooldown.<seconds>} wins, {@code 0} means no wait, and
 * {@code uxmessentials.<feature>.cooldown.bypass} skips the gate entirely.
 *
 * <p>Two keying styles share these mechanics. A {@link CooldownKind} keys a feature's tiered cooldown
 * (teleport, warp, kit), carrying the feature segment, the config default, and, for teleport, the
 * {@link CooldownStartPhase} that decides when the clock starts. The {@link #checkLabel}/
 * {@link #stampLabel} pair keys the generic per-command cooldown by an operator-chosen command label
 * or rule id, gated by {@code uxmessentials.cooldown.bypass.<label>} with the same min-reducer
 * semantics.
 */
public interface Cooldowns {

    /** Gate {@code who} for {@code kind}; ok when ready, else the remaining {@link Duration}. */
    Result<Unit, Duration> check(PlayerRef who, CooldownKind kind);

    /** Start the cooldown clock for {@code who} on {@code kind}, sized by the resolved quota. */
    void stamp(PlayerRef who, CooldownKind kind);

    /** Gate {@code who} for the generic per-command cooldown keyed by {@code label}. */
    Result<Unit, Duration> checkLabel(PlayerRef who, String label);

    /** Start the generic per-command cooldown clock for {@code who} keyed by {@code label}. */
    void stampLabel(PlayerRef who, String label);

    /**
     * Start the generic per-command cooldown clock keyed by {@code label}, using {@code configDefault} as the wait
     * when the player holds no matching {@code uxmessentials.cooldown.<label>.<seconds>} node. A custom command
     * declares its own cooldown in its file, and this is how that declared value reaches the stamp; the
     * label-only form keeps meaning "whatever the permission nodes say, else no wait".
     */
    default void stampLabel(PlayerRef who, String label, Duration configDefault) {
        stampLabel(who, label);
    }

    /**
     * When a teleport cooldown begins, configured per {@code teleport.conf} (default
     * {@link #TELEPORT}). Choosing {@link #TELEPORT} or {@link #ACCEPT} means a denied, expired, or
     * self-cancelled request never burns the requester's cooldown. A subtlety most plugins get
     * wrong. The same enum applies to {@code /warp}, {@code /home}, {@code /rtp}, and any cooldowned
     * teleport.
     */
    enum CooldownStartPhase {
        /** The clock starts when {@code /tpa} is issued. */
        REQUEST,
        /** The clock starts when the target accepts. */
        ACCEPT,
        /** The clock starts only when the player actually arrives, the safe default. */
        TELEPORT
    }

    /**
     * A tiered cooldown's identity: the feature segment used to build the permission nodes, the
     * {@code stampScope} the per-holder stamp is keyed under, the config-default duration in seconds
     * when the player holds no matching tier node, and the {@link CooldownStartPhase} that decides when
     * the clock starts.
     *
     * <p>The {@code feature} and {@code stampScope} are separate so a family of related cooldowns can
     * share one tier node space while each keeps its own independent stamp. {@code /kit} uses this: every
     * kit resolves its wait against the shared {@code uxmessentials.kit.cooldown.<seconds>} tier
     * ({@code feature = "kit"}), but each kit stamps under its own per-id scope ({@code stampScope =
     * "kit." + id}) so claiming one kit does not start another's cooldown. The teleport and warp kinds set
     * {@code stampScope} equal to {@code feature}, one tier, one stamp.
     *
     * @param feature the node segment, e.g. {@code tp} → {@code uxmessentials.tp.cooldown.<seconds>}
     * @param stampScope the per-holder stamp key; defaults to {@code feature} for a single-stamp kind
     * @param defaultSeconds the config fallback in seconds when no tier node matches
     * @param startPhase when the cooldown clock starts for this kind
     */
    record CooldownKind(String feature, String stampScope, long defaultSeconds, CooldownStartPhase startPhase) {

        public CooldownKind {
            if (feature == null || feature.isBlank()) {
                throw new IllegalArgumentException("feature must be non-blank");
            }
            if (stampScope == null || stampScope.isBlank()) {
                throw new IllegalArgumentException("stampScope must be non-blank");
            }
            if (defaultSeconds < 0) {
                throw new IllegalArgumentException("defaultSeconds must be >= 0: " + defaultSeconds);
            }
            Objects.requireNonNull(startPhase, "startPhase");
        }

        /** A kind whose stamp is keyed by its own feature, the single-tier, single-stamp form. */
        public CooldownKind(String feature, long defaultSeconds, CooldownStartPhase startPhase) {
            this(feature, feature, defaultSeconds, startPhase);
        }

        /** A cooldown that starts on arrival, the canonical teleport default. */
        public static CooldownKind onTeleport(String feature, long defaultSeconds) {
            return new CooldownKind(feature, defaultSeconds, CooldownStartPhase.TELEPORT);
        }

        /**
         * A cooldown sharing the {@code feature} tier node space but stamped under a distinct
         * {@code stampScope}, so related instances rate-limit independently. {@code /kit} keys each kit
         * here: {@code scoped("kit", "kit." + id, seconds, TELEPORT)}.
         */
        public static CooldownKind scoped(
                String feature, String stampScope, long defaultSeconds, CooldownStartPhase startPhase) {
            return new CooldownKind(feature, stampScope, defaultSeconds, startPhase);
        }

        /** The permission node prefix this kind resolves its tier against. */
        public String cooldownNode() {
            return "uxmessentials." + feature + ".cooldown";
        }

        /** The bypass node that skips the gate entirely. */
        public String bypassNode() {
            return cooldownNode() + ".bypass";
        }

        /** The default duration as a {@link Duration}. */
        public Duration defaultDuration() {
            return Duration.ofSeconds(defaultSeconds);
        }

        /** A copy of this kind with a different start phase, for operator-driven reconfiguration. */
        public CooldownKind withStartPhase(@Nullable CooldownStartPhase phase) {
            return new CooldownKind(feature, stampScope, defaultSeconds, phase == null ? startPhase : phase);
        }
    }
}

package com.uxplima.uxmessentials.playerstate.application.port;

import com.uxplima.uxmessentials.playerstate.domain.AirAmount;
import com.uxplima.uxmessentials.playerstate.domain.BurnDuration;
import com.uxplima.uxmessentials.playerstate.domain.ExperienceChange;
import com.uxplima.uxmessentials.playerstate.domain.FoodLevel;
import com.uxplima.uxmessentials.playerstate.domain.FreezeDuration;
import com.uxplima.uxmessentials.playerstate.domain.GlowColor;
import com.uxplima.uxmessentials.playerstate.domain.HealthLevel;
import com.uxplima.uxmessentials.playerstate.domain.PersonalTime;
import com.uxplima.uxmessentials.playerstate.domain.PersonalWeather;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * Outbound port for the apply-once and live-only effects that carry no persisted snapshot flag, heal, feed,
 * extinguish, suicide, night-vision, glow, and the per-player time/weather overrides. The adapter resolves the live
 * {@code Player} and performs each on the player's owning region/entity thread via the {@code Scheduler}
 * port; an offline target is a silent no-op.
 *
 * <p>These are split from {@link StateReconciler} because they act on the live player without changing the
 * persisted {@link com.uxplima.uxmessentials.playerstate.domain.PlayerStateSnapshot}: healing a player does
 * not toggle a flag, and the night-vision/ptime/pweather overrides are client-side presentation the context
 * does not re-derive from a snapshot.
 */
public interface PlayerEffects {

    /** Restore {@code who}'s health to full and, when {@code clearEffects}, remove active potion effects. */
    void heal(PlayerRef who, boolean clearEffects);

    /** Restore {@code who}'s food level and saturation to full. */
    void feed(PlayerRef who);

    /** Put out {@code who} if they are on fire. */
    void extinguish(PlayerRef who);

    /** Empty {@code who}'s inventory (main, armor and offhand). */
    void clearInventory(PlayerRef who);

    /** Kill {@code who} (the {@code /suicide} self-kill). */
    void kill(PlayerRef who);

    /** Toggle a permanent night-vision effect on {@code who}; returns the resulting on/off state. */
    boolean toggleNightVision(PlayerRef who);

    /**
     * Toggle a permanent glowing outline on {@code who}; returns the resulting on/off state. Turning the outline off
     * also drops any colour {@link #setGlow} gave it, so a re-toggle starts from the uncoloured outline again.
     */
    boolean toggleGlow(PlayerRef who);

    /**
     * Turn the glowing outline on for {@code who} and draw it in {@code colour} ({@link GlowColor#DEFAULT} leaves the
     * uncoloured white outline). Unlike {@link #toggleGlow} this is idempotent: a player who already glows keeps
     * glowing and only changes colour.
     */
    void setGlow(PlayerRef who, GlowColor colour);

    /** Apply {@code who}'s personal client-side time, or reset it when {@code time.reset()}. */
    void applyTime(PlayerRef who, PersonalTime time);

    /** Apply {@code who}'s personal client-side weather, or reset it for {@code PersonalWeather#RESET}. */
    void applyWeather(PlayerRef who, PersonalWeather weather);

    /**
     * Apply an experience {@code change} to {@code who}, reading the current total and computing the clamped
     * non-negative target on the player's owning thread, then reporting the resulting level and point total
     * back through {@code onResult}. {@code onResult} runs on the same thread as the mutation; an offline
     * target neither mutates nor reports.
     */
    void applyExperience(
            PlayerRef who, ExperienceChange change, java.util.function.Consumer<ExperienceReport> onResult);

    /** Set {@code who}'s remaining air to {@code air} (clamped to their maximum at construction). */
    void setRemainingAir(PlayerRef who, AirAmount air);

    /** Set {@code who} on fire for {@code duration} (or put them out when the duration is zero). */
    void setFire(PlayerRef who, BurnDuration duration);

    /** Apply the powder-snow freeze to {@code who} for {@code duration} (or thaw them when it is zero). */
    void setFreeze(PlayerRef who, FreezeDuration duration);

    /** Set {@code who}'s food level to {@code food} (clamped to {@code 0..20} in the domain). */
    void setFoodLevel(PlayerRef who, FoodLevel food);

    /** Set {@code who}'s health to {@code value}, clamped to their live maximum in the adapter; {@code 0} kills. */
    void setHealth(PlayerRef who, HealthLevel value);

    /** Reset {@code who}'s time-since-rest statistic so accumulated phantom pressure clears. */
    void resetRest(PlayerRef who);

    /**
     * A snapshot of a player's experience after an {@code /exp} operation: the level and the absolute
     * experience-point total, used to render the confirmation.
     *
     * @param level the player's experience level
     * @param totalPoints the player's absolute experience-point total
     */
    record ExperienceReport(int level, int totalPoints) {}
}

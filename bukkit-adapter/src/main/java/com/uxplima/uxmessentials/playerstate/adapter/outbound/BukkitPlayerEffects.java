package com.uxplima.uxmessentials.playerstate.adapter.outbound;

import java.util.Objects;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.WeatherType;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.uxplima.uxmessentials.playerstate.application.port.PlayerEffects;
import com.uxplima.uxmessentials.playerstate.domain.AirAmount;
import com.uxplima.uxmessentials.playerstate.domain.BurnDuration;
import com.uxplima.uxmessentials.playerstate.domain.ExperienceChange;
import com.uxplima.uxmessentials.playerstate.domain.FoodLevel;
import com.uxplima.uxmessentials.playerstate.domain.FreezeDuration;
import com.uxplima.uxmessentials.playerstate.domain.GlowColor;
import com.uxplima.uxmessentials.playerstate.domain.HealthLevel;
import com.uxplima.uxmessentials.playerstate.domain.PersonalTime;
import com.uxplima.uxmessentials.playerstate.domain.PersonalWeather;
import com.uxplima.uxmessentials.shared.adapter.outbound.team.PlayerTeamCoordinator;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link PlayerEffects} implementation: the apply-once and live-only verbs that act on the live player
 * without changing the persisted snapshot, heal, feed, extinguish, suicide, night-vision, glow, and the
 * per-player time/weather overrides. Each resolves the live {@code Player} and runs on the player's owning
 * region/entity thread through the injected {@link Scheduler} port; an offline target is a silent no-op.
 *
 * <p>{@link #toggleNightVision} and {@link #toggleGlow} report the resulting on/off state synchronously off the
 * snapshot the call captures so the use case can render the right confirmation, while the effect mutation itself
 * is hopped to the entity thread.
 *
 * <p>The outline's colour is not an entity property: vanilla draws it in the colour of the player's scoreboard team, so
 * {@link #setGlow} hands the colour to the shared {@link PlayerTeamCoordinator}, which owns team membership for the
 * nametags and scoreboard contexts too.
 */
@NullMarked
public final class BukkitPlayerEffects implements PlayerEffects {

    private static final long INFINITE_TICKS = -1L;

    private final Scheduler scheduler;
    private final PlayerTeamCoordinator teams;

    public BukkitPlayerEffects(Scheduler scheduler, PlayerTeamCoordinator teams) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.teams = Objects.requireNonNull(teams, "teams");
    }

    @Override
    public void heal(PlayerRef who, boolean clearEffects) {
        onEntity(who, player -> {
            double max = maxHealth(player);
            player.setHealth(max);
            player.setFireTicks(0);
            if (clearEffects) {
                player.getActivePotionEffects().forEach(effect -> player.removePotionEffect(effect.getType()));
            }
        });
    }

    @Override
    public void feed(PlayerRef who) {
        onEntity(who, player -> {
            player.setFoodLevel(20);
            player.setSaturation(20.0f);
            player.setExhaustion(0.0f);
        });
    }

    @Override
    public void extinguish(PlayerRef who) {
        onEntity(who, player -> player.setFireTicks(0));
    }

    @Override
    public void clearInventory(PlayerRef who) {
        onEntity(who, player -> player.getInventory().clear());
    }

    @Override
    public void kill(PlayerRef who) {
        onEntity(who, player -> player.setHealth(0.0));
    }

    @Override
    public boolean toggleNightVision(PlayerRef who) {
        Player snapshot = Bukkit.getPlayer(who.uuid());
        boolean willEnable = snapshot == null || !snapshot.hasPotionEffect(PotionEffectType.NIGHT_VISION);
        onEntity(who, player -> {
            if (player.hasPotionEffect(PotionEffectType.NIGHT_VISION)) {
                player.removePotionEffect(PotionEffectType.NIGHT_VISION);
            } else {
                player.addPotionEffect(
                        new PotionEffect(PotionEffectType.NIGHT_VISION, (int) INFINITE_TICKS, 0, false, false));
            }
        });
        return willEnable;
    }

    @Override
    public boolean toggleGlow(PlayerRef who) {
        Player snapshot = Bukkit.getPlayer(who.uuid());
        boolean willEnable = snapshot == null || !snapshot.isGlowing();
        onEntity(who, player -> {
            boolean glowing = !player.isGlowing();
            player.setGlowing(glowing);
            // An outline that goes out takes its colour with it, so the next bare /glow starts from the plain white
            // one rather than a colour the player set an hour ago and has forgotten about.
            if (!glowing) {
                teams.colour(player, GlowColor.DEFAULT);
            }
        });
        return willEnable;
    }

    @Override
    public void setGlow(PlayerRef who, GlowColor colour) {
        onEntity(who, player -> {
            player.setGlowing(true);
            teams.colour(player, colour);
        });
    }

    @Override
    public void applyTime(PlayerRef who, PersonalTime time) {
        onEntity(who, player -> {
            if (time.reset()) {
                player.resetPlayerTime();
            } else {
                player.setPlayerTime(time.ticks(), false);
            }
        });
    }

    @Override
    public void applyWeather(PlayerRef who, PersonalWeather weather) {
        onEntity(who, player -> {
            switch (weather) {
                case CLEAR -> player.setPlayerWeather(WeatherType.CLEAR);
                case RAIN -> player.setPlayerWeather(WeatherType.DOWNFALL);
                case RESET -> player.resetPlayerWeather();
            }
        });
    }

    @Override
    public void applyExperience(PlayerRef who, ExperienceChange change, Consumer<ExperienceReport> onResult) {
        Objects.requireNonNull(change, "change");
        Objects.requireNonNull(onResult, "onResult");
        onEntity(who, player -> {
            mutateExperience(player, change);
            onResult.accept(new ExperienceReport(player.getLevel(), totalExperience(player)));
        });
    }

    @Override
    public void setRemainingAir(PlayerRef who, AirAmount air) {
        Objects.requireNonNull(air, "air");
        onEntity(
                who,
                player -> player.setRemainingAir(
                        AirAmount.ofTicks(air.ticks(), player.getMaximumAir()).ticks()));
    }

    @Override
    public void setFire(PlayerRef who, BurnDuration duration) {
        Objects.requireNonNull(duration, "duration");
        onEntity(who, player -> player.setFireTicks(duration.ticks()));
    }

    @Override
    public void setFreeze(PlayerRef who, FreezeDuration duration) {
        Objects.requireNonNull(duration, "duration");
        // Cap the requested ticks at the player's max freeze ticks so the powder-snow shiver tops out at the
        // full frozen visual rather than rolling over silently when an operator asks for a long freeze.
        onEntity(who, player -> player.setFreezeTicks(Math.min(duration.ticks(), player.getMaxFreezeTicks())));
    }

    @Override
    public void setFoodLevel(PlayerRef who, FoodLevel food) {
        Objects.requireNonNull(food, "food");
        onEntity(who, player -> player.setFoodLevel(food.value()));
    }

    @Override
    public void setHealth(PlayerRef who, HealthLevel value) {
        Objects.requireNonNull(value, "value");
        onEntity(who, player -> player.setHealth(Math.min(value.value(), maxHealth(player))));
    }

    @Override
    public void resetRest(PlayerRef who) {
        onEntity(who, player -> player.setStatistic(Statistic.TIME_SINCE_REST, 0));
    }

    private static void mutateExperience(Player player, ExperienceChange change) {
        if (!change.mutates()) {
            return;
        }
        if (change.unit() == ExperienceChange.Unit.LEVELS) {
            int target = (int) Math.min(Integer.MAX_VALUE, change.resolve(player.getLevel()));
            player.setLevel(target);
            return;
        }
        int target = (int) Math.min(Integer.MAX_VALUE, change.resolve(totalExperience(player)));
        player.setExp(0.0f);
        player.setLevel(0);
        player.setTotalExperience(0);
        player.giveExp(target);
    }

    private static int totalExperience(Player player) {
        return Math.max(0, player.calculateTotalExperiencePoints());
    }

    private void onEntity(PlayerRef who, Consumer<Player> action) {
        Objects.requireNonNull(who, "who");
        scheduler.onEntity(who, () -> {
            Player player = Bukkit.getPlayer(who.uuid());
            if (player != null && player.isOnline()) {
                action.accept(player);
            }
        });
    }

    private static double maxHealth(Player player) {
        var attribute = player.getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? 20.0 : attribute.getValue();
    }
}

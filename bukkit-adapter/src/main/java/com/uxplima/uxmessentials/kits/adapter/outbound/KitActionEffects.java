package com.uxplima.uxmessentials.kits.adapter.outbound;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRegistryKeys;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Builds and applies the Bukkit/Adventure side of each kit {@link com.uxplima.uxmessentials.kits.domain.KitAction}
 * the {@link BukkitKitActionRunner} sequences. The one place the kit action engine touches sounds, titles,
 * particles, fireworks, and MiniMessage. Each spec is parsed defensively: a malformed value logs a one-line
 * warning through {@link Logger} and the effect is skipped, never thrown, so a typo on the claim path is
 * harmless. Operator-authored text (message/actionbar/title/broadcast) is MiniMessage, the same config-data
 * category as the per-kit display overrides.
 */
@NullMarked
final class KitActionEffects {

    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Logger log;

    KitActionEffects(Logger log) {
        this.log = Objects.requireNonNull(log, "log");
    }

    void message(Player player, String text) {
        player.sendMessage(miniMessage.deserialize(text));
    }

    void actionBar(Player player, String text) {
        player.sendActionBar(miniMessage.deserialize(text));
    }

    void broadcast(String text) {
        org.bukkit.Bukkit.getServer().broadcast(miniMessage.deserialize(text));
    }

    /** Empty the recipient's inventory; the {@code clear-inventory} action's effect (it carries no value). */
    void clearInventory(Player player) {
        player.getInventory().clear();
    }

    /** Show a title; spec is {@code fadeIn;stay;fadeOut;<title>;<subtitle>} in ticks, missing parts default safely. */
    void title(Player player, String spec, String kitId) {
        String[] parts = spec.split(";", 5);
        if (parts.length < 4) {
            log.warn("kit " + kitId + ": malformed title spec (need fadeIn;stay;fadeOut;title[;subtitle]): " + spec);
            return;
        }
        long fadeIn = parseLong(parts[0], 0L);
        long stay = parseLong(parts[1], 40L);
        long fadeOut = parseLong(parts[2], 10L);
        Component main = miniMessage.deserialize(parts[3]);
        Component sub = parts.length == 5 ? miniMessage.deserialize(parts[4]) : Component.empty();
        Title.Times times = Title.Times.times(ticks(fadeIn), ticks(stay), ticks(fadeOut));
        player.showTitle(Title.title(main, sub, times));
    }

    /** Play a sound; spec is {@code KEY;volume;pitch}, volume/pitch optional (default 1.0). */
    void sound(Player player, String spec, String kitId) {
        List<String> parts = com.google.common.base.Splitter.on(';').splitToList(spec);
        Sound sound = resolveSound(parts.get(0).strip());
        if (sound == null) {
            log.warn("kit " + kitId + ": unknown sound key: " + parts.get(0));
            return;
        }
        float volume = parts.size() > 1 ? parseFloat(parts.get(1), 1.0f) : 1.0f;
        float pitch = parts.size() > 2 ? parseFloat(parts.get(2), 1.0f) : 1.0f;
        player.playSound(Objects.requireNonNull(player.getLocation(), "player location"), sound, volume, pitch);
    }

    /** Spawn a particle; spec is a single particle key (legacy {@code particles} target), e.g. {@code FLAME}. */
    void particle(Player player, String spec, String kitId) {
        Particle particle = resolveParticle(spec.strip());
        if (particle == null) {
            log.warn("kit " + kitId + ": unknown particle key: " + spec);
            return;
        }
        Location at = Objects.requireNonNull(player.getLocation(), "player location")
                .clone()
                .add(0, 1, 0);
        player.getWorld().spawnParticle(particle, at, 15, 0.3, 0.3, 0.3, 0.1);
    }

    /** Launch a firework; spec is {@code colors:RED,BLUE type:BALL_LARGE power:1}, each token optional. */
    void firework(Player player, String spec, String kitId) {
        FireworkSpec parsed = parseFirework(spec, kitId);
        Location at = Objects.requireNonNull(player.getLocation(), "player location");
        Firework firework = player.getWorld().spawn(at, Firework.class);
        FireworkMeta meta = firework.getFireworkMeta();
        meta.addEffect(FireworkEffect.builder()
                .with(parsed.type())
                .withColor(parsed.colors())
                .build());
        meta.setPower(parsed.power());
        firework.setFireworkMeta(meta);
    }

    private FireworkSpec parseFirework(String spec, String kitId) {
        List<Color> colors = new ArrayList<>();
        FireworkEffect.Type type = FireworkEffect.Type.BALL;
        int power = 1;
        for (String token :
                com.google.common.base.Splitter.on(' ').omitEmptyStrings().split(spec)) {
            String trimmed = token.strip();
            if (trimmed.startsWith("colors:")) {
                colors.addAll(parseColors(trimmed.substring("colors:".length()), kitId));
            } else if (trimmed.startsWith("type:")) {
                type = parseFireworkType(trimmed.substring("type:".length()), kitId);
            } else if (trimmed.startsWith("power:")) {
                power = (int) parseLong(trimmed.substring("power:".length()), 1L);
            }
        }
        if (colors.isEmpty()) {
            colors.add(Color.WHITE);
        }
        return new FireworkSpec(List.copyOf(colors), type, Math.max(0, Math.min(power, 4)));
    }

    private List<Color> parseColors(String csv, String kitId) {
        List<Color> colors = new ArrayList<>();
        for (String name :
                com.google.common.base.Splitter.on(',').omitEmptyStrings().split(csv)) {
            DyeColor dye = parseDye(name.strip());
            if (dye == null) {
                log.warn("kit " + kitId + ": unknown firework color: " + name);
            } else {
                colors.add(dye.getFireworkColor());
            }
        }
        return colors;
    }

    private FireworkEffect.Type parseFireworkType(String raw, String kitId) {
        try {
            return FireworkEffect.Type.valueOf(raw.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            log.warn("kit " + kitId + ": unknown firework type, defaulting to BALL: " + raw);
            return FireworkEffect.Type.BALL;
        }
    }

    private static @Nullable DyeColor parseDye(String name) {
        try {
            return DyeColor.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    private static @Nullable Sound resolveSound(String raw) {
        return BukkitRegistryKeys.resolveSound(raw);
    }

    private static @Nullable Particle resolveParticle(String raw) {
        return BukkitRegistryKeys.resolveParticle(raw);
    }

    private static Duration ticks(long count) {
        return Duration.ofMillis(Math.max(0L, count) * 50L);
    }

    private static long parseLong(String raw, long fallback) {
        try {
            return Long.parseLong(raw.strip());
        } catch (NumberFormatException notANumber) {
            return fallback;
        }
    }

    private static float parseFloat(String raw, float fallback) {
        try {
            return Float.parseFloat(raw.strip());
        } catch (NumberFormatException notANumber) {
            return fallback;
        }
    }

    private record FireworkSpec(List<Color> colors, FireworkEffect.Type type, int power) {}
}

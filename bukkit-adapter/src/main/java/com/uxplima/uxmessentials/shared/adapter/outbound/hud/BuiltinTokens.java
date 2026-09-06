package com.uxplima.uxmessentials.shared.adapter.outbound.hud;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.Objects;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.jspecify.annotations.NullMarked;

/**
 * Resolves a handful of built-in {@code {token}} placeholders against the live {@link Player} and server, so the
 * shipped scoreboard sidebar and tablist show real values out of the box <em>without</em> PlaceholderAPI installed.
 * Applied in the renderers, where the live player is in hand, as a distinct pass before the PlaceholderAPI bridge,
 * so a {@code {token}} and a {@code %papi%} placeholder never collide: this pass only ever rewrites the curly-brace
 * tokens it knows, leaving every {@code %…%} (and unknown {@code {…}}) untouched for PlaceholderAPI or MiniMessage.
 *
 * <p>This is the same convenience syntax the tablist name-format already used for {@code {player}}; that ad-hoc
 * substitution now routes through here so every rendered HUD source. Scoreboard title and lines, tablist header,
 * footer, and name-format, resolves the same token set consistently.
 *
 * <p>Alongside the per-viewer tokens it also resolves a handful of server-wide metric tokens, {@code {tps}},
 * {@code {tps_colored}}, {@code {uptime}}, {@code {ram_used}}, {@code {ram_max}}, which ignore the viewer. These
 * mirror the {@code %uxmessentials_server_*%} PlaceholderAPI placeholders but work with no PlaceholderAPI installed,
 * which is the point of the built-in pass: a HUD header can show live TPS and memory out of the box.
 *
 * <p>Each token is substituted only when present in the source (a cheap {@code contains} check), so a source carrying
 * no token returns unchanged with no allocation.
 */
@NullMarked
public final class BuiltinTokens {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final long BYTES_PER_MB = 1024L * 1024L;

    private BuiltinTokens() {}

    /** Replace every known built-in {@code {token}} in {@code source} with its live value for {@code player}. */
    public static String apply(Player player, String source) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(source, "source");
        if (source.indexOf('{') < 0) {
            return source; // no token can be present, so nothing to do
        }
        String out = source;
        out = replace(out, "{player}", player::getName);
        out = replace(out, "{displayname}", () -> displayName(player));
        // The {online} token reads only the roster size, never a player's mutable entity state. This pass runs in
        // the scoreboard/tablist renderers, which already hold the live viewer on its own region thread, and on the
        // PlaceholderAPI bridge thread for the metric tokens. Neither is the global thread, but a global marshal is
        // both unnecessary (a torn count during a join/quit self-corrects on the next HUD refresh) and unsafe off a
        // region tick. So read the count inline rather than hop.
        out = replace(
                out,
                "{online}",
                () -> Integer.toString(Bukkit.getOnlinePlayers().size()));
        out = replace(out, "{max_players}", () -> Integer.toString(Bukkit.getMaxPlayers()));
        out = replace(out, "{world}", () -> player.getWorld().getName());
        out = replace(out, "{ping}", () -> Integer.toString(player.getPing()));
        // Server-wide metric tokens: the viewer is irrelevant, they read the live server and JVM.
        out = replace(out, "{tps}", BuiltinTokens::tps);
        out = replace(out, "{tps_colored}", BuiltinTokens::tpsColored);
        out = replace(out, "{uptime}", BuiltinTokens::uptime);
        out = replace(out, "{ram_used}", () -> Long.toString(usedMemoryMb()));
        out = replace(out, "{ram_max}", () -> Long.toString(Runtime.getRuntime().maxMemory() / BYTES_PER_MB));
        return out;
    }

    /** The 1-minute tick rate, clamped to the 20.0 ceiling and trimmed to one decimal place. */
    private static String tps() {
        return formatTps(oneMinuteTps());
    }

    /** The 1-minute tick rate wrapped in a MiniMessage colour: green at or above 18, yellow at 15+, red below. */
    private static String tpsColored() {
        double value = oneMinuteTps();
        String colour = value >= 18.0 ? "green" : value >= 15.0 ? "yellow" : "red";
        return "<" + colour + ">" + formatTps(value) + "</" + colour + ">";
    }

    private static double oneMinuteTps() {
        double[] live = Bukkit.getTPS();
        return live.length > 0 ? Math.min(20.0, live[0]) : 20.0;
    }

    /**
     * A tick rate rounded to two decimal places with trailing zeros stripped, so 20.0 reads {@code 20} and
     * 19.97 reads {@code 19.97}, matching the {@code %uxmessentials_server_tps%} rendering.
     */
    private static String formatTps(double value) {
        return new java.math.BigDecimal(value)
                .setScale(2, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString();
    }

    /** Process uptime in the compact {@code 1h30m} form, read off the JVM runtime bean. */
    private static String uptime() {
        return compact(Duration.ofMillis(ManagementFactory.getRuntimeMXBean().getUptime()));
    }

    private static long usedMemoryMb() {
        Runtime runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory()) / BYTES_PER_MB;
    }

    /** {@code duration} as {@code 1d2h3m4s}, trimming zero units; {@code 0s} when non-positive. */
    private static String compact(Duration duration) {
        long total = Math.max(0, duration.toSeconds());
        if (total == 0) {
            return "0s";
        }
        StringBuilder out = new StringBuilder();
        appendUnit(out, total / 86_400, 'd');
        appendUnit(out, total % 86_400 / 3_600, 'h');
        appendUnit(out, total % 3_600 / 60, 'm');
        appendUnit(out, total % 60, 's');
        return out.toString();
    }

    private static void appendUnit(StringBuilder out, long value, char unit) {
        if (value > 0) {
            out.append(value).append(unit);
        }
    }

    /** Substitute {@code token} with the value the supplier yields, only when the token is present. */
    private static String replace(String source, String token, java.util.function.Supplier<String> value) {
        if (!source.contains(token)) {
            return source;
        }
        return source.replace(token, value.get());
    }

    /** The player's display name flattened to plain text, falling back to the account name. */
    private static String displayName(Player player) {
        String plain = PLAIN.serialize(player.displayName());
        return plain.isEmpty() ? player.getName() : plain;
    }
}

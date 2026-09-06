package com.uxplima.uxmessentials.communication.adapter.inbound.listener;

import java.util.Locale;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.communication.domain.DeathCause;
import com.uxplima.uxmessentials.communication.domain.PlaceholderBindings;
import org.jspecify.annotations.NullMarked;

/**
 * Builds the {@link PlaceholderBindings} a connection template is substituted against from a live Bukkit player
 * and event, on the player's region thread where the event fires. The tokens are runtime content: the player's
 * name and display name, the online count, the world. Never plugin {@code MessageKey} strings, so nothing here
 * is parity-checked.
 *
 * <p>The display name is flattened to plain text so a substituted {@code {displayname}} cannot smuggle a
 * MiniMessage tag the operator did not author into the template the adapter parses; the bare {@code {player}}
 * carries the account name. The translation from a live {@code Player} to tokens lives here so the listener stays
 * focused on the policy decision.
 */
@NullMarked
final class ConnectionPlaceholders {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private ConnectionPlaceholders() {}

    /** Tokens for a join or quit: {@code player}, {@code displayname}, {@code count}, {@code world}. */
    static PlaceholderBindings connection(Player player, int onlineCount) {
        return PlaceholderBindings.empty()
                .with("player", player.getName())
                .with("displayname", PLAIN.serialize(player.displayName()))
                .with("count", Integer.toString(onlineCount))
                .with("world", player.getWorld().getName());
    }

    /**
     * Tokens for a death: the connection tokens plus the rendered vanilla death line as {@code message}, the
     * killer's name as {@code killer}, the killer's weapon as {@code killer_weapon}, and the classified death
     * {@code cause} as its lowercased key ({@code fall}, {@code pvp}, {@code lava}, …) so a template can read the
     * cause even under the default policy. {@code killer} and {@code killer_weapon} are the empty string when no
     * player dealt the blow / no weapon applies, so neither token ever renders raw in a template.
     */
    static PlaceholderBindings death(
            Player player, int onlineCount, DeathCause cause, String deathMessage, String killer, String killerWeapon) {
        return connection(player, onlineCount)
                .with("message", deathMessage)
                .with("killer", killer)
                .with("killer_weapon", killerWeapon)
                .with("cause", cause.name().toLowerCase(Locale.ROOT));
    }
}

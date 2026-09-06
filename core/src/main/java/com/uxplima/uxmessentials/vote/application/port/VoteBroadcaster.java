package com.uxplima.uxmessentials.vote.application.port;

import java.util.Map;
import java.util.Set;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.display.BroadcastChannel;

/**
 * Outbound port for fanning one of the plugin's own {@link MessageKey} strings out to every receiving
 * online player across a set of {@link BroadcastChannel surfaces}. The application has already decided
 * <em>whether</em> to broadcast (see {@link com.uxplima.uxmessentials.vote.application.BroadcastDecision});
 * this port owns the <em>how</em>: the recipient loop, the per-player opt-out check, resolving the message
 * in each viewer's locale, delivering it on each requested channel, and any broadcast sound.
 *
 * <p>Like {@link com.uxplima.uxmessentials.shared.application.message.Notifier} this carries only key content
 * the plugin's parity-checked strings, never operator-authored vote-link or reward text. The adapter maps
 * the live players and channels to the Bukkit/Adventure API at the boundary.
 */
public interface VoteBroadcaster {

    /**
     * Resolve {@code key} with {@code placeholders} for each receiving online player and deliver it on
     * every channel in {@code channels}. A no-op when {@code channels} is empty.
     *
     * @param key the message to broadcast
     * @param placeholders the placeholder values to substitute when resolving the message
     * @param channels the surfaces to deliver the message on
     */
    void broadcast(MessageKey key, Map<String, String> placeholders, Set<BroadcastChannel> channels);
}

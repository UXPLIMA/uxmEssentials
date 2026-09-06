package com.uxplima.uxmessentials.moderation.adapter.inbound.listener;

import java.util.Map;
import java.util.Objects;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.moderation.application.ModerationMessageKey;
import com.uxplima.uxmessentials.moderation.application.port.CommandSpyStore;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Powers {@code /commandspy}: when a player runs a command, every staff member currently spying (and not the
 * one running it) is shown a spy line naming the runner and the command. Read-only. The dispatch is never
 * cancelled and the event is observed, not consumed.
 *
 * <p>Runs at {@link EventPriority#MONITOR} with {@code ignoreCancelled = true} so a command another listener
 * already cancelled (a muted-command block, a frozen player) is never echoed to spies. When no staff are
 * spying the listener short-circuits before resolving the runner, so an idle server pays nothing.
 *
 * <p>The runner's name and the command they typed are player-controlled and flow into a MiniMessage template
 * downstream, so both are run through {@link MiniMessage#escapeTags} here before substitution, otherwise a
 * player could type a command containing MiniMessage markup (including a clickable {@code run_command}) and
 * have it rendered, and clicked, in staff chat.
 */
@NullMarked
public final class CommandSpyListener implements Listener {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private final CommandSpyStore store;
    private final Messages messages;
    private final MessageSink sink;

    public CommandSpyListener(CommandSpyStore store, Messages messages, MessageSink sink) {
        this.store = Objects.requireNonNull(store, "store");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        var spies = store.activeSpies();
        if (spies.isEmpty()) {
            return;
        }
        Player runner = event.getPlayer();
        PlayerRef runnerRef = BukkitRefs.toRef(runner);
        Map<String, String> placeholders = Map.of(
                "player", MINI_MESSAGE.escapeTags(runner.getName()),
                "command", MINI_MESSAGE.escapeTags(event.getMessage()));
        for (PlayerRef spy : spies) {
            if (!spy.uuid().equals(runnerRef.uuid())) {
                sink.deliver(spy, messages.resolve(spy, ModerationMessageKey.COMMANDSPY_OBSERVED, placeholders));
            }
        }
    }
}

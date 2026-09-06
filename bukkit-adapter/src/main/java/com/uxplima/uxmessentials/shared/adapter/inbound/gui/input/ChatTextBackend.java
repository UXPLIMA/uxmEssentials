package com.uxplima.uxmessentials.shared.adapter.inbound.gui.input;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import io.papermc.paper.event.player.AsyncChatEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The chat backend of the text-input seam: one {@link AsyncChatEvent} listener that captures a player's next chat line
 * and reports it as the input. This single shared listener replaces the per-context chat-prompt listeners that used to
 * be copied into kits, warps, and the three economy flows: there is now exactly one place a chat line becomes input.
 *
 * <p>A pending prompt carries a deadline so an unanswered prompt expires instead of swallowing a chat line minutes
 * later; registering a second prompt for the same player replaces the first (the overlap guard). The captured line is
 * handed to the outcome on the async chat thread, the seam ({@link TextInput}) hops it onto the viewer's region thread
 * before the call site's callback runs, so the call site never has to. {@code Cancelled} is never produced here: a
 * chat prompt has no structural cancel; typing a cancel keyword is detected upstream by {@link TextInput} from the
 * submitted text. Install once on enable and {@link #uninstall()} on disable so a disabled plugin holds no listener.
 */
@NullMarked
final class ChatTextBackend implements TextInputBackend, Listener {

    private static final Duration PROMPT_EXPIRY = Duration.ofMinutes(2);

    private final Plugin plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    ChatTextBackend(Plugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    /** Register the chat listener. Call once, on enable. */
    void install() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /** Unregister the listener and drop any pending prompts. Call on disable; mirrors {@link #install()}. */
    void uninstall() {
        HandlerList.unregisterAll(this);
        pending.clear();
    }

    @Override
    public void open(
            Player player,
            PlayerRef viewer,
            Component prompt,
            @Nullable String initialText,
            Consumer<InputResult> outcome) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(outcome, "outcome");
        // The chat field cannot be pre-seeded, so initialText is intentionally ignored here.
        pending.put(player.getUniqueId(), new Pending(outcome, System.currentTimeMillis() + PROMPT_EXPIRY.toMillis()));
        player.sendMessage(prompt);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Pending entry = pending.remove(event.getPlayer().getUniqueId());
        if (entry == null) {
            return;
        }
        if (System.currentTimeMillis() > entry.deadline()) {
            return; // the prompt expired, let this line post as a normal chat message rather than swallow it
        }
        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText()
                .serialize(event.message())
                .trim();
        entry.outcome().accept(new InputResult.Submitted(text));
    }

    /** Drop a player's pending prompt when they leave, so a quit mid-prompt never lingers until the deadline. */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }

    private record Pending(Consumer<InputResult> outcome, long deadline) {}
}

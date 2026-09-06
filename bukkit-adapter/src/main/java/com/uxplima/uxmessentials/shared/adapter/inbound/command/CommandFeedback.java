package com.uxplima.uxmessentials.shared.adapter.inbound.command;

import java.util.Map;
import java.util.Objects;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyleTags;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Renders a catalog template straight to a {@link CommandSender} at the command boundary, supplying the
 * shared {@code <prefix>} tag the {@link com.uxplima.uxmessentials.shared.application.port.MessageSink}
 * supplies on the delivery path. Command classes use this for the synchronous rejections they answer in
 * line, the players-only / unknown-player / no-permission failures and the console-side replies, which
 * the entity-scheduled sink cannot carry because the recipient may be the console or an offline target.
 *
 * <p>The {@code <prefix>} value is itself a catalog entry, so it is resolved in the recipient's locale and
 * folded in as a parsed {@link Placeholder}; a template that does not reference {@code <prefix>} is rendered
 * unchanged. Resolution and parsing happen once per call on the calling (command) thread, which is the tick
 * thread for a synchronous command, so there is no region hop to make.
 */
@NullMarked
public final class CommandFeedback {

    /** The catalog key for the shared chat prefix the {@code <prefix>} tag expands to. */
    private static final MessageKey PREFIX = () -> "prefix";

    private final Messages messages;
    private final MiniMessage miniMessage;

    public CommandFeedback(Messages messages) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.miniMessage = MiniMessage.miniMessage();
    }

    /** Resolve {@code key} for {@code sender} with no placeholders and send it with the prefix folded in. */
    public void send(CommandSender sender, MessageKey key) {
        send(sender, key, Map.of());
    }

    /** Resolve {@code key} for {@code sender} with {@code placeholders} and send it with the prefix folded in. */
    public void send(CommandSender sender, MessageKey key, Map<String, String> placeholders) {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(placeholders, "placeholders");
        PlayerRef viewer = refOf(sender);
        String rendered = messages.resolve(viewer, key, placeholders);
        sender.sendMessage(parse(viewer, rendered));
    }

    /** Send an already-resolved catalog source string to {@code sender}, folding the prefix in. */
    public void sendRendered(CommandSender sender, PlayerRef viewer, String rendered) {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(rendered, "rendered");
        sender.sendMessage(parse(viewer, rendered));
    }

    private Component parse(PlayerRef viewer, String rendered) {
        TagResolver prefix = Placeholder.parsed("prefix", messages.resolve(viewer, PREFIX, Map.of()));
        return miniMessage.deserialize(rendered, prefix, StyleTags.resolver());
    }

    /**
     * The locale-bearing {@link PlayerRef} for {@code sender}: the player's own ref, or a synthetic
     * system ref (the reserved system uuid carrying the sender name) for a non-player. Exposed so a caller that
     * pre-resolves a message fragment for inlining still resolves it in the sender's locale.
     */
    public static PlayerRef refOf(CommandSender sender) {
        return sender instanceof Player player ? BukkitRefs.toRef(player) : PlayerRef.system(sender.getName());
    }
}

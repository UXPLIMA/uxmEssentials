package com.uxplima.uxmessentials.communication.adapter.inbound.listener;

import java.util.Objects;
import java.util.function.Supplier;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import io.papermc.paper.event.player.AsyncChatEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import com.uxplima.uxmessentials.communication.adapter.outbound.ChatMeta;
import com.uxplima.uxmessentials.communication.adapter.outbound.ChatMetaSource;
import com.uxplima.uxmessentials.communication.adapter.outbound.ChatPlaceholderExpander;
import com.uxplima.uxmessentials.communication.domain.ChatFormatPolicy;
import com.uxplima.uxmessentials.communication.domain.LegacyChatCodes;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmlib.text.Text;
import org.jspecify.annotations.NullMarked;

/**
 * Renders public chat through the operator's {@link ChatFormatPolicy}: when the policy is enabled it installs a
 * {@code ChatRenderer} on the {@link AsyncChatEvent} that builds the line from the speaker's LuckPerms prefix,
 * suffix, and primary group (a per-group format overrides the default), expands the {@code <prefix>},
 * {@code <suffix>}, {@code <display_name>}, {@code <name>}, {@code <world>}, and {@code <message>} placeholders,
 * and parses the whole format as MiniMessage. The speaker's own message is inserted as plain text unless the
 * policy allows player formatting <em>and</em> the speaker holds {@code uxmessentials.communication.chat.format},
 * in which case the message is parsed as MiniMessage too.
 *
 * <p>Before the format is captured it is run through the {@link ChatPlaceholderExpander}, so an operator's
 * PlaceholderAPI {@code %token%} placeholders in the format expand for the speaker when PlaceholderAPI is
 * installed (a no-op leaving {@code %…%} untouched otherwise). The speaker's typed message is never PlaceholderAPI
 * expanded, only the operator-authored format is, so a player cannot smuggle a placeholder through their message.
 * The prefix and suffix are parsed as MiniMessage, except that a prefix/suffix written with legacy colour codes
 * (a {@code &c} or section-sign sequence, detected by {@link LegacyChatCodes}) is deserialized through Adventure's
 * legacy serializer instead, so a permission plugin that reports a legacy prefix still renders in colour.
 *
 * <p>Runs at {@link EventPriority#NORMAL}, before {@link ChatLockListener} at {@code HIGH}: a locked chat is
 * cancelled there and a cancelled event never reaches the renderer, so installing a renderer here can never
 * override the lock. {@code ignoreCancelled = true} also skips a message an earlier listener already cancelled.
 *
 * <p>{@code AsyncChatEvent} fires off the main thread. This listener only reads the speaker's cached permission
 * meta, a permission flag, and the speaker's name/world (plain field reads) before capturing them into an
 * immutable {@link Rendering} snapshot; the installed renderer then assembles the component from that snapshot and
 * the event-supplied display-name/message components, touching no Bukkit API, so the async-listener contract
 * holds.
 */
@NullMarked
public final class ChatFormatListener implements Listener {

    private static final String FORMAT_PERMISSION = "uxmessentials.communication.chat.format";

    private final Supplier<ChatFormatPolicy> policy;
    private final ChatMetaSource metaSource;
    private final ChatPlaceholderExpander expander;

    public ChatFormatListener(
            Supplier<ChatFormatPolicy> policy, ChatMetaSource metaSource, ChatPlaceholderExpander expander) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.metaSource = Objects.requireNonNull(metaSource, "metaSource");
        this.expander = Objects.requireNonNull(expander, "expander");
    }

    /** Install the format renderer for this line unless chat formatting is disabled. */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        ChatFormatPolicy current = policy.get();
        if (!current.enabled()) {
            return;
        }
        Player speaker = event.getPlayer();
        ChatMeta meta = metaSource.metaFor(speaker.getUniqueId());
        // Expand the operator format's PlaceholderAPI %tokens% for the speaker before capture; a no-op without PAPI.
        String format = expander.expand(
                speaker.getUniqueId(), current.formatFor(meta.primaryGroup().orElse(null)));
        boolean parseMessage = current.allowPlayerFormat() && speaker.hasPermission(FORMAT_PERMISSION);
        // Name and world are plain field reads on the async chat thread: no scheduling, no foreign-region state.
        Rendering rendering = new Rendering(
                format,
                meta.prefix(),
                meta.suffix(),
                speaker.getName(),
                speaker.getWorld().getName(),
                parseMessage,
                current.nameHover(),
                current.nameClick());
        event.renderer((source, sourceDisplayName, message, viewer) -> rendering.render(sourceDisplayName, message));
    }

    /**
     * The immutable snapshot the renderer assembles a line from. Captured on the listener thread so the renderer
     * (which the server may invoke once per viewer) reads no live Bukkit state: only the event-supplied
     * display-name and message components plus these captured strings.
     */
    private record Rendering(
            String format,
            String prefix,
            String suffix,
            String name,
            String world,
            boolean parseMessage,
            String nameHover,
            String nameClick) {

        Component render(Component displayName, Component message) {
            String raw = Text.plain(message);
            TagResolver messageTag = parseMessage ? Text.parsed("message", raw) : Text.placeholder("message", raw);
            return StyledText.render(
                    format,
                    affixTag("prefix", prefix),
                    affixTag("suffix", suffix),
                    Text.component("display_name", decorate(displayName)),
                    Text.placeholder("name", name),
                    Text.placeholder("world", world),
                    messageTag);
        }

        /**
         * The {@code <prefix>}/{@code <suffix>} resolver for {@code affix}: a legacy-coded affix (a {@code &c} or
         * section-sign sequence) is deserialized through Adventure's legacy serializer and inserted as a component,
         * every other affix is parsed as MiniMessage. Deciding once keeps a legacy prefix from rendering literally
         * without ever double-parsing a MiniMessage one.
         */
        private static TagResolver affixTag(String key, String affix) {
            if (LegacyChatCodes.containsLegacyCodes(affix)) {
                return Text.component(key, legacy(affix));
            }
            return Text.parsed(key, affix);
        }

        /** Deserialize a legacy-coded affix, using the section-sign serializer when it carries section codes else ampersand. */
        private static Component legacy(String affix) {
            LegacyComponentSerializer serializer = affix.indexOf(LegacyComponentSerializer.SECTION_CHAR) >= 0
                    ? LegacyComponentSerializer.legacySection()
                    : LegacyComponentSerializer.legacyAmpersand();
            return serializer.deserialize(affix);
        }

        private Component decorate(Component displayName) {
            Component result = displayName;
            if (!nameHover.isBlank()) {
                result = result.hoverEvent(
                        HoverEvent.showText(StyledText.render(nameHover, Text.placeholder("name", name))));
            }
            if (!nameClick.isBlank()) {
                result = result.clickEvent(ClickEvent.suggestCommand(nameClick.replace("{name}", name)));
            }
            return result;
        }
    }
}

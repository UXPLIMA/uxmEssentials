package com.uxplima.uxmessentials.shared.adapter.outbound.sink;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyleTags;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The {@link MessageSink} implementation: parse an already-resolved MiniMessage source string into a
 * {@code Component} exactly once, then deliver it to the viewer on the viewer's region thread via the
 * injected {@link Scheduler}. The shared {@code <prefix>} tag (which catalog templates reference but
 * never inline) is supplied here as a parsed resolver. A system viewer is delivered to the console on the global
 * region thread; a missing or offline player viewer is a silent no-op (the entity scheduler refuses a despawned
 * entity).
 *
 * <p>A single {@link MiniMessage} instance handles parsing; production does not split trusted /
 * untrusted at this layer (docs/03-paper-api §4.1). All templates are operator-owned catalog
 * content.
 *
 * <p>Before MiniMessage parses, the source runs through a per-viewer pre-parse transform, the {@code
 * preParse} factory. The default factory yields the identity transform; the PlaceholderAPI integration
 * supplies one that expands {@code %papi%} placeholders against the viewer, so operator-authored catalog
 * content (announcer lines, join messages) may embed third-party placeholders. The transform runs on the
 * caller's thread before the entity hop, and degrades to the identity when PlaceholderAPI is absent, so it
 * never touches the domain and is a no-op without the soft-depend.
 */
@NullMarked
public final class BukkitMessageSink implements MessageSink {

    private final Scheduler scheduler;
    private final MiniMessage miniMessage;
    private final String prefixTemplate;
    private final Function<UUID, UnaryOperator<String>> preParse;

    /** The pre-parse transform factory that performs no expansion, the no-PlaceholderAPI default. */
    public static final Function<UUID, UnaryOperator<String>> NO_PRE_PARSE = uuid -> UnaryOperator.identity();

    public BukkitMessageSink(Scheduler scheduler, String prefixTemplate) {
        this(scheduler, prefixTemplate, NO_PRE_PARSE);
    }

    public BukkitMessageSink(
            Scheduler scheduler, String prefixTemplate, Function<UUID, UnaryOperator<String>> preParse) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.prefixTemplate = Objects.requireNonNull(prefixTemplate, "prefixTemplate");
        this.preParse = Objects.requireNonNull(preParse, "preParse");
        this.miniMessage = MiniMessage.miniMessage();
    }

    @Override
    public void deliver(PlayerRef viewer, String renderedText) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(renderedText, "renderedText");
        String expanded = preParse.apply(viewer.uuid()).apply(renderedText);
        TagResolver prefix = Placeholder.parsed("prefix", prefixTemplate);
        Component component = miniMessage.deserialize(expanded, prefix, StyleTags.resolver());
        if (viewer.isSystem()) {
            scheduler.onGlobal(() -> Bukkit.getConsoleSender().sendMessage(component));
            return;
        }
        scheduler.onEntity(viewer, () -> sendTo(viewer, component));
    }

    private void sendTo(PlayerRef viewer, Component component) {
        Player player = Bukkit.getPlayer(viewer.uuid());
        if (player != null) {
            player.sendMessage(component);
        }
    }
}

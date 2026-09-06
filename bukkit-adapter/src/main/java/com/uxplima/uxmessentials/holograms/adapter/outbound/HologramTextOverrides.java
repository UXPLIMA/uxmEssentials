package com.uxplima.uxmessentials.holograms.adapter.outbound;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramLine;
import com.uxplima.uxmessentials.holograms.domain.HologramType;
import com.uxplima.uxmessentials.shared.adapter.outbound.papi.PlaceholderApiSupport;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmlib.packet.display.DisplayTextPackets;
import org.jspecify.annotations.NullMarked;

/**
 * Resolves a text hologram's lines <em>per viewer</em> and sends each viewer a text-override packet, so a
 * hologram whose lines embed a PlaceholderAPI token renders that viewer's own placeholder values while staying a
 * single shared {@code TextDisplay}. The approach: one real shared entity, plus a
 * per-viewer metadata override, and it lives here, off {@link HologramRenderer}, so the renderer keeps the
 * shared-entity lifecycle and this collaborator owns the per-viewer placeholder work.
 *
 * <p>A hologram is per-viewer iff it is a {@link HologramType#TEXT} hologram and at least one of its lines
 * contains a {@code %...%} token (the same cheap check {@link PlaceholderApiSupport#hasPlaceholder} uses for the
 * message bridge). For such a hologram the renderer keeps the native render with the global-resolved text as the
 * broadcast base, then asks this collaborator to send each eligible viewer their own override. A static
 * hologram (no token) is never touched, so a default server with no placeholder hologram pays nothing.
 *
 * <p>The per-viewer text is built exactly as the shared entity renders it: each line's MiniMessage source is run
 * through that viewer's {@code messageBridge} (the per-viewer PlaceholderAPI transform, the identity when
 * PlaceholderAPI is absent, so per-viewer text then equals the global text), deserialised, and the lines joined
 * with newlines. Resolution is fail-soft per viewer: a bridge or parse error for one viewer is logged and
 * skipped so it never stops the others from getting their override.
 */
@NullMarked
public final class HologramTextOverrides {

    private final DisplayTextPackets packets;
    private final Function<java.util.UUID, UnaryOperator<String>> bridgeFactory;
    private final MiniMessage miniMessage;
    private final Supplier<TagResolver> globalTags;
    private final HologramPageState pageState;
    private final Logger log;

    public HologramTextOverrides(
            DisplayTextPackets packets,
            Function<java.util.UUID, UnaryOperator<String>> bridgeFactory,
            MiniMessage miniMessage,
            Supplier<TagResolver> globalTags,
            HologramPageState pageState,
            Logger log) {
        this.packets = Objects.requireNonNull(packets, "packets");
        this.bridgeFactory = Objects.requireNonNull(bridgeFactory, "bridgeFactory");
        this.miniMessage = Objects.requireNonNull(miniMessage, "miniMessage");
        this.globalTags = Objects.requireNonNull(globalTags, "globalTags");
        this.pageState = Objects.requireNonNull(pageState, "pageState");
        this.log = Objects.requireNonNull(log, "log");
    }

    /**
     * Whether {@code hologram} needs per-viewer text: a {@link HologramType#TEXT} hologram that is either
     * multi-page (each viewer sees their own current page over the shared display) or has at least one line
     * carrying a {@code %...%} placeholder token or a built-in {@code {player}}/{@code {page}}/{@code {pages}}
     * token. Item and block holograms render the model only (v1), so their label lines are never a per-viewer
     * target.
     */
    boolean hasPerViewerText(Hologram hologram) {
        Objects.requireNonNull(hologram, "hologram");
        if (hologram.type() != HologramType.TEXT) {
            return false;
        }
        if (hologram.isMultiPage()) {
            return true;
        }
        for (HologramLine line : hologram.lines()) {
            if (PlaceholderApiSupport.hasPlaceholder(line.value()) || HologramTokens.hasToken(line.value())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Send one viewer their text-override packet for the shared {@code entityId}, carrying that viewer's own
     * resolved text. The renderer hops each eligible viewer onto their own entity thread and calls this once per
     * viewer, so resolution stays per viewer. Fail-soft: a bridge or parse error for this viewer is logged and
     * swallowed so the renderer's sibling-viewer loop continues. Caller-guaranteed to be invoked only for a
     * hologram that {@link #hasPerViewerText(Hologram) needs} per-viewer text.
     */
    void sendOverride(Player viewer, int entityId, Hologram hologram) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(hologram, "hologram");
        try {
            Component text = resolveFor(viewer, hologram);
            packets.send(viewer, packets.textOverride(entityId, text));
        } catch (RuntimeException failure) {
            log.warn(
                    "event=hologram_per_viewer_text_failed hologram={} viewer={} error={}",
                    hologram.name().value(),
                    viewer.getUniqueId(),
                    failure.toString());
        }
    }

    /**
     * Resolve {@code hologram}'s lines for one viewer, joined with newlines as the shared entity renders them.
     * For a multi-page hologram the viewer's current page (page 0 by default) is resolved, so each viewer sees
     * their own page over the one shared display; for a single-page hologram its lines are resolved as before.
     * The built-in {@code {player}}/{@code {page}}/{@code {pages}} tokens are substituted first (per viewer), then
     * the placeholder bridge runs.
     */
    private Component resolveFor(Player viewer, Hologram hologram) {
        java.util.UUID uuid = viewer.getUniqueId();
        UnaryOperator<String> bridge = bridgeFactory.apply(uuid);
        TagResolver tags = globalTags.get();
        int pages = hologram.pageCount();
        int page =
                hologram.isMultiPage() ? pageState.currentPage(hologram.name().value(), uuid, pages) : 0;
        java.util.List<HologramLine> lines = hologram.isMultiPage() ? hologram.pageLines(page) : hologram.lines();
        java.util.List<Component> resolved = new java.util.ArrayList<>(lines.size());
        for (HologramLine line : lines) {
            String tokens = HologramTokens.resolve(line.value(), viewer.getName(), page + 1, pages);
            // The per-viewer path renders a static frame, so an inline animation directive is stripped rather than
            // shown literally: animation and per-viewer placeholders do not combine (the placeholder wins).
            String source = HologramAnimations.stripDirective(bridge.apply(tokens));
            resolved.add(miniMessage.deserialize(source, tags));
        }
        return Component.join(JoinConfiguration.newlines(), resolved);
    }
}

package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import com.google.common.base.Splitter;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.ServerConnector;
import com.uxplima.uxmessentials.shared.application.port.Logger;

/**
 * The movement slice of the menu action vocabulary: the two ways a click (or an operator's {@code /menu} spec) can
 * relocate the viewer, {@code teleport} within this server and {@code connect} to another proxy backend.
 * Registered once at startup into the shared {@link MenuBindings} alongside {@link MenuVocabulary},
 * {@link MessagingActions} and {@link CommandActions}, so a disk-loaded spec resolves {@code teleport} /
 * {@code connect} the same way a code-registered feature menu does.
 *
 * <p>The destination is operator content (a world name and coordinates from the spec, not a code-authored string)
 * so no {@code MessageKey} is involved; the only text either action produces is a diagnostic line on the operator
 * {@code log}. {@code teleport} goes through Paper's {@code teleportAsync}, the Folia-safe primitive: the click
 * already runs on the viewer's own entity thread, and {@code teleportAsync} performs the region hop and async chunk
 * load itself. It is fire-and-forget by design. The returned future is never joined, so the tick thread is never
 * blocked waiting on it.
 *
 * <p>Both actions are fail-soft. {@code teleport} skips (with a one-line operator warning) on a malformed
 * destination or an unknown world rather than throwing; {@code connect} is a silent no-op on a blank target, and the
 * connector itself already degrades to a logged no-op when no proxy channel is registered. Every handler is also
 * wrapped in {@link #safe}, so anything unexpected a call throws becomes a logged no-op instead of an exception
 * escaping back into the click dispatch: one bad effect must not abort the rest of a chain.
 */
public final class MovementActions {

    private MovementActions() {}

    /**
     * Register the movement actions into {@code bindings}. {@code connector} is the shared proxy seam the
     * {@code connect} action sends players through (a no-op when no proxy channel is registered); {@code log} is the
     * operator console logger a fail-soft or skipped action warns through. Left separate from
     * {@link MenuVocabulary#registerActions} so that method's existing call-sites stay untouched, the composition
     * root calls both.
     */
    public static void register(MenuBindings bindings, ServerConnector connector, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(connector, "connector");
        Objects.requireNonNull(log, "log");
        Consumer<MenuActionContext> teleport = safe("teleport", log, ctx -> teleport(ctx, log));
        bindings.action("teleport", teleport);
        bindings.action("tp", teleport);
        Consumer<MenuActionContext> connect = safe("connect", log, ctx -> connect(ctx, connector));
        bindings.action("connect", connect);
        bindings.action("server", connect);
    }

    /**
     * Wrap {@code body} so any thrown {@link RuntimeException} becomes a one-line operator warning and a no-op rather
     * than escaping into the click dispatch. The same fail-soft contract {@link MessagingActions} and the npc/
     * holograms click runner apply to their effect actions. On MockBukkit the {@code teleportAsync} stub throws, so
     * this is also what keeps a valid teleport from crashing a test run; on real Paper it is the last-resort guard.
     */
    private static Consumer<MenuActionContext> safe(String action, Logger log, Consumer<MenuActionContext> body) {
        return ctx -> {
            try {
                body.accept(ctx);
            } catch (RuntimeException failure) {
                log.warn("event=menu_movement_action_failed action={} value={}", action, ctx.arg());
            }
        };
    }

    /**
     * Teleport the viewer to a {@code <world> <x> <y> <z> [yaw] [pitch]} destination. A malformed argument (too few
     * tokens, a non-numeric coordinate) or an unknown/blank world is a fail-soft skip with an operator warning, the
     * viewer stays put. The hop uses {@code teleportAsync} and is fire-and-forget: the future is deliberately not
     * joined, since blocking the tick thread on it is forbidden and the click already runs on the viewer's region.
     */
    private static void teleport(MenuActionContext ctx, Logger log) {
        Optional<ParsedTeleport> parsed = ParsedTeleport.parse(ctx.arg());
        if (parsed.isEmpty()) {
            log.warn("event=menu_teleport_skipped reason=malformed_destination value={}", ctx.arg());
            return;
        }
        ParsedTeleport dest = parsed.get();
        World world = Bukkit.getWorld(dest.world());
        if (world == null) {
            log.warn("event=menu_teleport_skipped reason=unknown_world world={}", dest.world());
            return;
        }
        Location target = new Location(world, dest.x(), dest.y(), dest.z(), dest.yaw(), dest.pitch());
        var ignored = ctx.player().teleportAsync(target);
    }

    /** Send the viewer to the named proxy backend; a blank target is a silent no-op, as is an absent proxy channel. */
    private static void connect(MenuActionContext ctx, ServerConnector connector) {
        String server = ctx.arg().strip();
        if (server.isBlank()) {
            return; // no backend named. Nothing to connect to, but not an error
        }
        connector.connect(ctx.player(), server);
    }

    /**
     * A parsed {@code <world> <x> <y> <z> [yaw] [pitch]} teleport destination. The world name and the three
     * coordinates are required; the yaw and pitch are optional and default to {@code 0f}. Parsing is Bukkit-free
     * it resolves no world and builds no {@link Location}, so it can be exercised by plain-JUnit grammar tests; the
     * action resolves the world and constructs the location. Too few tokens, or any non-numeric number, yields an
     * empty result, which the action treats as a fail-soft skip.
     */
    public record ParsedTeleport(String world, double x, double y, double z, float yaw, float pitch) {

        private static final int REQUIRED_TOKENS = 4;

        public static Optional<ParsedTeleport> parse(String value) {
            Objects.requireNonNull(value, "value");
            // omitEmptyStrings collapses runs of whitespace and drops the leading/trailing empties, so a blank value
            // yields no tokens (an empty result) and token 0 is always a real world name.
            List<String> tokens = Splitter.onPattern("\\s+").omitEmptyStrings().splitToList(value.strip());
            if (tokens.size() < REQUIRED_TOKENS) {
                return Optional.empty();
            }
            try {
                double x = Double.parseDouble(tokens.get(1));
                double y = Double.parseDouble(tokens.get(2));
                double z = Double.parseDouble(tokens.get(3));
                float yaw = tokens.size() > 4 ? Float.parseFloat(tokens.get(4)) : 0f;
                float pitch = tokens.size() > 5 ? Float.parseFloat(tokens.get(5)) : 0f;
                return Optional.of(new ParsedTeleport(tokens.get(0), x, y, z, yaw, pitch));
            } catch (NumberFormatException malformed) {
                return Optional.empty();
            }
        }
    }
}

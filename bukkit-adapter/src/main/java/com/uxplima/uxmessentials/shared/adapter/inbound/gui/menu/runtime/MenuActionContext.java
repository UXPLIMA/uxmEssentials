package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime;

import java.util.Map;
import java.util.Objects;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.ClickKind;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;

/**
 * What an action binding receives on a click: the per-open {@link MenuContext} plus the live {@link Player} and
 * the gesture that fired. Actions need the live handle (to give items, play sounds, run commands) and the click
 * kind (to branch left vs. shift-right), neither of which belongs on the render-time {@link MenuContext}.
 *
 * <p>Public so feature bindings can read it; created by the engine on each click. A binding that drives the
 * window it fired in (refresh, reset-pagination) reads {@link #control()}, the engine-supplied handle onto the
 * clicked menu.
 */
public final class MenuActionContext {

    private final MenuContext ctx;

    private final Player player;

    private final ClickKind clickKind;

    private final Map<String, String> args;

    private final MenuControl control;

    /**
     * The constructor every non-listener call-site uses: a context with no menu-control handle, so a control action
     * invoked through it is a safe no-op. Delegates to the canonical constructor with {@link MenuControl#NOOP}, which
     * keeps the existing call-sites (feature bindings, unit tests) compiling unchanged, only the live click path
     * needs the five-argument form below.
     */
    public MenuActionContext(MenuContext ctx, Player player, ClickKind clickKind, Map<String, String> args) {
        this(ctx, player, clickKind, args, MenuControl.NOOP);
    }

    /** The canonical constructor: the same context plus the engine's handle onto the menu the click fired in. */
    public MenuActionContext(
            MenuContext ctx, Player player, ClickKind clickKind, Map<String, String> args, MenuControl control) {
        this.ctx = Objects.requireNonNull(ctx, "ctx");
        this.player = Objects.requireNonNull(player, "player");
        this.clickKind = Objects.requireNonNull(clickKind, "clickKind");
        this.args = Map.copyOf(Objects.requireNonNull(args, "args"));
        this.control = Objects.requireNonNull(control, "control");
    }

    public PlayerRef viewer() {
        return ctx.viewer();
    }

    /**
     * The per-open context this click happened in. Most bindings read the delegating accessors below instead; the
     * whole context is exposed for the developer-API boundary, which hands a click handler the same view a render
     * handler sees.
     */
    public MenuContext context() {
        return ctx;
    }

    public Player player() {
        return player;
    }

    public ClickKind clickKind() {
        return clickKind;
    }

    /**
     * The engine's handle onto the menu this click fired in. What a {@code refresh}/{@code reset-pagination} action
     * drives the window through. A context built outside a live click carries {@link MenuControl#NOOP}, so reading it
     * is always safe.
     */
    public MenuControl control() {
        return control;
    }

    public int page() {
        return ctx.page();
    }

    public <T> T subject(Class<T> type) {
        return ctx.subject(type);
    }

    public <T> T entry(Class<T> type) {
        return ctx.entry(type);
    }

    /** The invoked action ref's arguments: {@code "command:spawn"} arrives as {@code {value: "spawn"}}. */
    public Map<String, String> args() {
        return args;
    }

    /** The single positional argument of an {@code id:value} action ref, or empty when the ref carried none. */
    public String arg() {
        return args.getOrDefault("value", "");
    }
}

package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import com.google.common.base.Splitter;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.outbound.action.ClickCommandRunner;
import com.uxplima.uxmessentials.shared.application.port.Logger;

/**
 * The player/command slice of the menu action vocabulary: the ways a click (or an operator's {@code /menu} spec)
 * can make the viewer speak or dispatch a command, beyond the plain {@code command} / {@code console} actions
 * {@link MenuVocabulary} already registers. Registered once at startup into the shared {@link MenuBindings}
 * alongside {@link MenuVocabulary} and {@link MessagingActions}, so a disk-loaded spec resolves {@code chat-as-player}
 * / {@code command-as-op} / {@code commandevent} / {@code command-random} / {@code console-random} the same way a
 * code-registered feature menu does.
 *
 * <p>Command dispatch reuses the shared {@link ClickCommandRunner} the npc/holograms click chains already run
 * through, in particular the temporary-op grant/perform/revoke of {@link ClickCommandRunner#runAsPlayerOp}, so the
 * elevation logic is not reimplemented here. The two elevated actions ({@code command-as-op} and {@code
 * console-random}) are gated behind the same {@code custommenus.allow-console} flag as {@link MenuVocabulary}'s
 * {@code console} action: menu-driven privileged dispatch stays opt-in, and when the flag is off the action warns
 * through the operator {@code log} and does nothing. The player-level actions ({@code chat-as-player}, {@code
 * commandevent}, {@code command-random}) run as the viewer and carry no elevation, so they are ungated.
 *
 * <p>Every action is fail-soft through {@link #safe}: a blank or malformed argument, or a dispatch that throws,
 * becomes a logged-or-silent no-op rather than an exception thrown back into the click. One bad effect must not
 * abort the rest of a chain or spill a stack trace onto the tick thread. A single leading {@code /} is stripped from
 * every command argument, since {@code performCommand} / {@code dispatchCommand} expect the bare command, matching
 * the existing {@code command} action.
 */
public final class CommandActions {

    private CommandActions() {}

    /**
     * Register the player/command actions into {@code bindings}. {@code commandRunner} is the shared dispatch seam
     * the elevated and player command actions run through (reusing its op grant/revoke); {@code allowConsole} gates
     * the two elevated actions exactly as it gates {@link MenuVocabulary}'s {@code console} action; {@code log} is the
     * operator console logger a fail-soft or disabled-gate action warns through. Left separate from
     * {@link MenuVocabulary#registerActions} so that method's existing call-sites stay untouched, the composition
     * root calls both.
     */
    public static void register(
            MenuBindings bindings, ClickCommandRunner commandRunner, boolean allowConsole, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(commandRunner, "commandRunner");
        Objects.requireNonNull(log, "log");
        Consumer<MenuActionContext> chat = safe("chat-as-player", log, CommandActions::chatAsPlayer);
        bindings.action("chat-as-player", chat);
        bindings.action("chat", chat);
        Consumer<MenuActionContext> commandAsOp =
                gated("command-as-op", allowConsole, log, ctx -> runAsOp(ctx, commandRunner));
        bindings.action("command-as-op", commandAsOp);
        bindings.action("command-op", commandAsOp);
        bindings.action("commandevent", safe("commandevent", log, CommandActions::commandEvent));
        bindings.action("command-random", safe("command-random", log, ctx -> commandRandom(ctx, commandRunner)));
        bindings.action(
                "console-random", gated("console-random", allowConsole, log, ctx -> consoleRandom(ctx, commandRunner)));
    }

    /**
     * Wrap {@code body} so any thrown {@link RuntimeException} becomes a one-line operator warning and a no-op rather
     * than escaping into the click dispatch. The same fail-soft contract {@link MessagingActions} and the npc/
     * holograms click runner apply to their effect actions.
     */
    private static Consumer<MenuActionContext> safe(String action, Logger log, Consumer<MenuActionContext> body) {
        return ctx -> {
            try {
                body.accept(ctx);
            } catch (RuntimeException failure) {
                log.warn("event=menu_command_action_failed action={} value={}", action, ctx.arg());
            }
        };
    }

    /**
     * Gate an elevated action behind {@code allowConsole}. When enabled the action runs fail-soft through
     * {@link #safe}; when disabled it warns and does nothing, exactly like {@link MenuVocabulary}'s {@code console}
     * action, so an operator who forgot the {@code custommenus.allow-console} flag sees why nothing happened. The
     * decision is fixed at registration time, so a per-click branch never re-reads it.
     */
    private static Consumer<MenuActionContext> gated(
            String action, boolean allowConsole, Logger log, Consumer<MenuActionContext> body) {
        if (allowConsole) {
            return safe(action, log, body);
        }
        return ctx -> log.warn(
                "menu {} action is disabled (custommenus.allow-console=false); ignored: {}", action, ctx.arg());
    }

    /** Make the viewer speak {@code arg} into public chat so the chat pipeline (format, other plugins) sees it. */
    private static void chatAsPlayer(MenuActionContext ctx) {
        String message = ctx.arg();
        if (message.isBlank()) {
            return; // an empty chat line is a no-op, not an error
        }
        ctx.player().chat(message);
    }

    /** Dispatch {@code arg} as the viewer with operator permissions for the one call, reusing the runner's grant/revoke. */
    private static void runAsOp(MenuActionContext ctx, ClickCommandRunner runner) {
        String command = stripSlash(ctx.arg());
        if (command.isBlank()) {
            return; // nothing to elevate, skip rather than toggle op for an empty command
        }
        runner.runAsPlayerOp(ctx.player(), command);
    }

    /**
     * Dispatch {@code arg} through the command <em>event</em> so another plugin can intercept or cancel it before it
     * runs: fire a {@link PlayerCommandPreprocessEvent} for the viewer, and unless a listener cancels it, perform the
     * (possibly rewritten) command as the viewer. Ungated: this runs with the viewer's own permissions.
     */
    private static void commandEvent(MenuActionContext ctx) {
        String command = stripSlash(ctx.arg());
        if (command.isBlank()) {
            return;
        }
        Player player = ctx.player();
        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, "/" + command);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return; // a listener vetoed the command. Respect the veto and dispatch nothing
        }
        player.performCommand(stripSlash(event.getMessage()));
    }

    /** Pick one command at random from a {@code ;}-separated list and run it as the viewer; an empty list is a no-op. */
    private static void commandRandom(MenuActionContext ctx, ClickCommandRunner runner) {
        String choice = pickRandom(ctx.arg());
        if (choice.isEmpty()) {
            return;
        }
        runner.runAsPlayer(ctx.player(), stripSlash(choice));
    }

    /** Pick one command at random from a {@code ;}-separated list and run it as the console; an empty list is a no-op. */
    private static void consoleRandom(MenuActionContext ctx, ClickCommandRunner runner) {
        String choice = pickRandom(ctx.arg());
        if (choice.isEmpty()) {
            return;
        }
        runner.runAsConsole(stripSlash(choice));
    }

    /** The trimmed choice picked uniformly at random from {@code value}, or empty when it holds no candidate. */
    private static String pickRandom(String value) {
        List<String> choices = choices(value);
        if (choices.isEmpty()) {
            return "";
        }
        return choices.get(ThreadLocalRandom.current().nextInt(choices.size()));
    }

    /**
     * Split a {@code ;}-separated command list into its trimmed, non-blank candidates. Exposed for the pure grammar
     * tests (like {@link MessagingActions}'s {@code ParsedX.parse}); the returned list is empty when nothing survives
     * trimming, which the random actions read as a no-op.
     */
    public static List<String> choices(String value) {
        return Splitter.on(';').trimResults().omitEmptyStrings().splitToList(value);
    }

    /**
     * Drop a single leading {@code /} from a command after trimming, since {@code performCommand} / {@code
     * dispatchCommand} expect the bare command. Only one slash is removed, so {@code //something} keeps its second.
     * Exposed for the pure grammar tests.
     */
    public static String stripSlash(String command) {
        String trimmed = command.strip();
        return trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
    }
}

package com.uxplima.uxmessentials.npc.adapter.inbound.gui;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.shared.adapter.inbound.command.ClickActionValueCheck;
import com.uxplima.uxmessentials.shared.domain.action.ClickAction;
import com.uxplima.uxmessentials.shared.domain.action.ClickActionType;
import com.uxplima.uxmessentials.shared.domain.action.ClickTrigger;
import org.jspecify.annotations.Nullable;

/**
 * The one-line text codec the NPC editor's click-action sub-list uses, so the generic {@code ListProperty}
 * (which edits a {@code List<String>}) can drive the typed {@link ClickAction} chain. A line is
 * {@code <trigger> <type> <value…>}, exactly the {@code /npc action add} argument shape, so an operator who
 * knows the command knows the GUI line. {@link #render} turns a stored action into that line for display and
 * editing; {@link #parse} turns an edited line back into an action, returning empty when the trigger/type are
 * unknown or the value fails the cheap per-type check (the sub-list then drops the bad line rather than storing
 * it). It carries no domain logic beyond the trigger/type word mapping the command already owns.
 */
final class NpcActionLines {

    private NpcActionLines() {}

    /** Render an action to its {@code trigger type value} edit line. */
    static String render(ClickAction action) {
        Objects.requireNonNull(action, "action");
        return triggerWord(action.trigger()) + " " + typeWord(action.type()) + " " + action.value();
    }

    /** Render a whole chain to its display lines, preserving order. */
    static List<String> render(List<ClickAction> actions) {
        return actions.stream().map(NpcActionLines::render).toList();
    }

    /**
     * Parse a {@code trigger type value} line into an action, or empty when the trigger or type word is unknown,
     * the value is missing, or the value fails the type's cheap validity check (a malformed line is dropped, not
     * stored). A {@code give hand} captured through the command is stored as a {@code b64:} token, so a
     * GUI-edited {@code give} line is plain text content the same check accepts.
     */
    static Optional<ClickAction> parse(String line) {
        Objects.requireNonNull(line, "line");
        String[] parts = line.strip().split("\\s+", 3);
        if (parts.length < 3) {
            return Optional.empty();
        }
        ClickTrigger trigger = parseTrigger(parts[0]);
        Optional<ClickActionType> type = ClickActionValueCheck.parseType(parts[1]);
        if (trigger == null || type.isEmpty()) {
            return Optional.empty();
        }
        String value = parts[2];
        if (ClickActionValueCheck.check(type.get(), value) instanceof ClickActionValueCheck.Result.Invalid) {
            return Optional.empty();
        }
        return Optional.of(new ClickAction(trigger, type.get(), value));
    }

    private static String triggerWord(ClickTrigger trigger) {
        return switch (trigger) {
            case LEFT_CLICK -> "left";
            case RIGHT_CLICK -> "right";
            case ANY -> "any";
        };
    }

    private static @Nullable ClickTrigger parseTrigger(String word) {
        return switch (word.strip().toLowerCase(Locale.ROOT)) {
            case "left" -> ClickTrigger.LEFT_CLICK;
            case "right" -> ClickTrigger.RIGHT_CLICK;
            case "any" -> ClickTrigger.ANY;
            default -> null;
        };
    }

    private static String typeWord(ClickActionType type) {
        return switch (type) {
            case RUN_CONSOLE -> "console";
            case RUN_PLAYER -> "player";
            case RUN_PLAYER_AS_OP -> "player_op";
            case MESSAGE -> "message";
            case ACTIONBAR -> "actionbar";
            case TITLE -> "title";
            case SOUND -> "sound";
            case CONNECT -> "connect";
            case DELAY -> "delay";
            case RANDOM -> "random";
            case CHANCE -> "chance";
            case PERMISSION -> "permission";
            case CONDITION -> "condition";
            case COST -> "cost";
            case GIVE -> "give";
        };
    }
}

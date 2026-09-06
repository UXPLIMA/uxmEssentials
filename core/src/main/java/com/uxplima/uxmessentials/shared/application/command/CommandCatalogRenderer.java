package com.uxplima.uxmessentials.shared.application.command;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Renders the resolved command surface into the HOCON an operator edits in the root {@code commands.conf}.
 *
 * <p>This is the write half of the catalog: {@link CommandCatalogConfig} reads the same shape back. The
 * file is keyed by the stable {@link CommandId} rather than the renameable name, because the permission
 * node binds to the id: an operator who renames {@code home} to {@code casa} keeps the same permission.
 * Commands are emitted in the order they were resolved so a regenerated file diffs cleanly against an
 * earlier one, and names and aliases are quoted so values with spaces or quotes survive the round-trip.
 */
public final class CommandCatalogRenderer {

    private static final String NL = "\n";

    /** An id that needs no quoting: the shape every built-in command carries. */
    private static final java.util.regex.Pattern PLAIN_ID = java.util.regex.Pattern.compile("[a-z][a-z0-9]*");

    private CommandCatalogRenderer() {}

    /** Render every command into a single {@code commands { <id> { ... } }} document. */
    public static String render(List<EffectiveCommand> commands) {
        List<EffectiveCommand> snapshot = List.copyOf(Objects.requireNonNull(commands, "commands"));
        StringBuilder out = new StringBuilder();
        appendHeader(out);
        out.append("commands {").append(NL);
        for (EffectiveCommand command : snapshot) {
            appendCommand(out, command);
        }
        out.append("}").append(NL);
        return out.toString();
    }

    private static void appendHeader(StringBuilder out) {
        out.append("# Command catalog: rename, realias or disable any command without touching code.")
                .append(NL);
        out.append("# Each block is keyed by the command's stable id. The id never changes, so the")
                .append(NL);
        out.append("# permission node stays the same even after you rename the command below.")
                .append(NL);
        out.append("# Command-surface edits take effect on the next server restart.")
                .append(NL);
        out.append("#").append(NL);
        out.append("#   enabled = false   drops the command entirely (it registers nothing).")
                .append(NL);
        out.append("#   name              the primary literal players type; edit it to rename.")
                .append(NL);
        out.append("#   aliases           extra literals that map to the same command.")
                .append(NL);
        out.append("#   localized-aliases extra literals by locale (Unicode is supported), e.g. tr=[\"ev\"].")
                .append(NL);
        out.append("#   gui               running the command bare opens its GUI; false falls back to usage text.")
                .append(NL);
        out.append("#").append(NL);
        out.append("# This file was generated from the live command surface on first run and is left")
                .append(NL);
        out.append("# untouched afterwards; later edits remain operator-owned across upgrades.")
                .append(NL);
        out.append(NL);
    }

    private static void appendCommand(StringBuilder out, EffectiveCommand command) {
        out.append("  ").append(key(command.id().value())).append(" {").append(NL);
        out.append("    enabled = ").append(command.enabled()).append(NL);
        out.append("    name = ").append(quote(command.name())).append(NL);
        out.append("    aliases = ").append(renderAliases(command.aliases())).append(NL);
        appendLocalizedAliases(out, command.localizedAliases());
        out.append("    gui = ").append(command.gui()).append(NL);
        out.append("  }").append(NL);
    }

    /**
     * The block key for a command id. A plain id is written bare, the way every hand-written catalog entry reads. A
     * namespaced one ({@code custom:welcome}) is quoted, because HOCON reads an unquoted colon as the separator
     * between a key and its value and would otherwise split the id in half.
     */
    private static String key(String id) {
        return PLAIN_ID.matcher(id).matches() ? id : quote(id);
    }

    private static void appendLocalizedAliases(StringBuilder out, Map<String, List<String>> localized) {
        if (localized.isEmpty()) {
            out.append("    localized-aliases = {}").append(NL);
            return;
        }
        out.append("    localized-aliases {").append(NL);
        localized.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> out.append("      ")
                        .append(quote(entry.getKey()))
                        .append(" = ")
                        .append(renderAliases(entry.getValue()))
                        .append(NL));
        out.append("    }").append(NL);
    }

    private static String renderAliases(List<String> aliases) {
        StringBuilder list = new StringBuilder("[");
        for (int i = 0; i < aliases.size(); i++) {
            if (i > 0) {
                list.append(", ");
            }
            list.append(quote(aliases.get(i)));
        }
        return list.append("]").toString();
    }

    private static String quote(String value) {
        String escaped = value.replace("\\", "\\\\").replace("\"", "\\\"");
        return "\"" + escaped + "\"";
    }
}

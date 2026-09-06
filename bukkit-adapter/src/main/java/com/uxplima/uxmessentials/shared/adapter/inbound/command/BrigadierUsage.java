package com.uxplima.uxmessentials.shared.adapter.inbound.command;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import org.jspecify.annotations.NullMarked;

/**
 * Derives a command's usage suffix from a built Brigadier tree the way {@code CommandDispatcher.getSmartUsage}
 * does, but without a source: it never calls {@code canUse}, so the root's permission predicate is not
 * evaluated (there is no live sender at registration time) and a deeper branch is bracketed as optional only
 * from the static shape: whether the parent node is itself executable.
 *
 * <p>The result is the part of the usage line after the root literal: {@code <mode> [<player>]} for
 * {@code /gamemode}, {@code <player> <amount> [<currency>]} for {@code /pay}. A node with no children yields
 * the empty string (such a node already carries a root executor, so it is never handed here).
 */
@NullMarked
final class BrigadierUsage {

    private static final String SPACE = " ";

    private BrigadierUsage() {}

    /** The usage suffix for {@code root}'s children, joined the way smart-usage joins them. */
    static String of(LiteralCommandNode<CommandSourceStack> root) {
        Objects.requireNonNull(root, "root");
        return joinChildren(root, false);
    }

    /** Render every usable child of {@code node} and join the single- or multi-branch shape. */
    private static String joinChildren(CommandNode<CommandSourceStack> node, boolean parentExecutable) {
        List<CommandNode<CommandSourceStack>> children = List.copyOf(node.getChildren());
        if (children.isEmpty()) {
            return "";
        }
        if (children.size() == 1) {
            return smartUsage(children.get(0), parentExecutable);
        }
        Set<String> rendered = new LinkedHashSet<>();
        for (CommandNode<CommandSourceStack> child : children) {
            rendered.add(smartUsage(child, false));
        }
        return bracketAlternatives(rendered, parentExecutable);
    }

    /** One node's usage text plus its own (optionally bracketed) sub-tree, mirroring smart-usage. */
    private static String smartUsage(CommandNode<CommandSourceStack> node, boolean optional) {
        String self = optional ? "[" + node.getUsageText() + "]" : node.getUsageText();
        if (node.getChildren().isEmpty()) {
            return self;
        }
        boolean executable = node.getCommand() != null;
        String deeper = joinChildren(node, executable);
        return deeper.isEmpty() ? self : self + SPACE + deeper;
    }

    /** Join distinct alternative branches; one collapses, several become {@code (a|b)} or {@code [a|b]}. */
    private static String bracketAlternatives(Set<String> rendered, boolean parentExecutable) {
        if (rendered.size() == 1) {
            String only = rendered.iterator().next();
            return parentExecutable ? "[" + only + "]" : only;
        }
        String open = parentExecutable ? "[" : "(";
        String close = parentExecutable ? "]" : ")";
        return open + String.join("|", rendered) + close;
    }
}

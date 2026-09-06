package com.uxplima.uxmessentials.itemworld.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.bukkit.TreeType;
import org.bukkit.inventory.ItemFlag;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.uxplima.uxmessentials.itemworld.domain.TimeSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.command.CommandSourceStackMock;

/**
 * Pins the tab-completion the shared {@link ItemworldCommandSupport} argument helpers offer, exercised through a
 * real Brigadier dispatcher against the live MockBukkit registries. These cover the maintainer's gaps where
 * {@code /enchant}, {@code /itemflag}, {@code /remove}, {@code /tree} and {@code /bigtree} suggested nothing for
 * their first argument. Each helper builds the same node the command wires, so a regression that drops the
 * provider (or points it at the wrong registry) fails here. The value still parses as a bare word, so suggestions
 * never narrow what the command accepts: they only surface the obvious choices.
 */
class ItemworldCommandSuggestionsTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void enchantArgumentSuggestsRegistryEnchantments() {
        List<String> all = suggest(node(ItemworldCommandSupport.enchantArgument()), "root ");
        // A well-known vanilla enchantment must surface (its bare minecraft path), proving the registry is read.
        assertThat(all).contains("sharpness");

        assertThat(suggest(node(ItemworldCommandSupport.enchantArgument()), "root sharp"))
                .contains("sharpness")
                .allMatch(s -> s.startsWith("sharp"));
    }

    @Test
    void itemFlagArgumentSuggestsLowerCasedFlags() {
        List<String> all = suggest(node(ItemworldCommandSupport.itemFlagArgument()), "root ");
        // Every ItemFlag, lower-cased: the form BukkitItemResolver#itemFlag upper-cases before resolving.
        assertThat(all)
                .containsExactlyInAnyOrderElementsOf(java.util.Arrays.stream(ItemFlag.values())
                        .map(f -> f.name().toLowerCase(java.util.Locale.ROOT))
                        .toList());

        assertThat(suggest(node(ItemworldCommandSupport.itemFlagArgument()), "root hide_e"))
                .allMatch(s -> s.startsWith("hide_e"))
                .isNotEmpty();
    }

    @Test
    void mobTypeArgumentSuggestsSpawnableTypesForRemove() {
        List<String> all = suggest(node(ItemworldCommandSupport.mobTypeArgument()), "root ");
        // A spawnable type the PurgeSelection NAMED_TYPE matcher resolves; PLAYER is never spawnable, so excluded.
        assertThat(all).contains("zombie").doesNotContain("player");
    }

    @Test
    void treeTypeArgumentSuggestsLowerCasedTreeTypes() {
        List<String> all = suggest(node(ItemworldCommandSupport.treeTypeArgument()), "root ");
        assertThat(all)
                .containsExactlyInAnyOrderElementsOf(java.util.Arrays.stream(TreeType.values())
                        .map(t -> t.name().toLowerCase(java.util.Locale.ROOT))
                        .toList());
    }

    @Test
    void timeKeywordsAllParseAsSetValues() {
        // The /time value type-ahead offers the named keywords TimeSpec.parse resolves (a raw tick number is also
        // accepted but not enumerated). Each must parse as a SET value, or the suggestion would be unusable.
        List<String> keywords = List.of("day", "noon", "sunset", "night", "midnight", "sunrise");
        assertThat(keywords)
                .allMatch(value -> TimeSpec.parse(TimeSpec.Mode.SET, value).isPresent());
    }

    private static com.mojang.brigadier.tree.LiteralCommandNode<CommandSourceStack> node(
            com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> arg) {
        return Commands.literal("root").then(arg.executes(ctx -> 1)).build();
    }

    private List<String> suggest(com.mojang.brigadier.tree.LiteralCommandNode<CommandSourceStack> root, String input) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.getRoot().addChild(root);
        var parse = dispatcher.parse(input, CommandSourceStackMock.from(server.addPlayer()));
        Suggestions suggestions = dispatcher.getCompletionSuggestions(parse).join();
        return suggestions.getList().stream().map(Suggestion::getText).toList();
    }
}

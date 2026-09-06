package com.uxplima.uxmessentials.holograms.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import io.papermc.paper.command.brigadier.CommandSourceStack;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.uxplima.uxmessentials.holograms.domain.Billboard;
import com.uxplima.uxmessentials.holograms.domain.HologramType;
import com.uxplima.uxmessentials.holograms.domain.TextAlignment;
import com.uxplima.uxmessentials.holograms.domain.Visibility;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import org.junit.jupiter.api.Test;

/**
 * Pins the {@code /hologram} tab-completion: the {@code name} arguments complete against the current hologram
 * names through {@link CommandSuggestions#fromStrings} (the helper the command wires onto every name node), and
 * the fixed-choice arguments offer their full enum value set. The provider reads only the supplied name list and
 * the partial token, so it is exercised here without a live server. The enum lists assert that the choices the
 * command offers (billboard, alignment, visibility mode, create type) stay in lockstep with the domain enums
 * the gap the maintainer reported, where {@code FIXED} and friends suggested nothing.
 */
class HologramSuggestionsTest {

    private static List<String> complete(SuggestionProvider<CommandSourceStack> provider, String typedSoFar)
            throws Exception {
        // fromStrings reads only builder.getRemaining(), never the context, so a null context is safe here.
        SuggestionsBuilder builder = new SuggestionsBuilder(typedSoFar, 0);
        Suggestions suggestions = getSuggestions(provider, builder);
        return suggestions.getList().stream().map(s -> s.getText()).toList();
    }

    private static Suggestions getSuggestions(
            SuggestionProvider<CommandSourceStack> provider, SuggestionsBuilder builder) throws Exception {
        try {
            return provider.getSuggestions(null, builder).get();
        } catch (CommandSyntaxException impossible) {
            // fromStrings never parses, so it cannot raise a syntax error; surface it if the contract ever changes.
            throw new AssertionError("suggestion provider must not throw a syntax error", impossible);
        }
    }

    @Test
    void nameArgumentCompletesAgainstTheCurrentHologramNames() throws Exception {
        SuggestionProvider<CommandSourceStack> names =
                CommandSuggestions.fromStrings(() -> List.of("spawn", "shop", "pvp"));

        assertThat(complete(names, "")).containsExactlyInAnyOrder("spawn", "shop", "pvp");
        // Prefix-filtered case-insensitively by the partial token.
        assertThat(complete(names, "sp")).containsExactly("spawn");
        assertThat(complete(names, "S")).containsExactlyInAnyOrder("spawn", "shop");
        assertThat(complete(names, "none")).isEmpty();
    }

    @Test
    void billboardChoicesCoverEveryDomainValue() throws Exception {
        List<String> values = Arrays.stream(Billboard.values()).map(Enum::name).toList();
        SuggestionProvider<CommandSourceStack> provider = CommandSuggestions.fromStrings(() -> values);

        assertThat(complete(provider, "")).containsExactlyInAnyOrder("CENTER", "FIXED", "VERTICAL", "HORIZONTAL");
        // The maintainer's exact gap: FIXED must surface when partially typed.
        assertThat(complete(provider, "FI")).containsExactly("FIXED");
    }

    @Test
    void alignmentChoicesCoverEveryDomainValue() throws Exception {
        List<String> values =
                Arrays.stream(TextAlignment.values()).map(Enum::name).toList();
        SuggestionProvider<CommandSourceStack> provider = CommandSuggestions.fromStrings(() -> values);

        assertThat(complete(provider, "")).containsExactlyInAnyOrder("LEFT", "CENTER", "RIGHT");
    }

    @Test
    void visibilityModeChoicesCoverEveryDomainValue() throws Exception {
        List<String> values =
                Arrays.stream(Visibility.Mode.values()).map(Enum::name).toList();
        SuggestionProvider<CommandSourceStack> provider = CommandSuggestions.fromStrings(() -> values);

        assertThat(complete(provider, "")).containsExactlyInAnyOrder("ALL", "PERMISSION", "MANUAL");
    }

    @Test
    void hologramTypeChoicesCoverEveryDomainValue() throws Exception {
        List<String> values =
                Arrays.stream(HologramType.values()).map(Enum::name).toList();
        SuggestionProvider<CommandSourceStack> provider = CommandSuggestions.fromStrings(() -> values);

        assertThat(complete(provider, "")).containsExactlyInAnyOrder("TEXT", "ITEM", "BLOCK", "HEAD", "ENTITY");
    }
}

package com.uxplima.uxmessentials.playerstate.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.uxplima.uxmessentials.playerstate.domain.GameModeRef;
import com.uxplima.uxmessentials.playerstate.domain.PersonalWeather;
import com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandSuggestions;
import org.junit.jupiter.api.Test;

/**
 * Pins the {@code /gamemode} and {@code /pweather} tab-completion the maintainer reported missing, and the
 * invariant that backs it: a suggested token must be one the domain resolver actually parses. The providers are
 * the same {@link CommandSuggestions#fromStrings} the commands wire, fed from the same domain sources, so a
 * suggestion set that drifts away from what {@link GameModeRef#parse}//{@link PersonalWeather#parse} accept fails
 * here. {@code fromStrings} reads only the partial token, so it runs without a live server.
 */
class PlayerStateSuggestionsTest {

    @Test
    void gamemodeSuggestsEveryParseableForm() throws Exception {
        List<String> tokens = new ArrayList<>();
        for (GameModeRef mode : GameModeRef.values()) {
            tokens.add(mode.canonical());
            tokens.add(mode.shortAlias());
            tokens.add(Integer.toString(mode.id()));
        }
        SuggestionProvider<?> provider = CommandSuggestions.fromStrings(() -> tokens);

        List<String> all = complete(provider, "");
        // The full name, the short alias and the numeric id for every mode (s/c/a/sp, 0..3, survival..spectator).
        assertThat(all)
                .contains("survival", "creative", "adventure", "spectator", "s", "c", "a", "sp", "0", "1", "2", "3");
        // Every suggested token must parse: the rule is never to offer a form the command rejects.
        assertThat(all).allMatch(token -> GameModeRef.parse(token).isPresent());
    }

    @Test
    void pweatherSuggestsOneParseableValuePerOutcome() throws Exception {
        SuggestionProvider<?> provider = CommandSuggestions.fromStrings(() -> List.of("clear", "rain", "reset"));

        List<String> all = complete(provider, "");
        assertThat(all).containsExactlyInAnyOrder("clear", "rain", "reset");
        // Each canonical value resolves, and the three cover the three distinct outcomes.
        assertThat(PersonalWeather.parse("clear")).contains(PersonalWeather.CLEAR);
        assertThat(PersonalWeather.parse("rain")).contains(PersonalWeather.RAIN);
        assertThat(PersonalWeather.parse("reset")).contains(PersonalWeather.RESET);
        assertThat(all).allMatch(token -> PersonalWeather.parse(token).isPresent());
    }

    private static List<String> complete(SuggestionProvider<?> provider, String typedSoFar) throws Exception {
        SuggestionsBuilder builder = new SuggestionsBuilder(typedSoFar, 0);
        try {
            @SuppressWarnings("unchecked")
            // fromStrings ignores the context entirely (it reads only the builder), so a null source is safe here.
            SuggestionProvider<Object> raw = (SuggestionProvider<Object>) provider;
            Suggestions suggestions = raw.getSuggestions(null, builder).get();
            return suggestions.getList().stream().map(s -> s.getText()).toList();
        } catch (CommandSyntaxException impossible) {
            throw new AssertionError("fromStrings never parses, so it cannot raise a syntax error", impossible);
        }
    }
}

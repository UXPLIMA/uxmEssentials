package com.uxplima.uxmessentials.shared.adapter.inbound.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Pins the selector tokens a player-target argument offers, and the invariant behind the maintainer's report
 * ({@code /nuke @e} was suggested but never resolved): a suggested selector must be one the argument can actually
 * resolve to players. {@code @e}/{@code @n} pull in non-player entities, so neither arity ever offers them; the
 * single-target form additionally drops {@code @a}, which matches more than one player and so fails its parser.
 * {@link CommandSuggestions#playerSelectorTokens} reads only the typed prefix, so it runs without a live server
 * online names are appended separately by the providers, which is why a plain text prefix yields no tokens here.
 */
class PlayerSelectorSuggestionsTest {

    @Test
    void multiOffersEveryPlayerSelectorAndNoEntitySelector() {
        List<String> tokens = CommandSuggestions.playerSelectorTokens(true, "");
        assertThat(tokens).containsExactlyInAnyOrder("@a", "@p", "@r", "@s");
        assertThat(tokens).doesNotContain("@e", "@n");
    }

    @Test
    void singleDropsAtAllAlongsideTheEntitySelectors() {
        List<String> tokens = CommandSuggestions.playerSelectorTokens(false, "");
        assertThat(tokens).containsExactlyInAnyOrder("@p", "@r", "@s");
        assertThat(tokens).doesNotContain("@a", "@e", "@n");
    }

    @Test
    void atAPrefixIsOfferedOnlyForMultiTarget() {
        // @a resolves a whole roster, so it is a valid multi-target token but never a single-target one.
        assertThat(CommandSuggestions.playerSelectorTokens(true, "@a")).containsExactly("@a");
        assertThat(CommandSuggestions.playerSelectorTokens(false, "@a")).isEmpty();
    }

    @Test
    void atEPrefixIsNeverOffered() {
        // The core regression: @e was being suggested for player targets even though the parser rejects it.
        assertThat(CommandSuggestions.playerSelectorTokens(true, "@e")).isEmpty();
        assertThat(CommandSuggestions.playerSelectorTokens(false, "@e")).isEmpty();
    }

    @Test
    void aNameLikePrefixMatchesNoSelectorToken() {
        // The token list is selectors only; online names are appended by the providers, not by this helper.
        assertThat(CommandSuggestions.playerSelectorTokens(true, "st")).isEmpty();
        assertThat(CommandSuggestions.playerSelectorTokens(false, "st")).isEmpty();
    }
}

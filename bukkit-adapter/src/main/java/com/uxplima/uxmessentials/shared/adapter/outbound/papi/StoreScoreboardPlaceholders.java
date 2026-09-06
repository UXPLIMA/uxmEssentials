package com.uxplima.uxmessentials.shared.adapter.outbound.papi;

import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.scoreboard.adapter.outbound.ScoreboardRenderer;
import com.uxplima.uxmessentials.scoreboard.application.port.ScoreboardVisibilityStore;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * {@link ScoreboardPlaceholders} over the scoreboard context's {@link ScoreboardVisibilityStore}: the same PDC-backed
 * "hidden" bit the {@code /scoreboard} toggle flips and the render loop reads. Built during scoreboard wiring, so the
 * placeholder matches whether the player actually sees the sidebar in game.
 *
 * <p>Visibility is the inverse of the store's hidden bit. The store resolves the live player to read the bit, so it has
 * no value for an offline requester; the resolver guards the placeholder behind the online flag, so this is only asked
 * for a connected player.
 */
@NullMarked
public final class StoreScoreboardPlaceholders implements ScoreboardPlaceholders {

    private final ScoreboardVisibilityStore store;
    private final ScoreboardRenderer renderer;

    public StoreScoreboardPlaceholders(ScoreboardVisibilityStore store, ScoreboardRenderer renderer) {
        this.store = Objects.requireNonNull(store, "store");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

    @Override
    public boolean visible(PlayerRef who) {
        return !store.hidden(Objects.requireNonNull(who, "who"));
    }

    @Override
    public Optional<String> board(PlayerRef who) {
        return renderer.appliedBoard(Objects.requireNonNull(who, "who"));
    }
}

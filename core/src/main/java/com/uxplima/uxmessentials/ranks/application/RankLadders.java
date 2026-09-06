package com.uxplima.uxmessentials.ranks.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.ranks.domain.Rank;
import com.uxplima.uxmessentials.ranks.domain.RankId;
import com.uxplima.uxmessentials.ranks.domain.RankLadder;
import com.uxplima.uxmessentials.shared.application.port.ConfigStore;

/**
 * Reads the operator's ladder from {@code modules/ranks/ranks.conf} into a domain {@link RankLadder}. The
 * layout loader mounts that sibling file under the module's scoped {@code ranks} key, so each rank is a section
 * at {@code ranks.<id>} carrying its {@code order}, {@code display-name}, {@code cost}, and the raw
 * {@code requirements} / {@code actions} lists later phases parse. Every rank id is the section name.
 *
 * <p>This is application code, not domain: it depends on the {@link ConfigStore} port to read the tree, then
 * hands the pure list to {@link RankLadder#of(List)} which owns the ordering and uniqueness invariants. A
 * missing knob falls back to a sane default (an unnamed rank shows its id, a rank with no cost is free) so a
 * partially-trimmed file still yields a usable ladder.
 */
public final class RankLadders {

    /** The scoped key the {@code ranks.conf} sibling file is mounted under ({@code modules.ranks.ranks}). */
    private static final String LADDER = "ranks";

    private RankLadders() {}

    /** Parse the ladder from the module's scoped {@link ConfigStore} ({@code modules.ranks}). */
    public static RankLadder from(ConfigStore config) {
        Objects.requireNonNull(config, "config");
        List<Rank> ranks = new ArrayList<>();
        for (String id : config.getKeys(LADDER)) {
            ranks.add(readRank(config, id));
        }
        return RankLadder.of(ranks);
    }

    private static Rank readRank(ConfigStore config, String id) {
        String base = LADDER + "." + id;
        int order = config.getInt(base + ".order", 0);
        String displayName = config.getString(base + ".display-name", id);
        long cost = config.getLong(base + ".cost", 0L);
        List<String> requirements = config.getStringList(base + ".requirements", List.of());
        List<String> actions = config.getStringList(base + ".actions", List.of());
        return new Rank(RankId.of(id), order, displayName, cost, requirements, actions);
    }
}

package com.uxplima.uxmessentials.holograms.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.action.ClickAction;
import org.jspecify.annotations.Nullable;

/**
 * A mutable builder for {@link Hologram}, kept package-private so it is purely an internal mechanism: each
 * {@link Hologram} transition reads {@link Hologram#toBuilder()}, changes the one field it owns, and calls
 * {@link #build()}, which routes through the canonical {@code Hologram} constructor, so every validation and
 * range check still fires. Extracting the per-field copy boilerplate here keeps {@code Hologram} small without
 * changing its public surface.
 */
final class HologramBuilder {

    private HologramName name;
    private Position location;
    private HologramContent content;
    private Appearance appearance;
    private Visibility visibility;
    private Rotation rotation;
    private int refreshIntervalTicks;
    private Instant createdAt;
    private @Nullable String linkedNpcName;
    private @Nullable String clickCommand;
    private @Nullable LeaderboardSpec leaderboard;
    private @Nullable List<HologramPage> pages;
    private boolean growUp;
    private List<ClickAction> actions;

    HologramBuilder(Hologram source) {
        Objects.requireNonNull(source, "source");
        this.name = source.name();
        this.location = source.location();
        this.content = source.content();
        this.appearance = source.appearance();
        this.visibility = source.visibility();
        this.rotation = source.rotation();
        this.refreshIntervalTicks = source.refreshIntervalTicks();
        this.createdAt = source.createdAt();
        this.linkedNpcName = source.linkedNpcName();
        this.clickCommand = source.clickCommand();
        this.leaderboard = source.leaderboard();
        this.pages = source.pages();
        this.growUp = source.growUp();
        this.actions = source.actions();
    }

    HologramBuilder name(HologramName value) {
        this.name = value;
        return this;
    }

    HologramBuilder location(Position value) {
        this.location = value;
        return this;
    }

    HologramBuilder content(HologramContent value) {
        this.content = value;
        return this;
    }

    HologramBuilder appearance(Appearance value) {
        this.appearance = value;
        return this;
    }

    HologramBuilder visibility(Visibility value) {
        this.visibility = value;
        return this;
    }

    HologramBuilder rotation(Rotation value) {
        this.rotation = value;
        return this;
    }

    HologramBuilder refreshIntervalTicks(int value) {
        this.refreshIntervalTicks = value;
        return this;
    }

    HologramBuilder linkedNpcName(@Nullable String value) {
        this.linkedNpcName = value;
        return this;
    }

    HologramBuilder clickCommand(@Nullable String value) {
        this.clickCommand = value;
        return this;
    }

    HologramBuilder leaderboard(@Nullable LeaderboardSpec value) {
        this.leaderboard = value;
        return this;
    }

    HologramBuilder pages(@Nullable List<HologramPage> value) {
        this.pages = value;
        return this;
    }

    HologramBuilder growUp(boolean value) {
        this.growUp = value;
        return this;
    }

    HologramBuilder actions(List<ClickAction> value) {
        this.actions = value;
        return this;
    }

    Hologram build() {
        return new Hologram(
                name,
                location,
                content,
                appearance,
                visibility,
                rotation,
                refreshIntervalTicks,
                createdAt,
                linkedNpcName,
                clickCommand,
                leaderboard,
                pages,
                growUp,
                actions);
    }
}

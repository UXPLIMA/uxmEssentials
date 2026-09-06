package com.uxplima.uxmessentials.holograms.adapter.outbound;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import com.uxplima.uxmlib.hologram.HologramManager;
import com.uxplima.uxmlib.hologram.ModelHologram;
import org.jspecify.annotations.NullMarked;

/**
 * The renderer's view of a live uxmLib hologram, unifying the only operations the renderer performs on a
 * spawned entity (despawn, restrict-to-viewers, and per-viewer show/hide) across the text {@code Hologram}
 * and the item/block {@link ModelHologram}, which share that lifecycle but no common Java interface in the lib.
 * Each hologram type's spawn returns one of the two adapters below so the renderer's spawn, despawn and
 * visibility code is type-agnostic past the dispatch point.
 */
@NullMarked
interface RenderedHologram {

    /** The {@link #textEntityId()} of a hologram that carries no overridable text component (item/block). */
    int NO_ENTITY = -1;

    /** Despawn the backing entity through {@code manager} so it is also untracked. */
    void removeFrom(HologramManager manager);

    /** Make the entity visible only to explicitly shown players. */
    void restrictToViewers();

    /** Show the entity to {@code viewer}. */
    void show(Plugin plugin, Player viewer);

    /** Hide the entity from {@code viewer}. */
    void hide(Plugin plugin, Player viewer);

    /**
     * The backing {@code TextDisplay}'s entity id for a text hologram (so the renderer can target a per-viewer
     * text-override metadata packet at it), or {@link #NO_ENTITY} for an item or block hologram, which carries
     * no overridable text component.
     */
    int textEntityId();

    /** A {@link RenderedHologram} over a text {@code Hologram}. */
    static RenderedHologram ofText(com.uxplima.uxmlib.hologram.Hologram text) {
        return new RenderedHologram() {
            @Override
            public void removeFrom(HologramManager manager) {
                manager.remove(text);
            }

            @Override
            public void restrictToViewers() {
                text.restrictToViewers();
            }

            @Override
            public void show(Plugin plugin, Player viewer) {
                text.show(plugin, viewer);
            }

            @Override
            public void hide(Plugin plugin, Player viewer) {
                text.hide(plugin, viewer);
            }

            @Override
            public int textEntityId() {
                return text.entity().getEntityId();
            }
        };
    }

    /** A {@link RenderedHologram} over an item or block {@link ModelHologram}. */
    static RenderedHologram ofModel(ModelHologram model) {
        return new RenderedHologram() {
            @Override
            public void removeFrom(HologramManager manager) {
                manager.remove(model);
            }

            @Override
            public void restrictToViewers() {
                model.restrictToViewers();
            }

            @Override
            public void show(Plugin plugin, Player viewer) {
                model.show(plugin, viewer);
            }

            @Override
            public void hide(Plugin plugin, Player viewer) {
                model.hide(plugin, viewer);
            }

            @Override
            public int textEntityId() {
                // An item/block display has no text component to override per viewer.
                return NO_ENTITY;
            }
        };
    }

    /**
     * A {@link RenderedHologram} over a frozen decorative {@code entity} (an ENTITY hologram). The mob is the whole
     * hologram, there is no Display, so despawn removes the entity, visibility uses Paper's per-player entity
     * show/hide (and the default-visible toggle for the restrict-to-viewers modes), and there is no text component.
     */
    static RenderedHologram ofEntity(org.bukkit.entity.Entity entity) {
        return new RenderedHologram() {
            @Override
            public void removeFrom(HologramManager manager) {
                entity.remove();
            }

            @Override
            public void restrictToViewers() {
                entity.setVisibleByDefault(false);
            }

            @Override
            public void show(Plugin plugin, Player viewer) {
                viewer.showEntity(plugin, entity);
            }

            @Override
            public void hide(Plugin plugin, Player viewer) {
                viewer.hideEntity(plugin, entity);
            }

            @Override
            public int textEntityId() {
                return NO_ENTITY;
            }
        };
    }

    /**
     * Wrap {@code delegate} so its lifecycle also owns the {@code clickBox}. The {@code Interaction} entity spawned
     * beside a clickable hologram. Despawning the hologram (on stop, move, edit) removes the box too, so a hologram
     * never leaves an orphaned hitbox; visibility and the text-entity id pass straight through to the delegate (the
     * box is a non-persistent, invisible hitbox, not something a viewer is shown or hidden).
     */
    static RenderedHologram withClickBox(RenderedHologram delegate, org.bukkit.entity.Interaction clickBox) {
        return new RenderedHologram() {
            @Override
            public void removeFrom(HologramManager manager) {
                delegate.removeFrom(manager);
                clickBox.remove();
            }

            @Override
            public void restrictToViewers() {
                delegate.restrictToViewers();
            }

            @Override
            public void show(Plugin plugin, Player viewer) {
                delegate.show(plugin, viewer);
            }

            @Override
            public void hide(Plugin plugin, Player viewer) {
                delegate.hide(plugin, viewer);
            }

            @Override
            public int textEntityId() {
                return delegate.textEntityId();
            }
        };
    }
}

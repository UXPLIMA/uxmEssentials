package com.uxplima.uxmessentials.shared.adapter.outbound.hooks;

import java.util.Optional;

import org.bukkit.inventory.ItemStack;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The custom-head capability the menu engine reaches through the optional HeadDatabase hook. A skull icon can
 * name a HeadDatabase head by id (the {@code hdb:<id>} skull source); this seam resolves that id to its
 * {@link ItemStack}. When HeadDatabase is absent the whole capability is the no-op {@link #ABSENT}: the lookup
 * returns empty and the skull provider falls back to a plain player head, so a menu referencing an HDB head
 * still renders on a server that does not run HeadDatabase.
 *
 * <p>A lookup is best-effort by design. An unknown id, a HeadDatabase that has not yet finished loading its
 * head index (it builds the index asynchronously on startup, so an early read can come back empty), or a
 * reflective read that fails all collapse to {@link Optional#empty()} rather than throwing, a head lookup
 * degrades to the plain-head fallback, it never breaks a menu render. A null or blank id is empty as well.
 *
 * <p>The lookup touches the item on the calling thread with no I/O, so a caller invokes it on the viewer's
 * entity thread: the menu engine's item-build chain already runs there. This interface and its {@link #ABSENT}
 * default reference none of {@code me.arcaniax}; the only class that touches HeadDatabase is the real query
 * behind {@link HeadDatabaseHook#whenPresent}, and even that reaches it purely by reflection. So a
 * HeadDatabase-less server loads no SDK class when resolving this capability.
 */
@NullMarked
public interface HeadQuery {

    /** Whether a live HeadDatabase is present and usable, false for the absent default. */
    boolean available();

    /**
     * The HeadDatabase head for {@code id}, or {@link Optional#empty()} when HeadDatabase is unavailable, the id
     * is unknown or not yet indexed, or a reflective read fails. A null or blank id is empty. Never throws, so a
     * skull provider can call this on the render thread and fall back to a plain head on an empty result.
     */
    Optional<ItemStack> head(@Nullable String id);

    /**
     * The safe no-op used when HeadDatabase is absent: never available, and {@link #head} is always empty, so a
     * skull source naming an HDB head degrades to a plain player head. This default carries no
     * {@code me.arcaniax} type, so loading it on a HeadDatabase-less server pulls in no SDK class.
     */
    HeadQuery ABSENT = new HeadQuery() {
        @Override
        public boolean available() {
            return false;
        }

        @Override
        public Optional<ItemStack> head(@Nullable String id) {
            return Optional.empty();
        }
    };
}

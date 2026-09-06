package com.uxplima.uxmessentials.holograms.domain;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * Who may see a hologram, and how far away it stays visible, the per-player visibility
 * controls, kept separate from the visual {@link Appearance} so a visibility change is one transition rather
 * than a restyle. Pure value object (no Bukkit): a {@link Mode}, an optional permission node, and a distance in
 * blocks; the adapter maps each onto Paper's native per-viewer {@code show/hideEntity} and the display view
 * range at the rendering boundary.
 *
 * <p>{@link Mode#ALL} (the default) shows the hologram to everyone, the cheap, static-hologram path with no
 * per-viewer bookkeeping. {@link Mode#PERMISSION} shows it only to players who hold {@link #permission()}; the
 * node is operator-chosen and so dynamic (it is not a fixed plugin-declared node). {@link Mode#MANUAL} hides the
 * hologram from everyone until an operator explicitly shows it to a named player; the set of shown players is a
 * per-hologram, persisted association the operator grows with {@code /hologram show} and shrinks with {@code
 * /hologram hide}, so it survives a restart. Like {@code ALL}, {@code MANUAL} carries no permission node, its
 * gate is the explicit viewer set, not a node.
 *
 * <p>{@link #distance()} is the visibility radius in blocks: {@link #UNLIMITED} (0, the default) leaves the
 * vanilla display tracking range untouched, and a positive value culls the hologram beyond that many blocks.
 * The renderer maps blocks onto the native display view-range multiplier.
 *
 * @param mode who may see the hologram
 * @param permission the node required for {@link Mode#PERMISSION}, or {@code null} for {@link Mode#ALL}
 * @param distance the visibility radius in blocks, or {@link #UNLIMITED} (0) for the vanilla range
 */
public record Visibility(Mode mode, @Nullable String permission, int distance) {

    /** A distance of 0 means "unlimited": leave the vanilla display tracking range untouched. */
    public static final int UNLIMITED = 0;

    /** The largest visibility radius an operator may set, in blocks (the vanilla display tracking ceiling). */
    public static final int MAX_DISTANCE = 256;

    /** How the renderer decides who may see a hologram. */
    public enum Mode {
        /** Everyone sees the hologram: the default, no per-viewer bookkeeping. */
        ALL,
        /** Only players who hold the hologram's {@link Visibility#permission()} node see it. */
        PERMISSION,
        /** Hidden from everyone until an operator explicitly shows it to a named player. */
        MANUAL
    }

    public Visibility {
        Objects.requireNonNull(mode, "mode");
        if (mode == Mode.PERMISSION && (permission == null || permission.isBlank())) {
            throw new IllegalArgumentException("PERMISSION visibility needs a non-blank permission node");
        }
        if (mode != Mode.PERMISSION && permission != null) {
            throw new IllegalArgumentException(mode + " visibility carries no permission node");
        }
        if (distance < 0) {
            throw new IllegalArgumentException("distance must not be negative: " + distance);
        }
    }

    /** The default: visible to everyone, no permission node, vanilla view range. */
    public static Visibility everyone() {
        return new Visibility(Mode.ALL, null, UNLIMITED);
    }

    /** Visible only to players holding {@code node}, keeping the current distance. */
    public static Visibility restrictedTo(String node) {
        return new Visibility(Mode.PERMISSION, requireNode(node), UNLIMITED);
    }

    /** Hidden from everyone until shown to a named player, no permission node, vanilla view range. */
    public static Visibility manual() {
        return new Visibility(Mode.MANUAL, null, UNLIMITED);
    }

    /** A copy whose mode becomes {@link Mode#ALL} (dropping any permission node), keeping the distance. */
    public Visibility toEveryone() {
        return new Visibility(Mode.ALL, null, distance);
    }

    /** A copy whose mode becomes {@link Mode#PERMISSION} guarded by {@code node}, keeping the distance. */
    public Visibility toPermission(String node) {
        return new Visibility(Mode.PERMISSION, requireNode(node), distance);
    }

    /** A copy whose mode becomes {@link Mode#MANUAL} (dropping any permission node), keeping the distance. */
    public Visibility toManual() {
        return new Visibility(Mode.MANUAL, null, distance);
    }

    /** A copy with the visibility radius set to {@code blocks} (0 = unlimited), keeping the mode and node. */
    public Visibility withDistance(int blocks) {
        return new Visibility(mode, permission, blocks);
    }

    /** Clamp a raw distance to the accepted range, so operator input never throws at the command boundary. */
    public static int clampDistance(int raw) {
        return Math.min(MAX_DISTANCE, Math.max(UNLIMITED, raw));
    }

    /** Whether this hologram is restricted to permission-holders rather than visible to everyone. */
    public boolean isPermissionGated() {
        return mode == Mode.PERMISSION;
    }

    /** Whether this hologram is hidden by default and shown only to its explicit per-player viewer set. */
    public boolean isManual() {
        return mode == Mode.MANUAL;
    }

    /** Whether a finite visibility radius is set (a positive distance), rather than the vanilla range. */
    public boolean hasDistanceLimit() {
        return distance > UNLIMITED;
    }

    private static String requireNode(String node) {
        Objects.requireNonNull(node, "node");
        if (node.isBlank()) {
            throw new IllegalArgumentException("permission node must be non-blank");
        }
        return node;
    }
}

package com.uxplima.uxmessentials.holograms.application;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.uxplima.uxmessentials.holograms.application.port.HologramRepository;
import com.uxplima.uxmessentials.holograms.domain.Appearance;
import com.uxplima.uxmessentials.holograms.domain.Hologram;
import com.uxplima.uxmessentials.holograms.domain.HologramError;
import com.uxplima.uxmessentials.holograms.domain.HologramName;
import com.uxplima.uxmessentials.holograms.domain.Rotation;
import com.uxplima.uxmessentials.holograms.domain.Transform;
import com.uxplima.uxmessentials.holograms.domain.Visibility;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Position;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.shared.domain.Unit;

/**
 * {@code /hologram info <name>}: print a hologram's stored properties to the operator, its location, line
 * count, type, billboard, background, scale, view range, visibility, refresh interval and rotation, as one
 * header followed by structured {@link HologramsMessageKey} entries (the values interpolate the data). A name no
 * hologram exists at is rejected with {@link HologramError#NOT_FOUND}. The operator-only permission is enforced
 * at the adapter gate.
 */
public final class DescribeHologram {

    private static final String NO_OVERRIDE = "default";
    private static final String UNLIMITED = "unlimited";

    private final HologramRepository repository;
    private final Notifier notifier;

    public DescribeHologram(HologramRepository repository, Notifier notifier) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Print every property of the hologram {@code name} to {@code viewer}, or reject when no such hologram. */
    public Result<Unit, HologramError> describe(PlayerRef viewer, HologramName name) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(name, "name");
        Optional<Hologram> existing = repository.find(name);
        if (existing.isEmpty()) {
            notifier.send(viewer, HologramError.NOT_FOUND.messageKey(), Map.of("name", name.value()));
            return Result.err(HologramError.NOT_FOUND);
        }
        emit(viewer, existing.get());
        return Result.ok();
    }

    private void emit(PlayerRef viewer, Hologram hologram) {
        notifier.send(
                viewer,
                HologramsMessageKey.HOLOGRAM_INFO_HEADER,
                Map.of("name", hologram.name().value()));
        notifier.send(viewer, HologramsMessageKey.HOLOGRAM_INFO_LOCATION, location(hologram.location()));
        notifier.send(
                viewer,
                HologramsMessageKey.HOLOGRAM_INFO_LINES,
                Map.of("lines", Integer.toString(hologram.lineCount())));
        notifier.send(
                viewer,
                HologramsMessageKey.HOLOGRAM_INFO_TYPE,
                Map.of("type", hologram.type().name()));
        emitAppearance(viewer, hologram.appearance());
        notifier.send(viewer, HologramsMessageKey.HOLOGRAM_INFO_VISIBILITY, visibility(hologram.visibility()));
        notifier.send(
                viewer,
                HologramsMessageKey.HOLOGRAM_INFO_REFRESH,
                Map.of("ticks", Integer.toString(hologram.refreshIntervalTicks())));
        notifier.send(viewer, HologramsMessageKey.HOLOGRAM_INFO_ROTATION, rotation(hologram.rotation()));
        String linkedNpc = hologram.linkedNpcName();
        if (linkedNpc != null) {
            notifier.send(viewer, HologramsMessageKey.HOLOGRAM_INFO_LINKED_NPC, Map.of("npc", linkedNpc));
        }
    }

    private void emitAppearance(PlayerRef viewer, Appearance appearance) {
        notifier.send(
                viewer,
                HologramsMessageKey.HOLOGRAM_INFO_BILLBOARD,
                Map.of("billboard", appearance.billboard().name()));
        notifier.send(
                viewer,
                HologramsMessageKey.HOLOGRAM_INFO_BACKGROUND,
                Map.of("background", appearance.hasBackground() ? argbHex(appearance.backgroundArgb()) : NO_OVERRIDE));
        notifier.send(viewer, HologramsMessageKey.HOLOGRAM_INFO_SCALE, scale(appearance.transform()));
        notifier.send(
                viewer,
                HologramsMessageKey.HOLOGRAM_INFO_VIEW_RANGE,
                Map.of("range", Float.toString(appearance.viewRange())));
        notifier.send(
                viewer,
                HologramsMessageKey.HOLOGRAM_INFO_ALIGNMENT,
                Map.of("alignment", appearance.alignment().name()));
        notifier.send(
                viewer,
                HologramsMessageKey.HOLOGRAM_INFO_SEE_THROUGH,
                Map.of("seethrough", Boolean.toString(appearance.seeThrough())));
        notifier.send(viewer, HologramsMessageKey.HOLOGRAM_INFO_TRANSLATION, translation(appearance.transform()));
        notifier.send(viewer, HologramsMessageKey.HOLOGRAM_INFO_SHADOW, shadow(appearance));
    }

    private static Map<String, String> scale(Transform transform) {
        return Map.of(
                "x", Float.toString(transform.scaleX()),
                "y", Float.toString(transform.scaleY()),
                "z", Float.toString(transform.scaleZ()));
    }

    private static Map<String, String> translation(Transform transform) {
        return Map.of(
                "x", Float.toString(transform.translationX()),
                "y", Float.toString(transform.translationY()),
                "z", Float.toString(transform.translationZ()));
    }

    private static Map<String, String> shadow(Appearance appearance) {
        return Map.of(
                "radius", appearance.hasShadowRadius() ? Float.toString(appearance.shadowRadius()) : NO_OVERRIDE,
                "strength", appearance.hasShadowStrength() ? Float.toString(appearance.shadowStrength()) : NO_OVERRIDE);
    }

    private static Map<String, String> location(Position at) {
        return Map.of(
                "world", at.world().name(),
                "x", String.format(Locale.ROOT, "%.2f", at.x()),
                "y", String.format(Locale.ROOT, "%.2f", at.y()),
                "z", String.format(Locale.ROOT, "%.2f", at.z()));
    }

    private static Map<String, String> visibility(Visibility visibility) {
        return Map.of(
                "mode", visibility.mode().name(),
                "permission", visibility.permission() == null ? "" : visibility.permission(),
                "distance", visibility.hasDistanceLimit() ? Integer.toString(visibility.distance()) : UNLIMITED);
    }

    private static Map<String, String> rotation(Rotation rotation) {
        return Map.of("yaw", Float.toString(rotation.yaw()), "pitch", Float.toString(rotation.pitch()));
    }

    private static String argbHex(int argb) {
        return String.format(Locale.ROOT, "#%08X", argb);
    }
}

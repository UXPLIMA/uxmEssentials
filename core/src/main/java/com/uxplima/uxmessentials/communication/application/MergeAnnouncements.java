package com.uxplima.uxmessentials.communication.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.uxplima.uxmessentials.communication.application.port.AnnouncementStore;
import com.uxplima.uxmessentials.communication.application.port.AnnouncerSettingsStore;
import com.uxplima.uxmessentials.communication.domain.Announcement;
import com.uxplima.uxmessentials.communication.domain.AnnouncerConfig;
import com.uxplima.uxmessentials.communication.domain.AnnouncerSettings;
import com.uxplima.uxmessentials.communication.domain.StoredAnnouncement;

/**
 * Widens the announcer's source from the file-managed {@link AnnouncerConfig} alone to that config <em>plus</em> the
 * enabled, editor-managed announcements held in the {@link AnnouncementStore}. The config set stays backward
 * compatible (an operator who never touches the editor sees no change), and the store set is the GUI-managed
 * surface; a disabled store announcement is excluded here, so toggling enabled in the editor takes an announcement
 * in or out of the rotation on the next announcer tick with no reload.
 *
 * <p>This widens the source set and folds in the persisted global override: the merged config carries the file's
 * ordering, and the default interval and min-players gate are the operator's in-game override from the
 * {@link AnnouncerSettingsStore} when set, else the file values, so the settings screen overrides the file default
 * without a reload, the same way an enabled store announcement joins the rotation without one. When a store
 * announcement's id collides with a config one, the store version wins. The editor is the authoritative surface
 * for an id it manages, and a collision is an operator editing an id that also exists in the file, where the live
 * in-game edit is the one they expect to see. The merged announcement list preserves config order first, then
 * appends the store ids the config did not already carry.
 *
 * <p>Pure: it reads the store through the port and combines value objects, holding no Bukkit dependency, so the
 * supplier it builds is the seam both {@code NextAnnouncement} (the rotation) and the adapter's override-loop timer
 * read through.
 */
public final class MergeAnnouncements {

    private final AnnouncementStore store;
    private final AnnouncerSettingsStore settingsStore;

    public MergeAnnouncements(AnnouncementStore store, AnnouncerSettingsStore settingsStore) {
        this.store = Objects.requireNonNull(store, "store");
        this.settingsStore = Objects.requireNonNull(settingsStore, "settingsStore");
    }

    /**
     * The merged config: the file default interval and min-players gate folded with the persisted global override,
     * then {@code config}'s announcements first (with any whose id a store announcement reuses replaced by the store
     * version), then the remaining enabled store announcements appended. The ordering is carried straight from
     * {@code config}. A config with no announcements and no enabled store announcements yields an empty config that
     * never fires, exactly as the file config would (the override interval/gate are moot when nothing rotates).
     */
    public AnnouncerConfig merge(AnnouncerConfig config) {
        Objects.requireNonNull(config, "config");
        Map<String, Announcement> stored = new LinkedHashMap<>();
        for (StoredAnnouncement announcement : store.enabled()) {
            stored.put(announcement.id(), announcement.toAnnouncement());
        }
        List<Announcement> merged = new ArrayList<>();
        for (Announcement fromConfig : config.announcements()) {
            // A store announcement under the same id supersedes the file one; otherwise keep the file announcement.
            merged.add(stored.getOrDefault(fromConfig.id(), fromConfig));
        }
        for (Announcement fromStore : stored.values()) {
            if (config.announcements().stream().noneMatch(a -> a.id().equals(fromStore.id()))) {
                merged.add(fromStore);
            }
        }
        if (merged.isEmpty()) {
            return AnnouncerConfig.empty();
        }
        AnnouncerSettings settings = settingsStore.load();
        AnnouncerConfig withAnnouncements =
                new AnnouncerConfig(config.defaultInterval(), config.minOnlinePlayers(), config.ordering(), merged);
        return settings.applyTo(withAnnouncements);
    }
}

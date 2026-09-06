package com.uxplima.uxmessentials.poses.application;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;

/**
 * The poses context's user-visible message keys. Each constant maps 1:1 to a kebab-case catalog key in
 * {@code messages_<lang>.conf} ({@code POSES_SITTING} ↔ {@code poses.sitting}); the constant is the compile-time
 * handle, the catalog holds the text. There are no inline player-facing literals anywhere in the context, every
 * message resolves through one of these.
 *
 * <p>Per the i18n contract a disabled module still ships its keys so the catalog stays whole and the locale-parity
 * guard sees the full {@code en} key set. This is the Phase-0 seed: the feedback lines the first behaviour phases
 * ({@code /sit} and the region gate) will resolve through. Later phases add their own keys here as their verbs land.
 */
public enum PosesMessageKey implements MessageKey {

    // Sit/pose feedback: sent to the player when a pose begins and when it ends.
    POSES_SITTING("poses.sitting"),
    POSES_STOOD_UP("poses.stood-up"),

    // Refusal: the pose could not begin here (a protected region, a disabled sub-feature, or nothing to sit on).
    POSES_CANNOT_HERE("poses.cannot-here"),

    // Refusal: sitting is switched off, the player already holds a pose, or the seat is already taken.
    POSES_SIT_DISABLED("poses.sit-disabled"),
    POSES_ALREADY_POSING("poses.already-posing"),
    POSES_SEAT_OCCUPIED("poses.seat-occupied"),

    // Free poses: /lay, /bellyflop, /spin feedback and the shared "this pose is switched off" refusal.
    POSES_NOW_LAYING("poses.now-laying"),
    POSES_NOW_BELLYFLOPPING("poses.now-bellyflopping"),
    POSES_NOW_SPINNING("poses.now-spinning"),
    POSES_POSE_DISABLED("poses.pose-disabled"),

    // Crawl: /crawl is a toggle, so it has both a start and a stop line plus its own disabled refusal.
    POSES_NOW_CRAWLING("poses.now-crawling"),
    POSES_CRAWL_STOPPED("poses.crawl-stopped"),
    POSES_CRAWL_DISABLED("poses.crawl-disabled"),

    // Player-sit (stacking): sitting on another player's shoulders and the personal opt-out toggle.
    POSES_PLAYERSIT_DISABLED("poses.playersit-disabled"),
    POSES_PLAYERSIT_TARGET_REFUSES("poses.playersit-target-refuses"),
    POSES_CANNOT_SIT_ON_SELF("poses.cannot-sit-on-self"),
    POSES_PLAYERSIT_NOW_ALLOWED("poses.playersit-now-allowed"),
    POSES_PLAYERSIT_NOW_REFUSED("poses.playersit-now-refused"),

    // The bare /poses root's usage line: shown to a non-player sender who cannot open the GUI.
    POSES_USAGE("poses.usage"),

    // The /poses settings & status panel: the title, the shared value line, the on/off values, and the two button
    // labels: the current-pose status display and the player-sit opt-out.
    POSES_GUI_TITLE("poses.gui.title"),
    POSES_GUI_BACK("poses.gui.back"),
    POSES_GUI_VALUE_LORE("poses.gui.value-lore"),
    POSES_GUI_VALUE_ON("poses.gui.value-on"),
    POSES_GUI_VALUE_OFF("poses.gui.value-off"),
    POSES_GUI_STATUS("poses.gui.status"),
    POSES_GUI_PLAYERSIT("poses.gui.playersit");

    private final String key;

    PosesMessageKey(String key) {
        this.key = key;
    }

    @Override
    public String key() {
        return key;
    }
}

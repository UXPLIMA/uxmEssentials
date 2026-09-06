package com.uxplima.uxmessentials.moderation.adapter.inbound.gui;

import java.util.Optional;

import com.uxplima.uxmessentials.moderation.application.ModerationMessageKey;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The sanction the bare-command GUI flow issues, {@code /ban}, {@code /mute}, {@code /tempban},
 * {@code /tempmute}, {@code /warn} or {@code /banip}. Bundled with the catalog labels its picker title and
 * confirm screen render under, and the use-case call its confirm buttons fire. One enum value drives the whole
 * flow so {@link PlayerPickerView}, {@link DurationPickerView} and {@link PunishmentConfirmView} stay generic
 * over the verb: the picker title, the confirm-button labels, and the {@link Executor} that performs the audited
 * use-case call are all read from here.
 *
 * <p>Two axes vary per verb. The timed verbs ({@code /tempban}, {@code /tempmute}) carry a duration-picker title
 * and route through {@link DurationPickerView} between the player picker and the confirm screen ({@link #timed()}
 * is true); the rest skip straight to confirm. {@code /banip} cannot be suppressed. Its use case has no silent
 * form, so it declares no silent labels ({@link #silentSupported()} is false) and the confirm screen omits the
 * silent button; every other verb shows both the normal and the silent confirm.
 *
 * <p>Distinct from {@link PunishmentKind} (which drives the management GUI's revoke dispatch and includes
 * {@code JAIL}): this enum is the creation-side sanctions a bare command opens, each carrying its creation-side
 * labels rather than a revoke label.
 */
@NullMarked
public enum PunishmentAction {
    BAN(
            ModerationMessageKey.MOD_GUI_PICK_BAN_TITLE,
            ModerationMessageKey.MOD_GUI_CONFIRM_BAN_TITLE,
            ModerationMessageKey.MOD_GUI_CONFIRM_BAN,
            ModerationMessageKey.MOD_GUI_CONFIRM_BAN_LORE,
            Optional.of(ModerationMessageKey.MOD_GUI_CONFIRM_BAN_SILENT),
            Optional.of(ModerationMessageKey.MOD_GUI_CONFIRM_BAN_SILENT_LORE),
            Optional.empty()),
    MUTE(
            ModerationMessageKey.MOD_GUI_PICK_MUTE_TITLE,
            ModerationMessageKey.MOD_GUI_CONFIRM_MUTE_TITLE,
            ModerationMessageKey.MOD_GUI_CONFIRM_MUTE,
            ModerationMessageKey.MOD_GUI_CONFIRM_MUTE_LORE,
            Optional.of(ModerationMessageKey.MOD_GUI_CONFIRM_MUTE_SILENT),
            Optional.of(ModerationMessageKey.MOD_GUI_CONFIRM_MUTE_SILENT_LORE),
            Optional.empty()),
    TEMPBAN(
            ModerationMessageKey.MOD_GUI_PICK_TEMPBAN_TITLE,
            ModerationMessageKey.MOD_GUI_CONFIRM_TEMPBAN_TITLE,
            ModerationMessageKey.MOD_GUI_CONFIRM_TEMPBAN,
            ModerationMessageKey.MOD_GUI_CONFIRM_TEMPBAN_LORE,
            Optional.of(ModerationMessageKey.MOD_GUI_CONFIRM_TEMPBAN_SILENT),
            Optional.of(ModerationMessageKey.MOD_GUI_CONFIRM_TEMPBAN_SILENT_LORE),
            Optional.of(ModerationMessageKey.MOD_GUI_DURATION_TEMPBAN_TITLE)),
    TEMPMUTE(
            ModerationMessageKey.MOD_GUI_PICK_TEMPMUTE_TITLE,
            ModerationMessageKey.MOD_GUI_CONFIRM_TEMPMUTE_TITLE,
            ModerationMessageKey.MOD_GUI_CONFIRM_TEMPMUTE,
            ModerationMessageKey.MOD_GUI_CONFIRM_TEMPMUTE_LORE,
            Optional.of(ModerationMessageKey.MOD_GUI_CONFIRM_TEMPMUTE_SILENT),
            Optional.of(ModerationMessageKey.MOD_GUI_CONFIRM_TEMPMUTE_SILENT_LORE),
            Optional.of(ModerationMessageKey.MOD_GUI_DURATION_TEMPMUTE_TITLE)),
    WARN(
            ModerationMessageKey.MOD_GUI_PICK_WARN_TITLE,
            ModerationMessageKey.MOD_GUI_CONFIRM_WARN_TITLE,
            ModerationMessageKey.MOD_GUI_CONFIRM_WARN,
            ModerationMessageKey.MOD_GUI_CONFIRM_WARN_LORE,
            Optional.of(ModerationMessageKey.MOD_GUI_CONFIRM_WARN_SILENT),
            Optional.of(ModerationMessageKey.MOD_GUI_CONFIRM_WARN_SILENT_LORE),
            Optional.empty()),
    BANIP(
            ModerationMessageKey.MOD_GUI_PICK_BANIP_TITLE,
            ModerationMessageKey.MOD_GUI_CONFIRM_BANIP_TITLE,
            ModerationMessageKey.MOD_GUI_CONFIRM_BANIP,
            ModerationMessageKey.MOD_GUI_CONFIRM_BANIP_LORE,
            Optional.empty(),
            Optional.empty(),
            Optional.empty());

    private final ModerationMessageKey pickerTitle;
    private final ModerationMessageKey confirmTitle;
    private final ModerationMessageKey applyLabel;
    private final ModerationMessageKey applyLore;
    private final Optional<ModerationMessageKey> silentLabel;
    private final Optional<ModerationMessageKey> silentLore;
    private final Optional<ModerationMessageKey> durationTitle;

    PunishmentAction(
            ModerationMessageKey pickerTitle,
            ModerationMessageKey confirmTitle,
            ModerationMessageKey applyLabel,
            ModerationMessageKey applyLore,
            Optional<ModerationMessageKey> silentLabel,
            Optional<ModerationMessageKey> silentLore,
            Optional<ModerationMessageKey> durationTitle) {
        this.pickerTitle = pickerTitle;
        this.confirmTitle = confirmTitle;
        this.applyLabel = applyLabel;
        this.applyLore = applyLore;
        this.silentLabel = silentLabel;
        this.silentLore = silentLore;
        this.durationTitle = durationTitle;
    }

    /** The player-picker title key for this sanction. */
    public ModerationMessageKey pickerTitle() {
        return pickerTitle;
    }

    /** The confirm-screen title key (rendered with the target name appended outside the tag). */
    public ModerationMessageKey confirmTitle() {
        return confirmTitle;
    }

    /** The normal (broadcast) confirm-button label key. */
    public ModerationMessageKey applyLabel() {
        return applyLabel;
    }

    /** The normal confirm-button lore key. */
    public ModerationMessageKey applyLore() {
        return applyLore;
    }

    /** The silent confirm-button label key, present only when the verb can be suppressed. */
    public Optional<ModerationMessageKey> silentLabel() {
        return silentLabel;
    }

    /** The silent confirm-button lore key, present only when the verb can be suppressed. */
    public Optional<ModerationMessageKey> silentLore() {
        return silentLore;
    }

    /** Whether the confirm screen shows the silent button (false for {@code /banip}, which cannot be silent). */
    public boolean silentSupported() {
        return silentLabel.isPresent();
    }

    /** Whether this verb routes through the duration picker before the confirm screen ({@code /tempban}/{@code /tempmute}). */
    public boolean timed() {
        return durationTitle.isPresent();
    }

    /** The duration-picker title key, present only for the timed verbs. */
    public Optional<ModerationMessageKey> durationTitle() {
        return durationTitle;
    }

    /**
     * Performs the audited use-case call for this sanction. The confirm view supplies the actor, the chosen
     * target, the optional reason captured on the confirm screen, and whether the broadcast is suppressed; the
     * caller's implementation routes to the matching {@code services.*().*(...)} use case. For a timed verb the
     * chosen duration string is bound into the implementation before it reaches the confirm view, so the executor
     * signature stays uniform across verbs. It is passed to {@link PunishmentConfirmView#open} rather than held on
     * the enum, since it needs the wired {@code ModerationServices} the enum cannot see.
     */
    @FunctionalInterface
    public interface Executor {

        /** Issue the sanction on {@code target} by {@code actor}, optionally silent, with an optional reason. */
        void execute(PlayerRef actor, PlayerRef target, Optional<String> reason, boolean silent);
    }
}

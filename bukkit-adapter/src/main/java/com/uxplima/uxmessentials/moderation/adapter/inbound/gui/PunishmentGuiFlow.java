package com.uxplima.uxmessentials.moderation.adapter.inbound.gui;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.moderation.adapter.ModerationServices;
import com.uxplima.uxmessentials.moderation.application.BanIp;
import com.uxplima.uxmessentials.moderation.application.ModerationMessageKey;
import com.uxplima.uxmessentials.moderation.domain.SanctionDuration;
import com.uxplima.uxmessentials.moderation.domain.SeenRecord;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.DurationPickerView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.PlayerPickerView;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * The bare-command GUI flow for the named sanctions: a player picker into a per-target confirm screen, ending in
 * the existing audited use-case call. {@code BanCommand}, {@code MuteCommand}, {@code TempbanCommand},
 * {@code TempmuteCommand}, {@code WarnCommand} and {@code BanipCommand} expose this as their
 * {@link com.uxplima.uxmessentials.shared.adapter.inbound.command.CommandRegistration#guiRoot() bare-root}
 * opener, so {@code /ban} with no arguments opens the picker while {@code /ban <player> [-s] [reason]} stays the
 * raw subcommand. One flow instance serves every sanction; {@link #open} is parameterised by {@link
 * PunishmentAction}.
 *
 * <p>The two timed verbs ({@code /tempban}, {@code /tempmute}) route picker → {@link DurationPickerView} →
 * confirm, carrying the chosen duration string immutably through to the executor; every other verb skips the
 * duration step. The reusable views stay generic: this flow supplies the per-verb titles, the offline-name
 * resolver (the moderation {@code TargetResolver}, so a typed offline name still resolves), the unknown-player
 * reply key, the duration validator (backed by {@link SanctionDuration}), and the pick/confirm callbacks. The
 * {@link PunishmentAction.Executor} is the only verb-specific code. It routes the chosen target to the matching
 * audited use case. No sanction is issued until a confirm button is clicked.
 *
 * <p>{@code /banip} bans the picked player's last-known address: its executor resolves the IP from the same
 * DB-backed seen record {@code /banip <player>} reads, and a target with no recorded IP gets the same
 * {@code SEENIP_NO_IP} notice the raw command gives rather than a silent no-op.
 */
@NullMarked
public final class PunishmentGuiFlow {

    /** A permanent mute carries no duration token; the timed form is {@code /tempmute}. */
    private static final String PERMANENT = "";

    /** {@code /banip} is permanent: its address ban carries no duration token. */
    private static final String NO_DURATION = "";

    private final ModerationServices services;
    private final PlayerPickerView picker;
    private final DurationPickerView durations;
    private final PunishmentConfirmView confirm;
    private final Messages messages;
    private final MessageSink sink;

    public PunishmentGuiFlow(
            ModerationServices services,
            PlayerPickerView picker,
            DurationPickerView durations,
            PunishmentConfirmView confirm,
            Messages messages,
            MessageSink sink) {
        this.services = Objects.requireNonNull(services, "services");
        this.picker = Objects.requireNonNull(picker, "picker");
        this.durations = Objects.requireNonNull(durations, "durations");
        this.confirm = Objects.requireNonNull(confirm, "confirm");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    /** Open the picker for {@code action}; a chosen target advances to the duration step or the confirm screen. */
    public void open(Player viewer, PlayerRef viewerRef, PunishmentAction action) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(viewerRef, "viewerRef");
        Objects.requireNonNull(action, "action");
        picker.open(viewer, viewerRef, pickRequest(viewer, viewerRef, action));
    }

    private PlayerPickerView.Request pickRequest(Player viewer, PlayerRef viewerRef, PunishmentAction action) {
        return new PlayerPickerView.Request(
                action.pickerTitle(),
                target -> onTargetPicked(viewer, viewerRef, action, target),
                name -> services.targets().resolve(name),
                ModerationMessageKey.UNKNOWN_TARGET);
    }

    /** A timed verb advances to the duration picker; the rest open the confirm screen straight away. */
    private void onTargetPicked(Player viewer, PlayerRef viewerRef, PunishmentAction action, PlayerRef target) {
        if (action.timed()) {
            openDuration(viewer, viewerRef, action, target);
        } else {
            openConfirm(viewer, viewerRef, action, target, executor(action, NO_DURATION));
        }
    }

    private void openDuration(Player viewer, PlayerRef viewerRef, PunishmentAction action, PlayerRef target) {
        durations.open(
                viewer,
                viewerRef,
                new DurationPickerView.Request(
                        action.durationTitle().orElseThrow(),
                        DurationPickerView.defaultPresets(),
                        duration -> openConfirm(viewer, viewerRef, action, target, executor(action, duration)),
                        PunishmentGuiFlow::isTimedDuration,
                        ModerationMessageKey.MOD_GUI_DURATION_REJECT,
                        Optional.of(() -> open(viewer, viewerRef, action))));
    }

    private void openConfirm(
            Player viewer,
            PlayerRef viewerRef,
            PunishmentAction action,
            PlayerRef target,
            PunishmentAction.Executor executor) {
        confirm.open(viewer, viewerRef, target, action, executor, () -> open(viewer, viewerRef, action));
    }

    /** A timed verb accepts only a positive, well-formed span: a permanent or malformed parse is rejected. */
    private static boolean isTimedDuration(String raw) {
        return SanctionDuration.parse(raw).duration().isPresent();
    }

    /**
     * The verb-specific use-case call. For the timed verbs the chosen {@code duration} string is bound here so
     * the executor signature stays uniform; for {@code /banip} the target's last-known IP is resolved at confirm
     * time and a missing address yields the {@code SEENIP_NO_IP} notice rather than a sanction.
     */
    private PunishmentAction.Executor executor(PunishmentAction action, String duration) {
        return switch (action) {
            case BAN -> (actor, target, reason, silent) -> services.ban().ban(actor, target, reason, silent);
            case MUTE ->
                (actor, target, reason, silent) -> services.mute().mute(actor, target, PERMANENT, reason, silent);
            case TEMPBAN ->
                (actor, target, reason, silent) -> services.tempBan().tempban(actor, target, duration, reason, silent);
            case TEMPMUTE ->
                (actor, target, reason, silent) -> services.mute().mute(actor, target, duration, reason, silent);
            case WARN -> (actor, target, reason, silent) -> services.warn().warn(actor, target, reason, silent);
            case BANIP -> (actor, target, reason, silent) -> banIp(actor, target, reason);
        };
    }

    /** Ban the picked player's last-known address, mirroring {@code /banip <player>}'s seen-record lookup. */
    private void banIp(PlayerRef actor, PlayerRef target, Optional<String> reason) {
        Optional<String> ip = services.repository().seen(target).flatMap(SeenRecord::lastIp);
        if (ip.isEmpty()) {
            sink.deliver(
                    actor, messages.resolve(actor, ModerationMessageKey.SEENIP_NO_IP, Map.of("player", target.name())));
            return;
        }
        services.banIp().banIp(actor, new BanIp.Target(ip.get(), Optional.of(target.uuid())), NO_DURATION, reason);
    }
}

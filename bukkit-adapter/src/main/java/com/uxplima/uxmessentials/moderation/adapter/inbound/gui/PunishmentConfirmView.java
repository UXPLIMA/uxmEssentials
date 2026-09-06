package com.uxplima.uxmessentials.moderation.adapter.inbound.gui;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.moderation.application.ModerationMessageKey;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * Registers the per-target punishment confirm screen with the menu engine and opens it. The bare {@code /ban},
 * {@code /mute}, {@code /tempban}, {@code /tempmute}, {@code /warn} and {@code /banip} GUI flow opens this once a
 * target is chosen in the {@link com.uxplima.uxmessentials.shared.adapter.inbound.gui.PlayerPickerView}. It shows
 * the target's head, two unambiguous confirm actions, apply (broadcast) and apply silently, an optional "set
 * reason" button that captures a free-text reason through the shared text-input seam, and a back button to the
 * picker. One spec serves every sanction: the {@link Confirm} subject carries the {@link PunishmentAction}, so the
 * title and the two confirm-button labels resolve through subject-driven catalog keys, and the
 * {@link PunishmentAction.Executor} the confirm click runs is carried on the subject too. This class holds no
 * ban/mute-specific branch.
 *
 * <p>{@code /banip} has no silent form, so its silent button is hidden by the
 * {@code moderation:confirm-silent-offered} view condition. The confirm click hops to the actor's entity region
 * thread, runs the audited use-case call there, and closes: the use case itself kicks/notifies and broadcasts.
 * The reason is carried across the input round-trip by reopening the screen with the captured value in the
 * subject, so the menu stays single-viewer and stateless between opens. {@link #confirm} is package-private so a
 * test can drive the normal/silent click and assert the executor sees the right {@code silent} flag.
 */
@NullMarked
public final class PunishmentConfirmView {

    /** The engine spec id this view registers and opens under. */
    public static final String SPEC_ID = "moderation-punishment-confirm";

    private static final String SPEC_RESOURCE = "modules/moderation/gui/moderation-punishment-confirm.conf";
    private static final int ROWS = 3;
    private static final String REASON_KEY = "moderation.reason";

    private final Menus menus;
    private final Scheduler scheduler;
    private final TextInput textInput;

    public PunishmentConfirmView(Menus menus, Scheduler scheduler, TextInput textInput) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
    }

    /**
     * Register the subject placeholders the spec reads, the silent-offered view condition, the apply / apply-silent
     * / set-reason / back actions, and the spec itself. Every label key is resolved from the subject's
     * {@link PunishmentAction}, so one registration serves all six verbs.
     */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.placeholder(
                "mod_confirm_title_key",
                ctx -> subject(ctx).action().confirmTitle().key());
        bindings.placeholder("mod_confirm_player", ctx -> subject(ctx).target().name());
        bindings.placeholder(
                "mod_confirm_apply_label_key",
                ctx -> subject(ctx).action().applyLabel().key());
        bindings.placeholder(
                "mod_confirm_apply_lore_key",
                ctx -> subject(ctx).action().applyLore().key());
        bindings.placeholder(
                "mod_confirm_silent_label_key",
                ctx -> silentLabelKey(subject(ctx).action()));
        bindings.placeholder(
                "mod_confirm_silent_lore_key", ctx -> silentLoreKey(subject(ctx).action()));
        bindings.placeholder("mod_confirm_reason", ctx -> subject(ctx).reason().orElse(""));
        bindings.placeholder("mod_confirm_reason_lore_key", ctx -> reasonLoreKey(subject(ctx)));
        bindings.condition(
                "moderation:confirm-silent-offered",
                (ctx, args) -> subject(ctx).action().silentSupported());
        bindings.action("moderation:punish-apply", ctx -> confirm(ctx, false));
        bindings.action("moderation:punish-apply-silent", ctx -> confirm(ctx, true));
        bindings.action("moderation:set-reason", this::promptReason);
        bindings.action(
                "moderation:punish-back",
                ctx -> ctx.subject(Confirm.class).onBack().run());
        menus.registerSpec(SPEC_ID, MenuSpecs.loadOrBundled(SPEC_RESOURCE, dataFolder, ROWS, log));
    }

    /**
     * Open the confirm screen for {@code target}. {@code executor} performs the audited use-case call on confirm;
     * {@code onBack} reopens the picker from the back button. The reason starts empty and is captured through the
     * reason button's text-input prompt.
     */
    public void open(
            Player viewer,
            PlayerRef actor,
            PlayerRef target,
            PunishmentAction action,
            PunishmentAction.Executor executor,
            Runnable onBack) {
        open(actor, target, action, executor, onBack, Optional.empty());
    }

    private void open(
            PlayerRef actor,
            PlayerRef target,
            PunishmentAction action,
            PunishmentAction.Executor executor,
            Runnable onBack,
            Optional<String> reason) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(onBack, "onBack");
        Objects.requireNonNull(reason, "reason");
        menus.open(actor, SPEC_ID, new Confirm(action, target, executor, reason, onBack));
    }

    /**
     * Run the executor for the chosen target on the actor's entity thread and close the screen. Package-private so
     * a test drives the normal vs silent button and asserts the {@code silent} flag the executor receives.
     */
    void confirm(MenuActionContext ctx, boolean silent) {
        Player viewer = ctx.player();
        PlayerRef actor = ctx.viewer();
        Confirm subject = ctx.subject(Confirm.class);
        scheduler.onEntity(actor, () -> {
            viewer.closeInventory();
            subject.executor().execute(actor, subject.target(), subject.reason(), silent);
        });
    }

    /** Prompt for a reason; a submission reopens the confirm screen carrying the typed reason, a cancel keeps it. */
    private void promptReason(MenuActionContext ctx) {
        Player viewer = ctx.player();
        PlayerRef actor = ctx.viewer();
        Confirm subject = ctx.subject(Confirm.class);
        InputRequest request = InputRequest.of(REASON_KEY, ModerationMessageKey.MOD_GUI_CONFIRM_REASON_PROMPT);
        textInput.prompt(
                viewer,
                actor,
                request,
                text -> applyReason(actor, subject, reasonOf(text)),
                () -> applyReason(actor, subject, subject.reason()));
    }

    /**
     * Reopen the confirm screen for {@code actor} carrying {@code reason} in the subject. Package-private so a test
     * drives the reason-input submit branch, the round-trip that reopens with the captured reason, without a live
     * anvil (MockBukkit cannot drive one), mirroring the economy amount seam.
     */
    void applyReason(PlayerRef actor, Confirm subject, Optional<String> reason) {
        open(actor, subject.target(), subject.action(), subject.executor(), subject.onBack(), reason);
    }

    /** A blank submission clears the reason; otherwise the trimmed line becomes the carried reason. */
    private static Optional<String> reasonOf(String text) {
        return text.isBlank() ? Optional.empty() : Optional.of(text.strip());
    }

    /** The silent-button label key; an absent silent form falls back to the apply key (the button is then hidden). */
    private static String silentLabelKey(PunishmentAction action) {
        return action.silentLabel().orElse(action.applyLabel()).key();
    }

    /** The silent-button lore key; an absent silent form falls back to the apply key (the button is then hidden). */
    private static String silentLoreKey(PunishmentAction action) {
        return action.silentLore().orElse(action.applyLore()).key();
    }

    /** The reason-button lore key: the "reason set" line when one is carried, otherwise the "no reason" line. */
    private static String reasonLoreKey(Confirm subject) {
        return subject.reason().isPresent()
                ? ModerationMessageKey.MOD_GUI_CONFIRM_REASON_SET_LORE.key()
                : ModerationMessageKey.MOD_GUI_CONFIRM_REASON_NONE_LORE.key();
    }

    private Confirm subject(MenuContext ctx) {
        return ctx.subject(Confirm.class);
    }

    /**
     * The subject of an open confirm screen: the sanction being confirmed, its target, the executor the confirm
     * buttons run, the reason captured so far, and the back callback. The head and button placeholders read these
     * directly, so the render touches no port; the set-reason round-trip reopens with a fresh subject carrying the
     * new reason.
     *
     * @param action the sanction this screen confirms, supplying the title and confirm-button label keys
     * @param target the player the sanction applies to
     * @param executor the audited use-case call the apply / apply-silent buttons run
     * @param reason the reason captured so far, empty until the operator sets one
     * @param onBack reopens the player picker from the back button
     */
    public record Confirm(
            PunishmentAction action,
            PlayerRef target,
            PunishmentAction.Executor executor,
            Optional<String> reason,
            Runnable onBack) {

        public Confirm {
            Objects.requireNonNull(action, "action");
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(executor, "executor");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(onBack, "onBack");
        }
    }
}

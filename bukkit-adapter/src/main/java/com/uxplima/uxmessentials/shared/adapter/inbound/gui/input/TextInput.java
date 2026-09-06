package com.uxplima.uxmessentials.shared.adapter.inbound.gui.input;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.application.message.GuiMessageKey;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.bedrock.BedrockDetector;
import com.uxplima.uxmlib.bedrock.BedrockScreen;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The one entry point for capturing a line of text from a player, whether through an anvil or through chat. A call
 * site hands a {@link InputRequest} (a stable key, a prompt label, an optional anvil pre-fill) plus a submit and a
 * cancel callback; the seam reads the operator's per-key mode from {@link InputSettings}, opens the matching backend,
 * and routes the result. This replaces the two split mechanisms, uxmLib's {@code AnvilInput} and the per-context chat
 * listeners, so every input point is configurable as anvil or chat without the call site knowing which ran.
 *
 * <p><b>Cancel policy.</b> Backends report a raw {@link InputResult} and the cancel-keyword check lives here, with
 * one exception: the sign backend sits on uxmLib's {@code PlayerInput}, which applies a keyword of its own one floor
 * down and hands over a result that is already {@code Cancelled}. It is given the operator's first configured keyword
 * so the two floors agree on that word; the rest of the list is caught here as it is for every other backend. A structural cancel (anvil closed) and a {@code Submitted} line that matches a configured cancel keyword both
 * resolve to a cancellation: the viewer is sent the {@code gui.input.cancelled} acknowledgement and {@code onCancel}
 * runs (reopening the prior menu, as before). Any other line runs {@code onSubmit} with the typed text.
 *
 * <p><b>Folia.</b> The backend may report on an async thread (chat) or the region thread (anvil); the seam hops both
 * the submit and the cancel branch onto the viewer's entity region before the callback runs, so a call site's callback
 * always executes where it can safely touch the player and reopen a GUI. The call site no longer hops for itself.
 *
 * <p><b>Bedrock.</b> A Floodgate viewer has no anvil or chat prompt worth showing, so when {@link BedrockDetector}
 * reports the viewer is a Bedrock player the seam sends a native Cumulus CustomForm with a single text input instead.
 * Its submitted value and its close both flow through the same {@link #route} policy as the anvil/chat backends, so the
 * cancel-keyword check and the entity-thread hop live in one place regardless of which prompt the viewer saw. A Java
 * viewer keeps the anvil/chat prompt byte-identically. Both defaults are the Java-only no-ops, so an engine wired
 * without Floodgate never redirects.
 */
@NullMarked
public final class TextInput {

    private final InputSettings settings;
    private final GuiText guiText;
    private final Scheduler scheduler;
    private final AnvilTextBackend anvilBackend;
    private final ChatTextBackend chatBackend;
    private final BedrockDetector bedrock;
    private final BedrockScreen bedrockScreen;
    private final Logger log;

    /**
     * The transient-sign backend a {@code sign} input point uses, or {@code null} on an engine wired without one
     * (every test fixture), in which case {@code sign} falls back to the anvil backend.
     */
    @Nullable private final TextInputBackend signBackend;

    /**
     * The native-dialog backend a {@code dialog} input point uses, or {@code null} when no dialog backend is wired
     * the server predates the 1.21.6 Dialog API, or the seam predates the dialog backend. When {@code null}, a
     * {@code dialog} input point falls back to the sign backend (or the anvil if no sign backend either), and the seam
     * logs the substitution once through {@link #dialogFallback()} so the operator is not silently handed a sign.
     */
    @Nullable private final TextInputBackend dialogBackend;

    /** Guards the one-time {@code input_mode_unavailable} log so a repeated dialog fallback does not spam the console. */
    private final AtomicBoolean dialogFallbackWarned = new AtomicBoolean();

    /**
     * As the eight-argument constructor, but with no Bedrock redirect: every viewer gets the anvil or chat prompt.
     * Kept so the tests and any wiring that predates the Bedrock seam stay a delegating call.
     */
    public TextInput(
            InputSettings settings,
            GuiText guiText,
            Scheduler scheduler,
            AnvilTextBackend anvilBackend,
            ChatTextBackend chatBackend,
            Logger log) {
        this(settings, guiText, scheduler, anvilBackend, chatBackend, BedrockDetector.NONE, BedrockScreen.NONE, log);
    }

    /**
     * As the ten-argument constructor, but with neither a sign nor a dialog backend: a {@code sign} or {@code dialog}
     * input point falls back to the anvil backend. Kept so the tests and any wiring that predates the native-screen
     * backends stay a delegating call.
     */
    public TextInput(
            InputSettings settings,
            GuiText guiText,
            Scheduler scheduler,
            AnvilTextBackend anvilBackend,
            ChatTextBackend chatBackend,
            BedrockDetector bedrock,
            BedrockScreen bedrockScreen,
            Logger log) {
        this(settings, guiText, scheduler, anvilBackend, chatBackend, bedrock, bedrockScreen, null, null, log);
    }

    public TextInput(
            InputSettings settings,
            GuiText guiText,
            Scheduler scheduler,
            AnvilTextBackend anvilBackend,
            ChatTextBackend chatBackend,
            BedrockDetector bedrock,
            BedrockScreen bedrockScreen,
            @Nullable TextInputBackend signBackend,
            @Nullable TextInputBackend dialogBackend,
            Logger log) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.anvilBackend = Objects.requireNonNull(anvilBackend, "anvilBackend");
        this.chatBackend = Objects.requireNonNull(chatBackend, "chatBackend");
        this.bedrock = Objects.requireNonNull(bedrock, "bedrock");
        this.bedrockScreen = Objects.requireNonNull(bedrockScreen, "bedrockScreen");
        this.log = Objects.requireNonNull(log, "log");
        this.signBackend = signBackend;
        this.dialogBackend = dialogBackend;
    }

    /**
     * Prompt {@code player} for a line of text per the request, then run exactly one of the callbacks on the viewer's
     * region thread: {@code onSubmit} with the typed line, or {@code onCancel} if they cancelled (closed the anvil or
     * typed a cancel keyword).
     *
     * @param player the live player to prompt
     * @param viewer the viewer reference. Locale, identity, and the region the callbacks run on
     * @param request the input point: its key (config lookup), label, and optional pre-fill
     * @param onSubmit receives the accepted line
     * @param onCancel runs on cancellation; typically reopens the menu the player came from
     */
    public void prompt(
            Player player, PlayerRef viewer, InputRequest request, Consumer<String> onSubmit, Runnable onCancel) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(onSubmit, "onSubmit");
        Objects.requireNonNull(onCancel, "onCancel");
        if (bedrock.isBedrock(viewer.uuid())) {
            sendInputForm(player, viewer, request, onSubmit, onCancel);
            return;
        }
        InputMode mode = settings.modeFor(request.key());
        TextInputBackend backend = backendFor(mode);
        Component prompt = buildPrompt(viewer, request.label(), request.placeholders(), mode);
        backend.open(
                player,
                viewer,
                prompt,
                request.initialText(),
                result -> scheduler.onEntity(viewer, () -> route(player, viewer, result, onSubmit, onCancel)));
    }

    /**
     * As {@link #prompt}, but with the prompt already resolved to a {@link Component} rather than looked up from a
     * {@link MessageKey} catalog, the entry point the menu engine uses, whose {@code input:} prompts are arbitrary
     * {@code @key}-or-MiniMessage strings the engine resolves through its own renderer, not catalog enum keys. The
     * backend is still chosen from the operator's per-{@code key} mode, and a Bedrock viewer still gets the Cumulus
     * form regardless of that mode; the cancel-keyword policy and the entity-thread hop are the shared {@link #route}.
     *
     * @param player the live player to prompt
     * @param viewer the viewer reference. Locale, identity, and the region the callbacks run on
     * @param key the input-point key the per-key mode is looked up by
     * @param prompt the already-resolved prompt label
     * @param initialText the anvil pre-fill, or {@code null}
     * @param onSubmit receives the accepted line
     * @param onCancel runs on cancellation
     */
    public void promptResolved(
            Player player,
            PlayerRef viewer,
            String key,
            Component prompt,
            @Nullable String initialText,
            Consumer<String> onSubmit,
            Runnable onCancel) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(onSubmit, "onSubmit");
        Objects.requireNonNull(onCancel, "onCancel");
        if (bedrock.isBedrock(viewer.uuid())) {
            sendResolvedInputForm(player, viewer, prompt, initialText, onSubmit, onCancel);
            return;
        }
        InputMode mode = settings.modeFor(key);
        TextInputBackend backend = backendFor(mode);
        // A chat prompt has no cancel button, so it carries the abort hint; a screen backend (anvil/sign) shows the
        // prompt as its own title and needs none: the same rule buildPrompt applies to a catalog-resolved prompt.
        Component effective = mode == InputMode.CHAT ? appendCancelHint(viewer, prompt) : prompt;
        backend.open(
                player,
                viewer,
                effective,
                initialText,
                result -> scheduler.onEntity(viewer, () -> route(player, viewer, result, onSubmit, onCancel)));
    }

    /**
     * The backend for {@code mode}: chat for {@code CHAT}, the transient sign for {@code SIGN}, the native dialog for
     * {@code DIALOG}, and the anvil for {@code ANVIL}. A {@code SIGN}/{@code DIALOG} point whose backend is not wired
     * falls back to the anvil; a {@code DIALOG} fallback is logged once by {@link #dialogFallback()} rather than
     * silently masquerading as a sign or an anvil.
     */
    private TextInputBackend backendFor(InputMode mode) {
        return switch (mode) {
            case CHAT -> chatBackend;
            case DIALOG -> dialogBackend != null ? dialogBackend : dialogFallback();
            case SIGN -> signBackend != null ? signBackend : anvilBackend;
            case ANVIL -> anvilBackend;
        };
    }

    /**
     * The backend a {@code dialog} input point falls back to when no dialog backend is wired. The server predates the
     * 1.21.6 Dialog API, or the seam was built without one. The substitution is logged once (not per prompt, guarded by
     * {@link #dialogFallbackWarned}) so an operator who configured {@code dialog} and saw a sign or an anvil can find
     * out why, instead of a silent masquerade.
     */
    private TextInputBackend dialogFallback() {
        TextInputBackend sign = signBackend;
        if (dialogFallbackWarned.compareAndSet(false, true)) {
            log.warn("event=input_mode_unavailable mode=dialog fallback={}", sign != null ? "sign" : "anvil");
        }
        return sign != null ? sign : anvilBackend;
    }

    /**
     * Render the request as a Cumulus CustomForm for a Bedrock viewer. The label resolves to plain text through the
     * same unprefixed {@link #buildPrompt} shape the anvil uses (no chat brand prefix in a form title), and serves as
     * both the form title and the single input's label. The submit and the close both re-enter {@link #route} on the
     * viewer's entity thread, so the cancel-keyword policy and the Folia hop are shared with the anvil/chat backends.
     */
    private void sendInputForm(
            Player player, PlayerRef viewer, InputRequest request, Consumer<String> onSubmit, Runnable onCancel) {
        Component label = buildPrompt(viewer, request.label(), request.placeholders(), InputMode.ANVIL);
        String plain = PlainTextComponentSerializer.plainText().serialize(label);
        bedrockScreen.sendInputForm(
                player,
                plain,
                plain,
                request.initialText(),
                value -> scheduler.onEntity(
                        viewer, () -> route(player, viewer, new InputResult.Submitted(value), onSubmit, onCancel)),
                () -> scheduler.onEntity(
                        viewer, () -> route(player, viewer, InputResult.Cancelled.INSTANCE, onSubmit, onCancel)));
    }

    /**
     * Render the resolved prompt as a Cumulus CustomForm for a Bedrock viewer: the {@link #promptResolved}
     * counterpart to {@link #sendInputForm}. The prompt is flattened to plain text for the form title and its single
     * input's label, and both the submit and the close re-enter {@link #route} on the viewer's entity thread, so the
     * cancel-keyword policy and the Folia hop stay shared with every other backend.
     */
    private void sendResolvedInputForm(
            Player player,
            PlayerRef viewer,
            Component prompt,
            @Nullable String initialText,
            Consumer<String> onSubmit,
            Runnable onCancel) {
        String plain = PlainTextComponentSerializer.plainText().serialize(prompt);
        bedrockScreen.sendInputForm(
                player,
                plain,
                plain,
                initialText,
                value -> scheduler.onEntity(
                        viewer, () -> route(player, viewer, new InputResult.Submitted(value), onSubmit, onCancel)),
                () -> scheduler.onEntity(
                        viewer, () -> route(player, viewer, InputResult.Cancelled.INSTANCE, onSubmit, onCancel)));
    }

    private Component buildPrompt(
            PlayerRef viewer, MessageKey label, Map<String, String> placeholders, InputMode mode) {
        if (mode != InputMode.CHAT) {
            // An anvil shows the prompt as its title; the brand chat prefix belongs to chat lines, not an inventory
            // title, so render the label without it. A chat prompt keeps the prefix the catalog key carries.
            return guiText.unprefixedText(viewer, label, placeholders);
        }
        return appendCancelHint(viewer, guiText.text(viewer, label, placeholders));
    }

    /** Append the "type &lt;keyword&gt; to cancel" hint to a chat prompt, which has no cancel button to click. */
    private Component appendCancelHint(PlayerRef viewer, Component prompt) {
        Component hint = guiText.text(
                viewer, GuiMessageKey.INPUT_CANCEL_HINT, Map.of("keyword", settings.primaryCancelKeyword()));
        return prompt.append(Component.space()).append(hint);
    }

    private void route(
            Player player, PlayerRef viewer, InputResult result, Consumer<String> onSubmit, Runnable onCancel) {
        if (result instanceof InputResult.Submitted submitted && !settings.isCancel(submitted.text())) {
            onSubmit.accept(submitted.text());
            return;
        }
        player.sendMessage(guiText.text(viewer, GuiMessageKey.INPUT_CANCELLED));
        onCancel.run();
    }
}

package com.uxplima.uxmessentials.shared.adapter.inbound.gui.input;

import java.nio.file.Path;
import java.util.Objects;

import org.bukkit.plugin.Plugin;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmlib.bedrock.BedrockDetector;
import com.uxplima.uxmlib.bedrock.BedrockScreen;
import com.uxplima.uxmlib.gui.anvil.AnvilInput;
import com.uxplima.uxmlib.gui.dialog.DialogInputScreen;
import com.uxplima.uxmlib.gui.input.PlayerInput;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Builds the text-input seam once at bootstrap: loads {@link InputSettings} from {@code text-input.conf}, wraps the
 * already-installed shared {@link AnvilInput} as the anvil backend, installs the single shared chat backend, installs a
 * uxmLib {@link PlayerInput} the sign backend drives, wires the native dialog backend where the server supports it, and
 * hands back the {@link TextInput} every GUI-using context shares. The chat listener and the {@code PlayerInput} sign
 * listener are registered here and torn down through
 * {@link Installed#uninstall()}, so on disable/reload exactly the listeners this installs come and go, mirroring how
 * the anvil input and the menu listener are installed once in {@code PluginModule}.
 */
@NullMarked
public final class TextInputInstaller {

    private TextInputInstaller() {}

    /**
     * Wire the seam with no Bedrock redirect: every viewer gets the anvil/chat prompt. Kept so tests that only need a
     * working seam stay a delegating call.
     */
    public static Installed install(
            Plugin plugin, Path dataFolder, AnvilInput anvil, GuiText guiText, Scheduler scheduler, Logger log) {
        return install(plugin, dataFolder, anvil, guiText, scheduler, log, BedrockDetector.NONE, BedrockScreen.NONE);
    }

    /**
     * Wire the seam.
     *
     * @param plugin the plugin the chat listener registers against
     * @param dataFolder the data folder; the input config is read from {@code text-input.conf} under it
     * @param anvil the shared, already-installed anvil input the anvil backend delegates to
     * @param guiText the catalog-to-component resolver the prompt labels go through
     * @param scheduler the Folia scheduler the seam hops callbacks onto the viewer's region with
     * @param log the operator logger the config codec warns through
     * @param bedrock the resolved detector; a Bedrock viewer gets a Cumulus CustomForm instead of the anvil/chat prompt
     * @param bedrockScreen the resolved screen the CustomForm is sent through; {@code NONE} on a Java-only server
     */
    public static Installed install(
            Plugin plugin,
            Path dataFolder,
            AnvilInput anvil,
            GuiText guiText,
            Scheduler scheduler,
            Logger log,
            BedrockDetector bedrock,
            BedrockScreen bedrockScreen) {
        Objects.requireNonNull(plugin, "plugin");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(anvil, "anvil");
        Objects.requireNonNull(guiText, "guiText");
        Objects.requireNonNull(scheduler, "scheduler");
        Objects.requireNonNull(log, "log");
        Objects.requireNonNull(bedrock, "bedrock");
        Objects.requireNonNull(bedrockScreen, "bedrockScreen");
        InputSettings settings = new InputSettings(dataFolder.resolve("text-input.conf"), log);
        AnvilTextBackend anvilBackend = new AnvilTextBackend(anvil);
        ChatTextBackend chatBackend = new ChatTextBackend(plugin);
        chatBackend.install();
        // uxmLib's PlayerInput owns the transient-sign mechanism; the sign backend only ever asks it for SIGN, and the
        // seam does its own entity-thread hop, so PlayerInput is built without a scheduler and torn down on disable.
        // It applies a cancel keyword of its own before a result reaches us, and there is no way to turn that off, so
        // it is given the operator's first configured keyword rather than the library default: with the default, the
        // literal word `cancel` aborted a sign prompt even on a server that had removed it from `cancel-keywords`,
        // and nothing said why. The remaining keywords arrive as Submitted and the seam's own check catches them.
        // Read once here, so a reload that changes the first keyword leaves this one stale until the next restart;
        // the real fix is for the keyword policy to live only on this floor, which needs the library to allow it.
        PlayerInput playerInput = new PlayerInput(plugin, null, settings.primaryCancelKeyword());
        playerInput.install();
        SignTextBackend signBackend = new SignTextBackend(playerInput);
        // Wire the dialog backend only where the native Dialog API exists (Minecraft 1.21.6+). On an older server it
        // stays null, so a dialog-mode input point falls back with a logged warning rather than an immediate cancel.
        @Nullable DialogTextBackend dialogBackend =
                DialogInputScreen.isSupported() ? DialogTextBackend.paperNative(guiText) : null;
        TextInput textInput = new TextInput(
                settings,
                guiText,
                scheduler,
                anvilBackend,
                chatBackend,
                bedrock,
                bedrockScreen,
                signBackend,
                dialogBackend,
                log);
        return new Installed(textInput, settings, () -> {
            chatBackend.uninstall();
            playerInput.uninstall();
        });
    }

    /** The wired seam plus its teardown hook. {@code settings} is exposed so a reload can re-read the config. */
    public record Installed(TextInput textInput, InputSettings settings, Runnable uninstall) {

        public Installed {
            Objects.requireNonNull(textInput, "textInput");
            Objects.requireNonNull(settings, "settings");
            Objects.requireNonNull(uninstall, "uninstall");
        }
    }
}

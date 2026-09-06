package com.uxplima.uxmessentials.warps.adapter.inbound.gui;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;

import com.uxplima.uxmessentials.playerwarps.application.port.PlayerWarpRepository;
import com.uxplima.uxmessentials.playerwarps.domain.PlayerWarpName;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.StyledText;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.warps.application.UseWarp;
import com.uxplima.uxmessentials.warps.application.WarpsMessageKey;
import com.uxplima.uxmessentials.warps.application.port.WarpRepository;
import com.uxplima.uxmessentials.warps.domain.WarpName;
import com.uxplima.uxmessentials.warps.domain.WelcomeMessage;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Registers the per-warp editor hub with the menu engine and opens it. A three-row property panel for one warp
 * reached from the warp manager (a left click on a warp icon), the {@code /warp editor <name>} command, and the
 * create flow: a teleport button that warps the viewer to the warp, an icon-material button, a category button (a
 * server-warp concept, gated behind the {@code warps:editor-server-warp} condition), a lock toggle, a password
 * button, a welcome-messages button into the engine welcome list, sounds and particles buttons into the engine
 * selectors, and warmup / cooldown override buttons. Each mutation saves the edited warp through the shared
 * {@link EditableWarp} loader and re-opens this panel with the new subject so the operator sees the result.
 *
 * <p>The edited warp is handed in as the menu subject, its name, owner, and a display snapshot read off the
 * viewer's entity thread at open, so the title and every current-value line fill from the {@code warp_edit_*}
 * placeholders without the renderer touching a port. The panel holds no new domain logic: it replays the old
 * bespoke window's handlers verbatim through the engine. This is the warp category settings-panel pattern
 * (subject-carried state, the input seam, the engine sub-screens, and re-open after a mutation). The selector and
 * welcome collaborators are injected after this view to break their re-open cycle. Every visible string resolves
 * from the warps catalog.
 */
@NullMarked
public final class WarpEditorView {

    /** The engine spec id this panel registers and opens under. */
    public static final String SPEC_ID = "warp-editor";

    private static final String SPEC_RESOURCE = "modules/warps/gui/warp-editor.conf";
    private static final int ROWS = 3;

    /** The display item a warp shows when no icon is set: the same default the browse menu falls back to. */
    private static final Material DEFAULT_ICON = Material.ENDER_PEARL;

    private final Menus menus;
    private final Messages messages;
    private final Scheduler scheduler;
    private final WarpRepository warpRepository;
    private final TextInput textInput;
    private final UseWarp useWarp;
    private final PlayerWarpRepositoryHandle playerWarpRepositoryHandle;
    private final PlayerWarpGoToHandle playerWarpGoTo;
    private final EditableWarpLoader loader;

    /** The sub-screen collaborators the buttons open; injected after this view to break their re-open cycle. */
    private @Nullable WarpCategorySelectorMenu categorySelector;

    private @Nullable WarpWelcomeMessagesView welcomeMessagesView;
    private @Nullable WarpSoundMenu soundMenu;
    private @Nullable WarpSoundSelectorView soundSelectorView;
    private @Nullable WarpParticleSelectorView particleSelectorView;

    public WarpEditorView(
            Menus menus,
            Messages messages,
            Scheduler scheduler,
            WarpRepository warpRepository,
            TextInput textInput,
            UseWarp useWarp,
            PlayerWarpRepositoryHandle playerWarpRepositoryHandle,
            PlayerWarpGoToHandle playerWarpGoTo) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.warpRepository = Objects.requireNonNull(warpRepository, "warpRepository");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
        this.useWarp = Objects.requireNonNull(useWarp, "useWarp");
        this.playerWarpRepositoryHandle =
                Objects.requireNonNull(playerWarpRepositoryHandle, "playerWarpRepositoryHandle");
        this.playerWarpGoTo = Objects.requireNonNull(playerWarpGoTo, "playerWarpGoTo");
        this.loader = new EditableWarpLoader(warpRepository, this);
    }

    public @Nullable PlayerWarpRepository playerWarpRepository() {
        return playerWarpRepositoryHandle.get();
    }

    /**
     * Wire the sub-screens the editor buttons open. Each reopens the editor after its work, so they hold this view
     * and this view holds them; this setter breaks that cycle, mirroring the category settings panel's {@code bind}.
     */
    public void bind(
            WarpCategorySelectorMenu categorySelector,
            WarpWelcomeMessagesView welcomeMessagesView,
            WarpSoundMenu soundMenu,
            WarpSoundSelectorView soundSelectorView,
            WarpParticleSelectorView particleSelectorView) {
        this.categorySelector = Objects.requireNonNull(categorySelector, "categorySelector");
        this.welcomeMessagesView = Objects.requireNonNull(welcomeMessagesView, "welcomeMessagesView");
        this.soundMenu = Objects.requireNonNull(soundMenu, "soundMenu");
        this.soundSelectorView = Objects.requireNonNull(soundSelectorView, "soundSelectorView");
        this.particleSelectorView = Objects.requireNonNull(particleSelectorView, "particleSelectorView");
    }

    /** Register the subject placeholders, the visibility condition, the action buttons, and the spec itself. */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        registerPlaceholders(bindings);
        bindings.condition(
                "warps:editor-server-warp", (ctx, args) -> subject(ctx).owner() == null);
        registerActions(bindings);
        menus.registerSpec(SPEC_ID, MenuSpecs.loadOrBundled(SPEC_RESOURCE, dataFolder, ROWS, log));
    }

    private void registerPlaceholders(MenuBindings bindings) {
        bindings.placeholder("warp_edit_name", ctx -> subject(ctx).warpName());
        bindings.placeholder(
                "warp_edit_icon_material", ctx -> iconMaterial(subject(ctx)).name());
        bindings.placeholder(
                "warp_edit_icon", ctx -> iconMaterial(subject(ctx)).name().toLowerCase(Locale.ROOT));
        bindings.placeholder(
                "warp_edit_lock",
                ctx -> lockState(ctx.viewer(), subject(ctx).display().isLocked()));
        bindings.placeholder(
                "warp_edit_password", ctx -> orNone(ctx, subject(ctx).display().password()));
        bindings.placeholder("warp_edit_welcome", this::welcomeText);
        bindings.placeholder("warp_edit_welcome_type", this::welcomeType);
        bindings.placeholder(
                "warp_edit_sound_departure",
                ctx -> orNone(ctx, subject(ctx).display().departureSound()));
        bindings.placeholder(
                "warp_edit_sound_arrival",
                ctx -> orNone(ctx, subject(ctx).display().arrivalSound()));
        bindings.placeholder(
                "warp_edit_particle_departure",
                ctx -> orNone(ctx, subject(ctx).display().departureParticle()));
        bindings.placeholder(
                "warp_edit_particle_arrival",
                ctx -> orNone(ctx, subject(ctx).display().arrivalParticle()));
        bindings.placeholder(
                "warp_edit_warmup", ctx -> seconds(ctx, subject(ctx).display().warmupOverrideSeconds()));
        bindings.placeholder(
                "warp_edit_cooldown", ctx -> seconds(ctx, subject(ctx).display().cooldownOverrideSeconds()));
        bindings.placeholder("warp_edit_category", this::categoryName);
        bindings.placeholder(
                "warp_edit_world",
                ctx -> subject(ctx).display().location().world().name());
        bindings.placeholder(
                "warp_edit_x",
                ctx -> Integer.toString(subject(ctx).display().location().blockX()));
        bindings.placeholder(
                "warp_edit_y",
                ctx -> Integer.toString(subject(ctx).display().location().blockY()));
        bindings.placeholder(
                "warp_edit_z",
                ctx -> Integer.toString(subject(ctx).display().location().blockZ()));
    }

    private void registerActions(MenuBindings bindings) {
        bindings.action("warps:editor-teleport", this::teleport);
        bindings.action("warps:editor-icon-set", this::setIcon);
        bindings.action("warps:editor-icon-clear", this::clearIcon);
        bindings.action("warps:editor-category", this::openCategory);
        bindings.action("warps:editor-lock", this::toggleLock);
        bindings.action("warps:editor-password-set", this::promptPassword);
        bindings.action("warps:editor-password-clear", this::clearPassword);
        bindings.action("warps:editor-welcome", this::openWelcome);
        bindings.action("warps:editor-sounds-departure", ctx -> openSounds(ctx, true));
        bindings.action("warps:editor-sounds-arrival", ctx -> openSounds(ctx, false));
        bindings.action("warps:editor-sounds-clear", this::clearSounds);
        bindings.action("warps:editor-particles-departure", ctx -> openParticles(ctx, true));
        bindings.action("warps:editor-particles-arrival", ctx -> openParticles(ctx, false));
        bindings.action("warps:editor-particles-clear", this::clearParticles);
        bindings.action("warps:editor-warmup-set", ctx -> promptDuration(ctx, true));
        bindings.action("warps:editor-warmup-clear", ctx -> clearDuration(ctx, true));
        bindings.action("warps:editor-cooldown-set", ctx -> promptDuration(ctx, false));
        bindings.action("warps:editor-cooldown-clear", ctx -> clearDuration(ctx, false));
        bindings.action("warps:editor-close", ctx -> ctx.player().closeInventory());
    }

    /**
     * Open the editor for the warp {@code warpName} ({@code warpOwner} null for a server warp). The warp is read on
     * the viewer's entity thread into a display snapshot handed to the engine as the subject, so the engine renders
     * off that snapshot without a port read of its own. A warp that no longer exists closes the menu.
     */
    public void open(Player player, PlayerRef viewer, String warpName, @Nullable PlayerRef warpOwner) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(warpName, "warpName");
        scheduler.onEntity(viewer, () -> {
            WarpDisplay display = snapshot(warpName, warpOwner);
            if (display == null) {
                player.closeInventory();
                return;
            }
            menus.open(viewer, SPEC_ID, new WarpEditTarget(warpName, warpOwner, display));
        });
    }

    /** Read the clicked warp's display projection, or {@code null} when it no longer exists. */
    private @Nullable WarpDisplay snapshot(String warpName, @Nullable PlayerRef warpOwner) {
        if (warpOwner != null) {
            PlayerWarpRepository repo = playerWarpRepositoryHandle.get();
            if (repo == null) {
                return null;
            }
            return repo.findByName(PlayerWarpName.of(warpName))
                    .filter(warp -> warp.owner().uuid().equals(warpOwner.uuid()))
                    .map(WarpDisplay::of)
                    .orElse(null);
        }
        return warpRepository.find(WarpName.of(warpName)).map(WarpDisplay::of).orElse(null);
    }

    /** Teleport to the warp through the same path the {@code /warp <name>} command uses; close first to debounce. */
    private void teleport(MenuActionContext ctx) {
        Player player = ctx.player();
        PlayerRef viewer = ctx.viewer();
        WarpEditTarget target = subject(ctx);
        player.closeInventory();
        if (target.owner() == null) {
            useWarp.use(viewer, WarpName.of(target.warpName()));
        } else {
            playerWarpGoTo.teleport(viewer, target.owner(), target.warpName());
        }
    }

    /** Copy the item in the operator's main hand as the icon material, then save and re-open; empty hand rejected. */
    private void setIcon(MenuActionContext ctx) {
        Player player = ctx.player();
        PlayerRef viewer = ctx.viewer();
        WarpEditTarget target = subject(ctx);
        Material hand = player.getInventory().getItemInMainHand().getType();
        if (hand.isAir()) {
            player.sendMessage(text(viewer, WarpsMessageKey.WARP_EDITOR_ICON_ERROR_HAND));
            reopen(ctx, target);
            return;
        }
        mutate(target, warp -> warp.setIconMaterial(Optional.of(hand.name())));
        reopen(ctx, target);
    }

    /** Clear the warp's icon, then save and re-open. */
    private void clearIcon(MenuActionContext ctx) {
        WarpEditTarget target = subject(ctx);
        mutate(target, warp -> warp.setIconMaterial(Optional.empty()));
        reopen(ctx, target);
    }

    /** Open the engine category selector for a server warp; choosing a category saves it and re-opens the editor. */
    private void openCategory(MenuActionContext ctx) {
        WarpEditTarget target = subject(ctx);
        if (categorySelector != null && target.owner() == null) {
            categorySelector.open(ctx.player(), ctx.viewer(), target.warpName());
        }
    }

    /** Toggle the warp's locked state, then save and re-open. */
    private void toggleLock(MenuActionContext ctx) {
        WarpEditTarget target = subject(ctx);
        mutate(target, warp -> warp.setLocked(!warp.isLocked()));
        reopen(ctx, target);
    }

    /** Capture a password through the input seam, then save it and re-open; cancel re-opens unchanged. */
    private void promptPassword(MenuActionContext ctx) {
        WarpEditTarget target = subject(ctx);
        prompt(ctx, "warp.password", WarpsMessageKey.WARP_EDITOR_PASSWORD_PROMPT, target, input -> {
            mutate(target, warp -> warp.setPassword(Optional.of(input)));
            reopen(ctx, target);
        });
    }

    /** Clear the warp's password, then save and re-open. */
    private void clearPassword(MenuActionContext ctx) {
        WarpEditTarget target = subject(ctx);
        mutate(target, warp -> warp.setPassword(Optional.empty()));
        reopen(ctx, target);
    }

    /** Open the engine welcome-messages list for this warp. */
    private void openWelcome(MenuActionContext ctx) {
        WarpEditTarget target = subject(ctx);
        if (welcomeMessagesView != null) {
            welcomeMessagesView.open(ctx.player(), ctx.viewer(), target.warpName(), target.owner());
        }
    }

    /** Open the engine sound selector for the departure or arrival side. */
    private void openSounds(MenuActionContext ctx, boolean departure) {
        WarpEditTarget target = subject(ctx);
        if (target.owner() == null) {
            if (soundMenu != null) {
                soundMenu.open(ctx.viewer(), new WarpSoundEdit(WarpName.of(target.warpName()), departure));
            }
        } else if (soundSelectorView != null) {
            soundSelectorView.open(ctx.player(), ctx.viewer(), target.warpName(), target.owner(), departure);
        }
    }

    /** Clear both sound sides, then save and re-open. */
    private void clearSounds(MenuActionContext ctx) {
        WarpEditTarget target = subject(ctx);
        mutate(target, EditableWarp::clearSounds);
        reopen(ctx, target);
    }

    /** Open the engine particle selector for the departure or arrival side. */
    private void openParticles(MenuActionContext ctx, boolean departure) {
        WarpEditTarget target = subject(ctx);
        if (particleSelectorView != null) {
            particleSelectorView.open(ctx.player(), ctx.viewer(), target.warpName(), target.owner(), departure);
        }
    }

    /** Clear both particle sides, then save and re-open. */
    private void clearParticles(MenuActionContext ctx) {
        WarpEditTarget target = subject(ctx);
        mutate(target, EditableWarp::clearParticles);
        reopen(ctx, target);
    }

    /** Capture a warmup or cooldown override in seconds through the input seam; cancel re-opens unchanged. */
    private void promptDuration(MenuActionContext ctx, boolean warmup) {
        WarpEditTarget target = subject(ctx);
        MessageKey promptKey =
                warmup ? WarpsMessageKey.WARP_EDITOR_WARMUP_PROMPT : WarpsMessageKey.WARP_EDITOR_COOLDOWN_PROMPT;
        String key = warmup ? "warp.warmup" : "warp.cooldown";
        prompt(ctx, key, promptKey, target, input -> applyDuration(ctx, target, warmup, input));
    }

    /** Parse the typed duration and, when valid, save it and re-open; a non-number sends the existing rejection. */
    void applyDuration(MenuActionContext ctx, WarpEditTarget target, boolean warmup, String input) {
        double parsed;
        try {
            parsed = Double.parseDouble(input.trim());
        } catch (NumberFormatException notANumber) {
            ctx.player().sendMessage(text(ctx.viewer(), WarpsMessageKey.WARP_EDITOR_INVALID_NUMBER));
            reopen(ctx, target);
            return;
        }
        Optional<Double> seconds = Optional.of(parsed);
        mutate(target, warp -> setDuration(warp, warmup, seconds));
        reopen(ctx, target);
    }

    /** Clear the warmup or cooldown override, then save and re-open. */
    private void clearDuration(MenuActionContext ctx, boolean warmup) {
        WarpEditTarget target = subject(ctx);
        mutate(target, warp -> setDuration(warp, warmup, Optional.empty()));
        reopen(ctx, target);
    }

    private static void setDuration(EditableWarp warp, boolean warmup, Optional<Double> seconds) {
        if (warmup) {
            warp.setWarmupOverride(seconds);
        } else {
            warp.setCooldownOverride(seconds);
        }
    }

    /** Drive the shared input seam for {@code key}, applying {@code action} on submit and re-opening on cancel. */
    private void prompt(
            MenuActionContext ctx,
            String inputKey,
            MessageKey promptKey,
            WarpEditTarget target,
            java.util.function.Consumer<String> action) {
        Player player = ctx.player();
        PlayerRef viewer = ctx.viewer();
        player.closeInventory();
        textInput.prompt(player, viewer, InputRequest.of(inputKey, promptKey), action, () -> reopen(ctx, target));
    }

    /** Apply a mutation to the edited warp through the shared loader; a stale warp is a no-op the re-open catches. */
    private void mutate(WarpEditTarget target, java.util.function.Consumer<EditableWarp> change) {
        EditableWarp warp = loader.load(target.warpName(), target.owner());
        if (warp != null) {
            change.accept(warp);
        }
    }

    /** Re-open the editor after a mutation so the operator sees the result with a fresh subject snapshot. */
    private void reopen(MenuActionContext ctx, WarpEditTarget target) {
        open(ctx.player(), ctx.viewer(), target.warpName(), target.owner());
    }

    /** The warp's live display item, a valid per-warp override, else the {@link #DEFAULT_ICON} fallback. */
    private static Material iconMaterial(WarpEditTarget target) {
        Optional<Material> override = target.display()
                .iconMaterial()
                .map(Material::matchMaterial)
                .filter(material -> material != Material.AIR);
        return override.orElse(DEFAULT_ICON);
    }

    private String welcomeText(MenuContext ctx) {
        List<WelcomeMessage> messages = subject(ctx).display().welcomeMessages();
        return messages.isEmpty() ? none(ctx.viewer()) : messages.get(0).message();
    }

    private String welcomeType(MenuContext ctx) {
        List<WelcomeMessage> messages = subject(ctx).display().welcomeMessages();
        return messages.isEmpty() ? none(ctx.viewer()) : messages.get(0).type();
    }

    private String categoryName(MenuContext ctx) {
        WarpEditTarget target = subject(ctx);
        if (target.owner() != null) {
            return none(ctx.viewer());
        }
        return warpRepository
                .find(WarpName.of(target.warpName()))
                .flatMap(warp -> warp.categoryId())
                .orElseGet(() -> none(ctx.viewer()));
    }

    private String seconds(MenuContext ctx, Optional<Double> value) {
        return value.map(d -> d + "s").orElseGet(() -> none(ctx.viewer()));
    }

    private String orNone(MenuContext ctx, Optional<String> value) {
        return value.orElseGet(() -> none(ctx.viewer()));
    }

    private String none(PlayerRef viewer) {
        return messages.resolve(viewer, WarpsMessageKey.WARP_EDITOR_VALUE_NONE, Map.of());
    }

    private String lockState(PlayerRef viewer, boolean locked) {
        WarpsMessageKey key =
                locked ? WarpsMessageKey.WARP_EDITOR_VALUE_LOCKED : WarpsMessageKey.WARP_EDITOR_VALUE_UNLOCKED;
        return messages.resolve(viewer, key, Map.of());
    }

    private Component text(PlayerRef viewer, MessageKey key) {
        return StyledText.render(messages.resolve(viewer, key, Map.of()));
    }

    private WarpEditTarget subject(MenuContext ctx) {
        return ctx.subject(WarpEditTarget.class);
    }

    private WarpEditTarget subject(MenuActionContext ctx) {
        return ctx.subject(WarpEditTarget.class);
    }
}

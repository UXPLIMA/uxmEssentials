package com.uxplima.uxmessentials.shared.adapter.inbound.gui;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Player;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.application.message.GuiMessageKey;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import org.jspecify.annotations.NullMarked;

/**
 * A reusable target-picker menu: a paginated grid of the online players' heads, plus a fixed "custom / offline name"
 * button that opens an anvil so a staff member can type a name the grid does not show (an offline target). Clicking a
 * head, or submitting a name the supplied resolver recognises, invokes the caller's {@code onPick} with the chosen
 * {@link PlayerRef}; an unresolvable typed name replies with the caller's unknown-player {@link MessageKey} and reopens
 * the picker.
 *
 * <p>The view holds no feature logic. One instance is shared across callers. The framework collaborators (the menu
 * engine, text, scheduler, anvil, server) live on the instance, and the per-use parts (the title, the pick callback,
 * the offline-name resolver, and the unknown-player reply key) are passed to {@link #open}. The moderation
 * {@code /ban} and {@code /mute} GUI flows reuse it; the offline resolver a caller passes is its own (moderation backs
 * it with {@code PlayerLookupTargetResolver}), so this class never reaches for a context's lookup itself.
 *
 * <p>A caller may also supply optional {@link Request#footerButtons() footer buttons}. Extra bottom-row actions that
 * are not a player pick (the jail hub uses two: a "jails" manager and a "jailed players" list). They default to empty,
 * so the sanction callers that only pick a target are unaffected; a supplied button renders in a free bottom-row slot
 * and fires its own callback with the live viewer.
 *
 * <p>It draws through the menu engine's paginated-list runtime ({@link Menus#openList}) over an {@link EntityListSpec}: a
 * holder-backed engine list routed and torn down by the one menu listener and one {@code closeMenu}, with paging
 * re-paginating the same holder so a 500-player roster pages cleanly. The heads are the listed entities (their
 * {@code onSelect} the pick callback), and the offline-name button plus any footer buttons are the spec's fixed
 * {@link EntityListSpec.ExtraButton extra buttons}.
 *
 * <p>Folia: the online roster is enumerated on the global region thread (iterating
 * {@code Server.getOnlinePlayers()} off it is illegal) and snapshotted to plain {@link PlayerRef}s; the engine then
 * builds and opens the menu on the viewer's own entity region thread, where its clicks also run. The anvil resolver
 * call is hopped to async because an offline-name resolution may block, then the result is delivered back on the
 * viewer's entity thread.
 */
@NullMarked
public final class PlayerPickerView {

    /** The engine spec id this menu registers and opens under. */
    public static final String SPEC_ID = "player-picker";

    private static final String SPEC_RESOURCE = "modules/management/gui/player-picker.conf";
    private static final String INPUT_KEY = "picker.player-name";

    private final Menus menus;
    private final Scheduler scheduler;
    private final TextInput textInput;
    private final Server server;
    private final Messages messages;
    private final MessageSink sink;

    public PlayerPickerView(
            Menus menus, Scheduler scheduler, TextInput textInput, Server server, Messages messages, MessageSink sink) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
        this.server = Objects.requireNonNull(server, "server");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    /**
     * Register the bindings the spec names and the spec itself. Called once from bootstrap: the picker is shared
     * infrastructure rather than a module's own screen, so one registration serves every caller.
     */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.list("picker:players", ctx -> ctx.subject(Session.class).roster());
        bindings.placeholder("picker_title_key", ctx -> requestOf(ctx).title().key());
        // The whole material token expands from one placeholder, so the skull prefix rides along with the uuid.
        bindings.placeholder(
                "picker_head_icon", ctx -> "skull:" + candidateOf(ctx).uuid());
        bindings.placeholder(
                "picker_head_name",
                ctx -> text(
                        ctx,
                        GuiMessageKey.PLAYER_PICKER_HEAD_NAME,
                        Map.of("player", candidateOf(ctx).name())));
        bindings.placeholder("picker_head_lore", ctx -> text(ctx, GuiMessageKey.PLAYER_PICKER_HEAD_LORE, Map.of()));
        registerFooter(bindings, "one", 0);
        registerFooter(bindings, "two", 1);
        bindings.action("picker:pick", ctx -> requestOf(ctx).onPick().accept(ctx.entry(PlayerRef.class)));
        bindings.action("picker:offline", ctx -> promptOffline(ctx.player(), ctx.viewer(), requestOf(ctx)));
        menus.registerSpec(SPEC_ID, MenuSpecs.loadOrBundled(SPEC_RESOURCE, dataFolder, 6, log));
    }

    /**
     * Register one footer button's placeholders, its shown-when condition and its click. The two buttons differ only
     * in which of the caller's footers they read, so they are registered from one place rather than written twice.
     */
    private void registerFooter(MenuBindings bindings, String name, int index) {
        bindings.condition(
                "picker:has-footer-" + name, (ctx, args) -> footer(ctx, index).isPresent());
        bindings.placeholder(
                "picker_footer_" + name + "_icon",
                ctx -> footer(ctx, index).map(button -> button.icon().name()).orElse(Material.AIR.name()));
        bindings.placeholder(
                "picker_footer_" + name + "_name",
                ctx -> footer(ctx, index)
                        .map(button -> text(ctx, button.label(), Map.of()))
                        .orElse(""));
        bindings.placeholder(
                "picker_footer_" + name + "_lore",
                ctx -> footer(ctx, index)
                        .map(button -> text(ctx, button.lore(), Map.of()))
                        .orElse(""));
        bindings.action(
                "picker:footer-" + name,
                ctx -> footer(ctx, index).ifPresent(button -> button.onClick().accept(ctx.player())));
    }

    /**
     * Open the picker for {@code viewer}: enumerate the online roster on the global thread, then open the head grid
     * through the engine on the viewer's entity thread. A head click or a resolved offline name fires
     * {@code request.onPick}.
     */
    public void open(Player viewer, PlayerRef viewerRef, Request request) {
        Objects.requireNonNull(viewer, "viewer");
        Objects.requireNonNull(viewerRef, "viewerRef");
        Objects.requireNonNull(request, "request");
        scheduler.onGlobal(() -> {
            // Route the roster through the viewer's canSee graph so a vanished player the viewer may not see is
            // never offered as a pick target; the viewer always sees themselves (canSee-self is true).
            List<PlayerRef> roster = server.getOnlinePlayers().stream()
                    .filter(viewer::canSee)
                    .map(BukkitRefs::toRef)
                    .toList();
            menus.open(viewerRef, SPEC_ID, new Session(request, roster));
        });
    }

    /** Open the input prompt for a typed name; a submission flows through {@link #resolveTyped}. */
    private void promptOffline(Player viewer, PlayerRef viewerRef, Request request) {
        textInput.prompt(
                viewer,
                viewerRef,
                InputRequest.of(INPUT_KEY, GuiMessageKey.PLAYER_PICKER_CUSTOM_PROMPT),
                text -> resolveTyped(viewer, viewerRef, request, text),
                () -> open(viewer, viewerRef, request));
    }

    /**
     * Resolve the typed name through the caller's offline resolver off the tick thread (a profile lookup may block),
     * then act on the viewer's entity thread: an unresolved name replies with the unknown-player key and reopens, a
     * resolved name fires the pick callback. Package-private so the resolve branch is unit-tested without driving a
     * live anvil: the sync test scheduler runs callbacks inline.
     */
    void resolveTyped(Player viewer, PlayerRef viewerRef, Request request, String input) {
        String name = input.strip();
        scheduler.async(() -> {
            Optional<PlayerRef> resolved = request.offlineResolver().apply(name);
            scheduler.onEntity(viewerRef, () -> {
                if (resolved.isEmpty()) {
                    sink.deliver(
                            viewerRef, messages.resolve(viewerRef, request.unknownPlayerKey(), Map.of("player", name)));
                    open(viewer, viewerRef, request);
                    return;
                }
                request.onPick().accept(resolved.get());
            });
        });
    }

    private String text(MenuContext ctx, MessageKey key, Map<String, String> placeholders) {
        return messages.resolve(ctx.viewer(), key, placeholders);
    }

    private static PlayerRef candidateOf(MenuContext ctx) {
        return ctx.entry(PlayerRef.class);
    }

    private static Request requestOf(MenuContext ctx) {
        return ctx.subject(Session.class).request();
    }

    private static Request requestOf(MenuActionContext ctx) {
        return ctx.subject(Session.class).request();
    }

    /** The caller's footer button at {@code index}, or empty when this caller supplied fewer buttons. */
    private static Optional<FooterButton> footer(MenuContext ctx, int index) {
        return footer(requestOf(ctx), index);
    }

    private static Optional<FooterButton> footer(MenuActionContext ctx, int index) {
        return footer(requestOf(ctx), index);
    }

    private static Optional<FooterButton> footer(Request request, int index) {
        List<FooterButton> footers = request.footerButtons();
        return index < footers.size() ? Optional.of(footers.get(index)) : Optional.empty();
    }

    /**
     * The subject of one open picker: what the caller asked for and the roster snapshotted on the global thread, so
     * the engine renders without touching the server's player list itself.
     *
     * @param request the opening screen's title, callbacks and footer buttons
     * @param roster the online players the viewer may see, in server order
     */
    public record Session(Request request, List<PlayerRef> roster) {

        public Session {
            Objects.requireNonNull(request, "request");
            roster = List.copyOf(Objects.requireNonNull(roster, "roster"));
        }
    }

    /**
     * One picker invocation's caller-supplied parts, keeping {@link PlayerPickerView} generic: the menu title, the
     * callback fired with the chosen target, the resolver that turns a typed offline name into a {@link PlayerRef}, the
     * reply key used when a typed name resolves to nothing, and any extra footer buttons.
     *
     * <p>The four-argument constructor is the common case (the sanction flows that only pick a target); it defaults
     * {@link #footerButtons} to empty so those callers are unchanged. The jail hub uses the full constructor to add its
     * [Jails] and [Jailed players] actions.
     *
     * @param title the menu-title catalog key
     * @param onPick invoked with the chosen target (a clicked head, or a resolved typed name)
     * @param offlineResolver maps a typed name to a target, or empty when the name is unknown
     * @param unknownPlayerKey the reply key for an unresolvable typed name (filled with {@code {player}})
     * @param footerButtons extra bottom-row buttons that are not a player pick (empty for the sanction callers)
     */
    public record Request(
            MessageKey title,
            Consumer<PlayerRef> onPick,
            Function<String, Optional<PlayerRef>> offlineResolver,
            MessageKey unknownPlayerKey,
            List<FooterButton> footerButtons) {

        public Request {
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(onPick, "onPick");
            Objects.requireNonNull(offlineResolver, "offlineResolver");
            Objects.requireNonNull(unknownPlayerKey, "unknownPlayerKey");
            footerButtons = List.copyOf(Objects.requireNonNull(footerButtons, "footerButtons"));
        }

        /** The common case: a picker with no extra footer buttons (just the target heads and the offline anvil). */
        public Request(
                MessageKey title,
                Consumer<PlayerRef> onPick,
                Function<String, Optional<PlayerRef>> offlineResolver,
                MessageKey unknownPlayerKey) {
            this(title, onPick, offlineResolver, unknownPlayerKey, List.of());
        }
    }

    /**
     * An optional bottom-row picker button that performs an action other than picking a player, its name and lore
     * catalog keys, its icon material, and the callback fired (with the live viewer) when it is clicked. The jail hub
     * adds two: a jails manager and a jailed-players list.
     *
     * @param label the button-name catalog key
     * @param lore the button-lore catalog key
     * @param icon the button material
     * @param onClick invoked with the viewer when the button is clicked
     */
    public record FooterButton(MessageKey label, MessageKey lore, Material icon, Consumer<Player> onClick) {

        public FooterButton {
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(lore, "lore");
            Objects.requireNonNull(icon, "icon");
            Objects.requireNonNull(onClick, "onClick");
        }
    }
}

package com.uxplima.uxmessentials.communication.adapter.inbound.gui;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.communication.application.CommunicationMessageKey;
import com.uxplima.uxmessentials.communication.application.port.AnnouncementStore;
import com.uxplima.uxmessentials.communication.application.port.AnnouncerSettingsStore;
import com.uxplima.uxmessentials.communication.domain.AnnouncerSettings;
import com.uxplima.uxmessentials.communication.domain.StoredAnnouncement;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityEditorLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityEditorView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.EntityListView;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayouts;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.InputRequest;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.input.TextInput;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.EditableProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ListProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ListPropertyLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ListPropertyText;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.TextProperty;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.property.ToggleProperty;
import com.uxplima.uxmessentials.shared.adapter.outbound.BukkitRefs;
import com.uxplima.uxmessentials.shared.adapter.outbound.style.Tiles;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.display.BroadcastChannel;
import com.uxplima.uxmessentials.shared.display.ConditionTargets;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmlib.item.ItemBuilder;
import org.jspecify.annotations.NullMarked;

/**
 * The {@code /announce} (and {@code /announce editor}) editor: a paginated {@link EntityListView} of the
 * DB-backed store announcements with a create button, and a per-announcement {@link EntityEditorView} whose
 * properties write straight through the {@link AnnouncementStore}. Every edit is durable, and because the announcer
 * rotates over the config set plus the enabled store set (re-read each tick), a create/edit/enable/delete here takes
 * effect on the next announcer tick with no reload.
 *
 * <p>The per-announcement properties read their current value fresh from the store on each open (the list-click
 * snapshot would go stale after an edit, the same reason the hologram editor re-reads its repository). The world and
 * permission targets do not write the condition directly. They compose into the stored display-condition string
 * through {@link ConditionTargets}, so setting one target preserves the other. The view holds no domain logic: a
 * property mutates by saving a {@link StoredAnnouncement} carrying the changed field; the announcer's merge step
 * does the rest.
 */
@NullMarked
public final class AnnouncementEditorView {

    private static final String MODULE = "communication";
    private static final String LIST_LAYOUT = "announcement-editor-list";
    private static final String EDITOR_LAYOUT = "announcement-editor";
    private static final String SETTINGS_LAYOUT = "announcer-settings";

    private static final String CREATE_INPUT_KEY = "communication.announce-create";
    private static final String TEXT_FIELD_INPUT_KEY = "editor.text-field";
    private static final String LIST_ENTRY_INPUT_KEY = "editor.list-entry";

    /** The default editor property slots: nine buttons across a three-row chest, back and delete on the last row. */
    private static final List<Integer> DEFAULT_PROPERTY_SLOTS = List.of(10, 11, 12, 13, 14, 15, 16, 19, 20);

    private static final int DEFAULT_BACK_SLOT = 22;
    private static final int DEFAULT_DELETE_SLOT = 26;
    private static final int DEFAULT_CREATE_SLOT = 49;

    /** The last slot of the six-row list chest, where the announcer-settings button sits. */
    private static final int SETTINGS_BUTTON_SLOT = 53;

    /** The two settings-screen property slots and its back button, a single three-row chest. */
    private static final List<Integer> SETTINGS_PROPERTY_SLOTS = List.of(11, 15);

    private static final int SETTINGS_BACK_SLOT = 22;

    /**
     * The settings screen edits one global record, so it has no list of entities to key the editor by; this is the
     * stable singleton handle the {@link EntityEditorView} is opened with. The properties read the live settings
     * fresh from the store on each open, so the marker carries no state of its own.
     */
    private static final Object SETTINGS_SINGLETON = new Object();

    /**
     * The stable synthetic actor a GUI value-lore word ("None", "On") is resolved for. A value-lore render has no
     * viewer handle (the property reads the bare value), so the locale-independent word is resolved against a fixed
     * ref rather than the live viewer, the same shape the hologram editor uses for its "none"/"default" words.
     */
    private static final PlayerRef GUI_ACTOR = new PlayerRef(new java.util.UUID(0L, 0L), "announce-gui");

    private final GuiText guiText;
    private final Scheduler scheduler;
    private final Messages messages;
    private final AnnouncementStore store;
    private final AnnouncerSettingsStore settingsStore;
    private final TextInput textInput;
    private final ListPropertyLayout messageListLayout;
    private final EntityListView<StoredAnnouncement> list;
    private final EntityEditorView<StoredAnnouncement> editor;
    private final EntityEditorView<Object> settings;

    public AnnouncementEditorView(
            Menus menus,
            GuiText guiText,
            Scheduler scheduler,
            Messages messages,
            AnnouncementStore store,
            AnnouncerSettingsStore settingsStore,
            GuiLayouts guiLayouts,
            TextInput textInput) {
        Objects.requireNonNull(menus, "menus");
        this.guiText = Objects.requireNonNull(guiText, "guiText");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.store = Objects.requireNonNull(store, "store");
        this.settingsStore = Objects.requireNonNull(settingsStore, "settingsStore");
        this.textInput = Objects.requireNonNull(textInput, "textInput");
        Objects.requireNonNull(guiLayouts, "guiLayouts");

        // The message-lines sub-menu is a ListProperty; GuiLayouts has no list-property loader, so it uses the code
        // default (the same way the hologram line-list sub-menu does), keeping the editor self-contained.
        this.messageListLayout = defaultMessageLayout();

        EntityEditorLayout editorLayout = guiLayouts.loadEntityEditor(
                MODULE,
                EDITOR_LAYOUT,
                EntityEditorLayout.withDelete(DEFAULT_PROPERTY_SLOTS, DEFAULT_BACK_SLOT, DEFAULT_DELETE_SLOT));
        this.editor = EntityEditorView.<StoredAnnouncement>builder()
                .menus(menus)
                .guiText(guiText)
                .scheduler(scheduler)
                .layout(editorLayout)
                .title((viewer, announcement) -> guiText.text(
                        viewer, CommunicationMessageKey.ANNOUNCE_EDITOR_TITLE, Map.of("id", announcement.id())))
                .valueLore(CommunicationMessageKey.ANNOUNCE_EDITOR_VALUE_LORE)
                .backName(CommunicationMessageKey.ANNOUNCE_EDITOR_BACK)
                .properties(this::properties)
                // list is assigned after this builder; both back and delete reach it through openList at click time
                // (the lambdas run long after construction), so the field read is always the fully-built list.
                .onBack((player, viewer) -> openList(player, viewer))
                .onDelete(
                        CommunicationMessageKey.ANNOUNCE_EDITOR_DELETE,
                        CommunicationMessageKey.ANNOUNCE_EDITOR_DELETE_CONFIRM,
                        this::deleteAnnouncement)
                .build();

        EntityListLayout listLayout = guiLayouts.loadEntityList(
                MODULE,
                LIST_LAYOUT,
                EntityListLayout.withCreate(Material.PAPER, DEFAULT_CREATE_SLOT, Material.LIME_DYE)
                        .withAction(SETTINGS_BUTTON_SLOT, Material.COMPARATOR));
        EntityEditorLayout settingsLayout = guiLayouts.loadEntityEditor(
                MODULE, SETTINGS_LAYOUT, EntityEditorLayout.codeDefault(SETTINGS_PROPERTY_SLOTS, SETTINGS_BACK_SLOT));
        this.settings = EntityEditorView.builder()
                .menus(menus)
                .guiText(guiText)
                .scheduler(scheduler)
                .layout(settingsLayout)
                .title((viewer, ignored) -> guiText.text(viewer, CommunicationMessageKey.ANNOUNCE_SETTINGS_TITLE))
                .valueLore(CommunicationMessageKey.ANNOUNCE_SETTINGS_VALUE_LORE)
                .backName(CommunicationMessageKey.ANNOUNCE_SETTINGS_BACK)
                .properties(ignored -> settingsProperties())
                .onBack((player, viewer) -> openList(player, viewer))
                .build();

        this.list = EntityListView.<StoredAnnouncement>builder()
                .menus(menus)
                .guiText(guiText)
                .scheduler(scheduler)
                .layout(listLayout)
                .title(CommunicationMessageKey.ANNOUNCE_EDITOR_LIST_TITLE)
                .navNames(
                        CommunicationMessageKey.ANNOUNCE_EDITOR_LIST_PREV,
                        CommunicationMessageKey.ANNOUNCE_EDITOR_LIST_NEXT)
                .entities(store::all)
                .iconRenderer(this::listIcon)
                .onSelect((player, announcement) -> editor.open(player, BukkitRefs.toRef(player), announcement))
                .onCreate(CommunicationMessageKey.ANNOUNCE_EDITOR_LIST_CREATE, this::promptCreate)
                .onAction(CommunicationMessageKey.ANNOUNCE_SETTINGS_BUTTON, this::openSettings)
                .build();
    }

    /** Open the editor list for {@code player}, on the viewer's entity thread (the framework schedules it). */
    public void open(Player player, PlayerRef viewer) {
        list.open(player, viewer);
    }

    /** Reopen the editor list: the back target and the post-delete landing, read at click time. */
    private void openList(Player player, PlayerRef viewer) {
        list.open(player, viewer);
    }

    /** Open the global announcer-settings screen: the list GUI's last-slot button opens it. */
    private void openSettings(Player player) {
        settings.open(player, BukkitRefs.toRef(player), SETTINGS_SINGLETON);
    }

    /** The settings screen, exposed so a test can resolve its property slots without a live click. */
    public EntityEditorView<Object> settings() {
        return settings;
    }

    /**
     * The two global announcer settings as editable properties: the default interval (seconds between
     * announcements) and the minimum online players gate. Each reads the live persisted value fresh from the store,
     * accepts a number through a text prompt, and writes it back; a blank or non-positive interval and a blank or
     * negative gate both clear the override, returning that setting to the file default.
     */
    private List<EditableProperty> settingsProperties() {
        return List.of(intervalProperty(), minPlayersProperty());
    }

    private EditableProperty intervalProperty() {
        return new TextProperty(
                TEXT_FIELD_INPUT_KEY,
                CommunicationMessageKey.ANNOUNCE_SETTINGS_PROP_INTERVAL,
                CommunicationMessageKey.ANNOUNCE_SETTINGS_PROP_INTERVAL_PROMPT,
                Material.CLOCK,
                () -> intervalWord(currentSettings()),
                AnnouncementEditorView::parseLong,
                raw -> saveSettings(settings -> settings.withIntervalSeconds(parseLongOrClear(raw))),
                textInput,
                scheduler);
    }

    private EditableProperty minPlayersProperty() {
        return new TextProperty(
                TEXT_FIELD_INPUT_KEY,
                CommunicationMessageKey.ANNOUNCE_SETTINGS_PROP_MIN_PLAYERS,
                CommunicationMessageKey.ANNOUNCE_SETTINGS_PROP_MIN_PLAYERS_PROMPT,
                Material.PLAYER_HEAD,
                () -> minPlayersWord(currentSettings()),
                AnnouncementEditorView::parseLong,
                raw -> saveSettings(settings -> settings.withMinOnlinePlayers((int) parseLongOrClear(raw))),
                textInput,
                scheduler);
    }

    /** The live persisted settings, read fresh each time a property renders or applies. */
    private AnnouncerSettings currentSettings() {
        return settingsStore.load();
    }

    /** Load, mutate, and persist the global settings in one write; the editor reopens once the write lands. */
    private void saveSettings(UnaryOperator<AnnouncerSettings> mutation) {
        settingsStore.save(mutation.apply(currentSettings()));
    }

    private String intervalWord(AnnouncerSettings settings) {
        return settings.interval()
                .map(duration -> Long.toString(duration.toSeconds()))
                .orElseGet(this::defaultWord);
    }

    private String minPlayersWord(AnnouncerSettings settings) {
        return settings.minOnlinePlayers().isPresent()
                ? Integer.toString(settings.minOnlinePlayers().getAsInt())
                : defaultWord();
    }

    private String defaultWord() {
        return messages.resolve(GUI_ACTOR, CommunicationMessageKey.ANNOUNCE_SETTINGS_VALUE_DEFAULT, Map.of());
    }

    /**
     * Accept a typed settings value: a non-negative integer, or a clear token ({@code -}, {@code default}) that
     * resets the setting to the file default. A non-numeric, non-clear entry is rejected so the prompt reopens
     * without writing.
     */
    private static Optional<String> parseLong(String raw) {
        String trimmed = raw.trim();
        if (isClearToken(trimmed)) {
            return Optional.of(trimmed);
        }
        try {
            long value = Long.parseLong(trimmed);
            return value >= 0 ? Optional.of(Long.toString(value)) : Optional.empty();
        } catch (NumberFormatException notANumber) {
            return Optional.empty();
        }
    }

    /** The already-validated value as a long; a clear token maps to {@code -1}, the "unset" sentinel the setters read. */
    private static long parseLongOrClear(String accepted) {
        String trimmed = accepted.trim();
        if (isClearToken(trimmed)) {
            return -1L;
        }
        return Long.parseLong(trimmed);
    }

    private static boolean isClearToken(String trimmed) {
        return trimmed.equals("-") || trimmed.equalsIgnoreCase("default") || trimmed.equalsIgnoreCase("clear");
    }

    /** Delete the announcement off-thread and return the viewer to the list once the row is gone. */
    private void deleteAnnouncement(Player player, StoredAnnouncement announcement) {
        PlayerRef viewer = BukkitRefs.toRef(player);
        scheduler.async(() -> {
            store.delete(announcement.id());
            scheduler.onEntity(viewer, () -> list.open(player, viewer));
        });
    }

    /** The per-announcement editor, exposed so a test can resolve a slot to its property without a live click. */
    public EntityEditorView<StoredAnnouncement> editor() {
        return editor;
    }

    /** The list view, exposed so a test can assert its rendered entries and the create handler. */
    public EntityListView<StoredAnnouncement> list() {
        return list;
    }

    private ItemStack listIcon(PlayerRef viewer, StoredAnnouncement announcement) {
        Map<String, String> placeholders = Map.of(
                "id", announcement.id(),
                "state", enabledWord(viewer, announcement.enabled()),
                "lines", Integer.toString(announcement.lines().size()),
                "channels", channels(announcement));
        return ItemBuilder.of(announcement.enabled() ? Material.PAPER : Material.GRAY_DYE)
                .name(Tiles.blankName())
                .lore(Tiles.titled(
                        guiText.text(viewer, CommunicationMessageKey.ANNOUNCE_EDITOR_ENTRY_NAME, placeholders),
                        guiText.text(viewer, CommunicationMessageKey.ANNOUNCE_EDITOR_ENTRY_LORE, placeholders)))
                .build();
    }

    private void promptCreate(Player player) {
        PlayerRef viewer = BukkitRefs.toRef(player);
        textInput.prompt(
                player,
                viewer,
                InputRequest.of(CREATE_INPUT_KEY, CommunicationMessageKey.ANNOUNCE_EDITOR_LIST_CREATE_PROMPT),
                text -> handleCreate(player, text),
                () -> list.open(player, viewer));
    }

    private void handleCreate(Player player, String text) {
        PlayerRef viewer = BukkitRefs.toRef(player);
        if (text.isBlank()) {
            list.open(player, viewer);
            return;
        }
        String id = text.trim();
        scheduler.async(() -> {
            if (store.exists(id)) {
                // A create collision keeps the existing announcement untouched and just reopens the list; the
                // create is a no-op rather than an overwrite, so an operator never loses an announcement by reusing
                // an id.
                scheduler.onEntity(viewer, () -> list.open(player, viewer));
                return;
            }
            // A fresh announcement seeds one placeholder line carrying the id; the operator edits the message next.
            store.save(StoredAnnouncement.fresh(id, "<gray>" + id));
            // Land straight in the new announcement's editor.
            store.find(id).ifPresent(created -> scheduler.onEntity(viewer, () -> editor.open(player, viewer, created)));
        });
    }

    private List<EditableProperty> properties(StoredAnnouncement announcement) {
        String id = announcement.id();
        List<EditableProperty> props = new ArrayList<>();
        props.add(messageProperty(id));
        props.add(enabledProperty(id));
        props.add(channelProperty(
                id, BroadcastChannel.CHAT, CommunicationMessageKey.ANNOUNCE_EDITOR_PROP_CHANNEL_CHAT, Material.PAPER));
        props.add(channelProperty(
                id,
                BroadcastChannel.ACTION_BAR,
                CommunicationMessageKey.ANNOUNCE_EDITOR_PROP_CHANNEL_ACTION_BAR,
                Material.SPRUCE_SIGN));
        props.add(channelProperty(
                id,
                BroadcastChannel.TITLE,
                CommunicationMessageKey.ANNOUNCE_EDITOR_PROP_CHANNEL_TITLE,
                Material.OAK_SIGN));
        props.add(channelProperty(
                id,
                BroadcastChannel.SUBTITLE,
                CommunicationMessageKey.ANNOUNCE_EDITOR_PROP_CHANNEL_SUBTITLE,
                Material.BIRCH_SIGN));
        props.add(channelProperty(
                id,
                BroadcastChannel.BOSS_BAR,
                CommunicationMessageKey.ANNOUNCE_EDITOR_PROP_CHANNEL_BOSS_BAR,
                Material.DRAGON_HEAD));
        props.add(worldProperty(id));
        props.add(permissionProperty(id));
        return props;
    }

    private EditableProperty messageProperty(String id) {
        return new ListProperty(
                LIST_ENTRY_INPUT_KEY,
                CommunicationMessageKey.ANNOUNCE_EDITOR_PROP_MESSAGE,
                Material.WRITABLE_BOOK,
                guiText,
                () -> current(id).map(StoredAnnouncement::lines).orElse(List.of()),
                lines -> applyLines(id, lines),
                new ListPropertyText(
                        CommunicationMessageKey.ANNOUNCE_EDITOR_MESSAGE_TITLE,
                        CommunicationMessageKey.ANNOUNCE_EDITOR_MESSAGE_ENTRY_NAME,
                        CommunicationMessageKey.ANNOUNCE_EDITOR_MESSAGE_ENTRY_HINTS,
                        CommunicationMessageKey.ANNOUNCE_EDITOR_MESSAGE_ADD,
                        CommunicationMessageKey.ANNOUNCE_EDITOR_MESSAGE_ADD_PROMPT,
                        CommunicationMessageKey.ANNOUNCE_EDITOR_MESSAGE_EDIT_PROMPT,
                        CommunicationMessageKey.ANNOUNCE_EDITOR_MESSAGE_REMOVE_CONFIRM,
                        CommunicationMessageKey.ANNOUNCE_EDITOR_MESSAGE_BACK),
                messageListLayout,
                textInput,
                scheduler);
    }

    /**
     * Apply a wholesale line-list edit. The line list is the announcement's content, so a list with at least one
     * line replaces the lines; an emptied list keeps the announcement valid (an announcement must declare a line),
     * so a single blank line stands in rather than letting the store reject the save.
     */
    private void applyLines(String id, List<String> next) {
        List<String> lines = next.isEmpty() ? List.of(" ") : next;
        update(id, announcement -> announcement.withLines(lines));
    }

    private EditableProperty enabledProperty(String id) {
        return ToggleProperty.ofBoolean(
                CommunicationMessageKey.ANNOUNCE_EDITOR_PROP_ENABLED,
                Material.LEVER,
                () -> current(id).map(StoredAnnouncement::enabled).orElse(false),
                this::enabledWord,
                value -> update(id, announcement -> announcement.withEnabled(value)),
                scheduler);
    }

    private EditableProperty channelProperty(String id, BroadcastChannel channel, MessageKey label, Material icon) {
        return ToggleProperty.ofBoolean(
                label,
                icon,
                () -> current(id)
                        .map(announcement -> announcement.channels().contains(channel))
                        .orElse(false),
                this::onOff,
                value -> update(
                        id,
                        announcement ->
                                announcement.withChannels(toggleChannel(announcement.channels(), channel, value))),
                scheduler);
    }

    private static Set<BroadcastChannel> toggleChannel(
            Set<BroadcastChannel> current, BroadcastChannel channel, boolean on) {
        Set<BroadcastChannel> next = new LinkedHashSet<>(current);
        if (on) {
            next.add(channel);
        } else {
            next.remove(channel);
        }
        // The domain record falls back to CHAT for an empty set, so turning the last channel off leaves CHAT on
        // rather than producing an announcement with no surface.
        return next;
    }

    private EditableProperty worldProperty(String id) {
        return new TextProperty(
                TEXT_FIELD_INPUT_KEY,
                CommunicationMessageKey.ANNOUNCE_EDITOR_PROP_WORLD,
                CommunicationMessageKey.ANNOUNCE_EDITOR_PROP_WORLD_PROMPT,
                Material.GRASS_BLOCK,
                () -> current(id)
                        .flatMap(announcement -> ConditionTargets.world(announcement.condition()))
                        .orElseGet(this::none),
                raw -> Optional.of(clearToken(raw)),
                value -> update(
                        id,
                        announcement -> announcement.withCondition(
                                ConditionTargets.withWorld(announcement.condition(), value))),
                textInput,
                scheduler);
    }

    private EditableProperty permissionProperty(String id) {
        return new TextProperty(
                TEXT_FIELD_INPUT_KEY,
                CommunicationMessageKey.ANNOUNCE_EDITOR_PROP_PERMISSION,
                CommunicationMessageKey.ANNOUNCE_EDITOR_PROP_PERMISSION_PROMPT,
                Material.NAME_TAG,
                () -> current(id)
                        .flatMap(announcement -> ConditionTargets.permission(announcement.condition()))
                        .orElseGet(this::none),
                raw -> Optional.of(clearToken(raw)),
                value -> update(
                        id,
                        announcement -> announcement.withCondition(
                                ConditionTargets.withPermission(announcement.condition(), value))),
                textInput,
                scheduler);
    }

    /** Read the current stored announcement, the fresh value the editor reads on each open. */
    private Optional<StoredAnnouncement> current(String id) {
        return store.find(id);
    }

    /** Apply {@code mutation} to the current announcement and persist it; a no-op if the row was deleted meanwhile. */
    private void update(String id, UnaryOperator<StoredAnnouncement> mutation) {
        current(id).ifPresent(announcement -> store.save(mutation.apply(announcement)));
    }

    /** A clear token ({@code -}, {@code none}, {@code clear}) empties a target; otherwise the trimmed text is kept. */
    private static String clearToken(String raw) {
        String trimmed = raw.trim();
        if (trimmed.equals("-") || trimmed.equalsIgnoreCase("none") || trimmed.equalsIgnoreCase("clear")) {
            return "";
        }
        return trimmed;
    }

    private String enabledWord(PlayerRef viewer, boolean enabled) {
        return messages.resolve(
                viewer,
                enabled
                        ? CommunicationMessageKey.ANNOUNCE_EDITOR_VALUE_ON
                        : CommunicationMessageKey.ANNOUNCE_EDITOR_VALUE_OFF,
                Map.of());
    }

    private String onOff(PlayerRef viewer, boolean on) {
        return enabledWord(viewer, on);
    }

    private String none() {
        return messages.resolve(GUI_ACTOR, CommunicationMessageKey.ANNOUNCE_EDITOR_VALUE_NONE, Map.of());
    }

    private static ListPropertyLayout defaultMessageLayout() {
        return new ListPropertyLayout(
                6,
                List.of(10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34),
                48,
                50,
                Material.PAPER,
                Material.LIME_DYE,
                Material.ARROW,
                Material.BLACK_STAINED_GLASS_PANE);
    }

    private static String channels(StoredAnnouncement announcement) {
        return announcement.channels().stream()
                .map(Enum::name)
                .sorted()
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }
}

package com.uxplima.uxmessentials.vaults.adapter.inbound.gui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.bukkit.Material;

import net.kyori.adventure.text.minimessage.MiniMessage;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.Menus;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuSpecs;
import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.application.port.Scheduler;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.shared.domain.Result;
import com.uxplima.uxmessentials.vaults.application.ListVaults;
import com.uxplima.uxmessentials.vaults.application.OpenVault;
import com.uxplima.uxmessentials.vaults.application.VaultAmountQuota;
import com.uxplima.uxmessentials.vaults.application.VaultChargeSettings;
import com.uxplima.uxmessentials.vaults.application.VaultNotifier;
import com.uxplima.uxmessentials.vaults.application.VaultSummary;
import com.uxplima.uxmessentials.vaults.application.VaultsMessageKey;
import com.uxplima.uxmessentials.vaults.domain.Vault;
import com.uxplima.uxmessentials.vaults.domain.VaultAmount;
import com.uxplima.uxmessentials.vaults.domain.VaultError;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Registers the {@code /vault} selector with the menu engine and opens it. A small class that registers the slot
 * list source, the placeholders its entries need, and the open-slot click action, then loads the
 * {@code vault-selector} spec and hands it to {@link Menus}.
 *
 * <p>The owned and locked indices collapse into one {@link VaultSlot} list template: an entry is owned when it
 * carries a {@link VaultSummary} and locked otherwise. The {@code vault:slots} source replicates the old view's
 * build logic. It lists the player's vaults, resolves the amount cap, and emits index {@code 1..cap}, including a
 * locked index only when {@code show-locked} is on. It reads the database, so the engine resolves it off the
 * viewer's region thread; it touches no Bukkit API. The {@code vault_icon}/{@code vault_name} placeholders read the
 * bound entry to render the right material and label, and {@code vault:open-slot} opens an owned vault through the
 * same {@link OpenVault} use case and {@link VaultView} the {@code /vault <n>} command drives, or nudges a locked
 * one, so the selector adds no quota, charge, or persistence logic of its own.
 */
@NullMarked
public final class VaultSelectorMenu {

    /** The engine spec id this menu registers and opens under. */
    public static final String SPEC_ID = "vault-selector";

    /** Disk-first then bundled, mirroring the GUI-layout loader, so an operator edit to the spec takes effect. */
    private static final String SPEC_RESOURCE = "modules/vaults/gui/vault-selector.conf";

    // A defensive clamp so an oversized stored name can never overflow the item label, independent of the
    // command's configurable rename cap (a name may pre-date a lowered cap or arrive from another backend).
    private static final int MAX_DISPLAY_NAME = 48;

    private final Menus menus;
    private final Messages messages;
    private final MessageSink sink;
    private final Scheduler scheduler;
    private final ListVaults listVaults;
    private final VaultAmountQuota amountQuota;
    private final OpenVault openVault;
    private final VaultView view;
    private final VaultNotifier notifier;
    private final VaultChargeSettings chargeSettings;
    private final VaultSelectorSettings settings;
    private final MiniMessage miniMessage;

    public VaultSelectorMenu(
            Menus menus,
            Messages messages,
            MessageSink sink,
            Scheduler scheduler,
            ListVaults listVaults,
            VaultAmountQuota amountQuota,
            OpenVault openVault,
            VaultView view,
            VaultNotifier notifier,
            VaultChargeSettings chargeSettings,
            VaultSelectorSettings settings) {
        this.menus = Objects.requireNonNull(menus, "menus");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.sink = Objects.requireNonNull(sink, "sink");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.listVaults = Objects.requireNonNull(listVaults, "listVaults");
        this.amountQuota = Objects.requireNonNull(amountQuota, "amountQuota");
        this.openVault = Objects.requireNonNull(openVault, "openVault");
        this.view = Objects.requireNonNull(view, "view");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.chargeSettings = Objects.requireNonNull(chargeSettings, "chargeSettings");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.miniMessage = MiniMessage.miniMessage();
    }

    /** Register the bindings the spec names and the spec itself; called once at vaults wiring time. */
    public void register(MenuBindings bindings, Path dataFolder, Logger log) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(dataFolder, "dataFolder");
        Objects.requireNonNull(log, "log");
        bindings.list("vault:slots", this::slots);
        bindings.placeholder("vault_icon", this::icon);
        bindings.placeholder("vault_name", this::name);
        bindings.placeholder("vault_lore", this::lore);
        bindings.action("vault:open-slot", this::openSlot);
        menus.registerSpec(SPEC_ID, MenuSpecs.loadOrBundled(SPEC_RESOURCE, dataFolder, 3, log));
    }

    /** Open the selector for {@code viewer} (the live player resolved by the engine). */
    public void open(PlayerRef viewer) {
        Objects.requireNonNull(viewer, "viewer");
        menus.open(viewer, SPEC_ID, null);
    }

    /**
     * The slot list, built off the viewer's region thread (it reads the database). Lists the player's vaults into
     * an index map, resolves the amount cap clamped to the highest owned index, and emits index {@code 1..cap}
     * each owned when in the map, else locked. A locked index is included only when {@code show-locked} is on, so
     * with show-locked off the list is exactly the owned indices.
     */
    private List<VaultSlot> slots(MenuContext ctx) {
        PlayerRef viewer = ctx.viewer();
        List<VaultSummary> owned = listVaults.list(viewer).asValue().orElse(List.of());
        Map<Integer, VaultSummary> byIndex =
                owned.stream().collect(Collectors.toMap(VaultSummary::index, summary -> summary));
        int cap = displayCap(viewer, byIndex.keySet());
        List<VaultSlot> out = new ArrayList<>();
        for (int index = 1; index <= cap; index++) {
            VaultSummary summary = byIndex.get(index);
            if (summary != null) {
                out.add(new VaultSlot(index, summary));
            } else if (settings.showLocked()) {
                out.add(new VaultSlot(index, null));
            }
        }
        return out;
    }

    /**
     * The highest index to render: the resolved amount cap, but never fewer than the highest index the player
     * already owns (so an owned vault is always shown even if the cap has since shrunk). An unlimited cap is
     * clamped to what the player owns, since an infinite locked tail cannot be rendered.
     */
    private int displayCap(PlayerRef viewer, java.util.Set<Integer> owned) {
        int highestOwned = owned.stream().mapToInt(Integer::intValue).max().orElse(0);
        VaultAmount amount = amountQuota.resolve(viewer);
        if (amount.unlimited()) {
            return highestOwned;
        }
        return Math.max(highestOwned, amount.cap());
    }

    /** The entry's icon material name: the owned (custom-or-default) icon, or the locked icon when locked. */
    private String icon(MenuContext ctx) {
        VaultSlot slot = ctx.entry(VaultSlot.class);
        VaultSummary summary = slot.summary();
        if (summary == null) {
            return settings.lockedIcon().name();
        }
        return settings.iconFor(summary.iconMaterial()).name();
    }

    /**
     * The entry's fully-resolved name source string, ready for the renderer to style. An owned entry shows the
     * player's clamped, tag-escaped custom name (or the default index name when none is set); a locked entry shows
     * the locked label. The placeholder resolves the catalog itself because the key differs by owned/locked/named,
     * which a single static {@code @key} could not express.
     */
    private String name(MenuContext ctx) {
        VaultSlot slot = ctx.entry(VaultSlot.class);
        VaultSummary summary = slot.summary();
        if (summary == null) {
            return resolve(
                    ctx.viewer(), VaultsMessageKey.VAULT_SELECTOR_LOCKED_NAME, "index", Integer.toString(slot.index()));
        }
        String custom = summary.displayName();
        if (custom == null || custom.isBlank()) {
            return resolve(
                    ctx.viewer(),
                    VaultsMessageKey.VAULT_SELECTOR_ENTRY_NAME,
                    "index",
                    Integer.toString(summary.index()));
        }
        return resolve(ctx.viewer(), VaultsMessageKey.VAULT_SELECTOR_NAMED_ENTRY, "name", displaySafe(custom));
    }

    /**
     * The entry's fully-resolved lore source string. An owned entry shows the shared entry lore; a locked entry
     * shows the locked lore with its index filled. Resolved here rather than as a static {@code @key} because the
     * key differs by owned/locked, the same reason {@link #name} resolves the catalog itself.
     */
    private String lore(MenuContext ctx) {
        VaultSlot slot = ctx.entry(VaultSlot.class);
        if (slot.summary() == null) {
            return resolve(
                    ctx.viewer(), VaultsMessageKey.VAULT_SELECTOR_LOCKED_LORE, "index", Integer.toString(slot.index()));
        }
        return messages.resolve(ctx.viewer(), VaultsMessageKey.VAULT_SELECTOR_ENTRY_LORE, Map.of());
    }

    /** Clamp the stored name to a sane menu width and escape MiniMessage tags so it cannot inject markup. */
    private String displaySafe(String name) {
        String clamped = name.length() > MAX_DISPLAY_NAME ? name.substring(0, MAX_DISPLAY_NAME) : name;
        return miniMessage.escapeTags(clamped);
    }

    private String resolve(PlayerRef viewer, MessageKey key, String token, String value) {
        return messages.resolve(viewer, key, Map.of(token, value));
    }

    /**
     * Left-click on a slot. A locked slot only sends the "open it with /vault n" nudge and leaves the menu open. An
     * owned slot opens that vault through the same {@link OpenVault} use case the command drives: the contents read
     * runs off the click (region) thread, and the window open (or rejection) bridges back to the viewer's entity
     * thread, mirroring {@code /vault <n>}.
     */
    private void openSlot(MenuActionContext ctx) {
        VaultSlot slot = ctx.entry(VaultSlot.class);
        PlayerRef viewer = ctx.viewer();
        if (slot.summary() == null) {
            sink.deliver(
                    viewer,
                    messages.resolve(
                            viewer,
                            VaultsMessageKey.VAULT_SELECTOR_LOCKED_CLICK,
                            Map.of("index", Integer.toString(slot.index()))));
            return;
        }
        org.bukkit.entity.Player player = ctx.player();
        int index = slot.index();
        scheduler.async(() -> {
            Result<Vault, VaultError> resolved = openVault.open(viewer, index);
            if (resolved.isErr()) {
                scheduler.onEntity(viewer, () -> reject(viewer, index, resolved.errorOrThrow()));
                return;
            }
            Vault vault = resolved.orElseThrow();
            scheduler.onEntity(viewer, () -> {
                player.closeInventory();
                view.open(player, viewer, viewer, vault);
                notifier.opened(viewer, index);
            });
        });
    }

    private void reject(PlayerRef viewer, int index, VaultError error) {
        switch (error) {
            case AMOUNT_EXCEEDED -> notifier.amountExceeded(viewer, index);
            case NONE_OWNED -> notifier.noneOwned(viewer);
            // Only owned vaults are clickable, so a charge failure here is the per-open fee.
            case CANNOT_AFFORD ->
                notifier.cannotAfford(viewer, chargeSettings.costToOpen().toPlainString());
            case DELETE_UNKNOWN -> notifier.deleteUnknown(viewer, index);
            case VAULT_UNKNOWN -> notifier.renameUnknown(viewer, index);
        }
    }

    /**
     * One cell of the selector grid: a vault index and the owned summary that fills it, or {@code null} when the
     * index is locked (within the cap but not yet allocated). The placeholders and the open action branch on
     * whether {@link #summary} is present, so owned and locked share one list template.
     *
     * @param index the one-based vault number this cell addresses
     * @param summary the owned vault's read model, or {@code null} when the index is locked
     */
    public record VaultSlot(int index, @Nullable VaultSummary summary) {

        public VaultSlot {
            if (index < 1) {
                throw new IllegalArgumentException("vault index must be at least 1: " + index);
            }
        }
    }

    /**
     * The selector's parsed presentation: the menu layout (rows, nav slots, nav icon), the owned and locked icon
     * materials, and whether locked indices are shown at all. Parsed once at wire time so a misconfigured material
     * falls back without a hot-path lookup.
     *
     * @param layout the menu layout (rows, nav slots, nav icon)
     * @param ownedIcon the icon material for an owned vault
     * @param lockedIcon the icon material for a locked index
     * @param showLocked whether to render locked indices at all
     */
    public record VaultSelectorSettings(GuiLayout layout, Material ownedIcon, Material lockedIcon, boolean showLocked) {

        public VaultSelectorSettings {
            Objects.requireNonNull(layout, "layout");
            Objects.requireNonNull(ownedIcon, "ownedIcon");
            Objects.requireNonNull(lockedIcon, "lockedIcon");
        }

        /**
         * Resolve a per-vault icon material <em>name</em> to a {@code Material}, falling back to the configured
         * owned icon for a {@code null}, blank, unknown, or air material. The lookup is on the menu-build path (the
         * icon is per-vault dynamic data, not config), so it is bounded to one {@code matchMaterial} call.
         */
        public Material iconFor(@Nullable String materialName) {
            if (materialName == null || materialName.isBlank()) {
                return ownedIcon;
            }
            Material parsed = Material.matchMaterial(materialName);
            return parsed != null && !parsed.isAir() ? parsed : ownedIcon;
        }
    }
}

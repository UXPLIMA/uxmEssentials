package com.uxplima.uxmessentials.vaults.application;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.uxplima.uxmessentials.shared.application.message.MessageKey;
import com.uxplima.uxmessentials.shared.application.message.Notifier;
import com.uxplima.uxmessentials.shared.application.port.MessageSink;
import com.uxplima.uxmessentials.shared.application.port.Messages;
import com.uxplima.uxmessentials.shared.domain.PlayerRef;
import com.uxplima.uxmessentials.vaults.domain.VaultAmount;
import com.uxplima.uxmessentials.vaults.domain.VaultSize;

/**
 * Names the vaults context's player-facing feedback: one method per thing the player can be told (the vault
 * opened, the listing of owned vaults, a quota rejection, the admin override notice), each resolving a
 * {@link VaultsMessageKey} through the shared {@link Notifier}. Naming them here is what keeps inline
 * player-facing text out of the context.
 */
public final class VaultNotifier {

    private final Notifier notifier;

    public VaultNotifier(Messages messages, MessageSink sink) {
        this.notifier = new Notifier(messages, sink);
    }

    /** Tell {@code viewer} the vault at {@code index} opened. */
    public void opened(PlayerRef viewer, int index) {
        send(viewer, VaultsMessageKey.VAULT_OPENED, Map.of("index", Integer.toString(index)));
    }

    /** Tell {@code actor} they opened {@code owner}'s vault at {@code index} (the admin override). */
    public void adminOpened(PlayerRef actor, PlayerRef owner, int index) {
        send(
                actor,
                VaultsMessageKey.VAULT_ADMIN_OPENED,
                Map.of("player", owner.name(), "index", Integer.toString(index)));
    }

    /** List {@code owner}'s vault numbers as a header plus one comma-joined entry line. */
    public void list(PlayerRef viewer, List<Integer> indices) {
        send(viewer, VaultsMessageKey.VAULT_LIST_HEADER, Map.of("count", Integer.toString(indices.size())));
        String joined = indices.stream().map(Object::toString).collect(Collectors.joining(", "));
        send(viewer, VaultsMessageKey.VAULT_LIST_ENTRY, Map.of("vaults", joined));
    }

    /** Tell {@code viewer} they own no vaults yet. */
    public void noneOwned(PlayerRef viewer) {
        send(viewer, VaultsMessageKey.VAULT_NONE_OWNED, Map.of());
    }

    /**
     * Report {@code viewer}'s vault usage: how many vaults they own against their count cap, and the slot size
     * of each. The cap renders as the unlimited sentinel when the owner has no count limit. Read-only: this
     * resolves nothing and opens nothing.
     */
    public void showInfo(PlayerRef viewer, int ownedCount, VaultAmount cap, VaultSize size) {
        send(viewer, VaultsMessageKey.VAULT_INFO_HEADER, Map.of("owned", Integer.toString(ownedCount)));
        send(
                viewer,
                VaultsMessageKey.VAULT_INFO_LINE,
                Map.of(
                        "owned", Integer.toString(ownedCount),
                        "cap", cap.unlimited() ? "∞" : Integer.toString(cap.cap()),
                        "size", Integer.toString(size.slots())));
    }

    /** Tell {@code viewer} the requested index exceeds their vault-count quota. */
    public void amountExceeded(PlayerRef viewer, int index) {
        send(viewer, VaultsMessageKey.VAULT_AMOUNT_EXCEEDED, Map.of("index", Integer.toString(index)));
    }

    /** Tell {@code viewer} the vault at {@code index} was deleted. */
    public void deleted(PlayerRef viewer, int index) {
        send(viewer, VaultsMessageKey.VAULT_DELETED, Map.of("index", Integer.toString(index)));
    }

    /** Tell {@code viewer} there is no vault at {@code index} to delete. */
    public void deleteUnknown(PlayerRef viewer, int index) {
        send(viewer, VaultsMessageKey.VAULT_DELETE_UNKNOWN, Map.of("index", Integer.toString(index)));
    }

    /** Tell {@code viewer} the vault at {@code index} was renamed to {@code name}. */
    public void renamed(PlayerRef viewer, int index, String name) {
        send(viewer, VaultsMessageKey.VAULT_RENAMED, Map.of("index", Integer.toString(index), "name", name));
    }

    /** Tell {@code viewer} the vault at {@code index} had its display name cleared. */
    public void nameCleared(PlayerRef viewer, int index) {
        send(viewer, VaultsMessageKey.VAULT_NAME_CLEARED, Map.of("index", Integer.toString(index)));
    }

    /** Tell {@code viewer} the vault at {@code index} had its icon set to {@code material}. */
    public void iconSet(PlayerRef viewer, int index, String material) {
        send(viewer, VaultsMessageKey.VAULT_ICON_SET, Map.of("index", Integer.toString(index), "material", material));
    }

    /** Tell {@code viewer} there is no vault at {@code index} to rename or re-icon. */
    public void renameUnknown(PlayerRef viewer, int index) {
        send(viewer, VaultsMessageKey.VAULT_RENAME_UNKNOWN, Map.of("index", Integer.toString(index)));
    }

    /** Tell {@code viewer} the name they gave is longer than the configured {@code max} characters. */
    public void nameTooLong(PlayerRef viewer, int max) {
        send(viewer, VaultsMessageKey.VAULT_NAME_TOO_LONG, Map.of("max", Integer.toString(max)));
    }

    /** Tell {@code viewer} the material they named for an icon is not a known Bukkit material. */
    public void unknownMaterial(PlayerRef viewer, String material) {
        send(viewer, VaultsMessageKey.VAULT_UNKNOWN_MATERIAL, Map.of("material", material));
    }

    /** Tell {@code viewer} custom vault icons are turned off by the operator. */
    public void iconNotAllowed(PlayerRef viewer) {
        send(viewer, VaultsMessageKey.VAULT_ICON_NOT_ALLOWED, Map.of());
    }

    /** Tell {@code viewer} they must hold an item to use it as a vault icon. */
    public void iconNoHeldItem(PlayerRef viewer) {
        send(viewer, VaultsMessageKey.VAULT_ICON_NO_HELD_ITEM, Map.of());
    }

    /** Tell {@code viewer} they cannot afford the vault fee of {@code cost}. */
    public void cannotAfford(PlayerRef viewer, String cost) {
        send(viewer, VaultsMessageKey.VAULT_CANNOT_AFFORD, Map.of("cost", cost));
    }

    /** Tell {@code actor} the audited player name resolved to nobody. */
    public void unknownTarget(PlayerRef actor, String name) {
        send(actor, VaultsMessageKey.VAULT_ADMIN_UNKNOWN_TARGET, Map.of("player", name));
    }

    /** Tell {@code sender} the command is player-only (a console actor cannot open a GUI). */
    public void playersOnly(PlayerRef sender) {
        send(sender, VaultsMessageKey.VAULT_PLAYERS_ONLY, Map.of());
    }

    private void send(PlayerRef viewer, MessageKey key, Map<String, String> placeholders) {
        notifier.send(viewer, key, placeholders);
    }
}

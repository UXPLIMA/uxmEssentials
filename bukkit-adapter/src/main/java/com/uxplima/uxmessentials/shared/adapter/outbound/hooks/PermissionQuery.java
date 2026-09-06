package com.uxplima.uxmessentials.shared.adapter.outbound.hooks;

import java.util.Objects;
import java.util.UUID;

import org.jspecify.annotations.NullMarked;

/**
 * The permission capability the menu engine reads through the Vault hook: test group membership and a node,
 * grant or revoke a node, and read a primary group. It is a small typed seam over {@code UUID}/{@code String}
 *. The later permission actions ({@code [givepermission]}/{@code [takepermission]}, Phase 2) and group/node
 * requirements (Phase 3) consume it without ever touching a provider SDK.
 *
 * <p>Vault routes permission changes through the underlying provider, so a caller must invoke this capability
 * on the viewer's entity thread (the menu engine's click and action chains already run there). This interface
 * and its {@link #ABSENT} default reference none of {@code net.milkbowl.vault}; the only class that does is the
 * real implementation behind {@link VaultPermissionHook#whenPresent}, so resolving an absent hook loads no SDK
 * class.
 */
@NullMarked
public interface PermissionQuery {

    /** Whether a live Vault permission service is registered and enabled, false for the absent default. */
    boolean available();

    /** Whether {@code player} belongs to {@code group}; false when no permission service is available. */
    boolean inGroup(UUID player, String group);

    /** Whether {@code player} holds {@code node}; false when no permission service is available. */
    boolean has(UUID player, String node);

    /** Grant {@code node} to {@code player}; true on success, false when unavailable or refused. */
    boolean add(UUID player, String node);

    /** Revoke {@code node} from {@code player}; true on success, false when unavailable or refused. */
    boolean remove(UUID player, String node);

    /** {@code player}'s primary group; the empty string when unknown or no service is available. */
    String primaryGroup(UUID player);

    /** The safe no-op used when Vault (or its permission service) is absent: never available, every op a no-op. */
    PermissionQuery ABSENT = new PermissionQuery() {
        @Override
        public boolean available() {
            return false;
        }

        @Override
        public boolean inGroup(UUID player, String group) {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(group, "group");
            return false;
        }

        @Override
        public boolean has(UUID player, String node) {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(node, "node");
            return false;
        }

        @Override
        public boolean add(UUID player, String node) {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(node, "node");
            return false;
        }

        @Override
        public boolean remove(UUID player, String node) {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(node, "node");
            return false;
        }

        @Override
        public String primaryGroup(UUID player) {
            Objects.requireNonNull(player, "player");
            return "";
        }
    };
}

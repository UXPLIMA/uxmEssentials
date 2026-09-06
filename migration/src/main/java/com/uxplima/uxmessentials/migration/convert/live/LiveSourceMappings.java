package com.uxplima.uxmessentials.migration.convert.live;

import java.util.List;

import com.uxplima.uxmessentials.migration.MappingRow;
import org.jspecify.annotations.NullMarked;

/**
 * The {@code SupportedMappings} rows for the built live sources (docs/12-migration §5). Each live source
 * migrates a single surface, a balance into the {@code Wallet} aggregate, so its table is one row. The
 * mapper name is the bukkit-side feed that performs the read, and the conflict unit is the player uuid,
 * matching how the economy writer keys a wallet. The drift guard ties each row to the registered source.
 */
@NullMarked
public final class LiveSourceMappings {

    private LiveSourceMappings() {}

    /** The Vault source's mapping rows: its balance maps to a {@code Wallet}, keyed by uuid. */
    public static List<MappingRow> vaultRows() {
        return List.of(new MappingRow("balance", "Wallet", "economy", "VaultBalanceFeed", "uuid"));
    }

    /** The PlayerPoints source's mapping rows: its points balance maps to a {@code Wallet}, keyed by uuid. */
    public static List<MappingRow> playerPointsRows() {
        return List.of(new MappingRow("points", "Wallet", "economy", "PlayerPointsBalanceFeed", "uuid"));
    }
}

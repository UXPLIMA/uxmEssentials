package com.uxplima.uxmessentials.migration.convert.live;

import java.util.stream.Stream;

import com.uxplima.uxmessentials.migration.convert.map.ImportedUser;
import org.jspecify.annotations.NullMarked;

/**
 * The platform-neutral seam a live import source reads balances through. A live source has no on-disk
 * data tree to walk; it reads from a running economy provider instead, and this seam is the only thing
 * it depends on. The bukkit adapter supplies the real read. Pulling balances out of Vault or
 * PlayerPoints, while the migration module stays free of any provider SDK.
 *
 * <p>{@link #available()} is a cheap presence probe answering "is this provider here to read from?" so
 * a source's {@code detect} can resolve without touching every account. {@link #users()} yields
 * balance-only {@link ImportedUser}s, the owner and the figure to seed a wallet with, with no homes or
 * mail, which the writer pairs with the default currency and the run's balance policy exactly as it
 * does for any other source.
 */
@NullMarked
public interface BalanceFeed {

    /** A cheap probe: true when the backing economy provider is present and can be read. */
    boolean available();

    /** The balance-only users this provider exposes, evaluated lazily as the stream is drained. */
    Stream<ImportedUser> users();
}

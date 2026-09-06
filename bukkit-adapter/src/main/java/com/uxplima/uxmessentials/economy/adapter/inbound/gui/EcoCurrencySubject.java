package com.uxplima.uxmessentials.economy.adapter.inbound.gui;

import com.uxplima.uxmessentials.economy.domain.Currency;
import org.jspecify.annotations.NullMarked;

/**
 * The shape both eco-admin menu subjects share: the currency the screen's actions apply to. The manage and bulk
 * screens are wired against one {@code MenuBindings}, so the shared {@code eco_currency} placeholder, which fills
 * every button's active-currency line. Is registered once and reads the active currency through this interface
 * rather than knowing which of the two subjects it was handed.
 */
@NullMarked
public sealed interface EcoCurrencySubject permits EconomyTargetMenu.TargetSubject, EconomyBulkMenu.BulkSubject {

    /** The currency the screen's Give / Take / Set / Reset (or bulk give-all / reset-all) apply to. */
    Currency active();
}

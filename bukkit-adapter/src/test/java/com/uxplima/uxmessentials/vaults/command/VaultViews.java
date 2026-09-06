package com.uxplima.uxmessentials.vaults.command;

import org.bukkit.Material;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.GuiLayout;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.binding.MenuBindings;
import com.uxplima.uxmessentials.shared.application.module.KernelPorts;
import com.uxplima.uxmessentials.shared.menu.TestMenuEngine;
import com.uxplima.uxmessentials.vaults.adapter.inbound.gui.VaultSelectorMenu;
import com.uxplima.uxmessentials.vaults.adapter.inbound.gui.VaultSelectorMenu.VaultSelectorSettings;
import com.uxplima.uxmessentials.vaults.adapter.inbound.gui.VaultView;
import com.uxplima.uxmessentials.vaults.application.ListVaults;
import com.uxplima.uxmessentials.vaults.application.OpenVault;
import com.uxplima.uxmessentials.vaults.application.SaveVault;
import com.uxplima.uxmessentials.vaults.application.VaultAmountQuota;
import com.uxplima.uxmessentials.vaults.application.VaultChargeSettings;
import com.uxplima.uxmessentials.vaults.application.VaultNotifier;
import com.uxplima.uxmessentials.vaults.domain.VaultItemPolicy;

/**
 * Shared test fixtures for the vaults command/GUI tests: a {@link VaultView} and {@link VaultSelectorMenu}
 * wired off a {@link KernelPorts} double the same way {@code VaultsWiring} wires them, so each test's
 * {@code services()} helper stays a single call rather than repeating the selector plumbing. The selector
 * settings default to a three-row picker showing locked indices, mirroring the shipped config. The selector is
 * built over a real (but listener-less) engine through {@link TestMenuEngine}; the command tests it feeds either
 * never open it or only check the window opens, while the dedicated golden and off-thread tests open and click it
 * through engines of their own.
 */
final class VaultViews {

    private VaultViews() {}

    /** A {@link VaultView} with the given blacklist policy, wired off the kernel doubles, no open sound. */
    static VaultView view(KernelPorts kernel, SaveVault saveVault, VaultItemPolicy policy) {
        return new VaultView(
                kernel.messages(),
                kernel.messageSink(),
                saveVault,
                kernel.scheduler(),
                kernel.permissions(),
                policy,
                null);
    }

    /** A {@link VaultView} that blocks nothing: the default for tests not exercising the blacklist. */
    static VaultView view(KernelPorts kernel, SaveVault saveVault) {
        return view(kernel, saveVault, VaultItemPolicy.allowAll());
    }

    /** The default three-row picker settings: a CHEST owned icon, a grey-pane locked icon, locked shown. */
    static VaultSelectorSettings selectorSettings() {
        GuiLayout layout = new GuiLayout(3, Material.CHEST, Material.ARROW, 21, 23, java.util.List.of());
        return new VaultSelectorSettings(layout, Material.CHEST, Material.GRAY_STAINED_GLASS_PANE, true);
    }

    /** A {@link VaultSelectorMenu} wired off the kernel doubles and the given collaborators, over a fresh engine. */
    static VaultSelectorMenu selector(
            KernelPorts kernel,
            ListVaults listVaults,
            VaultAmountQuota amountQuota,
            OpenVault openVault,
            VaultView view,
            VaultNotifier notifier,
            VaultChargeSettings chargeSettings) {
        TestMenuEngine engine = TestMenuEngine.create(kernel.messages(), kernel.scheduler());
        MenuBindings bindings = engine.bindings();
        VaultSelectorMenu menu = new VaultSelectorMenu(
                engine.menus(),
                kernel.messages(),
                kernel.messageSink(),
                kernel.scheduler(),
                listVaults,
                amountQuota,
                openVault,
                view,
                notifier,
                chargeSettings,
                selectorSettings());
        // Register the bindings and the bundled spec so menus.open resolves; a non-existent disk path makes the
        // loader fall through to the classpath resource. The command tests that open the picker get a working
        // engine; those that only fill VaultServices never open it, so this is harmless to them.
        menu.register(bindings, java.nio.file.Path.of("nonexistent-menu-data"), new SilentLogger());
        return menu;
    }

    /** Swallows the loader's diagnostics; the bundled spec loads cleanly in tests, so nothing is expected. */
    private static final class SilentLogger implements com.uxplima.uxmessentials.shared.application.port.Logger {
        @Override
        public void info(String message, Object... args) {}

        @Override
        public void warn(String message, Object... args) {}

        @Override
        public void error(String message, Throwable cause) {}

        @Override
        public void debug(String message, Object... args) {}
    }
}

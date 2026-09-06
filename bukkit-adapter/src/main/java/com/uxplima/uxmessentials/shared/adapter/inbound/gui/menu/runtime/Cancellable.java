package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime;

/**
 * A handle to something that can be torn down once: the menu's refresh task in practice. Kept deliberately
 * minimal so the runtime never names a scheduler type: the adapter's repeating task is wrapped as one of these,
 * and the holder cancels it when the menu closes without caring how the task was created.
 */
@FunctionalInterface
public interface Cancellable {

    void cancel();
}

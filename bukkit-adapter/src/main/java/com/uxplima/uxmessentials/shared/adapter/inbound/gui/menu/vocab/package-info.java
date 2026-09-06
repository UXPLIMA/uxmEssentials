/**
 * The generic, feature-agnostic vocabulary the data-driven menu engine ships with: the {@code close} / {@code open}
 * / {@code command} / {@code message} / {@code sound} actions every menu, ours and an operator's, can name without
 * a feature having to register them. Wired once at startup into the shared {@code MenuBindings}, so a spec loaded
 * from disk resolves these ids the same way a code-registered feature menu does. Console-side actions are gated
 * separately and live alongside this; nothing here runs as the console.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.vocab;

/**
 * Application layer of the custommenus bounded context: the {@code CustomMenusMessageKey} catalog handles for the
 * {@code /menu} command. The context is otherwise adapter-thin. It consumes the Phase-1 menu engine
 * ({@code Menus} / {@code MenuBindings}) rather than owning a domain aggregate, so its only {@code :core} surface
 * is this message-key block, kept here (like every other context's keys) so {@code MessageKeyCatalog} and the
 * locale-parity guard see the full key set whether or not the module is enabled.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.custommenus.application;

/**
 * The menu engine's public dev-API events: the cancellable Bukkit events another plugin can listen to and veto.
 *
 * <p>{@link com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.api.event.MenuOpenEvent} fires at the single
 * open choke-point every menu open funnels through, before either a chest or a native Bedrock form is shown;
 * cancelling it stops the open. {@link
 * com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.api.event.MenuClickEvent} fires at the click choke-point
 * before a button's conditions, requirements and actions run; cancelling it skips that click-handling while the
 * vanilla inventory click stays cancelled either way, so the item never moves. Both are ordinary Bukkit events with
 * the required static {@code getHandlerList()}, and both fire on the viewer's own region thread: a listener must
 * respect Folia threading and schedule any world work onto the owning region rather than acting inline.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.api.event;

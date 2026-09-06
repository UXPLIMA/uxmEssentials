/**
 * The economy context's inbound Brigadier commands: {@code /balance}, {@code /pay}, {@code /payconfirm},
 * {@code /paytoggle}, {@code /baltop}, and the {@code /eco} admin surface. Each is a thin adapter over the
 * economy use cases and the {@code EconomyProvider} port, none reaches into {@code economy.domain.*}, and
 * every provider call runs off the tick thread through the injected {@code Scheduler} so a (possibly foreign)
 * provider can never wedge the command. The optional {@code [currency]} argument is resolved against the
 * closed registry at this boundary; an unknown id is an error listing the valid ids, never a silent default.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.economy.adapter.inbound.command;

/**
 * The playerstate context's inbound Brigadier commands: {@code /god /fly /heal /feed /gamemode} (with the
 * {@code /gmc /gms /gma /gmsp} fixed-mode shortcuts), {@code /speed /walkspeed /flyspeed}, and the
 * {@code /ext /suicide /near /nightvision /ptime /pweather} verbs. Each is a thin adapter that maps its
 * arguments. Resolving the optional {@code .others} target through the shared
 * {@code uxmessentials.playerstate.others} gate. To a single use-case call; success feedback flows through
 * the use cases' {@code MessageSink}. {@code /repair}, {@code /repairall}, {@code /hat}, and {@code /more}
 * belong to the itemworld context and are deliberately absent.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.playerstate.adapter.inbound.command;

/**
 * The poses context's bukkit-adapter wiring: {@link com.uxplima.uxmessentials.poses.adapter.PosesWiring} constructs
 * the sit use cases over the kernel ports and the context's own seat port, session registry, and region gate, and
 * produces the {@code /sit} command and the interact / cancel / cleanup listeners the plugin registers. This is the
 * one place the poses context is wired: nothing else news up its classes.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.poses.adapter;

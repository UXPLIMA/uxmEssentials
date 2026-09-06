/**
 * The command-control context's bukkit-adapter wiring:
 * {@link com.uxplima.uxmessentials.commandcontrol.adapter.CommandControlWiring} derives the pure
 * {@link com.uxplima.uxmessentials.commandcontrol.domain.RuleSet} from the module's config, chooses the group source,
 * and produces the {@code PlayerCommandPreprocessEvent} gate the plugin registers. This is the one place the context
 * is wired: nothing else news up its classes.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.commandcontrol.adapter;

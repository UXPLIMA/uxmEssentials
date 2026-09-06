/**
 * The itemworld bounded context's domain, pure Java, no Bukkit/Paper/Adventure/SLF4J imports.
 *
 * <p>The itemworld surface is mostly stateless, ACL-thin mutations of live items, entities and world
 * state (docs/10-feature-modules.md §15.10), so the domain is deliberately small: it owns the value
 * objects and validation rules that a command's inputs must satisfy <em>before</em> a domain call
 * {@link com.uxplima.uxmessentials.itemworld.domain.ItemQuery item id},
 * {@link com.uxplima.uxmessentials.itemworld.domain.AmountSpec amounts with caps},
 * {@link com.uxplima.uxmessentials.itemworld.domain.EnchantSpec enchant level clamps},
 * {@link com.uxplima.uxmessentials.itemworld.domain.TimeSpec time} and
 * {@link com.uxplima.uxmessentials.itemworld.domain.WeatherSpec weather} enums,
 * {@link com.uxplima.uxmessentials.itemworld.domain.MobSpec mob spawn requests}, the
 * {@link com.uxplima.uxmessentials.itemworld.domain.PowertoolBinding powertool bind model}, and the
 * {@link com.uxplima.uxmessentials.itemworld.domain.PurgeSelection entity-purge selection}.
 *
 * <p>It also models the {@link com.uxplima.uxmessentials.itemworld.domain.SubFeatureGroup sub-feature
 * groups} the module splits into, each independently disableable. Concrete materialisation of items,
 * entities and world handles stays in the adapter; the domain reasons only over validated values, so a
 * cap or a clamp is unit-testable entirely in {@code :core}.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.itemworld.domain;

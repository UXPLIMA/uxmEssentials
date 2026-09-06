/**
 * The itemworld context's bukkit-side adapter root. {@link com.uxplima.uxmessentials.itemworld.adapter.ItemworldServices}
 * bundles the kernel ports, the abusable-verb audit logger, and the live {@code itemworld.conf} view the
 * inbound commands resolve their disable gate and caps through. The context is stateless and ACL-thin: there
 * is no persistence and no use-case object graph. The validating value objects live in {@code :core} and the
 * live item/inventory mutation runs at the command boundary on the kernel {@code Scheduler}.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.itemworld.adapter;

/**
 * The kits context's domain events: the sealed {@code KitEvent} family and its record implementation
 * {@code KitClaimed}. The event records something that already happened. A player claimed a kit and its
 * items were granted; the adapter bridges it to a Bukkit event so other plugins observe kit claims without
 * importing this package, and the audit log keys its {@code kit_give} line off it.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.kits.domain.event;

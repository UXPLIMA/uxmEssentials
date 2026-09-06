/**
 * The shared, pure foundation the scoreboard, tablist, and nametags modules build on: a {@link
 * com.uxplima.uxmessentials.shared.display.DisplayCondition} model for "show this element only when …", its
 * runtime {@link com.uxplima.uxmessentials.shared.display.ConditionContext} seam, the tolerant {@link
 * com.uxplima.uxmessentials.shared.display.ConditionParser} that reads operator condition strings, and the
 * {@link com.uxplima.uxmessentials.shared.display.AnimationSpec} that models a named animation.
 *
 * <p>Everything here is String/function based and free of Bukkit, Paper, Adventure, and rendering libraries, so
 * it lives in {@code :core}. The condition follows vote's spec/context split. The {@code DisplayCondition} is a
 * pure spec and the {@code ConditionContext} supplies runtime values through predicates and functions. Two
 * deliberate choices: an unparseable condition resolves to {@link
 * com.uxplima.uxmessentials.shared.display.DisplayCondition#never()} (a broken filter hides its element rather
 * than always showing it), and the {@code SCROLL}/{@code GRADIENT} animation kinds are adapter-bound: this
 * package models their declaration while the bukkit adapter binds uxmLib's text-animation utilities to render
 * them.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.shared.display;

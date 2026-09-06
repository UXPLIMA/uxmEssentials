/**
 * Converters that turn a competitor menu plugin's config into a uxmEssentials HOCON menu spec, so an operator
 * migrating off that plugin keeps their menus. Three are shipped so far, each a pure Configurate-only
 * {@code *Converter} doing the config-to-config mapping paired with a file-facing {@code *ConvertService} one-shot:
 * {@link com.uxplima.uxmessentials.custommenus.adapter.convert.DeluxeMenusConverter} /
 * {@link com.uxplima.uxmessentials.custommenus.adapter.convert.DeluxeMenusConvertService} behind
 * {@code /menu convert deluxemenus <path>},
 * {@link com.uxplima.uxmessentials.custommenus.adapter.convert.ZMenuConverter} /
 * {@link com.uxplima.uxmessentials.custommenus.adapter.convert.ZMenuConvertService} behind
 * {@code /menu convert zmenu <path>}, and
 * {@link com.uxplima.uxmessentials.custommenus.adapter.convert.OguiConverter} /
 * {@link com.uxplima.uxmessentials.custommenus.adapter.convert.OguiConvertService} behind
 * {@code /menu convert ogui <path>}. The services share their path resolution and directory walk through the
 * package-private {@link com.uxplima.uxmessentials.custommenus.adapter.convert.MenuConvertFiles}. Everything here is
 * Configurate-only and touches no engine internals. A converted spec re-enters the engine through the ordinary
 * {@code menus/} loader on the next reload.
 */
@org.jspecify.annotations.NullMarked
package com.uxplima.uxmessentials.custommenus.adapter.convert;

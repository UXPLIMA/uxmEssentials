package com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.api;

import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;

import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.providers.IconProvider;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuActionContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.runtime.MenuContext;
import com.uxplima.uxmessentials.shared.adapter.inbound.gui.menu.spec.MenuItemSpec;
import org.jspecify.annotations.NullMarked;

/**
 * The public registration surface another plugin uses to teach the menu engine new vocabulary. A plugin obtains it
 * from the Bukkit {@link org.bukkit.plugin.ServicesManager}:
 *
 * <pre>{@code
 * MenuApi menus = getServer().getServicesManager().load(MenuApi.class);
 * menus.registerAction("my-award", ctx -> ctx.player().giveExp(100));
 * }</pre>
 *
 * <p>Once registered, an id becomes usable in any menu spec: {@code registerAction} adds an action a
 * {@code click { left { click = ["my-award"] } }} or an open-action can fire. A custom "button" is simply a menu
 * item whose click runs a registered action, so this one method covers buttons; {@code registerRequirement} adds a
 * {@code view}/{@code click} condition written {@code my-cond:<value>}; {@code registerPlaceholder} adds a
 * {@code %my-token%} the renderer expands; and {@code registerListSource} adds a {@code list { source = my-src }}.
 * {@link #buildItem} renders a menu item spec to an {@link ItemStack} through the very same material, placeholder,
 * name/lore and decor pipeline a menu icon uses.
 *
 * <h2>Register once, on your enable</h2>
 * Each id is registered exactly once: a duplicate id throws {@link IllegalStateException} so a wiring mistake fails
 * loudly rather than letting a second handler silently win. Register on your own plugin's enable. Registration is
 * thread-safe against an already-running engine, so a plugin that enables after this host is still seen.
 *
 * <h2>Load or reload the menu after registering</h2>
 * A menu spec that names a custom id is validated when it loads: an unknown id is a loud load-time failure, not a
 * broken menu a player meets. Register your ids <em>before</em> the menu that uses them loads. A menu already on
 * disk when your plugin enables must therefore be re-validated afterwards. Run {@code /uxmess reload} or
 * {@code /menu reload} once your registrations are in place.
 *
 * <h2>Folia threading</h2>
 * Every registered handler runs on the <em>viewer's own region thread</em>. The thread the engine renders or
 * clicks the menu on, which on Folia is that entity's region rather than a single main thread. A handler may read
 * its context and act on the viewing player inline, but any world or cross-entity work it triggers must be
 * scheduled onto the owning region, never run inline against another region's state.
 *
 * @deprecated Superseded by {@link com.uxplima.uxmessentials.api.bukkit.menu.MenuApi}, which is published as a Maven
 *     artifact and names only Bukkit and JDK types. This interface hands out the engine's own runtime contexts and
 *     spec model, so every internal change to them is a breaking change for whoever compiled against it. Both
 *     surfaces write into the same registries, so a migration can be done one registration at a time: load
 *     {@code UxmEssentialsApi} instead of this service and call {@code api.menus()}.
 */
@Deprecated(forRemoval = true, since = "0.6.0")
@NullMarked
public interface MenuApi {

    /**
     * Register a custom action under {@code id}. A menu spec fires it by naming the id in a click gesture's action
     * list ({@code left { click = ["my-award"] } }) or in a menu's open/close actions; an {@code id:value} form
     * arrives on the handler as {@link MenuActionContext#arg()}. This is also how a custom clickable button is added
     *: a button is just a menu item whose click runs a registered action.
     *
     * @throws IllegalStateException if an action is already registered under {@code id}
     */
    void registerAction(String id, Consumer<MenuActionContext> handler);

    /**
     * Register a custom requirement (condition) under {@code id}. A menu spec gates an item's {@code view} or a
     * click on it by naming the id, optionally valued as {@code id:<value>}; the value and any other tokens arrive
     * as the handler's {@code Map<String, String>} argument. The handler returns {@code true} to pass the gate.
     *
     * @throws IllegalStateException if a requirement is already registered under {@code id}
     */
    void registerRequirement(String id, BiPredicate<MenuContext, Map<String, String>> handler);

    /**
     * Register a custom placeholder under {@code id}. The renderer expands {@code %id%} in a menu title, item name
     * or lore line to the string the handler returns for the current {@link MenuContext} (the viewer, page, list
     * entry and open arguments). Return an empty string to render nothing.
     *
     * @throws IllegalStateException if a placeholder is already registered under {@code id}
     */
    void registerPlaceholder(String id, Function<MenuContext, String> handler);

    /**
     * Register a custom list source under {@code id}. A menu spec pages over it with {@code list { source = id }},
     * and the engine renders one templated item per element the handler returns for the current {@link MenuContext}.
     * The handler is called off the tick thread, so it must not touch the Bukkit API without hopping to the owning
     * region first.
     *
     * @throws IllegalStateException if a list source is already registered under {@code id}
     */
    void registerListSource(String id, Function<MenuContext, List<?>> handler);

    /**
     * Register a custom icon source. The provider claims its own material-spec prefix (say {@code myplugin:<id>})
     * and returns the base {@link ItemStack} for a spec it owns, or {@link java.util.Optional#empty()} for one it
     * does not, so a menu item written {@code material = "myplugin:foo"} renders through it. It is consulted
     * <em>after</em> the built-in providers (skull, equipment, HeadDatabase, the item-plugin integrations), so it
     * only adds new prefixes and can never shadow a built-in.
     *
     * <p>Register on your own plugin's enable, the same register-on-enable contract the other {@code register*}
     * methods carry; a provider registered after this host has enabled is still seen, on the next render. The
     * provider's {@link IconProvider#icon} is invoked on the viewer's own region thread (the render thread on
     * Folia), so it must read only the viewer's state and never block.
     */
    void registerIconProvider(IconProvider provider);

    /**
     * Render {@code spec} to the {@link ItemStack} a viewer would see, resolving its material spec through the icon
     * providers, expanding every {@code %placeholder%}, and layering the name, lore and decor, exactly as a menu
     * icon is built. Handy for a plugin that wants a menu-styled item outside a menu (a hotbar item, a hand-built
     * inventory) without re-implementing the pipeline.
     */
    ItemStack buildItem(MenuItemSpec spec, MenuContext ctx);
}

package com.uxplima.uxmessentials.holograms.adapter.outbound;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;

import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;

import com.uxplima.uxmlib.hologram.Hologram;
import com.uxplima.uxmlib.hologram.ModelHologram;
import org.junit.jupiter.api.Test;

/**
 * Pins {@link RenderedHologram#textEntityId()}. The entity id the renderer targets a per-viewer text-override
 * packet at. A text hologram exposes its backing {@code TextDisplay}'s id; an item or block hologram carries no
 * overridable text component and reports {@link RenderedHologram#NO_ENTITY}, so the renderer never sends it an
 * override.
 */
class RenderedHologramEntityIdTest {

    @Test
    void aTextHologramExposesItsBackingDisplayId() {
        TextDisplay display = (TextDisplay) Proxy.newProxyInstance(
                TextDisplay.class.getClassLoader(), new Class<?>[] {TextDisplay.class}, (proxy, method, args) -> {
                    if ("getEntityId".equals(method.getName())) {
                        return 1234;
                    }
                    return null;
                });
        Hologram text = (Hologram) Proxy.newProxyInstance(
                Hologram.class.getClassLoader(), new Class<?>[] {Hologram.class}, (proxy, method, args) -> {
                    if ("entity".equals(method.getName())) {
                        return display;
                    }
                    return null;
                });

        assertThat(RenderedHologram.ofText(text).textEntityId()).isEqualTo(1234);
    }

    @Test
    void anItemOrBlockHologramHasNoOverridableTextEntity() {
        ModelHologram model = (ModelHologram) Proxy.newProxyInstance(
                ModelHologram.class.getClassLoader(), new Class<?>[] {ModelHologram.class}, (proxy, method, args) -> {
                    if ("entity".equals(method.getName())) {
                        return (Display) null;
                    }
                    return null;
                });

        assertThat(RenderedHologram.ofModel(model).textEntityId()).isEqualTo(RenderedHologram.NO_ENTITY);
    }
}

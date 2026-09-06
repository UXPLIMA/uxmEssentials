package com.uxplima.uxmessentials.communication.adapter.inbound.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

import com.uxplima.uxmessentials.communication.domain.DeathCause;
import org.junit.jupiter.api.Test;

/**
 * The Bukkit-to-domain death mapping in {@link DeathCauses}, driven through Mockito doubles so the classification
 * runs without a live server. It pins the "who before how" rule the death policy relies on. A player killer is
 * {@link DeathCause#PVP}, a creature blow is {@link DeathCause#MOB}, a fired projectile is {@link
 * DeathCause#PROJECTILE}, an environmental cause maps from its {@code DamageCause}, and a missing damage event is
 * {@link DeathCause#OTHER}, plus the readable-material fallback the {@code {killer_weapon}} token uses for a
 * plain (un-renamed) item.
 */
class DeathCausesTest {

    @Test
    void aPlayerKillerIsClassifiedAsPvp() {
        Player killer = mock(Player.class);

        // A player killer wins even when the underlying damage was, say, a projectile they fired.
        assertThat(DeathCauses.classify(killer, mock(EntityDamageByEntityEvent.class)))
                .isEqualTo(DeathCause.PVP);
        assertThat(DeathCauses.classify(killer, null)).isEqualTo(DeathCause.PVP);
    }

    @Test
    void aCreatureBlowIsClassifiedAsMob() {
        EntityDamageByEntityEvent byEntity = mock(EntityDamageByEntityEvent.class);
        when(byEntity.getDamager()).thenReturn(mock(Zombie.class));

        assertThat(DeathCauses.classify(null, byEntity)).isEqualTo(DeathCause.MOB);
    }

    @Test
    void aFiredProjectileIsClassifiedAsProjectile() {
        EntityDamageByEntityEvent byEntity = mock(EntityDamageByEntityEvent.class);
        when(byEntity.getDamager()).thenReturn(mock(Arrow.class));

        assertThat(DeathCauses.classify(null, byEntity)).isEqualTo(DeathCause.PROJECTILE);
    }

    @Test
    void anEnvironmentalCauseMapsFromItsDamageCause() {
        assertThat(DeathCauses.classify(null, damage(DamageCause.FALL))).isEqualTo(DeathCause.FALL);
        assertThat(DeathCauses.classify(null, damage(DamageCause.LAVA))).isEqualTo(DeathCause.LAVA);
        assertThat(DeathCauses.classify(null, damage(DamageCause.DROWNING))).isEqualTo(DeathCause.DROWNING);
        assertThat(DeathCauses.classify(null, damage(DamageCause.BLOCK_EXPLOSION)))
                .isEqualTo(DeathCause.EXPLOSION);
    }

    @Test
    void aMissingDamageEventIsClassifiedAsOther() {
        assertThat(DeathCauses.classify(null, null)).isEqualTo(DeathCause.OTHER);
        assertThat(DeathCauses.classify(null, damage(DamageCause.CUSTOM))).isEqualTo(DeathCause.OTHER);
    }

    @Test
    void readableMaterialTitleCasesTheKey() {
        assertThat(DeathCauses.readableMaterial(Material.DIAMOND_SWORD)).isEqualTo("Diamond Sword");
        assertThat(DeathCauses.readableMaterial(Material.BOW)).isEqualTo("Bow");
        assertThat(DeathCauses.readableMaterial(Material.NETHERITE_AXE)).isEqualTo("Netherite Axe");
    }

    @Test
    void aNullWeaponYieldsTheEmptyString() {
        assertThat(DeathCauses.weaponName(null)).isEmpty();
    }

    private static EntityDamageEvent damage(DamageCause cause) {
        EntityDamageEvent event = mock(EntityDamageEvent.class);
        when(event.getCause()).thenReturn(cause);
        return event;
    }
}

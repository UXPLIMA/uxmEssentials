package com.uxplima.uxmessentials.staff.adapter.outbound;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.uxplima.uxmessentials.shared.adapter.outbound.PayloadLimits;
import com.uxplima.uxmessentials.staff.domain.LoadoutBlob;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The anti-corruption codec between a player's live {@link PotionEffect}s and the opaque {@link LoadoutBlob}
 * the staff loadout stores them as: the potion-effect sibling of {@code VaultItemCodec}. Staff mode does not
 * touch a player's potions directly; it captures them here so they ride back onto the player on exit exactly as
 * they were, since the gadget hotbar swap leaves the live effects untouched but a restore from a different
 * session must be able to put them back.
 *
 * <p>Each effect is written as its type's namespaced key plus the scalar fields ({@code duration},
 * {@code amplifier}, {@code ambient}, {@code particles}, {@code icon}) so the round-trip is lossless. An effect
 * whose type no longer resolves on decode (a removed effect after an upgrade) is skipped rather than failing
 * the whole restore.
 */
@NullMarked
final class StaffEffectCodec {

    private StaffEffectCodec() {}

    /** Serialize {@code effects} (a player's active potions) into the opaque {@link LoadoutBlob} payload. */
    static LoadoutBlob encode(Collection<PotionEffect> effects) {
        Objects.requireNonNull(effects, "effects");
        if (effects.isEmpty()) {
            return LoadoutBlob.empty();
        }
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(effects.size());
            for (PotionEffect effect : effects) {
                writeEffect(out, effect);
            }
            return LoadoutBlob.of(bytes.toByteArray());
        } catch (IOException io) {
            throw new UncheckedIOException("staff loadout potion effects could not be serialized", io);
        }
    }

    /** Deserialize {@code blob} back into the list of potion effects, dropping any whose type no longer exists. */
    static List<PotionEffect> decode(LoadoutBlob blob) {
        Objects.requireNonNull(blob, "blob");
        if (blob.isEmpty()) {
            return List.of();
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(blob.bytes()))) {
            // Clamped before it sizes anything: the count comes off the stored blob, not from us.
            int count = PayloadLimits.entries(in.readInt());
            List<PotionEffect> effects = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                PotionEffect effect = readEffect(in);
                if (effect != null) {
                    effects.add(effect);
                }
            }
            return List.copyOf(effects);
        } catch (IOException io) {
            throw new UncheckedIOException("staff loadout potion effects could not be deserialized", io);
        }
    }

    private static void writeEffect(DataOutputStream out, PotionEffect effect) throws IOException {
        out.writeUTF(effect.getType().getKey().toString());
        out.writeInt(effect.getDuration());
        out.writeInt(effect.getAmplifier());
        out.writeBoolean(effect.isAmbient());
        out.writeBoolean(effect.hasParticles());
        out.writeBoolean(effect.hasIcon());
    }

    private static @Nullable PotionEffect readEffect(DataInputStream in) throws IOException {
        String key = in.readUTF();
        int duration = in.readInt();
        int amplifier = in.readInt();
        boolean ambient = in.readBoolean();
        boolean particles = in.readBoolean();
        boolean icon = in.readBoolean();
        NamespacedKey namespaced = NamespacedKey.fromString(key);
        if (namespaced == null) {
            return null;
        }
        PotionEffectType type = Registry.EFFECT.get(namespaced);
        if (type == null) {
            return null;
        }
        return new PotionEffect(type, duration, amplifier, ambient, particles, icon);
    }
}

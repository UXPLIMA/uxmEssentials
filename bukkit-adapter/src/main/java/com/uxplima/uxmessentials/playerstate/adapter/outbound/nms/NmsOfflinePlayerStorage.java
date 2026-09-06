package com.uxplima.uxmessentials.playerstate.adapter.outbound.nms;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;

import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;

import com.uxplima.uxmessentials.playerstate.adapter.outbound.OfflineInventory;
import com.uxplima.uxmessentials.playerstate.adapter.outbound.OfflinePlayerStorage;
import com.uxplima.uxmessentials.shared.application.port.Logger;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The {@link OfflinePlayerStorage} implementation, and the plugin's one deliberate piece of NMS. It reads and
 * writes an offline player's {@code playerdata/<uuid>.dat} the same way the server itself does, reusing the very
 * codecs {@link net.minecraft.world.entity.player.Player} and {@link net.minecraft.world.entity.LivingEntity}
 * persist with, so the on-disk shape stays exactly correct across the data-component model:
 *
 * <ul>
 *   <li>main inventory (slots 0..35), the {@code "Inventory"} list of {@link ItemStackWithSlot#CODEC};
 *   <li>armour and offhand (slots 36..40). The {@code "equipment"} {@link EntityEquipment#CODEC} map, keyed by
 *       {@link EquipmentSlot}; the server omits the key entirely when nothing is worn, so this does too;
 *   <li>ender chest (slots 0..26): the {@code "EnderItems"} list of {@link ItemStackWithSlot#CODEC}.
 * </ul>
 *
 * <p>This class is deliberately not {@code @NullMarked}: the NMS surface it touches is unannotated, so it stays
 * outside the project's null-analysis scope. It is reached only through the {@link OfflinePlayerStorage} port,
 * which is null-marked, so the seam the rest of the context sees is fully checked.
 */
public final class NmsOfflinePlayerStorage implements OfflinePlayerStorage {

    private static final String INVENTORY_KEY = "Inventory";
    private static final String EQUIPMENT_KEY = "equipment";
    private static final String ENDER_KEY = "EnderItems";
    private static final int MAIN_SLOTS = 36;

    private final Logger log;

    public NmsOfflinePlayerStorage(Logger log) {
        this.log = Objects.requireNonNull(log, "log");
    }

    @Override
    public boolean hasData(UUID uuid) {
        return Files.isRegularFile(datFile(uuid));
    }

    @Override
    public Optional<OfflineInventory> load(UUID uuid) {
        Path file = datFile(uuid);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            ValueInput in = TagValueInput.create(ProblemReporter.DISCARDING, registries(), root);
            ItemStack[] slots = new ItemStack[OfflineInventory.SLOT_COUNT];
            readMain(in, slots);
            readEquipment(in, slots);
            ItemStack[] ender = new ItemStack[OfflineInventory.ENDER_SIZE];
            readEnder(in, ender);
            return Optional.of(new OfflineInventory(slots, ender));
        } catch (IOException ex) {
            log.error("Failed to read offline player data for " + uuid, ex);
            return Optional.empty();
        }
    }

    @Override
    public void saveInventory(UUID uuid, ItemStack[] slots) {
        mutate(uuid, (root, registries) -> {
            TagValueOutput out = TagValueOutput.createWrappingWithContext(ProblemReporter.DISCARDING, registries, root);
            ValueOutput.TypedOutputList<ItemStackWithSlot> list = out.list(INVENTORY_KEY, ItemStackWithSlot.CODEC);
            for (int slot = 0; slot < MAIN_SLOTS && slot < slots.length; slot++) {
                net.minecraft.world.item.ItemStack nms = CraftItemStack.asNMSCopy(slots[slot]);
                if (!nms.isEmpty()) {
                    list.add(new ItemStackWithSlot(slot, nms));
                }
            }
            writeEquipment(out, root, slots);
        });
    }

    @Override
    public void saveEnderChest(UUID uuid, ItemStack[] ender) {
        mutate(uuid, (root, registries) -> {
            TagValueOutput out = TagValueOutput.createWrappingWithContext(ProblemReporter.DISCARDING, registries, root);
            ValueOutput.TypedOutputList<ItemStackWithSlot> list = out.list(ENDER_KEY, ItemStackWithSlot.CODEC);
            for (int slot = 0; slot < ender.length && slot < OfflineInventory.ENDER_SIZE; slot++) {
                net.minecraft.world.item.ItemStack nms = CraftItemStack.asNMSCopy(ender[slot]);
                if (!nms.isEmpty()) {
                    list.add(new ItemStackWithSlot(slot, nms));
                }
            }
        });
    }

    private static void readMain(ValueInput in, ItemStack[] slots) {
        for (ItemStackWithSlot entry : in.listOrEmpty(INVENTORY_KEY, ItemStackWithSlot.CODEC)) {
            if (entry.slot() >= 0 && entry.slot() < MAIN_SLOTS) {
                slots[entry.slot()] = CraftItemStack.asBukkitCopy(entry.stack());
            }
        }
    }

    private static void readEquipment(ValueInput in, ItemStack[] slots) {
        EntityEquipment eq = in.read(EQUIPMENT_KEY, EntityEquipment.CODEC).orElseGet(EntityEquipment::new);
        slots[36] = bukkitOrNull(eq.get(EquipmentSlot.FEET));
        slots[37] = bukkitOrNull(eq.get(EquipmentSlot.LEGS));
        slots[38] = bukkitOrNull(eq.get(EquipmentSlot.CHEST));
        slots[39] = bukkitOrNull(eq.get(EquipmentSlot.HEAD));
        slots[40] = bukkitOrNull(eq.get(EquipmentSlot.OFFHAND));
    }

    private static void readEnder(ValueInput in, ItemStack[] ender) {
        for (ItemStackWithSlot entry : in.listOrEmpty(ENDER_KEY, ItemStackWithSlot.CODEC)) {
            if (entry.slot() >= 0 && entry.slot() < ender.length) {
                ender[entry.slot()] = CraftItemStack.asBukkitCopy(entry.stack());
            }
        }
    }

    /** Write the armour/offhand block (slots 36..40), dropping the {@code equipment} key when nothing is worn. */
    private static void writeEquipment(TagValueOutput out, CompoundTag root, ItemStack[] slots) {
        EntityEquipment eq = new EntityEquipment();
        putEquip(eq, EquipmentSlot.FEET, slots, 36);
        putEquip(eq, EquipmentSlot.LEGS, slots, 37);
        putEquip(eq, EquipmentSlot.CHEST, slots, 38);
        putEquip(eq, EquipmentSlot.HEAD, slots, 39);
        putEquip(eq, EquipmentSlot.OFFHAND, slots, 40);
        if (eq.isEmpty()) {
            root.remove(EQUIPMENT_KEY);
        } else {
            out.store(EQUIPMENT_KEY, EntityEquipment.CODEC, eq);
        }
    }

    private static void putEquip(EntityEquipment eq, EquipmentSlot slot, ItemStack[] slots, int index) {
        if (index < slots.length) {
            net.minecraft.world.item.ItemStack nms = CraftItemStack.asNMSCopy(slots[index]);
            if (!nms.isEmpty()) {
                eq.set(slot, nms);
            }
        }
    }

    private void mutate(UUID uuid, BiConsumer<CompoundTag, HolderLookup.Provider> edit) {
        Path file = datFile(uuid);
        if (!Files.isRegularFile(file)) {
            return; // no stored data to write onto. The caller gates on hasData, but a logout can race it
        }
        try {
            CompoundTag root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            edit.accept(root, registries());
            NbtIo.writeCompressed(root, file);
        } catch (IOException ex) {
            log.error("Failed to write offline player data for " + uuid, ex);
        }
    }

    private static ItemStack bukkitOrNull(net.minecraft.world.item.ItemStack nms) {
        return nms.isEmpty() ? null : CraftItemStack.asBukkitCopy(nms);
    }

    private static Path datFile(UUID uuid) {
        return server().getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(uuid + ".dat");
    }

    private static HolderLookup.Provider registries() {
        return server().registryAccess();
    }

    private static MinecraftServer server() {
        return ((CraftServer) Bukkit.getServer()).getServer();
    }
}

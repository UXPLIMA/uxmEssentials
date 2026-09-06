package com.uxplima.uxmessentials.villagers.adapter.outbound;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;

import com.uxplima.uxmessentials.shared.adapter.outbound.PayloadLimits;
import org.jspecify.annotations.NullMarked;

/**
 * The one place a villager's custom {@link MerchantRecipe} set is turned into an opaque byte blob and back, so the
 * trade manager can stamp the edited trades into the villager's PDC and reapply them verbatim when the villager
 * loads. A recipe is a template. Its buy ingredients, its sell result, and the counters that govern how often it
 * may be used, so the blob stores, per recipe: the result item, the ingredient items, the max uses, and the
 * experience/villager-experience/price-multiplier trade metadata. Item stacks ride Paper's
 * {@link ItemStack#serializeAsBytes()} / {@link ItemStack#deserializeBytes(byte[])}, which round-trips the full item
 * (components, enchantments, custom name, amount, so a trade's amounts survive).
 *
 * <p>Decoded recipes always start at zero uses (a freshly-loaded villager offers its trades), so only {@code maxUses}
 * is persisted, not the live {@code uses} counter: the restock features own that counter at runtime.
 */
@NullMarked
public final class VillagerRecipeCodec {

    /** The uses a decoded recipe starts at: a reapplied trade is offered fresh, its use counter reset. */
    private static final int FRESH_USES = 0;

    private VillagerRecipeCodec() {}

    /** Serialize {@code recipes} into the opaque blob stored on the villager. */
    public static byte[] encode(List<MerchantRecipe> recipes) {
        Objects.requireNonNull(recipes, "recipes");
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(recipes.size());
            for (MerchantRecipe recipe : recipes) {
                writeRecipe(out, recipe);
            }
            return bytes.toByteArray();
        } catch (IOException io) {
            throw new UncheckedIOException("villager recipes could not be serialized", io);
        }
    }

    /** Deserialize a blob written by {@link #encode} back into the villager's recipe set. */
    public static List<MerchantRecipe> decode(byte[] blob) {
        Objects.requireNonNull(blob, "blob");
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(blob))) {
            // Clamped before it sizes anything: the count comes off the stored blob, not from us.
            int count = PayloadLimits.entries(in.readInt());
            List<MerchantRecipe> recipes = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                recipes.add(readRecipe(in));
            }
            return recipes;
        } catch (IOException io) {
            throw new UncheckedIOException("villager recipes could not be deserialized", io);
        }
    }

    private static void writeRecipe(DataOutputStream out, MerchantRecipe recipe) throws IOException {
        writeItem(out, recipe.getResult());
        List<ItemStack> ingredients = recipe.getIngredients();
        out.writeInt(ingredients.size());
        for (ItemStack ingredient : ingredients) {
            writeItem(out, ingredient);
        }
        out.writeInt(recipe.getMaxUses());
        out.writeBoolean(recipe.hasExperienceReward());
        out.writeInt(recipe.getVillagerExperience());
        out.writeFloat(recipe.getPriceMultiplier());
    }

    private static MerchantRecipe readRecipe(DataInputStream in) throws IOException {
        ItemStack result = readItem(in);
        int ingredientCount = PayloadLimits.entries(in.readInt());
        List<ItemStack> ingredients = new ArrayList<>(ingredientCount);
        for (int index = 0; index < ingredientCount; index++) {
            ingredients.add(readItem(in));
        }
        int maxUses = in.readInt();
        boolean experienceReward = in.readBoolean();
        int villagerExperience = in.readInt();
        float priceMultiplier = in.readFloat();
        MerchantRecipe recipe = new MerchantRecipe(result, FRESH_USES, maxUses, experienceReward);
        recipe.setVillagerExperience(villagerExperience);
        recipe.setPriceMultiplier(priceMultiplier);
        recipe.setIngredients(ingredients);
        return recipe;
    }

    private static void writeItem(DataOutputStream out, ItemStack item) throws IOException {
        byte[] serialized = item.serializeAsBytes();
        out.writeInt(serialized.length);
        out.write(serialized);
    }

    private static ItemStack readItem(DataInputStream in) throws IOException {
        return ItemStack.deserializeBytes(PayloadLimits.readItemBytes(in));
    }
}

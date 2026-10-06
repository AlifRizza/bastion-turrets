package dev.bastion.workstation;

import com.google.gson.JsonObject;
import dev.bastion.registry.BastionRecipes;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * One workstation recipe (type bastion:workstation, PLAN Fase 9):
 * <pre>{"station": "part_workstation", "category": "bastion:gun_turret",
 *  "ingredients": [{"item": "minecraft:iron_ingot", "count": 3}], "result": {"item": "bastion:gun_barrel"},
 *  "time": 100, "energy": 3000}</pre>
 * {@code category} is the item whose tab the recipe sits under in the GUI; {@code time} in ticks; {@code energy} in FE for
 * the whole craft (spread over the time when processed, paid at once when crafted instantly).
 */
public record WorkstationRecipe(ResourceLocation id, WorkstationType station, ResourceLocation category, List<Counted> ingredients,
                                ItemStack result, int time, int energy) implements Recipe<Container> {
    /** An ingredient and how many of it one craft takes. */
    public record Counted(Ingredient ingredient, int count) {
    }

    /** FE drawn per tick while processing (the last tick may round a little over). */
    public int energyPerTick() {
        return (energy + time - 1) / time;
    }

    @Override
    public boolean matches(Container container, Level level) {
        return false; // never matched by vanilla crafting; stations pick their recipe explicitly
    }

    @Override
    public ItemStack assemble(Container container, RegistryAccess access) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess access) {
        return result;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return BastionRecipes.WORKSTATION_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return BastionRecipes.WORKSTATION.get();
    }

    @Override
    public boolean isSpecial() {
        return true; // keeps it out of the vanilla recipe book
    }

    public static class Serializer implements RecipeSerializer<WorkstationRecipe> {
        @Override
        public WorkstationRecipe fromJson(ResourceLocation id, JsonObject json) {
            List<Counted> ingredients = new ArrayList<>();
            for (var element : GsonHelper.getAsJsonArray(json, "ingredients")) {
                JsonObject entry = element.getAsJsonObject();
                ingredients.add(new Counted(Ingredient.fromJson(entry, false), GsonHelper.getAsInt(entry, "count", 1)));
            }
            return new WorkstationRecipe(id, WorkstationType.byId(GsonHelper.getAsString(json, "station")),
                    new ResourceLocation(GsonHelper.getAsString(json, "category")), List.copyOf(ingredients),
                    ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json, "result")),
                    GsonHelper.getAsInt(json, "time"), GsonHelper.getAsInt(json, "energy"));
        }

        @Override
        @Nullable
        public WorkstationRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            WorkstationType station = buf.readEnum(WorkstationType.class);
            ResourceLocation category = buf.readResourceLocation();
            List<Counted> ingredients = buf.readList(b -> new Counted(Ingredient.fromNetwork(b), b.readVarInt()));
            return new WorkstationRecipe(id, station, category, ingredients, buf.readItem(), buf.readVarInt(), buf.readVarInt());
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, WorkstationRecipe recipe) {
            buf.writeEnum(recipe.station);
            buf.writeResourceLocation(recipe.category);
            buf.writeCollection(recipe.ingredients, (b, counted) -> {
                counted.ingredient.toNetwork(b);
                b.writeVarInt(counted.count);
            });
            buf.writeItem(recipe.result);
            buf.writeVarInt(recipe.time);
            buf.writeVarInt(recipe.energy);
        }
    }
}

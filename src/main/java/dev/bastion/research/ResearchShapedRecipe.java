package dev.bastion.research;

import com.google.gson.JsonObject;
import dev.bastion.registry.BastionRecipes;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;

/**
 * A crafting-table recipe that only matches once its result is unlocked in this world (type bastion:research_shaped, PLAN 8
 * "Riset & Turret Codex"): the five workstations. Research is per world, so the level is enough, no player needed.
 */
public class ResearchShapedRecipe extends ShapedRecipe {
    public ResearchShapedRecipe(ShapedRecipe shaped) {
        super(shaped.getId(), shaped.getGroup(), shaped.category(), shaped.getWidth(), shaped.getHeight(), shaped.getIngredients(),
                shaped.getResultItem(RegistryAccess.EMPTY), shaped.showNotification());
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        return super.matches(container, level) && Research.unlocked(level, getResultItem(level.registryAccess()).getItem());
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return BastionRecipes.RESEARCH_SHAPED_SERIALIZER.get();
    }

    /** minecraft:crafting_shaped's JSON and network form, read by vanilla's serializer. */
    public static class Serializer implements RecipeSerializer<ResearchShapedRecipe> {
        @Override
        public ResearchShapedRecipe fromJson(ResourceLocation id, JsonObject json) {
            return new ResearchShapedRecipe(RecipeSerializer.SHAPED_RECIPE.fromJson(id, json));
        }

        @Override
        public ResearchShapedRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            return new ResearchShapedRecipe(RecipeSerializer.SHAPED_RECIPE.fromNetwork(id, buf));
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, ResearchShapedRecipe recipe) {
            RecipeSerializer.SHAPED_RECIPE.toNetwork(buf, recipe);
        }
    }
}

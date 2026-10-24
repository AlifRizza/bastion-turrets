package dev.bastion.registry;

import dev.bastion.Bastion;
import dev.bastion.research.ResearchShapedRecipe;
import dev.bastion.workstation.WorkstationRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Recipe type and serializer for the workstations (PLAN Fase 9), and the research-locked crafting recipe (0.6.0). */
public final class BastionRecipes {
    public static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(ForgeRegistries.RECIPE_TYPES, Bastion.MOD_ID);
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, Bastion.MOD_ID);

    public static final RegistryObject<RecipeType<WorkstationRecipe>> WORKSTATION = TYPES.register("workstation",
            () -> RecipeType.simple(Bastion.id("workstation")));
    public static final RegistryObject<RecipeSerializer<WorkstationRecipe>> WORKSTATION_SERIALIZER = SERIALIZERS.register("workstation",
            WorkstationRecipe.Serializer::new);
    /** Crafting-table recipes that wait for research (PLAN 8 "Riset & Turret Codex"): the workstations. */
    public static final RegistryObject<RecipeSerializer<ResearchShapedRecipe>> RESEARCH_SHAPED_SERIALIZER = SERIALIZERS.register("research_shaped",
            ResearchShapedRecipe.Serializer::new);

    private BastionRecipes() {
    }
}

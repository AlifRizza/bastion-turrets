package dev.bastion.client.compat.jei;

import dev.bastion.Bastion;
import dev.bastion.registry.BastionItems;
import dev.bastion.weapon.WeaponModuleItem;
import dev.bastion.workstation.WorkstationBlockEntity;
import dev.bastion.workstation.WorkstationRecipe;
import dev.bastion.workstation.WorkstationType;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

/** JEI pages for Bastion (PLAN "Compat JEI & Jade"). JEI finds this class by its annotation; nothing else refers to it. */
@JeiPlugin
public class BastionJeiPlugin implements IModPlugin {
    static final Map<WorkstationType, RecipeType<WorkstationRecipe>> STATIONS = new EnumMap<>(WorkstationType.class);
    static final RecipeType<WeaponModuleItem> WEAPONS = RecipeType.create(Bastion.MOD_ID, "turret_weapons", WeaponModuleItem.class);

    static {
        for (WorkstationType type : WorkstationType.values()) STATIONS.put(type, RecipeType.create(Bastion.MOD_ID, type.id(), WorkstationRecipe.class));
    }

    static ItemStack stationItem(WorkstationType type) {
        return new ItemStack(ForgeRegistries.ITEMS.getValue(Bastion.id(type.id())));
    }

    @Override
    public ResourceLocation getPluginUid() {
        return Bastion.id("jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper gui = registration.getJeiHelpers().getGuiHelper();
        for (WorkstationType type : WorkstationType.values()) registration.addRecipeCategories(new StationCategory(gui, STATIONS.get(type), type));
        registration.addRecipeCategories(new WeaponCategory(gui));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            for (WorkstationType type : WorkstationType.values()) registration.addRecipes(STATIONS.get(type), WorkstationBlockEntity.recipes(level, type));
        }
        // Every weapon, also before WeaponDataSync arrives (it may come after JEI loads): the page reads the data when drawn.
        registration.addRecipes(WEAPONS, BastionItems.REGISTER.getEntries().stream().map(RegistryObject::get)
                .filter(WeaponModuleItem.class::isInstance).map(WeaponModuleItem.class::cast).toList());
        info(registration, "turret_base", BastionItems.TURRET_BASE.get());
        info(registration, "large_turret_base", BastionItems.LARGE_TURRET_BASE.get());
        info(registration, "tier_upgrade_kit", BastionItems.TIER_UPGRADE_KIT_T2.get(), BastionItems.TIER_UPGRADE_KIT_T3.get());
        info(registration, "repair_kit", BastionItems.REPAIR_KIT.get());
        info(registration, "turret_configurator", BastionItems.TURRET_CONFIGURATOR.get());
        info(registration, "hub_connector", BastionItems.HUB_CONNECTOR.get());
        info(registration, "feed_hub", BastionItems.FEED_HUB.get());
        info(registration, "control_hub", BastionItems.HUB_INTERFACE.get(), BastionItems.HUB_CASING.get(), BastionItems.HUB_POWER_PORT.get(),
                BastionItems.SATELLITE_UPLINK_T1.get(), BastionItems.SATELLITE_UPLINK_T2.get(), BastionItems.SATELLITE_UPLINK_T3.get());
        info(registration, "creative_power_source", BastionItems.CREATIVE_POWER_SOURCE.get());
    }

    private static void info(IRecipeRegistration registration, String key, ItemLike... items) {
        registration.addItemStackInfo(Arrays.stream(items).map(ItemStack::new).toList(), Component.translatable("jei.bastion.info." + key));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        for (WorkstationType type : WorkstationType.values()) registration.addRecipeCatalyst(stationItem(type), STATIONS.get(type));
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        for (WorkstationType type : WorkstationType.values()) {
            registration.addRecipeTransferHandler(new StationTransfer(registration.getTransferHelper(), STATIONS.get(type), type), STATIONS.get(type));
        }
    }
}

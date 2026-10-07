package dev.bastion.registry;

import dev.bastion.Bastion;
import dev.bastion.menu.FeedHubMenu;
import dev.bastion.menu.TurretMenu;
import dev.bastion.workstation.WorkstationMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class BastionMenus {
    public static final DeferredRegister<MenuType<?>> REGISTER = DeferredRegister.create(ForgeRegistries.MENU_TYPES, Bastion.MOD_ID);

    public static final RegistryObject<MenuType<TurretMenu>> TURRET = REGISTER.register("turret", () -> IForgeMenuType.create(TurretMenu::new));
    public static final RegistryObject<MenuType<WorkstationMenu>> WORKSTATION = REGISTER.register("workstation",
            () -> IForgeMenuType.create(WorkstationMenu::new));
    public static final RegistryObject<MenuType<FeedHubMenu>> FEED_HUB = REGISTER.register("feed_hub", () -> IForgeMenuType.create(FeedHubMenu::new));
}

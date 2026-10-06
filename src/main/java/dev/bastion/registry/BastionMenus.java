package dev.bastion.registry;

import dev.bastion.Bastion;
import dev.bastion.menu.TurretMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class BastionMenus {
    public static final DeferredRegister<MenuType<?>> REGISTER = DeferredRegister.create(ForgeRegistries.MENU_TYPES, Bastion.MOD_ID);

    public static final RegistryObject<MenuType<TurretMenu>> TURRET = REGISTER.register("turret", () -> IForgeMenuType.create(TurretMenu::new));
}

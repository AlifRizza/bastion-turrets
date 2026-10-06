package dev.bastion.registry;

import dev.bastion.Bastion;
import dev.bastion.modifier.ModifierItem;
import dev.bastion.turret.TurretBaseItem;
import dev.bastion.turret.TurretConfiguratorItem;
import dev.bastion.weapon.WeaponModuleItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class BastionItems {
    public static final DeferredRegister<Item> REGISTER = DeferredRegister.create(ForgeRegistries.ITEMS, Bastion.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Bastion.MOD_ID);

    public static final RegistryObject<TurretBaseItem> TURRET_BASE = REGISTER.register("turret_base",
            () -> new TurretBaseItem(BastionBlocks.TURRET_BASE.get(), new Item.Properties()));
    public static final RegistryObject<TurretBaseItem> LARGE_TURRET_BASE = REGISTER.register("large_turret_base",
            () -> new TurretBaseItem(BastionBlocks.LARGE_TURRET_BASE.get(), new Item.Properties().stacksTo(16)));

    // Weapon modules, PLAN 7; each names its data/bastion/turret_weapons JSON.
    public static final RegistryObject<WeaponModuleItem> GUN_TURRET = REGISTER.register("gun_turret",
            () -> new WeaponModuleItem(new Item.Properties().stacksTo(1), Bastion.id("gun")));
    public static final RegistryObject<WeaponModuleItem> MACHINE_GUN_TURRET = REGISTER.register("machine_gun_turret",
            () -> new WeaponModuleItem(new Item.Properties().stacksTo(1), Bastion.id("machine_gun")));
    public static final RegistryObject<WeaponModuleItem> SHOTGUN_TURRET = REGISTER.register("shotgun_turret",
            () -> new WeaponModuleItem(new Item.Properties().stacksTo(1), Bastion.id("shotgun")));
    public static final RegistryObject<WeaponModuleItem> SNIPER_TURRET = REGISTER.register("sniper_turret",
            () -> new WeaponModuleItem(new Item.Properties().stacksTo(1), Bastion.id("sniper")));
    public static final RegistryObject<WeaponModuleItem> ROCKET_LAUNCHER_TURRET = REGISTER.register("rocket_launcher_turret",
            () -> new WeaponModuleItem(new Item.Properties().stacksTo(1), Bastion.id("rocket_launcher")));
    public static final RegistryObject<WeaponModuleItem> FLAMETHROWER_TURRET = REGISTER.register("flamethrower_turret",
            () -> new WeaponModuleItem(new Item.Properties().stacksTo(1), Bastion.id("flamethrower")));
    public static final RegistryObject<WeaponModuleItem> LASER_RIFLE_TURRET = REGISTER.register("laser_rifle_turret",
            () -> new WeaponModuleItem(new Item.Properties().stacksTo(1), Bastion.id("laser_rifle")));
    // Big modules: Large Turret Base only.
    public static final RegistryObject<WeaponModuleItem> MISSILE_LAUNCHER_TURRET = REGISTER.register("missile_launcher_turret",
            () -> new WeaponModuleItem(new Item.Properties().stacksTo(1), Bastion.id("missile_launcher"), true));
    /** Runs on Forge Energy fed into the base, no ammo. */
    public static final RegistryObject<WeaponModuleItem> TESLA_TURRET = REGISTER.register("tesla_turret",
            () -> new WeaponModuleItem(new Item.Properties().stacksTo(1), Bastion.id("tesla"), true));

    // Ammo, PLAN 7.4; tagged bastion:ammo/kinetic and bastion:ammo/scatter.
    public static final RegistryObject<Item> KINETIC_ROUNDS = REGISTER.register("kinetic_rounds", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> SCATTER_SHELLS = REGISTER.register("scatter_shells", () -> new Item(new Item.Properties().stacksTo(32)));
    public static final RegistryObject<Item> SNIPER_ROUNDS = REGISTER.register("sniper_rounds", () -> new Item(new Item.Properties().stacksTo(32)));
    public static final RegistryObject<Item> ROCKETS = REGISTER.register("rockets", () -> new Item(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> MISSILES = REGISTER.register("missiles", () -> new Item(new Item.Properties().stacksTo(32)));
    /** One Laser Rifle shot. No recipe yet: Laser Cells will be charged from empty ones with FE at a station (user: later). */
    public static final RegistryObject<Item> LASER_CELL = REGISTER.register("laser_cell", () -> new Item(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> FUEL_CANISTER = REGISTER.register("fuel_canister", () -> new Item(new Item.Properties().stacksTo(16)));
    /** Creative-only (no recipe): fits every weapon and is never used up. */
    public static final RegistryObject<Item> CREATIVE_AMMO = REGISTER.register("creative_ammo",
            () -> hinted(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC), "creative_ammo"));

    // Modifiers, PLAN 4.6; effects in data/bastion/turret_modifiers/<name>.json.
    public static final RegistryObject<ModifierItem> RANGE_MODULE = modifier("range_module");
    public static final RegistryObject<ModifierItem> RAPID_CYCLER = modifier("rapid_cycler");
    public static final RegistryObject<ModifierItem> DAMAGE_AMPLIFIER = modifier("damage_amplifier");
    public static final RegistryObject<ModifierItem> PENETRATOR = modifier("penetrator");
    public static final RegistryObject<ModifierItem> AMMO_RECYCLER = modifier("ammo_recycler");
    public static final RegistryObject<ModifierItem> COOLANT_LOOP = modifier("coolant_loop");
    public static final RegistryObject<ModifierItem> OVERCLOCK = modifier("overclock");
    public static final RegistryObject<ModifierItem> TARGETING_AI = modifier("targeting_ai");

    // Tools, PLAN 4.1 / 4.7.
    public static final RegistryObject<Item> REPAIR_KIT = REGISTER.register("repair_kit", () -> hinted(new Item.Properties().stacksTo(16), "repair_kit"));
    public static final RegistryObject<Item> TIER_UPGRADE_KIT_T2 = REGISTER.register("tier_upgrade_kit_t2", () -> hinted(new Item.Properties().stacksTo(16), "tier_upgrade_kit", 1));
    public static final RegistryObject<Item> TIER_UPGRADE_KIT_T3 = REGISTER.register("tier_upgrade_kit_t3", () -> hinted(new Item.Properties().stacksTo(16), "tier_upgrade_kit", 2));
    public static final RegistryObject<TurretConfiguratorItem> TURRET_CONFIGURATOR = REGISTER.register("turret_configurator",
            () -> new TurretConfiguratorItem(new Item.Properties()));
    /** What a destroyed T1 base leaves behind; crafted with a repair kit back into a base. */
    public static final RegistryObject<Item> DAMAGED_TURRET_BASE = REGISTER.register("damaged_turret_base", () -> hinted(new Item.Properties(), "damaged_turret_base"));
    public static final RegistryObject<Item> DAMAGED_LARGE_TURRET_BASE = REGISTER.register("damaged_large_turret_base",
            () -> hinted(new Item.Properties().stacksTo(16), "damaged_large_turret_base"));

    /** Plain item with a one-line usage hint, tooltip.bastion.<key>. */
    private static Item hinted(Item.Properties properties, String key, Object... args) {
        return new Item(properties) {
            @Override
            public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
                tooltip.add(Component.translatable("tooltip.bastion." + key, args).withStyle(ChatFormatting.GRAY));
            }
        };
    }

    private static RegistryObject<ModifierItem> modifier(String name) {
        return REGISTER.register(name, () -> new ModifierItem(new Item.Properties().stacksTo(1)));
    }

    // Lists every bastion item automatically, so later phases only register items.
    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("bastion", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.bastion"))
            .icon(() -> TURRET_BASE.get().getDefaultInstance())
            .displayItems((params, output) -> REGISTER.getEntries().forEach(item -> output.accept(item.get())))
            .build());
}

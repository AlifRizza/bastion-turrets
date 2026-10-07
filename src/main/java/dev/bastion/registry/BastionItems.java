package dev.bastion.registry;

import dev.bastion.Bastion;
import dev.bastion.modifier.ModifierItem;
import dev.bastion.turret.TurretBaseItem;
import dev.bastion.turret.TurretConfiguratorItem;
import dev.bastion.turret.TurretInventory;
import dev.bastion.weapon.WeaponModuleItem;
import dev.bastion.workstation.PartItem;
import dev.bastion.workstation.WorkstationBlock;
import dev.bastion.workstation.WorkstationItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
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
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

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
    /** Anti-boss gun: a Rail Slug and FE per shot. */
    public static final RegistryObject<WeaponModuleItem> RAILGUN_TURRET = REGISTER.register("railgun_turret",
            () -> new WeaponModuleItem(new Item.Properties().stacksTo(1), Bastion.id("railgun"), true));
    /** Lobs shells that leave fire patches; ground targets only. */
    public static final RegistryObject<WeaponModuleItem> MORTAR_TURRET = REGISTER.register("mortar_turret",
            () -> new WeaponModuleItem(new Item.Properties().stacksTo(1), Bastion.id("mortar"), true));

    // Ammo, PLAN 7.4; tagged bastion:ammo/kinetic and bastion:ammo/scatter.
    public static final RegistryObject<Item> KINETIC_ROUNDS = REGISTER.register("kinetic_rounds", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> SCATTER_SHELLS = REGISTER.register("scatter_shells", () -> new Item(new Item.Properties().stacksTo(32)));
    public static final RegistryObject<Item> SNIPER_ROUNDS = REGISTER.register("sniper_rounds", () -> new Item(new Item.Properties().stacksTo(32)));
    public static final RegistryObject<Item> ROCKETS = REGISTER.register("rockets", () -> new Item(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> MISSILES = REGISTER.register("missiles", () -> new Item(new Item.Properties().stacksTo(32)));
    /** One Laser Rifle shot: an Empty Laser Cell charged with FE at the Charging Station. */
    public static final RegistryObject<Item> LASER_CELL = REGISTER.register("laser_cell", () -> new Item(new Item.Properties().stacksTo(16)));
    /** Charged into a Laser Cell with FE at the Charging Station. */
    public static final RegistryObject<Item> EMPTY_LASER_CELL = REGISTER.register("empty_laser_cell", () -> new Item(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> FUEL_CANISTER = REGISTER.register("fuel_canister", () -> new Item(new Item.Properties().stacksTo(16)));
    /** One Railgun shot (plus FE from the base). */
    public static final RegistryObject<Item> RAIL_SLUGS = REGISTER.register("rail_slugs", () -> new Item(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> MORTAR_SHELLS = REGISTER.register("mortar_shells", () -> new Item(new Item.Properties().stacksTo(16)));
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
    // Weapon-specific modules (fit only their weapon, see ModifierEffect#weapons).
    public static final RegistryObject<ModifierItem> CHOKE_MODULE = modifier("choke_module");
    /** Weapon-specific: reloads faster, Rocket Launcher and Missile Launcher only. */
    public static final RegistryObject<ModifierItem> AUTOLOADER = modifier("autoloader");

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

    // Workstations (PLAN Fase 9): the only way to make turret items in survival.
    public static final RegistryObject<WorkstationItem> PART_WORKSTATION = workstation(BastionBlocks.PART_WORKSTATION);
    public static final RegistryObject<WorkstationItem> PART_ASSEMBLER = workstation(BastionBlocks.PART_ASSEMBLER);
    public static final RegistryObject<WorkstationItem> MODULE_WORKSTATION = workstation(BastionBlocks.MODULE_WORKSTATION);
    public static final RegistryObject<WorkstationItem> AMMO_WORKSTATION = workstation(BastionBlocks.AMMO_WORKSTATION);
    public static final RegistryObject<WorkstationItem> CHARGING_STATION = workstation(BastionBlocks.CHARGING_STATION);
    public static final RegistryObject<BlockItem> CREATIVE_POWER_SOURCE = REGISTER.register("creative_power_source",
            () -> new BlockItem(BastionBlocks.CREATIVE_POWER_SOURCE.get(), new Item.Properties().rarity(Rarity.EPIC)));

    /**
     * Parts, a set of three per base and weapon (the Missile Launcher takes two pods), made at the Part Workstation and
     * assembled at the Part Assembler. Which bones of which model each one shows: assets/bastion/parts.json.
     */
    public static final List<RegistryObject<PartItem>> PARTS = Stream.of(
            "turret_base_plating", "turret_base_housing", "turret_base_turntable",
            "large_base_plating", "large_base_housing", "large_base_turntable",
            "gun_barrel", "gun_receiver", "gun_mount",
            "machine_gun_barrels", "machine_gun_receiver", "machine_gun_mount",
            "shotgun_barrels", "shotgun_receiver", "shotgun_mount",
            "sniper_barrel", "sniper_receiver", "sniper_scope_mount",
            "rocket_pod", "rocket_pod_housing", "rocket_launcher_mount",
            "missile_pod", "missile_guidance_core", "missile_launcher_mount",
            "tesla_coil", "tesla_crown", "tesla_capacitor_bank",
            "flamethrower_nozzle", "flamethrower_fuel_tanks", "flamethrower_housing",
            "laser_focus_barrel", "laser_emitter_crystal", "laser_housing",
            "railgun_rails", "railgun_receiver", "railgun_mount",
            "mortar_barrel", "mortar_housing", "mortar_ring"
    ).map(id -> REGISTER.register(id, () -> new PartItem(new Item.Properties().stacksTo(16)))).toList();

    private static RegistryObject<WorkstationItem> workstation(RegistryObject<WorkstationBlock> block) {
        return REGISTER.register(block.getId().getPath(), () -> new WorkstationItem(block.get(), new Item.Properties()));
    }

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

    // Creative tabs (user: bases, weapon modules, ammo and modules apart), sorted by item type so new items land in the
    // right tab by themselves. Tools (repair and upgrade kits, configurator, damaged bases) sit with the bases.
    public static final RegistryObject<CreativeModeTab> TAB = tab("bastion", TURRET_BASE, null,
            item -> !(item instanceof WeaponModuleItem) && !(item instanceof ModifierItem) && !isAmmo(item) && !workshop(item)
                    && !(item instanceof PartItem) && item != EMPTY_LASER_CELL.get());
    public static final RegistryObject<CreativeModeTab> WEAPONS_TAB = tab("bastion_weapons", GUN_TURRET, "bastion",
            item -> item instanceof WeaponModuleItem);
    public static final RegistryObject<CreativeModeTab> AMMO_TAB = tab("bastion_ammo", KINETIC_ROUNDS, "bastion_weapons",
            item -> isAmmo(item) || item == EMPTY_LASER_CELL.get());
    public static final RegistryObject<CreativeModeTab> MODULES_TAB = tab("bastion_modules", RANGE_MODULE, "bastion_ammo",
            item -> item instanceof ModifierItem);
    /** The five stations and the creative power source (PLAN Fase 9). */
    public static final RegistryObject<CreativeModeTab> WORKSHOP_TAB = tab("bastion_workshop", PART_WORKSTATION, "bastion_modules",
            BastionItems::workshop);
    public static final RegistryObject<CreativeModeTab> PARTS_TAB = tab("bastion_parts", PARTS.get(0), "bastion_workshop",
            item -> item instanceof PartItem);

    private static boolean workshop(Item item) {
        return item instanceof WorkstationItem || item == CREATIVE_POWER_SOURCE.get();
    }

    /** Anything in the bastion:ammo tag (incl. Creative Ammo); tags are bound by the time a tab's contents are built. */
    private static boolean isAmmo(Item item) {
        return new ItemStack(item).is(TurretInventory.AMMO);
    }

    private static RegistryObject<CreativeModeTab> tab(String name, Supplier<? extends Item> icon, @Nullable String after, Predicate<Item> holds) {
        return TABS.register(name, () -> {
            CreativeModeTab.Builder builder = CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + name))
                    .icon(() -> icon.get().getDefaultInstance())
                    .displayItems((params, output) -> REGISTER.getEntries().stream().map(RegistryObject::get).filter(holds).forEach(output::accept));
            if (after != null) builder.withTabsBefore(Bastion.id(after));
            return builder.build();
        });
    }
}

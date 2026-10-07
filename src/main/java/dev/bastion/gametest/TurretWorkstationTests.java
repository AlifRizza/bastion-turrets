package dev.bastion.gametest;

import dev.bastion.Bastion;
import dev.bastion.registry.BastionBlocks;
import dev.bastion.registry.BastionItems;
import dev.bastion.workstation.WorkstationBlock;
import dev.bastion.workstation.WorkstationBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.RegistryObject;

import java.util.List;

/** Workstations (PLAN Fase 9): multiblock, FE, timed processing, automation filter, assembling parts, instant crafting. */
@GameTestHolder(Bastion.MOD_ID)
@PrefixGameTestTemplate(false)
public class TurretWorkstationTests {
    private static final String ARENA = "arena";
    /** Core; with the front north the station grows west (-x), south (+z) and up. */
    private static final BlockPos CORE = new BlockPos(4, 2, 2);

    private static WorkstationBlockEntity station(GameTestHelper helper, RegistryObject<WorkstationBlock> block, boolean powered) {
        block.get().placeAt(helper.getLevel(), helper.absolutePos(CORE), Direction.NORTH);
        if (powered) helper.setBlock(CORE.east(), BastionBlocks.CREATIVE_POWER_SOURCE.get());
        return (WorkstationBlockEntity) helper.getBlockEntity(CORE);
    }

    private static IItemHandler items(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockEntity(pos).getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElseThrow(IllegalStateException::new);
    }

    private static net.minecraft.world.item.Item part(String name) {
        return BastionItems.PARTS.stream().filter(p -> p.getId().getPath().equals(name)).findFirst().orElseThrow().get();
    }

    private static void select(WorkstationBlockEntity station, String station_, String result) {
        station.selectRecipe(Bastion.id("workstation/" + station_ + "/" + result));
    }

    @GameTest(template = ARENA, batch = "workstationIsOneBlock")
    public static void workstationIsOneBlock(GameTestHelper helper) {
        WorkstationBlockEntity station = station(helper, BastionBlocks.PART_ASSEMBLER, false);
        List<BlockPos> blocks = BastionBlocks.PART_ASSEMBLER.get().footprint(helper.absolutePos(CORE), Direction.NORTH);
        helper.assertTrue(blocks.size() == 8, "a Part Assembler is 2x2x2");
        for (BlockPos p : blocks) {
            helper.assertTrue(WorkstationBlock.core(helper.getLevel(), p) == station, "block " + p + " does not lead to the core");
        }
        helper.getLevel().destroyBlock(blocks.get(7), true); // the top back corner, not the core
        for (BlockPos p : blocks) helper.assertTrue(helper.getLevel().getBlockState(p).isAir(), "block left behind at " + p);
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(CORE)).inflate(4));
        helper.assertTrue(drops.size() == 1 && drops.get(0).getItem().is(BastionItems.PART_ASSEMBLER.get()),
                "expected one Part Assembler drop, got " + drops.stream().map(e -> e.getItem().toString()).toList());
        helper.succeed();
    }

    /** Automation reaches the core through any block, may only insert the selected recipe's ingredients and only take the output. */
    @GameTest(template = ARENA, timeoutTicks = 320, batch = "partWorkstationNeedsPower")
    public static void partWorkstationNeedsPower(GameTestHelper helper) {
        WorkstationBlockEntity station = station(helper, BastionBlocks.PART_WORKSTATION, false);
        IItemHandler belt = items(helper, CORE.west().above()); // the top of the other half
        helper.assertTrue(!ItemHandlerHelper.insertItem(belt, new ItemStack(Items.IRON_INGOT), false).isEmpty(), "accepted items with no recipe picked");
        select(station, "part_workstation", "gun_barrel");
        helper.assertTrue(ItemHandlerHelper.insertItem(belt, new ItemStack(Items.IRON_INGOT, 3), false).isEmpty(), "refused the barrel's iron");
        helper.assertTrue(ItemHandlerHelper.insertItem(belt, new ItemStack(Items.COPPER_INGOT), false).isEmpty(), "refused the barrel's copper");
        helper.assertTrue(!ItemHandlerHelper.insertItem(belt, new ItemStack(Items.DIRT), false).isEmpty(), "accepted dirt");
        helper.assertTrue(belt.extractItem(0, 1, true).isEmpty(), "automation could take an input back out");
        helper.runAtTickTime(150, () -> {
            helper.assertTrue(station.items().getStackInSlot(WorkstationBlockEntity.OUTPUT).isEmpty() && !station.working(), "crafted without power");
            helper.setBlock(CORE.east(), BastionBlocks.CREATIVE_POWER_SOURCE.get());
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getTick() > 150, "too early");
            helper.assertTrue(belt.extractItem(WorkstationBlockEntity.OUTPUT, 1, true).is(part("gun_barrel")), "no Gun Barrel yet");
            helper.assertTrue(station.items().getStackInSlot(0).isEmpty() && station.items().getStackInSlot(1).isEmpty(), "ingredients left over");
        });
    }

    /** Three Gun parts in, a Gun Turret out, after the recipe's time. */
    @GameTest(template = ARENA, timeoutTicks = 300, batch = "assemblerBuildsTheGun")
    public static void assemblerBuildsTheGun(GameTestHelper helper) {
        WorkstationBlockEntity station = station(helper, BastionBlocks.PART_ASSEMBLER, true);
        select(station, "part_assembler", "gun_turret");
        IItemHandler belt = items(helper, CORE);
        for (String part : List.of("gun_barrel", "gun_receiver", "gun_mount")) {
            ItemStack stack = new ItemStack(part(part));
            helper.assertTrue(ItemHandlerHelper.insertItem(belt, stack, false).isEmpty(), "refused " + part);
        }
        helper.runAtTickTime(100, () -> helper.assertTrue(station.working(), "not working with parts and power"));
        helper.succeedWhen(() -> helper.assertTrue(station.items().getStackInSlot(WorkstationBlockEntity.OUTPUT).is(BastionItems.GUN_TURRET.get()),
                "no Gun Turret yet, progress " + station.progress()));
    }

    /** Instant: the player's materials and the station's FE, the result straight into the player's inventory. */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "ammoWorkstationCraftsInstantly")
    public static void ammoWorkstationCraftsInstantly(GameTestHelper helper) {
        WorkstationBlockEntity station = station(helper, BastionBlocks.AMMO_WORKSTATION, true);
        helper.runAtTickTime(5, () -> {
            select(station, "ammo_workstation", "kinetic_rounds");
            FakePlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
            player.getInventory().clearContent();
            player.getInventory().add(new ItemStack(Items.COPPER_INGOT));
            player.getInventory().add(new ItemStack(Items.GUNPOWDER));
            player.getInventory().add(new ItemStack(Items.IRON_NUGGET));
            int energy = station.energy();
            station.instantCraft(player, 1);
            helper.assertTrue(player.getInventory().countItem(BastionItems.KINETIC_ROUNDS.get()) == 16, "expected 16 Kinetic Rounds");
            helper.assertTrue(player.getInventory().countItem(Items.GUNPOWDER) == 0, "materials not used");
            helper.assertTrue(station.energy() < energy, "no energy spent");
            station.instantCraft(player, 1);
            helper.assertTrue(player.getInventory().countItem(BastionItems.KINETIC_ROUNDS.get()) == 16, "crafted without materials");
            player.getInventory().clearContent();
            helper.succeed();
        });
    }

    /** Laser Cells: an Empty Laser Cell charged with FE at the Charging Station (1x1x1), over time from its inputs. */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "chargingStationChargesLaserCells")
    public static void chargingStationChargesLaserCells(GameTestHelper helper) {
        WorkstationBlockEntity station = station(helper, BastionBlocks.CHARGING_STATION, true);
        helper.assertTrue(BastionBlocks.CHARGING_STATION.get().footprint(helper.absolutePos(CORE), Direction.NORTH).size() == 1, "not one block");
        select(station, "charging_station", "laser_cell");
        ItemHandlerHelper.insertItem(items(helper, CORE), new ItemStack(BastionItems.EMPTY_LASER_CELL.get(), 2), false);
        helper.succeedWhen(() -> helper.assertTrue(station.items().getStackInSlot(WorkstationBlockEntity.OUTPUT).getCount() == 2
                && station.items().getStackInSlot(WorkstationBlockEntity.OUTPUT).is(BastionItems.LASER_CELL.get()), "cells not charged yet"));
    }

    /** Survival: the crafting table makes the workstations, nothing else of the mod. */
    @GameTest(template = ARENA, batch = "craftingTableOnlyMakesWorkstations")
    public static void craftingTableOnlyMakesWorkstations(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING);
        var registry = helper.getLevel().registryAccess();
        var made = recipes.stream().map(r -> r.getResultItem(registry)).filter(s -> s.getItem().getCreatorModId(s).equals(Bastion.MOD_ID))
                .map(s -> s.getItem().toString()).sorted().toList();
        helper.assertTrue(made.equals(List.of("ammo_workstation", "charging_station", "module_workstation", "part_assembler", "part_workstation")),
                "the crafting table still makes " + made);
        helper.succeed();
    }

    /** Cable and pipe mods pull: the Creative Power Source offers endless FE to extract on every side, and takes none. */
    @GameTest(template = ARENA, batch = "creativePowerSourceCanBePulled")
    public static void creativePowerSourceCanBePulled(GameTestHelper helper) {
        BlockPos pos = new BlockPos(4, 2, 2);
        helper.setBlock(pos, BastionBlocks.CREATIVE_POWER_SOURCE.get());
        for (Direction side : Direction.values()) {
            IEnergyStorage source = helper.getBlockEntity(pos).getCapability(ForgeCapabilities.ENERGY, side).orElse(null);
            helper.assertTrue(source != null && source.canExtract() && !source.canReceive(), "no pullable FE on the " + side + " side");
            helper.assertTrue(source.extractEnergy(1_000_000, false) == 1_000_000, "did not hand out what was pulled");
            helper.assertTrue(source.receiveEnergy(1000, false) == 0, "took FE in");
        }
        helper.succeed();
    }
}

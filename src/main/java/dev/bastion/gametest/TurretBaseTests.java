package dev.bastion.gametest;

import dev.bastion.Bastion;
import dev.bastion.registry.BastionBlocks;
import dev.bastion.registry.BastionItems;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretInventory;
import dev.bastion.turret.TurretTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.List;

/** Fase 1 acceptance checks (PLAN 8). Run with {@code ./gradlew runGameTestServer}. */
@GameTestHolder(Bastion.MOD_ID)
@PrefixGameTestTemplate(false)
public class TurretBaseTests {
    private static final String EMPTY = "empty"; // data/bastion/structures/empty.nbt, 3x3x3 air
    private static final BlockPos BASE = new BlockPos(1, 1, 1);

    private static TurretBaseBlockEntity placeBase(GameTestHelper helper) {
        helper.setBlock(BASE, BastionBlocks.TURRET_BASE.get());
        return (TurretBaseBlockEntity) helper.getBlockEntity(BASE);
    }

    /** GameTestHelper#destroyBlock never drops loot in 1.20.1; break it the way a player or explosion does. */
    private static void breakWithDrops(GameTestHelper helper) {
        helper.getLevel().destroyBlock(helper.absolutePos(BASE), true);
    }

    private static ItemStack rounds(int count) {
        return new ItemStack(BastionItems.KINETIC_ROUNDS.get(), count);
    }

    @GameTest(template = EMPTY)
    public static void automationSeesOnlyUnlockedAmmoSlots(GameTestHelper helper) {
        TurretBaseBlockEntity base = placeBase(helper);
        IItemHandler handler = base.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElseThrow(IllegalStateException::new);
        helper.assertTrue(handler.getSlots() == TurretTier.T1.ammoSlots, "T1 must expose exactly its 3 ammo slots");
        helper.assertTrue(!ItemHandlerHelper.insertItem(handler, new ItemStack(Items.DIRT), false).isEmpty(), "non-ammo must be refused");

        for (int i = 0; i < 4; i++) ItemHandlerHelper.insertItem(handler, rounds(64), false);
        TurretInventory inventory = base.inventory();
        helper.assertTrue(inventory.getStackInSlot(TurretInventory.AMMO_START + 2).getCount() == 64, "third ammo slot should fill");
        helper.assertTrue(inventory.getStackInSlot(TurretInventory.AMMO_START + 3).isEmpty(), "slot locked at T1 must stay empty");
        helper.assertTrue(inventory.getStackInSlot(TurretInventory.WEAPON).isEmpty(), "weapon slot must be out of reach");
        helper.assertTrue(base.comparatorLevel() == 15, "full ammo should read 15 on a comparator");
        helper.succeed();
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void hopperFeedsAmmoAndKeepsTheRest(GameTestHelper helper) {
        TurretBaseBlockEntity base = placeBase(helper);
        BlockPos hopperPos = BASE.above();
        helper.setBlock(hopperPos, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        HopperBlockEntity hopper = (HopperBlockEntity) helper.getBlockEntity(hopperPos);
        hopper.setItem(0, new ItemStack(Items.DIRT, 2));
        hopper.setItem(1, new ItemStack(BastionItems.SCATTER_SHELLS.get(), 3));

        helper.succeedWhen(() -> {
            helper.assertTrue(base.inventory().getStackInSlot(TurretInventory.AMMO_START).getCount() == 3, "shells not delivered yet");
            helper.assertTrue(hopper.getItem(0).getCount() == 2, "dirt must stay in the hopper");
        });
    }

    @GameTest(template = EMPTY)
    public static void brokenBaseCarriesItsContents(GameTestHelper helper) {
        placeBase(helper).inventory().insertItem(TurretInventory.AMMO_START, rounds(5), false);
        breakWithDrops(helper);

        List<ItemEntity> drops = helper.getEntities(EntityType.ITEM, BASE, 2);
        helper.assertTrue(drops.size() == 1, "expected one drop, got " + drops.size());
        ItemStack drop = drops.get(0).getItem();
        CompoundTag data = BlockItem.getBlockEntityData(drop);
        helper.assertTrue(drop.is(BastionItems.TURRET_BASE.get()) && data != null, "drop must be a base carrying its BlockEntityTag");

        TurretBaseBlockEntity restored = new TurretBaseBlockEntity(helper.absolutePos(BASE), BastionBlocks.TURRET_BASE.get().defaultBlockState());
        restored.load(data);
        helper.assertTrue(restored.inventory().getStackInSlot(TurretInventory.AMMO_START).getCount() == 5, "contents not restored on load");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void emptyBaseDropsAPlainItem(GameTestHelper helper) {
        placeBase(helper);
        breakWithDrops(helper);
        List<ItemEntity> drops = helper.getEntities(EntityType.ITEM, BASE, 2);
        helper.assertTrue(drops.size() == 1 && !drops.get(0).getItem().hasTag(), "empty base should drop a stackable item without NBT");
        helper.succeed();
    }
}

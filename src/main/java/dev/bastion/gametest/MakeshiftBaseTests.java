package dev.bastion.gametest;

import dev.bastion.Bastion;
import dev.bastion.compat.BlockInfo;
import dev.bastion.registry.BastionBlocks;
import dev.bastion.registry.BastionItems;
import dev.bastion.turret.BaseKind;
import dev.bastion.turret.TurretBaseBlock;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretInventory;
import dev.bastion.turret.TurretTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** The Makeshift Turret Base (0.4.0 early game): no tier, modules, FE or Connector; drops scrap when destroyed. */
@GameTestHolder(Bastion.MOD_ID)
@PrefixGameTestTemplate(false)
public class MakeshiftBaseTests {
    private static final String EMPTY = "empty"; // 3x3x3 air
    private static final BlockPos BASE = new BlockPos(1, 1, 1);

    private static TurretBaseBlockEntity place(GameTestHelper helper) {
        helper.setBlock(BASE, BastionBlocks.MAKESHIFT_TURRET_BASE.get());
        return (TurretBaseBlockEntity) helper.getBlockEntity(BASE);
    }

    @GameTest(template = EMPTY)
    public static void makeshiftRefusesModernModules(GameTestHelper helper) {
        TurretBaseBlockEntity base = place(helper);
        helper.assertTrue(base.kind() == BaseKind.MAKESHIFT, "kind " + base.kind());
        helper.assertTrue(!base.inventory().isItemValid(TurretInventory.WEAPON, new ItemStack(BastionItems.GUN_TURRET.get())), "took a Gun");
        helper.assertTrue(!base.inventory().isItemValid(TurretInventory.WEAPON, new ItemStack(BastionItems.MORTAR_TURRET.get())), "took a Mortar");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void makeshiftHasNoModulesConnectorOrFe(GameTestHelper helper) {
        TurretBaseBlockEntity base = place(helper);
        for (int i = TurretInventory.MODIFIER_START; i <= TurretInventory.CONNECTOR; i++) {
            helper.assertTrue(!base.inventory().isActive(i), "slot " + i + " is active");
        }
        helper.assertTrue(!base.inventory().isItemValid(TurretInventory.CONNECTOR, new ItemStack(BastionItems.HUB_CONNECTOR.get())), "took a Connector");
        helper.assertTrue(!base.getCapability(ForgeCapabilities.ENERGY, Direction.UP).isPresent(), "has an energy capability");
        helper.assertTrue(base.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).isPresent(), "lost its ammo handler");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void tierKitDoesNothingOnMakeshift(GameTestHelper helper) {
        TurretBaseBlockEntity base = place(helper);
        FakePlayer player = ControlHubTests.player(helper, new BlockPos(1, 1, 0));
        ItemStack kit = new ItemStack(BastionItems.TIER_UPGRADE_KIT_T2.get(), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, kit);
        BlockPos at = helper.absolutePos(BASE);
        BlockState state = helper.getLevel().getBlockState(at);
        state.use(helper.getLevel(), player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false));
        helper.assertTrue(base.tier() == TurretTier.T1, "tier " + base.tier());
        helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 2, "the kit was used");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void makeshiftHealthIsFiftyFromConfig(GameTestHelper helper) {
        TurretBaseBlockEntity base = place(helper);
        helper.assertTrue(base.maxHealth() == 50f && base.health() == 50f, "hp " + base.health() + "/" + base.maxHealth());
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void destroyedMakeshiftDropsScrap(GameTestHelper helper) {
        TurretBaseBlockEntity base = place(helper);
        base.inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(Items.COBBLESTONE, 5));
        base.takeDamage(helper.getLevel().damageSources().generic(), 100_000);
        List<ItemEntity> drops = helper.getEntities(EntityType.ITEM, BASE, 3);
        Map<Object, Integer> counts = drops.stream().collect(Collectors.toMap(e -> e.getItem().getItem(), e -> e.getItem().getCount(), Integer::sum));
        helper.assertTrue(counts.equals(Map.of(Items.IRON_INGOT, 1, Items.OAK_PLANKS, 2)), "drops " + counts);
        helper.assertTrue(!counts.containsKey(BastionItems.DAMAGED_TURRET_BASE.get()), "dropped a damaged base");
        helper.assertBlockNotPresent(BastionBlocks.MAKESHIFT_TURRET_BASE.get(), BASE);
        helper.succeed();
    }

    /** A makeshift base falls apart into wood and dust (user 2026-10-23): no blast hurts what stands next to it. */
    @GameTest(template = EMPTY)
    public static void destroyedMakeshiftDoesNotExplode(GameTestHelper helper) {
        TurretBaseBlockEntity base = place(helper);
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(1, 1, 0));
        base.takeDamage(helper.getLevel().damageSources().generic(), 100_000);
        helper.assertBlockNotPresent(BastionBlocks.MAKESHIFT_TURRET_BASE.get(), BASE);
        helper.assertTrue(pig.getHealth() == pig.getMaxHealth(), "pig hurt: " + pig.getHealth() + "/" + pig.getMaxHealth());
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void makeshiftKeepsItsContents(GameTestHelper helper) {
        TurretBaseBlockEntity base = place(helper);
        base.inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(Items.COBBLESTONE, 7));
        helper.getLevel().destroyBlock(helper.absolutePos(BASE), true);
        List<ItemEntity> drops = helper.getEntities(EntityType.ITEM, BASE, 3);
        helper.assertTrue(drops.size() == 1, "expected one drop, got " + drops.size());
        ItemStack drop = drops.get(0).getItem();
        CompoundTag data = BlockItem.getBlockEntityData(drop);
        helper.assertTrue(drop.is(BastionItems.MAKESHIFT_TURRET_BASE.get()) && data != null && data.contains("Inventory") && !data.contains("Tier"),
                "drop " + drop + " " + data);

        TurretBaseBlockEntity restored = new TurretBaseBlockEntity(helper.absolutePos(BASE), BastionBlocks.MAKESHIFT_TURRET_BASE.get().defaultBlockState());
        restored.load(data);
        helper.assertTrue(restored.inventory().getStackInSlot(TurretInventory.AMMO_START).getCount() == 7, "contents not restored");
        // a save and load into a fresh entity keeps the kind and the inventory
        TurretBaseBlockEntity copy = new TurretBaseBlockEntity(helper.absolutePos(BASE), BastionBlocks.MAKESHIFT_TURRET_BASE.get().defaultBlockState());
        copy.load(restored.saveWithoutMetadata());
        helper.assertTrue(copy.kind() == BaseKind.MAKESHIFT && copy.inventory().getStackInSlot(TurretInventory.AMMO_START).is(Items.COBBLESTONE),
                "round trip lost " + copy.kind());
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void makeshiftMountsOnWallsAndCeilings(GameTestHelper helper) {
        for (Direction facing : List.of(Direction.NORTH, Direction.DOWN)) {
            helper.setBlock(BASE, BastionBlocks.MAKESHIFT_TURRET_BASE.get().defaultBlockState().setValue(TurretBaseBlock.FACING, facing));
            TurretBaseBlockEntity base = (TurretBaseBlockEntity) helper.getBlockEntity(BASE);
            helper.assertTrue(base != null && base.kind() == BaseKind.MAKESHIFT && base.facing() == facing, "facing " + facing);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void jadeLinesHaveNoTierOnMakeshift(GameTestHelper helper) {
        TurretBaseBlockEntity base = place(helper);
        CompoundTag tag = BlockInfo.write(base);
        helper.assertTrue(!tag.contains("Tier"), "Tier written");
        String lines = BlockInfo.lines(tag, true).stream().map(CompatTests::text).collect(Collectors.joining("|"));
        helper.assertTrue(!lines.contains("turret.tier"), "tier line: " + lines);
        helper.succeed();
    }

    /**
     * A player who loads the chunk after the base was hit (walking up, relogging) still sees its cracks: the update tag
     * carries the HP fraction. A makeshift base never regenerates, so no later sync would ever send it.
     */
    @GameTest(template = EMPTY, batch = "updateTagCarriesDamage") // in the default batch it reshuffled the arenas and failed laserPiercesEveryValidTarget twice
    public static void updateTagCarriesDamage(GameTestHelper helper) {
        TurretBaseBlockEntity base = place(helper);
        base.takeDamage(helper.getLevel().damageSources().generic(), base.maxHealth() * 0.7f);
        TurretBaseBlockEntity client = new TurretBaseBlockEntity(base.getBlockPos(), base.getBlockState());
        client.load(base.getUpdateTag());
        helper.assertTrue(Math.abs(client.clientHealth() - 0.3f) < 0.01f, "client health " + client.clientHealth());
        helper.succeed();
    }
}

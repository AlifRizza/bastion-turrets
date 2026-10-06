package dev.bastion.dev;

import com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem;
import dev.bastion.Bastion;
import dev.bastion.registry.BastionBlocks;
import dev.bastion.registry.BastionItems;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretHitboxEntity;
import dev.bastion.turret.TurretInventory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/** Dev-only GameTests that need Create in the run. */
@GameTestHolder(Bastion.MOD_ID)
@PrefixGameTestTemplate(false)
public class CreatePlacementTests {
    /**
     * A player can put a belt funnel on a turret's side (belt -> funnel -> turret). The funnel's shape reaches into
     * the base's block, so this broke once the invisible hitbox entity refused blocks like any mob.
     */
    @GameTest(template = "arena", batch = "beltFunnelOnTurretSide", timeoutTicks = 100)
    public static void beltFunnelOnTurretSide(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos turret = new BlockPos(7, 3, 3), funnel = turret.west();
        BeltConnectorItem.createBelts(level, helper.absolutePos(new BlockPos(3, 2, 3)), helper.absolutePos(funnel.below()));
        helper.setBlock(turret.below(), Blocks.STONE);
        helper.setBlock(turret, BastionBlocks.TURRET_BASE.get());
        ((TurretBaseBlockEntity) helper.getBlockEntity(turret)).inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(BastionItems.GUN_TURRET.get()));
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(!level.getEntitiesOfClass(TurretHitboxEntity.class, new AABB(helper.absolutePos(turret)).inflate(1)).isEmpty(),
                    "the turret's hitbox has not spawned yet");
            FakePlayer player = FakePlayerFactory.getMinecraft(level);
            Vec3 face = Vec3.atCenterOf(helper.absolutePos(turret)).add(-0.5, 0, 0);
            player.moveTo(face.x - 2, face.y - 0.5, face.z, -90, 0); // two blocks west, looking east
            ItemStack stack = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("create", "andesite_funnel")));
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            player.gameMode.useItemOn(player, level, stack, InteractionHand.MAIN_HAND,
                    new BlockHitResult(face, Direction.WEST, helper.absolutePos(turret), false));
            String placed = String.valueOf(ForgeRegistries.BLOCKS.getKey(helper.getBlockState(funnel).getBlock()));
            helper.assertTrue(placed.equals("create:andesite_belt_funnel"), "expected a belt funnel on the turret's side, got " + placed);
            helper.succeed();
        });
    }
}

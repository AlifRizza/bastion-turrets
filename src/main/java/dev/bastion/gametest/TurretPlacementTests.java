package dev.bastion.gametest;

import dev.bastion.Bastion;
import dev.bastion.registry.BastionBlocks;
import dev.bastion.registry.BastionItems;
import dev.bastion.turret.Footprint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.Set;

/** Placement hologram (PLAN "Hologram penempatan multiblock"): the footprint is exactly what a click fills. */
@GameTestHolder(Bastion.MOD_ID)
@PrefixGameTestTemplate(false)
public class TurretPlacementTests {
    private static final String ARENA = "arena";
    /** The floor block clicked (template floors sit at test y 1); the block goes on top of it. */
    private static final BlockPos FLOOR = new BlockPos(4, 1, 4);

    /** A fake player north of the spot looking along {@code yaw} with {@code item} in hand, clicking {@code face} of FLOOR. */
    private static BlockPlaceContext click(GameTestHelper helper, Item item, float yaw, Direction face) {
        FakePlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        BlockPos floor = helper.absolutePos(FLOOR);
        player.moveTo(floor.getX() + 0.5, floor.getY() + 1, floor.getZ() - 2.5, yaw, 30);
        ItemStack stack = new ItemStack(item);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Vec3 hit = Vec3.atCenterOf(floor).relative(face, 0.5);
        return new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, new BlockHitResult(hit, face, floor, false));
    }

    /** Absolute positions of {@code block} within 3 blocks of the spot. */
    private static Set<BlockPos> placed(GameTestHelper helper, Block block) {
        Set<BlockPos> found = new HashSet<>();
        BlockPos.betweenClosed(helper.absolutePos(FLOOR).offset(-3, 1, -3), helper.absolutePos(FLOOR).offset(3, 3, 3))
                .forEach(p -> { if (helper.getLevel().getBlockState(p).is(block)) found.add(p.immutable()); });
        return found;
    }

    @GameTest(template = ARENA, batch = "largeBaseFootprintIsWhatGetsPlaced")
    public static void largeBaseFootprintIsWhatGetsPlaced(GameTestHelper helper) {
        helper.assertTrue(BastionBlocks.LARGE_TURRET_BASE.get().footprint(click(helper, BastionItems.LARGE_TURRET_BASE.get(), 90, Direction.NORTH)) == null,
                "a large base offered a footprint on a side face");
        BlockPlaceContext context = click(helper, BastionItems.LARGE_TURRET_BASE.get(), 90, Direction.UP); // looking west
        Footprint footprint = BastionBlocks.LARGE_TURRET_BASE.get().footprint(context);
        helper.assertTrue(footprint != null && footprint.placeable() && footprint.blocks().size() == 4, "footprint " + footprint);
        helper.assertTrue(((BlockItem) BastionItems.LARGE_TURRET_BASE.get()).place(context).consumesAction(), "not placed");
        Set<BlockPos> placed = placed(helper, BastionBlocks.LARGE_TURRET_BASE.get());
        helper.assertTrue(placed.equals(Set.copyOf(footprint.blocks())), "placed " + placed + ", footprint " + footprint.blocks());
        helper.succeed();
    }

    /** A cow standing in a station's back cell: that cell is in the way and nothing is placed inside it (no suffocated pets). */
    @GameTest(template = ARENA, batch = "stationRefusesToBuildInsideAMob")
    public static void stationRefusesToBuildInsideAMob(GameTestHelper helper) {
        BlockItem item = (BlockItem) BastionItems.PART_ASSEMBLER.get();
        BlockPlaceContext context = click(helper, item, 0, Direction.UP);
        BlockPos back = BastionBlocks.PART_ASSEMBLER.get().footprint(context).blocks().get(2); // x 0, ground, one block back
        Cow cow = EntityType.COW.create(helper.getLevel()); // absolute position: the same space as the footprint
        cow.moveTo(Vec3.atBottomCenterOf(back));
        cow.setNoAi(true);
        helper.getLevel().addFreshEntity(cow);
        Footprint footprint = BastionBlocks.PART_ASSEMBLER.get().footprint(context);
        helper.assertTrue(footprint.blocked().contains(back), "the cow's cell is not marked: " + footprint.blocked());
        helper.assertTrue(!item.place(context).consumesAction(), "placed a station around a cow");
        cow.discard();
        helper.succeed();
    }

    @GameTest(template = ARENA, batch = "assemblerFootprintMarksTheBlocker")
    public static void assemblerFootprintMarksTheBlocker(GameTestHelper helper) {
        BlockItem item = (BlockItem) BastionItems.PART_ASSEMBLER.get();
        BlockPlaceContext context = click(helper, item, 0, Direction.UP); // looking south: the front faces north, back at the player
        BlockPos blocker = BastionBlocks.PART_ASSEMBLER.get().footprint(context).blocks().get(7); // far top corner
        helper.getLevel().setBlockAndUpdate(blocker, Blocks.STONE.defaultBlockState());
        Footprint footprint = BastionBlocks.PART_ASSEMBLER.get().footprint(context);
        helper.assertTrue(footprint.front() == Direction.NORTH && footprint.blocks().size() == 8, "footprint " + footprint);
        helper.assertTrue(footprint.blocked().equals(Set.of(blocker)), "blocked " + footprint.blocked() + ", expected " + blocker);
        helper.assertTrue(!item.place(context).consumesAction(), "placed through a stone block");
        helper.getLevel().setBlockAndUpdate(blocker, Blocks.AIR.defaultBlockState());
        helper.assertTrue(item.place(context).consumesAction(), "not placed once clear");
        Set<BlockPos> placed = placed(helper, BastionBlocks.PART_ASSEMBLER.get());
        helper.assertTrue(placed.equals(Set.copyOf(footprint.blocks())), "placed " + placed + ", footprint " + footprint.blocks());
        helper.succeed();
    }
}

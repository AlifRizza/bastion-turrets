package dev.bastion.gametest;

import dev.bastion.Bastion;
import dev.bastion.config.BastionConfig;
import dev.bastion.menu.FeedHubMenu;
import dev.bastion.registry.BastionBlocks;
import dev.bastion.registry.BastionItems;
import dev.bastion.turret.FeedHubBlock;
import dev.bastion.turret.FeedHubBlockEntity;
import dev.bastion.turret.LargeTurretBaseBlock;
import dev.bastion.turret.TurretBaseBlock;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretInventory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Feed Hub: ammo goes to the turrets mounted on it whose weapon takes it, evenly; what does not fit waits in the buffer. */
@GameTestHolder(Bastion.MOD_ID)
@PrefixGameTestTemplate(false)
public class TurretFeedHubTests {
    private static final String ARENA = "arena";
    /** In the air, so turrets fit on every side (the arena's floor is at y 1). */
    private static final BlockPos HUB = new BlockPos(5, 3, 3);

    private static IItemHandler hub(GameTestHelper helper) {
        helper.setBlock(HUB, BastionBlocks.FEED_HUB.get());
        return helper.getBlockEntity(HUB).getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElseThrow(IllegalStateException::new);
    }

    /** A standard base at {@code pos} pointing {@code facing}, with {@code weapon} (or none). */
    private static TurretBaseBlockEntity base(GameTestHelper helper, BlockPos pos, Direction facing, @Nullable Item weapon) {
        helper.setBlock(pos, BastionBlocks.TURRET_BASE.get().defaultBlockState().setValue(TurretBaseBlock.FACING, facing));
        TurretBaseBlockEntity base = (TurretBaseBlockEntity) helper.getBlockEntity(pos);
        if (weapon != null) base.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(weapon));
        return base;
    }

    private static int ammo(TurretBaseBlockEntity base, Item item) {
        int total = 0;
        for (int i = 0; i < base.inventory().ammoSlots(); i++) {
            ItemStack stack = base.inventory().getStackInSlot(TurretInventory.AMMO_START + i);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    private static boolean bufferEmpty(GameTestHelper helper) {
        IItemHandler buffer = ((FeedHubBlockEntity) helper.getBlockEntity(HUB)).buffer();
        for (int i = 0; i < buffer.getSlots(); i++) if (!buffer.getStackInSlot(i).isEmpty()) return false;
        return true;
    }

    /**
     * Two guns, a shotgun and an unarmed base on the hub, and a gun base standing next to it (its back is not on the
     * hub): 16 kinetic rounds split 8/8 between the mounted guns, the shells go to the shotgun, rockets are refused.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "feedHubSplitsEvenly")
    public static void feedHubSplitsEvenly(GameTestHelper helper) {
        IItemHandler hub = hub(helper);
        TurretBaseBlockEntity east = base(helper, HUB.east(), Direction.EAST, BastionItems.GUN_TURRET.get());
        TurretBaseBlockEntity west = base(helper, HUB.west(), Direction.WEST, BastionItems.GUN_TURRET.get());
        TurretBaseBlockEntity north = base(helper, HUB.north(), Direction.NORTH, BastionItems.SHOTGUN_TURRET.get());
        TurretBaseBlockEntity top = base(helper, HUB.above(), Direction.UP, null);
        TurretBaseBlockEntity beside = base(helper, HUB.south(), Direction.UP, BastionItems.GUN_TURRET.get());

        helper.assertTrue(ItemHandlerHelper.insertItem(hub, new ItemStack(BastionItems.KINETIC_ROUNDS.get(), 16), false).isEmpty(), "kinetic rounds refused");
        helper.assertTrue(ItemHandlerHelper.insertItem(hub, new ItemStack(BastionItems.SCATTER_SHELLS.get(), 4), false).isEmpty(), "scatter shells refused");
        helper.assertTrue(ItemHandlerHelper.insertItem(hub, new ItemStack(BastionItems.ROCKETS.get(), 1), false).getCount() == 1,
                "took rockets no mounted turret fires");
        helper.succeedWhen(() -> {
            helper.assertTrue(bufferEmpty(helper), "the buffer still holds ammo");
            helper.assertTrue(ammo(east, BastionItems.KINETIC_ROUNDS.get()) == 8 && ammo(west, BastionItems.KINETIC_ROUNDS.get()) == 8,
                    "uneven split: east " + ammo(east, BastionItems.KINETIC_ROUNDS.get()) + ", west " + ammo(west, BastionItems.KINETIC_ROUNDS.get()));
            helper.assertTrue(ammo(north, BastionItems.SCATTER_SHELLS.get()) == 4, "the shotgun did not get its shells");
            helper.assertTrue(top.inventory().findAmmoSlot() < 0 && ammo(top, BastionItems.KINETIC_ROUNDS.get()) == 0, "fed the unarmed base");
            helper.assertTrue(ammo(beside, BastionItems.KINETIC_ROUNDS.get()) == 0, "fed a base that is not mounted on the hub");
        });
    }

    /** With the only gun full, its rounds wait in the buffer, and go in once it has room again. */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "feedHubBuffersWhenFull")
    public static void feedHubBuffersWhenFull(GameTestHelper helper) {
        IItemHandler hub = hub(helper);
        TurretBaseBlockEntity gun = base(helper, HUB.east(), Direction.EAST, BastionItems.GUN_TURRET.get());
        for (int i = 0; i < gun.inventory().ammoSlots(); i++) {
            gun.inventory().setStackInSlot(TurretInventory.AMMO_START + i, new ItemStack(BastionItems.KINETIC_ROUNDS.get(), 64));
        }
        helper.assertTrue(ItemHandlerHelper.insertItem(hub, new ItemStack(BastionItems.KINETIC_ROUNDS.get(), 10), false).isEmpty(),
                "refused rounds its turret fires while that turret is full");
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(!bufferEmpty(helper), "the rounds did not wait in the buffer");
            gun.inventory().setStackInSlot(TurretInventory.AMMO_START, ItemStack.EMPTY);
        });
        helper.runAfterDelay(21, () -> helper.succeedWhen(() -> {
            helper.assertTrue(bufferEmpty(helper), "the buffer did not empty into the gun");
            helper.assertTrue(gun.inventory().getStackInSlot(TurretInventory.AMMO_START).getCount() == 10, "the gun did not get the 10 rounds");
        }));
    }

    /** Hubs on every block of a box {@code width} wide and {@code height} tall from {@code min}. */
    private static void hubs(GameTestHelper helper, BlockPos min, int width, int height) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                for (int z = 0; z < width; z++) helper.setBlock(min.offset(x, y, z), BastionBlocks.FEED_HUB.get());
            }
        }
    }

    /** Every block of the box carries its place in a structure of that size. */
    private static void assertBox(GameTestHelper helper, BlockPos min, int width, int height) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                for (int z = 0; z < width; z++) {
                    BlockPos pos = min.offset(x, y, z);
                    BlockState state = helper.getBlockState(pos);
                    boolean ok = state.is(BastionBlocks.FEED_HUB.get())
                            && state.getValue(FeedHubBlock.X) == FeedHubBlock.Part.of(x, width)
                            && state.getValue(FeedHubBlock.Y) == FeedHubBlock.Part.of(y, height)
                            && state.getValue(FeedHubBlock.Z) == FeedHubBlock.Part.of(z, width);
                    helper.assertTrue(ok, pos + " is " + state + ", expected part of a " + width + "x" + width + "x" + height + " from " + min);
                }
            }
        }
    }

    /** Loose hubs merge: a 2x2x1, a 2x2x2 and a 3x3x3 side by side (gaps between them). */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "feedHubForms")
    public static void feedHubForms(GameTestHelper helper) {
        hubs(helper, new BlockPos(1, 2, 1), 2, 1);
        hubs(helper, new BlockPos(4, 2, 1), 2, 2);
        hubs(helper, new BlockPos(7, 2, 1), 3, 3);
        helper.succeedWhen(() -> {
            assertBox(helper, new BlockPos(1, 2, 1), 2, 1);
            assertBox(helper, new BlockPos(4, 2, 1), 2, 2);
            assertBox(helper, new BlockPos(7, 2, 1), 3, 3);
        });
    }

    /** Breaking a corner of a 3x3x1 re-forms the rest: a 2x2x1 from the lowest corner, the four others alone. */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "feedHubSplits")
    public static void feedHubSplits(GameTestHelper helper) {
        BlockPos min = new BlockPos(4, 2, 1);
        hubs(helper, min, 3, 1);
        helper.runAtTickTime(5, () -> {
            assertBox(helper, min, 3, 1);
            helper.destroyBlock(min.offset(2, 0, 2));
        });
        helper.runAfterDelay(6, () -> helper.succeedWhen(() -> {
            assertBox(helper, min, 2, 1);
            for (BlockPos alone : List.of(min.offset(2, 0, 0), min.offset(2, 0, 1), min.offset(0, 0, 2), min.offset(1, 0, 2))) {
                assertBox(helper, alone, 1, 1);
            }
        }));
    }

    private static IItemHandler items(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockEntity(pos).getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElseThrow(IllegalStateException::new);
    }

    private static TurretBaseBlockEntity large(GameTestHelper helper, BlockPos min, Item weapon) {
        LargeTurretBaseBlock.placeAt(helper.getLevel(), helper.absolutePos(min), BastionBlocks.LARGE_TURRET_BASE.get());
        TurretBaseBlockEntity base = (TurretBaseBlockEntity) helper.getBlockEntity(min);
        base.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(weapon));
        return base;
    }

    /**
     * Slots add up (2x2x1: 36, 3x3x3: 243), any block reaches them all, and a handler taken before the hubs merged
     * (a pipe's cached capability) sees the merged structure.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "feedHubSharesStorage")
    public static void feedHubSharesStorage(GameTestHelper helper) {
        BlockPos small = new BlockPos(2, 2, 1), big = new BlockPos(5, 2, 1); // the gun goes on small.west() (x 1)
        helper.setBlock(small, BastionBlocks.FEED_HUB.get());
        IItemHandler early = items(helper, small);
        hubs(helper, small, 2, 1);
        hubs(helper, big, 3, 3);
        base(helper, small.west(), Direction.WEST, BastionItems.GUN_TURRET.get());
        helper.runAfterDelay(2, () -> helper.succeedWhen(() -> {
            helper.assertTrue(early.getSlots() == 36, "early handler sees " + early.getSlots() + " slots");
            helper.assertTrue(items(helper, big.offset(2, 2, 2)).getSlots() == 243, "3x3x3 slots: " + items(helper, big.offset(2, 2, 2)).getSlots());
            IItemHandler far = items(helper, small.offset(1, 0, 1)); // not the block the gun sits on
            helper.assertTrue(ItemHandlerHelper.insertItem(far, new ItemStack(BastionItems.KINETIC_ROUNDS.get(), 64), true).isEmpty(),
                    "the far block refused rounds the gun on its structure fires");
        }));
    }

    /**
     * A large base on all four blocks of a 2x2x1 gets its shells; one standing half on another 2x2x1 gets none. (A Mortar,
     * which only uses shells when it fires: an idle Missile Launcher loads its tubes from its slots.)
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "feedHubFeedsLargeBase")
    public static void feedHubFeedsLargeBase(GameTestHelper helper) {
        BlockPos fed = new BlockPos(1, 2, 1), half = new BlockPos(6, 2, 1);
        hubs(helper, fed, 2, 1);
        hubs(helper, half, 2, 1);
        TurretBaseBlockEntity on = large(helper, fed.above(), BastionItems.MORTAR_TURRET.get());
        TurretBaseBlockEntity off = large(helper, half.above().east(), BastionItems.MORTAR_TURRET.get());
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(ItemHandlerHelper.insertItem(items(helper, fed), new ItemStack(BastionItems.MORTAR_SHELLS.get(), 8), false).isEmpty(),
                    "refused shells for the large base on top");
            helper.assertTrue(ItemHandlerHelper.insertItem(items(helper, half), new ItemStack(BastionItems.MORTAR_SHELLS.get(), 8), false).getCount() == 8,
                    "took shells for a base standing half off the structure");
        });
        helper.runAfterDelay(4, () -> helper.succeedWhen(() -> {
            helper.assertTrue(ammo(on, BastionItems.MORTAR_SHELLS.get()) == 8, "the large base got " + ammo(on, BastionItems.MORTAR_SHELLS.get()));
            helper.assertTrue(ammo(off, BastionItems.MORTAR_SHELLS.get()) == 0, "fed the base standing half off");
        }));
    }

    /** Breaking one block of a 2x2x1 drops only that block's slots; the others keep theirs and stand alone. */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "feedHubDropsOwnShare")
    public static void feedHubDropsOwnShare(GameTestHelper helper) {
        BlockPos min = new BlockPos(4, 2, 2);
        hubs(helper, min, 2, 1);
        helper.runAtTickTime(3, () -> {
            ((FeedHubBlockEntity) helper.getBlockEntity(min)).buffer().setStackInSlot(0, new ItemStack(BastionItems.KINETIC_ROUNDS.get(), 10));
            ((FeedHubBlockEntity) helper.getBlockEntity(min.offset(1, 0, 1))).buffer().setStackInSlot(0, new ItemStack(BastionItems.SCATTER_SHELLS.get(), 3));
            helper.destroyBlock(min.offset(1, 0, 1));
        });
        helper.runAfterDelay(4, () -> helper.succeedWhen(() -> {
            int shells = 0, rounds = 0;
            for (ItemEntity drop : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(min)).inflate(3))) {
                if (drop.getItem().is(BastionItems.SCATTER_SHELLS.get())) shells += drop.getItem().getCount();
                if (drop.getItem().is(BastionItems.KINETIC_ROUNDS.get())) rounds += drop.getItem().getCount();
            }
            helper.assertTrue(shells == 3 && rounds == 0, "dropped " + shells + " shells, " + rounds + " rounds");
            helper.assertTrue(((FeedHubBlockEntity) helper.getBlockEntity(min)).buffer().getStackInSlot(0).getCount() == 10, "the rest lost its rounds");
            assertBox(helper, min, 1, 1);
        }));
    }

    /** Breaking a 2x2x2's lowest block re-forms its top layer as a 2x2x1 whose new lowest block feeds the gun on top. */
    @GameTest(template = ARENA, timeoutTicks = 80, batch = "feedHubNewControllerFeeds")
    public static void feedHubNewControllerFeeds(GameTestHelper helper) {
        BlockPos min = new BlockPos(4, 2, 2);
        hubs(helper, min, 2, 2);
        TurretBaseBlockEntity gun = base(helper, min.offset(1, 2, 1), Direction.UP, BastionItems.GUN_TURRET.get());
        helper.runAtTickTime(3, () -> helper.destroyBlock(min));
        helper.runAtTickTime(5, () -> {
            assertBox(helper, min.above(), 2, 1);
            helper.assertTrue(ItemHandlerHelper.insertItem(items(helper, min.above()), new ItemStack(BastionItems.KINETIC_ROUNDS.get(), 16), false).isEmpty(),
                    "the re-formed top layer refused rounds");
        });
        helper.runAfterDelay(6, () -> helper.succeedWhen(() ->
                helper.assertTrue(ammo(gun, BastionItems.KINETIC_ROUNDS.get()) == 16, "the gun got " + ammo(gun, BastionItems.KINETIC_ROUNDS.get()))));
    }

    /** A 2x2x1 stores 4 x the per-block FE, takes 4 x the per-block input per tick, and charges the Tesla Coil on top. */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "feedHubPowersTesla")
    public static void feedHubPowersTesla(GameTestHelper helper) {
        BlockPos min = new BlockPos(4, 2, 2);
        hubs(helper, min, 2, 1);
        TurretBaseBlockEntity tesla = large(helper, min.above(), BastionItems.TESLA_TURRET.get());
        int input = 4 * BastionConfig.FEED_HUB_MAX_INPUT.get();
        helper.runAtTickTime(3, () -> {
            IEnergyStorage hub = helper.getBlockEntity(min.offset(1, 0, 1)).getCapability(ForgeCapabilities.ENERGY, Direction.EAST)
                    .orElseThrow(IllegalStateException::new);
            helper.assertTrue(hub.getMaxEnergyStored() == 4 * BastionConfig.FEED_HUB_ENERGY_CAPACITY.get(), "capacity " + hub.getMaxEnergyStored());
            helper.assertTrue(hub.receiveEnergy(Integer.MAX_VALUE, false) == input, "took more or less than its per-tick input");
            helper.assertTrue(hub.receiveEnergy(1, false) == 0, "took FE past its per-tick input");
        });
        helper.runAfterDelay(4, () -> helper.succeedWhen(() -> {
            IEnergyStorage hub = helper.getBlockEntity(min).getCapability(ForgeCapabilities.ENERGY, Direction.UP).orElseThrow(IllegalStateException::new);
            helper.assertTrue(tesla.energy() > 0, "the Tesla Coil got no FE");
            helper.assertTrue(tesla.energy() + hub.getEnergyStored() == input, "FE lost or made: " + tesla.energy() + " + " + hub.getEnergyStored());
        }));
    }

    /**
     * The GUI's server side on a 3x3x1 (81 slots, 9 rows): 4 rows show, scrolling one row shows slot 40 in the window,
     * shift-click from the inventory fills the structure (not just the rows on screen), the summary totals the ammo.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "feedHubMenuScrolls")
    public static void feedHubMenuScrolls(GameTestHelper helper) {
        BlockPos min = new BlockPos(4, 2, 1);
        hubs(helper, min, 3, 1);
        base(helper, min.west(), Direction.WEST, BastionItems.GUN_TURRET.get()).setEnabled(false);
        helper.runAtTickTime(3, () -> {
            FeedHubBlockEntity hub = (FeedHubBlockEntity) helper.getBlockEntity(min.offset(1, 0, 1));
            hub.storage().setStackInSlot(40, new ItemStack(BastionItems.SCATTER_SHELLS.get(), 5));
            FakePlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
            player.getInventory().setItem(0, new ItemStack(BastionItems.KINETIC_ROUNDS.get(), 20));
            FeedHubMenu menu = new FeedHubMenu(0, player.getInventory(), hub);
            helper.assertTrue(menu.maxScroll() == 5, "max scroll " + menu.maxScroll());
            helper.assertTrue(menu.slots.get(31).getItem().isEmpty(), "row 4 shown before scrolling");
            menu.clickMenuButton(player, 1);
            helper.assertTrue(menu.slots.get(31).getItem().is(BastionItems.SCATTER_SHELLS.get()), "slot 40 not in the window after scrolling one row");
            int hotbar = FeedHubMenu.VISIBLE + 27; // first hotbar slot
            menu.clickMenuButton(player, 5);
            menu.quickMoveStack(player, hotbar);
            helper.assertTrue(player.getInventory().getItem(0).isEmpty(), "shift-click left " + player.getInventory().getItem(0));
            helper.assertTrue(hub.storage().getStackInSlot(0).is(BastionItems.KINETIC_ROUNDS.get()), "shift-click did not fill the structure's first slot");
            List<FeedHubMenu.AmmoTotal> ammo = menu.ammo();
            helper.assertTrue(ammo.size() == 2 && ammo.get(0).count() == 20 && ammo.get(1).count() == 5, "summary " + ammo);
            helper.succeed();
        });
    }

    /** Shift-clicking only part of a stack out of Storage (inventory nearly full) still marks the hub to be saved. */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "feedHubPartialShiftClickSaves")
    public static void feedHubPartialShiftClickSaves(GameTestHelper helper) {
        BlockPos pos = new BlockPos(4, 2, 2);
        helper.setBlock(pos, BastionBlocks.FEED_HUB.get());
        helper.runAtTickTime(3, () -> {
            FeedHubBlockEntity hub = (FeedHubBlockEntity) helper.getBlockEntity(pos);
            hub.buffer().setStackInSlot(0, new ItemStack(BastionItems.KINETIC_ROUNDS.get(), 64));
            FakePlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
            Inventory inventory = player.getInventory();
            for (int i = 0; i < 36; i++) inventory.setItem(i, new ItemStack(Items.STONE, 64));
            inventory.setItem(0, new ItemStack(BastionItems.KINETIC_ROUNDS.get(), 54)); // room for 10
            FeedHubMenu menu = new FeedHubMenu(0, inventory, hub);
            LevelChunk chunk = helper.getLevel().getChunkAt(helper.absolutePos(pos));
            chunk.setUnsaved(false);
            menu.quickMoveStack(player, 0);
            int left = hub.buffer().getStackInSlot(0).getCount();
            inventory.clearContent();
            helper.assertTrue(left == 54, "moved " + (64 - left) + " instead of 10");
            helper.assertTrue(chunk.isUnsaved(), "a partial shift-click did not mark the hub to be saved (the stack comes back on reload)");
            helper.succeed();
        });
    }

    /**
     * Two 2x2x1 side by side; the first loses its lowest block. Until the re-form a tick later its other blocks must
     * not take the neighbouring 2x2x1 for part of their structure: they work alone instead.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "feedHubStaleStateStaysApart")
    public static void feedHubStaleStateStaysApart(GameTestHelper helper) {
        BlockPos min = new BlockPos(3, 2, 2);
        hubs(helper, min, 2, 1);
        hubs(helper, min.east(2), 2, 1);
        helper.runAtTickTime(3, () -> {
            assertBox(helper, min, 2, 1);
            assertBox(helper, min.east(2), 2, 1);
            helper.setBlock(min, Blocks.AIR);
            FeedHubBlockEntity left = (FeedHubBlockEntity) helper.getBlockEntity(min.east());
            int reached = left.group().members().size();
            helper.assertTrue(reached == 1, "before the re-form the block reaches " + reached + " blocks");
            helper.succeed();
        });
    }

    /** A turret removed after the hub looked at its structure this tick gets nothing pushed into it (no rounds lost). */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "feedHubSkipsRemovedTurret")
    public static void feedHubSkipsRemovedTurret(GameTestHelper helper) {
        BlockPos pos = new BlockPos(4, 2, 2);
        helper.setBlock(pos, BastionBlocks.FEED_HUB.get());
        base(helper, pos.east(), Direction.EAST, BastionItems.GUN_TURRET.get());
        helper.runAtTickTime(3, () -> {
            FeedHubBlockEntity hub = (FeedHubBlockEntity) helper.getBlockEntity(pos);
            hub.buffer().setStackInSlot(0, new ItemStack(BastionItems.KINETIC_ROUNDS.get(), 8));
            helper.assertTrue(hub.feeds().size() == 1, "the gun is not on the hub");
            helper.setBlock(pos.east(), Blocks.AIR);
            FeedHubBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(pos), hub.getBlockState(), hub);
            int left = hub.buffer().getStackInSlot(0).getCount();
            helper.assertTrue(left == 8, "pushed " + (8 - left) + " rounds into a removed turret");
            helper.succeed();
        });
    }

    /** Scrolled to the bottom of a 3x3x1 (9 rows) when it shrinks to a 2x2x1 (4 rows): the GUI follows to the first row. */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "feedHubScrollFollowsShrink")
    public static void feedHubScrollFollowsShrink(GameTestHelper helper) {
        BlockPos min = new BlockPos(4, 2, 1);
        hubs(helper, min, 3, 1);
        FeedHubMenu[] menu = new FeedHubMenu[1];
        helper.runAtTickTime(3, () -> {
            FakePlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
            menu[0] = new FeedHubMenu(0, player.getInventory(), (FeedHubBlockEntity) helper.getBlockEntity(min));
            menu[0].clickMenuButton(player, 5);
            helper.assertTrue(menu[0].scroll() == 5, "did not scroll to the last row");
            helper.destroyBlock(min.offset(2, 0, 2));
        });
        helper.runAtTickTime(6, () -> {
            assertBox(helper, min, 2, 1);
            ((FeedHubBlockEntity) helper.getBlockEntity(min)).buffer().setStackInSlot(0, new ItemStack(BastionItems.KINETIC_ROUNDS.get(), 7));
            helper.assertTrue(menu[0].scroll() == 0, "still scrolled to row " + menu[0].scroll() + " of a 4-row structure");
            helper.assertTrue(menu[0].slots.get(0).getItem().getCount() == 7, "the first slot does not show the structure's first slot");
            helper.succeed();
        });
    }

    /**
     * Idle cost: a full 3x3x3 (243 stacks) with 27 full guns, 200 distribution ticks. Every stack is ammo nobody has room
     * for, so a tick should cost one check per kind of ammo, not one per stack and turret.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "feedHubIdleIsCheap")
    public static void feedHubIdleIsCheap(GameTestHelper helper) {
        BlockPos min = new BlockPos(5, 2, 1);
        hubs(helper, min, 3, 3);
        List<TurretBaseBlockEntity> guns = new ArrayList<>();
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                guns.add(base(helper, min.offset(x, 3, z), Direction.UP, BastionItems.GUN_TURRET.get()));
                guns.add(base(helper, min.offset(-1, x, z), Direction.WEST, BastionItems.GUN_TURRET.get()));
                guns.add(base(helper, min.offset(3, x, z), Direction.EAST, BastionItems.GUN_TURRET.get()));
            }
        }
        helper.runAtTickTime(3, () -> {
            for (TurretBaseBlockEntity gun : guns) {
                gun.setEnabled(false);
                for (int i = 0; i < gun.inventory().ammoSlots(); i++) {
                    gun.inventory().setStackInSlot(TurretInventory.AMMO_START + i, new ItemStack(BastionItems.KINETIC_ROUNDS.get(), 64));
                }
            }
            FeedHubBlockEntity hub = (FeedHubBlockEntity) helper.getBlockEntity(min);
            for (int i = 0; i < hub.storage().getSlots(); i++) hub.storage().setStackInSlot(i, new ItemStack(BastionItems.KINETIC_ROUNDS.get(), 64));
            helper.assertTrue(hub.storage().getSlots() == 243 && hub.feeds().size() == 27, "setup: " + hub.storage().getSlots() + " slots, " + hub.feeds().size() + " turrets");
            long start = System.nanoTime();
            for (int i = 0; i < 200; i++) FeedHubBlockEntity.serverTick(helper.getLevel(), helper.absolutePos(min), hub.getBlockState(), hub);
            long ms = (System.nanoTime() - start) / 1_000_000;
            Bastion.LOGGER.info("[feedHubIdleIsCheap] 200 full ticks took {} ms", ms);
            helper.assertTrue(ms < 40, "200 idle ticks of a full 3x3x3 with 27 full turrets took " + ms + " ms");
            helper.succeed();
        });
    }
}

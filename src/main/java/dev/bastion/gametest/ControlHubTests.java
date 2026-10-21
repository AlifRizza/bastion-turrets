package dev.bastion.gametest;

import dev.bastion.Bastion;
import dev.bastion.hub.ControlHubStructure;
import dev.bastion.hub.ControlHubStructure.Kind;
import dev.bastion.hub.FilterMix;
import dev.bastion.hub.HubCasingBlock;
import dev.bastion.hub.HubConnectorItem;
import dev.bastion.hub.HubEdit;
import dev.bastion.hub.HubInterfaceBlock;
import dev.bastion.hub.HubInterfaceBlock.Status;
import dev.bastion.hub.HubInterfaceBlockEntity;
import dev.bastion.hub.SatelliteUplinkBlock;
import dev.bastion.menu.ControlHubMenu;
import dev.bastion.menu.TurretMenu;
import dev.bastion.network.HubAction;
import dev.bastion.network.HubRoster;
import dev.bastion.network.TurretConfigUpdate;
import dev.bastion.registry.BastionBlocks;
import dev.bastion.registry.BastionItems;
import dev.bastion.turret.FeedHubBlock.Part;
import dev.bastion.turret.LargeTurretBaseBlock;
import dev.bastion.turret.TurretBaseBlock;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretInventory;
import dev.bastion.turret.TurretTier;
import dev.bastion.turret.targeting.TargetFilter;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
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
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Turret Control Hub (PLAN "Turret Control Hub"): layout, forming, energy, reach, remote turret GUIs, batch settings. */
@GameTestHolder(Bastion.MOD_ID)
@PrefixGameTestTemplate(false)
public class ControlHubTests {
    private static final String ARENA = "arena", YARD = "yard", EMPTY = "empty";
    /** Arena: the Interface faces north in the middle layer (y 2); the cube runs south (z 3..5) from y 1 to 3; the satellite stands on (6, 4, 4). */
    private static final BlockPos IFACE = new BlockPos(6, 2, 3);

    static SatelliteUplinkBlock satellite(int tier) {
        return (tier == 3 ? BastionBlocks.SATELLITE_UPLINK_T3 : tier == 2 ? BastionBlocks.SATELLITE_UPLINK_T2 : BastionBlocks.SATELLITE_UPLINK_T1).get();
    }

    /**
     * A cube hub with its Interface at absolute {@code iface} facing {@code facing}: casings, a Power Port in the first slot
     * (bottom layer, front row, left as seen from in front) and, unless {@code tier} is 0, a satellite of that tier on top.
     */
    static HubInterfaceBlockEntity hub(ServerLevel level, BlockPos iface, Direction facing, int tier) {
        level.setBlock(iface, BastionBlocks.HUB_INTERFACE.get().defaultBlockState().setValue(HubInterfaceBlock.FACING, facing), 3);
        List<BlockPos> slots = ControlHubStructure.slots(iface, facing);
        for (int i = 0; i < slots.size(); i++) {
            level.setBlock(slots.get(i), (i == 0 ? BastionBlocks.HUB_POWER_PORT : BastionBlocks.HUB_CASING).get().defaultBlockState(), 3);
        }
        if (tier > 0) satellite(tier).placeAt(level, ControlHubStructure.center(iface, facing).above(2));
        return (HubInterfaceBlockEntity) level.getBlockEntity(iface);
    }

    /** A hub facing north with its Interface at test position {@code iface}. */
    static HubInterfaceBlockEntity hub(GameTestHelper helper, BlockPos iface, int tier) {
        return hub(helper.getLevel(), helper.absolutePos(iface), Direction.NORTH, tier);
    }

    private static void assertFirst(GameTestHelper helper, HubInterfaceBlockEntity hub, Kind kind, BlockPos at) {
        List<ControlHubStructure.Issue> issues = ControlHubStructure.check(helper.getLevel(), hub.getBlockPos(), Direction.NORTH).issues();
        helper.assertTrue(!issues.isEmpty() && issues.get(0).kind() == kind && issues.get(0).pos().equals(at),
                "expected " + kind + " at " + at + " first, got " + issues);
    }

    /** Interface, 25 casings and a Power Port, a satellite on top: formed a tick later (OFFLINE: no FE), every part knows its place. */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "hubFormsWhenComplete")
    public static void hubFormsWhenComplete(GameTestHelper helper) {
        HubInterfaceBlockEntity hub = hub(helper, IFACE, 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(hub.status() == Status.OFFLINE, "expected formed without FE (OFFLINE), got " + hub.status());
            helper.assertTrue(hub.tier() == 1, "tier " + hub.tier());
            BlockPos center = hub.center();
            List<BlockPos> slots = ControlHubStructure.slots(hub.getBlockPos(), Direction.NORTH);
            helper.assertTrue(slots.size() == 26, "a cube has 26 parts besides the Interface, got " + slots.size());
            BlockPos iface = hub.getBlockPos(); // facing north: left as seen from in front is east, the back is south
            helper.assertTrue(slots.get(0).equals(iface.below().east()) && slots.get(10).equals(iface.west()) && slots.get(12).equals(center)
                    && slots.get(21).equals(center.above()) && slots.get(25).equals(center.above().south().west()),
                    "slots 0, 10, 12, 21, 25 should be bottom-front-left, middle-front-right, core, top middle, top-back-right: " + slots);
            for (BlockPos pos : slots) {
                BlockState state = helper.getLevel().getBlockState(pos);
                helper.assertTrue(state.getValue(HubCasingBlock.X) == Part.of(pos.getX() - center.getX() + 1, 3)
                        && state.getValue(HubCasingBlock.Y) == Part.of(pos.getY() - center.getY() + 1, 3)
                        && state.getValue(HubCasingBlock.Z) == Part.of(pos.getZ() - center.getZ() + 1, 3), "wrong place at " + pos + ": " + state);
            }
            helper.assertTrue(helper.getLevel().getBlockState(center.above(2)).getValue(SatelliteUplinkBlock.STATUS) == Status.OFFLINE,
                    "the satellite on top did not follow the hub");
        });
    }

    /**
     * Five broken cubes in a row: no satellite, satellite on a top corner, stone in the hidden core, no Power Port, stone in
     * the top and the bottom layer (the bottom one named first). None forms.
     */
    @GameTest(template = YARD, timeoutTicks = 60, batch = "hubNamesTheFirstProblem")
    public static void hubNamesTheFirstProblem(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HubInterfaceBlockEntity noSatellite = hub(helper, new BlockPos(3, 2, 3), 0);
        HubInterfaceBlockEntity offCentre = hub(helper, new BlockPos(9, 2, 3), 0);
        // On the top layer's back-right corner instead of its middle.
        satellite(1).placeAt(level, ControlHubStructure.slots(offCentre.getBlockPos(), Direction.NORTH).get(25).above());
        HubInterfaceBlockEntity stone = hub(helper, new BlockPos(15, 2, 3), 1);
        level.setBlock(stone.center(), Blocks.STONE.defaultBlockState(), 3);
        HubInterfaceBlockEntity noPort = hub(helper, new BlockPos(21, 2, 3), 1);
        level.setBlock(ControlHubStructure.slots(noPort.getBlockPos(), Direction.NORTH).get(0), BastionBlocks.HUB_CASING.get().defaultBlockState(), 3);
        HubInterfaceBlockEntity layers = hub(helper, new BlockPos(27, 2, 3), 1);
        // Placed by coordinates, not through slots(), so the order is checked against the spec rather than against itself.
        BlockPos bottomStone = layers.center().below().south().west(), topStone = layers.center().above().south().east();
        level.setBlock(topStone, Blocks.STONE.defaultBlockState(), 3); // top layer, back row, left
        level.setBlock(bottomStone, Blocks.STONE.defaultBlockState(), 3); // bottom layer, back row, right
        helper.runAtTickTime(5, () -> {
            assertFirst(helper, noSatellite, Kind.NEED_SATELLITE, noSatellite.center().above(2));
            assertFirst(helper, offCentre, Kind.NEED_SATELLITE, offCentre.center().above(2));
            assertFirst(helper, stone, Kind.WRONG, stone.center());
            List<ControlHubStructure.Issue> issues = ControlHubStructure.check(level, noPort.getBlockPos(), Direction.NORTH).issues();
            helper.assertTrue(issues.size() == 26 && issues.stream().allMatch(i -> i.kind() == Kind.NEED_PORT), "no port: " + issues);
            List<ControlHubStructure.Issue> stones = ControlHubStructure.check(level, layers.getBlockPos(), Direction.NORTH).issues();
            helper.assertTrue(stones.equals(List.of(new ControlHubStructure.Issue(bottomStone, Kind.WRONG),
                    new ControlHubStructure.Issue(topStone, Kind.WRONG))), "stone in the bottom and top layers, bottom first: " + stones);
            for (HubInterfaceBlockEntity hub : List.of(noSatellite, offCentre, stone, noPort, layers)) {
                helper.assertTrue(hub.status() == Status.UNFORMED, "formed anyway at " + hub.getBlockPos());
            }
            helper.succeed();
        });
    }

    /** A second cube built against a formed one shares the first one's x 6 wall (3 rows x 3 layers): those count as another hub's, it never forms. */
    @GameTest(template = YARD, timeoutTicks = 80, batch = "partOfAnotherHubIsWrong")
    public static void partOfAnotherHubIsWrong(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HubInterfaceBlockEntity first = hub(helper, new BlockPos(5, 2, 3), 1);
        BlockPos second = helper.absolutePos(new BlockPos(7, 2, 3)); // its cube spans x 6..8: the first one's x 6 wall is shared
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(first.formed(), "the first hub did not form");
            level.setBlock(second, BastionBlocks.HUB_INTERFACE.get().defaultBlockState().setValue(HubInterfaceBlock.FACING, Direction.NORTH), 3);
            List<BlockPos> slots = ControlHubStructure.slots(second, Direction.NORTH);
            for (int i = 0; i < slots.size(); i++) { // the bottom layer replaces the floor; the shared wall stays the first hub's
                if (!(level.getBlockState(slots.get(i)).getBlock() instanceof HubCasingBlock)) {
                    level.setBlock(slots.get(i), (i == 0 ? BastionBlocks.HUB_POWER_PORT : BastionBlocks.HUB_CASING).get().defaultBlockState(), 3);
                }
            }
            satellite(1).placeAt(level, ControlHubStructure.center(second, Direction.NORTH).above(2));
        });
        helper.runAtTickTime(15, () -> {
            HubInterfaceBlockEntity hub = (HubInterfaceBlockEntity) level.getBlockEntity(second);
            List<ControlHubStructure.Issue> issues = ControlHubStructure.check(level, second, Direction.NORTH).issues();
            helper.assertTrue(issues.size() == 9 && issues.stream().allMatch(i -> i.kind() == Kind.OTHER_HUB),
                    "expected the 9 shared blocks as another hub's: " + issues);
            helper.assertTrue(!hub.formed() && first.formed(), "second formed: " + hub.formed() + ", first still formed: " + first.formed());
            helper.succeed();
        });
    }

    /**
     * A part of the bottom layer, then one of the top layer, then the satellite broken: each time the hub breaks up within a
     * tick and its parts forget their places; put back, it forms again.
     */
    @GameTest(template = ARENA, timeoutTicks = 80, batch = "breakingAPartUnformsTheHub")
    public static void breakingAPartUnformsTheHub(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HubInterfaceBlockEntity hub = hub(helper, IFACE, 1);
        List<BlockPos> slots = ControlHubStructure.slots(hub.getBlockPos(), Direction.NORTH);
        BlockPos bottom = slots.get(2), top = slots.get(23), satellite = hub.center().above(2);
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(hub.formed(), "did not form");
            level.removeBlock(bottom, false);
        });
        helper.runAtTickTime(7, () -> {
            helper.assertTrue(hub.status() == Status.UNFORMED, "still formed after a bottom-layer part was broken");
            for (BlockPos pos : slots) {
                BlockState state = level.getBlockState(pos);
                helper.assertTrue(!(state.getBlock() instanceof HubCasingBlock) || state.getValue(HubCasingBlock.X) == Part.ALONE,
                        "part still formed at " + pos);
            }
            level.setBlock(bottom, BastionBlocks.HUB_CASING.get().defaultBlockState(), 3);
        });
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(hub.formed(), "did not form again with the part back");
            level.removeBlock(top, false);
        });
        helper.runAtTickTime(12, () -> {
            helper.assertTrue(hub.status() == Status.UNFORMED, "still formed after a top-layer part was broken");
            level.setBlock(top, BastionBlocks.HUB_CASING.get().defaultBlockState(), 3);
        });
        helper.runAtTickTime(15, () -> {
            helper.assertTrue(hub.formed(), "did not form again with the top part back");
            level.removeBlock(satellite, false);
        });
        helper.runAtTickTime(17, () -> {
            helper.assertTrue(hub.status() == Status.UNFORMED, "still formed after the satellite was broken");
            helper.succeed();
        });
    }

    /**
     * Breaking the Interface frees the parts and the satellite. A part still marked by a hub that is gone (set by hand here)
     * does not block a new Interface.
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "brokenInterfaceFreesItsParts")
    public static void brokenInterfaceFreesItsParts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HubInterfaceBlockEntity hub = hub(helper, IFACE, 1);
        BlockPos iface = hub.getBlockPos(), center = hub.center();
        List<BlockPos> slots = ControlHubStructure.slots(iface, Direction.NORTH);
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(hub.formed(), "did not form");
            level.removeBlock(iface, false);
        });
        helper.runAtTickTime(7, () -> {
            for (BlockPos pos : slots) helper.assertTrue(level.getBlockState(pos).getValue(HubCasingBlock.X) == Part.ALONE, "still formed at " + pos);
            helper.assertTrue(level.getBlockState(center.above(2)).getValue(SatelliteUplinkBlock.STATUS) == Status.UNFORMED, "satellite still formed");
            BlockPos stale = slots.get(3);
            level.setBlock(stale, level.getBlockState(stale).setValue(HubCasingBlock.X, Part.LOW).setValue(HubCasingBlock.Y, Part.LOW)
                    .setValue(HubCasingBlock.Z, Part.LOW), 2);
            level.setBlock(iface, BastionBlocks.HUB_INTERFACE.get().defaultBlockState().setValue(HubInterfaceBlock.FACING, Direction.NORTH), 3);
        });
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(level.getBlockEntity(iface) instanceof HubInterfaceBlockEntity again && again.formed(),
                    "a part marked by a vanished hub blocked the new one");
            helper.succeed();
        });
    }

    /** A cube whose columns straddle a chunk border forms like any other. */
    @GameTest(template = YARD, timeoutTicks = 60, batch = "hubAcrossChunkBorder")
    public static void hubAcrossChunkBorder(GameTestHelper helper) {
        int border = Math.floorMod(-helper.absolutePos(BlockPos.ZERO).getX(), 16); // first test x where a chunk starts
        if (border < 2) border += 16;
        HubInterfaceBlockEntity hub = hub(helper, new BlockPos(border, 2, 3), 1); // columns border-1 .. border+1
        helper.succeedWhen(() -> helper.assertTrue(hub.formed(), "a hub over a chunk border did not form"));
    }

    /** Breaking the satellite's top half takes the bottom too, and the satellite drops once. */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "satelliteHalvesBreakTogether")
    public static void satelliteHalvesBreakTogether(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos bottom = helper.absolutePos(new BlockPos(6, 2, 3));
        AABB area = new AABB(bottom).inflate(3);
        level.getEntitiesOfClass(ItemEntity.class, area).forEach(Entity::discard); // left over from an earlier batch
        satellite(2).placeAt(level, bottom);
        level.destroyBlock(bottom.above(), true);
        helper.runAtTickTime(2, () -> {
            helper.assertTrue(level.getBlockState(bottom).isAir() && level.getBlockState(bottom.above()).isAir(), "a half stayed");
            List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, area);
            helper.assertTrue(drops.size() == 1 && drops.get(0).getItem().is(BastionItems.SATELLITE_UPLINK_T2.get()), "drops: " + drops);
            drops.forEach(Entity::discard);
            helper.succeed();
        });
    }

    private static IEnergyStorage energy(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos).getCapability(ForgeCapabilities.ENERGY, Direction.UP).orElseThrow(IllegalStateException::new);
    }

    /**
     * A formed hub pays its tier's upkeep every tick: 20, 60, 150 FE (config defaults). Counted over 20 ticks from when all
     * three are online: a hub across a chunk that loads late forms at its 20-tick re-check, not a tick after placing.
     */
    @GameTest(template = YARD, timeoutTicks = 80, batch = "hubPaysUpkeepPerTier")
    public static void hubPaysUpkeepPerTier(GameTestHelper helper) {
        HubInterfaceBlockEntity[] hubs = {hub(helper, new BlockPos(5, 2, 3), 1), hub(helper, new BlockPos(12, 2, 3), 2), hub(helper, new BlockPos(19, 2, 3), 3)};
        for (HubInterfaceBlockEntity hub : hubs) hub.setEnergy(100_000);
        int[] start = new int[3];
        long[] from = {-1};
        helper.onEachTick(() -> {
            if (from[0] < 0) {
                if (!hubs[0].online() || !hubs[1].online() || !hubs[2].online()) return;
                for (int i = 0; i < 3; i++) start[i] = hubs[i].energy();
                from[0] = helper.getTick();
            } else if (helper.getTick() == from[0] + 20) {
                int[] cost = {20, 60, 150};
                for (int i = 0; i < 3; i++) {
                    int used = start[i] - hubs[i].energy();
                    helper.assertTrue(hubs[i].online() && used == 20 * cost[i], "T" + (i + 1) + " used " + used + " FE in 20 ticks, expected " + 20 * cost[i]);
                }
                helper.succeed();
            }
        });
    }

    /** Ten ticks of FE: online, then OFFLINE (satellite too); FE through the Power Port brings it back. */
    @GameTest(template = ARENA, timeoutTicks = 80, batch = "hubGoesOfflineWithoutFe")
    public static void hubGoesOfflineWithoutFe(GameTestHelper helper) {
        HubInterfaceBlockEntity hub = hub(helper, IFACE, 1);
        hub.setEnergy(200);
        IEnergyStorage port = energy(helper.getLevel(), ControlHubStructure.slots(hub.getBlockPos(), Direction.NORTH).get(0));
        helper.runAtTickTime(4, () -> helper.assertTrue(hub.online(), "not online with FE"));
        helper.runAtTickTime(30, () -> {
            helper.assertTrue(hub.status() == Status.OFFLINE && hub.energy() < 20, "still " + hub.status() + " with " + hub.energy() + " FE");
            helper.assertTrue(port.receiveEnergy(2000, false) == 2000, "the port refused FE");
        });
        helper.runAtTickTime(32, () -> {
            helper.assertTrue(hub.online(), "not back online once FE came in");
            helper.assertTrue(helper.getLevel().getBlockState(hub.center().above(2)).getValue(SatelliteUplinkBlock.STATUS) == Status.ONLINE,
                    "the satellite did not follow");
            helper.succeed();
        });
    }

    /** A port in no hub takes nothing; a formed hub's port takes 2,000 FE per tick at most, and never past the capacity. */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "powerPortTakesFeOnlyWhenFormed")
    public static void powerPortTakesFeOnlyWhenFormed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos lone = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlock(lone, BastionBlocks.HUB_POWER_PORT.get().defaultBlockState(), 3);
        IEnergyStorage alone = energy(level, lone);
        HubInterfaceBlockEntity hub = hub(helper, IFACE, 1);
        IEnergyStorage port = energy(level, ControlHubStructure.slots(hub.getBlockPos(), Direction.NORTH).get(0));
        helper.assertTrue(alone.receiveEnergy(1000, false) == 0, "a port outside a hub took FE");
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(hub.formed(), "did not form");
            int first = port.receiveEnergy(5000, false), second = port.receiveEnergy(5000, false);
            helper.assertTrue(first == 2000 && second == 0, "one tick took " + first + " then " + second + " (expected 2000, 0)");
        });
        helper.runAtTickTime(6, () -> {
            hub.setEnergy(hub.capacity() - 100);
            helper.assertTrue(port.receiveEnergy(5000, false) == 100, "took past the capacity");
            helper.succeed();
        });
    }

    /** Every hub part has its Module Workstation recipe, in the hub's own tab. */
    @GameTest(template = EMPTY, batch = "hubPartRecipesExist")
    public static void hubPartRecipesExist(GameTestHelper helper) {
        for (String item : List.of("hub_casing", "hub_power_port", "hub_interface", "satellite_uplink_t1", "satellite_uplink_t2", "satellite_uplink_t3",
                "hub_connector")) {
            ResourceLocation id = Bastion.id("workstation/module_workstation/" + item);
            helper.assertTrue(helper.getLevel().getRecipeManager().byKey(id).isPresent(), "no recipe " + id);
        }
        helper.succeed();
    }

    /**
     * Right-clicking an Interface links the whole stack to it, in this dimension only; other blocks do nothing; shift +
     * right-click clears it in the air only, never while looking at a block (a crouching player clicking a turret).
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "connectorLinksByClickingTheInterface")
    public static void connectorLinksByClickingTheInterface(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HubInterfaceBlockEntity hub = hub(helper, IFACE, 1);
        BlockPos iface = hub.getBlockPos();
        FakePlayer player = player(helper, new BlockPos(6, 1, 1));
        ItemStack stack = new ItemStack(BastionItems.HUB_CONNECTOR.get(), 3);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        UseOnContext onInterface = new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(iface), Direction.NORTH, iface, false));
        // A plain right-click leaves it to the Interface (it opens the hub's screen, user 2026-10-21); shift links.
        InteractionResult plain = stack.onItemUseFirst(onInterface);
        boolean linkedByPlain = HubConnectorItem.isLinked(stack);
        player.setShiftKeyDown(true);
        InteractionResult result = stack.onItemUseFirst(onInterface);
        player.setShiftKeyDown(false);
        helper.assertTrue(plain == InteractionResult.PASS && !linkedByPlain, "a plain right-click linked it: " + plain);
        helper.assertTrue(result.consumesAction() && stack.getCount() == 3 && HubConnectorItem.linkedTo(stack, level, iface),
                "not linked by a click on the Interface: " + result + ", " + stack.getTag());
        helper.assertTrue(!HubConnectorItem.linkedTo(stack, level, iface.east()), "linked to another position");
        helper.assertTrue(!HubConnectorItem.linkedTo(stack, level.getServer().getLevel(Level.NETHER), iface), "linked in another dimension");
        ItemStack other = new ItemStack(BastionItems.HUB_CONNECTOR.get());
        BlockPos floor = helper.absolutePos(new BlockPos(2, 0, 2));
        helper.assertTrue(other.onItemUseFirst(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false))) == InteractionResult.PASS && !HubConnectorItem.isLinked(other),
                "a click on another block linked it");
        player.setYRot(0); // south, at the Interface 1.5 blocks ahead
        player.setXRot(0);
        player.setShiftKeyDown(true);
        stack.use(level, player, InteractionHand.MAIN_HAND);
        boolean kept = HubConnectorItem.isLinked(stack);
        Vec3 at = player.position();
        player.setPos(at.x, level.getMaxBuildHeight() - 2, at.z); // nothing above the build height: looking up is the air
        player.setXRot(-90);
        stack.use(level, player, InteractionHand.MAIN_HAND);
        boolean cleared = !HubConnectorItem.isLinked(stack);
        player.setPos(at.x, at.y, at.z); // the fake player is shared with later tests
        player.setXRot(0);
        player.setShiftKeyDown(false);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.assertTrue(kept, "shift + right-click while looking at a block cleared the link");
        helper.assertTrue(cleared, "shift + use in the air did not clear the link");
        helper.succeed();
    }

    /** The Connector slot takes Hub Connectors only (on a T1 base too), no other slot takes one, and automation can neither put one in nor take it out. */
    @GameTest(template = ARENA, batch = "connectorSlotTakesOnlyConnectors")
    public static void connectorSlotTakesOnlyConnectors(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TurretBaseBlockEntity turret = base(level, helper.absolutePos(new BlockPos(2, 1, 2)), null);
        TurretInventory inventory = turret.inventory();
        ItemStack connector = new ItemStack(BastionItems.HUB_CONNECTOR.get());
        helper.assertTrue(inventory.isItemValid(TurretInventory.CONNECTOR, connector), "a T1 base refuses a connector");
        helper.assertTrue(!inventory.isItemValid(TurretInventory.CONNECTOR, new ItemStack(BastionItems.KINETIC_ROUNDS.get())), "the Connector slot took ammo");
        for (int slot = 0; slot < TurretInventory.CONNECTOR; slot++) {
            helper.assertTrue(!inventory.isItemValid(slot, connector), "slot " + slot + " took a connector");
        }
        IItemHandler automation = turret.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElseThrow(IllegalStateException::new);
        helper.assertTrue(ItemHandlerHelper.insertItem(automation, connector.copy(), false).getCount() == 1
                && inventory.getStackInSlot(TurretInventory.CONNECTOR).isEmpty(), "automation put a connector in");
        inventory.setStackInSlot(TurretInventory.CONNECTOR, connector.copy());
        for (int slot = 0; slot < automation.getSlots(); slot++) {
            helper.assertTrue(!automation.extractItem(slot, 1, true).is(BastionItems.HUB_CONNECTOR.get()), "automation could take the connector out");
        }
        level.removeBlock(turret.getBlockPos(), false);
        helper.succeed();
    }

    /** A turret saved before the Connector slot (14 slots) loads with an empty 15th slot and everything else where it was. */
    @GameTest(template = EMPTY, batch = "oldTurretSaveGetsAConnectorSlot")
    public static void oldTurretSaveGetsAConnectorSlot(GameTestHelper helper) {
        TurretInventory old = new TurretInventory(() -> TurretTier.T3, () -> false, slot -> {});
        old.setStackInSlot(TurretInventory.WEAPON, new ItemStack(BastionItems.GUN_TURRET.get()));
        old.setStackInSlot(TurretInventory.AMMO_START + 8, new ItemStack(BastionItems.KINETIC_ROUNDS.get(), 32));
        old.setStackInSlot(TurretInventory.MODIFIER_START + 3, new ItemStack(BastionItems.RANGE_MODULE.get()));
        CompoundTag tag = old.serializeNBT();
        tag.putInt("Size", 14); // what a turret saved before this update holds
        TurretInventory loaded = new TurretInventory(() -> TurretTier.T3, () -> false, slot -> {});
        loaded.deserializeNBT(tag);
        helper.assertTrue(loaded.getSlots() == 15, "slots after loading: " + loaded.getSlots());
        helper.assertTrue(loaded.getStackInSlot(TurretInventory.WEAPON).is(BastionItems.GUN_TURRET.get())
                && loaded.getStackInSlot(TurretInventory.AMMO_START + 8).getCount() == 32
                && loaded.getStackInSlot(TurretInventory.MODIFIER_START + 3).is(BastionItems.RANGE_MODULE.get())
                && loaded.getStackInSlot(TurretInventory.CONNECTOR).isEmpty(), "contents moved while loading");
        helper.succeed();
    }

    /** A destroyed turret drops its connector with its modules (it is not lost with the ammo). */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "connectorDropsWhenTheTurretIsDestroyed")
    public static void connectorDropsWhenTheTurretIsDestroyed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(6, 1, 3));
        AABB area = new AABB(pos).inflate(6);
        level.getEntitiesOfClass(ItemEntity.class, area).forEach(Entity::discard); // left over from an earlier batch
        TurretBaseBlockEntity turret = base(level, pos, null);
        turret.inventory().setStackInSlot(TurretInventory.CONNECTOR, new ItemStack(BastionItems.HUB_CONNECTOR.get()));
        turret.takeDamage(level.damageSources().generic(), 100_000);
        helper.runAtTickTime(3, () -> {
            List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, area);
            helper.assertTrue(drops.stream().anyMatch(item -> item.getItem().is(BastionItems.HUB_CONNECTOR.get())), "no connector among " + drops);
            drops.forEach(Entity::discard);
            helper.succeed();
        });
    }

    /** The fake player standing on test position {@code rel}, no GUI open; not an operator on the GameTest server. */
    static FakePlayer player(GameTestHelper helper, BlockPos rel) {
        FakePlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(rel));
        player.setPos(at.x, at.y, at.z);
        player.containerMenu = player.inventoryMenu;
        return player;
    }

    /** An unarmed floor base at absolute {@code pos} owned by {@code owner} (null: nobody). */
    static TurretBaseBlockEntity base(ServerLevel level, BlockPos pos, @Nullable UUID owner) {
        level.setBlock(pos, BastionBlocks.TURRET_BASE.get().defaultBlockState(), 3);
        TurretBaseBlockEntity base = (TurretBaseBlockEntity) level.getBlockEntity(pos);
        base.setOwner(owner);
        return base;
    }

    /** A Hub Connector linked to {@code hub}, in the turret's Connector slot. */
    static void connect(TurretBaseBlockEntity turret, HubInterfaceBlockEntity hub) {
        turret.inventory().setStackInSlot(TurretInventory.CONNECTOR,
                HubConnectorItem.link(new ItemStack(BastionItems.HUB_CONNECTOR.get()), hub.getLevel(), hub.getBlockPos()));
    }

    /** Right-clicking a formed Interface opens its screen whatever is in hand (user 2026-10-21: it used to need an empty hand). */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "interfaceOpensWithAnythingInHand")
    public static void interfaceOpensWithAnythingInHand(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HubInterfaceBlockEntity hub = hub(helper, IFACE, 1);
        BlockPos iface = hub.getBlockPos();
        FakePlayer player = player(helper, new BlockPos(6, 1, 1));
        boolean[] done = {false};
        helper.onEachTick(() -> {
            if (done[0] || !hub.formed()) return;
            done[0] = true;
            for (ItemStack held : List.of(new ItemStack(BastionItems.TURRET_BASE.get()), new ItemStack(BastionItems.GUN_TURRET.get()),
                    new ItemStack(BastionItems.HUB_CONNECTOR.get()))) {
                player.setItemInHand(InteractionHand.MAIN_HAND, held);
                player.containerMenu = player.inventoryMenu;
                InteractionResult result = level.getBlockState(iface).use(level, player, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(iface), Direction.NORTH, iface, false));
                boolean open = player.containerMenu instanceof ControlHubMenu;
                player.containerMenu = player.inventoryMenu;
                helper.assertTrue(result.consumesAction() && open, "holding " + held.getItem() + ": " + result + ", screen open " + open);
            }
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            helper.succeed();
        });
    }

    /** The reach is a square of chunks centred on the satellite's chunk: 1, 3, 7 chunks a side. */
    @GameTest(template = EMPTY, batch = "coverageIsChunkSquare")
    public static void coverageIsChunkSquare(GameTestHelper helper) {
        BlockPos center = new BlockPos(8, 64, 8); // chunk 0, 0
        record Case(int side, int x, int z, boolean in) {
        }
        for (Case c : List.of(new Case(1, 15, 0, true), new Case(1, 16, 8, false), new Case(1, -1, 8, false),
                new Case(3, 31, -16, true), new Case(3, 32, 8, false), new Case(3, 8, -17, false),
                new Case(7, 63, -48, true), new Case(7, 64, 8, false), new Case(7, 8, -49, false))) {
            boolean in = ControlHubStructure.covers(center, c.side(), new BlockPos(c.x(), 70, c.z()));
            helper.assertTrue(in == c.in(), "side " + c.side() + " at " + c.x() + ", " + c.z() + ": " + in);
        }
        helper.succeed();
    }

    /**
     * In the satellite's chunk (all a T1 reaches): the player's turret, an unowned one, the player's large base, and a
     * stranger's that shows only once it trusts the player; one just past the chunk never shows. The menu sends each
     * turret's targeting once, then only what changed.
     */
    @GameTest(template = YARD, timeoutTicks = 60, batch = "hubListsOnlyTurretsThePlayerMayOpen")
    public static void hubListsOnlyTurretsThePlayerMayOpen(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = player(helper, new BlockPos(15, 2, 13));
        helper.assertTrue(!player.hasPermissions(2), "the fake player is an operator here: ownership cannot be tested");
        HubInterfaceBlockEntity hub = hub(helper, new BlockPos(15, 2, 14), 1);
        hub.setEnergy(100_000);
        BlockPos center = hub.center();
        // The corner of the satellite's chunk farthest from the hub, so nothing lands in its 3x3.
        int x = (center.getX() & 15) < 8 ? (center.getX() | 15) - 3 : (center.getX() & ~15) + 1;
        int z = (center.getZ() & 15) < 8 ? (center.getZ() | 15) - 3 : (center.getZ() & ~15) + 1;
        BlockPos corner = new BlockPos(x, center.getY(), z);
        TurretBaseBlockEntity own = base(level, corner, player.getUUID());
        TurretBaseBlockEntity free = base(level, corner.east(), null);
        TurretBaseBlockEntity stranger = base(level, corner.east(2), UUID.randomUUID());
        LargeTurretBaseBlock.placeAt(level, corner.south(), BastionBlocks.LARGE_TURRET_BASE.get());
        TurretBaseBlockEntity large = TurretBaseBlock.core(level, corner.south());
        large.setOwner(player.getUUID());
        for (TurretBaseBlockEntity turret : List.of(own, free, stranger, large)) connect(turret, hub);
        BlockPos outside = new BlockPos((center.getX() & ~15) - 2, center.getY(), center.getZ());
        connect(base(level, outside, player.getUUID()), hub);
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(hub.online(), "the hub is not online");
            List<TurretBaseBlockEntity> seen = hub.visibleTurrets(player);
            helper.assertTrue(seen.size() == 3 && seen.containsAll(List.of(own, free, large)), "listed " + seen.size() + ": " + seen);
            ControlHubMenu menu = new ControlHubMenu(0, player.getInventory(), hub);
            HubRoster first = menu.roster(), second = menu.roster();
            helper.assertTrue(first.turrets().size() == 3 && first.filters().size() == 3 && second.filters().isEmpty(),
                    "targeting sent for " + first.filters().size() + ", then " + second.filters().size());
            TargetFilter trusting = stranger.filter().copy();
            trusting.trusted.add(player.getGameProfile().getName().toLowerCase(Locale.ROOT));
            stranger.setFilter(trusting);
            HubRoster third = menu.roster();
            helper.assertTrue(third.turrets().size() == 4 && third.filters().keySet().equals(Set.of(stranger.getBlockPos())),
                    "after trusting: " + third.turrets().size() + " turrets, targeting sent for " + third.filters().keySet());
            for (BlockPos pos : List.of(corner, corner.east(), corner.east(2), outside)) level.removeBlock(pos, false);
            for (BlockPos pos : LargeTurretBaseBlock.footprint(corner.south())) level.removeBlock(pos, false);
            helper.succeed();
        });
    }

    /** Only turrets holding a connector linked to this hub show, open or take batch edits: not one without, unlinked or linked elsewhere. */
    @GameTest(template = YARD, timeoutTicks = 60, batch = "hubListsOnlyConnectedTurrets")
    public static void hubListsOnlyConnectedTurrets(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HubInterfaceBlockEntity hub = hub(helper, new BlockPos(15, 2, 14), 2);
        hub.setEnergy(100_000);
        FakePlayer player = player(helper, new BlockPos(15, 2, 12));
        BlockPos center = hub.center();
        TurretBaseBlockEntity linked = base(level, center.offset(-4, 0, 4), player.getUUID());
        TurretBaseBlockEntity unlinked = base(level, center.offset(-2, 0, 4), player.getUUID());
        TurretBaseBlockEntity elsewhere = base(level, center.offset(0, 0, 4), player.getUUID());
        TurretBaseBlockEntity none = base(level, center.offset(2, 0, 4), player.getUUID());
        connect(linked, hub);
        unlinked.inventory().setStackInSlot(TurretInventory.CONNECTOR, new ItemStack(BastionItems.HUB_CONNECTOR.get()));
        elsewhere.inventory().setStackInSlot(TurretInventory.CONNECTOR,
                HubConnectorItem.link(new ItemStack(BastionItems.HUB_CONNECTOR.get()), level, hub.getBlockPos().east(20)));
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(hub.online(), "not online");
            List<TurretBaseBlockEntity> seen = hub.visibleTurrets(player);
            helper.assertTrue(seen.equals(List.of(linked)), "listed " + seen);
            for (TurretBaseBlockEntity other : List.of(unlinked, elsewhere, none)) {
                helper.assertTrue(hub.reachable(player, other.getBlockPos()) == null, "reachable without a connector to this hub: " + other.getBlockPos());
            }
            ControlHubMenu menu = new ControlHubMenu(0, player.getInventory(), hub);
            int changed = menu.edit(List.of(linked.getBlockPos(), unlinked.getBlockPos(), elsewhere.getBlockPos(), none.getBlockPos()), HubEdit.enabled(false));
            helper.assertTrue(changed == 1 && !linked.enabled() && unlinked.enabled() && elsewhere.enabled() && none.enabled(), "batch reached " + changed);
            for (TurretBaseBlockEntity turret : List.of(linked, unlinked, elsewhere, none)) level.removeBlock(turret.getBlockPos(), false);
            helper.succeed();
        });
    }

    /**
     * A turret 16 blocks out (a T2 reaches it) opens through the hub although the player is far from it; its GUI stays
     * usable only while the player stays within 8 blocks of the Interface, the hub is online and whole. A turret past the
     * reach is never reachable. Taking the connector out closes it too.
     */
    @GameTest(template = YARD, timeoutTicks = 80, batch = "remoteTurretGuiFollowsTheHub")
    public static void remoteTurretGuiFollowsTheHub(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HubInterfaceBlockEntity hub = hub(helper, new BlockPos(5, 2, 14), 2);
        hub.setEnergy(100_000);
        FakePlayer player = player(helper, new BlockPos(5, 2, 12));
        BlockPos near = hub.center().offset(16, 0, 0), out = hub.center().offset(48, 0, 0);
        TurretBaseBlockEntity turret = base(level, near, player.getUUID());
        connect(turret, hub);
        connect(base(level, out, player.getUUID()), hub);
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(hub.online(), "not online");
            TurretMenu menu = new TurretMenu(0, player.getInventory(), turret, hub.getBlockPos());
            helper.assertTrue(player.distanceToSqr(Vec3.atCenterOf(near)) > 64 && menu.stillValid(player), "a turret in reach is not usable through the hub");
            helper.assertTrue(hub.reachable(player, out) == null, "a turret past the reach is reachable");
            player.setPos(player.getX() + 10, player.getY(), player.getZ());
            helper.assertTrue(!menu.stillValid(player), "still usable 10 blocks from the Interface");
            player.setPos(player.getX() - 10, player.getY(), player.getZ());
            hub.setEnergy(0);
        });
        helper.runAtTickTime(8, () -> {
            helper.assertTrue(hub.status() == Status.OFFLINE && !new TurretMenu(0, player.getInventory(), turret, hub.getBlockPos()).stillValid(player),
                    "usable while the hub is offline");
            hub.setEnergy(100_000);
        });
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(new TurretMenu(0, player.getInventory(), turret, hub.getBlockPos()).stillValid(player), "not usable once online again");
            ItemStack connector = turret.inventory().getStackInSlot(TurretInventory.CONNECTOR);
            turret.inventory().setStackInSlot(TurretInventory.CONNECTOR, ItemStack.EMPTY);
            helper.assertTrue(!new TurretMenu(0, player.getInventory(), turret, hub.getBlockPos()).stillValid(player), "usable with its connector taken out");
            turret.inventory().setStackInSlot(TurretInventory.CONNECTOR, connector);
            level.removeBlock(ControlHubStructure.slots(hub.getBlockPos(), Direction.NORTH).get(2), false);
        });
        helper.runAtTickTime(13, () -> {
            helper.assertTrue(!new TurretMenu(0, player.getInventory(), turret, hub.getBlockPos()).stillValid(player), "usable after a hub part was broken");
            level.removeBlock(near, false);
            level.removeBlock(out, false);
            helper.succeed();
        });
    }

    /** Settings for a far turret only get through while its GUI is open and valid, here through the hub. */
    @GameTest(template = YARD, timeoutTicks = 60, batch = "configUpdateNeedsAValidGui")
    public static void configUpdateNeedsAValidGui(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HubInterfaceBlockEntity hub = hub(helper, new BlockPos(5, 2, 14), 2);
        hub.setEnergy(100_000);
        FakePlayer player = player(helper, new BlockPos(5, 2, 12));
        BlockPos at = hub.center().offset(16, 0, 0);
        TurretBaseBlockEntity turret = base(level, at, player.getUUID());
        connect(turret, hub);
        helper.runAtTickTime(5, () -> {
            TargetFilter lowest = turret.filter().copy();
            lowest.priority = TargetFilter.Priority.LOWEST_HEALTH;
            TurretConfigUpdate update = new TurretConfigUpdate(at, lowest, true, false, false);
            helper.assertTrue(!update.apply(player), "applied from afar with no GUI open");
            player.containerMenu = new TurretMenu(0, player.getInventory(), turret, hub.getBlockPos());
            helper.assertTrue(update.apply(player) && turret.filter().priority == TargetFilter.Priority.LOWEST_HEALTH,
                    "not applied through the hub's GUI");
            player.containerMenu = player.inventoryMenu;
            level.removeBlock(at, false);
            helper.succeed();
        });
    }

    /** One batch change reaches every selected turret and nothing else of their settings; one past the reach is skipped. */
    @GameTest(template = YARD, timeoutTicks = 60, batch = "batchEditChangesOnlyThatSetting")
    public static void batchEditChangesOnlyThatSetting(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        HubInterfaceBlockEntity hub = hub(helper, new BlockPos(15, 2, 14), 2);
        hub.setEnergy(100_000);
        FakePlayer player = player(helper, new BlockPos(15, 2, 12));
        BlockPos center = hub.center();
        List<TurretBaseBlockEntity> turrets = List.of(base(level, center.offset(-3, 0, 4), player.getUUID()),
                base(level, center.offset(0, 0, 4), player.getUUID()), base(level, center.offset(3, 0, 4), player.getUUID()));
        turrets.forEach(turret -> connect(turret, hub));
        ResourceLocation zombie = new ResourceLocation("minecraft:zombie"), creeper = new ResourceLocation("minecraft:creeper");
        TargetFilter zombies = turrets.get(0).filter().copy();
        zombies.rules.put(zombie, TargetFilter.Rule.ALWAYS);
        turrets.get(0).setFilter(zombies);
        TargetFilter alex = turrets.get(1).filter().copy();
        alex.trusted.add("alex");
        turrets.get(1).setFilter(alex);
        BlockPos far = center.offset(80, 0, 0); // past a T2's reach
        TurretBaseBlockEntity outside = base(level, far, player.getUUID());
        connect(outside, hub);
        helper.runAtTickTime(5, () -> {
            ControlHubMenu menu = new ControlHubMenu(0, player.getInventory(), hub);
            List<BlockPos> positions = new ArrayList<>(turrets.stream().map(TurretBaseBlockEntity::getBlockPos).toList());
            positions.add(far);
            int changed = menu.edit(positions, HubEdit.priority(TargetFilter.Priority.LOWEST_HEALTH));
            helper.assertTrue(changed == 3 && turrets.stream().allMatch(t -> t.filter().priority == TargetFilter.Priority.LOWEST_HEALTH),
                    "priority changed on " + changed);
            helper.assertTrue(outside.filter().priority == TargetFilter.Priority.NEAREST, "a turret past the reach changed");
            helper.assertTrue(turrets.get(0).filter().rules.get(zombie) == TargetFilter.Rule.ALWAYS && turrets.get(1).filter().trusted.contains("alex")
                    && turrets.get(2).filter().trusted.isEmpty(), "other settings changed");
            menu.edit(positions, HubEdit.rule(creeper, TargetFilter.Rule.NEVER));
            helper.assertTrue(turrets.stream().allMatch(t -> t.filter().rules.get(creeper) == TargetFilter.Rule.NEVER)
                    && turrets.get(0).filter().rules.size() == 2, "the creeper rule did not reach all three");
            menu.edit(positions, HubEdit.rule(creeper, null));
            helper.assertTrue(turrets.stream().noneMatch(t -> t.filter().rules.containsKey(creeper)), "the creeper rule was not removed");
            menu.edit(positions, HubEdit.enabled(false));
            helper.assertTrue(turrets.stream().noneMatch(TurretBaseBlockEntity::enabled) && outside.enabled(), "turn off did not reach exactly the three");
            for (TurretBaseBlockEntity turret : turrets) level.removeBlock(turret.getBlockPos(), false);
            level.removeBlock(far, false);
            helper.succeed();
        });
    }

    /** Bad names, unknown mobs and out-of-range values are refused; a batch for more than 256 turrets does not decode. */
    @GameTest(template = EMPTY, batch = "batchEditRejectsBadInput")
    public static void batchEditRejectsBadInput(GameTestHelper helper) {
        for (HubEdit edit : List.of(new HubEdit(HubEdit.Kind.TRUSTED_ADD, "bad name!", 0), new HubEdit(HubEdit.Kind.TRUSTED_ADD, "", 0),
                new HubEdit(HubEdit.Kind.RULE, "bastion:not_a_mob", 0), new HubEdit(HubEdit.Kind.RULE, "minecraft:zombie", 5),
                new HubEdit(HubEdit.Kind.CATEGORY, "NOPE", 1), new HubEdit(HubEdit.Kind.PLAYER_MODE, "", 9),
                new HubEdit(HubEdit.Kind.PRIORITY, "", -1), new HubEdit(HubEdit.Kind.ENABLED, "", 2))) {
            helper.assertTrue(!edit.valid(), "accepted " + edit);
        }
        helper.assertTrue(HubEdit.trust("Steve_1", true).valid() && HubEdit.rule(new ResourceLocation("minecraft:zombie"), null).valid(), "refused good edits");
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeEnum(HubAction.Kind.EDIT);
        buf.writeVarInt(ControlHubMenu.MAX_TURRETS + 1);
        boolean refused = false;
        try {
            HubAction.decode(buf);
        } catch (DecoderException e) {
            refused = true;
        }
        helper.assertTrue(refused, "decoded a batch for more than " + ControlHubMenu.MAX_TURRETS + " turrets");
        helper.succeed();
    }

    /** What two filters share shows as such; what differs as null (Mixed); names and rules count how many have them. */
    @GameTest(template = EMPTY, batch = "filterMixShowsMixedValues")
    public static void filterMixShowsMixedValues(GameTestHelper helper) {
        ResourceLocation zombie = new ResourceLocation("minecraft:zombie"), creeper = new ResourceLocation("minecraft:creeper");
        TargetFilter a = new TargetFilter(), b = new TargetFilter();
        a.rules.put(zombie, TargetFilter.Rule.ALWAYS);
        b.rules.put(zombie, TargetFilter.Rule.ALWAYS);
        b.rules.put(creeper, TargetFilter.Rule.NEVER);
        b.priority = TargetFilter.Priority.LOWEST_HEALTH;
        a.trusted.add("alex");
        b.trusted.addAll(List.of("alex", "steve"));
        FilterMix mix = new FilterMix(List.of(a, b));
        helper.assertTrue(Boolean.TRUE.equals(mix.category(TargetFilter.Category.HOSTILE)) && Boolean.FALSE.equals(mix.category(TargetFilter.Category.PASSIVE)),
                "shared categories not shown as shared");
        helper.assertTrue(mix.priority() == null && mix.playerMode() == TargetFilter.PlayerMode.ALL_EXCEPT_TRUSTED, "priority should be mixed, player mode shared");
        helper.assertTrue(mix.rule(zombie) == TargetFilter.Rule.ALWAYS && mix.ruleCount(zombie) == 2, "zombie rule");
        helper.assertTrue(mix.rule(creeper) == null && mix.ruleCount(creeper) == 1, "the creeper rule should be partial");
        helper.assertTrue(mix.trusted().equals(Map.of("alex", 2, "steve", 1)), "trusted counts " + mix.trusted());
        helper.succeed();
    }

    /**
     * Fed half its upkeep, a hub stays OFFLINE instead of flipping every tick (each flip re-sent its block, replayed the
     * online/offline sounds and closed remote turret GUIs); it comes back once it holds controlHubRestartTicks (100) of
     * upkeep: 2,000 FE for a T1, not 1,999.
     */
    @GameTest(template = ARENA, timeoutTicks = 80, batch = "underfedHubStaysOffline")
    public static void underfedHubStaysOffline(GameTestHelper helper) {
        HubInterfaceBlockEntity hub = hub(helper, IFACE, 1);
        IEnergyStorage port = energy(helper.getLevel(), ControlHubStructure.slots(hub.getBlockPos(), Direction.NORTH).get(0));
        int[] flips = {0};
        Status[] last = {null};
        helper.onEachTick(() -> {
            long tick = helper.getTick();
            if (tick < 5 || tick >= 45) return;
            port.receiveEnergy(10, false); // T1 upkeep is 20
            if (hub.status() == Status.UNFORMED) return; // forming late (a chunk loaded later) is not a flip
            if (last[0] != null && hub.status() != last[0]) flips[0]++;
            last[0] = hub.status();
        });
        helper.runAtTickTime(45, () -> {
            helper.assertTrue(flips[0] == 0 && hub.status() == Status.OFFLINE, "flipped " + flips[0] + " times on half its upkeep, now " + hub.status());
            hub.setEnergy(1999);
        });
        helper.runAtTickTime(47, () -> {
            helper.assertTrue(hub.status() == Status.OFFLINE, "back online with less than 100 ticks of upkeep");
            hub.setEnergy(2000);
        });
        helper.runAtTickTime(49, () -> {
            helper.assertTrue(hub.online(), "not back online with 100 ticks of upkeep");
            helper.succeed();
        });
    }
}

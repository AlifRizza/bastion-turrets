package dev.bastion.dev;

import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.content.kinetics.belt.BeltBlockEntity;
import com.simibubi.create.content.kinetics.belt.BeltHelper;
import com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem;
import com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity;
import com.simibubi.create.content.logistics.funnel.AbstractDirectionalFunnelBlock;
import com.simibubi.create.content.logistics.funnel.FunnelBlock;
import dev.bastion.Bastion;
import dev.bastion.registry.BastionBlocks;
import dev.bastion.registry.BastionItems;
import dev.bastion.turret.LargeTurretBaseBlock;
import dev.bastion.turret.TurretBaseBlock;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretInventory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Dev-only Create automation test rig (src/dev, needs Create in the dev run): one lane per turret, each
 * chest -> extracting funnel -> belt -> mechanical arm -> turret, plus one lane belt -> funnel -> turret without an arm.
 * Built by {@code /bastiondev automation}; {@code /bastiondev automation report} counts what reached each turret.
 */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID)
public final class AutomationRig {
    /** Lanes run east (+X), one every LANE_GAP blocks south. */
    private static final int BELT_LENGTH = 5, LANE_GAP = 5, AMMO_STACKS = 3;
    private static final List<Lane> LANES = new ArrayList<>();
    private static int checkBeltsIn = -1;
    private static ServerLevel checkLevel;

    record Lane(String name, BlockPos turret, BlockPos beltStart, BlockPos motor, boolean arm) {
    }

    private AutomationRig() {
    }

    /** Builds every lane with its first belt block at {@code origin}; returns how many lanes were built. */
    public static int build(ServerLevel level, BlockPos origin) {
        LANES.clear();
        int lanes = 7;
        for (BlockPos p : BlockPos.betweenClosed(origin.offset(-2, -1, -2), origin.offset(BELT_LENGTH + 6, 5, lanes * LANE_GAP))) {
            level.setBlock(p, p.getY() == origin.getY() - 1 ? Blocks.SMOOTH_STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        lane(level, origin.offset(0, 0, 0), "Gun", BastionItems.GUN_TURRET.get(), BastionItems.KINETIC_ROUNDS.get(), false, true);
        lane(level, origin.offset(0, 0, LANE_GAP), "Machine Gun", BastionItems.MACHINE_GUN_TURRET.get(), BastionItems.KINETIC_ROUNDS.get(), false, true);
        lane(level, origin.offset(0, 0, 2 * LANE_GAP), "Shotgun", BastionItems.SHOTGUN_TURRET.get(), BastionItems.SCATTER_SHELLS.get(), false, true);
        lane(level, origin.offset(0, 0, 3 * LANE_GAP), "Sniper", BastionItems.SNIPER_TURRET.get(), BastionItems.SNIPER_ROUNDS.get(), false, true);
        lane(level, origin.offset(0, 0, 4 * LANE_GAP), "Rocket", BastionItems.ROCKET_LAUNCHER_TURRET.get(), BastionItems.ROCKETS.get(), false, true);
        lane(level, origin.offset(0, 0, 5 * LANE_GAP), "Missile (2x2)", BastionItems.MISSILE_LAUNCHER_TURRET.get(), BastionItems.MISSILES.get(), true, true);
        lane(level, origin.offset(0, 0, 6 * LANE_GAP), "Gun, funnel", BastionItems.GUN_TURRET.get(), BastionItems.KINETIC_ROUNDS.get(), false, false);
        checkBeltsIn = 30;
        checkLevel = level;
        return lanes;
    }

    /**
     * One lane, z = the belt row: chest (y+2) over an extracting funnel (y+1) dropping onto the belt start, a belt
     * east to x+4, then either an arm at (x+5, z+1) taking from the belt end and loading the turret at x+6, or a
     * funnel at x+5 on the turret's side that the belt feeds directly.
     */
    private static void lane(ServerLevel level, BlockPos o, String name, Item weapon, Item ammo, boolean large, boolean arm) {
        BlockPos beltEnd = o.east(BELT_LENGTH - 1), turretPos = o.east(BELT_LENGTH + 1);
        // the source
        level.setBlock(o.above(2), Blocks.CHEST.defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(o.above(2)) instanceof ChestBlockEntity chest) {
            for (int i = 0; i < AMMO_STACKS; i++) chest.setItem(i, new ItemStack(ammo, ammo.getMaxStackSize()));
        }
        level.setBlock(o.above(), create("andesite_funnel").setValue(AbstractDirectionalFunnelBlock.FACING, Direction.DOWN)
                .setValue(FunnelBlock.EXTRACTING, true), Block.UPDATE_ALL);
        // the belt and the motor turning it (on the start pulley's axis, north side)
        BeltConnectorItem.createBelts(level, o, beltEnd);
        BlockPos motor = o.north();
        level.setBlock(motor, create("creative_motor").setValue(DirectionalKineticBlock.FACING, Direction.SOUTH), Block.UPDATE_ALL);
        // the turret
        if (large) {
            LargeTurretBaseBlock.placeAt(level, turretPos, BastionBlocks.LARGE_TURRET_BASE.get());
        } else {
            level.setBlock(turretPos, BastionBlocks.TURRET_BASE.get().defaultBlockState().setValue(TurretBaseBlock.FACING, Direction.UP), Block.UPDATE_ALL);
        }
        if (level.getBlockEntity(turretPos) instanceof TurretBaseBlockEntity turret) {
            turret.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(weapon));
        }
        // the hand-off
        if (arm) {
            BlockPos armPos = o.east(BELT_LENGTH).south();
            level.setBlock(armPos.below(), create("creative_motor").setValue(DirectionalKineticBlock.FACING, Direction.UP), Block.UPDATE_ALL);
            level.setBlock(armPos, create("mechanical_arm"), Block.UPDATE_ALL);
            BlockEntity armBe = level.getBlockEntity(armPos);
            if (armBe != null) {
                CompoundTag tag = armBe.saveWithoutMetadata();
                ListTag points = new ListTag();
                points.add(point("create:belt", beltEnd.subtract(armPos), "TAKE"));
                points.add(point(Bastion.MOD_ID + ":turret", turretPos.subtract(armPos), "DEPOSIT"));
                tag.put("InteractionPoints", points);
                armBe.load(tag);
                armBe.setChanged();
            }
        } else {
            // facing away from the turret, towards the belt end that feeds it
            level.setBlock(o.east(BELT_LENGTH), create("andesite_funnel").setValue(AbstractDirectionalFunnelBlock.FACING, Direction.WEST)
                    .setValue(FunnelBlock.EXTRACTING, false), Block.UPDATE_ALL);
        }
        sign(level, o.west(), name + (arm ? " (arm)" : " (funnel)"));
        LANES.add(new Lane(name, turretPos, o, motor, arm));
    }

    private static BlockState create(String id) {
        return ForgeRegistries.BLOCKS.getValue(new ResourceLocation("create", id)).defaultBlockState();
    }

    private static CompoundTag point(String type, BlockPos relative, String mode) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", type);
        tag.put("Pos", NbtUtils.writeBlockPos(relative));
        tag.putString("Mode", mode);
        return tag;
    }

    private static void sign(ServerLevel level, BlockPos pos, String text) {
        level.setBlock(pos, Blocks.OAK_SIGN.defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof SignBlockEntity sign) {
            sign.setText(new SignText().setMessage(1, Component.literal(text)), true);
        }
    }

    /** Belts run whichever way their motor turns them: once spinning, flip any lane whose belt runs west. */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || checkBeltsIn < 0 || --checkBeltsIn > 0) return;
        checkBeltsIn = -1;
        for (Lane lane : LANES) {
            BeltBlockEntity belt = BeltHelper.getSegmentBE(checkLevel, lane.beltStart());
            if (belt != null && belt.getSpeed() != 0 && belt.getMovementFacing() != Direction.EAST
                    && checkLevel.getBlockEntity(lane.motor()) instanceof CreativeMotorBlockEntity motor) {
                motor.generatedSpeed.setValue(-motor.generatedSpeed.getValue());
            }
        }
    }

    /** One line per lane: ammo now in the turret (plus loaded tubes for the launcher) and what the chest still holds. */
    public static List<String> report(ServerLevel level) {
        List<String> lines = new ArrayList<>();
        for (Lane lane : LANES) {
            int ammo = 0;
            String extra = "";
            if (level.getBlockEntity(lane.turret()) instanceof TurretBaseBlockEntity turret) {
                for (int i = TurretInventory.AMMO_START; i < TurretInventory.MODIFIER_START; i++) ammo += turret.inventory().getStackInSlot(i).getCount();
                int tubes = Integer.bitCount(turret.weaponState().tubes);
                if (tubes > 0) extra = ", " + tubes + " tubes loaded";
            }
            int left = level.getBlockEntity(lane.beltStart().above(2)) instanceof ChestBlockEntity chest
                    ? IntStream.range(0, chest.getContainerSize()).map(i -> chest.getItem(i).getCount()).sum() : -1;
            BeltBlockEntity belt = BeltHelper.getSegmentBE(level, lane.beltStart());
            String beltInfo = belt == null ? "no belt" : "belt " + belt.getMovementFacing() + " @" + belt.getSpeed();
            lines.add(lane.name() + (lane.arm() ? " [arm]" : " [funnel]") + ": " + ammo + " in turret" + extra + ", " + left + " left in chest, " + beltInfo);
        }
        return lines;
    }
}

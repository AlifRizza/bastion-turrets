package dev.bastion.hub;

import dev.bastion.hub.HubInterfaceBlock.Status;
import dev.bastion.turret.FeedHubBlock.Part;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turret Control Hub layout (PLAN "Turret Control Hub"): a 3x3x3 cube whose middle layer has the Hub Interface in the
 * middle of its front side (facing out, the cube behind it, at the eye level of a player on the ground), every other
 * block a Hub Casing or Hub Power Port (at least one port), and a Satellite Uplink standing on the cube's top middle
 * block. One check serves the Interface (forming, server) and the blueprint hologram (client). A formed casing or port
 * carries its place in the cube in its block state (x, y, z as in FeedHubBlock), which is also how a hub tells its own
 * parts from another hub's.
 */
public final class ControlHubStructure {
    /** What is wrong at one position; the action bar names the first one. */
    public enum Kind {NEED_SATELLITE, NEED_BLOCK, WRONG, OTHER_HUB, NEED_PORT}

    public record Issue(BlockPos pos, Kind kind) {
    }

    /** The check's outcome: formed when nothing is wrong; tier = the satellite's (0 without one). */
    public record Result(List<Issue> issues, int tier) {
        public boolean formed() {
            return issues.isEmpty();
        }
    }

    private ControlHubStructure() {
    }

    /** The cube's middle block: one block behind the Interface, at its height. The reach is counted from its column. */
    public static BlockPos center(BlockPos interfacePos, Direction facing) {
        return interfacePos.relative(facing.getOpposite());
    }

    /** Where the satellite's bottom half stands: on the cube's top middle block. */
    public static BlockPos satellite(BlockPos center) {
        return center.above(2);
    }

    /**
     * The 26 cube blocks besides the Interface, in problem order: the bottom layer, then the middle, then the top; in each
     * layer the front row first, left to right as seen standing in front of the Interface.
     */
    public static List<BlockPos> slots(BlockPos interfacePos, Direction facing) {
        Direction back = facing.getOpposite(), right = back.getClockWise();
        List<BlockPos> slots = new ArrayList<>(26);
        for (int layer = -1; layer <= 1; layer++) {
            for (int row = 0; row < 3; row++) {
                for (int col = -1; col <= 1; col++) {
                    if (layer != 0 || row != 0 || col != 0) slots.add(interfacePos.relative(back, row).relative(right, col).above(layer));
                }
            }
        }
        return slots;
    }

    /**
     * What keeps the hub of the Interface at {@code interfacePos} from forming: the satellite first, then the slots in
     * {@link #slots} order, then (only once every slot holds a part) the missing Power Port on every casing. Never loads a
     * chunk: a position in an unloaded one counts as missing.
     */
    public static Result check(Level level, BlockPos interfacePos, Direction facing) {
        BlockPos center = center(interfacePos, facing);
        List<Issue> issues = new ArrayList<>();
        int tier = 0;
        BlockPos satellite = satellite(center);
        BlockState up = level.isLoaded(satellite) ? level.getBlockState(satellite) : null;
        if (up != null && up.getBlock() instanceof SatelliteUplinkBlock uplink && up.getValue(SatelliteUplinkBlock.PART) == 0) {
            tier = uplink.tier();
        } else if (up == null || up.canBeReplaced()) {
            issues.add(new Issue(satellite, Kind.NEED_SATELLITE));
            BlockPos top = satellite.above(); // the satellite's upper half must fit too
            if (level.isLoaded(top) && !level.getBlockState(top).canBeReplaced()) issues.add(new Issue(top, Kind.WRONG));
        } else {
            issues.add(new Issue(satellite, Kind.WRONG));
        }
        int ports = 0;
        boolean complete = true;
        List<BlockPos> casings = new ArrayList<>(26);
        for (BlockPos pos : slots(interfacePos, facing)) {
            BlockState state = level.isLoaded(pos) ? level.getBlockState(pos) : null;
            if (state == null || !(state.getBlock() instanceof HubCasingBlock)) {
                issues.add(new Issue(pos, state == null || state.canBeReplaced() ? Kind.NEED_BLOCK : Kind.WRONG));
                complete = false;
            } else if (ownedElsewhere(level, pos, state, center)) {
                issues.add(new Issue(pos, Kind.OTHER_HUB));
                complete = false;
            } else if (state.getBlock() instanceof HubPowerPortBlock) {
                ports++;
            } else {
                casings.add(pos);
            }
        }
        // Before every slot holds a part, an empty slot can still take the port: only then point at the casings.
        if (complete && ports == 0) for (BlockPos pos : casings) issues.add(new Issue(pos, Kind.NEED_PORT));
        return new Result(issues, tier);
    }

    /** "Turret Control Hub incomplete (3): needs a Hub Casing or Hub Power Port" for the action bar of an unformed hub. */
    public static Component message(Result result) {
        Issue first = result.issues().get(0);
        return Component.translatable("message.bastion.control_hub.incomplete", result.issues().size(),
                Component.translatable("message.bastion.control_hub." + first.kind().name().toLowerCase(Locale.ROOT)));
    }

    /** A formed part whose place puts it around another middle block that still has a formed Interface. */
    private static boolean ownedElsewhere(Level level, BlockPos pos, BlockState state, BlockPos center) {
        if (state.getValue(HubCasingBlock.X) == Part.ALONE) return false;
        BlockPos owner = partCenter(pos, state);
        return !owner.equals(center) && hubAt(level, owner) != null;
    }

    /** The middle block of the cube a formed part sits in. */
    public static BlockPos partCenter(BlockPos pos, BlockState state) {
        return pos.offset(step(state.getValue(HubCasingBlock.X)), step(state.getValue(HubCasingBlock.Y)), step(state.getValue(HubCasingBlock.Z)));
    }

    private static int step(Part part) {
        return part == Part.LOW ? 1 : part == Part.HIGH ? -1 : 0;
    }

    /** The formed Interface of the hub around {@code center} (in the middle of one of its sides, facing away), or null. */
    @Nullable
    public static HubInterfaceBlockEntity hubAt(Level level, BlockPos center) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos pos = center.relative(side);
            if (!level.isLoaded(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof HubInterfaceBlock && state.getValue(HubInterfaceBlock.FACING) == side
                    && state.getValue(HubInterfaceBlock.STATUS) != Status.UNFORMED && level.getBlockEntity(pos) instanceof HubInterfaceBlockEntity hub) {
                return hub;
            }
        }
        return null;
    }

    /** Gives every part its place in the cube (clients only: no neighbour updates). The caller sets the status. */
    static void form(Level level, BlockPos interfacePos, Direction facing) {
        BlockPos center = center(interfacePos, facing);
        for (BlockPos pos : slots(interfacePos, facing)) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof HubCasingBlock)) continue;
            BlockState formed = state.setValue(HubCasingBlock.X, Part.of(pos.getX() - center.getX() + 1, 3))
                    .setValue(HubCasingBlock.Y, Part.of(pos.getY() - center.getY() + 1, 3))
                    .setValue(HubCasingBlock.Z, Part.of(pos.getZ() - center.getZ() + 1, 3));
            if (formed != state) level.setBlock(pos, formed, Block.UPDATE_CLIENTS);
        }
    }

    /** Its parts and satellite stop being this hub's (parts of other hubs are left alone). */
    static void unform(Level level, BlockPos interfacePos, Direction facing) {
        BlockPos center = center(interfacePos, facing);
        for (BlockPos pos : slots(interfacePos, facing)) {
            if (!level.isLoaded(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof HubCasingBlock && state.getValue(HubCasingBlock.X) != Part.ALONE && partCenter(pos, state).equals(center)) {
                level.setBlock(pos, state.setValue(HubCasingBlock.X, Part.ALONE).setValue(HubCasingBlock.Y, Part.ALONE)
                        .setValue(HubCasingBlock.Z, Part.ALONE), Block.UPDATE_CLIENTS);
            }
        }
        setSatellite(level, center, Status.UNFORMED);
    }

    /** The satellite on top of the cube around {@code center} shows the hub's status (dish folded, spinning or stopped). */
    static void setSatellite(Level level, BlockPos center, Status status) {
        BlockPos pos = satellite(center);
        if (!level.isLoaded(pos)) return;
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof SatelliteUplinkBlock && state.getValue(SatelliteUplinkBlock.PART) == 0
                && state.getValue(SatelliteUplinkBlock.STATUS) != status) {
            level.setBlock(pos, state.setValue(SatelliteUplinkBlock.STATUS, status), Block.UPDATE_CLIENTS);
        }
    }

    /** A part was placed or removed at {@code pos}: every Interface whose hub could hold it checks again next tick. */
    public static void notifyAround(Level level, BlockPos pos) {
        if (level.isClientSide) return;
        // The Interface sits in the middle layer: one above a bottom-layer part, one below a top-layer part, two below the satellite.
        for (int dy = -2; dy <= 1; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos at = pos.offset(dx, dy, dz);
                    if (level.isLoaded(at) && level.getBlockState(at).getBlock() instanceof HubInterfaceBlock block) level.scheduleTick(at, block, 1);
                }
            }
        }
    }

    /** Whether {@code pos} lies in the {@code side} x {@code side} chunks centred on the chunk of {@code center}. */
    public static boolean covers(BlockPos center, int side, BlockPos pos) {
        int r = side / 2;
        return Math.abs((pos.getX() >> 4) - (center.getX() >> 4)) <= r && Math.abs((pos.getZ() >> 4) - (center.getZ() >> 4)) <= r;
    }
}

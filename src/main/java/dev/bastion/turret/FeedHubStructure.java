package dev.bastion.turret;

import dev.bastion.turret.FeedHubBlock.Part;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Feed Hub multiblock (PLAN "Feed Hub", 1a): hubs next to each other merge into boxes with a square footprint 1..3
 * wide, at most as tall as wide. A block's place in its box is in its state (FeedHubBlock X/Y/Z), so a structure needs
 * no saved data, reaches the client with the blocks and picks the connected model. Every change re-partitions all
 * connected hubs from scratch, so the result never depends on the order they were built in.
 */
public final class FeedHubStructure {
    public static final int MAX_WIDTH = 3;
    /** {width, height}, most blocks first: 27, 18, 9, 8, 4, 1. */
    private static final int[][] SHAPES = {{3, 3}, {3, 2}, {3, 1}, {2, 2}, {2, 1}, {1, 1}};
    // ponytail: hubs gathered per re-form; past this a giant hub wall only re-forms the part found first.
    private static final int SEARCH_LIMIT = 512;

    private FeedHubStructure() {
    }

    /** The box a structure fills. */
    public record Box(BlockPos min, int width, int height) {
        public static Box single(BlockPos pos) {
            return new Box(pos, 1, 1);
        }

        /** The box of the hub at {@code pos}, read from the states of the blocks on the way (loaded blocks only). */
        public static Box of(Level level, BlockPos pos, BlockState state) {
            BlockPos min = new BlockPos(pos.getX() - back(level, pos, state, Direction.Axis.X),
                    pos.getY() - back(level, pos, state, Direction.Axis.Y), pos.getZ() - back(level, pos, state, Direction.Axis.Z));
            return new Box(min, length(level, min, Direction.Axis.X), length(level, min, Direction.Axis.Y));
        }

        public boolean contains(BlockPos pos) {
            return pos.getX() >= min.getX() && pos.getX() < min.getX() + width && pos.getY() >= min.getY() && pos.getY() < min.getY() + height
                    && pos.getZ() >= min.getZ() && pos.getZ() < min.getZ() + width;
        }

        public int blocks() {
            return width * width * height;
        }

        /** Bottom layer first, then x, then z: the order of the structure's slots and FE. */
        public List<BlockPos> positions() {
            List<BlockPos> positions = new ArrayList<>(blocks());
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    for (int z = 0; z < width; z++) positions.add(min.offset(x, y, z));
                }
            }
            return positions;
        }

        /** Every block is a loaded hub that knows its place in this box (states can lag a tick behind a break). */
        public boolean intact(Level level) {
            for (BlockPos pos : positions()) {
                if (!isHub(level, pos)) return false;
                BlockState state = level.getBlockState(pos);
                if (formed(state, pos, this) != state) return false;
            }
            return true;
        }

        /** "3x3x2" */
        public String size() {
            return width + "x" + width + "x" + height;
        }
    }

    /** Re-partitions every hub connected to {@code start} (a scheduled tick after a hub was placed or broken). */
    static void reform(Level level, BlockPos start) {
        if (!isHub(level, start)) return;
        Set<BlockPos> hubs = new HashSet<>(List.of(start));
        Deque<BlockPos> open = new ArrayDeque<>(hubs);
        while (!open.isEmpty() && hubs.size() < SEARCH_LIMIT) {
            BlockPos at = open.poll();
            for (Direction side : Direction.values()) {
                BlockPos next = at.relative(side);
                if (!hubs.contains(next) && isHub(level, next)) {
                    hubs.add(next);
                    open.add(next);
                }
            }
        }
        List<BlockPos> order = new ArrayList<>(hubs);
        order.sort(Comparator.comparingInt((BlockPos p) -> p.getY()).thenComparingInt(p -> p.getX()).thenComparingInt(p -> p.getZ()));
        Set<BlockPos> free = new HashSet<>(hubs);
        for (BlockPos pos : order) {
            if (!free.contains(pos)) continue;
            for (int[] shape : SHAPES) {
                Box box = new Box(pos, shape[0], shape[1]);
                List<BlockPos> blocks = box.positions();
                if (!free.containsAll(blocks)) continue;
                for (BlockPos block : blocks) {
                    free.remove(block);
                    form(level, block, box);
                }
                break; // 1x1x1 always fits
            }
        }
    }

    /** Gives the block its place in the box (clients only: neighbours, turrets included, are not told). */
    private static void form(Level level, BlockPos pos, Box box) {
        BlockState state = level.getBlockState(pos);
        BlockState formed = formed(state, pos, box);
        if (formed != state) level.setBlock(pos, formed, Block.UPDATE_CLIENTS);
    }

    /** The state of the hub at {@code pos} as a block of {@code box}. */
    private static BlockState formed(BlockState state, BlockPos pos, Box box) {
        return state.setValue(FeedHubBlock.X, Part.of(pos.getX() - box.min().getX(), box.width()))
                .setValue(FeedHubBlock.Y, Part.of(pos.getY() - box.min().getY(), box.height()))
                .setValue(FeedHubBlock.Z, Part.of(pos.getZ() - box.min().getZ(), box.width()));
    }

    /** A hub in a loaded chunk (never loads one). */
    static boolean isHub(Level level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockState(pos).getBlock() instanceof FeedHubBlock;
    }

    /** Steps from {@code pos} back to its structure's low end on {@code axis}. */
    private static int back(Level level, BlockPos pos, BlockState state, Direction.Axis axis) {
        int steps = 0;
        while (steps < MAX_WIDTH - 1 && FeedHubBlock.part(state, axis).ordinal() >= Part.MIDDLE.ordinal()) {
            BlockPos next = pos.relative(axis, -steps - 1);
            if (!isHub(level, next)) break;
            state = level.getBlockState(next);
            steps++;
        }
        return steps;
    }

    /** Blocks from {@code min} to its structure's high end on {@code axis}. */
    private static int length(Level level, BlockPos min, Direction.Axis axis) {
        if (!isHub(level, min) || FeedHubBlock.part(level.getBlockState(min), axis) == Part.ALONE) return 1;
        int length = 1;
        while (length < MAX_WIDTH && isHub(level, min.relative(axis, length))) {
            length++;
            if (FeedHubBlock.part(level.getBlockState(min.relative(axis, length - 1)), axis) == Part.HIGH) break;
        }
        return length;
    }
}

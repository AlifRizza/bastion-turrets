package dev.bastion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.bastion.Bastion;
import dev.bastion.turret.Footprint;
import dev.bastion.turret.Multiblock;
import dev.bastion.turret.TurretBaseItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Placement hologram (PLAN "Hologram penempatan multiblock"): holding a multiblock and aiming at a block shows its model
 * as a ghost and a box per block it would fill, cyan where free, red where something is in the way. Reads the same
 * Multiblock#footprint the click uses.
 */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID, value = Dist.CLIENT)
public final class PlacementPreview {
    private static final int FREE = EmissiveLayer.MODEL_GLOW, BLOCKED = 0xFF3A30;
    private static final float FILL = 0.15f, EDGE = 0.6f;
    private static GhostRenderer ghosts;

    private PlacementPreview() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return; // the stage VfxManager draws in
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.isSpectator() || mc.screen != null
                || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        for (InteractionHand hand : InteractionHand.values()) { // like a click: the main hand's block wins
            ItemStack stack = mc.player.getItemInHand(hand);
            if (!(stack.getItem() instanceof BlockItem item)) continue;
            if (!(item.getBlock() instanceof Multiblock multiblock)) return;
            BlockPlaceContext context = item.updatePlacementContext(new BlockPlaceContext(mc.player, hand, stack, hit));
            Footprint footprint = context == null ? null : multiblock.footprint(context);
            if (footprint != null) draw(event.getPoseStack(), event.getCamera().getPosition(), footprint, stack, item);
            return;
        }
    }

    private static void draw(PoseStack pose, Vec3 camera, Footprint footprint, ItemStack stack, BlockItem item) {
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        for (BlockPos pos : footprint.blocks()) box(pose, buffers, pos, footprint.blocked().contains(pos) ? BLOCKED : FREE);
        // Model origin = the footprint's centre on its floor, front north (as the block renderers place it).
        double x = footprint.blocks().stream().mapToInt(BlockPos::getX).average().orElseThrow() + 0.5;
        double z = footprint.blocks().stream().mapToInt(BlockPos::getZ).average().orElseThrow() + 0.5;
        int y = footprint.blocks().stream().mapToInt(BlockPos::getY).min().orElseThrow();
        pose.translate(x, y, z);
        pose.mulPose(Axis.YP.rotationDegrees(turn(footprint.front())));
        if (ghosts == null) ghosts = new GhostRenderer();
        String model = BuiltInRegistries.BLOCK.getKey(item.getBlock()).getPath();
        ghosts.draw(pose, buffers, PlacementGhost.of(model, item instanceof TurretBaseItem ? TurretBaseItem.tier(stack) : null),
                footprint.placeable() ? FREE : BLOCKED);
        pose.popPose();
        buffers.endBatch();
    }

    /** GeoBlockRenderer#rotateBlock's turn for a front (the models face north); UP and DOWN do not turn. */
    private static float turn(Direction front) {
        return switch (front) {
            case SOUTH -> 180;
            case WEST -> 90;
            case EAST -> 270;
            default -> 0;
        };
    }

    private static void box(PoseStack pose, MultiBufferSource buffers, BlockPos pos, int rgb) {
        float r = (rgb >> 16 & 255) / 255f, g = (rgb >> 8 & 255) / 255f, b = (rgb & 255) / 255f;
        AABB box = new AABB(pos).inflate(0.002); // just outside the block faces: no z-fighting with what is there
        LevelRenderer.renderLineBox(pose, buffers.getBuffer(RenderType.lines()), box, r, g, b, EDGE);
        VertexConsumer fill = buffers.getBuffer(BastionRenderTypes.ghostBox());
        Matrix4f m = pose.last().pose();
        float x0 = (float) box.minX, y0 = (float) box.minY, z0 = (float) box.minZ, x1 = (float) box.maxX, y1 = (float) box.maxY, z1 = (float) box.maxZ;
        float[][] quads = {
                {x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1}, {x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0},
                {x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0}, {x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1},
                {x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0}, {x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1}};
        for (float[] q : quads) {
            for (int i = 0; i < 12; i += 3) fill.vertex(m, q[i], q[i + 1], q[i + 2]).color(r, g, b, FILL).endVertex();
        }
    }
}

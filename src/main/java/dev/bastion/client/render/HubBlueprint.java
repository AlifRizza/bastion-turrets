package dev.bastion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.bastion.Bastion;
import dev.bastion.hub.ControlHubStructure;
import dev.bastion.hub.HubInterfaceBlock;
import dev.bastion.hub.HubInterfaceBlock.Status;
import dev.bastion.registry.BastionItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * Control Hub holograms (PLAN "Belum jadi: petunjuk ala Tinkers"). The blueprint: right-clicking an unfinished Hub
 * Interface shows, for 10 s and only to that player, a box per problem: cyan with the needed block turning inside where
 * one is missing, red where a block is in the way or belongs to another hub, amber on every casing while there is no
 * Power Port. Checked again every frame from the client's blocks, so boxes go as the player fills them, and all of it once
 * the hub forms. The scan: a cyan sheet sweeping a hub's cube from the Interface to the back as it forms.
 */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID, value = Dist.CLIENT)
public final class HubBlueprint {
    private static final int SHOW_TICKS = 200, SCAN_TICKS = 24;
    private static final int RED = 0xFF3A30, AMBER = 0xFFB347;
    @Nullable
    private static BlockPos shown, scanned;
    private static Direction shownFacing = Direction.NORTH, scanFacing = Direction.NORTH;
    private static long shownUntil, scanStart;

    private HubBlueprint() {
    }

    /** Shows the blueprint of the hub whose Interface is at {@code iface}. */
    public static void show(BlockPos iface, Direction facing) {
        shown = iface;
        shownFacing = facing;
        shownUntil = time() + SHOW_TICKS;
    }

    /** Sweeps the scan over the hub whose Interface is at {@code iface}. */
    public static void scan(BlockPos iface, Direction facing) {
        scanned = iface;
        scanFacing = facing;
        scanStart = time();
    }

    private static long time() {
        ClientLevel level = Minecraft.getInstance().level;
        return level == null ? 0 : level.getGameTime();
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || (shown == null && scanned == null)) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        if (shown != null) blueprint(level, pose, buffers, event.getPartialTick());
        if (scanned != null) scan(level, pose, buffers, event.getPartialTick());
        pose.popPose();
        buffers.endBatch();
    }

    private static void blueprint(ClientLevel level, PoseStack pose, MultiBufferSource buffers, float partialTick) {
        BlockState state = level.getBlockState(shown);
        if (level.getGameTime() > shownUntil || !(state.getBlock() instanceof HubInterfaceBlock) || state.getValue(HubInterfaceBlock.STATUS) != Status.UNFORMED) {
            shown = null;
            return;
        }
        float spin = (level.getGameTime() + partialTick) * 4;
        for (ControlHubStructure.Issue issue : ControlHubStructure.check(level, shown, shownFacing).issues()) {
            switch (issue.kind()) {
                case NEED_BLOCK -> {
                    PlacementPreview.box(pose, buffers, issue.pos(), EmissiveLayer.MODEL_GLOW);
                    item(pose, buffers, issue.pos(), BastionItems.HUB_CASING.get(), spin);
                }
                case NEED_SATELLITE -> {
                    PlacementPreview.box(pose, buffers, issue.pos(), EmissiveLayer.MODEL_GLOW);
                    PlacementPreview.box(pose, buffers, issue.pos().above(), EmissiveLayer.MODEL_GLOW);
                    item(pose, buffers, issue.pos(), BastionItems.SATELLITE_UPLINK_T1.get(), spin);
                }
                case NEED_PORT -> PlacementPreview.box(pose, buffers, issue.pos(), AMBER);
                default -> PlacementPreview.box(pose, buffers, issue.pos(), RED);
            }
        }
    }

    /** The needed block as a small item turning in the middle of its box. */
    private static void item(PoseStack pose, MultiBufferSource buffers, BlockPos pos, Item item, float spin) {
        pose.pushPose();
        pose.translate(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(spin));
        pose.scale(0.5f, 0.5f, 0.5f);
        Minecraft mc = Minecraft.getInstance();
        mc.getItemRenderer().renderStatic(new ItemStack(item), ItemDisplayContext.FIXED, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                pose, buffers, mc.level, 0);
        pose.popPose();
    }

    /** A cube-tall cyan sheet across the hub, moving from the Interface's front face to the back over SCAN_TICKS, fading. */
    private static void scan(ClientLevel level, PoseStack pose, MultiBufferSource buffers, float partialTick) {
        float t = (level.getGameTime() - scanStart + partialTick) / SCAN_TICKS;
        if (t < 0 || t >= 1) {
            scanned = null;
            return;
        }
        Direction back = scanFacing.getOpposite(), right = back.getClockWise();
        Vec3 front = Vec3.atBottomCenterOf(scanned).add(Vec3.atLowerCornerOf(scanFacing.getNormal()).scale(0.5));
        Vec3 depth = Vec3.atLowerCornerOf(back.getNormal()), across = Vec3.atLowerCornerOf(right.getNormal());
        Vec3 a = front.add(depth.scale(3 * t)).add(across.scale(-1.5)).add(0, -1, 0); // the cube runs from one below the Interface
        Vec3 b = front.add(depth.scale(3 * t + 0.1)).add(across.scale(1.5)).add(0, 2.05, 0); // to one above it
        PlacementPreview.box(pose, buffers, new AABB(a, b), EmissiveLayer.MODEL_GLOW, 0.35f * (1 - t), 0.9f * (1 - t));
    }
}

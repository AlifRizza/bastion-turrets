package dev.bastion.client.vfx;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.bastion.Bastion;
import dev.bastion.client.BurnEffects;
import dev.bastion.client.ClientTurret;
import dev.bastion.client.MortarEffects;
import dev.bastion.client.TeslaEffects;
import dev.bastion.client.particle.VfxParticle;
import dev.bastion.client.render.ArcRenderer;
import dev.bastion.client.render.BeamRenderer;
import dev.bastion.client.render.CasingRenderer;
import dev.bastion.client.render.FlameJetRenderer;
import dev.bastion.client.render.LaserChargeRenderer;
import dev.bastion.client.render.RailRenderer;
import dev.bastion.client.render.SmokeTrailRenderer;
import dev.bastion.client.render.TracerRenderer;
import dev.bastion.client.render.TurretHolograms;
import dev.bastion.config.BastionClientConfig;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The single entry point for every Bastion effect (PLAN 5):
 * {@code VfxManager.play(VfxPresets.GUN_MUZZLE, origin, direction, VfxParams.of().color(0xFF4FD8FF))}.
 * Also owns the quality preset (PLAN 5.8) and drives every client-side effect system each tick and frame.
 */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID, value = Dist.CLIENT)
public final class VfxManager {
    private VfxManager() {
    }

    public static void play(VfxPreset preset, Vec3 origin, Vec3 direction, VfxParams params) {
        if (Minecraft.getInstance().level == null) return;
        preset.play(new Vfx(origin, direction, params));
    }

    // --- quality preset, PLAN 5.8 --------------------------------------------------------------

    public static BastionClientConfig.VfxQuality quality() {
        return BastionClientConfig.VFX_QUALITY.get();
    }

    public static float particleMultiplier() {
        return switch (quality()) {
            case LOW -> 0.3f;
            case MEDIUM -> 0.6f;
            case HIGH -> 1f;
        };
    }

    /** At least one particle survives scaling, so no effect silently vanishes on LOW. */
    static int scaledCount(int count) {
        return count <= 0 ? 0 : Math.max(1, Math.round(count * particleMultiplier()));
    }

    public static int trailLength() {
        return switch (quality()) {
            case LOW -> 4;
            case MEDIUM -> 8;
            case HIGH -> 16;
        };
    }

    public static int maxDynamicLights() {
        if (!BastionClientConfig.DYNAMIC_LIGHTS.get()) return 0;
        int preset = switch (quality()) {
            case LOW -> 0;
            case MEDIUM -> 12;
            case HIGH -> 24;
        };
        return Math.min(preset, BastionClientConfig.MAX_DYNAMIC_LIGHTS.get());
    }

    // --- drivers ---------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        VfxParticle.pruneBudget();
        ClientTurret.tickAll(mc.level);
        TurretHolograms.tick(mc.level);
        TracerRenderer.tick();
        SmokeTrailRenderer.tick(mc.level.getGameTime());
        ArcRenderer.tick();
        BeamRenderer.tick();
        RailRenderer.tick();
        TeslaEffects.tick(mc.level);
        MortarEffects.tick(mc.level);
        BurnEffects.tick(mc.level);
        DecalRenderer.tick(mc.level);
        CasingRenderer.tick(mc.level);
        DynamicLightManager.tick();
        CameraShake.tick();
        ScreenFlash.tick();
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        PoseStack poseStack = event.getPoseStack();
        Camera camera = event.getCamera();
        float partialTick = event.getPartialTick();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        DecalRenderer.render(poseStack, buffers, camera, partialTick);
        CasingRenderer.render(poseStack, buffers, camera, partialTick);
        if (Minecraft.getInstance().level != null) SmokeTrailRenderer.render(poseStack, buffers, camera, partialTick, Minecraft.getInstance().level);
        TracerRenderer.render(poseStack, buffers, camera, partialTick);
        ArcRenderer.render(poseStack, buffers, camera, partialTick);
        BeamRenderer.render(poseStack, buffers, camera, partialTick);
        if (Minecraft.getInstance().level != null) LaserChargeRenderer.render(poseStack, buffers, camera, partialTick, Minecraft.getInstance().level);
        if (Minecraft.getInstance().level != null) RailRenderer.render(poseStack, buffers, camera, partialTick, Minecraft.getInstance().level);
        if (Minecraft.getInstance().level != null) MortarEffects.render(poseStack, buffers, camera, partialTick, Minecraft.getInstance().level);
        if (Minecraft.getInstance().level != null) FlameJetRenderer.render(poseStack, buffers, camera, partialTick, Minecraft.getInstance().level);
        if (Minecraft.getInstance().level != null) TurretHolograms.render(poseStack, buffers, camera, partialTick, Minecraft.getInstance().level);
        buffers.endBatch();
    }

    /** Leaving a world drops every effect, so nothing orphaned survives into the next one. */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (!event.getLevel().isClientSide()) return;
        ClientTurret.clear();
        TracerRenderer.clear();
        SmokeTrailRenderer.clear();
        ArcRenderer.clear();
        BeamRenderer.clear();
        RailRenderer.clear();
        TeslaEffects.clear();
        MortarEffects.clear();
        BurnEffects.clear();
        DecalRenderer.clear();
        CasingRenderer.clear();
        DynamicLightManager.clear();
    }
}

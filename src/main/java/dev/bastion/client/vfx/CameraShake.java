package dev.bastion.client.vfx;

import dev.bastion.Bastion;
import dev.bastion.config.BastionClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Trauma-model camera shake (PLAN 5.6): sources add trauma scaled by distance, shake = trauma^2,
 * trauma decays every tick. Disabled by the cameraShake client option.
 */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID, value = Dist.CLIENT)
public final class CameraShake {
    private static final float MAX_YAW = 2.4f, MAX_PITCH = 2.0f, MAX_ROLL = 3.0f, DECAY_PER_TICK = 0.045f;
    private static float trauma, prevTrauma, time;

    private CameraShake() {
    }

    static void add(Vec3 source, float amount, float radius) {
        if (!BastionClientConfig.CAMERA_SHAKE.get() || Minecraft.getInstance().player == null) return;
        double distance = Minecraft.getInstance().player.getEyePosition().distanceTo(source);
        if (distance >= radius) return;
        trauma = Mth.clamp(trauma + amount * (float) (1 - distance / radius), 0, 1);
    }

    static void tick() {
        prevTrauma = trauma;
        trauma = Math.max(0, trauma - DECAY_PER_TICK);
        time += 1;
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        float t = Mth.lerp((float) event.getPartialTick(), prevTrauma, trauma);
        if (t <= 0) return;
        float shake = t * t;
        float s = time + (float) event.getPartialTick();
        event.setYaw(event.getYaw() + MAX_YAW * shake * wave(s, 1.3f, 0.0f));
        event.setPitch(event.getPitch() + MAX_PITCH * shake * wave(s, 1.7f, 2.1f));
        event.setRoll(event.getRoll() + MAX_ROLL * shake * wave(s, 2.3f, 4.2f));
    }

    /** Cheap smooth noise in -1..1: two detuned sines. */
    private static float wave(float s, float speed, float phase) {
        return (Mth.sin(s * speed + phase) + Mth.sin(s * speed * 2.17f + phase * 1.3f)) * 0.5f;
    }
}

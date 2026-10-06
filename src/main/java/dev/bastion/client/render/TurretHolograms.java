package dev.bastion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.bastion.Bastion;
import dev.bastion.client.ClientTurret;
import dev.bastion.registry.BastionSounds;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretState;
import dev.bastion.weapon.WeaponData;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * Lock-on visuals for weapons with Precision Lock (PLAN 7.1): a hologram reticle spins above the sensor,
 * shrinking and shifting from white to the energy colour as the lock completes (beep when it does), and a
 * faint aiming laser runs from the sensor to the target while the turret engages.
 */
public final class TurretHolograms {
    private static final ResourceLocation LASER = Bastion.id("textures/vfx/tracer.png");
    private static final Set<Long> LOCKED = new HashSet<>();

    private TurretHolograms() {
    }

    /** Once per client tick: the lock beep, played once per lock. */
    public static void tick(ClientLevel level) {
        for (ClientTurret client : ClientTurret.all()) {
            if (!(level.getBlockEntity(client.pos) instanceof TurretBaseBlockEntity turret)) continue;
            int needed = lockTicks(turret);
            boolean locked = needed > 0 && client.lockProgress(needed) >= 1;
            if (locked && LOCKED.add(client.pos.asLong()) && client.sensor != null) {
                level.playLocalSound(client.sensor.x, client.sensor.y, client.sensor.z, BastionSounds.GUN_LOCK.get(),
                        SoundSource.HOSTILE, 0.6f, 1f, false);
            } else if (!locked) {
                LOCKED.remove(client.pos.asLong());
            }
        }
    }

    public static void render(PoseStack poseStack, MultiBufferSource buffers, Camera camera, float partialTick, ClientLevel level) {
        float time = level.getGameTime() + partialTick;
        for (ClientTurret client : ClientTurret.all()) {
            if (client.sensor == null || !(level.getBlockEntity(client.pos) instanceof TurretBaseBlockEntity turret)) continue;
            int charge = param(turret, "charge_ticks");
            // Only weapons that turn to aim get a laser sight while charging; the Tesla Coil charges without aiming.
            if (charge > 0 && turret.inventory().weaponData().aim().turnSpeed() > 0) laserSight(poseStack, buffers, camera, partialTick, level, client, turret, charge, time);
            int needed = lockTicks(turret);
            if (needed <= 0) continue;
            TurretState state = turret.clientState();
            boolean engaged = state == TurretState.ACQUIRING || state == TurretState.AIMING || state == TurretState.COOLDOWN
                    || state == TurretState.FIRING;
            if (!engaged) continue;
            int color = turret.inventory().weaponData().energyColor();
            float progress = client.lockProgress(needed);
            int argb = lerpColor(0xFFFFFF, color, progress);
            float size = Mth.lerp(progress, 0.9f, 0.5f);
            HologramRenderer.billboard(poseStack, buffers, camera, HologramRenderer.RETICLE, client.sensor.add(0, 0.45, 0),
                    size, time * (0.06f + 0.1f * progress), argb, 0.5f + 0.4f * progress, time);

            Entity target = level.getEntity(turret.clientTargetId());
            if (target != null) {
                VertexConsumer laser = buffers.getBuffer(BastionRenderTypes.additiveGlow(LASER));
                TrailRenderer.segment(laser, poseStack.last().pose(), camera.getPosition(), client.sensor,
                        target.getEyePosition(partialTick), 0.012f, color, 0.22f);
            }
        }
    }

    /**
     * Charged weapons (Sniper): while charging, a laser from the muzzle to the target that thickens and brightens
     * as the charge fills, with a glowing dot where it lands.
     */
    private static void laserSight(PoseStack poseStack, MultiBufferSource buffers, Camera camera, float partialTick, ClientLevel level,
                                   ClientTurret client, TurretBaseBlockEntity turret, int chargeTicks, float time) {
        if (turret.clientState() != TurretState.CHARGING || client.muzzles[0] == null) return;
        Entity target = level.getEntity(turret.clientTargetId());
        if (target == null) return;
        float progress = client.chargeProgress(level.getGameTime(), partialTick, chargeTicks);
        int color = turret.inventory().weaponData().energyColor();
        Vec3 end = target.getBoundingBox().getCenter().lerp(target.getEyePosition(partialTick), 0.5);
        VertexConsumer laser = buffers.getBuffer(BastionRenderTypes.additiveGlow(LASER));
        float flicker = 0.85f + 0.15f * Mth.sin(time * 2.1f);
        TrailRenderer.segment(laser, poseStack.last().pose(), camera.getPosition(), client.muzzles[0], end,
                0.03f + 0.05f * progress, color, (0.5f + 0.5f * progress) * flicker);                      // glow
        TrailRenderer.segment(laser, poseStack.last().pose(), camera.getPosition(), client.muzzles[0], end,
                0.012f + 0.012f * progress, 0xFFFFFF, 0.25f + 0.75f * progress);                            // hot core
        HologramRenderer.billboard(poseStack, buffers, camera, HologramRenderer.RETICLE, end, 0.35f + 0.3f * progress, time * 0.2f,
                color, 0.6f + 0.4f * progress, time);
    }

    /** Ticks to a Precision Lock for the mounted weapon, 0 when it has none. */
    private static int lockTicks(TurretBaseBlockEntity turret) {
        return param(turret, "precision_lock_ticks");
    }

    /** A whole-number weapon param, 0 when the mounted weapon does not define it. */
    private static int param(TurretBaseBlockEntity turret, String key) {
        WeaponData data = turret.inventory().weaponData();
        Float value = data == null ? null : data.params().get(key);
        return value == null ? 0 : Math.round(value);
    }

    private static int lerpColor(int a, int b, float t) {
        int r = (int) Mth.lerp(t, a >> 16 & 255, b >> 16 & 255);
        int g = (int) Mth.lerp(t, a >> 8 & 255, b >> 8 & 255);
        int bl = (int) Mth.lerp(t, a & 255, b & 255);
        return r << 16 | g << 8 | bl;
    }
}

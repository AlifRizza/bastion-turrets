package dev.bastion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.bastion.Bastion;
import dev.bastion.client.ClientTurret;
import dev.bastion.registry.BastionWeaponTypes;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretState;
import dev.bastion.weapon.WeaponData;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Laser Rifle charging: light gathers at the emitter (muzzle_0) as an orb that grows from a spark to full size over the
 * charge and pulses faster as it fills, with a lens-flare streak across it in the last stretch. Particles streaming into
 * it come from LaserEffects. Halo alpha-blended (reads by day), glow and core additive.
 */
public final class LaserChargeRenderer {
    private static final ResourceLocation ORB = Bastion.id("textures/vfx/orb.png"), FLARE = Bastion.id("textures/vfx/flare.png");

    private LaserChargeRenderer() {
    }

    private record Orb(Vec3 at, float progress, int argb) {
    }

    public static void render(PoseStack poseStack, MultiBufferSource buffers, Camera camera, float partialTick, ClientLevel level) {
        List<Orb> orbs = new ArrayList<>();
        for (ClientTurret client : ClientTurret.all()) {
            if (client.muzzles[0] == null || client.chargeStart < 0) continue;
            if (!(level.getBlockEntity(client.pos) instanceof TurretBaseBlockEntity turret) || turret.clientState() != TurretState.CHARGING) continue;
            WeaponData data = turret.inventory().weaponData();
            if (data == null || !data.type().equals(BastionWeaponTypes.LASER_RIFLE.getId())) continue;
            int charge = Math.round(data.params().getOrDefault("charge_ticks", 1f));
            orbs.add(new Orb(client.muzzles[0], client.chargeProgress(level.getGameTime(), partialTick, charge), data.energyColor()));
        }
        if (orbs.isEmpty()) return;
        float time = level.getGameTime() + partialTick;
        Matrix4f pose = poseStack.last().pose();
        Vec3 cam = camera.getPosition();
        Quaternionf facing = camera.rotation();
        VertexConsumer halo = buffers.getBuffer(BastionRenderTypes.translucentGlow(ORB));
        for (Orb o : orbs) billboard(halo, pose, cam, facing, o.at, size(o, time, 0.12f, 0.9f), size(o, time, 0.12f, 0.9f), ArcRenderer.deepen(o.argb), 0.55f);
        VertexConsumer glow = buffers.getBuffer(BastionRenderTypes.additiveGlow(ORB));
        for (Orb o : orbs) {
            billboard(glow, pose, cam, facing, o.at, size(o, time, 0.07f, 0.62f), size(o, time, 0.07f, 0.62f), o.argb, 1f);
            billboard(glow, pose, cam, facing, o.at, size(o, time, 0.03f, 0.25f), size(o, time, 0.03f, 0.25f), 0xFFFFFF, 1f);
        }
        VertexConsumer flare = buffers.getBuffer(BastionRenderTypes.additiveGlow(FLARE));
        for (Orb o : orbs) {
            float f = Mth.clamp((o.progress - 0.65f) / 0.35f, 0, 1);
            if (f > 0) billboard(flare, pose, cam, facing, o.at, 2.4f * f * f, 0.14f * f, o.argb, f);
        }
    }

    /** Half-size from {@code small} at the start of the charge to {@code full} at its end, with a pulse that quickens. */
    private static float size(Orb o, float time, float small, float full) {
        float eased = o.progress * o.progress;
        return Mth.lerp(eased, small, full) * (1 + 0.12f * Mth.sin(time * (0.5f + 2.5f * o.progress)));
    }

    private static void billboard(VertexConsumer buffer, Matrix4f pose, Vec3 cam, Quaternionf facing, Vec3 at, float halfW, float halfH,
                                  int rgb, float alpha) {
        Vec3 c = at.subtract(cam);
        float[][] corners = {{-1, -1, 0, 1}, {-1, 1, 0, 0}, {1, 1, 1, 0}, {1, -1, 1, 1}};
        for (float[] k : corners) {
            Vector3f p = facing.transform(new Vector3f(k[0] * halfW, k[1] * halfH, 0)).add((float) c.x, (float) c.y, (float) c.z);
            buffer.vertex(pose, p.x, p.y, p.z).color((rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f, alpha)
                    .uv(k[2], k[3]).uv2(LightTexture.FULL_BRIGHT).endVertex();
        }
    }
}

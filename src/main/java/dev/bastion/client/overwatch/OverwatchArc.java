package dev.bastion.client.overwatch;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.bastion.Bastion;
import dev.bastion.client.render.BastionRenderTypes;
import dev.bastion.client.render.TrailRenderer;
import dev.bastion.registry.BastionWeaponTypes;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.weapon.Ballistics;
import dev.bastion.weapon.WeaponData;
import dev.bastion.weapon.WeaponType;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Overwatch arc indicator (PLAN "Overwatch Link"): for a ballistic weapon, glowing dashes along the shell's path from the
 * muzzle to the first block in its way and a ring of the blast radius where it lands; red when it can't fire there.
 */
public final class OverwatchArc {
    private static final ResourceLocation LASER = Bastion.id("textures/vfx/tracer.png");
    private static final int RED = 0xFF3A30, RING_SEGMENTS = 32;
    /** Ticks of flight per dash (and per gap). */
    private static final double DASH = 1;

    private OverwatchArc() {
    }

    public static void render(PoseStack poseStack, MultiBufferSource buffers, Camera camera, float partialTick, ClientLevel level) {
        TurretBaseBlockEntity base = OverwatchClient.turret(level);
        Vec3 point = OverwatchClient.aimPoint();
        WeaponData data = base == null ? null : base.inventory().weaponData();
        WeaponType type = data == null ? null : BastionWeaponTypes.REGISTRY.get().getValue(data.type());
        if (point == null || type == null || !type.ballistic()) return;
        boolean ok = OverwatchClient.canFire();
        int colour = ok ? data.energyColor() : RED;
        VertexConsumer glow = buffers.getBuffer(BastionRenderTypes.additiveGlow(LASER));
        Matrix4f pose = poseStack.last().pose();
        Vec3 cam = camera.getPosition(), landing = point;
        Ballistics.Launch launch = ok ? Ballistics.solve(base, data, point) : null;
        if (launch != null) {
            double gravity = data.param("gravity");
            Vec3 last = launch.muzzle();
            int step = 0;
            for (double t = DASH; ; t += DASH) {
                Vec3 next = launch.at(Math.min(t, launch.ticks()), gravity);
                BlockHitResult hit = level.clip(new ClipContext(last, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, Minecraft.getInstance().player));
                boolean blocked = hit.getType() != HitResult.Type.MISS;
                if (blocked) next = hit.getLocation();
                if (step++ % 2 == 0) TrailRenderer.segment(glow, pose, cam, last, next, 0.06f, colour, 0.85f);
                if (blocked || t >= launch.ticks()) {
                    landing = next;
                    break;
                }
                last = next;
            }
        }
        ring(glow, pose, cam, landing, data.params().getOrDefault("blast_radius", 1f), colour);
    }

    /** A flat circle just above {@code centre}. */
    private static void ring(VertexConsumer glow, Matrix4f pose, Vec3 cam, Vec3 centre, double radius, int colour) {
        Vec3 last = null;
        for (int i = 0; i <= RING_SEGMENTS; i++) {
            double a = i * 2 * Math.PI / RING_SEGMENTS;
            Vec3 p = centre.add(Math.cos(a) * radius, 0.05, Math.sin(a) * radius);
            if (last != null) TrailRenderer.segment(glow, pose, cam, last, p, 0.05f, colour, 0.9f);
            last = p;
        }
    }
}

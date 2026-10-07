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
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Railgun light outside the model (the model's own glow turns violet and its coil bands light up one by one while it
 * charges: ClientTurret, WeaponModel). Once the charge is full a white-hot core burns in the slot between the rails, from
 * the breech (muzzle_1) to the muzzle (muzzle_0), pulsing until the shot, flashing with it and fading. After a shot an
 * ion trail hangs along the slug's path: a core that narrows and a double helix that widens as both fade, each with an
 * alpha-blended pass in a deeper shade under the additive one, so it reads against a daytime sky.
 */
public final class RailRenderer {
    private static final ResourceLocation STREAK = Bastion.id("textures/vfx/tracer.png"), SHAFT = Bastion.id("textures/vfx/arc.png");
    /** Share of the charge that fills the channel; the rest is the full barrel crackling (RailgunEffects). */
    public static final float FILL = 0.65f;
    private static final int AFTERGLOW = 14, TRAIL_LIFE = 34, MAX_TRAILS = 32;
    private static final List<Trail> TRAILS = new ArrayList<>();

    private RailRenderer() {
    }

    /** An ion trail out of {@code from}; its head keeps up with the slug ({@code speed} blocks per tick) until it lands. */
    public static Trail trail(Vec3 from, Vec3 direction, double speed, double range, int argb) {
        if (TRAILS.size() >= MAX_TRAILS) TRAILS.remove(0);
        Trail trail = new Trail(from, direction.normalize(), speed, range, argb);
        TRAILS.add(trail);
        return trail;
    }

    public static void tick() {
        TRAILS.removeIf(t -> ++t.age > TRAIL_LIFE);
    }

    public static void clear() {
        TRAILS.clear();
    }

    private record Channel(Vec3 breech, Vec3 muzzle, float glow, int argb) {
    }

    public static void render(PoseStack poseStack, MultiBufferSource buffers, Camera camera, float partialTick, ClientLevel level) {
        List<Channel> channels = channels(level, partialTick);
        if (channels.isEmpty() && TRAILS.isEmpty()) return;
        Matrix4f pose = poseStack.last().pose();
        Vec3 cam = camera.getPosition();
        VertexConsumer halo = buffers.getBuffer(BastionRenderTypes.translucentGlow(SHAFT));
        for (Trail t : TRAILS) t.render(halo, pose, cam, partialTick, true);
        VertexConsumer streak = buffers.getBuffer(BastionRenderTypes.additiveGlow(STREAK));
        for (Channel c : channels) {
            TrailRenderer.segment(streak, pose, cam, c.breech, c.muzzle, 0.2f, c.argb, Math.min(1, 0.9f * c.glow));
            TrailRenderer.segment(streak, pose, cam, c.breech, c.muzzle, 0.06f, 0xFFFFFF, Math.min(1, c.glow));
        }
        for (Trail t : TRAILS) t.render(streak, pose, cam, partialTick, false);
    }

    /** Fully charged railguns (pulsing) and ones that just fired (flash, fading). */
    private static List<Channel> channels(ClientLevel level, float partialTick) {
        List<Channel> out = new ArrayList<>();
        float time = level.getGameTime() + partialTick;
        for (ClientTurret client : ClientTurret.all()) {
            Vec3 breech = client.muzzles[1], muzzle = client.muzzles[0];
            if (breech == null || muzzle == null || !(level.getBlockEntity(client.pos) instanceof TurretBaseBlockEntity turret)) continue;
            WeaponData data = turret.inventory().weaponData();
            if (data == null || !data.type().equals(BastionWeaponTypes.RAILGUN.getId())) continue;
            float since = level.getGameTime() - client.lastShotTick + partialTick;
            if (turret.clientState() == TurretState.CHARGING && client.chargeStart >= 0) {
                float progress = client.chargeProgress(level.getGameTime(), partialTick, Math.round(data.param("charge_ticks")));
                if (progress >= FILL) out.add(new Channel(breech, muzzle, 0.75f + 0.25f * Mth.sin(time * 1.7f), data.energyColor()));
            } else if (since < AFTERGLOW) {
                out.add(new Channel(breech, muzzle, 1.6f * (1 - since / AFTERGLOW), data.energyColor()));
            }
        }
        return out;
    }

    public static final class Trail {
        private final Vec3 from, direction, side, up;
        private final double speed;
        private final int argb;
        private double length;
        private int age;

        private Trail(Vec3 from, Vec3 direction, double speed, double range, int argb) {
            this.from = from;
            this.direction = direction;
            this.speed = speed;
            this.length = range;
            this.argb = argb;
            Vec3 axis = Math.abs(direction.y) < 0.99 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
            this.side = direction.cross(axis).normalize();
            this.up = direction.cross(side);
        }

        /** Where the slug stopped: the trail ends there. */
        public void land(Vec3 end) {
            length = Math.min(length, from.distanceTo(end));
        }

        /** {@code halo}: the alpha-blended pass in a deeper shade (reads by day); otherwise the additive glow and core. */
        private void render(VertexConsumer buffer, Matrix4f pose, Vec3 cam, float partialTick, boolean halo) {
            float age = this.age + partialTick, a = Mth.clamp(age / TRAIL_LIFE, 0, 1);
            double head = Math.min(length, speed * (age + 1));
            Vec3 end = from.add(direction.scale(head));
            float fade = a < 0.15f ? 1 : (1 - (a - 0.15f) / 0.85f) * (1 - (a - 0.15f) / 0.85f);
            float narrow = 1 - 0.7f * a;
            int deep = ArcRenderer.deepen(argb);
            if (halo) {
                TrailRenderer.segment(buffer, pose, cam, from, end, 0.55f * narrow, deep, 0.35f * fade);
            } else {
                TrailRenderer.segment(buffer, pose, cam, from, end, 0.4f * narrow, argb, 0.8f * fade);
                TrailRenderer.segment(buffer, pose, cam, from, end, 0.1f * narrow, 0xFFFFFF, fade);
            }
            // Double helix: two strands a half turn apart that widen and twist slowly as the trail fades.
            double step = Math.max(0.3, head / 120), radius = 0.15 + 0.45 * a;
            for (int strand = 0; strand < 2; strand++) {
                Vec3 last = null;
                for (double d = 0; d <= head; d += step) {
                    double angle = d * 2.4 + strand * Math.PI + age * 0.15;
                    Vec3 point = from.add(direction.scale(d)).add(side.scale(Math.cos(angle) * radius)).add(up.scale(Math.sin(angle) * radius));
                    if (last != null) {
                        if (halo) TrailRenderer.segment(buffer, pose, cam, last, point, 0.09f, deep, 0.55f * fade);
                        else TrailRenderer.segment(buffer, pose, cam, last, point, 0.06f, argb, 0.9f * fade);
                    }
                    last = point;
                }
            }
        }
    }
}

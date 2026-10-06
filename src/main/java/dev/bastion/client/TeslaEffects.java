package dev.bastion.client;

import dev.bastion.client.render.ArcRenderer;
import dev.bastion.client.vfx.VfxManager;
import dev.bastion.client.vfx.VfxParams;
import dev.bastion.client.vfx.VfxPresets;
import dev.bastion.network.TurretFireEvent;
import dev.bastion.registry.BastionSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Tesla Coil on the client. The discharge: a bolt from the terminal (muzzle_0) to each target, following it, with a
 * flash at both ends; the target then crawls with small arcs for a moment. While charging, arcs leap off the crown
 * (muzzle_1..4) and the terminal, more often as the charge fills; idle, a spark jumps off the crown now and then.
 */
public final class TeslaEffects {
    private static final double FAR_SOUND_DISTANCE = 32;
    private static final int SHOCK_TICKS = 14;
    /** Entity id -> ticks left and arc colour, for the crawling arcs after a hit. */
    private static final Map<Integer, int[]> SHOCKED = new HashMap<>();

    private TeslaEffects() {
    }

    public static void discharge(ClientLevel level, TurretFireEvent event, Vec3 terminal, int color) {
        VfxManager.play(VfxPresets.TESLA_DISCHARGE, terminal, new Vec3(0, 1, 0), VfxParams.of().color(color).seed(event.seed()));
        int i = 0;
        for (TurretFireEvent.Shot shot : event.shots()) {
            Entity target = level.getEntity(shot.blockState());
            Supplier<Vec3> to = target != null ? () -> target.getBoundingBox().getCenter() : shot::end;
            long seed = event.seed() + i * 7919L;
            ArcRenderer.spawn(() -> terminal, to, color, 0.1f, 7, 2, 3, 0.16f, seed);
            ArcRenderer.spawn(() -> terminal, to, color, 0.045f, 5, 1, 1, 0.22f, seed + 1); // a thinner twin: the bolt looks braided
            VfxManager.play(VfxPresets.TESLA_IMPACT, to.get(), shot.normal(), VfxParams.of().color(color).normal(shot.normal()).seed(seed));
            if (target != null) SHOCKED.put(target.getId(), new int[]{SHOCK_TICKS, color});
            i++;
        }
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        boolean far = player.position().distanceTo(terminal) > FAR_SOUND_DISTANCE;
        RandomSource random = RandomSource.create(event.seed());
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance((far ? BastionSounds.TESLA_ZAP_TAIL : BastionSounds.TESLA_ZAP).get(),
                SoundSource.HOSTILE, far ? 1.6f : 1.2f, 0.92f + random.nextFloat() * 0.16f, random, terminal.x, terminal.y, terminal.z));
    }

    /** Each tick while charging: arcs leap between the crown and the terminal, or off into the air. */
    public static void charging(ClientLevel level, ClientTurret client, float progress, int color) {
        Vec3 terminal = client.muzzles[0];
        RandomSource random = level.random;
        if (terminal == null || random.nextFloat() > 0.3f + 0.6f * progress) return;
        Vec3 rim = client.muzzles[1 + random.nextInt(4)];
        Vec3 from = rim != null && random.nextBoolean() ? rim : terminal;
        Vec3 out = new Vec3(random.nextGaussian(), Math.abs(random.nextGaussian()) * 0.6, random.nextGaussian()).normalize();
        Vec3 to = rim != null && from == terminal && random.nextBoolean() ? rim : from.add(out.scale(0.7 + 0.8 * random.nextFloat()));
        ArcRenderer.spawn(() -> from, () -> to, color, 0.05f, 2, 1, 1, 0.28f, random.nextLong());
    }

    /** Each tick while idle and armed: once in a while a short spark jumps off the crown, with a faint crackle. */
    public static void idle(ClientLevel level, ClientTurret client, int color) {
        RandomSource random = level.random;
        Vec3 rim = client.muzzles[1 + random.nextInt(4)];
        if (rim == null || random.nextInt(45) != 0) return;
        Vec3 out = rim.subtract(client.muzzles[0] != null ? client.muzzles[0] : rim).add(0, 0.2, 0);
        out = out.lengthSqr() < 1e-4 ? new Vec3(0, 1, 0) : out.normalize();
        Vec3 to = rim.add(out.add(random.nextGaussian() * 0.4, random.nextGaussian() * 0.4, random.nextGaussian() * 0.4).normalize()
                .scale(0.35 + 0.35 * random.nextFloat()));
        ArcRenderer.spawn(() -> rim, () -> to, color, 0.03f, 2, 1, 0, 0.3f, random.nextLong());
        VfxManager.play(VfxPresets.TESLA_SPARKLE, rim, out, VfxParams.of().color(color).seed(random.nextLong()));
        level.playLocalSound(rim.x, rim.y, rim.z, BastionSounds.TESLA_CRACKLE.get(), SoundSource.HOSTILE, 0.35f,
                0.9f + random.nextFloat() * 0.2f, false);
    }

    /** Struck targets crawl with small arcs between random points of their body for a moment. */
    public static void tick(ClientLevel level) {
        for (Iterator<Map.Entry<Integer, int[]>> it = SHOCKED.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, int[]> entry = it.next();
            Entity entity = level.getEntity(entry.getKey());
            if (entity == null || !entity.isAlive() || --entry.getValue()[0] <= 0) {
                it.remove();
                continue;
            }
            RandomSource random = level.random;
            if (random.nextFloat() > 0.7f) continue;
            Vec3 a = onBody(entity, random), b = onBody(entity, random);
            ArcRenderer.spawn(() -> entity.position().add(a), () -> entity.position().add(b), entry.getValue()[1], 0.025f, 2, 1, 0,
                    0.3f, random.nextLong());
        }
    }

    public static void clear() {
        SHOCKED.clear();
    }

    /** A random point on the surface of the entity's box, relative to its position. */
    private static Vec3 onBody(Entity entity, RandomSource random) {
        AABB box = entity.getBoundingBox().move(entity.position().reverse());
        double x = box.minX + random.nextFloat() * box.getXsize(), y = box.minY + random.nextFloat() * box.getYsize(),
                z = box.minZ + random.nextFloat() * box.getZsize();
        return switch (random.nextInt(4)) {
            case 0 -> new Vec3(box.minX, y, z);
            case 1 -> new Vec3(box.maxX, y, z);
            case 2 -> new Vec3(x, y, box.minZ);
            default -> new Vec3(x, y, box.maxZ);
        };
    }
}

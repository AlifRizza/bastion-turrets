package dev.bastion.client;

import dev.bastion.client.vfx.DynamicLightManager;
import dev.bastion.client.vfx.VfxManager;
import dev.bastion.client.vfx.VfxParams;
import dev.bastion.client.vfx.VfxPresets;
import dev.bastion.registry.BastionSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Entities burning from turret flames (TurretBurn, told by BurnSync): flames lick over the body, it glows, it crackles,
 * until the time runs out or the server says it was put out. Your own fire is not drawn in first person, it would
 * cover the screen.
 */
public final class BurnEffects {
    /** Bastion fire orange: the burn has no turret to take a colour from. */
    private static final int FIRE = 0xFFFF7A1E;
    private static final Map<Integer, Integer> BURNING = new HashMap<>();

    private BurnEffects() {
    }

    public static void set(int entityId, int ticks) {
        if (ticks <= 0) BURNING.remove(entityId);
        else BURNING.put(entityId, ticks);
    }

    public static void tick(ClientLevel level) {
        Minecraft mc = Minecraft.getInstance();
        for (Iterator<Map.Entry<Integer, Integer>> it = BURNING.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Integer> entry = it.next();
            Entity entity = level.getEntity(entry.getKey());
            if (entity == null || !entity.isAlive() || entry.getValue() <= 1) {
                it.remove();
                continue;
            }
            entry.setValue(entry.getValue() - 1);
            if (entity == mc.player && mc.options.getCameraType().isFirstPerson()) continue;
            RandomSource random = level.random;
            AABB box = entity.getBoundingBox();
            Vec3 at = new Vec3(box.minX + box.getXsize() * (0.15 + 0.7 * random.nextFloat()), box.minY + box.getYsize() * (0.05 + 0.85 * random.nextFloat()),
                    box.minZ + box.getZsize() * (0.15 + 0.7 * random.nextFloat()));
            VfxManager.play(VfxPresets.BURNING, at, new Vec3(0, 1, 0), VfxParams.of().color(FIRE).seed(random.nextLong()));
            if (entry.getValue() % 5 == 0) DynamicLightManager.add(box.getCenter(), 10, 6);
            if (random.nextInt(30) == 0) {
                level.playLocalSound(at.x, at.y, at.z, BastionSounds.BURNING.get(), SoundSource.HOSTILE, 0.5f, 0.9f + random.nextFloat() * 0.2f, false);
            }
        }
    }

    public static void clear() {
        BURNING.clear();
    }
}

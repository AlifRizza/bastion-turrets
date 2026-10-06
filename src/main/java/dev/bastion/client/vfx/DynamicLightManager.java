package dev.bastion.client.vfx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Short-lived block light from muzzle flashes and impacts (PLAN 5.5). LevelRendererMixin asks
 * {@link #apply} for every block light lookup; changes mark the affected chunk sections for re-render.
 * Budget: {@link VfxManager#maxDynamicLights()} sources (weakest/oldest dropped first) and a cap on
 * section rebuilds per tick. Lookups run on chunk-build threads, so readers see an immutable snapshot.
 */
public final class DynamicLightManager {
    private static final int MAX_SECTION_UPDATES_PER_TICK = 48;
    private static final List<Light> LIGHTS = new ArrayList<>();
    private static volatile List<Light> snapshot = List.of();
    private static final Set<Long> dirtySections = new HashSet<>();

    private DynamicLightManager() {
    }

    /** One-shot flash: light {@code level} (0-15) at {@code pos} for {@code ticks}. */
    public static void add(Vec3 pos, int level, int ticks) {
        add(null, pos, level, ticks);
    }

    /**
     * Keyed light: re-adding the same key refreshes it in place instead of stacking, so a Machine Gun
     * keeps one light alive while it fires (PLAN 7.2).
     */
    public static void add(Object key, Vec3 pos, int level, int ticks) {
        int max = VfxManager.maxDynamicLights();
        if (max <= 0 || level <= 0) return;
        if (key != null) {
            for (Light light : LIGHTS) {
                if (key.equals(light.key) && light.pos.distanceToSqr(pos) < 0.25) {
                    light.ticksLeft = Math.max(light.ticksLeft, ticks);
                    return;
                }
            }
            LIGHTS.removeIf(light -> {
                boolean same = key.equals(light.key);
                if (same) markDirty(light);
                return same;
            });
        }
        Light light = new Light(key, pos, Mth.clamp(level, 1, 15), ticks);
        LIGHTS.add(light);
        while (LIGHTS.size() > max) {
            Light drop = LIGHTS.stream().min(Comparator.comparingInt((Light l) -> l.level).thenComparingInt(l -> l.ticksLeft)).orElseThrow();
            LIGHTS.remove(drop);
            markDirty(drop);
        }
        markDirty(light);
        publish();
    }

    static void tick() {
        boolean changed = LIGHTS.removeIf(light -> {
            boolean expired = --light.ticksLeft <= 0;
            if (expired) markDirty(light);
            return expired;
        });
        if (changed) publish();
        flushDirty();
    }

    static void clear() {
        LIGHTS.clear();
        dirtySections.clear();
        publish();
    }

    /** Packed light with the dynamic block light merged in (max of both). */
    public static int apply(BlockPos pos, int packedLight) {
        List<Light> lights = snapshot;
        if (lights.isEmpty()) return packedLight;
        double cx = pos.getX() + 0.5, cy = pos.getY() + 0.5, cz = pos.getZ() + 0.5;
        int best = 0;
        for (Light light : lights) {
            double d = Math.sqrt(light.pos.distanceToSqr(cx, cy, cz));
            int value = (int) (light.level - d); // 1 level per block, like vanilla block light
            if (value > best) best = value;
        }
        int block = LightTexture.block(packedLight);
        return best > block ? LightTexture.pack(best, LightTexture.sky(packedLight)) : packedLight;
    }

    private static void publish() {
        snapshot = List.copyOf(LIGHTS);
    }

    private static void markDirty(Light light) {
        int r = light.level;
        BlockPos min = BlockPos.containing(light.pos.add(-r, -r, -r)), max = BlockPos.containing(light.pos.add(r, r, r));
        for (int sx = SectionPos.blockToSectionCoord(min.getX()); sx <= SectionPos.blockToSectionCoord(max.getX()); sx++)
            for (int sy = SectionPos.blockToSectionCoord(min.getY()); sy <= SectionPos.blockToSectionCoord(max.getY()); sy++)
                for (int sz = SectionPos.blockToSectionCoord(min.getZ()); sz <= SectionPos.blockToSectionCoord(max.getZ()); sz++)
                    dirtySections.add(SectionPos.asLong(sx, sy, sz));
    }

    private static void flushDirty() {
        if (dirtySections.isEmpty()) return;
        var renderer = Minecraft.getInstance().levelRenderer;
        var it = dirtySections.iterator();
        for (int i = 0; i < MAX_SECTION_UPDATES_PER_TICK && it.hasNext(); i++) {
            long section = it.next();
            it.remove();
            renderer.setSectionDirty(SectionPos.x(section), SectionPos.y(section), SectionPos.z(section));
        }
    }

    private static final class Light {
        final Object key;
        final Vec3 pos;
        final int level;
        int ticksLeft;

        Light(Object key, Vec3 pos, int level, int ticksLeft) {
            this.key = key;
            this.pos = pos;
            this.level = level;
            this.ticksLeft = ticksLeft;
        }
    }
}

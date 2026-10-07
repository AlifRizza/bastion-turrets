package dev.bastion.client;

import dev.bastion.client.render.ArcRenderer;
import dev.bastion.client.render.RailRenderer;
import dev.bastion.client.render.TracerRenderer;
import dev.bastion.client.vfx.VfxManager;
import dev.bastion.client.vfx.VfxParams;
import dev.bastion.client.vfx.VfxPresets;
import dev.bastion.network.TurretFireEvent;
import dev.bastion.registry.BastionSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Railgun on the client (user: the barrel lights up, then electricity runs over the whole barrel like the Tesla Coil,
 * then a very fast slug). The light is RailRenderer; once the channel is full, arcs crawl along the rails, wrap from one
 * rail round to the other and leap off into the air. The shot: a heavy streak (the slug, flying with the server's bullet),
 * a flash and three rings punched through the air ahead of the muzzle, the ion trail; where it lands, a flash, a violet
 * shockwave, debris, a thud; steam from the vents a moment later.
 */
public final class RailgunEffects {
    private static final double FAR_SOUND_DISTANCE = 32;
    /** Rail geometry in blocks (design_weapons.py railgun): upper rail from 1.5 to 5.5 px above the axis, 4.5 px half-wide. */
    private static final double RAIL_INNER = 1.5 / 16, RAIL_OUTER = 5.5 / 16, RAIL_HALF = 4.5 / 16;

    private RailgunEffects() {
    }

    /** The barrel's frame from its locators: breech and muzzle on the axis, up toward the upper rail. */
    private record Rails(Vec3 breech, Vec3 along, Vec3 up, Vec3 side) {
        @Nullable
        static Rails of(ClientTurret client) {
            Vec3 muzzle = client.muzzles[0], breech = client.muzzles[1], upper = client.muzzles[2];
            if (muzzle == null || breech == null || upper == null) return null;
            Vec3 along = muzzle.subtract(breech), up = upper.subtract(muzzle).normalize();
            return new Rails(breech, along, up, along.normalize().cross(up).normalize());
        }

        /** A point {@code t} (0 breech, 1 muzzle) along the barrel, offset {@code dy} up and {@code dx} sideways. */
        Vec3 at(double t, double dy, double dx) {
            return breech.add(along.scale(t)).add(up.scale(dy)).add(side.scale(dx));
        }
    }

    /** Each tick while charging; arcs only once the channel is full of light. */
    public static void charging(ClientLevel level, ClientTurret client, float progress, int color) {
        Rails rails = progress < RailRenderer.FILL ? null : Rails.of(client);
        if (rails == null) return;
        RandomSource random = level.random;
        for (int i = 0, n = 3 + random.nextInt(2); i < n; i++) {
            double t = 0.05 + random.nextDouble() * 0.95;
            double sign = random.nextBoolean() ? 1 : -1, flank = random.nextBoolean() ? RAIL_HALF : -RAIL_HALF;
            Vec3 from = rails.at(t, sign * (RAIL_INNER + random.nextDouble() * (RAIL_OUTER - RAIL_INNER)), flank);
            Vec3 to = switch (random.nextInt(3)) {
                // along the same rail a little way
                case 0 -> rails.at(Math.min(1, t + 0.06 + random.nextDouble() * 0.12), sign * (RAIL_INNER + random.nextDouble() * (RAIL_OUTER - RAIL_INNER)), flank);
                // round the flank onto the other rail
                case 1 -> rails.at(t + (random.nextDouble() - 0.5) * 0.08, -sign * (RAIL_INNER + random.nextDouble() * (RAIL_OUTER - RAIL_INNER)), flank);
                // off into the air
                default -> from.add(rails.side.scale(Math.signum(flank)).add(rails.up.scale(sign * 0.5)).normalize()
                        .add(random.nextGaussian() * 0.3, random.nextGaussian() * 0.3, random.nextGaussian() * 0.3).normalize()
                        .scale(0.45 + 0.5 * random.nextDouble()));
            };
            ArcRenderer.spawn(() -> from, () -> to, color, 0.065f, 3, 1, 1, 0.3f, random.nextLong());
            if (random.nextInt(4) == 0) {
                VfxManager.play(VfxPresets.TESLA_SPARKLE, from, rails.side.scale(Math.signum(flank)), VfxParams.of().color(color).seed(random.nextLong()));
            }
        }
        if (random.nextInt(3) == 0) {
            Vec3 at = rails.at(random.nextDouble(), 0, 0);
            level.playLocalSound(at.x, at.y, at.z, BastionSounds.TESLA_CRACKLE.get(), SoundSource.HOSTILE, 0.5f, 0.8f + random.nextFloat() * 0.3f, false);
        }
    }

    public static void fire(ClientLevel level, TurretFireEvent event, ClientTurret client, Vec3 muzzle, int color) {
        if (event.shots().isEmpty()) return;
        TurretFireEvent.Shot shot = event.shots().get(0);
        Vec3 direction = shot.end().subtract(muzzle).normalize();
        VfxManager.play(VfxPresets.RAIL_MUZZLE, muzzle, direction, VfxParams.of().color(color).seed(event.seed()));
        for (int i = 0; i < 3; i++) {
            VfxManager.play(VfxPresets.RAIL_RING, muzzle.add(direction.scale(0.6 + 1.3 * i)), direction,
                    VfxParams.of().color(color).scale(1 + 0.45f * i).seed(event.seed() + i));
        }
        RailRenderer.Trail trail = RailRenderer.trail(muzzle, direction, event.speed(), shot.end().distanceTo(muzzle), color);
        TracerRenderer.spawnBullet(shot.blockState(), muzzle, shot.end(), event.speed(), color, 0.2f, (end, normal, hit, blockState) -> {
            trail.land(end);
            land(level, end, normal, hit, blockState, color, event.seed());
        });
        client.after(12, () -> {
            if (client.eject != null) VfxManager.play(VfxPresets.OVERHEAT_STEAM, client.eject, new Vec3(0, 1, 0), VfxParams.of().seed(event.seed()));
        });
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        boolean far = player.position().distanceTo(muzzle) > FAR_SOUND_DISTANCE;
        RandomSource random = RandomSource.create(event.seed());
        Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance((far ? BastionSounds.RAILGUN_FIRE_TAIL : BastionSounds.RAILGUN_FIRE).get(),
                SoundSource.HOSTILE, far ? 2.4f : 1.6f, 0.94f + random.nextFloat() * 0.12f, random, muzzle.x, muzzle.y, muzzle.z));
    }

    private static void land(ClientLevel level, Vec3 end, Vec3 normal, int hit, int blockState, int color, long seed) {
        int blockColor = hit == TurretFireEvent.BLOCK
                ? 0xFF000000 | Block.stateById(blockState).getMapColor(level, BlockPos.containing(end)).col : 0xFF6A6470;
        VfxParams params = VfxParams.of().color(color).normal(normal).blockColor(blockColor).seed(seed);
        VfxManager.play(VfxPresets.RAIL_IMPACT, end, normal.reverse(), params);
        if (hit == TurretFireEvent.BLOCK) VfxManager.play(VfxPresets.RAIL_GROUND, end, normal.reverse(), params);
        level.playLocalSound(end.x, end.y, end.z, BastionSounds.RAILGUN_IMPACT.get(), SoundSource.HOSTILE, 1.6f,
                0.92f + level.random.nextFloat() * 0.16f, false);
    }
}

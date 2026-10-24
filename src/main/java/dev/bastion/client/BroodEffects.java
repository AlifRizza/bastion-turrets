package dev.bastion.client;

import dev.bastion.Bastion;
import dev.bastion.client.vfx.VfxManager;
import dev.bastion.client.vfx.VfxParams;
import dev.bastion.client.vfx.VfxPresets;
import dev.bastion.network.TurretImpactEvent;
import dev.bastion.projectile.BroodCapsuleEntity;
import dev.bastion.registry.BastionSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Brood Cannon on the client (PLAN "Turret 3x3"): a wisp behind a capsule in flight, a thump and a ring of dust where it
 * lands, a hiss of gas and a glow as its petals open, and a spider's small death burst. The launch is TurretFireEffects
 * (WeaponFx.BROOD_CANNON).
 */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID, value = Dist.CLIENT)
public final class BroodEffects {
    private static final int GREEN = 0xFF9DFF6A, FLAME = 0xFFFF7A2E;
    private static final Vec3 UP = new Vec3(0, 1, 0);

    private BroodEffects() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (event.phase != TickEvent.Phase.END || level == null || Minecraft.getInstance().isPaused()) return;
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof BroodCapsuleEntity capsule && capsule.getDeltaMovement().lengthSqr() > 1e-4) {
                VfxManager.play(VfxPresets.SMOKE_WISP, capsule.position().add(0, 0.4, 0), capsule.getDeltaMovement().normalize().reverse(),
                        VfxParams.of().seed(level.getGameTime() * 31 + capsule.getId()));
            }
        }
    }

    /** BROOD_LAND, BROOD_OPEN and SPIDER_BURST (ClientPacketHandler). */
    public static void impact(ClientLevel level, TurretImpactEvent event) {
        Vec3 at = event.pos();
        long seed = at.hashCode();
        BlockPos below = BlockPos.containing(at.subtract(0, 0.1, 0));
        int blockColor = 0xFF000000 | level.getBlockState(below).getMapColor(level, below).col;
        switch (event.kind()) {
            case TurretImpactEvent.BROOD_LAND -> {
                VfxManager.play(VfxPresets.BROOD_LAND, at.add(0, 0.05, 0), UP, VfxParams.of().blockColor(blockColor).normal(UP).seed(seed));
                level.playLocalSound(at.x, at.y, at.z, BastionSounds.BROOD_CAPSULE_LAND.get(), SoundSource.HOSTILE, 1f, 0.95f + level.random.nextFloat() * 0.1f, false);
            }
            case TurretImpactEvent.BROOD_OPEN -> {
                VfxManager.play(VfxPresets.BROOD_OPEN, at.add(0, 0.4, 0), UP, VfxParams.of().color(GREEN).seed(seed));
                level.playLocalSound(at.x, at.y, at.z, BastionSounds.BROOD_CAPSULE_OPEN.get(), SoundSource.HOSTILE, 0.9f, 0.95f + level.random.nextFloat() * 0.1f, false);
            }
            case TurretImpactEvent.SPIDER_BURST -> {
                VfxManager.play(VfxPresets.SPIDER_BURST, at, UP, VfxParams.of().color(FLAME).blockColor(blockColor).scale(event.scale() / 2.5f).seed(seed));
                level.playLocalSound(at.x, at.y, at.z, BastionSounds.SPIDER_BURST.get(), SoundSource.HOSTILE, 1.2f, 0.95f + level.random.nextFloat() * 0.1f, false);
            }
            default -> {
            }
        }
    }
}

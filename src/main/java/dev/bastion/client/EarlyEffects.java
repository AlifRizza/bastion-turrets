package dev.bastion.client;

import dev.bastion.Bastion;
import dev.bastion.client.render.SmokeTrailRenderer;
import dev.bastion.client.render.TracerRenderer;
import dev.bastion.client.vfx.VfxManager;
import dev.bastion.client.vfx.VfxParams;
import dev.bastion.client.vfx.VfxPreset;
import dev.bastion.client.vfx.VfxPresets;
import dev.bastion.client.vfx.WeaponFx;
import dev.bastion.network.TurretFireEvent;
import dev.bastion.projectile.TurretArrowEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The early weapons (PLAN "Turret early game"), rustic: nothing glows, no light, casing or heat haze. The sling shots fly as
 * lit sprites that spin and tumble, after a puff at the muzzle, and land in chips, snow or a splat. The Arrow Launcher's
 * arrows are real arrows drawn by the vanilla renderer: its strings snap at each tube, and every turret arrow in flight
 * trails a pale wind streak, a tipped one also its potion colour's mist (ours: ArrowMixin stops vanilla's swirls), and one
 * that sticks in a block kicks up a little dust.
 */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID, value = Dist.CLIENT)
public final class EarlyEffects {
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Map<WeaponFx, Sling> SLINGS = Map.of(
            WeaponFx.COBBLESTONE, new Sling(Bastion.id("textures/vfx/pebble.png"), 0.45f, VfxPresets.COBBLE_TRAIL),
            WeaponFx.SNOWBALL, new Sling(Bastion.id("textures/vfx/snow_clump.png"), 0.25f, VfxPresets.SNOW_TRAIL),
            WeaponFx.POTATO, new Sling(Bastion.id("textures/vfx/potato_chunk.png"), 0.8f, null));
    /** Turret arrows already stuck (their dust played). */
    private static final Set<Integer> STUCK = new HashSet<>();

    /** A sling shot in flight: its sprite, its spin in radians a tick and the wisp it leaves every tick, if any. */
    private record Sling(ResourceLocation sprite, float spin, @Nullable VfxPreset trail) {
    }

    private EarlyEffects() {
    }

    static void fire(ClientLevel level, TurretFireEvent event, WeaponFx fx, ClientTurret client, Vec3 muzzle, Vec3 direction, int color) {
        long seed = event.seed();
        if (fx == WeaponFx.ARROW_LAUNCHER) {
            // The arrows are entities (ArrowLauncherWeapon); the strings snap at each tube of the rack (muzzle_0..5).
            for (int i = 0; i < 6; i++) {
                Vec3 tube = client.muzzles[i];
                if (tube != null && tube.distanceToSqr(muzzle) < 1) VfxManager.play(fx.muzzle(), tube, direction, VfxParams.of().seed(seed + i));
            }
        } else {
            VfxManager.play(fx.muzzle(), muzzle, direction, VfxParams.of().seed(seed));
            Sling sling = SLINGS.get(fx);
            Vec3 back = direction.reverse();
            for (TurretFireEvent.Shot shot : event.shots()) {
                int id = shot.blockState(); // a bullet's id
                TracerRenderer.spawnPellet(id, muzzle, shot.end(), event.speed(), sling.sprite(), fx.tracerWidth(), sling.spin(),
                        sling.trail() == null ? null : at -> VfxManager.play(sling.trail(), at, back, VfxParams.of().seed(level.getGameTime() * 31 + id)),
                        (end, normal, hit, blockState) -> TurretFireEffects.impact(level, fx, false,
                                new TurretFireEvent.Shot(end, normal, hit, blockState), color, seed + id));
            }
        }
        TurretFireEffects.playShot(level, event, muzzle, fx, RandomSource.create(seed));
    }

    /** Every turret arrow, each tick: the wind streak in flight, a tipped one's mist, dust once it sticks. */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (event.phase != TickEvent.Phase.END || level == null || Minecraft.getInstance().isPaused()) return;
        long now = level.getGameTime();
        Set<Integer> alive = new HashSet<>();
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof TurretArrowEntity arrow)) continue;
            alive.add(arrow.getId());
            // Vanilla keeps a stuck arrow's last step as its motion: from where it was to where it went in.
            Vec3 heading = arrow.getDeltaMovement().normalize();
            VfxParams params = VfxParams.of().color(0xB0000000 | arrow.getColor() & 0xFFFFFF).seed(now * 31 + arrow.getId());
            boolean tipped = arrow.getColor() != -1;
            if (!arrow.stuck()) {
                SmokeTrailRenderer.addWind(arrow.getId(), arrow.position().subtract(heading.scale(0.4)), now);
                // Every other tick (vanilla: 2 swirls every tick): six arrows a volley stay inside the particle budget (PLAN 9).
                if (tipped && (now + arrow.getId()) % 2 == 0) VfxManager.play(VfxPresets.ARROW_MIST, arrow.position(), UP, params);
                continue;
            }
            if (STUCK.add(arrow.getId())) {
                SmokeTrailRenderer.end(arrow.getId(), arrow.position(), now);
                BlockPos in = BlockPos.containing(arrow.position().add(heading.scale(0.3)));
                BlockState block = level.getBlockState(in);
                int blockColor = block.isAir() ? 0xFF8A8A8A : 0xFF000000 | block.getMapColor(level, in).col;
                VfxManager.play(VfxPresets.ARROW_STICK, arrow.position(), heading.reverse(), params.blockColor(blockColor));
            }
            if (tipped && now % 5 == 0) VfxManager.play(VfxPresets.ARROW_MIST, arrow.position(), UP, params); // vanilla's stuck rate
        }
        STUCK.retainAll(alive);
    }
}

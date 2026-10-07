package dev.bastion.weapon;

import dev.bastion.Bastion;
import dev.bastion.config.BastionConfig;
import dev.bastion.network.BastionNetwork;
import dev.bastion.network.TurretImpactEvent;
import dev.bastion.turret.TurretBurn;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mortar fire patches (user decision 2026-10-07): where a shell lands, a size x size block patch of Bastion's own fire
 * burns for a while. Everyone standing in it burns, not only the turret's targets (fire does not choose): the custom
 * burn (TurretBurn), which keeps going burn_ticks after they leave it and spares fire-immune mobs. Burnable blocks in
 * the patch catch real vanilla fire, the user's exception to PLAN 1 NO-VANILLA, unless the server config turns that off
 * (mortarBlockFire). Clients get one FIRE_PATCH event and draw the flames themselves. Not saved.
 */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID)
public final class FirePatches {
    private static final int BURN_INTERVAL = 5;
    private static final Map<ResourceKey<Level>, List<Active>> ACTIVE = new HashMap<>();

    /** What a shell carries: patch width in blocks (odd), how long it burns, and the burn it gives. */
    public record Patch(int size, int ticks, int burnTicks, float burnDamage) {
    }

    private static final class Active {
        final AABB area;
        final Patch patch;
        int age;

        Active(AABB area, Patch patch) {
            this.area = area;
            this.patch = patch;
        }
    }

    private FirePatches() {
    }

    public static void start(ServerLevel level, Vec3 at, Patch patch) {
        // On the ground below a burst in the air or on a mob, so the fire lies where feet are.
        BlockHitResult down = level.clip(new ClipContext(at.add(0, 0.1, 0), at.add(0, -4, 0), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, null));
        Vec3 ground = down.getType() == HitResult.Type.BLOCK ? down.getLocation() : at;
        BlockPos center = BlockPos.containing(ground.add(0, 0.05, 0)); // the air block standing on the ground
        int r = patch.size() / 2;
        AABB area = new AABB(center.getX() - r, ground.y - 0.5, center.getZ() - r, center.getX() + r + 1, ground.y + 1.5, center.getZ() + r + 1);
        ACTIVE.computeIfAbsent(level.dimension(), k -> new ArrayList<>()).add(new Active(area, patch));
        if (BastionConfig.MORTAR_BLOCK_FIRE.get()) igniteBlocks(level, center, r);
        BastionNetwork.sendNear(level, center, new TurretImpactEvent(new Vec3(center.getX() + 0.5, ground.y, center.getZ() + 0.5),
                TurretImpactEvent.FIRE_PATCH, patch.ticks() / 20f, Vec3.ZERO, patch.size()));
    }

    /** Vanilla fire in every free spot of the patch (one block up and down too) that touches something burnable. */
    private static void igniteBlocks(ServerLevel level, BlockPos center, int r) {
        for (BlockPos spot : BlockPos.betweenClosed(center.offset(-r, -1, -r), center.offset(r, 1, r))) {
            if (!level.isEmptyBlock(spot)) continue;
            for (Direction side : Direction.values()) {
                BlockPos next = spot.relative(side);
                if (level.getBlockState(next).isFlammable(level, next, side.getOpposite()) && BaseFireBlock.canBePlacedAt(level, spot, side)) {
                    level.setBlock(spot, BaseFireBlock.getState(level, spot), Block.UPDATE_ALL_IMMEDIATE);
                    break;
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        List<Active> active = ACTIVE.get(level.dimension());
        if (active == null || active.isEmpty()) return;
        active.removeIf(fire -> {
            if (fire.age++ % BURN_INTERVAL == 0) {
                for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, fire.area, e -> e.isAlive() && !e.isSpectator())) {
                    TurretBurn.ignite(entity, fire.patch.burnTicks(), fire.patch.burnDamage());
                }
            }
            return fire.age >= fire.patch.ticks();
        });
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        ACTIVE.clear();
    }

    /** Patches burning in a level, for tests. */
    public static int active(ServerLevel level) {
        List<Active> active = ACTIVE.get(level.dimension());
        return active == null ? 0 : active.size();
    }
}

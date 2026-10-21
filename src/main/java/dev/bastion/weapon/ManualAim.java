package dev.bastion.weapon;

import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretHitboxEntity;
import dev.bastion.turret.targeting.TargetSelector;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Overwatch aim (PLAN "Overwatch Link"): the barrel angles for the controller's aim point, the same on server and client. */
public final class ManualAim {
    private ManualAim() {
    }

    /** Barrel yaw and pitch (turret frame, as TurretBaseBlockEntity yaw()/pitch()); onTarget false when held at a pitch limit. */
    public record Aim(float yaw, float pitch, boolean onTarget) {
    }

    /** What the crosshair is on: the spot, and the creature there (null: a block or nothing). */
    public record Pick(Vec3 point, @Nullable Entity entity) {
    }

    /**
     * The crosshair ray from {@code from} to {@code to}: the first creature before the first block, at the point the ray
     * meets its body (a shell-lobbing weapon's spot is its feet: MortarWeapon fires at them), else the block, else the
     * ray's end. {@code viewer} (the player at the hub) is skipped. The pick variant of ProjectileUtil gives the meeting
     * point; the projectile variant gives the feet (user 2026-10-20: the barrel dropped to them).
     */
    public static Pick pick(Entity viewer, Vec3 from, Vec3 to, boolean ballistic) {
        BlockHitResult block = viewer.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, viewer));
        Vec3 end = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(viewer, from, end, new AABB(from, end).inflate(1),
                e -> e instanceof LivingEntity && !(e instanceof TurretHitboxEntity) && e.isPickable() && e.isAlive(), from.distanceToSqr(end));
        if (hit == null) return new Pick(end, null);
        return new Pick(ballistic ? hit.getEntity().position() : hit.getLocation(), hit.getEntity());
    }

    /** How the turret points to send a shot at {@code point}; null when the weapon cannot reach it (Mortar: too close or too far). */
    @Nullable
    public static Aim solve(TurretBaseBlockEntity turret, WeaponType type, StatSheet stats, Vec3 point) {
        Vec3 toward = type.manualAim(turret, point, stats);
        if (toward == null) return null;
        WeaponData data = stats.data();
        Vec3 local = turret.toLocal(toward.subtract(turret.pivot(data)));
        float elevation = TargetSelector.elevation(local);
        float held = Mth.clamp(elevation, data.aim().minPitch(), data.aim().maxPitch());
        return new Aim(TargetSelector.yaw(local), -held, Math.abs(held - elevation) < 0.5f);
    }
}

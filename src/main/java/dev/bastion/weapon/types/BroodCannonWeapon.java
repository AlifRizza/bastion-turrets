package dev.bastion.weapon.types;

import dev.bastion.projectile.BroodCapsuleEntity;
import dev.bastion.registry.BastionWeaponTypes;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.unit.BroodSpiderEntity;
import dev.bastion.unit.SpiderStats;
import dev.bastion.unit.TurretUnits;
import dev.bastion.weapon.FireContext;
import dev.bastion.weapon.ShotReport;
import dev.bastion.weapon.StatSheet;
import dev.bastion.weapon.WeaponData;
import dev.bastion.weapon.WeaponState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Brood Cannon (Fortress Turret Base, PLAN "Turret 3x3", user 2026-10-23): a Mortar that lobs a capsule of
 * units_per_capsule robot spiders instead of a shell (same arc, ground-only targets, min_range, Overwatch arc). Each shot
 * takes a Brood Capsule and energy_per_shot FE. While its spiders and unopened capsules fill unit_cap (or the dimension's
 * maxTurretUnits) it waits, spending nothing. It spreads its capsules over a crowd: after each shot it picks again, the
 * target fewest spiders and capsules are on first (user 2026-10-24).
 */
public class BroodCannonWeapon extends MortarWeapon {
    @Override
    public void tick(TurretBaseBlockEntity turret, WeaponState state, StatSheet stats, boolean engaged) {
        WeaponData data = stats.data();
        int cap = Math.round(data.param("unit_cap"));
        state.unitsFull = TurretUnits.count(turret.getLevel(), turret.getBlockPos()) + Math.round(data.param("units_per_capsule")) > cap
                || TurretUnits.room(turret.getLevel(), turret.getBlockPos(), cap) <= 0;
    }

    @Override
    public boolean ready(StatSheet stats, WeaponState state) {
        return !state.unitsFull;
    }

    /** Needs the FE for a shot as well as a capsule (the base checks the capsule). */
    @Override
    public boolean outOfAmmo(TurretBaseBlockEntity turret, WeaponState state) {
        return !turret.hasEnergy(cost(turret));
    }

    /** The capsule leaves on the Mortar's arc, carrying the spiders' numbers and a copy of the turret's filter. */
    @Override
    public void onFire(FireContext context) {
        TurretBaseBlockEntity turret = context.turret();
        if (!turret.useEnergy(cost(turret))) return;
        WeaponData data = context.stats().data();
        BroodCapsuleEntity capsule = new BroodCapsuleEntity(context.level(), turret.getBlockPos(), turret.filter(), turret.owner(),
                SpiderStats.of(context.stats()), Math.round(data.param("units_per_capsule")), Math.round(data.param("unit_cap")),
                Math.round(data.param("open_ticks")), data.param("gravity"));
        capsule.aim(context.target() instanceof LivingEntity living ? living : null, data.param("capsule_damage") * context.stats().damageScale());
        capsule.setPos(context.muzzle());
        capsule.launch(launchVelocity(context));
        context.level().addFreshEntity(capsule);
        TurretUnits.ticked(capsule); // counted from now, before its first tick
        ShotReport.send(context, this, 0, List.of());
        turret.retarget(); // the next capsule goes where fewest are (rank)
    }

    /**
     * Fewest spiders on it, capsules coming down on it and other Brood Cannons holding it as their target first (cannons
     * picking at the same time spread too); the filter's order breaks the ties.
     */
    @Override
    public List<LivingEntity> rank(TurretBaseBlockEntity turret, List<LivingEntity> candidates, StatSheet stats) {
        if (candidates.size() < 2) return candidates;
        AABB area = new AABB(turret.getBlockPos()).inflate(stats.range() + 16);
        Map<LivingEntity, Integer> taken = BroodSpiderEntity.taken(turret.getLevel(), area, null);
        // Other cannons' held picks, from the loaded chunks round it (turrets' block entities: a handful per chunk).
        Level level = turret.getLevel();
        ChunkPos middle = new ChunkPos(turret.getBlockPos());
        int chunks = (int) Math.ceil((stats.range() + 16) / 16);
        Set<TurretBaseBlockEntity> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int cx = middle.x - chunks; cx <= middle.x + chunks; cx++) {
            for (int cz = middle.z - chunks; cz <= middle.z + chunks; cz++) {
                if (!level.hasChunk(cx, cz)) continue;
                for (BlockEntity be : level.getChunk(cx, cz).getBlockEntities().values()) {
                    if (!(be instanceof TurretBaseBlockEntity other) || other == turret || !seen.add(other) || other.target() == null) continue;
                    WeaponData data = other.inventory().weaponData();
                    if (data != null && data.type().equals(BastionWeaponTypes.BROOD_CANNON.getId())) taken.merge(other.target(), 1, Integer::sum);
                }
            }
        }
        Map<UUID, Integer> coming = new HashMap<>();
        for (BroodCapsuleEntity capsule : turret.getLevel().getEntitiesOfClass(BroodCapsuleEntity.class, area)) {
            if (capsule.aimedAt() != null) coming.merge(capsule.aimedAt(), 1, Integer::sum);
        }
        List<LivingEntity> ranked = new ArrayList<>(super.rank(turret, candidates, stats));
        ranked.sort(Comparator.comparingInt(e -> taken.getOrDefault(e, 0) + coming.getOrDefault(e.getUUID(), 0)));
        return ranked;
    }

    private static int cost(TurretBaseBlockEntity turret) {
        WeaponData data = turret.inventory().weaponData();
        return data == null ? Integer.MAX_VALUE : Math.round(data.param("energy_per_shot"));
    }
}

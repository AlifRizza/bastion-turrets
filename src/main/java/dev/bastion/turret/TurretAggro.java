package dev.bastion.turret;

import dev.bastion.config.BastionConfig;
import dev.bastion.unit.TurretUnit;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;

/**
 * Hostile mobs go after turrets (PLAN 4.1) and their units (PLAN "Turret 3x3": spiders, drones): every naturally hostile
 * mob gets target goals for turret hitboxes and units, one priority below players, so a reachable player is still
 * chased first. Neutral mobs stay neutral.
 */
public final class TurretAggro {
    /** Zombies and skeletons target players at 2 and villagers/golems at 3. */
    private static final int PRIORITY = 3;

    private TurretAggro() {
    }

    public static void onJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof Mob mob) || !(mob instanceof Enemy)
                || mob instanceof NeutralMob || !BastionConfig.MOBS_ATTACK_TURRETS.get()) return;
        mob.targetSelector.addGoal(PRIORITY, new NearestAttackableTargetGoal<>(mob, TurretHitboxEntity.class, true));
        mob.targetSelector.addGoal(PRIORITY, new NearestAttackableTargetGoal<>(mob, LivingEntity.class, 10, true, false, e -> e instanceof TurretUnit));
    }
}

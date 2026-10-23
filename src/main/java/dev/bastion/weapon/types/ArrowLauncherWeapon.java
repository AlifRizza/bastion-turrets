package dev.bastion.weapon.types;

import dev.bastion.projectile.TurretArrowEntity;
import dev.bastion.weapon.Ballistics;
import dev.bastion.weapon.FireContext;
import dev.bastion.weapon.ShotReport;
import dev.bastion.weapon.WeaponData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Arrow Launcher (early game, floor makeshift base only): aims like the Mortar (high arc over walls, ground targets past
 * min_range, the drag-aware solve: its arrows keep `drag` of their speed a tick), but one arrow item looses a volley of
 * `arrows` arrows of that kind (TurretArrowEntity): the first comes down on the target's feet, the rest evenly around a
 * ring of `ring_radius` (each its own solve; a ring point out of reach takes the centre's), so a lone mob is hit and a crowd
 * around it gets the rest.
 */
public class ArrowLauncherWeapon extends MortarWeapon {
    @Override
    public void onFire(FireContext context) {
        WeaponData data = context.stats().data();
        // From the real muzzle at the moment of firing, at the creature it was fired at (no Overwatch: a makeshift base has no Connector).
        Vec3 to = context.target() != null ? context.target().position() : context.manualPoint();
        Vec3 centre = to != null ? arc(context, data, to) : null;
        if (centre == null) centre = context.direction().scale(data.param("shell_speed"));
        int arrows = Math.round(data.param("arrows"));
        double ring = data.param("ring_radius");
        ItemStack ammo = context.turret().lastAmmo();
        for (int i = 0; i < arrows; i++) {
            Vec3 velocity = null;
            if (i > 0 && to != null) {
                double angle = 2 * Math.PI * (i - 1) / (arrows - 1);
                velocity = arc(context, data, to.add(Math.cos(angle) * ring, 0, Math.sin(angle) * ring));
            }
            TurretArrowEntity arrow = new TurretArrowEntity(context.level(), context.turret().getBlockPos(), ammo, context.stats().damage());
            arrow.setPos(context.muzzle());
            arrow.launch(velocity != null ? velocity : centre, data.param("gravity"), data.param("drag"), Math.round(data.param("stuck_ticks")), Math.round(data.param("glow_ticks")));
            context.level().addFreshEntity(arrow);
        }
        ShotReport.send(context, this, 0, List.of());
    }

    @Nullable
    private static Vec3 arc(FireContext context, WeaponData data, Vec3 to) {
        return Ballistics.highArc(context.muzzle(), to, data.param("shell_speed"), data.param("gravity"), data.param("drag"), data.aim().maxPitch());
    }
}

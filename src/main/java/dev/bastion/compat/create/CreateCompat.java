package dev.bastion.compat.create;

import com.simibubi.create.api.registry.CreateRegistries;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointType;
import dev.bastion.Bastion;
import dev.bastion.turret.TurretBaseBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;

/**
 * Create integration, loaded only when Create is installed (Bastion checks ModList first, so a world without Create
 * never touches these classes). Funnels and chutes already work through the turret's item handler; Create's
 * mechanical arm only serves blocks with a registered interaction point type, so turret bases get one.
 */
public final class CreateCompat {
    private CreateCompat() {
    }

    public static void register(IEventBus modBus) {
        DeferredRegister<ArmInteractionPointType> types = DeferredRegister.create(CreateRegistries.ARM_INTERACTION_POINT_TYPE, Bastion.MOD_ID);
        types.register("turret", TurretPointType::new);
        types.register(modBus);
    }

    /**
     * Any block of a turret base, standard or large. The default point inserts into and extracts from the block's item
     * handler, which is the turret's ammo-only handler (parts of a large base forward to their core), so an arm can
     * load the right ammo and nothing else.
     */
    static final class TurretPointType extends ArmInteractionPointType {
        @Override
        public boolean canCreatePoint(Level level, BlockPos pos, BlockState state) {
            return state.getBlock() instanceof TurretBaseBlock;
        }

        @Override
        public ArmInteractionPoint createPoint(Level level, BlockPos pos, BlockState state) {
            return new ArmInteractionPoint(this, level, pos, state);
        }
    }
}

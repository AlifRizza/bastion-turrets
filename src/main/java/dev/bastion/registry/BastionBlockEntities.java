package dev.bastion.registry;

import dev.bastion.Bastion;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretPartBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class BastionBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> REGISTER = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Bastion.MOD_ID);

    @SuppressWarnings("DataFlowIssue") // null data fixer type is the standard for modded block entities
    public static final RegistryObject<BlockEntityType<TurretBaseBlockEntity>> TURRET_BASE = REGISTER.register("turret_base",
            () -> BlockEntityType.Builder.of(TurretBaseBlockEntity::new, BastionBlocks.TURRET_BASE.get(), BastionBlocks.LARGE_TURRET_BASE.get())
                    .build(null));
    /** The three non-core blocks of a large base; they forward automation to the core. */
    @SuppressWarnings("DataFlowIssue")
    public static final RegistryObject<BlockEntityType<TurretPartBlockEntity>> TURRET_PART = REGISTER.register("turret_part",
            () -> BlockEntityType.Builder.of(TurretPartBlockEntity::new, BastionBlocks.LARGE_TURRET_BASE.get()).build(null));
}

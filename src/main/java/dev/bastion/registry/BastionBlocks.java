package dev.bastion.registry;

import dev.bastion.Bastion;
import dev.bastion.turret.LargeTurretBaseBlock;
import dev.bastion.turret.TurretBaseBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class BastionBlocks {
    public static final DeferredRegister<Block> REGISTER = DeferredRegister.create(ForgeRegistries.BLOCKS, Bastion.MOD_ID);

    // No requiresCorrectToolForDrops: breaking by hand must still drop the base and its stored ammo.
    public static final RegistryObject<TurretBaseBlock> TURRET_BASE = REGISTER.register("turret_base", () -> new TurretBaseBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5f, 6f).sound(SoundType.METAL).noOcclusion().dynamicShape()));
    /** 2x2x1 multiblock for big weapon modules; pistons cannot split it. */
    public static final RegistryObject<LargeTurretBaseBlock> LARGE_TURRET_BASE = REGISTER.register("large_turret_base", () -> new LargeTurretBaseBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5f, 9f).sound(SoundType.METAL).noOcclusion().dynamicShape()
                    .pushReaction(PushReaction.BLOCK)));
}

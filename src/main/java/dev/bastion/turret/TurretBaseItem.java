package dev.bastion.turret;

import dev.bastion.client.render.TurretBaseItemRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.function.Consumer;

/** Base as an item: the animated base model in hand and inventory, plus a tooltip for what it carries. */
public class TurretBaseItem extends BlockItem implements GeoItem {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.turret_base.idle");

    private final AnimatableInstanceCache animations = GeckoLibUtil.createInstanceCache(this);

    public TurretBaseItem(Block block, Properties properties) {
        super(block, properties);
    }

    /** The 2x2 Large Turret Base rather than the standard one. */
    public boolean large() {
        return getBlock() instanceof LargeTurretBaseBlock;
    }

    public static TurretTier tier(ItemStack stack) {
        CompoundTag data = getBlockEntityData(stack);
        return data == null ? TurretTier.T1 : TurretTier.byName(data.getString("Tier"));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag data = getBlockEntityData(stack);
        TurretTier tier = tier(stack);
        tooltip.add(Component.translatable("tooltip.bastion.turret_base.tier", tier.ordinal() + 1).withStyle(ChatFormatting.GRAY));
        int stored = data == null ? 0 : data.getCompound("Inventory").getList("Items", Tag.TAG_COMPOUND).size();
        if (stored > 0) {
            tooltip.add(Component.translatable("tooltip.bastion.turret_base.stored", stored).withStyle(ChatFormatting.GRAY));
        }
        if (large()) tooltip.add(Component.translatable("tooltip.bastion.large_turret_base").withStyle(ChatFormatting.DARK_GRAY));
    }

    // Only ever called on the client, so the client-package renderer is never loaded on a server.
    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(TurretBaseItemRenderer.extensions());
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "base", 0, state -> state.setAndContinue(IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animations;
    }
}

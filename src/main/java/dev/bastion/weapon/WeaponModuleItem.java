package dev.bastion.weapon;

import dev.bastion.client.render.WeaponItemRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
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

/**
 * A weapon module, PLAN 4.3: does nothing in hand, works only mounted on a turret base. Its stats
 * come from the weapon JSON named by {@code weaponId}; its model files are named after the item id.
 */
public class WeaponModuleItem extends Item implements GeoItem {
    private final ResourceLocation weaponId;
    private final boolean large;
    private final AnimatableInstanceCache animations = GeckoLibUtil.createInstanceCache(this);

    public WeaponModuleItem(Properties properties, ResourceLocation weaponId) {
        this(properties, weaponId, false);
    }

    /** @param large a big module that mounts only on a Large Turret Base (and nothing small fits there) */
    public WeaponModuleItem(Properties properties, ResourceLocation weaponId, boolean large) {
        super(properties);
        this.weaponId = weaponId;
        this.large = large;
    }

    public boolean large() {
        return large;
    }

    public ResourceLocation weaponId() {
        return weaponId;
    }

    @Nullable
    public WeaponData data() {
        return WeaponDataLoader.get(weaponId);
    }

    /** Model, texture and animation file name: the item id path, e.g. gun_turret. */
    public String modelName() {
        return BuiltInRegistries.ITEM.getKey(this).getPath();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        WeaponData data = data();
        if (data == null) return;
        tooltip.add(Component.translatable("tooltip.bastion.weapon.damage", fmt(data.damage())).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.bastion.weapon.fire_rate", fmt(20f / data.fireInterval())).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.bastion.weapon.range", fmt(data.range())).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.bastion.weapon." + modelName() + ".ability").withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable(large ? "tooltip.bastion.weapon.fits_large" : "tooltip.bastion.weapon.fits_small")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    private static String fmt(float value) {
        return value == (int) value ? Integer.toString((int) value) : String.format("%.1f", value);
    }

    // Only ever called on the client, so the client-package renderer is never loaded on a server.
    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(WeaponItemRenderer.extensions());
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        RawAnimation heldIdle = RawAnimation.begin().thenLoop("animation." + modelName() + ".held_idle");
        controllers.add(new AnimationController<>(this, "held", 0, state -> state.setAndContinue(heldIdle)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animations;
    }
}

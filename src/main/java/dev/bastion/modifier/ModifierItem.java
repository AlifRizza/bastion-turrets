package dev.bastion.modifier;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A modifier module (PLAN 4.6). Its effect is data: data/<ns>/turret_modifiers/<item path>.json. A base
 * accepts each modifier once (TurretInventory rejects duplicates).
 */
public class ModifierItem extends Item {
    public ModifierItem(Properties properties) {
        super(properties);
    }

    public ModifierEffect effect() {
        return ModifierDataLoader.get(BuiltInRegistries.ITEM.getKey(this));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        ModifierEffect e = effect();
        percent(tooltip, "range", e.range());
        percent(tooltip, "fire_rate", e.fireRate());
        percent(tooltip, "damage", e.damage());
        percent(tooltip, "heat_per_shot", e.heatPerShot());
        percent(tooltip, "heat_dissipation", e.heatDissipation());
        percent(tooltip, "turn_speed", e.turnSpeed());
        percent(tooltip, "max_health", e.maxHealth());
        percent(tooltip, "ammo_save_chance", e.ammoSaveChance());
        if (e.pierce() != 0) tooltip.add(line("pierce", String.format("%+d", e.pierce()), e.pierce() > 0));
        if (e.leadAccuracy() != 0) tooltip.add(Component.translatable("tooltip.bastion.modifier.lead_accuracy").withStyle(ChatFormatting.BLUE));
    }

    private static void percent(List<Component> tooltip, String key, float value) {
        if (value == 0) return;
        boolean good = key.equals("heat_per_shot") ? value < 0 : value > 0;
        tooltip.add(line(key, String.format("%+d%%", Math.round(value * 100)), good));
    }

    private static Component line(String key, String value, boolean good) {
        return Component.translatable("tooltip.bastion.modifier." + key, value).withStyle(good ? ChatFormatting.BLUE : ChatFormatting.RED);
    }
}

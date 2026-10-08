package dev.bastion.modifier;

import dev.bastion.weapon.WeaponModuleItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;

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
        percent(tooltip, "spread", e.spread());
        percent(tooltip, "reload_speed", e.reloadSpeed());
        if (!e.weapons().isEmpty()) { // weapon-specific module: name the modules it fits
            String fits = BuiltInRegistries.ITEM.stream().filter(i -> i instanceof WeaponModuleItem w && e.weapons().contains(w.weaponId()))
                    .map(i -> i.getDescription().getString()).collect(Collectors.joining(", "));
            tooltip.add(Component.translatable("tooltip.bastion.modifier.only", fits).withStyle(ChatFormatting.GOLD));
        }
        if (!Boolean.TRUE.equals(DistExecutor.unsafeCallWhenOn(Dist.CLIENT, () -> Screen::hasShiftDown))) {
            tooltip.add(Component.translatable("tooltip.bastion.modifier.shift").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        List<WeaponModuleItem> all = weapons(), works = worksOn();
        if (works.size() == all.size()) {
            tooltip.add(Component.translatable("tooltip.bastion.modifier.works_all").withStyle(ChatFormatting.GRAY));
            return;
        }
        boolean except = works.size() > all.size() / 2; // a long list reads better as the few it skips
        tooltip.add(Component.translatable(except ? "tooltip.bastion.modifier.works_except" : "tooltip.bastion.modifier.works_on")
                .withStyle(ChatFormatting.GRAY));
        for (WeaponModuleItem weapon : except ? all.stream().filter(w -> !works.contains(w)).toList() : works) {
            tooltip.add(Component.literal(" - ").append(weapon.getDescription()).withStyle(except ? ChatFormatting.RED : ChatFormatting.GREEN));
        }
    }

    /** The weapon modules this module changes something on, in registry order (ModifierEffect.affects). */
    public List<WeaponModuleItem> worksOn() {
        ModifierEffect e = effect();
        return weapons().stream().filter(w -> e.affects(w.weaponId(), w.data())).toList();
    }

    private static List<WeaponModuleItem> weapons() {
        return BuiltInRegistries.ITEM.stream().filter(i -> i instanceof WeaponModuleItem w && w.data() != null)
                .map(i -> (WeaponModuleItem) i).toList();
    }

    private static void percent(List<Component> tooltip, String key, float value) {
        if (value == 0) return;
        boolean good = key.equals("heat_per_shot") || key.equals("spread") ? value < 0 : value > 0;
        tooltip.add(line(key, String.format("%+d%%", Math.round(value * 100)), good));
    }

    private static Component line(String key, String value, boolean good) {
        return Component.translatable("tooltip.bastion.modifier." + key, value).withStyle(good ? ChatFormatting.BLUE : ChatFormatting.RED);
    }
}

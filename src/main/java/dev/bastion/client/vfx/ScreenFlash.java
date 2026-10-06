package dev.bastion.client.vfx;

import dev.bastion.Bastion;
import dev.bastion.config.BastionClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Brief white overlay for very close blasts (PLAN 5.6). Disabled by the screenFlash client option. */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID, value = Dist.CLIENT)
public final class ScreenFlash {
    private static float strength, prevStrength;

    private ScreenFlash() {
    }

    static void add(Vec3 source, float amount, float radius) {
        if (!BastionClientConfig.SCREEN_FLASH.get() || Minecraft.getInstance().player == null) return;
        double distance = Minecraft.getInstance().player.getEyePosition().distanceTo(source);
        if (distance >= radius) return;
        strength = Math.max(strength, Mth.clamp(amount * (float) (1 - distance / radius), 0, 0.85f));
    }

    static void tick() {
        prevStrength = strength;
        strength = Math.max(0, strength * 0.72f - 0.02f);
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        float s = Mth.lerp(event.getPartialTick(), prevStrength, strength);
        if (s <= 0.003f) return;
        int alpha = (int) (s * 255) << 24;
        event.getGuiGraphics().fill(0, 0, event.getWindow().getGuiScaledWidth(), event.getWindow().getGuiScaledHeight(), alpha | 0xFFF4F8FF & 0xFFFFFF);
    }
}

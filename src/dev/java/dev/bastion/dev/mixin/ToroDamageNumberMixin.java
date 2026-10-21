package dev.bastion.dev.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Dev runs only (src/dev, never in a jar): ToroHealth draws its damage numbers with packed light 0, so any colour comes
 * out dark (user 2026-10-20: "merah gelap"). Drawn here at full brightness with a black outline instead, readable on
 * any background, day or night. The colour stays ToroHealth's own config (run/config/torohealth.json damageColor).
 * Optional: without ToroHealth in the run the mixin simply does not apply.
 */
@Mixin(targets = "net.torocraft.torohealth.bars.HealthBarRenderer", remap = false)
public abstract class ToroDamageNumberMixin {
    @Redirect(method = "drawDamageNumber", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Font;drawInBatch(Ljava/lang/String;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I"))
    private static int bastion$brightNumber(Font font, String text, float x, float y, int color, boolean shadow, Matrix4f pose,
                                            MultiBufferSource buffers, Font.DisplayMode mode, int background, int light) {
        font.drawInBatch8xOutline(Component.literal(text).getVisualOrderText(), x, y, 0xFF000000 | color, 0xFF000000, pose, buffers,
                LightTexture.FULL_BRIGHT);
        return 0;
    }
}

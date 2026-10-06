package dev.bastion.config;

import net.minecraftforge.common.ForgeConfigSpec;

/** Client-only visual settings (PLAN 5.5, 5.6, 5.8). Holds no client classes, so it is safe to load on a server. */
public final class BastionClientConfig {
    public enum VfxQuality { LOW, MEDIUM, HIGH }

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.EnumValue<VfxQuality> VFX_QUALITY;
    public static final ForgeConfigSpec.BooleanValue DYNAMIC_LIGHTS;
    public static final ForgeConfigSpec.IntValue MAX_DYNAMIC_LIGHTS;
    public static final ForgeConfigSpec.BooleanValue CAMERA_SHAKE;
    public static final ForgeConfigSpec.BooleanValue SCREEN_FLASH;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        VFX_QUALITY = b
                .comment("Particle count, trail length and dynamic light budget preset.")
                .defineEnum("vfxQuality", VfxQuality.HIGH);
        DYNAMIC_LIGHTS = b
                .comment("Short-lived light from muzzle flashes and impacts. Glow effects stay on when disabled.")
                .define("dynamicLights", true);
        MAX_DYNAMIC_LIGHTS = b
                .comment("Hard cap on simultaneous dynamic lights; the preset may lower it further.")
                .defineInRange("maxDynamicLights", 24, 0, 64);
        CAMERA_SHAKE = b
                .comment("Camera shake from nearby heavy shots and turret destruction (accessibility).")
                .define("cameraShake", true);
        SCREEN_FLASH = b
                .comment("Brief screen flash when a turret is destroyed very close by (accessibility).")
                .define("screenFlash", true);
        SPEC = b.build();
    }
}

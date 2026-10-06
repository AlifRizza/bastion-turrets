package dev.bastion.client;

import dev.bastion.Bastion;
import dev.bastion.client.particle.VfxParticle;
import dev.bastion.client.particle.VfxParticle.Behavior;
import dev.bastion.client.render.BakedTurretBaseModel;
import dev.bastion.client.render.RocketRenderer;
import dev.bastion.client.render.TurretBaseRenderer;
import dev.bastion.client.render.WorkstationRenderer;
import dev.bastion.client.screen.TurretScreen;
import dev.bastion.client.screen.WorkstationScreen;
import dev.bastion.registry.BastionBlockEntities;
import dev.bastion.registry.BastionEntities;
import dev.bastion.registry.BastionParticles;
import dev.bastion.registry.BastionMenus;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.core.particles.ParticleType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.registries.RegistryObject;

/** Client-only wiring: renderers, particle providers and screens. Never loaded on a dedicated server. */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class BastionClient {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(BastionMenus.TURRET.get(), TurretScreen::new);
            MenuScreens.register(BastionMenus.WORKSTATION.get(), WorkstationScreen::new);
        });
    }

    @SubscribeEvent
    public static void onRegisterGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register("turret_base", new BakedTurretBaseModel.Loader());
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BastionBlockEntities.TURRET_BASE.get(), context -> new TurretBaseRenderer());
        event.registerBlockEntityRenderer(BastionBlockEntities.WORKSTATION.get(), context -> new WorkstationRenderer());
        event.registerEntityRenderer(BastionEntities.TURRET_HITBOX.get(), NoopRenderer::new);
        event.registerEntityRenderer(BastionEntities.TURRET_ROCKET.get(), RocketRenderer::new);
        event.registerEntityRenderer(BastionEntities.TURRET_MISSILE.get(), context -> new RocketRenderer(context, "turret_missile"));
    }

    /** How each particle moves and looks (PLAN 5.4); sprites come from assets/bastion/particles/<type>.json. */
    @SubscribeEvent
    public static void onRegisterParticles(RegisterParticleProvidersEvent event) {
        particle(event, BastionParticles.SPARK, Behavior.billboard(true).stretched(0.5f, true).physics(0.9f, 0.96f, true));
        particle(event, BastionParticles.EMBER, Behavior.billboard(true).physics(-0.02f, 0.96f, false));
        particle(event, BastionParticles.SMOKE_PUFF, Behavior.billboard(false).physics(-0.01f, 0.92f, false).spinning(0.02f));
        particle(event, BastionParticles.HEAT_HAZE, Behavior.billboard(false).physics(-0.02f, 0.94f, false).spinning(0.01f));
        particle(event, BastionParticles.SHOCKWAVE_RING, Behavior.billboard(true).flat(0));
        particle(event, BastionParticles.MUZZLE_FLASH, Behavior.billboard(true).withFrames());
        particle(event, BastionParticles.DEBRIS, Behavior.billboard(false).physics(1f, 0.98f, true).spinning(0.3f));
        particle(event, BastionParticles.MUZZLE_RING, Behavior.billboard(true).flat(1));
        particle(event, BastionParticles.IMPACT_SPLASH, Behavior.billboard(true).withFrames().flat(0));
        particle(event, BastionParticles.SMOKE_WISP, Behavior.billboard(false).physics(-0.012f, 0.95f, false));
        particle(event, BastionParticles.FIREBALL, Behavior.billboard(true).withFrames().physics(-0.012f, 0.86f, false).spinning(0.03f).burning());
        particle(event, BastionParticles.BLAST_SMOKE, Behavior.billboard(false).physics(-0.016f, 0.93f, false).spinning(0.012f));
        particle(event, BastionParticles.FLAME, Behavior.billboard(true).stretched(0.35f, false).physics(-0.01f, 0.87f, false).burning());
        particle(event, BastionParticles.DUST_RING, Behavior.billboard(false).flat(0));
        particle(event, BastionParticles.FLAME_JET, Behavior.billboard(true).withFrames().physics(-0.02f, 0.9f, false).spinning(0.05f)
                .burning().colliding());
    }

    private static void particle(RegisterParticleProvidersEvent event, RegistryObject<ParticleType<BastionParticles.Options>> type, Behavior behavior) {
        event.registerSpriteSet(type.get(), sprites -> new VfxParticle.Provider(sprites, behavior));
    }
}

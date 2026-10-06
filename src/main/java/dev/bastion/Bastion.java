package dev.bastion;

import com.mojang.logging.LogUtils;
import dev.bastion.compat.create.CreateCompat;
import dev.bastion.config.BastionClientConfig;
import dev.bastion.config.BastionConfig;
import dev.bastion.modifier.ModifierDataLoader;
import dev.bastion.network.BastionNetwork;
import dev.bastion.network.WeaponDataSync;
import dev.bastion.registry.BastionBlockEntities;
import dev.bastion.registry.BastionBlocks;
import dev.bastion.registry.BastionEntities;
import dev.bastion.registry.BastionItems;
import dev.bastion.registry.BastionMenus;
import dev.bastion.registry.BastionParticles;
import dev.bastion.registry.BastionRecipes;
import dev.bastion.registry.BastionSounds;
import dev.bastion.registry.BastionWeaponTypes;
import dev.bastion.turret.TurretAggro;
import dev.bastion.weapon.WeaponDataLoader;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;

@Mod(Bastion.MOD_ID)
public final class Bastion {
    public static final String MOD_ID = "bastion";
    public static final Logger LOGGER = LogUtils.getLogger();

    // ponytail: deprecated context getters / ResourceLocation ctor kept on purpose; their replacements
    // need Forge 47.3.10+ and PLAN 2 targets 47.2+. Switch when the minimum Forge version is raised.
    public Bastion() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        BastionBlocks.REGISTER.register(modBus);
        BastionItems.REGISTER.register(modBus);
        BastionItems.TABS.register(modBus);
        BastionBlockEntities.REGISTER.register(modBus);
        BastionEntities.REGISTER.register(modBus);
        modBus.addListener(BastionEntities::registerAttributes);
        if (ModList.get().isLoaded("create")) CreateCompat.register(modBus); // optional: mechanical arms load turrets
        BastionMenus.REGISTER.register(modBus);
        BastionParticles.REGISTER.register(modBus);
        BastionSounds.REGISTER.register(modBus);
        BastionWeaponTypes.REGISTER.register(modBus);
        BastionRecipes.TYPES.register(modBus);
        BastionRecipes.SERIALIZERS.register(modBus);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, BastionConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, BastionClientConfig.SPEC);

        BastionNetwork.register();
        MinecraftForge.EVENT_BUS.addListener((AddReloadListenerEvent event) -> {
            event.addListener(new WeaponDataLoader());
            event.addListener(new ModifierDataLoader());
        });
        MinecraftForge.EVENT_BUS.addListener(Bastion::syncWeaponData);
        MinecraftForge.EVENT_BUS.addListener(TurretAggro::onJoinLevel);
    }

    /** Clients get the weapon and modifier JSON on login and after every /reload. */
    private static void syncWeaponData(OnDatapackSyncEvent event) {
        WeaponDataSync message = new WeaponDataSync(WeaponDataLoader.all(), ModifierDataLoader.all());
        if (event.getPlayer() != null) {
            BastionNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(event::getPlayer), message);
        } else {
            BastionNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), message);
        }
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}

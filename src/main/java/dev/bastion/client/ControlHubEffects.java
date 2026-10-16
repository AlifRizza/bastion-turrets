package dev.bastion.client;

import dev.bastion.client.render.HubBlueprint;
import dev.bastion.client.vfx.VfxManager;
import dev.bastion.client.vfx.VfxParams;
import dev.bastion.client.vfx.VfxPresets;
import dev.bastion.hub.ControlHubStructure;
import dev.bastion.hub.HubInterfaceBlock.Status;
import dev.bastion.hub.SatelliteUplinkBlockEntity;
import dev.bastion.registry.BastionSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/** Control Hub client effects (PLAN "Tampilan"): the scan and a flash at the dish when a hub forms; the dish's hum while online. */
public final class ControlHubEffects {
    private static final Map<BlockPos, DishHum> HUMS = new HashMap<>();

    private ControlHubEffects() {
    }

    /** A hub this client saw forming. */
    public static void formed(BlockPos iface, Direction facing) {
        HubBlueprint.scan(iface, facing);
        Vec3 dish = Vec3.atBottomCenterOf(ControlHubStructure.center(iface, facing)).add(0, 3.8, 0);
        VfxManager.play(VfxPresets.TIER_UP, dish, new Vec3(0, 1, 0), VfxParams.of().scale(0.6f).color(VfxPresets.CYAN).seed(iface.hashCode()));
    }

    /**
     * Keeps the dish humming while its satellite is online (called every client tick by the satellite while online). Asks
     * the sound engine, not the sound: leaving a world, F3+T or a new audio device drop every sound without stopping it.
     */
    public static void dishHum(SatelliteUplinkBlockEntity satellite) {
        DishHum hum = HUMS.get(satellite.getBlockPos());
        if (hum != null && Minecraft.getInstance().getSoundManager().isActive(hum)) return;
        hum = new DishHum(satellite);
        HUMS.put(satellite.getBlockPos(), hum);
        Minecraft.getInstance().getSoundManager().play(hum);
    }

    /** Leaving a world: its satellites go with it (VfxManager.onLevelUnload). */
    public static void clear() {
        HUMS.clear();
    }

    /** The dish's loop: stops once its satellite is gone or no longer online. */
    private static final class DishHum extends AbstractTickableSoundInstance {
        private final SatelliteUplinkBlockEntity satellite;

        DishHum(SatelliteUplinkBlockEntity satellite) {
            super(BastionSounds.CONTROL_HUB_DISH_LOOP.get(), SoundSource.BLOCKS, RandomSource.create());
            this.satellite = satellite;
            looping = true;
            delay = 0;
            volume = 0.5f;
            attenuation = SoundInstance.Attenuation.LINEAR;
            x = satellite.getBlockPos().getX() + 0.5;
            y = satellite.getBlockPos().getY() + 1.6;
            z = satellite.getBlockPos().getZ() + 0.5;
        }

        /** Starts even at Blocks volume 0, so turning the volume up later finds it playing. */
        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            if (satellite.isRemoved() || satellite.status() != Status.ONLINE) {
                stop();
                HUMS.remove(satellite.getBlockPos(), this);
            }
        }
    }
}

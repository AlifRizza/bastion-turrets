package dev.bastion.client;

import dev.bastion.client.render.ArcRenderer;
import dev.bastion.client.vfx.DynamicLightManager;
import dev.bastion.client.vfx.VfxManager;
import dev.bastion.client.vfx.VfxParams;
import dev.bastion.client.vfx.VfxPreset;
import dev.bastion.client.vfx.VfxPresets;
import dev.bastion.registry.BastionSounds;
import dev.bastion.workstation.WorkstationBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Workstation looks between frames (PLAN Fase 9): sparks where the tools touch the work while it runs (machining on the
 * Part Workstation, welding arcs on the Part Assembler, a printer head's sparkle, the ammo press's puffs, arcs into
 * the cell on the Charging Station), the machine
 * hum, and the finish: a ring of light and a chime. Tool positions come from WorkstationRenderer each frame.
 */
public final class WorkstationEffects {
    private static final Map<WorkstationBlockEntity, State> STATES = new WeakHashMap<>();

    private static final class State {
        final Vec3[] tools = new Vec3[4];
        @Nullable
        Hum hum;
        int ticks;
    }

    private WorkstationEffects() {
    }

    private static State state(WorkstationBlockEntity station) {
        return STATES.computeIfAbsent(station, s -> new State());
    }

    public static void tools(WorkstationBlockEntity station, Vec3[] tools) {
        System.arraycopy(tools, 0, state(station).tools, 0, tools.length);
    }

    public static void tick(WorkstationBlockEntity station) {
        State state = state(station);
        if (!station.working()) return;
        state.ticks++;
        if (state.hum == null || state.hum.isStopped()) {
            state.hum = new Hum(station);
            Minecraft.getInstance().getSoundManager().play(state.hum);
        }
        RandomSource random = station.getLevel().random;
        switch (station.type()) {
            case PART_WORKSTATION -> {
                if (state.ticks % 3 == 0) spark(state.tools[0], VfxPresets.WORKSTATION_SPARKS, 0xFFFFB347, random);
            }
            case PART_ASSEMBLER -> {
                Vec3 torch = state.tools[random.nextInt(2)];
                if (random.nextInt(3) == 0 && torch != null) {
                    spark(torch, VfxPresets.WORKSTATION_WELD, 0xFF9FE8FF, random);
                    DynamicLightManager.add(torch, 11, 2);
                    if (random.nextInt(4) == 0) sound(torch, BastionSounds.WORKSTATION_WELD.get(), 0.5f, random);
                }
            }
            case MODULE_WORKSTATION -> {
                if (state.ticks % 4 == 0) spark(state.tools[0], VfxPresets.WORKSTATION_SPARKS, 0xFF4FD8FF, random);
            }
            case CHARGING_STATION -> { // arcs leap from the coil tips into the cell
                Vec3 tip = state.tools[random.nextInt(4)];
                if (tip != null && state.ticks % 2 == 0) {
                    Vec3 cell = Vec3.atBottomCenterOf(station.getBlockPos()).add(0, 0.5, 0);
                    ArcRenderer.spawn(() -> tip, () -> cell, 0xFF4FD8FF, 0.03f, 2, 1, 0, 0.25f, random.nextLong());
                    if (random.nextInt(8) == 0) sound(tip, BastionSounds.TESLA_CRACKLE.get(), 0.3f, random);
                }
            }
            case AMMO_WORKSTATION -> {
                if (state.ticks % 20 == 10 && state.tools[0] != null) { // the press bottoms out once a second
                    spark(state.tools[0], VfxPresets.WORKSTATION_PUFF, 0xFFFFB347, random);
                    sound(state.tools[0], BastionSounds.WORKSTATION_PRESS.get(), 0.6f, random);
                }
            }
        }
    }

    /** A craft finished: a ring of light rises from the work spot, a chime. */
    public static void crafted(WorkstationBlockEntity station) {
        RandomSource random = station.getLevel().random;
        Vec3 at = state(station).tools[0] != null ? state(station).tools[0] : Vec3.atCenterOf(station.getBlockPos()).add(0, 0.6, 0);
        VfxManager.play(VfxPresets.WORKSTATION_DONE, at, new Vec3(0, 1, 0), VfxParams.of().color(0xFF4FD8FF).seed(random.nextLong()));
        sound(at, BastionSounds.WORKSTATION_DONE.get(), 0.7f, random);
    }

    private static void spark(@Nullable Vec3 at, VfxPreset preset, int color, RandomSource random) {
        if (at != null) VfxManager.play(preset, at, new Vec3(0, 1, 0), VfxParams.of().color(color).seed(random.nextLong()));
    }

    private static void sound(Vec3 at, net.minecraft.sounds.SoundEvent sound, float volume, RandomSource random) {
        var level = Minecraft.getInstance().level;
        if (level != null) level.playLocalSound(at.x, at.y, at.z, sound, SoundSource.BLOCKS, volume, 0.92f + random.nextFloat() * 0.16f, false);
    }

    /** The machine hum while a station works; fades out and stops once it has been idle a second. */
    private static final class Hum extends AbstractTickableSoundInstance {
        private final WorkstationBlockEntity station;
        private int silent;

        Hum(WorkstationBlockEntity station) {
            super(BastionSounds.WORKSTATION_LOOP.get(), SoundSource.BLOCKS, RandomSource.create());
            this.station = station;
            this.looping = true;
            this.delay = 0;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            Vec3 at = Vec3.atCenterOf(station.getBlockPos());
            this.x = at.x;
            this.y = at.y;
            this.z = at.z;
            this.volume = 0.45f;
        }

        @Override
        public void tick() {
            silent = station.working() ? 0 : silent + 1;
            volume = station.working() ? 0.45f : Math.max(0, 0.45f - silent * 0.05f);
            if (station.isRemoved() || silent > 20) stop();
        }
    }
}

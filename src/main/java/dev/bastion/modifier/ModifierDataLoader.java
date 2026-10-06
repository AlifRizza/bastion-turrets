package dev.bastion.modifier;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.bastion.Bastion;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.HashMap;
import java.util.Map;

/** Loads data/<ns>/turret_modifiers/*.json, keyed by the modifier item's id; synced to clients like weapon data. */
public class ModifierDataLoader extends SimpleJsonResourceReloadListener {
    private static volatile Map<ResourceLocation, ModifierEffect> effects = Map.of();

    public ModifierDataLoader() {
        super(new Gson(), "turret_modifiers");
    }

    public static ModifierEffect get(ResourceLocation item) {
        return effects.getOrDefault(item, ModifierEffect.NONE);
    }

    public static Map<ResourceLocation, ModifierEffect> all() {
        return effects;
    }

    public static void accept(Map<ResourceLocation, ModifierEffect> synced) {
        effects = Map.copyOf(synced);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resources, ProfilerFiller profiler) {
        Map<ResourceLocation, ModifierEffect> loaded = new HashMap<>();
        files.forEach((id, json) -> ModifierEffect.CODEC.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(error -> Bastion.LOGGER.error("Bad turret modifier {}: {}", id, error))
                .ifPresent(effect -> loaded.put(id, effect)));
        effects = Map.copyOf(loaded);
        Bastion.LOGGER.info("Loaded {} turret modifiers", loaded.size());
    }
}

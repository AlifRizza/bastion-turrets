package dev.bastion.client.render;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.bastion.Bastion;
import dev.bastion.workstation.PartItem;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.state.BoneSnapshot;
import software.bernie.geckolib.model.GeoModel;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A part is a piece of a weapon or base model (PLAN Fase 9): assets/bastion/parts.json maps each part to its source
 * model, texture and the bones it shows. Bones are shared with the turret renderers, so every frame this resets them to
 * rest and hides the cubes of every bone not in the part (children are hidden one by one, not with their parent);
 * PartItemRenderer shows them all again afterwards.
 */
public class PartModel extends GeoModel<PartItem> {
    record Shape(String model, String texture, Set<String> bones) {
    }

    private static Map<String, Shape> shapes;

    static Shape shape(PartItem item) {
        if (shapes == null) shapes = load();
        Shape shape = shapes.get(BuiltInRegistries.ITEM.getKey(item).getPath());
        if (shape == null) throw new IllegalStateException("assets/bastion/parts.json has no entry for " + BuiltInRegistries.ITEM.getKey(item));
        return shape;
    }

    private static Map<String, Shape> load() {
        Map<String, Shape> loaded = new HashMap<>();
        try (Reader reader = Minecraft.getInstance().getResourceManager().openAsReader(Bastion.id("parts.json"))) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            root.entrySet().forEach(entry -> {
                JsonObject part = entry.getValue().getAsJsonObject();
                Set<String> bones = new java.util.HashSet<>();
                GsonHelper.getAsJsonArray(part, "bones").forEach(bone -> bones.add(bone.getAsString()));
                loaded.put(entry.getKey(), new Shape(GsonHelper.getAsString(part, "model"), GsonHelper.getAsString(part, "texture"), Set.copyOf(bones)));
            });
        } catch (Exception e) {
            Bastion.LOGGER.error("Could not read assets/bastion/parts.json", e);
        }
        return loaded;
    }

    @Override
    public ResourceLocation getModelResource(PartItem item) {
        return Bastion.id("geo/" + shape(item).model() + ".geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(PartItem item) {
        return Bastion.id("textures/" + shape(item).texture() + ".png");
    }

    @Override
    public ResourceLocation getAnimationResource(PartItem item) {
        return Bastion.id("animations/" + shape(item).model() + ".animation.json");
    }

    @Override
    public void setCustomAnimations(PartItem item, long instanceId, AnimationState<PartItem> animationState) {
        Set<String> shown = shape(item).bones();
        for (CoreGeoBone bone : getAnimationProcessor().getRegisteredBones()) {
            BoneSnapshot rest = bone.getInitialSnapshot();
            bone.setRotX(rest.getRotX());
            bone.setRotY(rest.getRotY());
            bone.setRotZ(rest.getRotZ());
            bone.setPosX(rest.getOffsetX());
            bone.setPosY(rest.getOffsetY());
            bone.setPosZ(rest.getOffsetZ());
            bone.setScaleX(rest.getScaleX());
            bone.setScaleY(rest.getScaleY());
            bone.setScaleZ(rest.getScaleZ());
            bone.setHidden(!shown.contains(bone.getName()));
            bone.setChildrenHidden(false);
        }
    }
}

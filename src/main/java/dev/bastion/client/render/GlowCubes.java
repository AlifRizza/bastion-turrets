package dev.bastion.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.util.RenderUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * The quads of a model that have glow pixels in its {@code _e} texture. EmissiveLayer redraws only these instead of
 * the whole model, which is almost all transparent there: with hundreds of turrets in view the glow pass used to cost
 * as much as the model itself. Renderers opt in by routing renderCubesOfBone through {@link #renderCubesOfBone}.
 */
public final class GlowCubes {
    /** Set by EmissiveLayer for the length of its re-render (render thread only); null = draw every cube. */
    @Nullable
    static Map<GeoCube, boolean[]> active;
    // ponytail: models rebaked by a resource reload get fresh entries and the old few stay behind; fine for F3+T.
    private static final Map<BakedGeoModel, Map<GeoCube, boolean[]>> CACHE = new IdentityHashMap<>();

    private GlowCubes() {
    }

    static Map<GeoCube, boolean[]> of(BakedGeoModel model, ResourceLocation glowTexture) {
        return CACHE.computeIfAbsent(model, m -> scan(m, glowTexture));
    }

    /** GeoRenderer#renderCubesOfBone, skipping cubes and faces without glow while {@link #active} is set. */
    public static <T extends GeoAnimatable> void renderCubesOfBone(GeoRenderer<T> renderer, PoseStack poseStack, GeoBone bone,
                                                                   VertexConsumer buffer, int packedLight, int packedOverlay,
                                                                   float red, float green, float blue, float alpha) {
        if (bone.isHidden()) return;
        Map<GeoCube, boolean[]> glow = active;
        for (GeoCube cube : bone.getCubes()) {
            boolean[] lit = glow == null ? null : glow.get(cube);
            if (glow != null && lit == null) continue;
            poseStack.pushPose();
            if (lit == null) {
                renderer.renderCube(poseStack, cube, buffer, packedLight, packedOverlay, red, green, blue, alpha);
            } else {
                // GeoRenderer#renderCube, for the lit faces only
                RenderUtils.translateToPivotPoint(poseStack, cube);
                RenderUtils.rotateMatrixAroundCube(poseStack, cube);
                RenderUtils.translateAwayFromPivotPoint(poseStack, cube);
                Matrix3f normalPose = poseStack.last().normal();
                Matrix4f pose = poseStack.last().pose();
                GeoQuad[] quads = cube.quads();
                for (int i = 0; i < quads.length; i++) {
                    if (!lit[i]) continue;
                    Vector3f normal = normalPose.transform(new Vector3f(quads[i].normal()));
                    RenderUtils.fixInvertedFlatCube(cube, normal);
                    renderer.createVerticesOfQuad(quads[i], pose, normal, buffer, packedLight, packedOverlay, red, green, blue, alpha);
                }
            }
            poseStack.popPose();
        }
    }

    private static Map<GeoCube, boolean[]> scan(BakedGeoModel model, ResourceLocation texture) {
        Map<GeoCube, boolean[]> glow = new IdentityHashMap<>();
        try (InputStream in = Minecraft.getInstance().getResourceManager().open(texture); NativeImage image = NativeImage.read(in)) {
            for (GeoBone bone : model.topLevelBones()) collect(bone, image, glow);
        } catch (IOException e) {
            // no _e texture: nothing glows
        }
        return glow;
    }

    private static void collect(GeoBone bone, NativeImage image, Map<GeoCube, boolean[]> glow) {
        for (GeoCube cube : bone.getCubes()) {
            GeoQuad[] quads = cube.quads();
            boolean[] lit = new boolean[quads.length];
            boolean any = false;
            for (int i = 0; i < quads.length; i++) {
                lit[i] = quads[i] != null && glows(quads[i], image);
                any |= lit[i];
            }
            if (any) glow.put(cube, lit);
        }
        for (GeoBone child : bone.getChildBones()) collect(child, image, glow);
    }

    /** Any non-transparent pixel inside the face's UV rectangle. */
    private static boolean glows(GeoQuad quad, NativeImage image) {
        float u0 = 1, v0 = 1, u1 = 0, v1 = 0;
        for (GeoVertex vertex : quad.vertices()) {
            u0 = Math.min(u0, vertex.texU());
            u1 = Math.max(u1, vertex.texU());
            v0 = Math.min(v0, vertex.texV());
            v1 = Math.max(v1, vertex.texV());
        }
        int w = image.getWidth(), h = image.getHeight();
        int x0 = Mth.clamp(Mth.floor(u0 * w), 0, w - 1), x1 = Mth.clamp(Mth.ceil(u1 * w) - 1, x0, w - 1);
        int y0 = Mth.clamp(Mth.floor(v0 * h), 0, h - 1), y1 = Mth.clamp(Mth.ceil(v1 * h) - 1, y0, h - 1);
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                if ((image.getPixelRGBA(x, y) >>> 24) != 0) return true;
            }
        }
        return false;
    }
}

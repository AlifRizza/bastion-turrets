package dev.bastion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.bastion.Bastion;
import dev.bastion.turret.BaseKind;
import dev.bastion.turret.TurretBaseBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.RenderUtils;

import java.util.Set;

/**
 * Damage on a makeshift base (PLAN "Turret early game": cracks, not health lights). Its stone and wood cubes are drawn
 * again a hair larger, cutout and lit like the base, with textures/block/makeshift_cracks_N.png. The 16x16 tile spans
 * the block, projected on each face at one texel per model px, so cracks meet across neighbouring faces.
 */
public class CrackLayer extends GeoRenderLayer<TurretBaseBlockEntity> {
    /** Bones that crack (tools/blockout/design_makeshift_base.py): stone and wood; iron and rope stay whole. */
    private static final Set<String> CRACKED = Set.of("plinth", "body", "posts", "deck");
    /** Health fractions at or below which crack levels 0, 1 and 2 show. */
    private static final float[] STAGES = {0.75f, 0.5f, 0.25f};
    /** How far the cracks stand off the faces, in blocks (~0.16 px): in front of them without z-fighting. */
    private static final float INFLATE = 0.01f;
    private static final RenderType[] TYPES = new RenderType[STAGES.length];

    static {
        for (int i = 0; i < TYPES.length; i++) TYPES[i] = RenderType.entityCutoutNoCull(Bastion.id("textures/block/makeshift_cracks_" + i + ".png"));
    }

    public CrackLayer(GeoRenderer<TurretBaseBlockEntity> renderer) {
        super(renderer);
    }

    /** The crack level for a health fraction, -1 while it is still whole. */
    static int stage(float health) {
        int stage = -1;
        while (stage + 1 < STAGES.length && health <= STAGES[stage + 1]) stage++;
        return stage;
    }

    @Override
    public void render(PoseStack poseStack, TurretBaseBlockEntity turret, BakedGeoModel bakedModel, RenderType renderType,
                       MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
        if (turret.kind() != BaseKind.MAKESHIFT) return;
        int stage = stage(turret.clientHealth());
        if (stage < 0) return;
        VertexConsumer cracks = bufferSource.getBuffer(TYPES[stage]);
        for (GeoBone bone : bakedModel.topLevelBones()) draw(poseStack, bone, cracks, packedLight);
    }

    /** GeoRenderer#renderRecursively for the cracked bones, as BakedTurretBaseModel walks them. */
    private static void draw(PoseStack pose, GeoBone bone, VertexConsumer out, int light) {
        pose.pushPose();
        RenderUtils.prepMatrixForBone(pose, bone);
        if (CRACKED.contains(bone.getName())) {
            for (GeoCube cube : bone.getCubes()) {
                pose.pushPose();
                RenderUtils.translateToPivotPoint(pose, cube);
                RenderUtils.rotateMatrixAroundCube(pose, cube);
                RenderUtils.translateAwayFromPivotPoint(pose, cube);
                for (GeoQuad quad : cube.quads()) if (quad != null) quad(pose.last().pose(), pose.last().normal(), quad, out, light);
                pose.popPose();
            }
        }
        for (GeoBone child : bone.getChildBones()) draw(pose, child, out, light);
        pose.popPose();
    }

    private static void quad(Matrix4f pose, Matrix3f normalPose, GeoQuad quad, VertexConsumer out, int light) {
        Vector3f n = quad.normal();
        Vector3f lit = normalPose.transform(new Vector3f(n));
        boolean top = Math.abs(n.y) >= Math.max(Math.abs(n.x), Math.abs(n.z)), side = Math.abs(n.x) >= Math.abs(n.z);
        for (GeoVertex vertex : quad.vertices()) {
            Vector3f p = vertex.position(); // block units: x and z in -0.5..0.5, y in 0..1
            float u = !top && side ? p.z + 0.5f : p.x + 0.5f;
            float v = top ? p.z + 0.5f : 1 - p.y;
            out.vertex(pose, p.x + n.x * INFLATE, p.y + n.y * INFLATE, p.z + n.z * INFLATE).color(1f, 1f, 1f, 1f).uv(u, v)
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(lit.x, lit.y, lit.z).endVertex();
        }
    }
}

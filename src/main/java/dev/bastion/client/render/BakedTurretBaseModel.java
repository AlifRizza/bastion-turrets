package dev.bastion.client.render;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.bastion.turret.LargeTurretBaseBlock;
import dev.bastion.turret.TurretBaseBlock;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretTier;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.IDynamicBakedModel;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.geometry.IGeometryBakingContext;
import net.minecraftforge.client.model.geometry.IGeometryLoader;
import net.minecraftforge.client.model.geometry.IUnbakedGeometry;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.util.RenderUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * The still part of a turret base, meshed into the chunk like any block (bases with hundreds of turrets: drawing the
 * base through GeckoLib every frame was most of the render cost). The quads come from the base's own GeckoLib
 * geometry, with GeckoLib's own transforms, so it looks as before; TurretBaseRenderer keeps the animated energy ring,
 * the glow and the weapon. Built lazily per facing, tier and quadrant: GeckoLib loads its models in its own reload
 * listener, after block models bake. Model JSON: {@code "loader": "bastion:turret_base", "geo": ..., "texture": ...}.
 */
public final class BakedTurretBaseModel implements IDynamicBakedModel {
    /** Bones TurretBaseRenderer still draws every frame, with their children (the base's idle animation moves only these). */
    public static final Set<String> ANIMATED = Set.of("energy_ring");

    private final ResourceLocation geo;
    private final TextureAtlasSprite sprite;
    private final Map<Key, List<BakedQuad>> quads = new ConcurrentHashMap<>();

    private record Key(Direction facing, TurretTier tier, int quadrant) {
    }

    private BakedTurretBaseModel(ResourceLocation geo, TextureAtlasSprite sprite) {
        this.geo = geo;
        this.sprite = sprite;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData data,
                                    @Nullable RenderType renderType) {
        if (side != null || state == null || !state.hasProperty(TurretBaseBlock.FACING)) return List.of();
        boolean large = state.hasProperty(LargeTurretBaseBlock.QUADRANT);
        if (large && !state.getValue(LargeTurretBaseBlock.CORE)) return List.of(); // the core block draws the whole 2x2
        TurretTier tier = Objects.requireNonNullElse(data.get(TurretBaseBlockEntity.MODEL_TIER), TurretTier.T1);
        Key key = new Key(state.getValue(TurretBaseBlock.FACING), tier, large ? state.getValue(LargeTurretBaseBlock.QUADRANT) : -1);
        List<BakedQuad> list = quads.computeIfAbsent(key, this::build); // null (GeckoLib not loaded yet) is not cached
        return list == null ? List.of() : list;
    }

    @Nullable
    private List<BakedQuad> build(Key key) {
        BakedGeoModel model = GeckoLibCache.getBakedModels().get(geo);
        if (model == null) return null;
        // GeoBlockRenderer's placement, then TurretBaseRenderer#rotateBlock: a large base turns around its 2x2 middle.
        PoseStack pose = new PoseStack();
        if (key.quadrant < 0) pose.translate(0.5, 0, 0.5);
        else pose.translate(1 - (key.quadrant & 1), 0, 1 - (key.quadrant >> 1));
        pose.translate(0, 0.5, 0);
        pose.mulPose(key.facing.getRotation());
        pose.translate(0, -0.5, 0);
        List<BakedQuad> out = new ArrayList<>();
        for (GeoBone bone : model.topLevelBones()) collect(pose, bone, key.tier, out);
        return List.copyOf(out);
    }

    /** GeoRenderer#renderRecursively at rest pose, skipping animated bones and the tier parts this tier lacks. */
    private void collect(PoseStack pose, GeoBone bone, TurretTier tier, List<BakedQuad> out) {
        if (ANIMATED.contains(bone.getName()) || !TurretBaseModel.shown(bone.getName(), tier)) return;
        pose.pushPose();
        RenderUtils.prepMatrixForBone(pose, bone);
        for (GeoCube cube : bone.getCubes()) {
            pose.pushPose();
            RenderUtils.translateToPivotPoint(pose, cube);
            RenderUtils.rotateMatrixAroundCube(pose, cube);
            RenderUtils.translateAwayFromPivotPoint(pose, cube);
            Matrix4f matrix = pose.last().pose();
            boolean flat = cube.size().x == 0 || cube.size().y == 0 || cube.size().z == 0;
            Vector3f centre = new Vector3f();
            int corners = 0;
            for (GeoQuad quad : cube.quads()) {
                if (quad == null) continue;
                for (GeoVertex vertex : quad.vertices()) centre.add(matrix.transformPosition(new Vector3f(vertex.position())));
                corners += 4;
            }
            centre.div(Math.max(corners, 1));
            for (GeoQuad quad : cube.quads()) {
                if (quad == null) continue;
                bake(quad, matrix, centre, false, out);
                if (flat) bake(quad, matrix, centre, true, out); // a flat cube's face is seen from both sides
            }
            pose.popPose();
        }
        for (GeoBone child : bone.getChildBones()) collect(pose, child, tier, out);
        pose.popPose();
    }

    /**
     * One block-format quad facing out of its cube. Chunk layers cull back faces while GeckoLib draws without culling
     * (and mirrors X, flipping some windings), so winding and normal are taken from the geometry: away from the cube's
     * centre, or along GeckoLib's normal (and its reverse) for a flat cube.
     */
    private void bake(GeoQuad quad, Matrix4f matrix, Vector3f centre, boolean back, List<BakedQuad> out) {
        GeoVertex[] vertices = quad.vertices();
        Vector3f[] p = new Vector3f[4];
        for (int i = 0; i < 4; i++) p[i] = matrix.transformPosition(new Vector3f(vertices[i].position()));
        Vector3f outward = new Vector3f(p[0]).add(p[1]).add(p[2]).add(p[3]).div(4).sub(centre);
        if (back || outward.lengthSquared() < 1e-8f) outward = matrix.transformDirection(new Vector3f(quad.normal()));
        if (back) outward.negate();
        Vector3f normal = new Vector3f(p[1]).sub(p[0]).cross(new Vector3f(p[2]).sub(p[0]));
        if (normal.lengthSquared() < 1e-12f) return; // degenerate
        boolean reverse = normal.dot(outward) < 0;
        normal.normalize();
        if (reverse) normal.negate();
        int[] data = new int[32];
        for (int i = 0; i < 4; i++) {
            int v = reverse ? 3 - i : i;
            int o = i * 8;
            data[o] = Float.floatToRawIntBits(p[v].x);
            data[o + 1] = Float.floatToRawIntBits(p[v].y);
            data[o + 2] = Float.floatToRawIntBits(p[v].z);
            data[o + 3] = -1; // white
            data[o + 4] = Float.floatToRawIntBits(sprite.getU(vertices[v].texU() * 16));
            data[o + 5] = Float.floatToRawIntBits(sprite.getV(vertices[v].texV() * 16));
            data[o + 6] = 0; // the chunk renderer lights it
            data[o + 7] = (Math.round(normal.x * 127) & 255) | (Math.round(normal.y * 127) & 255) << 8 | (Math.round(normal.z * 127) & 255) << 16;
        }
        out.add(new BakedQuad(data, -1, Direction.getNearest(normal.x, normal.y, normal.z), sprite, true));
    }

    // Lit like GeckoLib's entity rendering: directional shade, no ambient occlusion.
    @Override
    public boolean useAmbientOcclusion() {
        return false;
    }

    @Override
    public boolean isGui3d() {
        return true;
    }

    @Override
    public boolean usesBlockLight() {
        return true;
    }

    @Override
    public boolean isCustomRenderer() {
        return false;
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return sprite;
    }

    @Override
    public ItemOverrides getOverrides() {
        return ItemOverrides.EMPTY;
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        return ChunkRenderTypeSet.of(RenderType.cutout());
    }

    public static final class Loader implements IGeometryLoader<Geometry> {
        @Override
        public Geometry read(JsonObject json, JsonDeserializationContext context) {
            return new Geometry(new ResourceLocation(GsonHelper.getAsString(json, "geo")), new ResourceLocation(GsonHelper.getAsString(json, "texture")));
        }
    }

    public record Geometry(ResourceLocation geo, ResourceLocation texture) implements IUnbakedGeometry<Geometry> {
        @Override
        public BakedModel bake(IGeometryBakingContext context, ModelBaker baker, Function<Material, TextureAtlasSprite> spriteGetter,
                               ModelState modelState, ItemOverrides overrides, ResourceLocation modelLocation) {
            return new BakedTurretBaseModel(geo, spriteGetter.apply(new Material(InventoryMenu.BLOCK_ATLAS, texture)));
        }
    }
}

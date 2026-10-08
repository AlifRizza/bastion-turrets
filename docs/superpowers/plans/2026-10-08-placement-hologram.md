# Placement Hologram Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** While a player holds a multiblock item (Large Turret Base, Part Workstation, Part Assembler, Module Workstation, Ammo Workstation) and aims at a block, show a translucent ghost of the model plus a box per block it would fill, cyan where free and red where blocked.

**Architecture:** A `Multiblock` interface gives `LargeTurretBaseBlock` and `WorkstationBlock` one `footprint(BlockPlaceContext)` method (blocks, blocked blocks, front). Their `getStateForPlacement` is rewritten on top of it, so placement and preview cannot disagree. A client-only `PlacementPreview` (RenderLevelStageEvent) builds the same context the click would, then draws per-block boxes (own render type + vanilla line boxes) and a `GhostRenderer` (GeckoLib `GeoObjectRenderer`) at the footprint centre.

**Tech Stack:** Forge 1.20.1 (47.x), GeckoLib 4.8.4, Java 17, GameTest.

**Spec:** `docs/PLAN.md` → "Hologram penempatan multiblock (permintaan user 2026-10-08 00:44, desain B disetujui 00:46)"

## Global Constraints

- PLAN 9.5: everything touching `net.minecraft.client` lives in `dev.bastion.client.*`; common code only reaches it through Forge client events.
- PLAN 1 NO-VANILLA: own textures/models; vanilla `RenderType`/`LevelRenderer` APIs are allowed as code foundation.
- Glow colour cyan = `EmissiveLayer.MODEL_GLOW` (0x4FD8FF); blocked red = 0xFF3A30.
- Ghost ~40% alpha, box fill ~15%, box edges ~60%; fullbright; depth-tested; ghost and fill write no depth.
- 1x1 blocks (Turret Base, Charging Station, Feed Hub) get no hologram.
- Do not commit; the user says "commit" (CLAUDE.md / session rule). Never touch `run/saves/showcase`; scenes use `-PshowcaseWorld=showcase_fx`.
- Run with `--offline`.

## Review Focus

1. Workstation ghost turned the wrong way for EAST/WEST fronts (rotation sign): the ghost must match the placed station exactly. Pinned by Task 4's scene, which places the real station next to its ghost for a west-facing player.
2. Main hand holds another block while the off hand holds a multiblock: vanilla places the main-hand block, so no hologram may show. Pinned in Task 3 (hand loop rule) and Task 4 (scene step).
3. Large Base aimed at a wall or ceiling face: it can never go there, no hologram. Pinned in Task 1 test (`footprint` returns null for a side face).
4. Shared GeckoLib bones: after a T1 ghost hides `armor_t2`/`fins_t3`, the T3 base item in the hotbar and placed T3 bases must still show their armour. Each base renderer re-applies `showTier` every frame; Task 4 scene shows a placed T3 base next to a T1 ghost.
5. Standing inside the footprint: the click fails (vanilla entity check at the clicked block, the large base's `isUnobstructed` for the rest). The large base marks its other cells red; the clicked cell is not marked for entities (vanilla rule, out of scope). Documented in TESTING.

---

### Task 1: One footprint for placement and preview

**Files:**
- Create: `src/main/java/dev/bastion/turret/Footprint.java`
- Create: `src/main/java/dev/bastion/turret/Multiblock.java`
- Modify: `src/main/java/dev/bastion/turret/LargeTurretBaseBlock.java` (`getStateForPlacement`, lines ~67-92)
- Modify: `src/main/java/dev/bastion/workstation/WorkstationBlock.java` (`getStateForPlacement`, lines ~94-104)
- Test: `src/main/java/dev/bastion/gametest/TurretPlacementTests.java` (new)

**Interfaces:**
- Produces: `record Footprint(List<BlockPos> blocks, Set<BlockPos> blocked, Direction front)` with `boolean placeable()`; `interface Multiblock { @Nullable Footprint footprint(BlockPlaceContext context); }`. `front` is `Direction.UP` for the large base (it does not turn), the station's FACING for workstations. `blocks` order = the block's own order (large: clicked first; workstation: part 0 first).

- [ ] **Step 1: Write the failing tests**

```java
package dev.bastion.gametest;

import dev.bastion.Bastion;
import dev.bastion.registry.BastionBlocks;
import dev.bastion.registry.BastionItems;
import dev.bastion.turret.Footprint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.Set;

/** Placement hologram (PLAN "Hologram penempatan multiblock"): the footprint is exactly what a click fills. */
@GameTestHolder(Bastion.MOD_ID)
@PrefixGameTestTemplate(false)
public class TurretPlacementTests {
    private static final String ARENA = "arena";
    /** The floor block clicked (template floors sit at test y 1); the block goes on top of it. */
    private static final BlockPos FLOOR = new BlockPos(4, 1, 4);

    /** A fake player north of the spot looking along {@code yaw} with {@code item} in hand, clicking {@code face} of FLOOR. */
    private static BlockPlaceContext click(GameTestHelper helper, Item item, float yaw, Direction face) {
        FakePlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        BlockPos floor = helper.absolutePos(FLOOR);
        player.moveTo(floor.getX() + 0.5, floor.getY() + 1, floor.getZ() - 2.5, yaw, 30);
        ItemStack stack = new ItemStack(item);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        Vec3 hit = Vec3.atCenterOf(floor).relative(face, 0.5);
        return new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, new BlockHitResult(hit, face, floor, false));
    }

    /** Absolute positions of {@code block} within 3 blocks of the spot. */
    private static Set<BlockPos> placed(GameTestHelper helper, Block block) {
        Set<BlockPos> found = new HashSet<>();
        BlockPos.betweenClosed(helper.absolutePos(FLOOR).offset(-3, 1, -3), helper.absolutePos(FLOOR).offset(3, 3, 3))
                .forEach(p -> { if (helper.getLevel().getBlockState(p).is(block)) found.add(p.immutable()); });
        return found;
    }

    @GameTest(template = ARENA, batch = "largeBaseFootprintIsWhatGetsPlaced")
    public static void largeBaseFootprintIsWhatGetsPlaced(GameTestHelper helper) {
        helper.assertTrue(BastionBlocks.LARGE_TURRET_BASE.get().footprint(click(helper, BastionItems.LARGE_TURRET_BASE.get(), 90, Direction.NORTH)) == null,
                "a large base offered a footprint on a side face");
        BlockPlaceContext context = click(helper, BastionItems.LARGE_TURRET_BASE.get(), 90, Direction.UP); // looking west
        Footprint footprint = BastionBlocks.LARGE_TURRET_BASE.get().footprint(context);
        helper.assertTrue(footprint != null && footprint.placeable() && footprint.blocks().size() == 4, "footprint " + footprint);
        helper.assertTrue(((BlockItem) BastionItems.LARGE_TURRET_BASE.get()).place(context).consumesAction(), "not placed");
        Set<BlockPos> placed = placed(helper, BastionBlocks.LARGE_TURRET_BASE.get());
        helper.assertTrue(placed.equals(Set.copyOf(footprint.blocks())), "placed " + placed + ", footprint " + footprint.blocks());
        helper.succeed();
    }

    @GameTest(template = ARENA, batch = "assemblerFootprintMarksTheBlocker")
    public static void assemblerFootprintMarksTheBlocker(GameTestHelper helper) {
        BlockItem item = (BlockItem) BastionItems.PART_ASSEMBLER.get();
        BlockPlaceContext context = click(helper, item, 0, Direction.UP); // looking south: the front faces north, back at the player
        BlockPos blocker = BastionBlocks.PART_ASSEMBLER.get().footprint(context).blocks().get(7); // far top corner
        helper.getLevel().setBlockAndUpdate(blocker, Blocks.STONE.defaultBlockState());
        Footprint footprint = BastionBlocks.PART_ASSEMBLER.get().footprint(context);
        helper.assertTrue(footprint.front() == Direction.NORTH && footprint.blocks().size() == 8, "footprint " + footprint);
        helper.assertTrue(footprint.blocked().equals(Set.of(blocker)), "blocked " + footprint.blocked() + ", expected " + blocker);
        helper.assertTrue(!item.place(context).consumesAction(), "placed through a stone block");
        helper.getLevel().setBlockAndUpdate(blocker, Blocks.AIR.defaultBlockState());
        helper.assertTrue(item.place(context).consumesAction(), "not placed once clear");
        Set<BlockPos> placed = placed(helper, BastionBlocks.PART_ASSEMBLER.get());
        helper.assertTrue(placed.equals(Set.copyOf(footprint.blocks())), "placed " + placed + ", footprint " + footprint.blocks());
        helper.succeed();
    }
}
```

- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew runGameTestServer --offline -q; grep -E "required tests|failed!" run/logs/latest.log`
Expected: compile error (`Footprint` / `footprint()` do not exist) → that is the failing state.

- [ ] **Step 3: Implement**

`Footprint.java`:
```java
package dev.bastion.turret;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.List;
import java.util.Set;

/**
 * Where a multiblock goes when placed with a given click (PLAN "Hologram penempatan multiblock"): every block it fills,
 * the ones in the way, and where its front looks (UP: it does not turn). Placement and the hologram both read it.
 */
public record Footprint(List<BlockPos> blocks, Set<BlockPos> blocked, Direction front) {
    public boolean placeable() {
        return blocked.isEmpty();
    }
}
```

`Multiblock.java`:
```java
package dev.bastion.turret;

import net.minecraft.world.item.context.BlockPlaceContext;
import org.jetbrains.annotations.Nullable;

/** A block that fills more than one block when placed: Large Turret Base, multi-block workstations. */
public interface Multiblock {
    /** The blocks this click would fill, or null where it can never go (a large base on a wall) or for a 1x1 block. */
    @Nullable
    Footprint footprint(BlockPlaceContext context);
}
```

`LargeTurretBaseBlock`: `implements Multiblock`; replace `getStateForPlacement` with:
```java
    /**
     * Floor only, clicking the top of a block: the base grows forward and to the right of the player from the
     * clicked spot. A block is in the way when it cannot be replaced or something stands in it.
     */
    @Override
    @Nullable
    public Footprint footprint(BlockPlaceContext context) {
        if (context.getClickedFace() != Direction.UP) return null;
        BlockPos pos = context.getClickedPos();
        Direction forward = context.getHorizontalDirection();
        List<BlockPos> blocks = List.of(pos, pos.relative(forward), pos.relative(forward.getClockWise()),
                pos.relative(forward).relative(forward.getClockWise()));
        Level level = context.getLevel();
        Set<BlockPos> blocked = new HashSet<>();
        if (!context.canPlace()) blocked.add(pos);
        for (BlockPos p : blocks) {
            if (p.equals(pos)) continue;
            if (!level.getBlockState(p).canBeReplaced(context) || !level.isUnobstructed(defaultBlockState(), p, CollisionContext.empty())) {
                blocked.add(p);
            }
        }
        return new Footprint(blocks, blocked, Direction.UP);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Footprint footprint = footprint(context);
        if (footprint == null || !footprint.placeable()) return null;
        BlockPos pos = context.getClickedPos();
        int minX = footprint.blocks().stream().mapToInt(BlockPos::getX).min().orElseThrow();
        int minZ = footprint.blocks().stream().mapToInt(BlockPos::getZ).min().orElseThrow();
        int quadrant = (pos.getX() - minX) | (pos.getZ() - minZ) << 1;
        return defaultBlockState().setValue(QUADRANT, quadrant).setValue(CORE, true);
    }
```
Imports: `dev.bastion.turret` is the same package; add `java.util.HashSet`, `java.util.Set`.

`WorkstationBlock`: `implements Multiblock` (import `dev.bastion.turret.Footprint`, `dev.bastion.turret.Multiblock`, `java.util.HashSet`, `java.util.Set`); replace `getStateForPlacement` with:
```java
    /** Grows to the right of, up from and away from its front, which faces the player. 1x1 stations: none. */
    @Override
    @Nullable
    public Footprint footprint(BlockPlaceContext context) {
        if (type.blocks() == 1) return null;
        Direction front = context.getHorizontalDirection().getOpposite();
        Level level = context.getLevel();
        List<BlockPos> blocks = footprint(context.getClickedPos(), front);
        Set<BlockPos> blocked = new HashSet<>();
        if (!context.canPlace()) blocked.add(context.getClickedPos());
        for (BlockPos pos : blocks) {
            if (pos.equals(context.getClickedPos())) continue;
            if (pos.getY() >= level.getMaxBuildHeight() || !level.getBlockState(pos).canBeReplaced(context)) blocked.add(pos);
        }
        return new Footprint(blocks, blocked, front);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Footprint footprint = footprint(context);
        if (footprint != null && !footprint.placeable()) return null;
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(PART, 0);
    }
```
Note: the clicked-block `canPlace()` check is new inside these methods but `BlockItem.place` already fails on it before calling them, so placement behaves as before.

- [ ] **Step 4: Run tests**

Run: `./gradlew build --offline -q && ./gradlew runGameTestServer --offline -q; grep -E "required tests|failed!" run/logs/latest.log`
Expected: `All 102 required tests passed` (100 + 2 new; workstation and large base tests unchanged).

---

### Task 2: Ghost model renderer (client)

**Files:**
- Create: `src/main/java/dev/bastion/client/render/PlacementGhost.java`
- Create: `src/main/java/dev/bastion/client/render/GhostModel.java`
- Create: `src/main/java/dev/bastion/client/render/GhostRenderer.java`

**Interfaces:**
- Consumes: `TurretBaseModel.showTier(BakedGeoModel, TurretTier)`, `BastionRenderTypes.translucentEmissive(ResourceLocation)` (translucent, fullbright, no depth write, no cull).
- Produces: `PlacementGhost.of(String model, @Nullable TurretTier tier)`; `GhostRenderer#draw(PoseStack poseStack, MultiBufferSource buffers, PlacementGhost ghost, int rgb)` drawing the model at the pose origin (model origin = footprint centre on the floor, front north), tinted `rgb` at 40% alpha.

- [ ] **Step 1: Check the model files exist for every multiblock**

Run: `for n in large_turret_base part_workstation part_assembler module_workstation ammo_workstation; do ls src/main/resources/assets/bastion/geo/$n.geo.json src/main/resources/assets/bastion/textures/block/$n.png src/main/resources/assets/bastion/animations/$n.animation.json; done`
Expected: 15 paths, no "No such file". (Model name = block registry path.)

- [ ] **Step 2: Implement**

`PlacementGhost.java`:
```java
package dev.bastion.client.render;

import dev.bastion.turret.TurretTier;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.HashMap;
import java.util.Map;

/** What the placement hologram draws: a block's model at rest (name = block id path), at a base tier (null: not a base). */
public final class PlacementGhost implements GeoAnimatable {
    private static final Map<String, PlacementGhost> ALL = new HashMap<>();
    final String model;
    @Nullable
    final TurretTier tier;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private PlacementGhost(String model, @Nullable TurretTier tier) {
        this.model = model;
        this.tier = tier;
    }

    /** One per model and tier, so GeckoLib keeps one instance cache for it. */
    public static PlacementGhost of(String model, @Nullable TurretTier tier) {
        return ALL.computeIfAbsent(model + "/" + tier, key -> new PlacementGhost(model, tier));
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public double getTick(Object object) {
        return 0;
    }
}
```

`GhostModel.java`:
```java
package dev.bastion.client.render;

import dev.bastion.Bastion;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BoneSnapshot;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

/**
 * The placement hologram's model: the block's own geo and texture. Bones are shared with the block renderers, so every
 * frame this puts them at rest and shows them all, then hides the tier parts above the item's tier (bases).
 */
public class GhostModel extends GeoModel<PlacementGhost> {
    @Override
    public ResourceLocation getModelResource(PlacementGhost ghost) {
        return Bastion.id("geo/" + ghost.model + ".geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(PlacementGhost ghost) {
        return Bastion.id("textures/block/" + ghost.model + ".png");
    }

    @Override
    public ResourceLocation getAnimationResource(PlacementGhost ghost) {
        return Bastion.id("animations/" + ghost.model + ".animation.json");
    }

    @Override
    public void setCustomAnimations(PlacementGhost ghost, long instanceId, AnimationState<PlacementGhost> animationState) {
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
            bone.setHidden(false);
            bone.setChildrenHidden(false);
        }
        if (ghost.tier != null) TurretBaseModel.showTier(getBakedModel(getModelResource(ghost)), ghost.tier);
    }
}
```
(Import `BoneSnapshot` from the same package `PartModel` imports it from.)

`GhostRenderer.java`:
```java
package dev.bastion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.core.object.Color;
import software.bernie.geckolib.renderer.GeoObjectRenderer;

/** Draws a PlacementGhost: the real texture, tinted (cyan free, red blocked), 40% alpha, fullbright, no depth write. */
public class GhostRenderer extends GeoObjectRenderer<PlacementGhost> {
    private static final int ALPHA = 0x66; // ~40%
    private int rgb = EmissiveLayer.MODEL_GLOW;

    public GhostRenderer() {
        super(new GhostModel());
    }

    /** The model with its origin (footprint centre on the floor, front north) at the pose's origin. */
    public void draw(PoseStack poseStack, MultiBufferSource buffers, PlacementGhost ghost, int rgb) {
        this.rgb = rgb;
        render(poseStack, ghost, buffers, null, null, LightTexture.FULL_BRIGHT);
    }

    @Override
    public RenderType getRenderType(PlacementGhost ghost, ResourceLocation texture, MultiBufferSource buffers, float partialTick) {
        return BastionRenderTypes.translucentEmissive(texture);
    }

    @Override
    public Color getRenderColor(PlacementGhost ghost, float partialTick, int packedLight) {
        return Color.ofARGB(ALPHA << 24 | rgb);
    }

    /** The caller already placed the origin, so skip GeoObjectRenderer's block-centre offset. */
    @Override
    public void preRender(PoseStack poseStack, PlacementGhost ghost, BakedGeoModel model, MultiBufferSource buffers,
                          VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                          float red, float green, float blue, float alpha) {
        objectRenderTranslations = new Matrix4f(poseStack.last().pose());
    }
}
```
Before writing `Color.ofARGB(int)`, confirm it: `javap -cp <geckolib jar> software.bernie.geckolib.core.object.Color | grep ofARGB`; if only `ofARGB(int,int,int,int)` exists, use `Color.ofARGB(ALPHA, rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255)`.

- [ ] **Step 3: Build**

Run: `./gradlew build --offline -q`
Expected: BUILD SUCCESSFUL (nothing draws it yet; Task 3 wires it in, Task 4 checks it on screen).

---

### Task 3: PlacementPreview (boxes + ghost each frame)

**Files:**
- Create: `src/main/java/dev/bastion/client/render/PlacementPreview.java`
- Modify: `src/main/java/dev/bastion/client/render/BastionRenderTypes.java` (add `ghostBox()`)

**Interfaces:**
- Consumes: `Multiblock.footprint`, `Footprint`, `PlacementGhost.of`, `GhostRenderer#draw`, `TurretBaseItem.tier(ItemStack)`.
- Produces: nothing other code calls (event subscriber).

- [ ] **Step 1: Add the box render type** in `BastionRenderTypes` next to `MODEL_GLOW`:
```java
    private static final RenderType GHOST_BOX = create("bastion_ghost_box", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS,
            256, false, true, CompositeState.builder()
                    .setShaderState(POSITION_COLOR_SHADER)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(false));

    /** Placement hologram boxes: flat colour, translucent, both sides, no depth write. */
    public static RenderType ghostBox() {
        return GHOST_BOX;
    }
```

- [ ] **Step 2: Implement `PlacementPreview`**
```java
package dev.bastion.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.bastion.Bastion;
import dev.bastion.turret.Footprint;
import dev.bastion.turret.Multiblock;
import dev.bastion.turret.TurretBaseItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Placement hologram (PLAN "Hologram penempatan multiblock"): holding a multiblock and aiming at a block shows its model
 * as a ghost and a box per block it would fill, cyan where free, red where something is in the way. Reads the same
 * Multiblock#footprint the click uses.
 */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID, value = Dist.CLIENT)
public final class PlacementPreview {
    private static final int FREE = EmissiveLayer.MODEL_GLOW, BLOCKED = 0xFF3A30;
    private static final float FILL = 0.15f, EDGE = 0.6f;
    private static GhostRenderer ghosts;

    private PlacementPreview() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.isSpectator() || mc.screen != null
                || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        for (InteractionHand hand : InteractionHand.values()) { // like a click: the main hand's block wins
            ItemStack stack = mc.player.getItemInHand(hand);
            if (!(stack.getItem() instanceof BlockItem item)) continue;
            if (!(item.getBlock() instanceof Multiblock multiblock)) return;
            BlockPlaceContext context = item.updatePlacementContext(new BlockPlaceContext(mc.player, hand, stack, hit));
            Footprint footprint = context == null ? null : multiblock.footprint(context);
            if (footprint != null) draw(event.getPoseStack(), event.getCamera().getPosition(), footprint, stack, item);
            return;
        }
    }

    private static void draw(PoseStack pose, Vec3 camera, Footprint footprint, ItemStack stack, BlockItem item) {
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        for (BlockPos pos : footprint.blocks()) box(pose, buffers, pos, footprint.blocked().contains(pos) ? BLOCKED : FREE);
        // Model origin = the footprint's centre on its floor, front north (as the block renderers place it).
        double x = footprint.blocks().stream().mapToInt(BlockPos::getX).average().orElseThrow() + 0.5;
        double z = footprint.blocks().stream().mapToInt(BlockPos::getZ).average().orElseThrow() + 0.5;
        int y = footprint.blocks().stream().mapToInt(BlockPos::getY).min().orElseThrow();
        pose.translate(x, y, z);
        pose.mulPose(Axis.YP.rotationDegrees(turn(footprint.front())));
        if (ghosts == null) ghosts = new GhostRenderer();
        String model = BuiltInRegistries.BLOCK.getKey(item.getBlock()).getPath();
        ghosts.draw(pose, buffers, PlacementGhost.of(model, item instanceof TurretBaseItem ? TurretBaseItem.tier(stack) : null),
                footprint.placeable() ? FREE : BLOCKED);
        pose.popPose();
        buffers.endBatch();
    }

    /** GeoBlockRenderer#rotateBlock's turn for a front (the models face north); UP and DOWN do not turn. */
    private static float turn(Direction front) {
        return switch (front) {
            case SOUTH -> 180;
            case WEST -> 90;
            case EAST -> 270;
            default -> 0;
        };
    }

    private static void box(PoseStack pose, MultiBufferSource buffers, BlockPos pos, int rgb) {
        float r = (rgb >> 16 & 255) / 255f, g = (rgb >> 8 & 255) / 255f, b = (rgb & 255) / 255f;
        AABB box = new AABB(pos).inflate(0.002); // just outside the block faces: no z-fighting with what is there
        LevelRenderer.renderLineBox(pose, buffers.getBuffer(RenderType.lines()), box, r, g, b, EDGE);
        VertexConsumer fill = buffers.getBuffer(BastionRenderTypes.ghostBox());
        Matrix4f m = pose.last().pose();
        float x0 = (float) box.minX, y0 = (float) box.minY, z0 = (float) box.minZ, x1 = (float) box.maxX, y1 = (float) box.maxY, z1 = (float) box.maxZ;
        float[][] quads = {
                {x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1}, {x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0},
                {x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0}, {x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1},
                {x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0}, {x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1}};
        for (float[] q : quads) {
            for (int i = 0; i < 12; i += 3) fill.vertex(m, q[i], q[i + 1], q[i + 2]).color(r, g, b, FILL).endVertex();
        }
    }
}
```
Check `BlockItem#updatePlacementContext` is public in 1.20.1 (it is `@Nullable public`); if the compiler says otherwise, use `new BlockPlaceContext(...)` directly (plain BlockItems return it unchanged).

- [ ] **Step 3: Build and run all tests**

Run: `./gradlew build --offline -q && ./gradlew runGameTestServer --offline -q; grep -E "required tests|failed!" run/logs/latest.log`
Expected: `All 102 required tests passed`.

---

### Task 4: Dev scene + visual check

**Files:**
- Create: `src/dev/java/dev/bastion/dev/PlacementShowcase.java`
- Modify: `src/dev/java/dev/bastion/dev/Showcase.java` (add `&& !...equals("placement")` to `ON`)

- [ ] **Step 1: Write the scene** (`-Pshowcase=placement`), modelled on `HealthShowcase`; add `&& !System.getProperty("bastion.showcase").equals("placement")` to `Showcase.ON`.

```java
package dev.bastion.dev;

import dev.bastion.Bastion;
import dev.bastion.registry.BastionBlocks;
import dev.bastion.registry.BastionItems;
import dev.bastion.turret.TurretBaseItem;
import dev.bastion.turret.TurretTier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Dev-only: the placement hologram (-Pshowcase=placement in a scratch world). A T3 Large Base ghost free and blocked,
 * a Part Assembler ghost facing west next to the real station placed on the same spot, main hand beating off hand,
 * and a night shot.
 */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID, value = Dist.CLIENT)
public final class PlacementShowcase {
    private static final boolean ON = "placement".equals(System.getProperty("bastion.showcase"));
    private static final BlockPos ORIGIN = new BlockPos(0, 150, 0); // the block a station or base would fill first
    private static int tick = -1;

    private PlacementShowcase() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (!ON || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (mc.player == null || server == null) return;
        mc.options.pauseOnLostFocus = false;
        if (mc.screen instanceof PauseScreen) mc.setScreen(null);
        tick++;
        Vec3 floorTop = Vec3.atBottomCenterOf(ORIGIN);
        switch (tick) {
            case 20 -> Showcase.server(server, PlacementShowcase::build);
            case 30 -> {
                mc.options.hideGui = true;
                hold(server, TurretBaseItem.withTier(BastionItems.LARGE_TURRET_BASE.get(), TurretTier.T3), ItemStack.EMPTY);
                Showcase.camera(server, floorTop.add(-2.5, 2.6, -2.5), floorTop); // looking south-east: grows south-east
            }
            case 60 -> Showcase.shot(mc, "placement_large");
            case 62 -> Showcase.server(server, s -> s.overworld().setBlockAndUpdate(ORIGIN.south().east(), Blocks.STONE.defaultBlockState()));
            case 80 -> Showcase.shot(mc, "placement_large_blocked");
            case 82 -> {
                Showcase.server(server, s -> s.overworld().setBlockAndUpdate(ORIGIN.south().east(), Blocks.AIR.defaultBlockState()));
                hold(server, new ItemStack(BastionItems.PART_ASSEMBLER.get()), ItemStack.EMPTY);
                Showcase.camera(server, floorTop.add(3.5, 2.4, 0.4), floorTop); // looking west: the front faces east
            }
            case 110 -> Showcase.shot(mc, "placement_assembler_west");
            case 112 -> Showcase.server(server, s -> BastionBlocks.PART_ASSEMBLER.get().placeAt(s.overworld(), ORIGIN, Direction.EAST));
            case 114 -> hold(server, ItemStack.EMPTY, ItemStack.EMPTY); // the real station alone, same camera
            case 135 -> Showcase.shot(mc, "placement_assembler_placed");
            case 137 -> {
                Showcase.server(server, PlacementShowcase::clear);
                hold(server, new ItemStack(BastionItems.TURRET_BASE.get()), new ItemStack(BastionItems.PART_WORKSTATION.get()));
            }
            case 160 -> Showcase.shot(mc, "placement_mainhand_wins");
            case 162 -> {
                Showcase.server(server, s -> s.overworld().setDayTime(18000));
                hold(server, new ItemStack(BastionItems.PART_WORKSTATION.get()), ItemStack.EMPTY);
            }
            case 200 -> Showcase.shot(mc, "placement_night");
            case 205 -> mc.stop();
            default -> {
            }
        }
    }

    private static void hold(MinecraftServer server, ItemStack main, ItemStack off) {
        Showcase.server(server, s -> {
            ServerPlayer player = s.getPlayerList().getPlayers().get(0);
            player.getInventory().selected = 0;
            player.setItemInHand(InteractionHand.MAIN_HAND, main);
            player.setItemInHand(InteractionHand.OFF_HAND, off);
        });
    }

    private static void build(MinecraftServer server) {
        ServerLevel level = server.overworld();
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        level.setDayTime(6000);
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) level.setBlock(ORIGIN.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
        }
        clear(server);
    }

    private static void clear(MinecraftServer server) {
        ServerLevel level = server.overworld();
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                for (int y = 0; y < 6; y++) level.setBlock(ORIGIN.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }
}
```

- [ ] **Step 2: Run** `caffeinate -dimsu ./gradlew runClient --offline -PnoDevMods -Pshowcase=placement -PshowcaseWorld=showcase_fx -q` and open every `run/screenshots/showcase_placement_*.png`.
  Expected: cyan ghost + cyan boxes on free cells; the stone cell red and the ghost red; the west-facing assembler ghost lines up exactly with the placed station (if turned 180° or 90° off, fix `turn()`); no hologram in `placement_mainhand_wins`; T3 armour/fins visible on the large base ghost; readable at night.
- [ ] **Step 3: Tune** alpha/colour constants (`GhostRenderer.ALPHA`, `PlacementPreview.FILL/EDGE`) only if the shots are unreadable; re-run the scene after a change.

---

### Task 5: Docs

**Files:** `docs/TESTING.md`, `docs/release/UPLOAD.md` (0.1.2 "New"), `docs/release/DESCRIPTION.md` + `docs/release/DESCRIPTION.curseforge.html` (one sentence in the Large Turret Base / workstation paragraph), `docs/HANDOFF.md` (NOW item, decision row, scene list `placement`).

- [ ] **Step 1: TESTING** — new section "Placement hologram (0.1.2)": hold each of the 5 items and aim at the floor (ghost + boxes, front toward you for stations); put a block in one cell (that box and the ghost turn red, the click does nothing); aim a Large Base at a wall (nothing); main hand another block + off hand a station (nothing); stand inside the footprint (click fails, clicked cell not red: vanilla rule); T3 base from creative shows armour + fins in the ghost.
- [ ] **Step 2: UPLOAD changelog** under 0.1.2 "New": `- Placement hologram: holding a Large Turret Base or a multi-block workstation shows a see-through model and a box per block where it will go, red where something is in the way.`
- [ ] **Step 3: DESCRIPTION (.md + .html)**: after the Large Turret Base sentence, add `While you hold one, a hologram shows exactly where it will go.`
- [ ] **Step 4: HANDOFF**: NOW item with files, test names, scene, test count.
- [ ] **Step 5: Final check** `./gradlew build --offline -q && ./gradlew runGameTestServer --offline -q` → `All 102 required tests passed`; write the in-game checklist for the user. No commit until the user says "commit".

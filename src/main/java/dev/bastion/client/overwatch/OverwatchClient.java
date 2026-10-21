package dev.bastion.client.overwatch;

import com.mojang.blaze3d.platform.InputConstants;
import dev.bastion.Bastion;
import dev.bastion.menu.ControlHubMenu;
import dev.bastion.network.BastionNetwork;
import dev.bastion.network.OverwatchInput;
import dev.bastion.network.OverwatchRequest;
import dev.bastion.registry.BastionSounds;
import dev.bastion.registry.BastionWeaponTypes;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretState;
import dev.bastion.weapon.ManualAim;
import dev.bastion.weapon.StatSheet;
import dev.bastion.weapon.WeaponData;
import dev.bastion.weapon.WeaponType;
import net.minecraft.client.Camera;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.Input;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.Set;

/**
 * The client's side of an Overwatch session (PLAN "Overwatch Link"): which turret the camera is at (CameraMixin asks
 * {@link #view}), what the crosshair is on, the keys (F5 view, zoom, switch, Shift to leave) and the input it blocks while
 * the body stands at the hub. The server decides everything else and is sent the crosshair and trigger every tick.
 */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID, value = Dist.CLIENT)
public final class OverwatchClient {
    public static final KeyMapping NEXT = new KeyMapping("key.bastion.overwatch.next", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT, "key.categories.bastion");
    public static final KeyMapping PREVIOUS = new KeyMapping("key.bastion.overwatch.previous", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT, "key.categories.bastion");
    /**
     * Third person: a "sky view" (user 2026-10-21), raised this far over the pivot along the mount (a ceiling turret's view
     * hangs below it) and this far back; a large base's weapons are bigger. First person: a sight over the barrel, at most
     * SIGHT_AHEAD out (the Railgun's 4.7-block barrel put it in the ground), so the shots fly out from under the crosshair
     * (user 2026-10-20: from the barrel's line the Gun's tracers could not be seen). Never inside a block (CLEARANCE).
     */
    private static final double ABOVE = 3, ABOVE_LARGE = 4, BEHIND = 4, BEHIND_LARGE = 6, SIGHT_UP = 0.45, SIGHT_AHEAD = 1.2, CLEARANCE = 0.3;
    private static final int MAX_ZOOM = 4;
    /** Vanilla HUD parts the CCTV view replaces (OverwatchHud): no hotbar, hearts, hand-held crosshair or helmet overlays. */
    private static final Set<ResourceLocation> HIDDEN = Set.of(VanillaGuiOverlay.HOTBAR.id(), VanillaGuiOverlay.CROSSHAIR.id(),
            VanillaGuiOverlay.PLAYER_HEALTH.id(), VanillaGuiOverlay.ARMOR_LEVEL.id(), VanillaGuiOverlay.FOOD_LEVEL.id(),
            VanillaGuiOverlay.AIR_LEVEL.id(), VanillaGuiOverlay.MOUNT_HEALTH.id(), VanillaGuiOverlay.JUMP_BAR.id(),
            VanillaGuiOverlay.EXPERIENCE_BAR.id(), VanillaGuiOverlay.ITEM_NAME.id(), VanillaGuiOverlay.HELMET.id(),
            VanillaGuiOverlay.SPYGLASS.id(), VanillaGuiOverlay.VIGNETTE.id());

    @Nullable
    private static BlockPos turret, hub;
    @Nullable
    private static ControlHubMenu.Entry status;
    private static boolean firstPerson;
    private static int zoom = 1;
    // The crosshair as of the last frame, sent every tick.
    @Nullable
    private static Vec3 aimPoint;
    private static int aimEntity = -1;
    private static boolean canFire, watchOnly;
    /** The server's range multiplier under control (OverwatchView). */
    private static float rangeScale = 1;
    /** Where the shot lands along the barrel (null: none to show), and the view's field of view, for the HUD's marker. */
    @Nullable
    private static Vec3 barrelImpact;
    private static double fov = 70;

    /** Where the camera goes this frame: the anchor, the look direction and how far back (0 = first person). */
    public record View(Vec3 anchor, float yRot, float xRot, double distance) {
    }

    private OverwatchClient() {
    }

    public static void begin(BlockPos turretPos, BlockPos hubPos, float range) {
        TurretBaseBlockEntity previous = turret(Minecraft.getInstance().level); // switching (←/→): its barrel follows the server again
        if (previous != null) previous.clearPredictedAim();
        turret = turretPos;
        hub = hubPos;
        rangeScale = range;
        status = null;
        firstPerson = false;
        zoom = 1;
        aimPoint = null;
        aimEntity = -1;
        canFire = watchOnly = false;
        while (Minecraft.getInstance().options.keyShift.consumeClick()) ; // a Shift press from before (shift-click on the map) must not leave at once
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(BastionSounds.CONTROL_HUB_UI_SELECT.get(), 1f));
    }

    public static void end() {
        if (turret == null) return;
        TurretBaseBlockEntity base = turret(Minecraft.getInstance().level);
        if (base != null) base.clearPredictedAim();
        turret = hub = null;
        status = null;
        aimPoint = null;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(BastionSounds.CONTROL_HUB_UI_SELECT.get(), 0.8f));
    }

    public static void status(ControlHubMenu.Entry entry) {
        status = entry;
    }

    public static boolean active() {
        return turret != null;
    }

    @Nullable
    public static BlockPos turretPos() {
        return turret;
    }

    @Nullable
    public static BlockPos hubPos() {
        return hub;
    }

    @Nullable
    public static ControlHubMenu.Entry status() {
        return status;
    }

    /** The controlled turret in the client's world, or null when its chunk is not here (or it is gone). */
    @Nullable
    public static TurretBaseBlockEntity turret(@Nullable Level level) {
        return turret != null && level != null && level.isLoaded(turret) && level.getBlockEntity(turret) instanceof TurretBaseBlockEntity base ? base : null;
    }

    /** Camera.setup (CameraMixin): where the camera goes this frame, or null outside Overwatch. Mouse look turns the player, so the camera takes their view. */
    @Nullable
    public static View view(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        TurretBaseBlockEntity base = turret(mc.level);
        if (base == null || mc.player == null) return null;
        WeaponData data = base.inventory().weaponData();
        Vec3 pivot = data == null ? base.alongMount(1.5) : base.pivot(data);
        float yRot = mc.player.getViewYRot(partialTick), xRot = mc.player.getViewXRot(partialTick);
        if (firstPerson) {
            Vec3 ahead = Vec3.directionFromRotation(xRot, yRot), up = Vec3.directionFromRotation(xRot - 90, yRot);
            double reach = Math.min(SIGHT_AHEAD, data == null ? 0 : data.aim().muzzleLength());
            Vec3 sight = clear(mc, pivot, pivot.add(up.scale(SIGHT_UP)));
            return new View(clear(mc, sight, sight.add(ahead.scale(reach))), yRot, xRot, 0);
        }
        Vec3 raised = clear(mc, pivot, pivot.add(base.toWorld(new Vec3(0, base.large() ? ABOVE_LARGE : ABOVE, 0))));
        return new View(raised, yRot, xRot, base.large() ? BEHIND_LARGE : BEHIND);
    }

    /** {@code from} toward {@code to}, stopping CLEARANCE short of the first block in between: the camera never sits inside one. */
    private static Vec3 clear(Minecraft mc, Vec3 from, Vec3 to) {
        BlockHitResult hit = mc.level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() == HitResult.Type.MISS) return to;
        Vec3 way = to.subtract(from);
        double length = way.length();
        return length < 1e-6 ? from : from.add(way.scale(Math.max(0, hit.getLocation().distanceTo(from) - CLEARANCE) / length));
    }

    /**
     * After the camera is placed: what the crosshair is on (blocks, then the first creature before them, out to the weapon's
     * range past the turret) and where the barrel points for it, drawn this frame.
     */
    public static void aimFrom(Camera camera) {
        Minecraft mc = Minecraft.getInstance();
        TurretBaseBlockEntity base = turret(mc.level);
        WeaponData data = base == null ? null : base.inventory().weaponData();
        WeaponType type = data == null ? null : BastionWeaponTypes.REGISTRY.get().getValue(data.type());
        if (base == null || type == null || mc.player == null) {
            aimPoint = barrelImpact = null;
            canFire = watchOnly = false;
            if (base != null) base.clearPredictedAim();
            return;
        }
        StatSheet own = StatSheet.of(data, base.tier(), base.inventory().modifierEffect()), stats = own.withRange(own.range() * rangeScale);
        Vec3 from = camera.getPosition(), look = new Vec3(camera.getLookVector());
        ManualAim.Pick pick = ManualAim.pick(mc.player, from, from.add(look.scale(from.distanceTo(base.pivot(data)) + stats.range())), type.ballistic());
        aimPoint = pick.point();
        aimEntity = pick.entity() == null ? -1 : pick.entity().getId();
        watchOnly = data.aim().turnSpeed() <= 0;
        ManualAim.Aim aim = watchOnly ? null : ManualAim.solve(base, type, stats, aimPoint);
        TurretState state = base.clientState(); // red too while it cannot shoot at all (review 2026-10-20)
        canFire = aim != null && (aim.onTarget() || data.aim().fixedPitch()) // the Missile Launcher never tilts: its missiles home in
                && state != TurretState.NO_AMMO && state != TurretState.OVERHEAT && state != TurretState.DISABLED;
        if (aim != null) base.setPredictedAim(aim.yaw(), aim.pitch());
        else base.clearPredictedAim();
        // From the raised view the camera sees over what the barrel cannot, so the HUD also marks where the shot really
        // lands, along the barrel (user 2026-10-21: aim and shots looked out of sync). Not for arcs (the arc shows it) or
        // the Missile Launcher's fixed tubes (its missiles home in).
        barrelImpact = null;
        if (aim != null && !type.ballistic() && !data.aim().fixedPitch()) {
            Vec3 barrel = base.toWorld(Vec3.directionFromRotation(aim.pitch(), aim.yaw()));
            Vec3 muzzle = base.pivot(data).add(barrel.scale(data.aim().muzzleLength()));
            barrelImpact = ManualAim.pick(mc.player, muzzle, muzzle.add(barrel.scale(stats.range())), false).point();
        }
    }

    @Nullable
    public static Vec3 barrelImpact() {
        return barrelImpact;
    }

    public static double fov() {
        return fov;
    }

    @Nullable
    public static Vec3 aimPoint() {
        return aimPoint;
    }

    public static boolean canFire() {
        return canFire;
    }

    public static boolean watchOnly() {
        return watchOnly;
    }

    public static int zoom() {
        return zoom;
    }

    /** First person looks out from inside the weapon: its model is not drawn (WeaponMountLayer). */
    public static boolean hidesWeapon(BlockPos pos) {
        return firstPerson && pos.equals(turret);
    }

    /** Back to the body: tells the server and drops the view at once. */
    private static void leave() {
        BastionNetwork.CHANNEL.sendToServer(OverwatchRequest.exit());
        end();
    }

    /** Before vanilla reads its keys this tick: Overwatch takes F5, the switch keys and Shift, swallows inventory/hotbar/drop, sends input. */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START || turret == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            end();
            return;
        }
        if (turret(mc.level) == null) { // its chunk left this client, or it is gone (PLAN: no chunks are sent for the camera)
            leave();
            return;
        }
        Options o = mc.options;
        while (o.keyTogglePerspective.consumeClick()) firstPerson = !firstPerson;
        while (o.keyInventory.consumeClick()) ;
        while (o.keyDrop.consumeClick()) ;
        while (o.keySwapOffhand.consumeClick()) ;
        for (KeyMapping key : o.keyHotbarSlots) while (key.consumeClick()) ;
        while (NEXT.consumeClick()) BastionNetwork.CHANNEL.sendToServer(OverwatchRequest.cycle(1));
        while (PREVIOUS.consumeClick()) BastionNetwork.CHANNEL.sendToServer(OverwatchRequest.cycle(-1));
        // A press, not the held state: with Sneak set to Toggle, isDown() stays latched (review 2026-10-20).
        if (o.keyShift.consumeClick() && mc.screen == null) {
            while (o.keyShift.consumeClick()) ;
            // Toggle mode: that press latched sneaking on; setDown(true) flips it back, so the body is not left crouching.
            if (o.toggleCrouch().get() && o.keyShift.isDown()) o.keyShift.setDown(true);
            leave();
            return;
        }
        if (aimPoint != null) {
            BastionNetwork.CHANNEL.sendToServer(new OverwatchInput(turret, aimPoint, aimEntity, mc.screen == null && o.keyAttack.isDown()));
        }
    }

    /** The body at the hub stands still. */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (turret == null) return;
        Input input = event.getInput();
        input.forwardImpulse = input.leftImpulse = 0;
        input.up = input.down = input.left = input.right = input.jumping = input.shiftKeyDown = false;
    }

    /** No attacking, using or picking blocks at the hub with the view away. */
    @SubscribeEvent
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (turret == null) return;
        event.setCanceled(true);
        event.setSwingHand(false);
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        if (turret == null || Minecraft.getInstance().screen != null || event.getScrollDelta() == 0) return;
        zoom = Mth.clamp(zoom + (event.getScrollDelta() > 0 ? 1 : -1), 1, MAX_ZOOM); // ponytail: one step per event, a trackpad swipe zooms fast
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        if (turret == null) return;
        event.setFOV(event.getFOV() / zoom);
        if (event.usedConfiguredFov()) fov = event.getFOV(); // the world's, for the HUD to place its marker
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (turret != null) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        if (turret != null && HIDDEN.contains(event.getOverlay().id())) event.setCanceled(true);
    }

    /** The block outline would mark whatever the body at the hub looks at. */
    @SubscribeEvent
    public static void onHighlight(RenderHighlightEvent.Block event) {
        if (turret != null) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        turret = hub = null;
        status = null;
        aimPoint = null;
    }
}

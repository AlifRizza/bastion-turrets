package dev.bastion.gametest;

import com.mojang.authlib.GameProfile;
import dev.bastion.Bastion;
import dev.bastion.compat.BlockInfo;
import dev.bastion.hub.HubConnectorItem;
import dev.bastion.hub.HubInterfaceBlockEntity;
import dev.bastion.hub.OverwatchLinkItem;
import dev.bastion.hub.OverwatchSessions;
import dev.bastion.menu.ControlHubMenu;
import dev.bastion.projectile.TurretRocketEntity;
import dev.bastion.registry.BastionBlocks;
import dev.bastion.registry.BastionEntities;
import dev.bastion.registry.BastionItems;
import dev.bastion.registry.BastionWeaponTypes;
import dev.bastion.turret.LargeTurretBaseBlock;
import dev.bastion.turret.ManualControl;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretInventory;
import dev.bastion.turret.TurretState;
import dev.bastion.turret.targeting.TargetSelector;
import dev.bastion.weapon.Ballistics;
import dev.bastion.weapon.Bullets;
import dev.bastion.weapon.FireContext;
import dev.bastion.weapon.ManualAim;
import dev.bastion.weapon.StatSheet;
import dev.bastion.weapon.WeaponData;
import dev.bastion.weapon.WeaponState;
import dev.bastion.weapon.WeaponType;
import dev.bastion.weapon.types.MissileLauncherWeapon;
import dev.bastion.workstation.WorkstationRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/** Overwatch Link (PLAN "Overwatch Link"): the item, manual control of a turret, the hub sessions. */
@GameTestHolder(Bastion.MOD_ID)
@PrefixGameTestTemplate(false)
public class OverwatchTests {
    static final String ARENA = "arena", YARD = "yard", PIT = "pit", RANGE = "range";

    /** An Overwatch Link linked to {@code hub}, in the turret's Connector slot. */
    static void link(TurretBaseBlockEntity turret, HubInterfaceBlockEntity hub) {
        turret.inventory().setStackInSlot(TurretInventory.CONNECTOR,
                HubConnectorItem.link(new ItemStack(BastionItems.OVERWATCH_LINK.get()), hub.getLevel(), hub.getBlockPos()));
    }

    /** It links like a Hub Connector, fits the Connector slot, counts as connected, and the Module Workstation makes it. */
    @GameTest(template = ARENA, batch = "overwatchLinkIsAHubConnector")
    public static void overwatchLinkIsAHubConnector(GameTestHelper helper) {
        ItemStack stack = new ItemStack(BastionItems.OVERWATCH_LINK.get());
        helper.assertTrue(stack.getItem() instanceof HubConnectorItem, "not a Hub Connector");
        BlockPos iface = helper.absolutePos(new BlockPos(1, 1, 1));
        HubConnectorItem.link(stack, helper.getLevel(), iface);
        helper.assertTrue(HubConnectorItem.linkedTo(stack, helper.getLevel(), iface), "it does not link");
        helper.setBlock(new BlockPos(3, 2, 3), BastionBlocks.TURRET_BASE.get());
        TurretBaseBlockEntity turret = (TurretBaseBlockEntity) helper.getBlockEntity(new BlockPos(3, 2, 3));
        helper.assertTrue(turret.inventory().isItemValid(TurretInventory.CONNECTOR, stack), "the Connector slot refuses it");
        helper.assertTrue(!OverwatchLinkItem.fitted(turret), "fitted with an empty slot");
        turret.inventory().setStackInSlot(TurretInventory.CONNECTOR, stack);
        helper.assertTrue(OverwatchLinkItem.fitted(turret), "not fitted");
        turret.inventory().setStackInSlot(TurretInventory.CONNECTOR, new ItemStack(BastionItems.HUB_CONNECTOR.get()));
        helper.assertTrue(!OverwatchLinkItem.fitted(turret), "a plain Hub Connector counts as a Link");
        var recipe = helper.getLevel().getRecipeManager().byKey(Bastion.id("workstation/module_workstation/overwatch_link"));
        helper.assertTrue(recipe.isPresent() && recipe.get() instanceof WorkstationRecipe w
                && w.result().is(BastionItems.OVERWATCH_LINK.get()), "no Module Workstation recipe");
        helper.succeed();
    }

    // --- arcs and manual aim (Ballistics, ManualAim) ------------------------------------------------

    private static final BlockPos LARGE = new BlockPos(2, 2, 5);

    static TurretBaseBlockEntity largeWith(GameTestHelper helper, Item weapon) {
        LargeTurretBaseBlock.placeAt(helper.getLevel(), helper.absolutePos(LARGE), BastionBlocks.LARGE_TURRET_BASE.get());
        TurretBaseBlockEntity turret = (TurretBaseBlockEntity) helper.getBlockEntity(LARGE);
        turret.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(weapon));
        return turret;
    }

    static StatSheet stats(TurretBaseBlockEntity turret) {
        return StatSheet.of(turret.inventory().weaponData(), turret.tier(), turret.inventory().modifierEffect());
    }

    static WeaponType type(TurretBaseBlockEntity turret) {
        return BastionWeaponTypes.REGISTRY.get().getValue(turret.inventory().weaponData().type());
    }

    /** A small Gun base standing in the PIT, away from the large base. */
    private static TurretBaseBlockEntity pitGun(GameTestHelper helper) {
        helper.setBlock(new BlockPos(20, 2, 9), BastionBlocks.TURRET_BASE.get());
        TurretBaseBlockEntity gun = (TurretBaseBlockEntity) helper.getBlockEntity(new BlockPos(20, 2, 9));
        gun.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(BastionItems.GUN_TURRET.get()));
        return gun;
    }

    /** The launch Ballistics solves comes down on the point it was solved for; a spot past the shell's reach has none. */
    @GameTest(template = PIT, batch = "ballisticLaunchLandsOnTheAimPoint")
    public static void ballisticLaunchLandsOnTheAimPoint(GameTestHelper helper) {
        TurretBaseBlockEntity mortar = largeWith(helper, BastionItems.MORTAR_TURRET.get());
        WeaponData data = mortar.inventory().weaponData();
        Vec3 spot = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(15, 2, 6)));
        Ballistics.Launch launch = Ballistics.solve(mortar, data, spot);
        helper.assertTrue(launch != null, "no launch to a spot in range");
        Vec3 landing = launch.at(launch.ticks(), data.param("gravity"));
        helper.assertTrue(landing.distanceTo(spot) < 0.05, "lands at " + landing + ", aimed at " + spot);
        helper.assertTrue(Ballistics.solve(mortar, data, spot.add(80, 0, 0)) == null, "a launch past the shell's reach");
        TurretBaseBlockEntity gun = pitGun(helper);
        helper.assertTrue(type(mortar).ballistic() && !type(gun).ballistic(), "ballistic flags");
        mortar.inventory().setStackInSlot(TurretInventory.WEAPON, ItemStack.EMPTY);
        gun.inventory().setStackInSlot(TurretInventory.WEAPON, ItemStack.EMPTY);
        helper.succeed();
    }

    /**
     * A straight-firing weapon points its barrel right at the spot, and is held at its pitch limit (off target) for a spot
     * straight down (Gun: min_pitch -35). A Mortar points along its arc, and has no aim inside min_range or past its range.
     */
    @GameTest(template = PIT, batch = "manualAimFollowsTheWeapon")
    public static void manualAimFollowsTheWeapon(GameTestHelper helper) {
        TurretBaseBlockEntity gun = pitGun(helper);
        WeaponData gunData = gun.inventory().weaponData();
        Vec3 pivot = gun.pivot(gunData), spot = pivot.add(-6, -1, -3);
        ManualAim.Aim aim = ManualAim.solve(gun, type(gun), stats(gun), spot);
        Vec3 dir = spot.subtract(pivot);
        helper.assertTrue(aim != null && aim.onTarget() && Math.abs(aim.yaw() - TargetSelector.yaw(dir)) < 1e-3
                && Math.abs(aim.pitch() + TargetSelector.elevation(dir)) < 1e-3, "gun aim " + aim);
        ManualAim.Aim down = ManualAim.solve(gun, type(gun), stats(gun), pivot.add(0, -10, 0.01));
        helper.assertTrue(down != null && !down.onTarget() && Math.abs(down.pitch() + gunData.aim().minPitch()) < 1e-3, "straight down " + down);
        TurretBaseBlockEntity mortar = largeWith(helper, BastionItems.MORTAR_TURRET.get());
        Vec3 mortarPivot = mortar.pivot(mortar.inventory().weaponData());
        helper.assertTrue(ManualAim.solve(mortar, type(mortar), stats(mortar), mortarPivot.add(3, -2, 0)) == null, "aims inside min_range");
        helper.assertTrue(ManualAim.solve(mortar, type(mortar), stats(mortar), mortarPivot.add(60, -2, 0)) == null, "aims past its range");
        ManualAim.Aim lob = ManualAim.solve(mortar, type(mortar), stats(mortar), Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(15, 2, 6))));
        helper.assertTrue(lob != null && lob.onTarget() && -lob.pitch() >= mortar.inventory().weaponData().aim().minPitch(), "mortar aim " + lob);
        gun.inventory().setStackInSlot(TurretInventory.WEAPON, ItemStack.EMPTY);
        mortar.inventory().setStackInSlot(TurretInventory.WEAPON, ItemStack.EMPTY);
        helper.succeed();
    }

    // --- manual control on the turret ----------------------------------------------------------------

    private static final BlockPos BASE = new BlockPos(2, 2, 3), FAR = new BlockPos(9, 2, 3);

    private static TurretBaseBlockEntity small(GameTestHelper helper, net.minecraft.world.item.Item weapon, net.minecraft.world.item.Item ammo, int count) {
        helper.setBlock(BASE, BastionBlocks.TURRET_BASE.get());
        TurretBaseBlockEntity turret = (TurretBaseBlockEntity) helper.getBlockEntity(BASE);
        turret.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(weapon));
        if (count > 0) turret.inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(ammo, count));
        return turret;
    }

    private static int ammo(TurretBaseBlockEntity turret) {
        return turret.inventory().getStackInSlot(TurretInventory.AMMO_START).getCount();
    }

    private static void release(TurretBaseBlockEntity turret) {
        turret.setManual(null);
        turret.inventory().setStackInSlot(TurretInventory.WEAPON, ItemStack.EMPTY);
    }

    private static boolean hurt(LivingEntity entity) {
        return entity.getHealth() < entity.getMaxHealth();
    }

    /** A cow (which the turret's filter never shoots) under the crosshair takes the controller's shots. */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "manualFireHitsWhatTheCrosshairIsOn")
    public static void manualFireHitsWhatTheCrosshairIsOn(GameTestHelper helper) {
        TurretBaseBlockEntity turret = small(helper, BastionItems.GUN_TURRET.get(), BastionItems.KINETIC_ROUNDS.get(), 10);
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, FAR);
        ManualControl control = new ManualControl(UUID.randomUUID(), "Tester");
        turret.setManual(control);
        helper.onEachTick(() -> control.input(cow.getBoundingBox().getCenter(), cow.getId(), true, helper.getLevel().getGameTime()));
        helper.succeedWhen(() -> {
            helper.assertTrue(hurt(cow), "the cow was not hit, state " + turret.state());
            helper.assertTrue(ammo(turret) < 10, "no ammo spent");
            release(turret);
        });
    }

    /**
     * Steered, it hurts anything but the owner and the controller, players only with PvP on; unsteered it follows its filter.
     * Swapping to a weapon that never turns (Tesla) stops the steering.
     */
    @GameTest(template = ARENA, batch = "manualShotsSpareOwnerAndController")
    public static void manualShotsSpareOwnerAndController(GameTestHelper helper) {
        TurretBaseBlockEntity turret = small(helper, BastionItems.GUN_TURRET.get(), BastionItems.KINETIC_ROUNDS.get(), 0);
        var owner = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "owner"));
        var controller = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "controller"));
        var other = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "other"));
        turret.setOwner(owner.getUUID());
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, FAR);
        helper.assertTrue(!turret.steering() && !turret.wouldHit(cow), "the filter shoots cows");
        turret.setManual(new ManualControl(controller.getUUID(), "controller"));
        helper.assertTrue(turret.steering() && turret.wouldHit(cow), "steered, it spares the cow");
        helper.assertTrue(!turret.wouldHit(owner) && !turret.wouldHit(controller), "it hits the owner or the controller");
        var server = helper.getLevel().getServer();
        boolean pvp = server.isPvpAllowed();
        var creative = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "creative"));
        creative.setGameMode(GameType.CREATIVE);
        server.setPvpAllowed(true);
        boolean withPvp = turret.wouldHit(other), creativeHit = turret.wouldHit(creative);
        server.setPvpAllowed(false);
        boolean withoutPvp = turret.wouldHit(other);
        server.setPvpAllowed(pvp);
        helper.assertTrue(withPvp && !withoutPvp, "PvP on " + withPvp + ", off " + withoutPvp);
        helper.assertTrue(!creativeHit, "a creative player is hit with PvP on");
        turret.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(BastionItems.TESLA_TURRET.get()));
        helper.assertTrue(!turret.steering() && !turret.wouldHit(cow), "a Tesla is steered");
        release(turret);
        helper.succeed();
    }

    /**
     * The barrel's first move lands right on the aim (a servo would only start turning), and the next one stops at its
     * pitch limit for a spot straight down. Checked on the moves, not at fixed ticks: when a fresh base first ticks varies.
     */
    @GameTest(template = ARENA, batch = "barrelSnapsToTheAim")
    public static void barrelSnapsToTheAim(GameTestHelper helper) {
        TurretBaseBlockEntity turret = small(helper, BastionItems.GUN_TURRET.get(), BastionItems.KINETIC_ROUNDS.get(), 0);
        ManualControl control = new ManualControl(UUID.randomUUID(), "Tester");
        turret.setManual(control);
        WeaponData data = turret.inventory().weaponData();
        Vec3 pivot = turret.pivot(data), side = pivot.add(1, -0.5, 4), down = pivot.add(0, -10, 0.01);
        ManualAim.Aim aim = ManualAim.solve(turret, type(turret), stats(turret), side);
        control.input(side, -1, false, helper.getLevel().getGameTime());
        boolean[] turned = {false};
        helper.onEachTick(() -> {
            if (!turned[0] && (turret.yaw() != 0 || turret.pitch() != 0)) {
                helper.assertTrue(Math.abs(Mth.wrapDegrees(turret.yaw() - aim.yaw())) < 1e-3 && Math.abs(turret.pitch() - aim.pitch()) < 1e-3,
                        "first move to " + turret.yaw() + "/" + turret.pitch() + ", aim " + aim);
                turned[0] = true;
                control.input(down, -1, false, helper.getLevel().getGameTime());
            } else if (turned[0] && Math.abs(turret.pitch() - aim.pitch()) > 1e-3) {
                helper.assertTrue(Math.abs(turret.pitch() + data.aim().minPitch()) < 1e-3, "pitch " + turret.pitch() + " not at its limit");
                release(turret);
                helper.succeed();
            }
        });
    }

    /** A Sniper charge stops when the trigger is let go: 10 + 25 held ticks never add up to a shot. */
    @GameTest(template = ARENA, timeoutTicks = 120, batch = "lettingGoCancelsTheCharge")
    public static void lettingGoCancelsTheCharge(GameTestHelper helper) {
        TurretBaseBlockEntity turret = small(helper, BastionItems.SNIPER_TURRET.get(), BastionItems.SNIPER_ROUNDS.get(), 5);
        ManualControl control = new ManualControl(UUID.randomUUID(), "Tester");
        turret.setManual(control);
        Vec3 point = Vec3.atCenterOf(helper.absolutePos(FAR));
        helper.onEachTick(() -> {
            long t = helper.getTick();
            control.input(point, -1, t < 10 || t >= 15, helper.getLevel().getGameTime());
        });
        helper.runAtTickTime(8, () -> helper.assertTrue(turret.state() == TurretState.CHARGING, "not charging: " + turret.state()));
        helper.runAtTickTime(13, () -> helper.assertTrue(turret.state() == TurretState.IDLE, "let go but " + turret.state()));
        helper.runAtTickTime(40, () -> helper.assertTrue(ammo(turret) == 5, "fired at tick 40: the charge carried over"));
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getTick() > 40, "waiting for tick 40");
            helper.assertTrue(ammo(turret) < 5, "never fired after a full charge, state " + turret.state());
            release(turret);
        });
    }

    /** One trigger input, then silence: the turret fires, then stops once the input is INPUT_TIMEOUT ticks old. */
    @GameTest(template = ARENA, timeoutTicks = 80, batch = "triggerLetsGoWithoutInput")
    public static void triggerLetsGoWithoutInput(GameTestHelper helper) {
        TurretBaseBlockEntity turret = small(helper, BastionItems.GUN_TURRET.get(), BastionItems.KINETIC_ROUNDS.get(), 20);
        ManualControl control = new ManualControl(UUID.randomUUID(), "Tester");
        turret.setManual(control);
        control.input(Vec3.atCenterOf(helper.absolutePos(FAR)), -1, true, helper.getLevel().getGameTime());
        int[] at30 = new int[1];
        helper.runAtTickTime(30, () -> at30[0] = ammo(turret));
        helper.runAtTickTime(50, () -> {
            helper.assertTrue(at30[0] < 20, "never fired");
            helper.assertTrue(ammo(turret) == at30[0] && turret.state() == TurretState.IDLE,
                    "still firing without input: " + ammo(turret) + " left, " + turret.state());
            release(turret);
            helper.succeed();
        });
    }

    /** A controlled Mortar shells the spot under the crosshair: the fire patch lands there. */
    @GameTest(template = PIT, timeoutTicks = 300, batch = "manualMortarShellsTheSpot")
    public static void manualMortarShellsTheSpot(GameTestHelper helper) {
        TurretBaseBlockEntity mortar = largeWith(helper, BastionItems.MORTAR_TURRET.get());
        mortar.inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(BastionItems.MORTAR_SHELLS.get(), 4));
        for (int x = 13; x <= 17; x++) for (int z = 4; z <= 8; z++) helper.setBlock(new BlockPos(x, 1, z), Blocks.OAK_PLANKS);
        ManualControl control = new ManualControl(UUID.randomUUID(), "Tester");
        mortar.setManual(control);
        Vec3 spot = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(15, 2, 6)));
        helper.onEachTick(() -> control.input(spot, -1, true, helper.getLevel().getGameTime()));
        helper.succeedWhen(() -> {
            boolean fire = false;
            for (int x = 14; x <= 16; x++) for (int z = 5; z <= 7; z++) {
                fire |= helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(x, 2, z))).getBlock() instanceof BaseFireBlock;
            }
            helper.assertTrue(fire, "no fire at the spot yet, state " + mortar.state());
            release(mortar);
        });
    }

    /** Inside min_range the controlled Mortar never fires, and stays IDLE with the trigger held. */
    @GameTest(template = PIT, timeoutTicks = 100, batch = "manualMortarRefusesTooClose")
    public static void manualMortarRefusesTooClose(GameTestHelper helper) {
        TurretBaseBlockEntity mortar = largeWith(helper, BastionItems.MORTAR_TURRET.get());
        mortar.inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(BastionItems.MORTAR_SHELLS.get(), 4));
        ManualControl control = new ManualControl(UUID.randomUUID(), "Tester");
        mortar.setManual(control);
        Vec3 close = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(6, 2, 6)));
        helper.onEachTick(() -> control.input(close, -1, true, helper.getLevel().getGameTime()));
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(mortar.inventory().getStackInSlot(TurretInventory.AMMO_START).getCount() == 4, "it fired inside min_range");
            helper.assertTrue(mortar.state() == TurretState.IDLE, "expected IDLE, got " + mortar.state());
            release(mortar);
            helper.succeed();
        });
    }

    /** The Missile Launcher locks the creature under the crosshair: the cow is hit and the husk (its filter's pick) is not. */
    @GameTest(template = RANGE, timeoutTicks = 300, batch = "missileFollowsTheCrosshairLock")
    public static void missileFollowsTheCrosshairLock(GameTestHelper helper) {
        TurretBaseBlockEntity launcher = largeWith(helper, BastionItems.MISSILE_LAUNCHER_TURRET.get());
        launcher.inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(BastionItems.CREATIVE_AMMO.get()));
        launcher.weaponState().tubes = MissileLauncherWeapon.ALL_TUBES;
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(19, 2, 3));
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(19, 2, 10));
        ManualControl control = new ManualControl(UUID.randomUUID(), "Tester");
        launcher.setManual(control);
        crosshairOn(helper, control, cow.getBoundingBox().getCenter(), cow.getId());
        clickOn(helper, control, 2, cow, 1);
        helper.succeedWhen(() -> {
            helper.assertTrue(hurt(cow), "the locked cow was not hit, state " + launcher.state());
            helper.assertTrue(!hurt(husk), "the husk was hit");
            release(launcher);
        });
    }

    /** A Tesla never turns: under control it still picks its own targets (the husk) and keeps to its filter (not the cow). */
    @GameTest(template = RANGE, timeoutTicks = 200, batch = "teslaStaysAutomaticUnderControl")
    public static void teslaStaysAutomaticUnderControl(GameTestHelper helper) {
        TurretBaseBlockEntity tesla = largeWith(helper, BastionItems.TESLA_TURRET.get());
        var energy = helper.getBlockEntity(LARGE).getCapability(ForgeCapabilities.ENERGY, Direction.UP).orElseThrow(IllegalStateException::new);
        while (energy.receiveEnergy(Integer.MAX_VALUE, false) > 0) ;
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(9, 2, 5));
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(9, 2, 8));
        tesla.setManual(new ManualControl(UUID.randomUUID(), "Tester"));
        helper.assertTrue(!tesla.steering(), "a Tesla is steered");
        helper.succeedWhen(() -> {
            helper.assertTrue(hurt(husk), "the Tesla stopped firing on its own");
            helper.assertTrue(!hurt(cow), "the Tesla zapped the cow");
            release(tesla);
        });
    }

    // --- hub sessions (OverwatchSessions) -------------------------------------------------------------

    /** Yard (ControlHubTests): the hub's Interface and where its user stands. */
    private static final BlockPos IFACE = new BlockPos(15, 2, 14), STAND = new BlockPos(15, 2, 13);

    /** A corner of the satellite's chunk away from the hub cube: inside a T1 hub's reach. */
    private static BlockPos corner(HubInterfaceBlockEntity hub) {
        BlockPos center = hub.center();
        int x = (center.getX() & 15) < 8 ? (center.getX() | 15) - 3 : (center.getX() & ~15) + 1;
        int z = (center.getZ() & 15) < 8 ? (center.getZ() | 15) - 3 : (center.getZ() & ~15) + 1;
        return new BlockPos(x, center.getY(), z);
    }

    /** Runs {@code body} once, on the first tick the hub is online (a hub across a late chunk forms at its 20-tick re-check). */
    private static void whenOnline(GameTestHelper helper, HubInterfaceBlockEntity hub, Runnable body) {
        boolean[] done = {false};
        helper.onEachTick(() -> {
            if (done[0] || !hub.online()) return;
            done[0] = true;
            body.run();
        });
    }

    private static String key(Component message) {
        return message != null && message.getContents() instanceof TranslatableContents t ? t.getKey() : String.valueOf(message);
    }

    private static FakePlayer second(GameTestHelper helper) {
        FakePlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "second"));
        Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(STAND.east()));
        player.setPos(at.x, at.y, at.z);
        return player;
    }

    /** Only a turret with an Overwatch Link that the player may open, in reach, can be taken; stop gives it back. */
    @GameTest(template = YARD, timeoutTicks = 80, batch = "takingControlNeedsLinkAndReach")
    public static void takingControlNeedsLinkAndReach(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = ControlHubTests.player(helper, STAND);
        HubInterfaceBlockEntity hub = ControlHubTests.hub(helper, IFACE, 1);
        hub.setEnergy(100_000);
        BlockPos corner = corner(hub);
        TurretBaseBlockEntity plain = ControlHubTests.base(level, corner, player.getUUID());
        ControlHubTests.connect(plain, hub);
        TurretBaseBlockEntity linked = ControlHubTests.base(level, corner.east(), player.getUUID());
        link(linked, hub);
        TurretBaseBlockEntity stranger = ControlHubTests.base(level, corner.east(2), UUID.randomUUID());
        link(stranger, hub);
        BlockPos outsidePos = new BlockPos((hub.center().getX() & ~15) - 2, hub.center().getY(), hub.center().getZ());
        link(ControlHubTests.base(level, outsidePos, player.getUUID()), hub);
        whenOnline(helper, hub, () -> {
            BlockPos h = hub.getBlockPos();
            helper.assertTrue(key(OverwatchSessions.start(player, h, plain.getBlockPos())).equals("message.bastion.overwatch.no_link"), "plain connector taken");
            helper.assertTrue(key(OverwatchSessions.start(player, h, stranger.getBlockPos())).equals("message.bastion.overwatch.unreachable"), "stranger's taken");
            helper.assertTrue(key(OverwatchSessions.start(player, h, outsidePos)).equals("message.bastion.overwatch.unreachable"), "out of reach taken");
            helper.assertTrue(OverwatchSessions.start(player, h, linked.getBlockPos()) == null, "the linked turret was refused");
            helper.assertTrue(linked.manual() != null && linked.manual().player().equals(player.getUUID())
                    && linked.getBlockPos().equals(OverwatchSessions.controlled(player)), "not controlled after start");
            OverwatchSessions.stop(player);
            helper.assertTrue(linked.manual() == null && OverwatchSessions.controlled(player) == null, "stop left it controlled");
            for (BlockPos pos : List.of(corner, corner.east(), corner.east(2), outsidePos)) level.removeBlock(pos, false);
            helper.succeed();
        });
    }

    /** A turret one player controls is refused to another until they let go. */
    @GameTest(template = YARD, timeoutTicks = 80, batch = "oneControllerPerTurret")
    public static void oneControllerPerTurret(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer first = ControlHubTests.player(helper, STAND);
        FakePlayer second = second(helper);
        HubInterfaceBlockEntity hub = ControlHubTests.hub(helper, IFACE, 1);
        hub.setEnergy(100_000);
        TurretBaseBlockEntity turret = ControlHubTests.base(level, corner(hub), null);
        link(turret, hub);
        whenOnline(helper, hub, () -> {
            BlockPos h = hub.getBlockPos(), t = turret.getBlockPos();
            helper.assertTrue(OverwatchSessions.start(first, h, t) == null, "first refused");
            Component taken = OverwatchSessions.start(second, h, t);
            helper.assertTrue(key(taken).equals("message.bastion.overwatch.taken"), "second got " + key(taken));
            OverwatchSessions.stop(first);
            helper.assertTrue(OverwatchSessions.start(second, h, t) == null, "second refused after the first let go");
            OverwatchSessions.stop(second);
            level.removeBlock(t, false);
            helper.succeed();
        });
    }

    /** Damage, logout, a swapped-out Link, walking away, the turret removed and the hub offline each end the control. */
    @GameTest(template = YARD, timeoutTicks = 120, batch = "controlEndsWhenItShould")
    public static void controlEndsWhenItShould(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = ControlHubTests.player(helper, STAND);
        HubInterfaceBlockEntity hub = ControlHubTests.hub(helper, IFACE, 1);
        hub.setEnergy(100_000);
        BlockPos corner = corner(hub);
        TurretBaseBlockEntity turret = ControlHubTests.base(level, corner, player.getUUID());
        link(turret, hub);
        TurretBaseBlockEntity doomed = ControlHubTests.base(level, corner.east(), player.getUUID());
        link(doomed, hub);
        BlockPos h = hub.getBlockPos(), t = turret.getBlockPos();
        Vec3 stand = player.position();
        whenOnline(helper, hub, () -> {
            helper.assertTrue(OverwatchSessions.start(player, h, t) == null, "refused");
            MinecraftForge.EVENT_BUS.post(new LivingHurtEvent(player, level.damageSources().generic(), 1f)); // before armor/absorption: any hit counts
            helper.assertTrue(OverwatchSessions.controlled(player) == null && turret.manual() == null, "damage did not end it");
            helper.assertTrue(OverwatchSessions.start(player, h, t) == null, "refused after damage");
            MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player));
            helper.assertTrue(OverwatchSessions.controlled(player) == null, "logout did not end it");
            helper.assertTrue(OverwatchSessions.start(player, h, t) == null, "refused after logout");
            ControlHubTests.connect(turret, hub); // the Link swapped for a plain Hub Connector
            helper.runAfterDelay(2, () -> {
                helper.assertTrue(OverwatchSessions.controlled(player) == null && turret.manual() == null, "a plain connector kept it");
                link(turret, hub);
                helper.assertTrue(OverwatchSessions.start(player, h, t) == null, "refused with the Link back");
                player.setPos(stand.x + 12, stand.y, stand.z);
                helper.runAfterDelay(2, () -> {
                    helper.assertTrue(OverwatchSessions.controlled(player) == null, "walking away kept it");
                    player.setPos(stand.x, stand.y, stand.z);
                    helper.assertTrue(OverwatchSessions.start(player, h, doomed.getBlockPos()) == null, "refused the second turret");
                    level.removeBlock(doomed.getBlockPos(), false);
                    helper.runAfterDelay(2, () -> {
                        helper.assertTrue(OverwatchSessions.controlled(player) == null, "a removed turret is still controlled");
                        helper.assertTrue(OverwatchSessions.start(player, h, t) == null, "refused before the hub went off");
                        hub.setEnergy(0);
                        helper.runAfterDelay(3, () -> {
                            helper.assertTrue(!hub.online(), "the hub is still online");
                            helper.assertTrue(OverwatchSessions.controlled(player) == null && turret.manual() == null, "an offline hub kept it");
                            level.removeBlock(t, false);
                            helper.succeed();
                        });
                    });
                });
            });
        });
    }

    /** Input from anyone but the controller, for another turret, NaN or absurdly far is dropped. */
    @GameTest(template = YARD, timeoutTicks = 80, batch = "inputOnlyFromTheController")
    public static void inputOnlyFromTheController(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = ControlHubTests.player(helper, STAND);
        FakePlayer second = second(helper);
        HubInterfaceBlockEntity hub = ControlHubTests.hub(helper, IFACE, 1);
        hub.setEnergy(100_000);
        TurretBaseBlockEntity turret = ControlHubTests.base(level, corner(hub), null);
        link(turret, hub);
        whenOnline(helper, hub, () -> {
            BlockPos t = turret.getBlockPos();
            helper.assertTrue(OverwatchSessions.start(player, hub.getBlockPos(), t) == null, "refused");
            Vec3 good = Vec3.atCenterOf(t).add(5, 0, 0);
            OverwatchSessions.input(second, t, good, -1, true);
            OverwatchSessions.input(player, t.east(), good, -1, true);
            OverwatchSessions.input(player, t, new Vec3(Double.NaN, 0, 0), -1, true);
            OverwatchSessions.input(player, t, good.add(1000, 0, 0), -1, true);
            helper.assertTrue(turret.manual().point() == null, "bad input taken: " + turret.manual().point());
            OverwatchSessions.input(player, t, good, -1, true);
            helper.assertTrue(good.equals(turret.manual().point()), "good input dropped");
            OverwatchSessions.stop(player);
            level.removeBlock(t, false);
            helper.succeed();
        });
    }

    /** R flips the controlled launcher's Single/Salvo setting; a player not at its controls and a weapon without modes change nothing. */
    @GameTest(template = YARD, timeoutTicks = 80, batch = "modeKeyFlipsSalvo")
    public static void modeKeyFlipsSalvo(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = ControlHubTests.player(helper, STAND);
        FakePlayer second = second(helper);
        HubInterfaceBlockEntity hub = ControlHubTests.hub(helper, IFACE, 1);
        hub.setEnergy(100_000);
        TurretBaseBlockEntity turret = ControlHubTests.base(level, corner(hub), null);
        turret.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(BastionItems.ROCKET_LAUNCHER_TURRET.get()));
        link(turret, hub);
        whenOnline(helper, hub, () -> {
            BlockPos t = turret.getBlockPos();
            helper.assertTrue(OverwatchSessions.start(player, hub.getBlockPos(), t) == null, "refused");
            OverwatchSessions.toggleMode(second);
            helper.assertTrue(!turret.salvo(), "a player not at the controls switched it");
            OverwatchSessions.toggleMode(player);
            helper.assertTrue(turret.salvo(), "R did not switch to Salvo");
            OverwatchSessions.toggleMode(player); // held R repeating, or a modified client: too soon, ignored
            helper.assertTrue(turret.salvo(), "a second R in the same tick switched it back");
            helper.runAfterDelay(OverwatchSessions.REQUEST_TICKS, () -> {
                OverwatchSessions.toggleMode(player);
                helper.assertTrue(!turret.salvo(), "R did not switch back to Single");
                turret.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(BastionItems.GUN_TURRET.get()));
                helper.runAfterDelay(OverwatchSessions.REQUEST_TICKS, () -> {
                    OverwatchSessions.toggleMode(player);
                    helper.assertTrue(!turret.salvo(), "a Gun took a fire mode");
                    OverwatchSessions.stop(player);
                    level.removeBlock(t, false);
                    helper.succeed();
                });
            });
        });
    }

    /** ← / → go round the hub's Overwatch turrets, past one with a plain connector and one another player controls. */
    @GameTest(template = YARD, timeoutTicks = 80, batch = "switchingSkipsTakenAndPlainTurrets")
    public static void switchingSkipsTakenAndPlainTurrets(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = ControlHubTests.player(helper, STAND);
        FakePlayer second = second(helper);
        HubInterfaceBlockEntity hub = ControlHubTests.hub(helper, IFACE, 1);
        hub.setEnergy(100_000);
        BlockPos corner = corner(hub);
        TurretBaseBlockEntity a = ControlHubTests.base(level, corner, null), plain = ControlHubTests.base(level, corner.east(), null);
        TurretBaseBlockEntity b = ControlHubTests.base(level, corner.east(2), null), taken = ControlHubTests.base(level, corner.south(), null);
        link(a, hub);
        ControlHubTests.connect(plain, hub);
        link(b, hub);
        link(taken, hub);
        whenOnline(helper, hub, () -> {
            BlockPos h = hub.getBlockPos();
            helper.assertTrue(OverwatchSessions.start(second, h, taken.getBlockPos()) == null, "second refused");
            helper.assertTrue(OverwatchSessions.start(player, h, a.getBlockPos()) == null, "first refused");
            OverwatchSessions.cycle(player, 1);
            helper.assertTrue(b.getBlockPos().equals(OverwatchSessions.controlled(player)) && a.manual() == null, "next went to " + OverwatchSessions.controlled(player));
            OverwatchSessions.cycle(player, 1); // spam (a modified client): too soon, ignored
            helper.assertTrue(b.getBlockPos().equals(OverwatchSessions.controlled(player)), "a second switch in the same tick went through");
            helper.runAfterDelay(OverwatchSessions.REQUEST_TICKS, () -> {
                OverwatchSessions.cycle(player, 1);
                helper.assertTrue(a.getBlockPos().equals(OverwatchSessions.controlled(player)), "it did not wrap: " + OverwatchSessions.controlled(player));
                helper.assertTrue(taken.manual() != null && taken.manual().player().equals(second.getUUID()), "the other player's turret moved");
                OverwatchSessions.stop(player);
                OverwatchSessions.stop(second);
                for (BlockPos pos : List.of(corner, corner.east(), corner.east(2), corner.south())) level.removeBlock(pos, false);
                helper.succeed();
            });
        });
    }

    /** The hub's roster says which turrets have a Link and who controls them. */
    @GameTest(template = YARD, timeoutTicks = 80, batch = "rosterShowsLinkAndController")
    public static void rosterShowsLinkAndController(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer player = ControlHubTests.player(helper, STAND);
        HubInterfaceBlockEntity hub = ControlHubTests.hub(helper, IFACE, 1);
        hub.setEnergy(100_000);
        BlockPos corner = corner(hub);
        TurretBaseBlockEntity linked = ControlHubTests.base(level, corner, null), plain = ControlHubTests.base(level, corner.east(), null);
        link(linked, hub);
        ControlHubTests.connect(plain, hub);
        whenOnline(helper, hub, () -> {
            helper.assertTrue(OverwatchSessions.start(player, hub.getBlockPos(), linked.getBlockPos()) == null, "refused");
            List<ControlHubMenu.Entry> entries = new ControlHubMenu(0, player.getInventory(), hub).roster().turrets();
            ControlHubMenu.Entry l = entries.stream().filter(e -> e.pos().equals(linked.getBlockPos())).findFirst().orElseThrow();
            ControlHubMenu.Entry p = entries.stream().filter(e -> e.pos().equals(plain.getBlockPos())).findFirst().orElseThrow();
            helper.assertTrue(l.linked() && l.controller().equals(player.getGameProfile().getName()), "linked entry " + l);
            helper.assertTrue(!p.linked() && p.controller().isEmpty(), "plain entry " + p);
            OverwatchSessions.stop(player);
            for (BlockPos pos : List.of(corner, corner.east())) level.removeBlock(pos, false);
            helper.succeed();
        });
    }

    // --- the controller in Jade (BlockInfo) ----------------------------------------------------------

    /** Jade's compact turret lines name the player steering it, right after the state. */
    @GameTest(template = ARENA, batch = "infoLinesNameTheController")
    public static void infoLinesNameTheController(GameTestHelper helper) {
        TurretBaseBlockEntity turret = small(helper, BastionItems.GUN_TURRET.get(), BastionItems.KINETIC_ROUNDS.get(), 0);
        helper.assertTrue(BlockInfo.lines(BlockInfo.write(turret), false).stream().map(CompatTests::text)
                .noneMatch(line -> line.startsWith("gui.bastion.turret.manual")), "a manual line without a controller");
        turret.setManual(new ManualControl(UUID.randomUUID(), "Tester"));
        List<String> lines = BlockInfo.lines(BlockInfo.write(turret), false).stream().map(CompatTests::text).toList();
        helper.assertTrue(lines.size() == 4 && lines.get(3).equals("gui.bastion.turret.manual[Tester]"), "lines " + lines);
        release(turret);
        helper.succeed();
    }

    // --- shots keep the rule they were fired under (review I3) ----------------------------------------

    /**
     * A bullet fired by the controller keeps the controller's rule (it may hit the cow) after the control ends, and one
     * fired once the turret is back on its own keeps the filter's.
     */
    @GameTest(template = ARENA, batch = "bulletsKeepTheRuleTheyWereFiredUnder")
    public static void bulletsKeepTheRuleTheyWereFiredUnder(GameTestHelper helper) {
        TurretBaseBlockEntity turret = small(helper, BastionItems.GUN_TURRET.get(), BastionItems.KINETIC_ROUNDS.get(), 0);
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, FAR);
        WeaponData data = turret.inventory().weaponData();
        Vec3 pivot = turret.pivot(data), dir = cow.getBoundingBox().getCenter().subtract(pivot).normalize();
        turret.setManual(new ManualControl(UUID.randomUUID(), "Tester"));
        FireContext manual = new FireContext(helper.getLevel(), turret, pivot, dir, null, stats(turret), new WeaponState(), 1, false,
                cow.getBoundingBox().getCenter());
        Predicate<Entity> valid = Bullets.validTargets(manual, type(turret)), filtered = Bullets.filtered(manual, type(turret));
        turret.setManual(null);
        helper.assertTrue(valid.test(cow) && filtered.test(cow), "a manual shot lost the controller's rule when the control ended");
        FireContext auto = new FireContext(helper.getLevel(), turret, pivot, dir, null, stats(turret), new WeaponState(), 1, false, null);
        helper.assertTrue(!Bullets.validTargets(auto, type(turret)).test(cow), "an automatic shot hits the cow");
        release(turret);
        cow.discard();
        helper.succeed();
    }

    /**
     * A rocket fired under control keeps the controller's rule: launched straight at a cow (which the filter spares) and the
     * control let go before it arrives, it still hits the cow. Missiles share this rule (TurretRocketEntity).
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "rocketsKeepTheRuleTheyWereFiredUnder")
    public static void rocketsKeepTheRuleTheyWereFiredUnder(GameTestHelper helper) {
        TurretBaseBlockEntity turret = small(helper, BastionItems.GUN_TURRET.get(), BastionItems.KINETIC_ROUNDS.get(), 0);
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, FAR);
        turret.setManual(new ManualControl(UUID.randomUUID(), "Tester"));
        TurretRocketEntity rocket = new TurretRocketEntity(helper.getLevel(), turret.getBlockPos(), 4, 0, 0.5f, 0, 60);
        rocket.setPos(cow.getBoundingBox().getCenter().add(-3, 0, 0));
        rocket.launch(new Vec3(0.4, 0, 0));
        helper.getLevel().addFreshEntity(rocket);
        turret.setManual(null); // let go while it flies
        helper.succeedWhen(() -> {
            helper.assertTrue(hurt(cow), "the rocket fired under control spared the cow after the control ended");
            release(turret);
        });
    }

    // --- the crosshair ray (ManualAim.pick) -------------------------------------------------------------

    /**
     * The crosshair on a cow aims where the ray meets its body, not at its feet (user 2026-10-20: the barrel dropped to the
     * feet); for a shell-lobbing weapon its feet; past it, the block behind.
     */
    @GameTest(template = ARENA, batch = "crosshairPicksTheBodyNotTheFeet")
    public static void crosshairPicksTheBodyNotTheFeet(GameTestHelper helper) {
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, FAR);
        var viewer = FakePlayerFactory.getMinecraft(helper.getLevel());
        Vec3 body = cow.getBoundingBox().getCenter(), from = body.add(-5, 0, 0);
        ManualAim.Pick pick = ManualAim.pick(viewer, from, from.add(20, 0, 0), false);
        helper.assertTrue(pick.entity() == cow, "picked " + pick.entity());
        helper.assertTrue(Math.abs(pick.point().y - body.y) < 0.05 && pick.point().x < body.x, "aimed at " + pick.point() + ", body " + body);
        ManualAim.Pick lob = ManualAim.pick(viewer, from, from.add(20, 0, 0), true);
        helper.assertTrue(lob.entity() == cow && lob.point().equals(cow.position()), "a shell aims at " + lob.point());
        ManualAim.Pick wall = ManualAim.pick(viewer, from.add(0, 1.5, 0), from.add(20, 1.5, 0), false);
        helper.assertTrue(wall.entity() == null && wall.point().x < from.x + 20, "over the cow it picked " + wall.entity() + " at " + wall.point());
        cow.discard();
        helper.succeed();
    }

    // --- override: control works on a switched-off turret (user 2026-10-21) ------------------------------

    /** A turret switched off by its own switch still answers the controller: Overwatch overrides it. */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "controlOverridesASwitchedOffTurret")
    public static void controlOverridesASwitchedOffTurret(GameTestHelper helper) {
        TurretBaseBlockEntity turret = small(helper, BastionItems.GUN_TURRET.get(), BastionItems.KINETIC_ROUNDS.get(), 10);
        turret.setEnabled(false);
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, FAR);
        ManualControl control = new ManualControl(UUID.randomUUID(), "Tester");
        turret.setManual(control);
        helper.onEachTick(() -> control.input(cow.getBoundingBox().getCenter(), cow.getId(), true, helper.getLevel().getGameTime()));
        helper.succeedWhen(() -> {
            helper.assertTrue(hurt(cow), "a switched-off turret ignored its controller, state " + turret.state());
            turret.setEnabled(true);
            release(turret);
        });
    }

    /** Let go, the switched-off turret is off again. */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "switchedOffTurretIsOffAgainAfterControl")
    public static void switchedOffTurretIsOffAgainAfterControl(GameTestHelper helper) {
        TurretBaseBlockEntity turret = small(helper, BastionItems.GUN_TURRET.get(), BastionItems.KINETIC_ROUNDS.get(), 10);
        turret.setEnabled(false);
        turret.setManual(new ManualControl(UUID.randomUUID(), "Tester"));
        helper.runAtTickTime(5, () -> turret.setManual(null));
        helper.runAtTickTime(10, () -> {
            helper.assertTrue(turret.state() == TurretState.DISABLED, "after control: " + turret.state());
            turret.setEnabled(true);
            release(turret);
            helper.succeed();
        });
    }

    /**
     * Under control a turret reaches overwatchRangeMultiplier (1.5) times its range (user 2026-10-21): a Shotgun (range 12)
     * hits a cow 15 blocks away, which its own range cannot.
     */
    @GameTest(template = RANGE, timeoutTicks = 300, batch = "overrideReachesFurther")
    public static void overrideReachesFurther(GameTestHelper helper) {
        helper.setBlock(LARGE, BastionBlocks.TURRET_BASE.get());
        TurretBaseBlockEntity shotgun = (TurretBaseBlockEntity) helper.getBlockEntity(LARGE);
        shotgun.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(BastionItems.SHOTGUN_TURRET.get()));
        shotgun.inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(BastionItems.CREATIVE_AMMO.get()));
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, LARGE.east(15));
        double distance = cow.position().distanceTo(shotgun.pivot(shotgun.inventory().weaponData()));
        helper.assertTrue(distance > stats(shotgun).range() && distance < stats(shotgun).range() * 1.5, "the cow is " + distance + " away");
        ManualControl control = new ManualControl(UUID.randomUUID(), "Tester");
        shotgun.setManual(control);
        helper.onEachTick(() -> control.input(cow.getBoundingBox().getCenter(), cow.getId(), true, helper.getLevel().getGameTime()));
        helper.succeedWhen(() -> {
            helper.assertTrue(hurt(cow), "the controlled Shotgun did not reach the cow, state " + shotgun.state());
            release(shotgun);
        });
    }

    // --- launchers under control: one shot per click (PLAN "Overwatch: mode, tembak per klik & lock") -------

    /** The crosshair held on {@code point} (and creature {@code entity}, -1: none) every tick, the attack key up. */
    private static void crosshairOn(GameTestHelper helper, ManualControl control, Vec3 point, int entity) {
        helper.onEachTick(() -> control.input(point, entity, false, helper.getLevel().getGameTime()));
    }

    /**
     * Runs {@code body} once, at test tick {@code tick} or the first tick after it on which the level can look up every one
     * of {@code mobs} by id: a test placed in a freshly loaded chunk lists its entities a few ticks late, and a press on a
     * creature the server cannot find locks nothing (the cause of the "0 locked" flakes, 2026-10-22).
     */
    private static void once(GameTestHelper helper, long tick, List<LivingEntity> mobs, Runnable body) {
        boolean[] done = {false};
        helper.onEachTick(() -> {
            if (done[0] || helper.getTick() < tick) return;
            for (LivingEntity mob : mobs) if (helper.getLevel().getEntity(mob.getId()) != mob) return;
            done[0] = true;
            body.run();
        });
    }

    /** {@code count} presses on {@code mob} at test tick {@code tick}, or once the level can find it. */
    private static void clickOn(GameTestHelper helper, ManualControl control, long tick, LivingEntity mob, int count) {
        once(helper, tick, List.of(mob), () -> control.input(mob.getBoundingBox().getCenter(), mob.getId(), false, count, 0, helper.getLevel().getGameTime()));
    }

    /** True once the level can look {@code mob} up by id (see {@link #once}). */
    private static boolean findable(GameTestHelper helper, LivingEntity mob) {
        return helper.getLevel().getEntity(mob.getId()) == mob;
    }

    /** {@code count} presses of the attack key reaching the server at test tick {@code tick}. */
    private static void click(GameTestHelper helper, ManualControl control, long tick, Vec3 point, int entity, int count) {
        helper.runAtTickTime(tick, () -> control.input(point, entity, false, count, 0, helper.getLevel().getGameTime()));
    }

    private static TurretBaseBlockEntity rocketsAt(GameTestHelper helper, ManualControl control) {
        TurretBaseBlockEntity turret = small(helper, BastionItems.ROCKET_LAUNCHER_TURRET.get(), BastionItems.ROCKETS.get(), 16);
        turret.setManual(control);
        return turret;
    }

    /**
     * Each click fires a loaded tube at once (fire_interval 36 ignored); a click with all four spent is dropped, and a
     * tube that reloads does not fire by itself. A click while its rocket still slides in is dropped too (review
     * 2026-10-22: "selesai reload"); once seated the next click fires it. With no ammo left a click does nothing. Timed
     * from the launcher's first tick (it primes reloadTimer to 8), not from the test's start.
     */
    @GameTest(template = ARENA, timeoutTicks = 120, batch = "rocketClicksSkipTheFireInterval")
    public static void rocketClicksSkipTheFireInterval(GameTestHelper helper) {
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, FAR);
        ManualControl control = new ManualControl(UUID.randomUUID(), "Tester");
        TurretBaseBlockEntity turret = rocketsAt(helper, control);
        Vec3 at = cow.getBoundingBox().getCenter();
        crosshairOn(helper, control, at, cow.getId());
        Runnable press = () -> control.input(at, cow.getId(), false, 1, 0, helper.getLevel().getGameTime());
        int[] spent = {0};
        // From the first press (t0): tube 0 reloads ~t0+8 and slides in until ~t0+22; the next reload ~t0+44, seated ~t0+58.
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(turret.weaponState().reloadTimer == 8, "the launcher has not ticked yet"))
                .thenExecute(press).thenIdle(1).thenExecute(press).thenIdle(1).thenExecute(press).thenIdle(1).thenExecute(press)
                .thenIdle(2).thenExecute(press) // t0+5: all four spent
                .thenIdle(2)
                .thenExecute(() -> helper.assertTrue(ammo(turret) == 12 && turret.weaponState().spentTubes == 0b1111,
                        "after four quick clicks and one on spent tubes: ammo " + ammo(turret) + ", spent " + Integer.toBinaryString(turret.weaponState().spentTubes)))
                .thenIdle(5).thenExecute(press) // t0+12: tube 0 reloaded, still sliding in
                .thenIdle(3)
                .thenExecute(() -> helper.assertTrue(ammo(turret) == 12, "fired on its own or while the rocket slid in: ammo " + ammo(turret)))
                .thenIdle(11).thenExecute(press) // t0+26: seated
                .thenIdle(3)
                .thenExecute(() -> helper.assertTrue(ammo(turret) == 11, "the click on the seated rocket: ammo " + ammo(turret)))
                .thenExecute(() -> turret.inventory().setStackInSlot(TurretInventory.AMMO_START, ItemStack.EMPTY))
                .thenIdle(32)
                .thenExecute(() -> { // t0+62
                    spent[0] = turret.weaponState().spentTubes;
                    helper.assertTrue(spent[0] != 0b1111, "no tube reloaded by t0+62");
                })
                .thenExecute(press)
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(turret.state() == TurretState.NO_AMMO, "with no ammo: " + turret.state());
                    helper.assertTrue((turret.weaponState().spentTubes & ~spent[0]) == 0, "a rocket left with no ammo");
                })
                .thenExecute(() -> release(turret))
                .thenSucceed();
    }

    /** Holding the attack key fires once: one press, then the key held for 40 ticks, is one rocket. */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "holdingFiresOnce")
    public static void holdingFiresOnce(GameTestHelper helper) {
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, FAR);
        ManualControl control = new ManualControl(UUID.randomUUID(), "Tester");
        TurretBaseBlockEntity turret = rocketsAt(helper, control);
        Vec3 at = cow.getBoundingBox().getCenter();
        helper.onEachTick(() -> control.input(at, cow.getId(), true, helper.getLevel().getGameTime()));
        click(helper, control, 2, at, cow.getId(), 1);
        helper.runAtTickTime(45, () -> {
            helper.assertTrue(ammo(turret) == 15, "held for 40 ticks: ammo " + ammo(turret));
            release(turret);
            helper.succeed();
        });
    }

    /**
     * Presses reaching the server in one tick (a lag spike) each fire while a tube is loaded, one per tick: five presses
     * on four loaded tubes are four rockets; the fifth is dropped, it does not fire when a tube reloads (review 2026-10-22).
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "pressesInOneTickFireEachLoadedTube")
    public static void pressesInOneTickFireEachLoadedTube(GameTestHelper helper) {
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, FAR);
        ManualControl control = new ManualControl(UUID.randomUUID(), "Tester");
        TurretBaseBlockEntity turret = rocketsAt(helper, control);
        Vec3 at = cow.getBoundingBox().getCenter();
        crosshairOn(helper, control, at, cow.getId());
        click(helper, control, 2, at, cow.getId(), 5);
        helper.runAtTickTime(9, () -> helper.assertTrue(ammo(turret) == 12, "five presses in one tick on four tubes: ammo " + ammo(turret)));
        helper.runAtTickTime(16, () -> {
            helper.assertTrue(ammo(turret) == 12, "the fifth press fired once a tube reloaded: ammo " + ammo(turret));
            release(turret);
            helper.succeed();
        });
    }

    /** Rocket Salvo under control: a click before all four are seated does nothing; once seated, one click sends all four in one tick. */
    @GameTest(template = ARENA, timeoutTicks = 80, batch = "rocketSalvoClickNeedsAllFour")
    public static void rocketSalvoClickNeedsAllFour(GameTestHelper helper) {
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, FAR);
        ManualControl control = new ManualControl(UUID.randomUUID(), "Tester");
        TurretBaseBlockEntity turret = rocketsAt(helper, control);
        turret.setSalvo(true);
        turret.weaponState().spentTubes = 0b0001; // reloads at once, then slides in for SLIDE_TICKS + the salvo hold
        Vec3 at = cow.getBoundingBox().getCenter();
        crosshairOn(helper, control, at, cow.getId());
        click(helper, control, 2, at, cow.getId(), 1);
        click(helper, control, 40, at, cow.getId(), 1);
        int[] last = {16};
        boolean[] together = {false};
        helper.onEachTick(() -> {
            int now = ammo(turret);
            if (last[0] - now == 4) together[0] = true;
            last[0] = now;
        });
        helper.runAtTickTime(4, () -> helper.assertTrue(ammo(turret) == 16, "fired before the fourth rocket was seated: ammo " + ammo(turret)));
        helper.runAtTickTime(43, () -> {
            helper.assertTrue(ammo(turret) == 12 && together[0], "one click should send all four in one tick: ammo " + ammo(turret));
            release(turret);
            helper.succeed();
        });
    }

    /** Missile Single under control: holding launches nothing; three clicks on three ticks launch three (fire_interval 8 would allow one). */
    @GameTest(template = RANGE, timeoutTicks = 60, batch = "missileClicksLaunchEachTube")
    public static void missileClicksLaunchEachTube(GameTestHelper helper) {
        TurretBaseBlockEntity launcher = largeWith(helper, BastionItems.MISSILE_LAUNCHER_TURRET.get());
        launcher.inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(BastionItems.CREATIVE_AMMO.get()));
        launcher.weaponState().tubes = MissileLauncherWeapon.ALL_TUBES;
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(19, 2, 3));
        ManualControl control = new ManualControl(UUID.randomUUID(), "Tester");
        launcher.setManual(control);
        Vec3 at = cow.getBoundingBox().getCenter();
        helper.onEachTick(() -> control.input(at, cow.getId(), helper.getTick() <= 10, helper.getLevel().getGameTime()));
        helper.runAtTickTime(11, () -> helper.assertTrue(Integer.bitCount(launcher.weaponState().tubes) == 12,
                "holding launched: " + Integer.bitCount(launcher.weaponState().tubes) + " tubes left"));
        for (int tick : new int[]{12, 13, 14}) click(helper, control, tick, at, cow.getId(), 1);
        helper.runAtTickTime(17, () -> {
            helper.assertTrue(Integer.bitCount(launcher.weaponState().tubes) == 9, "three clicks: " + Integer.bitCount(launcher.weaponState().tubes) + " tubes left");
            release(launcher);
            helper.succeed();
        });
    }

    // --- controlled missiles fall back to the filter (user 2026-10-22, choice A) -------------------------------

    /** Nothing under the crosshair: a press sends the missile after the filter's husk, never the cow the filter spares. */
    @GameTest(template = RANGE, timeoutTicks = 300, batch = "unlockedMissileChasesTheFilter")
    public static void unlockedMissileChasesTheFilter(GameTestHelper helper) {
        TurretBaseBlockEntity launcher = largeWith(helper, BastionItems.MISSILE_LAUNCHER_TURRET.get());
        launcher.inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(BastionItems.CREATIVE_AMMO.get()));
        launcher.weaponState().tubes = MissileLauncherWeapon.ALL_TUBES;
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(19, 2, 3));
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(19, 2, 10));
        ManualControl control = new ManualControl(UUID.randomUUID(), "Tester");
        launcher.setManual(control);
        Vec3 ground = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(12, 2, 6)));
        crosshairOn(helper, control, ground, -1);
        once(helper, 2, List.of(cow, husk), () -> control.input(ground, -1, false, 1, 0, helper.getLevel().getGameTime()));
        helper.succeedWhen(() -> {
            helper.assertTrue(hurt(husk), "the missile did not go for the filter's husk");
            helper.assertTrue(!hurt(cow), "the cow was hit");
            release(launcher);
        });
    }

    /** A missile chasing for the filter flies through a cow: launched straight at it under control, the cow is untouched. */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "filterMissilePassesACow")
    public static void filterMissilePassesACow(GameTestHelper helper) {
        TurretBaseBlockEntity turret = small(helper, BastionItems.GUN_TURRET.get(), BastionItems.KINETIC_ROUNDS.get(), 0);
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, FAR);
        turret.setManual(new ManualControl(UUID.randomUUID(), "Tester"));
        TurretRocketEntity missile = new TurretRocketEntity(BastionEntities.TURRET_MISSILE.get(), helper.getLevel(), turret.getBlockPos(),
                4, 0, 0.5f, 0, 60).homing(null, 11, 1.1f, 0.07f, 0).underFilter();
        missile.setPos(cow.getBoundingBox().getCenter().add(-3, 0, 0));
        missile.launch(new Vec3(0.4, 0, 0));
        helper.getLevel().addFreshEntity(missile);
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(!hurt(cow), "a filter missile hit the cow");
            release(turret);
            helper.succeed();
        });
    }

    /** A controlled missile whose locked cow is gone turns to the filter: the husk, not the nearer second cow. */
    @GameTest(template = RANGE, timeoutTicks = 200, batch = "lostLockTurnsToTheFilter")
    public static void lostLockTurnsToTheFilter(GameTestHelper helper) {
        TurretBaseBlockEntity turret = small(helper, BastionItems.GUN_TURRET.get(), BastionItems.KINETIC_ROUNDS.get(), 0);
        LivingEntity locked = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(19, 2, 5));
        LivingEntity other = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(19, 2, 3));
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(19, 2, 10));
        turret.setManual(new ManualControl(UUID.randomUUID(), "Tester"));
        TurretRocketEntity missile = new TurretRocketEntity(BastionEntities.TURRET_MISSILE.get(), helper.getLevel(), turret.getBlockPos(),
                4, 0, 0.5f, 0, 120).homing(locked, 11, 1.1f, 0.07f, 0).randomRetarget();
        // Over the second cow (nearest once the lock is gone), heading for the husk side.
        missile.setPos(locked.getBoundingBox().getCenter().add(0, 3, -4));
        missile.launch(new Vec3(0, 0, 0.4));
        helper.getLevel().addFreshEntity(missile);
        helper.runAtTickTime(1, locked::discard);
        helper.succeedWhen(() -> {
            helper.assertTrue(hurt(husk), "the missile did not turn to the husk");
            helper.assertTrue(!hurt(other), "the missile hit the second cow");
            release(turret);
        });
    }

    // --- Missile Salvo locks (user 2026-10-22) -------------------------------------------------------------

    /** A Missile Launcher in salvo mode under {@code control}, every tube loaded, with Creative Ammo when {@code ammo}. */
    private static TurretBaseBlockEntity salvoLauncher(GameTestHelper helper, ManualControl control, boolean ammo) {
        TurretBaseBlockEntity launcher = largeWith(helper, BastionItems.MISSILE_LAUNCHER_TURRET.get());
        if (ammo) launcher.inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(BastionItems.CREATIVE_AMMO.get()));
        launcher.weaponState().tubes = MissileLauncherWeapon.ALL_TUBES;
        launcher.setSalvo(true);
        launcher.setManual(control);
        return launcher;
    }

    private static int loaded(TurretBaseBlockEntity launcher) {
        return Integer.bitCount(launcher.weaponState().tubes);
    }

    /**
     * Eleven locks on the cow launch nothing; the twelfth launches all twelve, every one at the cow. Waits on each step
     * rather than fixed ticks (a test's first turret ticks can come late).
     */
    @GameTest(template = RANGE, timeoutTicks = 120, batch = "salvoLaunchesOnTheTwelfthLock")
    public static void salvoLaunchesOnTheTwelfthLock(GameTestHelper helper) {
        ManualControl control = new ManualControl(UUID.randomUUID(), "Tester");
        TurretBaseBlockEntity launcher = salvoLauncher(helper, control, true);
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(19, 2, 3));
        Vec3 at = cow.getBoundingBox().getCenter();
        clickOn(helper, control, 2, cow, 11);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(control.lockCount() == 11, "eleven presses: " + control.lockCount() + " locked"))
                .thenExecuteAfter(3, () -> {
                    helper.assertTrue(control.lockCount() == 11 && loaded(launcher) == 12, "eleven locks launched: " + loaded(launcher) + " tubes left");
                    control.input(at, cow.getId(), false, 1, 0, helper.getLevel().getGameTime());
                })
                .thenWaitUntil(() -> helper.assertTrue(launcher.weaponState().salvoTargets.length == 12, "no salvo after the twelfth lock"))
                .thenExecute(() -> {
                    int[] targets = launcher.weaponState().salvoTargets;
                    helper.assertTrue(java.util.Arrays.stream(targets).allMatch(id -> id == cow.getId()),
                            "salvo targets " + java.util.Arrays.toString(targets) + ", cow " + cow.getId());
                    helper.assertTrue(control.lockCount() == 0, "locks left after the launch: " + control.lockCount());
                })
                .thenWaitUntil(() -> helper.assertTrue(loaded(launcher) == 0, "tubes left after the salvo: " + loaded(launcher)))
                .thenExecute(() -> release(launcher))
                .thenSucceed();
    }

    /** A mob that is gone or a press on empty space locks nothing; presses add locks in order; a right-click removes the last. */
    @GameTest(template = RANGE, timeoutTicks = 60, batch = "locksRefuseAndUnlock")
    public static void locksRefuseAndUnlock(GameTestHelper helper) {
        ManualControl control = new ManualControl(UUID.randomUUID(), "Tester");
        TurretBaseBlockEntity launcher = salvoLauncher(helper, control, false);
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(19, 2, 3));
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(19, 2, 10));
        LivingEntity gone = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(19, 2, 6));
        Vec3 goneAt = gone.getBoundingBox().getCenter();
        helper.runAtTickTime(1, gone::discard);
        click(helper, control, 2, goneAt, gone.getId(), 1);
        click(helper, control, 4, Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(12, 2, 6))), -1, 1);
        helper.startSequence()
                .thenExecuteAfter(6, () -> helper.assertTrue(control.lockCount() == 0, "locked a gone mob or empty space: " + control.lockCount()))
                .thenWaitUntil(() -> helper.assertTrue(findable(helper, cow) && findable(helper, husk), "the cow and husk are not listed yet"))
                .thenExecute(() -> control.input(cow.getBoundingBox().getCenter(), cow.getId(), false, 2, 0, helper.getLevel().getGameTime()))
                .thenIdle(2)
                .thenExecute(() -> control.input(husk.getBoundingBox().getCenter(), husk.getId(), false, 1, 0, helper.getLevel().getGameTime()))
                .thenWaitUntil(() -> helper.assertTrue(java.util.Arrays.equals(control.lockIds(), new int[]{cow.getId(), cow.getId(), husk.getId()}),
                        "locks " + java.util.Arrays.toString(control.lockIds())))
                .thenExecute(() -> helper.assertTrue(control.takeLocksChanged() && !control.takeLocksChanged(), "the change flag is not one-shot"))
                .thenExecute(() -> control.input(husk.getBoundingBox().getCenter(), husk.getId(), false, 0, 1, helper.getLevel().getGameTime()))
                .thenWaitUntil(() -> helper.assertTrue(java.util.Arrays.equals(control.lockIds(), new int[]{cow.getId(), cow.getId()}),
                        "after one right-click " + java.util.Arrays.toString(control.lockIds())))
                .thenExecute(() -> release(launcher))
                .thenSucceed();
    }

    /** Locks clear when the mode goes to Single. */
    @GameTest(template = RANGE, timeoutTicks = 60, batch = "locksClearOnModeChange")
    public static void locksClearOnModeChange(GameTestHelper helper) {
        ManualControl control = new ManualControl(UUID.randomUUID(), "Tester");
        TurretBaseBlockEntity launcher = salvoLauncher(helper, control, true);
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(19, 2, 3));
        clickOn(helper, control, 2, cow, 5);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(control.lockCount() == 5, "five presses: " + control.lockCount()))
                .thenExecute(() -> launcher.setSalvo(false))
                .thenIdle(2)
                .thenExecute(() -> helper.assertTrue(control.lockCount() == 0, "locks kept in Single: " + control.lockCount()))
                .thenExecute(() -> release(launcher))
                .thenSucceed();
    }

    /** A controlled salvo never launches a partial load: seven tubes, no ammo left, twelve locks, nothing leaves. */
    @GameTest(template = RANGE, timeoutTicks = 80, batch = "salvoNeverLaunchesPartial")
    public static void salvoNeverLaunchesPartial(GameTestHelper helper) {
        ManualControl control = new ManualControl(UUID.randomUUID(), "Tester");
        TurretBaseBlockEntity launcher = salvoLauncher(helper, control, false);
        launcher.weaponState().tubes = 0b1111111;
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(19, 2, 3));
        clickOn(helper, control, 2, cow, 12);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(control.lockCount() == 12, "twelve presses: " + control.lockCount()))
                .thenIdle(30)
                .thenExecute(() -> helper.assertTrue(loaded(launcher) == 7 && !launcher.weaponState().salvoActive, "launched a partial load: " + loaded(launcher) + " left"))
                .thenExecute(() -> release(launcher))
                .thenSucceed();
    }

    /**
     * A locked cow gone before the launch sends its missiles to the filter's husk, picked at launch: in the wide YARD the
     * husk is over 20 blocks from the flight toward the lock, out of a lost missile's own search (RETARGET_RANGE, 10).
     */
    @GameTest(template = YARD, timeoutTicks = 300, batch = "deadLockGoesToTheFilter")
    public static void deadLockGoesToTheFilter(GameTestHelper helper) {
        ManualControl control = new ManualControl(UUID.randomUUID(), "Tester");
        TurretBaseBlockEntity launcher = salvoLauncher(helper, control, false);
        launcher.weaponState().tubes = MissileLauncherWeapon.ALL_TUBES & ~1; // eleven: no launch until ammo comes
        LivingEntity locked = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(14, 2, 5));
        LivingEntity other = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(16, 2, 6));
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(14, 2, 28));
        clickOn(helper, control, 2, locked, 12);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(control.lockCount() == 12, "twelve presses: " + control.lockCount()))
                .thenExecute(locked::discard)
                .thenExecute(() -> launcher.inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(BastionItems.CREATIVE_AMMO.get())))
                .thenWaitUntil(() -> helper.assertTrue(hurt(husk), "the salvo did not go for the husk, " + loaded(launcher) + " tubes loaded"))
                .thenExecute(() -> helper.assertTrue(!hurt(other), "the salvo hit the other cow"))
                .thenExecute(() -> release(launcher))
                .thenSucceed();
    }

    /** Switching to Single in the middle of a salvo stops it: no missile leaves after the switch, and the locks are gone. */
    @GameTest(template = RANGE, timeoutTicks = 80, batch = "singleStopsTheRipple")
    public static void singleStopsTheRipple(GameTestHelper helper) {
        ManualControl control = new ManualControl(UUID.randomUUID(), "Tester");
        TurretBaseBlockEntity launcher = salvoLauncher(helper, control, true);
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(19, 2, 3));
        clickOn(helper, control, 2, cow, 12);
        int[] left = {-1};
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(launcher.weaponState().salvoActive && loaded(launcher) < 12, "the salvo did not start"))
                .thenExecute(() -> {
                    launcher.setSalvo(false);
                    left[0] = loaded(launcher);
                })
                .thenIdle(10) // ripple pairs are salvo_interval (2) apart: five more would have left by now
                .thenExecute(() -> helper.assertTrue(!launcher.weaponState().salvoActive && loaded(launcher) >= left[0] && left[0] > 0 && control.lockCount() == 0,
                        "the salvo went on in Single: " + left[0] + " at the switch, " + loaded(launcher) + " now, active "
                                + launcher.weaponState().salvoActive + ", locks " + control.lockCount()))
                .thenExecute(() -> release(launcher))
                .thenSucceed();
    }

    // --- follow-up minors (user 2026-10-22 21:00) -------------------------------------------------------------

    /** Presses made while a weapon that does not take them is mounted are not kept for a launcher swapped in later. */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "stalePressesDoNotFireAfterASwap")
    public static void stalePressesDoNotFireAfterASwap(GameTestHelper helper) {
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, FAR);
        TurretBaseBlockEntity turret = small(helper, BastionItems.GUN_TURRET.get(), BastionItems.KINETIC_ROUNDS.get(), 10);
        ManualControl control = new ManualControl(UUID.randomUUID(), "Tester");
        turret.setManual(control);
        Vec3 at = cow.getBoundingBox().getCenter();
        crosshairOn(helper, control, at, cow.getId());
        click(helper, control, 2, at, cow.getId(), 5);
        helper.runAtTickTime(6, () -> {
            turret.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(BastionItems.ROCKET_LAUNCHER_TURRET.get()));
            turret.inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(BastionItems.ROCKETS.get(), 16));
        });
        helper.runAtTickTime(12, () -> {
            helper.assertTrue(ammo(turret) == 16, "a rocket left with no press after the swap: ammo " + ammo(turret));
            release(turret);
            helper.succeed();
        });
    }

    /** Locks go when the launcher is taken out: no brackets are left on screen for a weapon without them. */
    @GameTest(template = RANGE, timeoutTicks = 40, batch = "locksClearWhenTheWeaponChanges")
    public static void locksClearWhenTheWeaponChanges(GameTestHelper helper) {
        ManualControl control = new ManualControl(UUID.randomUUID(), "Tester");
        TurretBaseBlockEntity launcher = salvoLauncher(helper, control, true);
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(19, 2, 3));
        clickOn(helper, control, 2, cow, 3);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(control.lockCount() == 3, "three presses: " + control.lockCount()))
                .thenExecute(() -> {
                    control.takeLocksChanged();
                    launcher.inventory().setStackInSlot(TurretInventory.WEAPON, ItemStack.EMPTY);
                    helper.assertTrue(control.lockCount() == 0 && control.takeLocksChanged(), "locks kept after the weapon came out: " + control.lockCount());
                })
                .thenExecute(() -> release(launcher))
                .thenSucceed();
    }

    /** Each press locks the creature its own packet named, in order; a press and an undo in one packet cancel out. */
    @GameTest(template = RANGE, timeoutTicks = 40, batch = "pressesKeepTheirOwnTarget")
    public static void pressesKeepTheirOwnTarget(GameTestHelper helper) {
        ManualControl control = new ManualControl(UUID.randomUUID(), "Tester");
        TurretBaseBlockEntity launcher = salvoLauncher(helper, control, false);
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(19, 2, 3));
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(19, 2, 10));
        Vec3 cowAt = cow.getBoundingBox().getCenter(), huskAt = husk.getBoundingBox().getCenter();
        once(helper, 2, List.of(cow, husk), () -> { // two packets before one turret tick (a lag spike)
            long time = helper.getLevel().getGameTime();
            control.input(cowAt, cow.getId(), false, 1, 0, time);
            control.input(huskAt, husk.getId(), false, 1, 0, time);
        });
        int[] both = {cow.getId(), husk.getId()};
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(java.util.Arrays.equals(control.lockIds(), both), "locks " + java.util.Arrays.toString(control.lockIds())))
                .thenExecute(() -> control.input(cowAt, cow.getId(), false, 1, 1, helper.getLevel().getGameTime()))
                .thenIdle(3)
                .thenExecute(() -> helper.assertTrue(java.util.Arrays.equals(control.lockIds(), both),
                        "a press and its undo in one packet left " + java.util.Arrays.toString(control.lockIds())))
                .thenExecute(() -> release(launcher))
                .thenSucceed();
    }

    /** Letting go in the middle of a controlled salvo stops it: the rest are not sent at the player's locks later. */
    @GameTest(template = RANGE, timeoutTicks = 60, batch = "lettingGoStopsTheSalvo")
    public static void lettingGoStopsTheSalvo(GameTestHelper helper) {
        ManualControl control = new ManualControl(UUID.randomUUID(), "Tester");
        TurretBaseBlockEntity launcher = salvoLauncher(helper, control, true);
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(19, 2, 3));
        clickOn(helper, control, 2, cow, 12);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(launcher.weaponState().salvoActive && loaded(launcher) < 12, "the salvo did not start"))
                .thenExecute(() -> {
                    launcher.setManual(null);
                    helper.assertTrue(!launcher.weaponState().salvoActive && launcher.weaponState().salvoTargets.length == 0,
                            "the salvo went on after letting go, targets " + java.util.Arrays.toString(launcher.weaponState().salvoTargets));
                })
                .thenExecute(() -> release(launcher))
                .thenSucceed();
    }

    /** Stone from the launcher's top to the cow at (19, 2, 3) in RANGE: x 16, y 2-7, z 1-6. */
    private static void wall(GameTestHelper helper) {
        for (int y = 2; y <= 7; y++) {
            for (int z = 1; z <= 6; z++) helper.setBlock(new BlockPos(16, y, z), Blocks.STONE);
        }
    }

    /** A wall between the launcher and the cow: the cow cannot be locked (a modified client could aim through it). */
    @GameTest(template = RANGE, timeoutTicks = 40, batch = "locksNeedTheLauncherToSeeTheMob")
    public static void locksNeedTheLauncherToSeeTheMob(GameTestHelper helper) {
        wall(helper);
        ManualControl control = new ManualControl(UUID.randomUUID(), "Tester");
        TurretBaseBlockEntity launcher = salvoLauncher(helper, control, false);
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(19, 2, 3));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(findable(helper, cow), "the cow is not listed yet"))
                .thenExecute(() -> control.input(cow.getBoundingBox().getCenter(), cow.getId(), false, 3, 0, helper.getLevel().getGameTime()))
                .thenIdle(3)
                .thenExecute(() -> helper.assertTrue(control.lockCount() == 0, "locked a cow behind a wall: " + control.lockCount()))
                .thenExecute(() -> release(launcher))
                .thenSucceed();
    }

    /** Missile Single on a cow behind a wall: the missile goes for the filter's husk the launcher can see instead. */
    @GameTest(template = RANGE, timeoutTicks = 300, batch = "walledCrosshairMobGoesToTheFilter")
    public static void walledCrosshairMobGoesToTheFilter(GameTestHelper helper) {
        wall(helper);
        TurretBaseBlockEntity launcher = largeWith(helper, BastionItems.MISSILE_LAUNCHER_TURRET.get());
        launcher.inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(BastionItems.CREATIVE_AMMO.get()));
        launcher.weaponState().tubes = MissileLauncherWeapon.ALL_TUBES;
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(19, 2, 3));
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(19, 2, 10));
        ManualControl control = new ManualControl(UUID.randomUUID(), "Tester");
        launcher.setManual(control);
        Vec3 at = cow.getBoundingBox().getCenter();
        crosshairOn(helper, control, at, cow.getId());
        once(helper, 2, List.of(cow, husk), () -> control.input(at, cow.getId(), false, 1, 0, helper.getLevel().getGameTime()));
        helper.succeedWhen(() -> {
            helper.assertTrue(hurt(husk), "the missile did not go for the husk");
            helper.assertTrue(!hurt(cow), "the missile hit the cow behind the wall");
            release(launcher);
        });
    }

    // --- second review (2026-10-22) -----------------------------------------------------------------------

    /** Two rockets sliding in at once (they reload 8 ticks apart, each slides 14): neither fires before its own slide ends. */
    @GameTest(template = ARENA, timeoutTicks = 80, batch = "eachRocketSlidesInBeforeItFires")
    public static void eachRocketSlidesInBeforeItFires(GameTestHelper helper) {
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, FAR);
        ManualControl control = new ManualControl(UUID.randomUUID(), "Tester");
        TurretBaseBlockEntity turret = rocketsAt(helper, control);
        turret.weaponState().spentTubes = 0b0011; // tube 0 reloads at once, tube 1 eight ticks later
        Vec3 at = cow.getBoundingBox().getCenter();
        crosshairOn(helper, control, at, cow.getId());
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(turret.weaponState().spentTubes == 0b0010, "tube 0 did not reload"))
                .thenExecute(() -> control.input(at, cow.getId(), false, 2, 0, helper.getLevel().getGameTime())) // tubes 2 and 3
                .thenWaitUntil(() -> helper.assertTrue((turret.weaponState().spentTubes & 0b0011) == 0 && ammo(turret) == 14,
                        "tube 1 did not reload or tubes 2-3 did not fire: spent " + Integer.toBinaryString(turret.weaponState().spentTubes) + ", ammo " + ammo(turret)))
                .thenExecute(() -> control.input(at, cow.getId(), false, 1, 0, helper.getLevel().getGameTime())) // tube 0: 8 of its 14 ticks
                .thenIdle(2)
                .thenExecute(() -> helper.assertTrue(ammo(turret) == 14, "a rocket still sliding in fired: ammo " + ammo(turret)))
                .thenIdle(6)
                .thenExecute(() -> control.input(at, cow.getId(), false, 1, 0, helper.getLevel().getGameTime()))
                .thenIdle(2)
                .thenExecute(() -> helper.assertTrue(ammo(turret) == 13, "the seated rocket did not fire: ammo " + ammo(turret)))
                .thenExecute(() -> release(turret))
                .thenSucceed();
    }

    /** Missile Single in a lag spike: a press on the cow and the crosshair moved off it arrive together; the missile still goes for the cow. */
    @GameTest(template = RANGE, timeoutTicks = 300, batch = "singleMissileKeepsThePressTarget")
    public static void singleMissileKeepsThePressTarget(GameTestHelper helper) {
        TurretBaseBlockEntity launcher = largeWith(helper, BastionItems.MISSILE_LAUNCHER_TURRET.get());
        launcher.inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(BastionItems.CREATIVE_AMMO.get()));
        launcher.weaponState().tubes = MissileLauncherWeapon.ALL_TUBES;
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(19, 2, 3));
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(19, 2, 10));
        ManualControl control = new ManualControl(UUID.randomUUID(), "Tester");
        launcher.setManual(control);
        Vec3 ground = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(12, 2, 6)));
        crosshairOn(helper, control, ground, -1);
        once(helper, 2, List.of(cow, husk), () -> {
            long time = helper.getLevel().getGameTime();
            control.input(cow.getBoundingBox().getCenter(), cow.getId(), false, 1, 0, time);
            control.input(ground, -1, false, 0, 0, time);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(hurt(cow), "the missile did not go for the cow its press was on");
            release(launcher);
        });
    }

    /** A control whose session ended while the turret could not be reached lets go on the turret's next tick. */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "releasedControlLetsGo")
    public static void releasedControlLetsGo(GameTestHelper helper) {
        TurretBaseBlockEntity turret = small(helper, BastionItems.GUN_TURRET.get(), BastionItems.KINETIC_ROUNDS.get(), 10);
        ManualControl control = new ManualControl(UUID.randomUUID(), "Tester");
        turret.setManual(control);
        control.release();
        helper.runAtTickTime(3, () -> {
            helper.assertTrue(turret.manual() == null, "the turret still has the released control");
            release(turret);
            helper.succeed();
        });
    }
}

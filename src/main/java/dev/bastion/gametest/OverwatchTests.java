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
        server.setPvpAllowed(true);
        boolean withPvp = turret.wouldHit(other);
        server.setPvpAllowed(false);
        boolean withoutPvp = turret.wouldHit(other);
        server.setPvpAllowed(pvp);
        helper.assertTrue(withPvp && !withoutPvp, "PvP on " + withPvp + ", off " + withoutPvp);
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
        helper.onEachTick(() -> control.input(cow.getBoundingBox().getCenter(), cow.getId(), true, helper.getLevel().getGameTime()));
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
            OverwatchSessions.cycle(player, 1);
            helper.assertTrue(a.getBlockPos().equals(OverwatchSessions.controlled(player)), "it did not wrap: " + OverwatchSessions.controlled(player));
            helper.assertTrue(taken.manual() != null && taken.manual().player().equals(second.getUUID()), "the other player's turret moved");
            OverwatchSessions.stop(player);
            OverwatchSessions.stop(second);
            for (BlockPos pos : List.of(corner, corner.east(), corner.east(2), corner.south())) level.removeBlock(pos, false);
            helper.succeed();
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
}

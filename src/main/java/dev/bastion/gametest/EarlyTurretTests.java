package dev.bastion.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.bastion.Bastion;
import dev.bastion.menu.TurretMenu;
import dev.bastion.projectile.TurretArrowEntity;
import dev.bastion.registry.BastionBlocks;
import dev.bastion.registry.BastionEntities;
import dev.bastion.registry.BastionItems;
import dev.bastion.registry.BastionSounds;
import dev.bastion.registry.BastionWeaponTypes;
import dev.bastion.turret.ManualControl;
import dev.bastion.turret.TurretBaseBlock;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretInventory;
import dev.bastion.turret.TurretState;
import dev.bastion.turret.TurretTier;
import dev.bastion.weapon.Ballistics;
import dev.bastion.weapon.FireContext;
import dev.bastion.weapon.StatSheet;
import dev.bastion.weapon.WeaponData;
import dev.bastion.weapon.WeaponState;
import dev.bastion.weapon.WeaponType;
import dev.bastion.weapon.types.SlingWeapon;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** The early weapons (0.4.0) on the Makeshift Turret Base: Cobblestone Turret, Snowball Repeater, Potato Shotgun, Arrow Launcher. */
@GameTestHolder(Bastion.MOD_ID)
@PrefixGameTestTemplate(false)
public class EarlyTurretTests {
    private static final String ARENA = "arena";
    private static final BlockPos BASE = new BlockPos(2, 2, 3);
    private static final BlockPos FAR = new BlockPos(9, 2, 3);

    private static final List<Supplier<? extends Item>> EARLY_MODULES = List.of(BastionItems.COBBLESTONE_TURRET, BastionItems.SNOWBALL_REPEATER,
            BastionItems.POTATO_SHOTGUN, BastionItems.ARROW_LAUNCHER);

    private static TurretBaseBlockEntity base(GameTestHelper helper, Block block, Item weapon, Item ammo, int count) {
        helper.setBlock(BASE, block);
        TurretBaseBlockEntity turret = (TurretBaseBlockEntity) helper.getBlockEntity(BASE);
        if (weapon != null) turret.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(weapon));
        if (count > 0) turret.inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(ammo, count));
        return turret;
    }

    private static TurretBaseBlockEntity makeshift(GameTestHelper helper, Item weapon, Item ammo, int count) {
        return base(helper, BastionBlocks.MAKESHIFT_TURRET_BASE.get(), weapon, ammo, count);
    }

    private static LivingEntity tanky(GameTestHelper helper, BlockPos pos) {
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, pos);
        husk.getAttribute(Attributes.ARMOR).setBaseValue(0); // a husk's 2 armor would shave 2% off every hit
        husk.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000);
        husk.setHealth(2000);
        return husk;
    }

    private static void disarm(TurretBaseBlockEntity turret) {
        turret.inventory().setStackInSlot(TurretInventory.WEAPON, ItemStack.EMPTY);
    }

    @GameTest(template = ARENA)
    public static void earlyModulesFitOnlyMakeshift(GameTestHelper helper) {
        TurretBaseBlockEntity makeshift = makeshift(helper, null, null, 0);
        for (Supplier<? extends Item> module : EARLY_MODULES) {
            ItemStack stack = new ItemStack(module.get());
            helper.assertTrue(makeshift.inventory().isItemValid(TurretInventory.WEAPON, stack), stack + " does not fit a makeshift base");
        }
        for (Block block : List.of(BastionBlocks.TURRET_BASE.get(), BastionBlocks.LARGE_TURRET_BASE.get(), BastionBlocks.FORTRESS_TURRET_BASE.get())) {
            TurretBaseBlockEntity other = base(helper, block, null, null, 0);
            for (Supplier<? extends Item> module : EARLY_MODULES) {
                helper.assertTrue(!other.inventory().isItemValid(TurretInventory.WEAPON, new ItemStack(module.get())),
                        module.get() + " fits " + block);
            }
        }
        helper.succeed();
    }

    /** Review focus 1: a hopper into an empty makeshift base takes early ammo only. */
    @GameTest(template = ARENA)
    public static void emptyMakeshiftTakesOnlyEarlyAmmo(GameTestHelper helper) {
        TurretBaseBlockEntity turret = makeshift(helper, null, null, 0);
        for (Item early : List.of(Items.COBBLESTONE, Items.SNOWBALL, Items.POTATO, Items.ARROW)) {
            helper.assertTrue(turret.inventory().isItemValid(TurretInventory.AMMO_START, new ItemStack(early)), early + " refused");
        }
        helper.assertTrue(!turret.inventory().isItemValid(TurretInventory.AMMO_START, new ItemStack(BastionItems.KINETIC_ROUNDS.get())),
                "kinetic rounds accepted");
        IItemHandler handler = turret.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElseThrow(IllegalStateException::new);
        helper.assertTrue(ItemHandlerHelper.insertItem(handler, new ItemStack(Items.COBBLESTONE), false).isEmpty(), "a hopper could not load cobblestone");
        helper.assertTrue(!ItemHandlerHelper.insertItem(handler, new ItemStack(BastionItems.KINETIC_ROUNDS.get()), false).isEmpty(),
                "a hopper loaded kinetic rounds");
        helper.succeed();
    }

    /** Review focus 1: the early ammo never leaks into an empty standard base. */
    @GameTest(template = ARENA)
    public static void emptyStandardBaseRefusesCobblestone(GameTestHelper helper) {
        TurretBaseBlockEntity turret = base(helper, BastionBlocks.TURRET_BASE.get(), null, null, 0);
        for (Item early : List.of(Items.COBBLESTONE, Items.POTATO, Items.SNOWBALL, Items.ARROW)) {
            helper.assertTrue(!turret.inventory().isItemValid(TurretInventory.AMMO_START, new ItemStack(early)), early + " accepted by a standard base");
        }
        helper.assertTrue(turret.inventory().isItemValid(TurretInventory.AMMO_START, new ItemStack(BastionItems.KINETIC_ROUNDS.get())), "kinetic refused");
        helper.succeed();
    }

    /** An early module on a standard base is refused in the world and tells why (the lang key exists in both files). */
    @GameTest(template = ARENA)
    public static void earlyModuleOnStandardBaseIsRefusedWithAMessage(GameTestHelper helper) {
        TurretBaseBlockEntity turret = base(helper, BastionBlocks.TURRET_BASE.get(), null, null, 0);
        FakePlayer player = ControlHubTests.player(helper, new BlockPos(5, 1, 1));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(BastionItems.COBBLESTONE_TURRET.get()));
        BlockPos at = helper.absolutePos(BASE);
        helper.getLevel().getBlockState(at).use(helper.getLevel(), player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false));
        helper.assertTrue(turret.inventory().getStackInSlot(TurretInventory.WEAPON).isEmpty(), "the module was mounted");
        helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 1, "the module was used up");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        langHas(helper, "message.bastion.turret.needs_makeshift_base");
        helper.succeed();
    }

    /** Both lang files carry {@code key} (a FakePlayer cannot show us the actionbar text itself). */
    private static void langHas(GameTestHelper helper, String key) {
        for (String lang : List.of("en_us", "id_id")) {
            try (InputStream in = EarlyTurretTests.class.getResourceAsStream("/assets/bastion/lang/" + lang + ".json")) {
                helper.assertTrue(new String(in.readAllBytes(), StandardCharsets.UTF_8).contains("\"" + key + "\""), lang + " lacks " + key);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /**
     * The six early sound events, and the Javelin's doors and the spiders' steps (user 2026-10-27), exist, play files that are
     * there, and have a subtitle in both lang files.
     */
    @GameTest(template = ARENA)
    public static void newSoundsHaveFilesAndSubtitles(GameTestHelper helper) {
        JsonObject sounds;
        try (InputStream in = EarlyTurretTests.class.getResourceAsStream("/assets/bastion/sounds.json")) {
            sounds = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        for (String name : List.of("cobblestone.fire", "snowball.fire", "potato.fire", "arrow_launcher.fire", "makeshift_base.place",
                "makeshift_base.destroyed", "javelin.doors_open", "javelin.doors_close", "brood_spider.step")) {
            helper.assertTrue(ForgeRegistries.SOUND_EVENTS.containsKey(Bastion.id(name)), name + " is not registered");
            JsonObject event = sounds.getAsJsonObject(name);
            helper.assertTrue(event != null, "sounds.json lacks " + name);
            helper.assertTrue(("subtitles.bastion." + name).equals(event.get("subtitle").getAsString()), name + " has the subtitle " + event.get("subtitle"));
            langHas(helper, "subtitles.bastion." + name);
            for (JsonElement sound : event.getAsJsonArray("sounds")) {
                String file = (sound.isJsonObject() ? sound.getAsJsonObject().get("name") : sound).getAsString().replace("bastion:", "");
                helper.assertTrue(EarlyTurretTests.class.getResource("/assets/bastion/sounds/" + file + ".ogg") != null, name + " plays a missing " + file);
            }
        }
        helper.succeed();
    }

    /** The makeshift base creaks when placed (its own sound) and otherwise sounds like wood. */
    @GameTest(template = ARENA)
    public static void makeshiftBaseSoundsLikeWood(GameTestHelper helper) {
        SoundType sound = BastionBlocks.MAKESHIFT_TURRET_BASE.get().defaultBlockState().getSoundType();
        helper.assertTrue(sound.getPlaceSound() == BastionSounds.MAKESHIFT_BASE_PLACE.get(), "placing plays " + sound.getPlaceSound().getLocation());
        helper.assertTrue(sound.getBreakSound() == SoundEvents.WOOD_BREAK && sound.getStepSound() == SoundEvents.WOOD_STEP,
                "breaking plays " + sound.getBreakSound().getLocation() + ", stepping " + sound.getStepSound().getLocation());
        helper.succeed();
    }

    /**
     * A spectral arrow's look reaches the client: the flag is synced entity data, so a fresh client copy given the spawn's
     * data draws the spectral texture. Plain and tipped arrows stay unflagged.
     */
    @GameTest(template = ARENA)
    public static void spectralArrowsSyncTheirLook(GameTestHelper helper) {
        for (ItemStack ammo : List.of(new ItemStack(Items.SPECTRAL_ARROW), new ItemStack(Items.ARROW),
                PotionUtils.setPotion(new ItemStack(Items.TIPPED_ARROW), Potions.SLOWNESS))) {
            TurretArrowEntity arrow = new TurretArrowEntity(helper.getLevel(), helper.absolutePos(BASE), ammo, 3);
            TurretArrowEntity copy = new TurretArrowEntity(BastionEntities.TURRET_ARROW.get(), helper.getLevel());
            List<SynchedEntityData.DataValue<?>> spawnData = arrow.getEntityData().getNonDefaultValues();
            if (spawnData != null) copy.getEntityData().assignValues(spawnData);
            helper.assertTrue(copy.spectral() == ammo.is(Items.SPECTRAL_ARROW), ammo + " reaches the client as spectral " + copy.spectral());
        }
        helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 200, batch = "cobblestoneHitsForThree")
    public static void cobblestoneHitsForThree(GameTestHelper helper) {
        TurretBaseBlockEntity turret = makeshift(helper, BastionItems.COBBLESTONE_TURRET.get(), Items.COBBLESTONE, 10);
        LivingEntity husk = tanky(helper, FAR);
        helper.succeedWhen(() -> {
            helper.assertTrue(husk.getHealth() < husk.getMaxHealth(), "no hit yet, turret state " + turret.state());
            helper.assertTrue(Math.abs(husk.getMaxHealth() - husk.getHealth() - 3) < 1e-3, "first hit dealt " + (husk.getMaxHealth() - husk.getHealth()));
            disarm(turret);
        });
    }

    /** Ratios and counts, not ms: 5 shots a second (4 ticks apart) and 2 snowballs = 8 shots, then NO_AMMO. */
    @GameTest(template = ARENA, timeoutTicks = 300, batch = "snowballRepeatsFivePerSecond")
    public static void snowballRepeatsFivePerSecond(GameTestHelper helper) {
        TurretBaseBlockEntity turret = makeshift(helper, BastionItems.SNOWBALL_REPEATER.get(), Items.SNOWBALL, 2);
        LivingEntity husk = tanky(helper, FAR);
        List<Long> hits = new ArrayList<>(); // a bullet takes the same time to arrive every time, so hit gaps are shot gaps
        float[] last = {husk.getMaxHealth()};
        helper.onEachTick(() -> {
            if (husk.getHealth() < last[0]) hits.add(helper.getTick());
            last[0] = husk.getHealth();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(turret.state() == TurretState.NO_AMMO && hits.size() >= 8, "still firing: " + hits + " " + turret.state());
            helper.assertTrue(hits.size() == 8, "expected exactly 8 shots from 2 snowballs, got " + hits);
            for (int i = 1; i < hits.size(); i++) {
                long gap = hits.get(i) - hits.get(i - 1);
                helper.assertTrue(Math.abs(gap - 4) <= 1, "shot gap " + gap + " in " + hits);
            }
            helper.assertTrue(Math.abs(husk.getMaxHealth() - husk.getHealth() - 8 * 1.5f) < 1e-3, "damage " + (husk.getMaxHealth() - husk.getHealth()));
            disarm(turret);
        });
    }

    private static FireContext context(GameTestHelper helper, TurretBaseBlockEntity turret, LivingEntity target, WeaponData data) {
        Vec3 muzzle = helper.absoluteVec(Vec3.atCenterOf(BASE).add(1, 0.5, 0));
        Vec3 direction = target.getBoundingBox().getCenter().subtract(muzzle).normalize();
        StatSheet stats = StatSheet.of(data, TurretTier.T1, turret.inventory().modifierEffect());
        return new FireContext(helper.getLevel(), turret, muzzle, direction, target, stats, new WeaponState(), 1, false, null);
    }

    @GameTest(template = ARENA, timeoutTicks = 80, batch = "potatoFiresFivePelletsOfTwo")
    public static void potatoFiresFivePelletsOfTwo(GameTestHelper helper) {
        TurretBaseBlockEntity potato = makeshift(helper, BastionItems.POTATO_SHOTGUN.get(), null, 0); // no ammo: it never fires by itself
        LivingEntity husk = tanky(helper, new BlockPos(4, 2, 3)); // point-blank
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(helper.getLevel().getEntity(husk.getUUID()) == husk, "husk not findable yet");
            helper.assertTrue(SlingWeapon.directions(context(helper, potato, husk, potato.inventory().weaponData())).size() == 5, "potato pellets");
            WeaponType type = BastionWeaponTypes.POTATO.get();
            type.onFire(context(helper, potato, husk, potato.inventory().weaponData()));
        });
        for (var module : List.of(BastionItems.COBBLESTONE_TURRET, BastionItems.SNOWBALL_REPEATER)) {
            helper.assertTrue(SlingWeapon.directions(context(helper, potato, husk, module.get().data())).size() == 1, module.getId() + " pellets");
        }
        helper.runAtTickTime(40, () -> {
            float lost = husk.getMaxHealth() - husk.getHealth();
            helper.assertTrue(lost >= 2 && Math.abs(lost / 2 - Math.round(lost / 2)) < 1e-3 && lost <= 10, "pellets dealt " + lost + ", not steps of 2 (max 5)");
            disarm(potato);
            helper.succeed();
        });
    }

    /** Bullets fly through what the turret may not shoot: a villager in the line is spared, the husk behind is hit. */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "slingBulletsPassNonTargets")
    public static void slingBulletsPassNonTargets(GameTestHelper helper) {
        TurretBaseBlockEntity turret = makeshift(helper, BastionItems.COBBLESTONE_TURRET.get(), Items.COBBLESTONE, 10);
        LivingEntity villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(6, 2, 3));
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, FAR);
        helper.succeedWhen(() -> {
            helper.assertTrue(husk.getHealth() < husk.getMaxHealth(), "husk not hit, turret state " + turret.state());
            helper.assertTrue(villager.getHealth() == villager.getMaxHealth(), "the villager in the line was hit");
            disarm(turret);
        });
    }

    @GameTest(template = ARENA)
    public static void earlyRecipesUseIronAtTheCraftingTable(GameTestHelper helper) {
        for (String id : List.of("makeshift_turret_base", "cobblestone_turret", "snowball_repeater", "potato_shotgun", "arrow_launcher")) {
            var recipe = helper.getLevel().getRecipeManager().byKey(Bastion.id(id)).orElse(null);
            helper.assertTrue(recipe != null, "no recipe " + id);
            helper.assertTrue(recipe instanceof CraftingRecipe && recipe.getType() == RecipeType.CRAFTING, id + " is not a crafting recipe");
            helper.assertTrue(recipe.getIngredients().stream().anyMatch(i -> i.test(new ItemStack(Items.IRON_INGOT))), id + " needs no iron");
        }
        helper.succeed();
    }

    // --- Arrow Launcher -----------------------------------------------------------------------------

    /** 24 x 30 x 11 inside: a volley climbs up to ~17 blocks. */
    private static final String PIT = "pit";
    private static final BlockPos LAUNCHER = new BlockPos(2, 2, 6), TARGET = new BlockPos(15, 2, 6);

    /** A floor Arrow Launcher at LAUNCHER loaded with {@code ammo}. */
    private static TurretBaseBlockEntity launcher(GameTestHelper helper, ItemStack ammo) {
        helper.setBlock(LAUNCHER, BastionBlocks.MAKESHIFT_TURRET_BASE.get());
        TurretBaseBlockEntity turret = (TurretBaseBlockEntity) helper.getBlockEntity(LAUNCHER);
        turret.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(BastionItems.ARROW_LAUNCHER.get()));
        turret.inventory().setStackInSlot(TurretInventory.AMMO_START, ammo);
        return turret;
    }

    /** The turret arrows inside this test's structure. */
    private static List<TurretArrowEntity> arrows(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(TurretArrowEntity.class,
                new AABB(helper.absolutePos(new BlockPos(-1, -1, -1)), helper.absolutePos(new BlockPos(27, 33, 14))));
    }

    /** A turret arrow of {@code turret}'s, flying from {@code from} toward {@code to} at {@code speed}, with vanilla's drag and gravity. */
    private static void shoot(GameTestHelper helper, TurretBaseBlockEntity turret, ItemStack ammo, Vec3 from, Vec3 to, double speed) {
        TurretArrowEntity arrow = new TurretArrowEntity(helper.getLevel(), turret.getBlockPos(), ammo, turret.inventory().weaponData().damage());
        arrow.setPos(from);
        arrow.launch(to.subtract(from).normalize().scale(speed), 0.05f, 0.99f, 1200, Math.round(turret.inventory().weaponData().param("glow_ticks")));
        helper.getLevel().addFreshEntity(arrow);
    }

    /** As above, into the middle of {@code mob} from 2.5 blocks on the turret's side. */
    private static void shoot(GameTestHelper helper, TurretBaseBlockEntity turret, ItemStack ammo, LivingEntity mob, double speed) {
        Vec3 at = mob.getBoundingBox().getCenter();
        shoot(helper, turret, ammo, at.add(-2.5, 0, 0), at, speed);
    }

    /** Runs {@code body} once, as soon as the level can find every one of {@code mobs} (a fresh chunk takes a few ticks). */
    private static void whenFindable(GameTestHelper helper, List<LivingEntity> mobs, Runnable body) {
        boolean[] done = {false};
        helper.onEachTick(() -> {
            if (done[0] || mobs.stream().anyMatch(mob -> helper.getLevel().getEntity(mob.getId()) != mob)) return;
            done[0] = true;
            body.run();
        });
    }

    /**
     * The drag-aware high arc reaches the whole band the launcher targets, from min_range to its range (and 30 blocks), coming
     * down on the aim point, and has none 80 off; its closed form is the vanilla arrow's tick (move, drag, gravity), and a
     * real turret arrow flies it, also with a config's other drag and gravity.
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "dragArcLandsOnItsTarget")
    public static void dragArcLandsOnItsTarget(GameTestHelper helper) {
        TurretBaseBlockEntity turret = makeshift(helper, BastionItems.ARROW_LAUNCHER.get(), null, 0);
        WeaponData data = turret.inventory().weaponData();
        float gravity = data.param("gravity"), drag = data.param("drag");
        Vec3 pivot = turret.pivot(data), feet = new Vec3(pivot.x, helper.absoluteVec(new Vec3(0, 2, 0)).y, pivot.z);
        double min = data.param("min_range"), nearest = Math.sqrt(min * min - Math.pow(pivot.y - feet.y, 2)) + 0.01; // min_range away, in 3D
        Ballistics.Launch launch = null;
        for (double distance : List.of(nearest, 30.0, (double) data.range())) {
            Vec3 spot = feet.add(distance, 0, 0);
            launch = Ballistics.solve(turret, data, spot);
            helper.assertTrue(launch != null, "no launch to a spot " + distance + " blocks off");
            Vec3 landing = launch.at(launch.ticks(), gravity);
            helper.assertTrue(landing.distanceTo(spot) < 0.1, "lands at " + landing + ", aimed at " + spot + " (" + distance + " off)");
        }
        helper.assertTrue(Ballistics.solve(turret, data, feet.add(80, 0, 0)) == null, "a launch 80 blocks off");
        Vec3 p = launch.muzzle(), v = launch.velocity();
        for (int t = 0; t < 40; t++) {
            p = p.add(v);
            v = v.scale(drag).subtract(0, gravity, 0);
        }
        helper.assertTrue(p.distanceTo(launch.at(40, gravity)) < 1e-6, "at(40) " + launch.at(40, gravity) + ", ticked " + p);

        Vec3 start = helper.absoluteVec(new Vec3(4.5, 3.5, 4.5)), velocity = new Vec3(0.4, 0.2, 0);
        TurretArrowEntity arrow = new TurretArrowEntity(helper.getLevel(), turret.getBlockPos(), new ItemStack(Items.ARROW), 3);
        arrow.setPos(start);
        arrow.launch(velocity, 0.08f, 0.95f, 1200, 200);
        helper.getLevel().addFreshEntity(arrow);
        Ballistics.Launch odd = new Ballistics.Launch(start, velocity, 0, 0.95f);
        helper.onEachTick(() -> {
            if (arrow.tickCount != 5) return;
            Vec3 expected = odd.at(5, 0.08f);
            helper.assertTrue(arrow.position().distanceTo(expected) < 1e-3, "arrow at " + arrow.position() + ", its arc says " + expected);
            helper.succeed();
        });
    }

    /**
     * At rest the launcher looks up like the Mortar, not level like a crossbow (user 2026-10-23): an idle turret sweeps at
     * its min_pitch, and the high arc never fires below 45 degrees anyway (dragArcLandsOnItsTarget: the band still reaches).
     */
    @GameTest(template = ARENA)
    public static void arrowLauncherRestsLookingUp(GameTestHelper helper) {
        WeaponData data = makeshift(helper, BastionItems.ARROW_LAUNCHER.get(), null, 0).inventory().weaponData();
        helper.assertTrue(data.aim().minPitch() >= 40, "rests " + data.aim().minPitch() + " degrees up, near level");
        helper.succeed();
    }

    /** An arrow stuck in a block goes stuck_ticks after it stopped (vanilla keeps one 60 s), not before. */
    @GameTest(template = ARENA, timeoutTicks = 400, batch = "stuckArrowsGoAfterStuckTicks")
    public static void stuckArrowsGoAfterStuckTicks(GameTestHelper helper) {
        TurretBaseBlockEntity turret = makeshift(helper, BastionItems.ARROW_LAUNCHER.get(), null, 0);
        int stuck = Math.round(turret.inventory().weaponData().param("stuck_ticks"));
        TurretArrowEntity arrow = new TurretArrowEntity(helper.getLevel(), turret.getBlockPos(), new ItemStack(Items.ARROW), 3);
        arrow.setPos(helper.absoluteVec(new Vec3(6.5, 3.5, 3.5)));
        arrow.launch(new Vec3(0, -1, 0), 0.05f, 0.99f, stuck, 200); // straight into the floor
        helper.getLevel().addFreshEntity(arrow);
        long[] stoppedAt = {-1};
        Vec3[] last = {arrow.position()};
        helper.onEachTick(() -> {
            if (stoppedAt[0] < 0 && arrow.tickCount >= 2 && arrow.position().equals(last[0])) stoppedAt[0] = helper.getTick();
            last[0] = arrow.position();
            if (stoppedAt[0] < 0) return;
            long since = helper.getTick() - stoppedAt[0];
            if (since == stuck - 5) helper.assertTrue(!arrow.isRemoved(), "gone " + since + " ticks after it stuck, stuck_ticks " + stuck);
            if (since == stuck + 5) {
                helper.assertTrue(arrow.isRemoved(), "still there " + since + " ticks after it stuck, stuck_ticks " + stuck);
                helper.succeed();
            }
        });
    }

    /** One arrow item is one volley of six arrows. */
    @GameTest(template = PIT, timeoutTicks = 300, batch = "oneArrowIsSixArrows")
    public static void oneArrowIsSixArrows(GameTestHelper helper) {
        TurretBaseBlockEntity turret = launcher(helper, new ItemStack(Items.ARROW));
        tanky(helper, TARGET);
        helper.succeedWhen(() -> {
            helper.assertTrue(turret.inventory().getStackInSlot(TurretInventory.AMMO_START).isEmpty(), "no volley yet, state " + turret.state());
            helper.assertTrue(arrows(helper).size() == 6, "one arrow item made " + arrows(helper).size() + " arrows");
            disarm(turret);
        });
    }

    /**
     * The volley is the kind of arrow the turret took from its ammo slot: one Slowness tipped arrow makes six tipped arrows.
     * Steered by an Overwatch input, which fires through the same path (take the ammo, then fire) whatever the arc's height.
     */
    @GameTest(template = PIT, timeoutTicks = 100, batch = "volleyFiresTheArrowsItTook")
    public static void volleyFiresTheArrowsItTook(GameTestHelper helper) {
        TurretBaseBlockEntity turret = launcher(helper, PotionUtils.setPotion(new ItemStack(Items.TIPPED_ARROW), Potions.SLOWNESS));
        LivingEntity husk = tanky(helper, TARGET);
        ManualControl control = new ManualControl(UUID.randomUUID(), "Tester");
        turret.setManual(control);
        helper.onEachTick(() -> control.input(husk.position(), husk.getId(), true, helper.getLevel().getGameTime())); // trigger held
        helper.succeedWhen(() -> {
            helper.assertTrue(turret.inventory().getStackInSlot(TurretInventory.AMMO_START).isEmpty(), "no volley yet, state " + turret.state());
            List<TurretArrowEntity> arrows = arrows(helper);
            helper.assertTrue(arrows.size() == 6 && arrows.stream().allMatch(arrow -> arrow.getColor() != -1),
                    "the volley lost the tipped arrow's effect, colours " + arrows.stream().map(TurretArrowEntity::getColor).toList());
            turret.setManual(null);
            disarm(turret);
        });
    }

    /** Review focus 3: nobody picks the volley's arrows up (no free arrows from a turret). They thunk as vanilla's do (user 10-27). */
    @GameTest(template = PIT, timeoutTicks = 300, batch = "arrowsCannotBePickedUp")
    public static void arrowsCannotBePickedUp(GameTestHelper helper) {
        TurretBaseBlockEntity turret = launcher(helper, new ItemStack(Items.ARROW));
        tanky(helper, TARGET);
        helper.succeedWhen(() -> {
            List<TurretArrowEntity> arrows = arrows(helper);
            helper.assertTrue(!arrows.isEmpty(), "no volley yet, state " + turret.state());
            for (TurretArrowEntity arrow : arrows) helper.assertTrue(arrow.pickup == AbstractArrow.Pickup.DISALLOWED, "an arrow allows pickup: " + arrow.pickup);
            for (TurretArrowEntity arrow : arrows) helper.assertTrue(!arrow.isSilent(), "an arrow is silent: no vanilla hit sound");
            disarm(turret);
        });
    }

    /** A tipped arrow's effect reaches the target; a spectral arrow makes it glow. */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "tippedArrowsCarryTheirEffect")
    public static void tippedArrowsCarryTheirEffect(GameTestHelper helper) {
        TurretBaseBlockEntity turret = makeshift(helper, BastionItems.ARROW_LAUNCHER.get(), null, 0); // no ammo: it never fires by itself
        LivingEntity slowed = tanky(helper, new BlockPos(9, 2, 1)), lit = tanky(helper, new BlockPos(9, 2, 5));
        whenFindable(helper, List.of(slowed, lit), () -> {
            shoot(helper, turret, PotionUtils.setPotion(new ItemStack(Items.TIPPED_ARROW), Potions.SLOWNESS), slowed, 2);
            shoot(helper, turret, new ItemStack(Items.SPECTRAL_ARROW), lit, 2);
        });
        int glow = Math.round(turret.inventory().weaponData().param("glow_ticks"));
        helper.succeedWhen(() -> {
            helper.assertTrue(slowed.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "the tipped arrow gave no Slowness");
            helper.assertTrue(lit.hasEffect(MobEffects.GLOWING), "the spectral arrow gave no Glowing");
            helper.assertTrue(lit.getEffect(MobEffects.GLOWING).getDuration() > glow - 20 && lit.getEffect(MobEffects.GLOWING).getDuration() <= glow,
                    "Glowing lasts " + lit.getEffect(MobEffects.GLOWING).getDuration() + ", not the weapon's glow_ticks " + glow);
            disarm(turret);
        });
    }

    /** About 3 a hit at any speed (vanilla scales arrow damage with speed), and a hit right after another still counts. */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "arrowsDealAboutThree")
    public static void arrowsDealAboutThree(GameTestHelper helper) {
        TurretBaseBlockEntity turret = makeshift(helper, BastionItems.ARROW_LAUNCHER.get(), null, 0);
        LivingEntity husk = tanky(helper, FAR);
        List<Float> losses = new ArrayList<>();
        float[] last = {husk.getMaxHealth()};
        whenFindable(helper, List.of(husk), () -> shoot(helper, turret, new ItemStack(Items.ARROW), husk, 3.0));
        helper.onEachTick(() -> {
            if (husk.getHealth() >= last[0]) return;
            losses.add(last[0] - husk.getHealth());
            last[0] = husk.getHealth();
            if (losses.size() == 1) shoot(helper, turret, new ItemStack(Items.ARROW), husk, 1.5); // lands inside the hurt cooldown
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(losses.size() == 2, "hits so far " + losses);
            for (float loss : losses) helper.assertTrue(Math.abs(loss - 3) <= 0.5, "an arrow dealt " + loss + " " + losses);
            disarm(turret);
        });
    }

    /** Review focus 3: an arrow flies through a cow and a villager in its way and hits the husk behind them. */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "arrowsPassNonTargets")
    public static void arrowsPassNonTargets(GameTestHelper helper) {
        TurretBaseBlockEntity turret = makeshift(helper, BastionItems.ARROW_LAUNCHER.get(), null, 0);
        LivingEntity cow = helper.spawnWithNoFreeWill(EntityType.COW, new BlockPos(5, 2, 3));
        LivingEntity villager = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(7, 2, 3));
        LivingEntity husk = tanky(helper, FAR);
        whenFindable(helper, List.of(cow, villager, husk), () ->
                shoot(helper, turret, new ItemStack(Items.ARROW), helper.absoluteVec(new Vec3(3.5, 2.8, 3.5)), husk.getBoundingBox().getCenter(), 2));
        helper.succeedWhen(() -> {
            helper.assertTrue(husk.getHealth() < husk.getMaxHealth(), "the husk behind them was not hit");
            helper.assertTrue(cow.getHealth() == cow.getMaxHealth(), "the cow in the way was hit");
            helper.assertTrue(villager.getHealth() == villager.getMaxHealth(), "the villager in the way was hit");
            disarm(turret);
        });
    }

    /** A husk behind a wall the launcher cannot see over: the volley arcs over it and comes down on the husk. */
    @GameTest(template = PIT, timeoutTicks = 300, batch = "arrowLauncherShootsOverAWall")
    public static void arrowLauncherShootsOverAWall(GameTestHelper helper) {
        TurretBaseBlockEntity turret = launcher(helper, new ItemStack(Items.ARROW, 4));
        for (int z = 2; z <= 10; z++) for (int y = 2; y <= 5; y++) helper.setBlock(new BlockPos(9, y, z), Blocks.STONE_BRICKS);
        LivingEntity husk = helper.spawnWithNoFreeWill(EntityType.HUSK, TARGET);
        helper.succeedWhen(() -> {
            helper.assertTrue(husk.getHealth() < husk.getMaxHealth(), "no arrow came down on the husk yet, state " + turret.state());
            helper.assertTrue(turret.inventory().getStackInSlot(TurretInventory.AMMO_START).getCount() < 4, "no arrow used");
            disarm(turret);
        });
    }

    /** A phantom in range (a flyer) and a husk inside min_range: it never fires. */
    @GameTest(template = PIT, timeoutTicks = 160, batch = "arrowLauncherIgnoresFlyersAndCloseTargets")
    public static void arrowLauncherIgnoresFlyersAndCloseTargets(GameTestHelper helper) {
        TurretBaseBlockEntity turret = launcher(helper, new ItemStack(Items.ARROW, 4));
        helper.spawnWithNoFreeWill(EntityType.PHANTOM, new BlockPos(15, 9, 6));
        helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(7, 2, 6));
        helper.runAtTickTime(140, () -> {
            helper.assertTrue(turret.state() == TurretState.IDLE, "expected IDLE, got " + turret.state());
            helper.assertTrue(turret.inventory().getStackInSlot(TurretInventory.AMMO_START).getCount() == 4, "it fired");
            disarm(turret);
            helper.succeed();
        });
    }

    /**
     * Review focus 2: an Arrow Launcher fits a makeshift base on the floor only, whether mounted by hand, through the screen
     * or a hopper, or carried by a broken base that is placed again on a wall (the module drops off).
     */
    @GameTest(template = ARENA, batch = "arrowLauncherNeedsAFloorBase")
    public static void arrowLauncherNeedsAFloorBase(GameTestHelper helper) {
        ItemStack module = new ItemStack(BastionItems.ARROW_LAUNCHER.get());
        FakePlayer player = ControlHubTests.player(helper, new BlockPos(6, 2, 3));
        for (Direction facing : Direction.values()) {
            helper.setBlock(BASE, BastionBlocks.MAKESHIFT_TURRET_BASE.get().defaultBlockState().setValue(TurretBaseBlock.FACING, facing));
            TurretBaseBlockEntity turret = (TurretBaseBlockEntity) helper.getBlockEntity(BASE);
            boolean floor = facing == Direction.UP;
            helper.assertTrue(turret.inventory().isItemValid(TurretInventory.WEAPON, module) == floor, "facing " + facing + ": fits " + !floor);
            helper.assertTrue(turret.inventory().isItemValid(TurretInventory.WEAPON, new ItemStack(BastionItems.COBBLESTONE_TURRET.get())),
                    "a Cobblestone Turret refused, facing " + facing);
            IItemHandler hopper = turret.getCapability(ForgeCapabilities.ITEM_HANDLER, facing).orElseThrow(IllegalStateException::new);
            helper.assertTrue(!ItemHandlerHelper.insertItem(hopper, module.copy(), false).isEmpty(), "a hopper mounted it, facing " + facing);
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            TurretMenu.writeOpenData(buf, turret);
            TurretMenu screen = new TurretMenu(0, player.getInventory(), buf); // the client's copy of the slots
            helper.assertTrue(screen.turret().isItemValid(TurretInventory.WEAPON, module) == floor, "the screen disagrees, facing " + facing);
        }

        helper.setBlock(BASE, BastionBlocks.MAKESHIFT_TURRET_BASE.get().defaultBlockState().setValue(TurretBaseBlock.FACING, Direction.NORTH));
        TurretBaseBlockEntity wall = (TurretBaseBlockEntity) helper.getBlockEntity(BASE);
        player.setItemInHand(InteractionHand.MAIN_HAND, module.copy());
        BlockPos at = helper.absolutePos(BASE);
        helper.getLevel().getBlockState(at).use(helper.getLevel(), player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(at), Direction.NORTH, at, false));
        helper.assertTrue(wall.inventory().getStackInSlot(TurretInventory.WEAPON).isEmpty(), "mounted on a wall base by hand");
        helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 1, "the module was used up");
        langHas(helper, "message.bastion.turret.needs_floor");

        helper.setBlock(BASE, BastionBlocks.MAKESHIFT_TURRET_BASE.get());
        ((TurretBaseBlockEntity) helper.getBlockEntity(BASE)).inventory().setStackInSlot(TurretInventory.WEAPON, module.copy());
        helper.getLevel().destroyBlock(at, true);
        List<ItemEntity> drops = helper.getEntities(EntityType.ITEM, BASE, 3);
        helper.assertTrue(drops.size() == 1, "breaking the base dropped " + drops.size() + " items");
        player.setItemInHand(InteractionHand.MAIN_HAND, drops.get(0).getItem().copy());
        drops.get(0).discard();
        BlockPos stone = helper.absolutePos(new BlockPos(0, 3, 3)), placed = new BlockPos(1, 3, 3);
        player.getItemInHand(InteractionHand.MAIN_HAND).useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(stone).add(0.5, 0, 0), Direction.EAST, stone, false)));
        TurretBaseBlockEntity onWall = helper.getBlockEntity(placed) instanceof TurretBaseBlockEntity b ? b : null;
        helper.assertTrue(onWall != null && onWall.facing() == Direction.EAST, "the base was not placed on the wall: " + onWall);
        helper.assertTrue(onWall.inventory().getStackInSlot(TurretInventory.WEAPON).isEmpty(), "the Arrow Launcher came along onto a wall base");
        helper.assertTrue(helper.getEntities(EntityType.ITEM, placed, 3).stream().anyMatch(e -> e.getItem().is(BastionItems.ARROW_LAUNCHER.get())),
                "the Arrow Launcher did not drop off");
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        helper.succeed();
    }

    /** Review: an empty wall or ceiling makeshift base takes no arrows (no weapon there fires them); a floor base does. */
    @GameTest(template = ARENA, batch = "wallMakeshiftRefusesArrowsWhenEmpty")
    public static void wallMakeshiftRefusesArrowsWhenEmpty(GameTestHelper helper) {
        FakePlayer player = ControlHubTests.player(helper, new BlockPos(6, 2, 3));
        for (Direction facing : Direction.values()) {
            helper.setBlock(BASE, BastionBlocks.MAKESHIFT_TURRET_BASE.get().defaultBlockState().setValue(TurretBaseBlock.FACING, facing));
            TurretBaseBlockEntity turret = (TurretBaseBlockEntity) helper.getBlockEntity(BASE);
            boolean floor = facing == Direction.UP;
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            TurretMenu.writeOpenData(buf, turret);
            TurretInventory screen = new TurretMenu(0, player.getInventory(), buf).turret(); // the client's copy of the slots
            IItemHandler hopper = turret.getCapability(ForgeCapabilities.ITEM_HANDLER, facing).orElseThrow(IllegalStateException::new);
            for (TurretInventory inventory : List.of(turret.inventory(), screen)) {
                helper.assertTrue(inventory.isItemValid(TurretInventory.AMMO_START, new ItemStack(Items.ARROW)) == floor, "arrow slot rule, facing " + facing);
                helper.assertTrue(inventory.isItemValid(TurretInventory.AMMO_START, new ItemStack(Items.SPECTRAL_ARROW)) == floor, "spectral arrow rule, facing " + facing);
                helper.assertTrue(inventory.isItemValid(TurretInventory.AMMO_START, new ItemStack(Items.COBBLESTONE)), "cobblestone refused, facing " + facing);
            }
            helper.assertTrue(ItemHandlerHelper.insertItem(hopper, new ItemStack(Items.ARROW), true).isEmpty() == floor, "a hopper's arrow, facing " + facing);
            helper.assertTrue(ItemHandlerHelper.insertItem(hopper, new ItemStack(Items.COBBLESTONE), true).isEmpty(), "a hopper's cobblestone, facing " + facing);
        }
        helper.succeed();
    }

    /** Review focus 4: Creative Ammo feeds plain arrows to an Arrow Launcher and stones to a Cobblestone Turret, and is never used up. */
    @GameTest(template = PIT, timeoutTicks = 300, batch = "creativeAmmoFeedsEarlyTurrets")
    public static void creativeAmmoFeedsEarlyTurrets(GameTestHelper helper) {
        TurretBaseBlockEntity launcher = launcher(helper, new ItemStack(BastionItems.CREATIVE_AMMO.get()));
        tanky(helper, TARGET);
        BlockPos slingAt = new BlockPos(2, 2, 9);
        helper.setBlock(slingAt, BastionBlocks.MAKESHIFT_TURRET_BASE.get());
        TurretBaseBlockEntity sling = (TurretBaseBlockEntity) helper.getBlockEntity(slingAt);
        sling.inventory().setStackInSlot(TurretInventory.WEAPON, new ItemStack(BastionItems.COBBLESTONE_TURRET.get()));
        sling.inventory().setStackInSlot(TurretInventory.AMMO_START, new ItemStack(BastionItems.CREATIVE_AMMO.get()));
        LivingEntity near = tanky(helper, new BlockPos(5, 2, 9)); // inside the launcher's min_range: only the Cobblestone Turret shoots it
        helper.succeedWhen(() -> {
            List<TurretArrowEntity> arrows = arrows(helper);
            helper.assertTrue(!arrows.isEmpty(), "no volley yet, state " + launcher.state());
            for (TurretArrowEntity arrow : arrows) helper.assertTrue(arrow.getColor() == -1, "an arrow from Creative Ammo carries an effect");
            helper.assertTrue(near.getHealth() < near.getMaxHealth(), "the Cobblestone Turret never hit, state " + sling.state());
            for (TurretBaseBlockEntity turret : List.of(launcher, sling)) {
                helper.assertTrue(turret.inventory().getStackInSlot(TurretInventory.AMMO_START).is(BastionItems.CREATIVE_AMMO.get()), "Creative Ammo used up");
            }
            disarm(launcher);
            disarm(sling);
        });
    }
}

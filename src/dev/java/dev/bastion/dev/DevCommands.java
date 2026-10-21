package dev.bastion.dev;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.bastion.Bastion;
import dev.bastion.config.BastionConfig;
import dev.bastion.registry.BastionItems;
import dev.bastion.turret.TurretBaseItem;
import dev.bastion.turret.TurretInventory;
import dev.bastion.turret.TurretTier;
import dev.bastion.weapon.WeaponModuleItem;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Locale;

/**
 * Dev-only test helpers (src/dev; in the release jar never, in the -PwithDevCommands test jar yes). {@code /bastiondev zombie|skeleton|ravager [health] [count]} spawns
 * tanky, sunlight-proof mobs where you look (up to 200, up to 5,000 HP), to watch turret damage numbers. With Spore in the
 * run (-PwithCompatMods) also {@code busser}, its flying ranged mob. {@code /bastiondev automation} builds the
 * Create belt/arm/funnel test rig next to you, {@code /bastiondev automation report} shows what reached each turret.
 * {@code /bastiondev turrets [t1|t2|t3]} gives every weapon on its base with Creative Ammo, ready to place (T3 by default).
 * {@code /bastiondev ai true|false}: the test mobs walk and attack (the default) or stand still with no AI, and
 * {@code /bastiondev knockbackimmune true|false}: they take no knockback; both apply to the mobs already out and every new one
 * (user 2026-10-20). Repulsors still shove mobs on the server config's heavy list by its heavy push.
 */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID)
public final class DevCommands {
    private static final String DUMMY_TAG = "bastion_dummy";
    private static final float DEFAULT_HEALTH = 500, MAX_HEALTH = 5000;
    private static final int MAX_COUNT = 200;
    /** Spore's BusserVariants in order (setVariant takes the index): only noxious (TOXIC) shoots stingers. */
    private static final List<String> BUSSER_VARIANTS = List.of("default", "kidnapper", "kamikaze", "noxious", "transporter");
    /** /bastiondev ai and knockbackimmune: what every test mob gets (until the server restarts). */
    private static boolean dummyAi = true, dummyKnockbackImmune;

    static { // vanilla caps max_health at 1,024; dev runs and the -PwithDevCommands test jar only
        try {
            // By its SRG name (maxValue), so the field is found in a dev run and in a reobfuscated jar alike.
            Field max = ObfuscationReflectionHelper.findField(RangedAttribute.class, "f_22308_");
            max.setDouble(Attributes.MAX_HEALTH, Math.max(max.getDouble(Attributes.MAX_HEALTH), MAX_HEALTH));
        } catch (ReflectiveOperationException | ObfuscationReflectionHelper.UnableToFindFieldException e) {
            throw new IllegalStateException("could not raise the max_health cap", e);
        }
    }

    private DevCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("bastiondev");
        // Compat mobs, only when their mod is in the run (-PwithCompatMods): Spore's flying Busser shoots stingers.
        EntityType<? extends Mob> busser = modMob("spore", "busser");
        if (busser != null) root.then(mob("busser", busser, BUSSER_VARIANTS));
        // The rig is built from Create's classes: without Create (the test jar on another PC) the command would crash the game.
        if (ModList.get().isLoaded("create")) {
            root.then(Commands.literal("automation")
                    .executes(c -> {
                        BlockPos origin = BlockPos.containing(c.getSource().getPosition()).east(3);
                        int lanes = AutomationRig.build(c.getSource().getLevel(), origin);
                        c.getSource().sendSuccess(() -> Component.literal("Built " + lanes + " Create automation lanes east of you. "
                                + "Check with /bastiondev automation report"), true);
                        return lanes;
                    })
                    .then(Commands.literal("report").executes(c -> {
                        List<String> lines = AutomationRig.report(c.getSource().getLevel());
                        lines.forEach(line -> c.getSource().sendSuccess(() -> Component.literal(line), false));
                        return lines.size();
                    })));
        }
        event.getDispatcher().register(root.requires(source -> source.hasPermission(2))
                .then(Commands.literal("owndamage") // testing only: owners and trusted players can hurt their own turrets
                        .then(Commands.argument("on", BoolArgumentType.bool()).executes(c -> {
                            boolean on = BoolArgumentType.getBool(c, "on");
                            BastionConfig.TRUSTED_CAN_DAMAGE.set(on);
                            c.getSource().sendSuccess(() -> Component.literal("Own turrets " + (on ? "CAN" : "can no longer")
                                    + " be damaged by their owner and trusted players (server config trustedCanDamage = " + on + ")"), true);
                            return 1;
                        })))
                .then(Commands.literal("ai") // testing only: test mobs walk and attack, or stand still (user 10-20)
                        .then(Commands.argument("on", BoolArgumentType.bool()).executes(c -> {
                            dummyAi = BoolArgumentType.getBool(c, "on");
                            int mobs = applyToDummies(c.getSource());
                            c.getSource().sendSuccess(() -> Component.literal("Test mobs " + (dummyAi ? "walk and attack" : "stand still (no AI)")
                                    + ": " + mobs + " now, and every new one"), true);
                            return mobs;
                        })))
                .then(Commands.literal("knockbackimmune") // testing only: test mobs take no knockback (user 10-20)
                        .then(Commands.argument("on", BoolArgumentType.bool()).executes(c -> {
                            dummyKnockbackImmune = BoolArgumentType.getBool(c, "on");
                            int mobs = applyToDummies(c.getSource());
                            c.getSource().sendSuccess(() -> Component.literal("Test mobs " + (dummyKnockbackImmune ? "take no knockback" : "take knockback again")
                                    + ": " + mobs + " now, and every new one"), true);
                            return mobs;
                        })))
                .then(Commands.literal("turrets") // every weapon, ready to place: no assembling for tests (user 10-10)
                        .executes(c -> giveTurrets(c.getSource(), TurretTier.T3))
                        .then(Commands.argument("tier", StringArgumentType.word())
                                .suggests((c, builder) -> SharedSuggestionProvider.suggest(List.of("t1", "t2", "t3"), builder))
                                .executes(c -> giveTurrets(c.getSource(), TurretTier.byName(StringArgumentType.getString(c, "tier").toUpperCase(Locale.ROOT))))))
                .then(mob("zombie", EntityType.ZOMBIE, null))
                .then(mob("skeleton", EntityType.SKELETON, null))
                .then(mob("ravager", EntityType.RAVAGER, null)));
    }

    /**
     * One base per weapon module (a Large Turret Base for the big ones) at {@code tier}, holding the weapon and Creative
     * Ammo (which also pays energy weapons' shots), into the player's inventory: placed, it fires.
     */
    private static int giveTurrets(CommandSourceStack source, TurretTier tier) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        int given = 0;
        for (RegistryObject<Item> entry : BastionItems.REGISTER.getEntries()) {
            if (!(entry.get() instanceof WeaponModuleItem module)) continue;
            ItemStack base = TurretBaseItem.withTier((module.large() ? BastionItems.LARGE_TURRET_BASE : BastionItems.TURRET_BASE).get(), tier);
            TurretInventory inventory = new TurretInventory(() -> tier, module::large, slot -> {});
            inventory.setStackInSlot(TurretInventory.WEAPON, new ItemStack(module));
            inventory.setStackInSlot(TurretInventory.AMMO_START, new ItemStack(BastionItems.CREATIVE_AMMO.get()));
            base.getOrCreateTagElement("BlockEntityTag").put("Inventory", inventory.serializeNBT());
            base.setHoverName(Component.empty().append(module.getDescription()).append(" " + tier.name()));
            if (!player.getInventory().add(base)) player.drop(base, false);
            given++;
        }
        int count = given;
        source.sendSuccess(() -> Component.literal("Gave " + count + " armed " + tier.name() + " turrets (Creative Ammo inside)"), true);
        return count;
    }

    /** Another mod's mob type, or null when that mod is not loaded. */
    @Nullable
    @SuppressWarnings("unchecked")
    private static EntityType<? extends Mob> modMob(String namespace, String path) {
        ResourceLocation id = new ResourceLocation(namespace, path);
        return ForgeRegistries.ENTITY_TYPES.containsKey(id) ? (EntityType<? extends Mob>) ForgeRegistries.ENTITY_TYPES.getValue(id) : null;
    }

    /** {@code <name> [health] [count]}, plus {@code [variant]} for mobs whose mod picks a variant at spawn (setVariant(int)). */
    private static LiteralArgumentBuilder<CommandSourceStack> mob(String name, EntityType<? extends Mob> type, @Nullable List<String> variants) {
        var count = Commands.argument("count", IntegerArgumentType.integer(1, MAX_COUNT))
                .executes(c -> spawn(c.getSource(), type, FloatArgumentType.getFloat(c, "health"), IntegerArgumentType.getInteger(c, "count"), -1));
        if (variants != null) {
            count.then(Commands.argument("variant", StringArgumentType.word())
                    .suggests((c, builder) -> SharedSuggestionProvider.suggest(variants, builder))
                    .executes(c -> {
                        int variant = variants.indexOf(StringArgumentType.getString(c, "variant"));
                        if (variant < 0) throw new SimpleCommandExceptionType(Component.literal("Variants: " + String.join(", ", variants))).create();
                        return spawn(c.getSource(), type, FloatArgumentType.getFloat(c, "health"), IntegerArgumentType.getInteger(c, "count"), variant);
                    }));
        }
        return Commands.literal(name)
                .executes(c -> spawn(c.getSource(), type, DEFAULT_HEALTH, 1, -1))
                .then(Commands.argument("health", FloatArgumentType.floatArg(1, MAX_HEALTH))
                        .executes(c -> spawn(c.getSource(), type, FloatArgumentType.getFloat(c, "health"), 1, -1))
                        .then(count));
    }

    /** @param variant index for the mob's own setVariant(int) (its mod adds that variant's AI), or -1 to leave it */
    private static int spawn(CommandSourceStack source, EntityType<? extends Mob> type, float health, int count, int variant) {
        ServerLevel level = source.getLevel();
        Vec3 at = lookTarget(source);
        for (int i = 0; i < count; i++) {
            Mob mob = type.create(level);
            if (mob == null) return i;
            // No finalizeSpawn: no random armour (it would change the damage taken), no baby, no reinforcements.
            // A sunflower spiral one body apart, so 200 of them stand side by side (ponytail: assumes flat ground).
            double angle = i * 2.4, radius = mob.getBbWidth() * 1.1 * Math.sqrt(i);
            mob.moveTo(at.x + Math.cos(angle) * radius, at.y, at.z + Math.sin(angle) * radius, level.random.nextFloat() * 360, 0);
            mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
            mob.setHealth(health);
            if (mob instanceof AbstractSkeleton skeleton) { // the bow finalizeSpawn would give, so it shoots back
                skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
                skeleton.setDropChance(EquipmentSlot.MAINHAND, 0);
                skeleton.reassessWeaponGoal();
            }
            if (variant >= 0) {
                try {
                    mob.getClass().getMethod("setVariant", int.class).invoke(mob, variant);
                } catch (ReflectiveOperationException e) {
                    source.sendFailure(Component.literal(type.getDescription().getString() + " has no setVariant(int): " + e));
                    return i;
                }
            }
            mob.setPersistenceRequired();
            mob.addTag(DUMMY_TAG);
            applyToggles(mob);
            level.addFreshEntity(mob);
        }
        source.sendSuccess(() -> Component.literal("Spawned " + count + " sunlight-proof " + type.getDescription().getString()
                + "(s) with " + health + " HP"), true);
        return count;
    }

    /** The ai and knockbackimmune settings on one test mob; knockback back to its type's own resistance when off. */
    @SuppressWarnings("unchecked")
    private static void applyToggles(Mob mob) {
        mob.setNoAi(!dummyAi);
        AttributeInstance resistance = mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (resistance == null) return;
        AttributeSupplier defaults = DefaultAttributes.getSupplier((EntityType<? extends LivingEntity>) mob.getType());
        resistance.setBaseValue(dummyKnockbackImmune ? 1 : defaults.getBaseValue(Attributes.KNOCKBACK_RESISTANCE));
    }

    /** Applies the toggles to every test mob in every loaded level; returns how many. */
    private static int applyToDummies(CommandSourceStack source) {
        int count = 0;
        for (ServerLevel level : source.getServer().getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof Mob mob && mob.getTags().contains(DUMMY_TAG)) {
                    applyToggles(mob);
                    count++;
                }
            }
        }
        return count;
    }

    /** The block face you look at (64 blocks), else 5 blocks ahead; the command position for consoles. */
    private static Vec3 lookTarget(CommandSourceStack source) {
        Entity entity = source.getEntity();
        if (entity == null) return source.getPosition();
        Vec3 eye = entity.getEyePosition();
        Vec3 look = entity.getViewVector(1);
        BlockHitResult hit = source.getLevel().clip(new ClipContext(eye, eye.add(look.scale(64)), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, entity));
        if (hit.getType() == HitResult.Type.MISS) return eye.add(look.scale(5));
        return Vec3.atBottomCenterOf(hit.getBlockPos().relative(hit.getDirection()));
    }

    /**
     * Zombies and skeletons set themselves alight in aiStep when the sun hits them; this runs at the start of the next tick,
     * before the fire deals damage or shows flames. Only while in sunlight; lava still burns.
     */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide || entity.getRemainingFireTicks() <= 0 || !entity.getTags().contains(DUMMY_TAG)) return;
        boolean inSun = entity.level().isDay() && entity.level().canSeeSky(BlockPos.containing(entity.getX(), entity.getEyeY(), entity.getZ()));
        if (inSun && !entity.isInLava()) entity.clearFire();
    }
}

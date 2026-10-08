package dev.bastion.dev;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.bastion.Bastion;
import dev.bastion.config.BastionConfig;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Dev-only test helpers (src/dev, never in the jar). {@code /bastiondev zombie|skeleton|ravager [health] [count]} spawns
 * tanky, sunlight-proof mobs where you look (up to 200, up to 5,000 HP), to watch turret damage numbers. With Spore in the
 * run (-PwithCompatMods) also {@code busser}, its flying ranged mob. {@code /bastiondev automation} builds the
 * Create belt/arm/funnel test rig next to you, {@code /bastiondev automation report} shows what reached each turret.
 */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID)
public final class DevCommands {
    private static final String DUMMY_TAG = "bastion_dummy";
    private static final float DEFAULT_HEALTH = 500, MAX_HEALTH = 5000;
    private static final int MAX_COUNT = 200;
    /** Spore's BusserVariants in order (setVariant takes the index): only noxious (TOXIC) shoots stingers. */
    private static final List<String> BUSSER_VARIANTS = List.of("default", "kidnapper", "kamikaze", "noxious", "transporter");

    static { // vanilla caps max_health at 1,024; dev runs only (this class is never in the jar)
        try {
            Field max = RangedAttribute.class.getDeclaredField("maxValue");
            max.setAccessible(true);
            max.setDouble(Attributes.MAX_HEALTH, Math.max(max.getDouble(Attributes.MAX_HEALTH), MAX_HEALTH));
        } catch (ReflectiveOperationException e) {
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
        event.getDispatcher().register(root.requires(source -> source.hasPermission(2))
                .then(Commands.literal("automation")
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
                        })))
                .then(Commands.literal("owndamage") // testing only: owners and trusted players can hurt their own turrets
                        .then(Commands.argument("on", BoolArgumentType.bool()).executes(c -> {
                            boolean on = BoolArgumentType.getBool(c, "on");
                            BastionConfig.TRUSTED_CAN_DAMAGE.set(on);
                            c.getSource().sendSuccess(() -> Component.literal("Own turrets " + (on ? "CAN" : "can no longer")
                                    + " be damaged by their owner and trusted players (server config trustedCanDamage = " + on + ")"), true);
                            return 1;
                        })))
                .then(mob("zombie", EntityType.ZOMBIE, null))
                .then(mob("skeleton", EntityType.SKELETON, null))
                .then(mob("ravager", EntityType.RAVAGER, null)));
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
            level.addFreshEntity(mob);
        }
        source.sendSuccess(() -> Component.literal("Spawned " + count + " sunlight-proof " + type.getDescription().getString()
                + "(s) with " + health + " HP"), true);
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

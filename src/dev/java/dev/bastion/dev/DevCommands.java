package dev.bastion.dev;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.bastion.Bastion;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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

import java.lang.reflect.Field;
import java.util.List;

/**
 * Dev-only test helpers (src/dev, never in the jar). {@code /bastiondev zombie|skeleton|ravager [health] [count]} spawns
 * tanky, sunlight-proof mobs where you look (up to 200, up to 5,000 HP), to watch turret damage numbers. {@code /bastiondev automation} builds the
 * Create belt/arm/funnel test rig next to you, {@code /bastiondev automation report} shows what reached each turret.
 */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID)
public final class DevCommands {
    private static final String DUMMY_TAG = "bastion_dummy";
    private static final float DEFAULT_HEALTH = 500, MAX_HEALTH = 5000;
    private static final int MAX_COUNT = 200;

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
        event.getDispatcher().register(Commands.literal("bastiondev").requires(source -> source.hasPermission(2))
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
                .then(mob("zombie", EntityType.ZOMBIE))
                .then(mob("skeleton", EntityType.SKELETON))
                .then(mob("ravager", EntityType.RAVAGER)));
    }

    /** {@code <name> [health] [count]}. */
    private static LiteralArgumentBuilder<CommandSourceStack> mob(String name, EntityType<? extends Mob> type) {
        return Commands.literal(name)
                .executes(c -> spawn(c.getSource(), type, DEFAULT_HEALTH, 1))
                .then(Commands.argument("health", FloatArgumentType.floatArg(1, MAX_HEALTH))
                        .executes(c -> spawn(c.getSource(), type, FloatArgumentType.getFloat(c, "health"), 1))
                        .then(Commands.argument("count", IntegerArgumentType.integer(1, MAX_COUNT))
                                .executes(c -> spawn(c.getSource(), type, FloatArgumentType.getFloat(c, "health"),
                                        IntegerArgumentType.getInteger(c, "count")))));
    }

    private static int spawn(CommandSourceStack source, EntityType<? extends Mob> type, float health, int count) {
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

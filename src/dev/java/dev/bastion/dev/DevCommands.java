package dev.bastion.dev;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.bastion.Bastion;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Dev-only test helpers (src/dev, never in the jar). {@code /bastiondev zombie [health] [count]} spawns tanky,
 * sunlight-proof zombies where you look, to watch turret damage numbers. {@code /bastiondev automation} builds the
 * Create belt/arm/funnel test rig next to you, {@code /bastiondev automation report} shows what reached each turret.
 */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID)
public final class DevCommands {
    private static final String DUMMY_TAG = "bastion_dummy";
    private static final float DEFAULT_HEALTH = 500;

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
                .then(Commands.literal("zombie")
                        .executes(c -> spawnZombies(c.getSource(), DEFAULT_HEALTH, 1))
                        .then(Commands.argument("health", FloatArgumentType.floatArg(1, 100_000))
                                .executes(c -> spawnZombies(c.getSource(), FloatArgumentType.getFloat(c, "health"), 1))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 20))
                                        .executes(c -> spawnZombies(c.getSource(), FloatArgumentType.getFloat(c, "health"),
                                                IntegerArgumentType.getInteger(c, "count")))))));
    }

    private static int spawnZombies(CommandSourceStack source, float health, int count) {
        ServerLevel level = source.getLevel();
        Vec3 at = lookTarget(source);
        for (int i = 0; i < count; i++) {
            Zombie zombie = EntityType.ZOMBIE.create(level);
            if (zombie == null) return i;
            // No finalizeSpawn: no random armour (it would change the damage taken), no baby, no reinforcements.
            double angle = i * 2.4;
            double radius = count > 1 ? 1.2 : 0;
            zombie.moveTo(at.x + Math.cos(angle) * radius, at.y, at.z + Math.sin(angle) * radius, level.random.nextFloat() * 360, 0);
            zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
            zombie.setHealth(health);
            zombie.setPersistenceRequired();
            zombie.addTag(DUMMY_TAG);
            level.addFreshEntity(zombie);
        }
        source.sendSuccess(() -> Component.literal("Spawned " + count + " sunlight-proof zombie(s) with " + health + " HP"), true);
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
     * Zombies set themselves alight in aiStep when the sun hits them; this runs at the start of the next tick,
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

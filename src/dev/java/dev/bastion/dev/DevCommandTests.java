package dev.bastion.dev;

import dev.bastion.Bastion;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Dev-only: /bastiondev spawns its test mobs with the HP asked for, past vanilla's 1,024 cap. */
@GameTestHolder(Bastion.MOD_ID)
@PrefixGameTestTemplate(false)
public class DevCommandTests {
    @GameTest(template = "arena", batch = "devCommandSpawnsTankyMobs")
    public static void devCommandSpawnsTankyMobs(GameTestHelper helper) {
        Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 2, 4)));
        var source = helper.getLevel().getServer().createCommandSourceStack().withLevel(helper.getLevel()).withPosition(at).withSuppressedOutput();
        var commands = helper.getLevel().getServer().getCommands();
        commands.performPrefixedCommand(source, "bastiondev ravager 5000 3");
        commands.performPrefixedCommand(source, "bastiondev skeleton 40 3");
        AABB area = new AABB(at, at).inflate(12);
        List<Ravager> ravagers = helper.getLevel().getEntitiesOfClass(Ravager.class, area);
        List<Skeleton> skeletons = helper.getLevel().getEntitiesOfClass(Skeleton.class, area);
        helper.assertTrue(ravagers.size() == 3 && skeletons.size() == 3, ravagers.size() + " ravagers, " + skeletons.size() + " skeletons");
        helper.assertTrue(ravagers.stream().allMatch(r -> r.getMaxHealth() == 5000 && r.getHealth() == 5000), "ravager HP " + ravagers.get(0).getHealth());
        helper.assertTrue(skeletons.stream().allMatch(s -> s.getMainHandItem().is(Items.BOW)), "a skeleton has no bow");
        ravagers.forEach(Mob::discard);
        skeletons.forEach(Mob::discard);
        helper.succeed();
    }

    /** With Spore in the run: /bastiondev busser spawns its flying Busser with the HP, count and variant asked for. */
    @GameTest(template = "arena", batch = "devCommandSpawnsBussers")
    public static void devCommandSpawnsBussers(GameTestHelper helper) {
        net.minecraft.resources.ResourceLocation id = new net.minecraft.resources.ResourceLocation("spore", "busser");
        if (!net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.containsKey(id)) {
            helper.succeed();
            return;
        }
        Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 2, 4)));
        var source = helper.getLevel().getServer().createCommandSourceStack().withLevel(helper.getLevel()).withPosition(at).withSuppressedOutput();
        helper.getLevel().getServer().getCommands().performPrefixedCommand(source, "bastiondev busser 300 2 noxious");
        var type = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(id);
        List<Mob> bussers = helper.getLevel().getEntitiesOfClass(Mob.class, new AABB(at, at).inflate(8), m -> m.getType() == type);
        helper.assertTrue(bussers.size() == 2, bussers.size() + " bussers");
        helper.assertTrue(bussers.stream().allMatch(b -> b.getMaxHealth() == 300), "busser HP " + bussers.get(0).getMaxHealth());
        for (Mob b : bussers) { // noxious = Spore's TOXIC variant (3), the one that shoots stingers
            try {
                int variant = (int) b.getClass().getMethod("getTypeVariant").invoke(b);
                helper.assertTrue(variant == 3, "busser variant " + variant + ", expected 3 (noxious)");
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
        bussers.forEach(Mob::discard);
        helper.succeed();
    }
}

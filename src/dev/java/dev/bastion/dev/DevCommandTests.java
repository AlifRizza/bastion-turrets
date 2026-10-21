package dev.bastion.dev;

import dev.bastion.Bastion;
import dev.bastion.registry.BastionItems;
import dev.bastion.turret.TurretBaseItem;
import dev.bastion.turret.TurretInventory;
import dev.bastion.turret.TurretTier;
import dev.bastion.weapon.WeaponModuleItem;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
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

    /**
     * /bastiondev turrets t2 (user 10-10: "too lazy to assemble it myself"): one base per weapon module, a Large Turret Base
     * for the big ones, at the tier asked for, each holding its weapon and Creative Ammo as placing it will load them.
     */
    @GameTest(template = "arena", batch = "devCommandGivesArmedTurrets")
    public static void devCommandGivesArmedTurrets(GameTestHelper helper) {
        FakePlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.getInventory().clearContent();
        var source = helper.getLevel().getServer().createCommandSourceStack().withLevel(helper.getLevel()).withEntity(player).withSuppressedOutput();
        helper.getLevel().getServer().getCommands().performPrefixedCommand(source, "bastiondev turrets t2");
        List<ItemStack> given = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (!player.getInventory().getItem(i).isEmpty()) given.add(player.getInventory().getItem(i).copy());
        }
        player.getInventory().clearContent(); // the fake player is shared with later tests
        List<WeaponModuleItem> modules = BastionItems.REGISTER.getEntries().stream().map(RegistryObject::get)
                .filter(item -> item instanceof WeaponModuleItem).map(item -> (WeaponModuleItem) item).toList();
        helper.assertTrue(!modules.isEmpty() && given.size() == modules.size(), given.size() + " stacks given for " + modules.size() + " weapon modules");
        for (WeaponModuleItem module : modules) {
            ItemStack base = given.stream().filter(stack -> {
                CompoundTag data = stack.getTagElement("BlockEntityTag");
                return data != null && ItemStack.of(data.getCompound("Inventory").getList("Items", 10).getCompound(0)).is(module);
            }).findFirst().orElse(null);
            helper.assertTrue(base != null, "no base holds " + module);
            helper.assertTrue(base.is(module.large() ? BastionItems.LARGE_TURRET_BASE.get() : BastionItems.TURRET_BASE.get()), module + " on the wrong base");
            helper.assertTrue(TurretBaseItem.tier(base) == TurretTier.T2, module + " base is " + TurretBaseItem.tier(base));
            TurretInventory inventory = new TurretInventory(() -> TurretTier.T2, module::large, slot -> {});
            inventory.deserializeNBT(base.getTagElement("BlockEntityTag").getCompound("Inventory"));
            helper.assertTrue(inventory.getStackInSlot(TurretInventory.WEAPON).is(module)
                    && inventory.getStackInSlot(TurretInventory.AMMO_START).is(BastionItems.CREATIVE_AMMO.get()), module + " base contents");
        }
        helper.succeed();
    }

    /**
     * /bastiondev ai false: dummies stand still (no AI), now and when spawned; ai true gives it back. /bastiondev
     * knockbackimmune true: dummies take no knockback (full knockback resistance), false undoes it (user 2026-10-20).
     */
    @GameTest(template = "arena", batch = "devCommandTogglesAiAndKnockback")
    public static void devCommandTogglesAiAndKnockback(GameTestHelper helper) {
        Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 2, 4)));
        var source = helper.getLevel().getServer().createCommandSourceStack().withLevel(helper.getLevel()).withPosition(at).withSuppressedOutput();
        var commands = helper.getLevel().getServer().getCommands();
        commands.performPrefixedCommand(source, "bastiondev ai false");
        commands.performPrefixedCommand(source, "bastiondev knockbackimmune true");
        commands.performPrefixedCommand(source, "bastiondev zombie 40 1");
        List<Zombie> zombies = helper.getLevel().getEntitiesOfClass(Zombie.class, new AABB(at, at).inflate(8));
        helper.assertTrue(zombies.size() == 1, zombies.size() + " zombies");
        Zombie zombie = zombies.get(0);
        boolean stillAtSpawn = zombie.isNoAi(), immuneAtSpawn = zombie.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 1;
        commands.performPrefixedCommand(source, "bastiondev ai true");
        commands.performPrefixedCommand(source, "bastiondev knockbackimmune false");
        boolean aiBack = !zombie.isNoAi(), knockbackBack = zombie.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) < 1;
        zombie.discard();
        helper.assertTrue(stillAtSpawn && immuneAtSpawn, "spawned with AI " + !stillAtSpawn + ", immune " + immuneAtSpawn);
        helper.assertTrue(aiBack && knockbackBack, "after switching back: AI " + aiBack + ", knockback " + knockbackBack);
        helper.succeed();
    }
}

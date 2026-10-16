package dev.bastion.dev;

import dev.bastion.Bastion;
import dev.bastion.client.render.HubBlueprint;
import dev.bastion.client.screen.HubSelection;
import dev.bastion.hub.ControlHubStructure;
import dev.bastion.hub.HubConnectorItem;
import dev.bastion.hub.HubInterfaceBlock;
import dev.bastion.hub.HubInterfaceBlockEntity;
import dev.bastion.hub.SatelliteUplinkBlock;
import dev.bastion.network.BastionNetwork;
import dev.bastion.network.HubAction;
import dev.bastion.registry.BastionBlocks;
import dev.bastion.registry.BastionItems;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.turret.TurretInventory;
import dev.bastion.turret.TurretTier;
import dev.bastion.turret.targeting.TargetFilter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Dev-only scene for the Turret Control Hub (-Pshowcase=hub, with -PshowcaseWorld=showcase_fx): an unfinished hub with
 * its blueprint, then T1, T2 and T3 cube hubs side by side, their Interfaces facing the camera (south), powered by Creative
 * Power Sources, with turrets around the T3 one (healthy, damaged, critical, unarmed, out of ammo), by day and by night;
 * then the T3 hub's screens (Map, List, zoom, a turret opened through it, back, Batch settings) and an OFFLINE hub.
 */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID, value = Dist.CLIENT)
public final class ControlHubShowcase {
    private static final boolean ON = "hub".equals(System.getProperty("bastion.showcase"));
    static final BlockPos ORIGIN = new BlockPos(0, 150, 0);
    // The Interfaces sit in their cubes' middle layer, one above the floor; FIELD is the floor-level anchor of the turrets.
    static final BlockPos T1_IFACE = ORIGIN.offset(-8, 1, 4), T2_IFACE = ORIGIN.offset(0, 1, 4), T3_IFACE = ORIGIN.offset(8, 1, 4);
    static final BlockPos UNFINISHED = ORIGIN.offset(-18, 1, 4), FIELD = ORIGIN.offset(8, 0, 4);
    private static int tick = -1;

    private ControlHubShowcase() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (!ON || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (mc.player == null || server == null) return;
        mc.options.pauseOnLostFocus = false;
        if (mc.screen instanceof PauseScreen) mc.setScreen(null);
        tick++;
        Vec3 lineup = Vec3.atBottomCenterOf(T2_IFACE).add(0, 1.5, -1);
        Vec3 dish = Vec3.atBottomCenterOf(T3_IFACE.north()).add(0, 3.8, 0);
        Vec3 unfinished = Vec3.atBottomCenterOf(UNFINISHED.north()).add(0, 1, 0);
        if (tick == 20) Showcase.server(server, ControlHubShowcase::build);
        if (tick == 22) mc.options.hideGui = true;
        if (tick == 40) Showcase.camera(server, lineup.add(0, 5, 13), lineup);
        if (tick == 60) Showcase.shot(mc, "hub_day");
        if (tick == 62) Showcase.camera(server, dish.add(3.2, 1.2, 3.6), dish);
        if (tick == 80) Showcase.shot(mc, "hub_dish");
        if (tick == 82) {
            Showcase.camera(server, unfinished.add(3.5, 4.5, 7.5), unfinished);
            HubBlueprint.show(UNFINISHED, Direction.SOUTH);
        }
        if (tick == 92) Showcase.shot(mc, "hub_blueprint");
        if (tick == 94) Showcase.server(server, s -> s.overworld().setDayTime(18000));
        if (tick == 96) Showcase.camera(server, lineup.add(0, 5, 13), lineup);
        if (tick == 112) Showcase.shot(mc, "hub_night");
        if (tick == 114) Showcase.server(server, s -> s.overworld().setDayTime(6000));
        Vec3 t3 = Vec3.atCenterOf(T3_IFACE), t2 = Vec3.atCenterOf(T2_IFACE);
        if (tick == 116) {
            mc.options.hideGui = false;
            Showcase.camera(server, t3.add(0, 0.3, 2.2), t3);
        }
        if (tick == 124) use(mc);
        if (tick == 140) Showcase.shot(mc, "hub_gui_map");
        if (tick == 142 && mc.screen != null) clickTab(mc.screen, 1);
        if (tick == 150) Showcase.shot(mc, "hub_gui_list");
        if (tick == 152 && mc.screen != null) {
            clickTab(mc.screen, 0);
            if (mc.screen instanceof AbstractContainerScreen<?> gui) { // zoom round a point 81 px into the map (at least 162 px wide)
                gui.mouseScrolled(gui.getGuiLeft() + 8 + 81, gui.getGuiTop() + 30 + 81, 1);
            }
        }
        if (tick == 154 && mc.screen instanceof AbstractContainerScreen<?> gui) { // then the + button (4x) and a left-drag moving the map
            for (GuiEventListener child : List.copyOf(gui.children())) {
                if (child instanceof AbstractWidget button && "+".equals(button.getMessage().getString())) {
                    gui.mouseClicked(button.getX() + 6, button.getY() + 6, 0);
                    gui.mouseReleased(button.getX() + 6, button.getY() + 6, 0);
                }
            }
            double x = gui.getGuiLeft() + 8 + 81, y = gui.getGuiTop() + 30 + 81;
            gui.mouseClicked(x, y, 0);
            gui.mouseDragged(x - 40, y - 30, 0, -40, -30);
            gui.mouseReleased(x - 40, y - 30, 0);
        }
        if (tick == 158) Showcase.shot(mc, "hub_gui_zoom");
        if (tick == 160) BastionNetwork.CHANNEL.sendToServer(HubAction.open(FIELD.offset(-4, 0, 6)));
        if (tick == 172) Showcase.shot(mc, "hub_gui_turret");
        if (tick == 174) {
            HubSelection.returning = true;
            BastionNetwork.CHANNEL.sendToServer(HubAction.back());
        }
        if (tick == 184) Showcase.shot(mc, "hub_gui_back");
        if (tick == 186) {
            Showcase.server(server, s -> { // different settings, so Mixed and (some) show
                if (s.overworld().getBlockEntity(FIELD.offset(14, 0, -6)) instanceof TurretBaseBlockEntity sniper) {
                    TargetFilter filter = sniper.filter().copy();
                    filter.priority = TargetFilter.Priority.LOWEST_HEALTH;
                    filter.rules.put(new ResourceLocation("minecraft:zombie"), TargetFilter.Rule.ALWAYS);
                    sniper.setFilter(filter);
                }
            });
            HubSelection.select(List.of(FIELD.offset(-4, 0, 6), FIELD.offset(5, 0, 7), FIELD.offset(14, 0, -6)));
        }
        if (tick == 192 && mc.screen != null) clickTab(mc.screen, 2);
        if (tick == 198) Showcase.shot(mc, "hub_gui_batch");
        if (tick == 200) {
            mc.setScreen(null);
            Showcase.server(server, s -> {
                ServerLevel level = s.overworld();
                level.removeBlock(source(T2_IFACE), false);
                if (level.getBlockEntity(T2_IFACE) instanceof HubInterfaceBlockEntity hub) hub.setEnergy(0);
            });
            Showcase.camera(server, t2.add(0, 0.3, 2.2), t2);
        }
        if (tick == 210) use(mc);
        if (tick == 224) Showcase.shot(mc, "hub_gui_offline");
        if (tick == 226) mc.setScreen(null);
        if (tick == 232) mc.stop();
    }

    static void build(MinecraftServer server) {
        ServerLevel level = server.overworld();
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        level.setDayTime(6000);
        ServerPlayer player = server.getPlayerList().getPlayers().get(0);
        player.setGameMode(GameType.CREATIVE);
        player.getInventory().clearContent();
        for (int x = -40; x <= 40; x++) {
            for (int z = -40; z <= 40; z++) {
                level.setBlock(ORIGIN.offset(x, -1, z), Blocks.SMOOTH_STONE.defaultBlockState(), 2);
                for (int y = 0; y < 8; y++) level.setBlock(ORIGIN.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        // The scratch world keeps what earlier scenes left (drops): clear the stage.
        level.getEntitiesOfClass(ItemEntity.class, new AABB(ORIGIN).inflate(48)).forEach(Entity::discard);
        level.getEntitiesOfClass(Mob.class, new AABB(ORIGIN).inflate(48)).forEach(Entity::discard); // and mobs earlier scenes spawned
        hub(level, T1_IFACE, 1);
        hub(level, T2_IFACE, 2);
        hub(level, T3_IFACE, 3);
        // Unfinished: no satellite, the middle layer's front-right casing missing (in sight of the blueprint camera).
        hub(level, UNFINISHED, 0);
        level.setBlock(ControlHubStructure.slots(UNFINISHED, Direction.SOUTH).get(10), Blocks.AIR.defaultBlockState(), 3);
        // Turrets round the T3 hub, in its 7x7 chunks; all but the Ion Rifle hold a connector linked to it.
        turret(level, FIELD.offset(-4, 0, 6), BastionItems.GUN_TURRET.get(), 1f, true, true);
        turret(level, FIELD.offset(5, 0, 7), BastionItems.MACHINE_GUN_TURRET.get(), 0.4f, true, true);
        turret(level, FIELD.offset(14, 0, -6), BastionItems.SNIPER_TURRET.get(), 0.15f, true, true);
        turret(level, FIELD.offset(-12, 0, -14), BastionItems.SHOTGUN_TURRET.get(), 1f, false, true);
        turret(level, FIELD.offset(24, 0, 18), null, 1f, false, true);
        turret(level, FIELD.offset(30, 0, -30), BastionItems.ION_RIFLE_TURRET.get(), 0.8f, true, false);
    }

    /** A hub whose Interface faces south (the camera), its Power Port fed by a Creative Power Source; tier 0: no satellite. */
    static HubInterfaceBlockEntity hub(ServerLevel level, BlockPos iface, int tier) {
        level.setBlock(iface, BastionBlocks.HUB_INTERFACE.get().defaultBlockState().setValue(HubInterfaceBlock.FACING, Direction.SOUTH), 3);
        List<BlockPos> slots = ControlHubStructure.slots(iface, Direction.SOUTH);
        for (int i = 0; i < slots.size(); i++) {
            level.setBlock(slots.get(i), (i == 0 ? BastionBlocks.HUB_POWER_PORT : BastionBlocks.HUB_CASING).get().defaultBlockState(), 3);
        }
        level.setBlock(source(iface), BastionBlocks.CREATIVE_POWER_SOURCE.get().defaultBlockState(), 3);
        if (tier > 0) {
            SatelliteUplinkBlock satellite = (tier == 3 ? BastionBlocks.SATELLITE_UPLINK_T3 : tier == 2 ? BastionBlocks.SATELLITE_UPLINK_T2
                    : BastionBlocks.SATELLITE_UPLINK_T1).get();
            satellite.placeAt(level, ControlHubStructure.satellite(ControlHubStructure.center(iface, Direction.SOUTH)));
        }
        return (HubInterfaceBlockEntity) level.getBlockEntity(iface);
    }

    /**
     * A floor turret with {@code weapon} (null: none) at {@code health} of its HP, with Creative Ammo unless {@code ammo} is
     * false, holding a connector linked to the T3 hub when {@code connected}.
     */
    private static void turret(ServerLevel level, BlockPos pos, Item weapon, float health, boolean ammo, boolean connected) {
        if (weapon == null) level.setBlock(pos, BastionBlocks.TURRET_BASE.get().defaultBlockState(), 3);
        else Showcase.place(level, pos, Direction.UP, weapon, TurretTier.T2);
        TurretBaseBlockEntity turret = (TurretBaseBlockEntity) level.getBlockEntity(pos);
        if (connected) {
            turret.inventory().setStackInSlot(TurretInventory.CONNECTOR,
                    HubConnectorItem.link(new ItemStack(BastionItems.HUB_CONNECTOR.get()), level, T3_IFACE));
        }
        if (weapon == null) return;
        if (!ammo) turret.inventory().setStackInSlot(TurretInventory.AMMO_START, ItemStack.EMPTY);
        if (health < 1) turret.takeDamage(level.damageSources().generic(), turret.maxHealth() * (1 - health));
    }

    /** Right-clicks the block under the crosshair, as the player would. */
    private static void use(Minecraft mc) {
        if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        }
    }

    /** The {@code index}th side tab left of the panel (ControlHubScreen TAB_X -23, TAB_Y 8, TAB_GAP 26). */
    static void clickTab(Screen screen, int index) {
        if (screen instanceof AbstractContainerScreen<?> gui) gui.mouseClicked(gui.getGuiLeft() - 23 + 10, gui.getGuiTop() + 8 + index * 26 + 10, 0);
    }

    /** The Creative Power Source beside a scene hub's Power Port (see hub()). */
    static BlockPos source(BlockPos iface) {
        return ControlHubStructure.slots(iface, Direction.SOUTH).get(0).west();
    }
}

package dev.bastion.network;

import dev.bastion.Bastion;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Single packet channel for every packet in PLAN 4.8. Turret messages to nearby players are queued and leave once per
 * tick as one TurretEvents packet per player ({@link #sendNear}).
 */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID)
public final class BastionNetwork {
    private static final String PROTOCOL = "8";
    /** Every S->C turret packet only reaches players this close (PLAN 4.8). */
    public static final double BROADCAST_RADIUS = 96;

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            Bastion.id("main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private record Queued(Vec3 pos, Object message) {
    }

    private static final Map<ResourceKey<Level>, List<Queued>> QUEUE = new HashMap<>();

    /** Called from the mod constructor so the channel exists before any connection. */
    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(TurretEvents.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(TurretEvents::encode).decoder(TurretEvents::decode).consumerMainThread(TurretEvents::handle).add();
        CHANNEL.messageBuilder(WeaponDataSync.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(WeaponDataSync::encode).decoder(WeaponDataSync::decode).consumerMainThread(WeaponDataSync::handle).add();
        CHANNEL.messageBuilder(TurretConfigUpdate.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(TurretConfigUpdate::encode).decoder(TurretConfigUpdate::decode).consumerMainThread(TurretConfigUpdate::handle).add();
        CHANNEL.messageBuilder(BurnSync.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(BurnSync::encode).decoder(BurnSync::decode).consumerMainThread(BurnSync::handle).add();
        CHANNEL.messageBuilder(WorkstationAction.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(WorkstationAction::encode).decoder(WorkstationAction::decode).consumerMainThread(WorkstationAction::handle).add();
    }

    /** Queues a turret message (one of TurretEvents' kinds) for players within BROADCAST_RADIUS of {@code pos}. */
    public static void sendNear(ServerLevel level, BlockPos pos, Object message) {
        sendNear(level, Vec3.atCenterOf(pos), message);
    }

    public static void sendNear(ServerLevel level, Vec3 pos, Object message) {
        QUEUE.computeIfAbsent(level.dimension(), k -> new ArrayList<>()).add(new Queued(pos, message));
    }

    /** End of each level tick: everything queued this tick, in order, one packet per player (more past MAX_MESSAGES). */
    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        List<Queued> queued = QUEUE.remove(level.dimension());
        if (queued == null) return;
        double radius = BROADCAST_RADIUS * BROADCAST_RADIUS;
        for (ServerPlayer player : level.players()) {
            List<Object> near = new ArrayList<>();
            for (Queued q : queued) if (q.pos.distanceToSqr(player.position()) < radius) near.add(q.message);
            for (int i = 0; i < near.size(); i += TurretEvents.MAX_MESSAGES) {
                TurretEvents packet = new TurretEvents(List.copyOf(near.subList(i, Math.min(near.size(), i + TurretEvents.MAX_MESSAGES))));
                CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        QUEUE.clear();
    }
}

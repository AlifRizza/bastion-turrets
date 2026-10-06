package dev.bastion.network;

import dev.bastion.Bastion;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Single packet channel for every packet in PLAN 4.8. */
public final class BastionNetwork {
    private static final String PROTOCOL = "6";
    /** Every S->C turret packet only reaches players this close (PLAN 4.8). */
    public static final double BROADCAST_RADIUS = 96;

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            Bastion.id("main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    /** Called from the mod constructor so the channel exists before any connection. */
    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(TurretStateSync.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(TurretStateSync::encode).decoder(TurretStateSync::decode).consumerMainThread(TurretStateSync::handle).add();
        CHANNEL.messageBuilder(WeaponDataSync.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(WeaponDataSync::encode).decoder(WeaponDataSync::decode).consumerMainThread(WeaponDataSync::handle).add();
        CHANNEL.messageBuilder(TurretFireEvent.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(TurretFireEvent::encode).decoder(TurretFireEvent::decode).consumerMainThread(TurretFireEvent::handle).add();
        CHANNEL.messageBuilder(SpinSync.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SpinSync::encode).decoder(SpinSync::decode).consumerMainThread(SpinSync::handle).add();
        CHANNEL.messageBuilder(TurretConfigUpdate.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(TurretConfigUpdate::encode).decoder(TurretConfigUpdate::decode).consumerMainThread(TurretConfigUpdate::handle).add();
        CHANNEL.messageBuilder(TurretImpactEvent.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(TurretImpactEvent::encode).decoder(TurretImpactEvent::decode).consumerMainThread(TurretImpactEvent::handle).add();
        CHANNEL.messageBuilder(RackSync.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(RackSync::encode).decoder(RackSync::decode).consumerMainThread(RackSync::handle).add();
        CHANNEL.messageBuilder(BurnSync.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(BurnSync::encode).decoder(BurnSync::decode).consumerMainThread(BurnSync::handle).add();
        CHANNEL.messageBuilder(WorkstationAction.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(WorkstationAction::encode).decoder(WorkstationAction::decode).consumerMainThread(WorkstationAction::handle).add();
    }

    public static void sendNear(ServerLevel level, BlockPos pos, Object message) {
        CHANNEL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, BROADCAST_RADIUS, level.dimension())), message);
    }
}

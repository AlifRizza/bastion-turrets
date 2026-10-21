package dev.bastion.hub;

import dev.bastion.Bastion;
import dev.bastion.config.BastionConfig;
import dev.bastion.menu.ControlHubMenu;
import dev.bastion.network.BastionNetwork;
import dev.bastion.network.OverwatchStatus;
import dev.bastion.network.OverwatchView;
import dev.bastion.turret.ManualControl;
import dev.bastion.turret.TurretBaseBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Overwatch sessions (PLAN "Overwatch Link"): which player controls which turret through which hub. A session needs what
 * opening the turret through the hub needs (HubInterfaceBlockEntity.reachable: online, in reach, loaded, theirs to open,
 * the player within 8 blocks of the Interface) plus an Overwatch Link, and nobody else at the controls. It is re-checked
 * every tick and ends on any damage to the player and on logout; the client is told when one begins or ends.
 */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID)
public final class OverwatchSessions {
    /** Aim points farther than this from the turret are garbage (or a cheat) and dropped. */
    private static final double MAX_AIM_DISTANCE = 512;
    /** Ticks between the controlled turret's ammo/FE for the HUD. */
    private static final int STATUS_INTERVAL = 10;

    private record Session(ServerPlayer player, ServerLevel level, BlockPos hub, BlockPos turret) {
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private OverwatchSessions() {
    }

    /** The turret the player controls now, or null. */
    @Nullable
    public static BlockPos controlled(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        return session == null ? null : session.turret();
    }

    /** Puts the player at the controls of the turret (leaving any other); returns why not, or null when it started. */
    @Nullable
    public static Component start(ServerPlayer player, BlockPos hubPos, BlockPos turretPos) {
        if (!(player.level().getBlockEntity(hubPos) instanceof HubInterfaceBlockEntity hub)) return Component.translatable("message.bastion.overwatch.unreachable");
        TurretBaseBlockEntity turret = hub.reachable(player, turretPos);
        if (turret == null) return Component.translatable("message.bastion.overwatch.unreachable");
        if (!OverwatchLinkItem.fitted(turret)) return Component.translatable("message.bastion.overwatch.no_link");
        ManualControl current = turret.manual();
        if (current != null && !current.player().equals(player.getUUID())) return Component.translatable("message.bastion.overwatch.taken", current.name());
        end(player, false);
        turret.setManual(new ManualControl(player.getUUID(), player.getGameProfile().getName()));
        SESSIONS.put(player.getUUID(), new Session(player, (ServerLevel) player.level(), hubPos.immutable(), turretPos.immutable()));
        if (player.containerMenu instanceof ControlHubMenu) player.closeContainer();
        send(player, new OverwatchView(turretPos.immutable(), hubPos.immutable(), BastionConfig.overwatchRange()));
        send(player, new OverwatchStatus(ControlHubMenu.Entry.of(turret)));
        return null;
    }

    /** Lets go of the turret; the client's view goes back to the player. */
    public static void stop(ServerPlayer player) {
        end(player, true);
    }

    private static void end(ServerPlayer player, boolean tell) {
        Session session = SESSIONS.remove(player.getUUID());
        if (session == null) return;
        if (session.level().getBlockEntity(session.turret()) instanceof TurretBaseBlockEntity turret && turret.manual() != null
                && turret.manual().player().equals(player.getUUID())) {
            turret.setManual(null);
        }
        if (tell) send(player, OverwatchView.NONE);
    }

    /** ← / →: the next (step 1) or previous (-1) Overwatch turret on the same hub that nobody else controls, wrapping round. */
    public static void cycle(ServerPlayer player, int step) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !(session.level().getBlockEntity(session.hub()) instanceof HubInterfaceBlockEntity hub)) return;
        List<BlockPos> choices = new ArrayList<>();
        for (TurretBaseBlockEntity turret : hub.visibleTurrets(player)) {
            ManualControl control = turret.manual();
            if (OverwatchLinkItem.fitted(turret) && (control == null || control.player().equals(player.getUUID()))) choices.add(turret.getBlockPos());
        }
        int at = choices.indexOf(session.turret());
        if (choices.size() < 2 || at < 0) return;
        Component refusal = start(player, session.hub(), choices.get(Math.floorMod(at + step, choices.size())));
        if (refusal != null) player.displayClientMessage(refusal, true);
    }

    /** One tick of the controller's input, for the turret of their session only; NaN and far-off points are dropped. */
    public static void input(ServerPlayer player, BlockPos turretPos, Vec3 point, int entity, boolean trigger) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !session.turret().equals(turretPos)) return;
        if (!Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z)
                || point.distanceToSqr(Vec3.atCenterOf(turretPos)) > MAX_AIM_DISTANCE * MAX_AIM_DISTANCE) return;
        if (session.level().getBlockEntity(turretPos) instanceof TurretBaseBlockEntity turret && turret.manual() != null
                && turret.manual().player().equals(player.getUUID())) {
            turret.manual().input(point, entity, trigger, session.level().getGameTime());
        }
    }

    /** The session's turret while it may still be controlled, else null. */
    @Nullable
    private static TurretBaseBlockEntity valid(Session session) {
        ServerPlayer player = session.player();
        if (player.isRemoved() || !player.isAlive() || player.level() != session.level()) return null;
        if (!(session.level().getBlockEntity(session.hub()) instanceof HubInterfaceBlockEntity hub)) return null;
        TurretBaseBlockEntity turret = hub.reachable(player, session.turret());
        if (turret == null || !OverwatchLinkItem.fitted(turret)) return null;
        ManualControl control = turret.manual();
        return control != null && control.player().equals(player.getUUID()) ? turret : null;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || SESSIONS.isEmpty()) return;
        for (Session session : List.copyOf(SESSIONS.values())) {
            TurretBaseBlockEntity turret = valid(session);
            if (turret == null) end(session.player(), true);
            else if (session.level().getGameTime() % STATUS_INTERVAL == 0) send(session.player(), new OverwatchStatus(ControlHubMenu.Entry.of(turret)));
        }
    }

    /**
     * Any hit on the player's body at the hub ends the control (user 2026-10-20): checked before armor and absorption, so a
     * hit they fully soak still counts (review 2026-10-20).
     */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getAmount() > 0) end(player, true);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) end(player, false);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        SESSIONS.clear();
    }

    /** GameTest fake players have no client to tell. */
    private static void send(ServerPlayer player, Object message) {
        if (!(player instanceof FakePlayer)) BastionNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }
}

package dev.bastion.hub;

import dev.bastion.Bastion;
import dev.bastion.config.BastionConfig;
import dev.bastion.menu.ControlHubMenu;
import dev.bastion.network.BastionNetwork;
import dev.bastion.network.OverwatchLocks;
import dev.bastion.network.OverwatchStatus;
import dev.bastion.network.OverwatchView;
import dev.bastion.turret.ManualControl;
import dev.bastion.turret.TurretBaseBlockEntity;
import dev.bastion.weapon.WeaponData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
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
    /** Ticks between two ←/→ or two R requests a player's session takes (held keys repeat; a modified client could spam). */
    public static final int REQUEST_TICKS = 4;
    /** Ticks between the controlled turret's ammo/FE for the HUD. */
    private static final int STATUS_INTERVAL = 10;

    private record Session(ServerPlayer player, ServerLevel level, BlockPos hub, BlockPos turret, ManualControl control) {
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    /** The game tick of each player's last ←/→ and last R that went through (REQUEST_TICKS apart). */
    private static final Map<UUID, Long> LAST_CYCLE = new HashMap<>(), LAST_MODE = new HashMap<>();

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
        ManualControl control = new ManualControl(player.getUUID(), player.getGameProfile().getName());
        turret.setManual(control);
        SESSIONS.put(player.getUUID(), new Session(player, (ServerLevel) player.level(), hubPos.immutable(), turretPos.immutable(), control));
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
        session.control().release(); // a turret out of reach now lets go when it ticks again
        // An unloaded turret is left alone (its control is never saved): looking it up would load its chunk.
        if (session.level().isLoaded(session.turret()) && session.level().getBlockEntity(session.turret()) instanceof TurretBaseBlockEntity turret && turret.manual() != null
                && turret.manual().player().equals(player.getUUID())) {
            turret.setManual(null);
        }
        if (tell) send(player, OverwatchView.NONE);
    }

    /** ← / →: the next (step 1) or previous (-1) Overwatch turret on the same hub that nobody else controls, wrapping round. */
    public static void cycle(ServerPlayer player, int step) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !(session.level().getBlockEntity(session.hub()) instanceof HubInterfaceBlockEntity hub) || tooSoon(LAST_CYCLE, player)) return;
        List<BlockPos> choices = new ArrayList<>();
        for (TurretBaseBlockEntity turret : hub.visibleTurrets(player)) {
            ManualControl control = turret.manual();
            // Only turrets the player's client has: one outside its chunks would end the session at once (a hub reach
            // enlarged by config can pass the render distance).
            if (OverwatchLinkItem.fitted(turret) && (control == null || control.player().equals(player.getUUID()))
                    && (player instanceof FakePlayer || sentTo(player, turret.getBlockPos()))) {
                choices.add(turret.getBlockPos());
            }
        }
        int at = choices.indexOf(session.turret());
        if (choices.size() < 2 || at < 0) return;
        Component refusal = start(player, session.hub(), choices.get(Math.floorMod(at + step, choices.size())));
        if (refusal != null) player.displayClientMessage(refusal, true);
    }

    /**
     * R: flips the controlled launcher's Single/Salvo setting, the turret's own (kept after the session, like the button in
     * its GUI); weapons without a salvo mode ignore it. Clients get it through the update tag (the HUD shows the mode).
     */
    public static void toggleMode(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        TurretBaseBlockEntity turret = session == null ? null : valid(session);
        WeaponData data = turret == null ? null : turret.inventory().weaponData();
        if (data == null || !data.params().containsKey("salvo_interval") || tooSoon(LAST_MODE, player)) return;
        turret.setSalvo(!turret.salvo());
        session.level().sendBlockUpdated(turret.getBlockPos(), turret.getBlockState(), turret.getBlockState(), Block.UPDATE_CLIENTS);
    }

    /** One tick of the controller's input, for the turret of their session only; NaN and far-off points are dropped. */
    public static void input(ServerPlayer player, BlockPos turretPos, Vec3 point, int entity, boolean trigger) {
        input(player, turretPos, point, entity, trigger, 0, 0);
    }

    /** As above, with the attack and use presses since the last input. */
    public static void input(ServerPlayer player, BlockPos turretPos, Vec3 point, int entity, boolean trigger, int clicks, int unlocks) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !session.turret().equals(turretPos)) return;
        if (!Double.isFinite(point.x) || !Double.isFinite(point.y) || !Double.isFinite(point.z)
                || point.distanceToSqr(Vec3.atCenterOf(turretPos)) > MAX_AIM_DISTANCE * MAX_AIM_DISTANCE) return;
        if (session.level().getBlockEntity(turretPos) instanceof TurretBaseBlockEntity turret && turret.manual() != null
                && turret.manual().player().equals(player.getUUID())) {
            turret.manual().input(point, entity, trigger, clicks, unlocks, session.level().getGameTime());
        }
    }

    /** True (and nothing recorded) when the player's last such request went through under REQUEST_TICKS ago. */
    private static boolean tooSoon(Map<UUID, Long> last, ServerPlayer player) {
        long now = player.level().getGameTime();
        Long before = last.get(player.getUUID());
        if (before != null && now - before >= 0 && now - before < REQUEST_TICKS) return true;
        last.put(player.getUUID(), now);
        return false;
    }

    /** Whether the server sends the chunk at {@code pos} to the player's client. */
    private static boolean sentTo(ServerPlayer player, BlockPos pos) {
        return player.serverLevel().getChunkSource().chunkMap.getPlayers(new ChunkPos(pos), false).contains(player);
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
            if (turret == null) {
                end(session.player(), true);
                continue;
            }
            if (session.level().getGameTime() % STATUS_INTERVAL == 0) send(session.player(), new OverwatchStatus(ControlHubMenu.Entry.of(turret)));
            if (turret.manual().takeLocksChanged()) send(session.player(), new OverwatchLocks(turret.manual().lockIds()));
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
        if (event.getEntity() instanceof ServerPlayer player) {
            end(player, false);
            LAST_CYCLE.remove(player.getUUID());
            LAST_MODE.remove(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        SESSIONS.clear();
        LAST_CYCLE.clear();
        LAST_MODE.clear();
    }

    /** GameTest fake players have no client to tell. */
    private static void send(ServerPlayer player, Object message) {
        if (!(player instanceof FakePlayer)) BastionNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }
}

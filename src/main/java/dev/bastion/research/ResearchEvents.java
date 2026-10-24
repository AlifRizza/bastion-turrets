package dev.bastion.research;

import dev.bastion.Bastion;
import dev.bastion.config.ResearchConfig;
import dev.bastion.network.BastionNetwork;
import dev.bastion.network.ResearchSync;
import dev.bastion.registry.BastionItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.network.PacketDistributor;

/** Server side of research (PLAN 8 "Riset & Turret Codex"): the world's data at start, a login's sync and Codex, the command. */
@Mod.EventBusSubscriber(modid = Bastion.MOD_ID)
public final class ResearchEvents {
    private static final String CODEX_GIVEN = "bastion_codex_given";

    private ResearchEvents() {
    }

    /** Before the first tick, so a new world is seen at game time 0 (ResearchData.initial). */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        ResearchData.get(event.getServer());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        BastionNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), ResearchSync.current(player.server));
        giveCodexOnce(player);
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        ResearchCommand.register(event.getDispatcher());
    }

    /** One Turret Codex per player, the first time they join with research on (a flag kept through death). */
    public static void giveCodexOnce(ServerPlayer player) {
        if (!ResearchConfig.enabled() || !ResearchConfig.giveCodex()) return;
        CompoundTag kept = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        if (kept.getBoolean(CODEX_GIVEN)) return;
        ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(BastionItems.TURRET_CODEX.get()));
        kept.putBoolean(CODEX_GIVEN, true);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, kept);
    }
}

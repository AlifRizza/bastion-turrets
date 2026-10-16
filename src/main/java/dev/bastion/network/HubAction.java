package dev.bastion.network;

import dev.bastion.hub.HubEdit;
import dev.bastion.menu.ControlHubMenu;
import dev.bastion.menu.TurretMenu;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

/**
 * C->S from the Control Hub screen (PLAN "Turret Control Hub"): open a turret's GUI through the hub, go back to the hub
 * from such a GUI, or apply one batch change to the selected turrets. Only for the hub or turret GUI the sender has open,
 * re-checked on the server.
 */
public record HubAction(Kind kind, List<BlockPos> turrets, @Nullable HubEdit edit) {
    public enum Kind {OPEN, BACK, EDIT}

    public static HubAction open(BlockPos turret) {
        return new HubAction(Kind.OPEN, List.of(turret), null);
    }

    public static HubAction back() {
        return new HubAction(Kind.BACK, List.of(), null);
    }

    public static HubAction edit(Collection<BlockPos> turrets, HubEdit edit) {
        return new HubAction(Kind.EDIT, List.copyOf(turrets), edit);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(kind);
        buf.writeCollection(turrets, FriendlyByteBuf::writeBlockPos);
        buf.writeNullable(edit, (b, e) -> e.write(b));
    }

    public static HubAction decode(FriendlyByteBuf buf) {
        Kind kind = buf.readEnum(Kind.class);
        int count = buf.readVarInt();
        if (count > ControlHubMenu.MAX_TURRETS) throw new DecoderException("too many turrets: " + count);
        List<BlockPos> turrets = new ArrayList<>(count);
        for (int i = 0; i < count; i++) turrets.add(buf.readBlockPos());
        return new HubAction(kind, turrets, buf.readNullable(HubEdit::read));
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null) {
            switch (kind) {
                case OPEN -> {
                    if (player.containerMenu instanceof ControlHubMenu menu && menu.stillValid(player) && !turrets.isEmpty()) menu.openTurret(turrets.get(0));
                }
                case BACK -> {
                    if (player.containerMenu instanceof TurretMenu menu && menu.hub() != null) ControlHubMenu.reopen(player, menu.hub());
                }
                case EDIT -> {
                    if (player.containerMenu instanceof ControlHubMenu menu && menu.stillValid(player) && edit != null) menu.edit(turrets, edit);
                }
            }
        }
        context.get().setPacketHandled(true);
    }
}

package dev.bastion.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * S->C every turret message one player gets in a tick, in order (BastionNetwork#sendNear queues them): with hundreds of
 * turrets that is thousands of small packets a second otherwise, each with its own header and flush.
 */
public record TurretEvents(List<Object> messages) {
    /** Most messages per packet; a busier tick sends several. */
    static final int MAX_MESSAGES = 512;

    private record Kind<T>(Class<T> type, BiConsumer<T, FriendlyByteBuf> encoder, Function<FriendlyByteBuf, T> decoder,
                           BiConsumer<T, Supplier<NetworkEvent.Context>> handler) {
    }

    private static final List<Kind<?>> KINDS = List.of(
            new Kind<>(TurretStateSync.class, TurretStateSync::encode, TurretStateSync::decode, TurretStateSync::handle),
            new Kind<>(TurretFireEvent.class, TurretFireEvent::encode, TurretFireEvent::decode, TurretFireEvent::handle),
            new Kind<>(BulletImpact.class, BulletImpact::encode, BulletImpact::decode, BulletImpact::handle),
            new Kind<>(SpinSync.class, SpinSync::encode, SpinSync::decode, SpinSync::handle),
            new Kind<>(RackSync.class, RackSync::encode, RackSync::decode, RackSync::handle),
            new Kind<>(TurretImpactEvent.class, TurretImpactEvent::encode, TurretImpactEvent::decode, TurretImpactEvent::handle));

    @SuppressWarnings("unchecked")
    private static Kind<Object> kind(int index) {
        return (Kind<Object>) KINDS.get(index);
    }

    private static int indexOf(Object message) {
        for (int i = 0; i < KINDS.size(); i++) if (KINDS.get(i).type == message.getClass()) return i;
        throw new IllegalArgumentException("not a turret event: " + message.getClass());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(messages.size());
        for (Object message : messages) {
            int index = indexOf(message);
            buf.writeByte(index);
            kind(index).encoder.accept(message, buf);
        }
    }

    public static TurretEvents decode(FriendlyByteBuf buf) {
        int count = Math.min(buf.readVarInt(), MAX_MESSAGES);
        List<Object> messages = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int index = buf.readByte();
            if (index < 0 || index >= KINDS.size()) break; // garbage: drop the rest
            messages.add(kind(index).decoder.apply(buf));
        }
        return new TurretEvents(messages);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        for (Object message : messages) kind(indexOf(message)).handler.accept(message, context);
        context.get().setPacketHandled(true);
    }
}

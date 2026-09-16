package carpet.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public class CarpetClient
{
    public record CarpetPayload(CompoundTag data) implements CustomPacketPayload
    {
        public static final StreamCodec<FriendlyByteBuf, CarpetPayload> STREAM_CODEC = CustomPacketPayload.codec(CarpetPayload::write, CarpetPayload::new);

        public static final Type<CarpetPayload> TYPE = new CustomPacketPayload.Type<>(CARPET_CHANNEL);

        public CarpetPayload(FriendlyByteBuf input)
        {
            this(input.readNbt());
        }

        public void write(FriendlyByteBuf output)
        {
            output.writeNbt(data);
        }

        @Override public Type<CarpetPayload> type()
        {
            return TYPE;
        }
    }

    public static final String HI = "69";
    public static final String HELLO = "420";

    public static final Identifier CARPET_CHANNEL = Identifier.fromNamespaceAndPath("carpet", "hello");
}

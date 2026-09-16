package carpet.patches;

import org.jspecify.annotations.Nullable;

import carpet.fakes.ClientConnectionInterface;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.ProtocolInfo;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;

public class FakeClientConnection extends Connection
{
    public FakeClientConnection(PacketFlow p)
    {
        super(p);

        ((ClientConnectionInterface)this).setChannel(new EmbeddedChannel());
    }

    @Override
    public void setReadOnly()
    {
    }

    @Override
    public void send(Packet<?> packet, @Nullable ChannelFutureListener channelFutureListener, boolean bl)
    {
    }

    @Override
    public void handleDisconnection()
    {
    }

    @Override
    public void setListenerForServerboundHandshake(PacketListener packetListener)
    {
    }

    @Override
    public <T extends PacketListener> void setupInboundProtocol(ProtocolInfo<T> protocolInfo, T packetListener)
    {
    }
}

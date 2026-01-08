package tf.tuff.viablocks;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import org.bukkit.entity.Player;

public class ChunkDataHandler extends ChannelOutboundHandlerAdapter {

    private final CustomBlockListener blockListener;
    private final Player player;

    public ChunkDataHandler(CustomBlockListener blockListener, Player player) {
        this.blockListener = blockListener;
        this.player = player;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) msg;
            buf.markReaderIndex();
            
            try {
                int packetId = readVarInt(buf);
                
                if (packetId == 0x20) {
                    int x = buf.readInt();
                    int z = buf.readInt();
                    
                    byte[] extraData = blockListener.getExtraDataForChunk(player.getWorld().getName(), x, z); 
                    
                    if (extraData != null && extraData.length > 0) {
                        buf.resetReaderIndex(); 
                        
                        ByteBuf tail = ctx.alloc().buffer();
                        tail.writeBytes(extraData);
                        
                        CompositeByteBuf composite = ctx.alloc().compositeBuffer();
                        composite.addComponents(true, buf.retain(), tail);
                        
                        super.write(ctx, composite, promise);
                        return;
                    }
                }
            } catch (Exception e) {
            } finally {
                 if (buf.refCnt() > 0 && msg == buf) {
                     buf.resetReaderIndex();
                 }
            }
        }
        super.write(ctx, msg, promise);
    }

    private void writeVarInt(ByteBuf buf, int value) {
        while ((value & -128) != 0) {
            buf.writeByte(value & 127 | 128);
            value >>>= 7;
        }
        buf.writeByte(value);
    }

    private int readVarInt(ByteBuf buf) {
        int numRead = 0;
        int result = 0;
        byte read;
        do {
            read = buf.readByte();
            int value = (read & 0b01111111);
            result |= (value << (7 * numRead));
            numRead++;
            if (numRead > 5) throw new RuntimeException("VarInt is too big");
        } while ((read & 0b10000000) != 0);
        return result;
    }
}
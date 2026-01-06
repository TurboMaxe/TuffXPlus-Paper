package tf.tuff.viablocks;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;

public class ChunkDataHandler extends ChannelOutboundHandlerAdapter {

    private final CustomBlockListener blockListener;

    public ChunkDataHandler(CustomBlockListener blockListener) {
        this.blockListener = blockListener;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof ByteBuf) {
            ByteBuf buf = (ByteBuf) msg;
            if (buf.readableBytes() < 2) {
                super.write(ctx, msg, promise);
                return;
            }

            buf.markReaderIndex();
            try {
                int packetLength = read(buf);
                int packetId = read(buf);   

                if (packetId == 0x20 || packetId == 0x27) { 
                    int chunkX = buf.readInt();
                    int chunkZ = buf.readInt();
                    
                    blockListener.plugin.plugin.getLogger().info("DEBUG: Found Chunk Packet at " + chunkX + "," + chunkZ);
                }
            } catch (Exception e) {

            } finally {
                buf.resetReaderIndex();
            }
        }
        super.write(ctx, msg, promise);
    }

    private int read(ByteBuf buf) {
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
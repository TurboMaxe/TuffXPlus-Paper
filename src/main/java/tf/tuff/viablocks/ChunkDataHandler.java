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
            buf.markReaderIndex();

            try {
                int originalLength = readVarInt(buf); 
                int lengthFieldSize = buf.readerIndex(); // How many bytes the length VarInt took
                int packetId = readVarInt(buf);

                if (packetId == 0x20 || packetId == 0x27) {
                    int chunkX = buf.readInt();
                    int chunkZ = buf.readInt();

                    byte[] customData = blockListener.getCachedChunkData(chunkX, chunkZ);
                    if (customData != null && customData.length > 0) {
                        
                        int bodyLength = buf.readableBytes() + (buf.readerIndex() - lengthFieldSize) + customData.length;
                        
                        ByteBuf newPacket = ctx.alloc().buffer();
                        
                        writeVarInt(newPacket, bodyLength);
                        
                        buf.resetReaderIndex();
                        readVarInt(buf); 
                        newPacket.writeBytes(buf);
                        

                        newPacket.writeBytes(customData);

                        buf.release();
                        ctx.write(newPacket, promise);
                        return;
                    }
                }
            } catch (Exception e) {
            } finally {
                buf.resetReaderIndex();
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
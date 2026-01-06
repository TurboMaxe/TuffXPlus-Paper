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
                int packetId = readVarInt(buf);
                
                StringBuilder hex = new StringBuilder();
                int bytesToRead = Math.min(buf.readableBytes(), 32);
                for (int i = 0; i < bytesToRead; i++) {
                    hex.append(String.format("%02X ", buf.getByte(buf.readerIndex() + i)));
                }

                blockListener.plugin.plugin.getLogger().info(
                    String.format("DEBUG | ID: 0x%02X | Readable: %d | Hex: %s", 
                    packetId, buf.readableBytes(), hex.toString())
                );

                if (packetId == 0x20 || packetId == 0x27) {
                    if (buf.readableBytes() >= 8) {
                        int x = buf.readInt();
                        int z = buf.readInt();
                        blockListener.plugin.plugin.getLogger().info("CHUNK FOUND AT: " + x + ", " + z);
                    }
                }
            } catch (Exception e) {
                blockListener.plugin.plugin.getLogger().warning("Failed to parse packet: " + e.getMessage());
            } finally {
                buf.resetReaderIndex();
            }
        } else {
            blockListener.plugin.plugin.getLogger().info("Non-ByteBuf Msg: " + msg.getClass().getName());
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
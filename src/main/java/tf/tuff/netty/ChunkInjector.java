package tf.tuff.netty;

import com.viaversion.viaversion.api.Via;
import io.netty.channel.Channel;
import org.bukkit.entity.Player;
import tf.tuff.viablocks.CustomBlockListener;
import tf.tuff.y0.Y0Plugin;

import java.util.UUID;

public class ChunkInjector {

    private final CustomBlockListener viaBlocks;
    private final Y0Plugin y0;

    public ChunkInjector(CustomBlockListener viaBlocks, Y0Plugin y0) {
        this.viaBlocks = viaBlocks;
        this.y0 = y0;
    }

    public void inject(Player player) {
        UUID uuid = player.getUniqueId();
        var viaConnection = Via.getAPI().getConnection(uuid);
        if (viaConnection == null) return;

        Channel channel = viaConnection.getChannel();
        if (channel == null) return;

        channel.eventLoop().submit(() -> {
            try {
                if (channel.pipeline().get("tuff_chunk_handler") != null) {
                    channel.pipeline().remove("tuff_chunk_handler");
                }
                if (channel.pipeline().get("viablocks_chunk_handler") != null) {
                    channel.pipeline().remove("viablocks_chunk_handler");
                }
                if (channel.pipeline().get("y0_chunk_handler") != null) {
                    channel.pipeline().remove("y0_chunk_handler");
                }

                if (channel.pipeline().get("via-encoder") != null) {
                    channel.pipeline().addBefore(
                        "via-encoder",
                        "tuff_chunk_handler",
                        new ChunkHandler(viaBlocks, y0, player)
                    );
                } else {
                    channel.pipeline().addFirst("tuff_chunk_handler",
                        new ChunkHandler(viaBlocks, y0, player));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void eject(Player player) {
        UUID uuid = player.getUniqueId();
        var viaConnection = Via.getAPI().getConnection(uuid);
        if (viaConnection == null) return;

        Channel channel = viaConnection.getChannel();
        if (channel != null && channel.isOpen()) {
            channel.eventLoop().submit(() -> {
                try {
                    if (channel.pipeline().get("tuff_chunk_handler") != null) {
                        channel.pipeline().remove("tuff_chunk_handler");
                    }
                } catch (Exception e) {
                }
            });
        }
    }
}

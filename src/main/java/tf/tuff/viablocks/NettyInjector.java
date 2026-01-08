package tf.tuff.viablocks;

import com.viaversion.viaversion.api.Via;
import io.netty.channel.Channel;
import org.bukkit.entity.Player;
import java.util.UUID;

public class NettyInjector {

    private final CustomBlockListener blockListener;

    public NettyInjector(CustomBlockListener blockListener) {
        this.blockListener = blockListener;
    }

    public void inject(Player player) {
        UUID uuid = player.getUniqueId();

        var viaConnection = Via.getAPI().getConnection(uuid);
        if (viaConnection == null) return;

        Channel channel = viaConnection.getChannel();
        if (channel == null) return;

        channel.eventLoop().submit(() -> {
            if (channel.pipeline().get("viablocks_chunk_handler") != null) {
                channel.pipeline().remove("viablocks_chunk_handler");
            }

            if (channel.pipeline().get("via-encoder") != null) {
                channel.pipeline().addBefore(
                    "via-encoder", 
                    "viablocks_chunk_handler", 
                    new ChunkDataHandler(blockListener, player)
                );
            } else {
                blockListener.plugin.plugin.getLogger().info("Failed to find via-encoder! This plugin may not work correctly.");
            } 
        });
    }

    public void eject(Player player) {
        UUID uuid = player.getUniqueId();
        var viaConnection = Via.getAPI().getConnection(uuid);
        if (viaConnection == null) return;

        Channel channel = viaConnection.getChannel();
        if (channel != null) {
            channel.eventLoop().submit(() -> {
                if (channel.pipeline().get("viablocks_chunk_handler") != null) {
                    channel.pipeline().remove("viablocks_chunk_handler");
                }
            });
        }
    }
}
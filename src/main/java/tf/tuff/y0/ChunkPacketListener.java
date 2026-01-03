package tf.tuff.y0;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;

public class ChunkPacketListener {

    private final Y0Plugin plugin;

    public ChunkPacketListener(Y0Plugin plugin) {
        this.plugin = plugin;
    }

    public void handleChunk(TuffX plugin, Player player, World world, int chunkX, int chunkZ){
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && world.isChunkLoaded(chunkX, chunkZ)) {
                Chunk chunk = world.getChunkAt(chunkX, chunkZ);
                plugin.processAndSendChunk(player, chunk);
            }
        });
    }
}

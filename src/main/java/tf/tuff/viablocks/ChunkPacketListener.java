package tf.tuff.viablocks;

import tf.tuff.TuffX;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;
import java.util.UUID;

public class ChunkPacketListener {

    private static boolean initialized = false;
    public final ViaBlocksPlugin plugin;

    public ChunkPacketListener(ViaBlocksPlugin plugin) {
        this.plugin = plugin;
    }

    public static void initialize(ViaBlocksPlugin plugin) {
        if (initialized) {
            return;
        }

        initialized = true;
    }

    public void handleChunk(TuffX plugin, Player player, World world, int chunkX, int chunkZ){
    plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!plugin.isPlayerEnabled(player)) {
                return;
            }
            if (world.isChunkLoaded(chunkX, chunkZ)) {
                Chunk chunk = world.getChunkAt(chunkX, chunkZ);
                plugin.getBlockListener().processChunkForSinglePlayer(chunk, player);
            }
        });
    }
}

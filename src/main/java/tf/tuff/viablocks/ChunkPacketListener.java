package tf.tuff.viablocks;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;
import java.util.UUID;

public class ChunkPacketListener {

    private static boolean initialized = false;
    private final ViaBlocksPlugin plugin;

    private ChunkPacketListener(ViaBlocksPlugin plugin) {
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
            Player livePlayer = plugin.getServer().getPlayer(playerId);
            if (livePlayer == null || !livePlayer.isOnline()) {
                return;
            }
            if (!plugin.isPlayerEnabled(livePlayer)) {
                return;
            }
            World world = livePlayer.getWorld();
            if (world.isChunkLoaded(chunkX, chunkZ)) {
                Chunk chunk = world.getChunkAt(chunkX, chunkZ);
                plugin.getBlockListener().processChunkForSinglePlayer(chunk, livePlayer);
            }
        });
    }
}

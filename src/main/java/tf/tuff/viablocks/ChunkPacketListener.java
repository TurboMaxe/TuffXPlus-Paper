package tf.tuff.viablocks;

import tf.tuff.TuffX;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;
import java.util.UUID;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.bukkit.scheduler.BukkitRunnable;

public class ChunkPacketListener {

    private static boolean initialized = false;
    public final ViaBlocksPlugin plugin;

    private final Queue<ChunkRequest> pendingRequests = new ConcurrentLinkedQueue<>();
    private BukkitRunnable processorTask;

    public ChunkPacketListener(ViaBlocksPlugin plugin) {
        this.plugin = plugin;
    }

    private static class ChunkRequest {
        final Player player;
        final World world;
        final int x, z;
        ChunkRequest(Player p, World w, int x, int z) { 
            this.player = p; this.world = w; this.x = x; this.z = z; 
        }
    }

    private void startProcessor() {
        processorTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!plugin.plugin.isEnabled()) {
                    this.cancel();
                    return;
                }

                int processed = 0;
                while (processed < 50 && !pendingRequests.isEmpty()) {
                    ChunkRequest req = pendingRequests.poll();
                    if (req == null) break;
                    
                    Player p = req.player;
                    if (p == null || !p.isOnline()) continue;

                    if (req.world.isChunkLoaded(req.x, req.z)) {
                        Chunk chunk = req.world.getChunkAt(req.x, req.z);
                        plugin.chunkSenderManager.addChunkToQueue(p, chunk);
                    }
                    processed++;
                }
            }
        };
        processorTask.runTaskTimer(plugin.plugin, 1L, 1L);
    }

    public void stop() {
        if (processorTask != null && !processorTask.isCancelled()) {
            processorTask.cancel();
        }
        pendingRequests.clear();
    }

    public static void initialize(ViaBlocksPlugin plugin) {
        if (initialized) {
            return;
        }

        initialized = true;
    }

    public void handleChunk(TuffX mainPlugin, Player player, World world, int chunkX, int chunkZ) {
        if (!plugin.isPlayerEnabled(player)) {
            return;
        }
        pendingRequests.add(new ChunkRequest(player, world, chunkX, chunkZ));
    }
}

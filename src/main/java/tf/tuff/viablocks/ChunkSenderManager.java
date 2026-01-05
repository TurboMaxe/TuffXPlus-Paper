package tf.tuff.viablocks;

import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Map;

public class ChunkSenderManager {
    private final ViaBlocksPlugin plugin;
    private final Map<UUID, Queue<Chunk>> playerChunkQueues = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitRunnable> playerTasks = new ConcurrentHashMap<>();
    private long intervalTicks;
    private int chunksPerTick;

    public ChunkSenderManager(ViaBlocksPlugin plugin, long intervalTicks, int chunksPerTick) {
        this.plugin = plugin;
        this.intervalTicks = Math.max(1L, intervalTicks);
        this.chunksPerTick = Math.max(1, chunksPerTick);
    }

    public void addChunksToQueue(Player player, List<Chunk> chunks) {
        if (!plugin.isPlayerEnabled(player)) return;
        Queue<Chunk> queue = playerChunkQueues.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentLinkedQueue<>());
        queue.addAll(chunks);
        startTaskForPlayer(player);
    }

    public void updateSettings(long intervalTicks, int chunksPerTick) {
        this.intervalTicks = Math.max(1L, intervalTicks);
        this.chunksPerTick = Math.max(1, chunksPerTick);
        
        for (UUID playerId : playerTasks.keySet()) {
             cancelTask(playerId);
             Player p = plugin.plugin.getServer().getPlayer(playerId);
             if (p != null) startTaskForPlayer(p);
        }
    }


    public void addChunkToQueue(Player player, Chunk chunk) {
        if (!plugin.isPlayerEnabled(player)) return;
        Queue<Chunk> queue = playerChunkQueues.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentLinkedQueue<>());
        queue.add(chunk);
        startTaskForPlayer(player);
    }

    private void startTaskForPlayer(Player player) {
        UUID playerId = player.getUniqueId();
        if (playerTasks.containsKey(playerId)) {
            return;
        }

        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                Player currentPlayer = plugin.plugin.getServer().getPlayer(playerId);
                if (currentPlayer == null || !currentPlayer.isOnline()) {
                    cancelTask(playerId);
                    return;
                }

                Queue<Chunk> queue = playerChunkQueues.get(playerId);
                if (queue == null || queue.isEmpty()) {
                    cancelTask(playerId);
                    return;
                }

                int processed = 0;
                while (processed < chunksPerTick) {
                    Chunk chunkToProcess = queue.poll();
                    if (chunkToProcess == null) {
                        cancelTask(playerId);
                        return;
                    }
                    if (chunkToProcess.isLoaded()) {
                        plugin.getBlockListener().processChunkForSinglePlayer(chunkToProcess, currentPlayer);
                    }
                    processed++;
                }

                if (queue.isEmpty()) {
                    cancelTask(playerId);
                }
            }
        };
        
        task.runTaskTimer(plugin.plugin, 1L, intervalTicks);
        playerTasks.put(playerId, task);
    }

    private void cancelTask(UUID playerId) {
        BukkitRunnable task = playerTasks.remove(playerId);
        if (task != null) {
            try {
                task.cancel();
            } catch (IllegalStateException e) {
            }
        }
    }

    public void onPlayerQuit(Player player) {
        cancelTask(player.getUniqueId());
    }
}

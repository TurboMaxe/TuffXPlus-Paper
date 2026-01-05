package tf.tuff.viablocks;

import org.bukkit.Chunk;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ChunkSenderManager {
    private final ViaBlocksPlugin plugin;
    
    // Map: PlayerUUID -> Queue of chunks
    private final Map<UUID, Queue<Chunk>> playerChunkQueues = new ConcurrentHashMap<>();
    private final Map<UUID, Set<Long>> playerPendingKeys = new ConcurrentHashMap<>();
    
    // Queue of players to process in round-robin fashion
    private final Queue<UUID> activePlayers = new ConcurrentLinkedQueue<>();
    private final Set<UUID> activePlayersSet = Collections.synchronizedSet(new HashSet<>());

    private BukkitRunnable globalTask;
    private long intervalTicks;
    private int globalChunksPerTick; 

    public ChunkSenderManager(ViaBlocksPlugin plugin, long intervalTicks, int chunksPerTick) {
        this.plugin = plugin;
        this.intervalTicks = Math.max(1L, intervalTicks);
        this.globalChunksPerTick = Math.max(1, chunksPerTick); 
        startGlobalTask();
    }

    public void addChunkToQueue(Player player, Chunk chunk) {
        addInternal(player, chunk);
    }
    
    public void addChunksToQueue(Player player, List<Chunk> chunks) {
        if (!plugin.isPlayerEnabled(player)) return;
        UUID uuid = player.getUniqueId();
        
        Queue<Chunk> queue = getQueue(uuid);
        Set<Long> pending = getPending(uuid);
        
        boolean addedAny = false;
        synchronized (pending) {
            for (Chunk chunk : chunks) {
                long key = getChunkKey(chunk);
                if (pending.add(key)) {
                    queue.add(chunk);
                    addedAny = true;
                }
            }
        }
        
        if (addedAny) activatePlayer(uuid);
    }

    private void addInternal(Player player, Chunk chunk) {
        if (!plugin.isPlayerEnabled(player)) return;
        UUID uuid = player.getUniqueId();
        
        Queue<Chunk> queue = getQueue(uuid);
        Set<Long> pending = getPending(uuid);
        
        long key = getChunkKey(chunk);
        boolean added;
        synchronized (pending) {
            added = pending.add(key);
        }
        
        if (added) {
            queue.add(chunk);
            activatePlayer(uuid);
        }
    }

    private void activatePlayer(UUID uuid) {
        if (activePlayersSet.add(uuid)) {
            activePlayers.add(uuid);
        }
    }

    private void startGlobalTask() {
        globalTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!plugin.plugin.isEnabled() || activePlayers.isEmpty()) return;

                int processedCount = 0;

                while (processedCount < globalChunksPerTick) {
                    UUID playerId = activePlayers.poll();
                    if (playerId == null) break;

                    Player player = plugin.plugin.getServer().getPlayer(playerId);
                    Queue<Chunk> queue = playerChunkQueues.get(playerId);

                    if (player == null || !player.isOnline() || queue == null || queue.isEmpty()) {
                        activePlayersSet.remove(playerId);
                        cleanupPlayer(playerId);
                        continue;
                    }

                    Chunk chunk = queue.poll();
                    if (chunk != null) {
                        Set<Long> pending = playerPendingKeys.get(playerId);
                        if (pending != null) {
                            synchronized (pending) {
                                pending.remove(getChunkKey(chunk));
                            }
                        }

                        if (chunk.isLoaded()) {
                            plugin.getBlockListener().processChunkForSinglePlayer(chunk, player);
                            processedCount++;
                        }
                    }

                    if (!queue.isEmpty()) {
                        activePlayers.add(playerId);
                    } else {
                        activePlayersSet.remove(playerId);
                    }
                }
            }
        };
        globalTask.runTaskTimer(plugin.plugin, 1L, intervalTicks);
    }
    
    public void updateSettings(long intervalTicks, int chunksPerTick) {
        this.intervalTicks = Math.max(1L, intervalTicks);
        this.globalChunksPerTick = Math.max(1, chunksPerTick);
        if (globalTask != null) globalTask.cancel();
        startGlobalTask();
    }

    public void onPlayerQuit(Player player) {
        cleanupPlayer(player.getUniqueId());
    }

    private void cleanupPlayer(UUID uuid) {
        playerChunkQueues.remove(uuid);
        playerPendingKeys.remove(uuid);
        activePlayersSet.remove(uuid);
    }

    private Queue<Chunk> getQueue(UUID uuid) {
        return playerChunkQueues.computeIfAbsent(uuid, k -> new ConcurrentLinkedQueue<>());
    }
    
    private Set<Long> getPending(UUID uuid) {
        return playerPendingKeys.computeIfAbsent(uuid, k -> new HashSet<>());
    }

    private long getChunkKey(Chunk chunk) {
        return (long) chunk.getX() & 0xFFFFFFFFL | ((long) chunk.getZ() & 0xFFFFFFFFL) << 32;
    }
}
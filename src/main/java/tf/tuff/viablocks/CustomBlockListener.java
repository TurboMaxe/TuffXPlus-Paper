package tf.tuff.viablocks;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import tf.tuff.viablocks.version.VersionAdapter;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.block.data.BlockData;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class CustomBlockListener implements Listener {

    private final ViaBlocksPlugin plugin;
    private final VersionAdapter versionAdapter;
    private final PaletteManager paletteManager;
    private final EnumSet<Material> modernMaterials;
    private final ChunkSenderManager chunkSenderManager;
    private static volatile Method minHeightMethod;
    private static volatile boolean minHeightChecked;
    private static final long X_MASK = (1L << 26) - 1L;
    private static final long Z_MASK = (1L << 26) - 1L;
    private static final long Y_MASK = (1L << 12) - 1L;
    private static final int Y_SHIFT = 0;
    private static final int Z_SHIFT = 12;
    private static final int X_SHIFT = 12 + 26;
    private final Map<UUID, Map<Integer, List<Long>>> pendingUpdates = new HashMap<>();
    private final Set<UUID> pendingFlush = new HashSet<>();

    public CustomBlockListener(ViaBlocksPlugin plugin, VersionAdapter versionAdapter, PaletteManager paletteManager, ChunkSenderManager chunkSenderManager) {
        this.plugin = plugin;
        this.versionAdapter = versionAdapter;
        this.paletteManager = paletteManager;
        this.chunkSenderManager = chunkSenderManager; 
        this.modernMaterials = versionAdapter.getModernMaterials();
    }

    public void onViaBlocksPlayerJoin(Player player) {
        if (plugin.plugin.getConfig().getBoolean("send-welcome-book", true) && plugin.isFirstJoin(player)) {
            plugin.sendWelcomeGui(player);
            plugin.markPlayerAsJoined(player);
        }
        sendPaletteToClient(player);
        runSync(() -> {
            if (!player.isOnline()) {
                return;
            }

            World world = player.getWorld();
            int viewDistance = this.versionAdapter.getClientViewDistance(player);
            int playerChunkX = player.getLocation().getChunk().getX();
            int playerChunkZ = player.getLocation().getChunk().getZ();
            List<Chunk> chunks = new ArrayList<>();
            for (int x = -viewDistance; x <= viewDistance; x++) {
                for (int z = -viewDistance; z <= viewDistance; z++) {
                    int currentChunkX = playerChunkX + x;
                    int currentChunkZ = playerChunkZ + z;
                    if (world.isChunkLoaded(currentChunkX, currentChunkZ)) {
                        chunks.add(world.getChunkAt(currentChunkX, currentChunkZ));
                    }
                }
            }
            if (!chunks.isEmpty()) {
                chunkSenderManager.addChunksToQueue(player, chunks);
            }
        });
    }

    public void handlePlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        pendingUpdates.remove(playerId);
        pendingFlush.remove(playerId);
        plugin.setPlayerEnabled(event.getPlayer(), false);
        chunkSenderManager.onPlayerQuit(event.getPlayer());
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        for (Player player : chunk.getWorld().getPlayers()) {
            chunkSenderManager.addChunkToQueue(player, chunk);
        }
    }

    public void processChunkForSinglePlayer(Chunk chunk, Player player) {
        ChunkSnapshot snapshot = chunk.getChunkSnapshot(false, false, false);
        World world = chunk.getWorld();
        int minHeight = getMinHeight(world);
        int maxHeight = world.getMaxHeight();
        runAsync(() -> {
            Map<Integer, List<Long>> foundBlocks = findModernBlocksInChunk(snapshot, minHeight, maxHeight);
            if (!foundBlocks.isEmpty()) {
                byte[] packetData = buildChunkPacket(foundBlocks);
                runSync(() -> {
                    if (player.isOnline()) {
                        player.sendPluginMessage(plugin.plugin, ViaBlocksPlugin.CLIENTBOUND_CHANNEL, packetData);
                    }
                });
            }
        });
    }

    private Map<Integer, List<Long>> findModernBlocksInChunk(ChunkSnapshot chunkSnapshot, int minHeight, int maxHeight) {
        Map<Integer, List<Long>> foundBlocks = new HashMap<>();
        int chunkX = chunkSnapshot.getX() << 4;
        int chunkZ = chunkSnapshot.getZ() << 4;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minHeight; y < maxHeight; y++) {
                    Material blockType = chunkSnapshot.getBlockType(x, y, z);
                    if (!this.modernMaterials.contains(blockType)) {
                        continue;
                    }
                    BlockData data = chunkSnapshot.getBlockData(x, y, z);
                    String stateKey = data.getAsString();
                    int materialId = this.paletteManager.getOrCreateId(stateKey);
                    if (materialId != -1) {
                        long packedLocation = packLocation(chunkX + x, y, chunkZ + z);
                        foundBlocks.computeIfAbsent(materialId, k -> new ArrayList<>()).add(packedLocation);
                    }
                }
            }
        }
        return foundBlocks;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        if (isModernMaterial(block.getType())) {
            updateBlockStateForNearbyPlayers(block);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (isModernMaterial(event.getBlock().getType())) {
            sendClearUpdateToNearbyPlayers(event.getBlock().getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        for (Block block : event.blockList()) {
            if (isModernMaterial(block.getType())) {
                sendClearUpdateToNearbyPlayers(block.getLocation());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockFromTo(BlockFromToEvent event) {
        Block destroyedBlock = event.getToBlock();
        if (isModernMaterial(destroyedBlock.getType())) {
            sendClearUpdateToNearbyPlayers(destroyedBlock.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockGrow(BlockGrowEvent event) {
        updateBlockStateForNearbyPlayers(event.getNewState().getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent event) {
        updateBlockStateForNearbyPlayers(event.getNewState().getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        updateBlockStateForNearbyPlayers(event.getNewState().getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        updateBlockStateForNearbyPlayers(event.getNewState().getBlock());
    }

    /*@EventHandler(priority = EventPriority.LOWEST) 
    public void onBlockPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        if (isModernMaterial(block.getType())) {
            plugin.plugin.getServer().getScheduler().runTaskLater(plugin, () -> updateBlockStateForNearbyPlayers(block), 1L);
        }
    }*/


    private void updateBlockStateForNearbyPlayers(Block block) {
        Location blockLocation = block.getLocation();
        for (Player player : block.getWorld().getPlayers()) {
            if (plugin.isPlayerEnabled(player) && player.getLocation().distanceSquared(blockLocation) < 6400) {
                int stateId;
                if (isModernMaterial(block.getType())) {
                    String fullState = block.getBlockData().getAsString();
                    stateId = this.paletteManager.getOrCreateId(fullState);
                } else {
                    stateId = 0;
                }

                if (stateId != -1) {
                    sendPacket(player, stateId, blockLocation);
                }
            }
        }
    }
    
    private void sendClearUpdateToNearbyPlayers(Location location) {
        final int AIR_ID = 0;
        for (Player player : location.getWorld().getPlayers()) {
            if (plugin.isPlayerEnabled(player) && player.getLocation().distanceSquared(location) < 6400) {
                sendPacket(player, AIR_ID, location);
            }
        }
    }

    private void sendPacket(Player player, int stateId, Location location) {
        if (!player.isOnline()) {
            return;
        }
        UUID playerId = player.getUniqueId();
        Map<Integer, List<Long>> updateData = pendingUpdates.computeIfAbsent(playerId, k -> new HashMap<>());
        updateData.computeIfAbsent(stateId, k -> new ArrayList<>()).add(packLocation(location));
        if (pendingFlush.add(playerId)) {
            runSyncLater(() -> flushPendingUpdates(playerId), plugin.getUpdateBatchDelayTicks());
        }
    }

    private void flushPendingUpdates(UUID playerId) {
        Map<Integer, List<Long>> updateData = pendingUpdates.remove(playerId);
        pendingFlush.remove(playerId);
        if (updateData == null || updateData.isEmpty()) {
            return;
        }
        Player player = plugin.plugin.getServer().getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return;
        }
        byte[] packetData = buildChunkPacket(updateData);
        player.sendPluginMessage(plugin, ViaBlocksPlugin.CLIENTBOUND_CHANNEL, packetData);
    }
    
    private byte[] buildChunkPacket(Map<Integer, List<Long>> blockData) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("ADD_CHUNK");
        out.writeInt(blockData.size());
        for (Map.Entry<Integer, List<Long>> entry : blockData.entrySet()) {
            out.writeInt(entry.getKey());
            out.writeInt(entry.getValue().size());
            for (Long loc : entry.getValue()) {
                out.writeLong(loc);
            }
        }
        return out.toByteArray();
    }

    public void sendPaletteToClient(Player player) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("INIT_PALETTE");
        List<String> palette = this.paletteManager.getPalette();
        out.writeInt(palette.size());
        for (String state : palette) {
            out.writeUTF(state);
        }
        player.sendPluginMessage(plugin, ViaBlocksPlugin.CLIENTBOUND_CHANNEL, out.toByteArray());
    }
    
    public boolean isModernMaterial(Material material) {
        return this.modernMaterials.contains(material);
    }
    
    private void runAsync(Runnable task) { if (plugin.isPaper) { plugin.plugin.getServer().getAsyncScheduler().runNow(plugin, scheduledTask -> task.run()); } else { plugin.plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task); } }
    private void runSync(Runnable task) { plugin.plugin.getServer().getScheduler().runTask(plugin, task); }
    private void runSyncLater(Runnable task, long delay) { plugin.plugin.getServer().getScheduler().runTaskLater(plugin, task, delay); }

    private int getMinHeight(World world) {
        if (!minHeightChecked) {
            try {
                minHeightMethod = world.getClass().getMethod("getMinHeight");
            } catch (Exception e) {
                minHeightMethod = null;
            }
            minHeightChecked = true;
        }
        if (minHeightMethod != null) {
            try {
                Object value = minHeightMethod.invoke(world);
                if (value instanceof Integer) {
                    return (Integer) value;
                }
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }
    
    private long packLocation(int x, int y, int z) {
        return ((long)x & X_MASK) << X_SHIFT | ((long)z & Z_MASK) << Z_SHIFT | ((long)y & Y_MASK);
    }

    private long packLocation(Location loc) {
        return packLocation(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }
}

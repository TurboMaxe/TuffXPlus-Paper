package tf.tuff.viablocks;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import tf.tuff.viablocks.version.VersionAdapter;
import org.bukkit.Bukkit;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class CustomBlockListener {

    public final ViaBlocksPlugin plugin;
    private final VersionAdapter versionAdapter;
    private final PaletteManager paletteManager;
    private final EnumSet<Material> modernMaterials;
    private final NettyInjector nettyInjector;
    private static final Map<String, Integer> worldMinHeights = new ConcurrentHashMap<>();
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
    private static final double UPDATE_RADIUS_SQUARED = 6400;
    private static final byte[] EMPTY_PACKET = new byte[0];
    
    private final Map<BlockData, Integer> blockDataIdCache = new ConcurrentHashMap<>();
    private final Cache<String, byte[]> chunkPacketCache;

    private record ChunkKey(String world, int x, int z) {}

    public CustomBlockListener(ViaBlocksPlugin plugin, VersionAdapter versionAdapter, PaletteManager paletteManager) {
        this.plugin = plugin;
        this.versionAdapter = versionAdapter;
        this.paletteManager = paletteManager;
        this.modernMaterials = versionAdapter.getModernMaterials();
        this.nettyInjector = new NettyInjector(this);
        this.chunkPacketCache = CacheBuilder.newBuilder()
            .maximumSize(4096) 
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .build();
    }

    public byte[] getCachedChunkData(int x, int z) {
        for (World world : Bukkit.getWorlds()) {
            String key = world.getName() + "_" + x + "_" + z;
            byte[] data = chunkPacketCache.getIfPresent(key);
            if (data != null) return data;
        }
        return null;
    }

    public void onViaBlocksPlayerJoin(Player player) {
        nettyInjector.inject(player);

        if (plugin.isFirstJoin(player)) {
            plugin.sendWelcomeGui(player);
            plugin.markPlayerAsJoined(player);
        }
        sendPaletteToClient(player);
        
        runSync(() -> {
            if (!player.isOnline()) return;
            World world = player.getWorld();
            int viewDistance = Math.min(this.versionAdapter.getClientViewDistance(player), 12);
            int px = player.getLocation().getChunk().getX();
            int pz = player.getLocation().getChunk().getZ();

            for (int x = -viewDistance; x <= viewDistance; x++) {
                for (int z = -viewDistance; z <= viewDistance; z++) {
                    int cx = px + x;
                    int cz = pz + z;
                    if (world.isChunkLoaded(cx, cz)) {
                        prepareChunkCache(world.getChunkAt(cx, cz));
                    }
                }
            }
        });
    }

    public void handlePlayerQuit(PlayerQuitEvent event) {
        nettyInjector.eject(event.getPlayer());
        
        UUID playerId = event.getPlayer().getUniqueId();
        pendingUpdates.remove(playerId);
        pendingFlush.remove(playerId);
        plugin.setPlayerEnabled(event.getPlayer(), false);
    }

    public void handleChunkLoad(ChunkLoadEvent event) {
        prepareChunkCache(event.getChunk());
    }

    public void prepareChunkCache(Chunk chunk) {
        if (!chunk.isLoaded() || modernMaterials.isEmpty()) return;

        String key = chunk.getWorld().getName() + "_" + chunk.getX() + "_" + chunk.getZ();
        
        if (chunkPacketCache.getIfPresent(key) != null) return;

        ChunkSnapshot snapshot = chunk.getChunkSnapshot(false, false, false);
        int minHeight = getMinHeight(chunk.getWorld());
        int maxHeight = chunk.getWorld().getMaxHeight();
        
        Map<Integer, List<Long>> foundBlocks = findModernBlocksInChunk(snapshot, minHeight, maxHeight);
        if (foundBlocks.isEmpty()) {
            chunkPacketCache.put(key, EMPTY_PACKET);
        } else {
            chunkPacketCache.put(key, buildChunkPacket(foundBlocks));
        }
    }

    public byte[] getExtraDataForChunk(String worldName, int x, int z) {
        String key = worldName + "_" + x + "_" + z;
        return chunkPacketCache.getIfPresent(key);
    }

    private Map<Integer, List<Long>> findModernBlocksInChunk(ChunkSnapshot chunkSnapshot, int minHeight, int maxHeight) {
        Map<Integer, List<Long>> foundBlocks = new HashMap<>();
        int chunkX = chunkSnapshot.getX() << 4;
        int chunkZ = chunkSnapshot.getZ() << 4;

        for (int y = minHeight; y < maxHeight; y++) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    Material blockType = chunkSnapshot.getBlockType(x, y, z);
                    if (blockType == Material.AIR || !this.modernMaterials.contains(blockType)) {
                        continue;
                    }
                    
                    BlockData data = chunkSnapshot.getBlockData(x, y, z);
                    
                    int materialId = blockDataIdCache.computeIfAbsent(data, key -> {
                        return this.paletteManager.getOrCreateId(key.getAsString());
                    });

                    if (materialId != -1) {
                        long packedLocation = packLocation(chunkX + x, y, chunkZ + z);
                        foundBlocks.computeIfAbsent(materialId, k -> new ArrayList<>()).add(packedLocation);
                    }
                }
            }
        }
        return foundBlocks;
    }

    public void handleBlockPlace(BlockPlaceEvent event) {
        handleModernBlockChange(
            event.getBlockReplacedState().getBlockData(),
            event.getBlock().getBlockData(),
            event.getBlock().getLocation()
        );
    }

    public void handleBlockBreak(BlockBreakEvent event) {
        handleModernBlockChange(
            event.getBlock().getBlockData(),
            Material.AIR.createBlockData(),
            event.getBlock().getLocation()
        );
    }

    public void handleBlockExplode(BlockExplodeEvent event) {
        for (Block block : event.blockList()) {
            handleModernBlockChange(
                block.getBlockData(),
                Material.AIR.createBlockData(),
                block.getLocation()
            );
        }
    }

    public void handleBlockFromTo(BlockFromToEvent event) {
        Block destroyedBlock = event.getToBlock();
        handleModernBlockChange(
            destroyedBlock.getBlockData(),
            Material.AIR.createBlockData(),
            destroyedBlock.getLocation()
        );
    }

    public void handleBlockGrow(BlockGrowEvent event) {
        handleModernBlockChange(
            event.getBlock().getBlockData(),
            event.getNewState().getBlockData(),
            event.getBlock().getLocation()
        );
    }

    public void handleBlockFade(BlockFadeEvent event) {
        handleModernBlockChange(
            event.getBlock().getBlockData(),
            event.getNewState().getBlockData(),
            event.getBlock().getLocation()
        );
    }

    public void handleBlockForm(BlockFormEvent event) {
        handleModernBlockChange(
            event.getBlock().getBlockData(),
            event.getNewState().getBlockData(),
            event.getBlock().getLocation()
        );
    }

    public void handleBlockSpread(BlockSpreadEvent event) {
        handleModernBlockChange(
            event.getBlock().getBlockData(),
            event.getNewState().getBlockData(),
            event.getBlock().getLocation()
        );
    }

    private void handleModernBlockChange(BlockData before, BlockData after, Location location) {
        if (!isFeatureEnabled() || plugin.viaBlocksEnabledPlayers.isEmpty() || location == null) {
            return;
        }
        boolean beforeModern = before != null && isModernMaterial(before.getMaterial());
        boolean afterModern = after != null && isModernMaterial(after.getMaterial());
        if (!beforeModern && !afterModern) {
            return;
        }
        if (afterModern) {
            sendBlockStateUpdateToNearbyPlayers(location, after);
        } else {
            sendClearUpdateToNearbyPlayers(location);
        }
        invalidateChunkCache(location.getChunk());
    }

    private boolean isFeatureEnabled() {
        return plugin.plugin.getConfig().getBoolean("viablocks.viablocks-enabled", false);
    }

    private void sendBlockStateUpdateToNearbyPlayers(Location location, BlockData data) {
        if (data == null || location.getWorld() == null) {
            return;
        }
        int stateId = blockDataIdCache.computeIfAbsent(data, key -> {
            return this.paletteManager.getOrCreateId(key.getAsString());
        });
        if (stateId == -1) {
            return;
        }

        World world = location.getWorld();
        for (Player player : world.getPlayers()) {
            if (plugin.isPlayerEnabled(player) && player.getLocation().distanceSquared(location) < UPDATE_RADIUS_SQUARED) {
                sendPacket(player, stateId, location);
            }
        }
    }

    private void sendClearUpdateToNearbyPlayers(Location location) {
        if (!isFeatureEnabled() || plugin.viaBlocksEnabledPlayers.isEmpty() || location.getWorld() == null) {
            return;
        }
        final int AIR_ID = 0;
        World world = location.getWorld();
        
        for (Player player : world.getPlayers()) {
            if (plugin.isPlayerEnabled(player) && player.getLocation().distanceSquared(location) < UPDATE_RADIUS_SQUARED) {
                sendPacket(player, AIR_ID, location);
            }
        }
    }

    private void invalidateChunkCache(Chunk chunk) {
        if (chunk == null) {
            return;
        }
        chunkPacketCache.invalidate(new ChunkKey(chunk.getWorld().getName(), chunk.getX(), chunk.getZ()));
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
        player.sendPluginMessage(plugin.plugin, ViaBlocksPlugin.CLIENTBOUND_CHANNEL, packetData);
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
        player.sendPluginMessage(plugin.plugin, ViaBlocksPlugin.CLIENTBOUND_CHANNEL, out.toByteArray());
    }
    
    public boolean isModernMaterial(Material material) {
        return this.modernMaterials.contains(material);
    }

    public void clearCache() {
        blockDataIdCache.clear();
        pendingUpdates.clear();
        pendingFlush.clear();
        chunkPacketCache.invalidateAll();
    }
    
    private void runSync(Runnable task) { 
        if (!plugin.plugin.isEnabled()) return;
        try {
            if (Bukkit.isPrimaryThread()) {
                task.run();
            } else {
                plugin.plugin.getServer().getScheduler().runTask(plugin.plugin, task); 
            }
        } catch (Exception e) {
        }
    }

    private void runSyncLater(Runnable task, long delay) { 
        if (!plugin.plugin.isEnabled()) return;
        try {
            plugin.plugin.getServer().getScheduler().runTaskLater(plugin.plugin, task, delay); 
        } catch (Exception e) {
        }
    }

    private int getMinHeight(World world) {
        return worldMinHeights.computeIfAbsent(world.getName(), k -> {
            try {
                Method method = world.getClass().getMethod("getMinHeight");
                Object value = method.invoke(world);
                if (value instanceof Integer) {
                    return (Integer) value;
                }
            } catch (Exception e) {
            }
            return 0;
        });
    }
    
    private long packLocation(int x, int y, int z) {
        return ((long)x & X_MASK) << X_SHIFT | ((long)z & Z_MASK) << Z_SHIFT | ((long)y & Y_MASK);
    }

    private long packLocation(Location loc) {
        return packLocation(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }
}

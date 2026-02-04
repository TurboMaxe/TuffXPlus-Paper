package tf.tuff.y0;

import com.github.retrooper.packetevents.PacketEvents;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.concurrent.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.google.common.cache.*;
import java.util.logging.Level;
import java.lang.reflect.Method; 
import it.unimi.dsi.fastutil.objects.*;
import it.unimi.dsi.fastutil.shorts.*;
import it.unimi.dsi.fastutil.bytes.*;
import java.util.function.Consumer;

import tf.tuff.TuffX;

public class Y0Plugin {

    public static final String CH = "eagler:below_y0";
    public ViaBlockIds v;
    
    private final ObjectOpenHashSet<UUID> aib = new ObjectOpenHashSet<>();
    private ObjectOpenHashSet<String> ew;
    private volatile Cache<WCK, ObjectArrayList<byte[]>> cc;
    private boolean d;
    private volatile ExecutorService cp;

    private final ThreadLocal<Object2ObjectOpenHashMap<BlockData, int[]>> tlcc = ThreadLocal.withInitial(() -> new Object2ObjectOpenHashMap<>(256));
    private final ThreadLocal<ShortArrayList> tlba = ThreadLocal.withInitial(() -> new ShortArrayList(4096));
    private final ThreadLocal<ByteArrayList> tlla = ThreadLocal.withInitial(() -> new ByteArrayList(4096));
    private final ThreadLocal<ByteArrayOutputStream> tlos = ThreadLocal.withInitial(() -> new ByteArrayOutputStream(8256));

    private TuffX plugin;
    
    private static final int[] EMPTY_LEGACY = {1, 0};

    private static final Map<BlockData, Integer> emissionCache = new ConcurrentHashMap<>();
    private static Method getLightEmissionMethod;

    public ChunkPacketListener cpl;
    private tf.tuff.netty.ChunkInjector chunkInjector;

    static {
        try {
            getLightEmissionMethod = BlockData.class.getMethod("getLightEmission");
            getLightEmissionMethod.setAccessible(true);
        } catch (NoSuchMethodException e) {
            getLightEmissionMethod = null;
        }
    }

    private static final Map<Material, Integer> legacy_light_map = Map.ofEntries(
        Map.entry(Material.TORCH, 14),
        Map.entry(Material.SOUL_TORCH, 10),
        Map.entry(Material.LANTERN, 15),
        Map.entry(Material.SOUL_LANTERN, 10),
        Map.entry(Material.GLOWSTONE, 15),
        Map.entry(Material.SEA_LANTERN, 15),
        Map.entry(Material.REDSTONE_LAMP, 15),
        Map.entry(Material.SHROOMLIGHT, 15),
        Map.entry(Material.CAMPFIRE, 15),
        Map.entry(Material.SOUL_CAMPFIRE, 10),
        Map.entry(Material.END_ROD, 14),
        Map.entry(Material.MAGMA_BLOCK, 3),
        Map.entry(Material.FIRE, 15),
        Map.entry(Material.SOUL_FIRE, 10),
        Map.entry(Material.CANDLE, 3),
        Map.entry(Material.WHITE_CANDLE, 3),
        Map.entry(Material.CAKE, 0),
        Map.entry(Material.CANDLE_CAKE, 3)
    );

    public Y0Plugin(TuffX plugin){
        this.plugin = plugin;
    }
    
    private void ld(String m) {
        if (d) plugin.getLogger().log(Level.INFO, "[TuffX-Debug] " + m);
    }

    public record WCK(String w, int x, int z) {}

    public void onTuffXLoad() {
        
    }
    
    public void onTuffXReload() {
        d = plugin.getConfig().getBoolean("y0.debug-mode", false);
        
        ObjectArrayList<String> ewList = new ObjectArrayList<>(plugin.getConfig().getStringList("y0.enabled-worlds"));
        ew = new ObjectOpenHashSet<>(ewList.size());
        if (plugin.getConfig().getBoolean("y0.y0-enabled", false)) ew.addAll(ewList);
    
        if (cc != null) {
            cc.invalidateAll();
        }
        cc = CacheBuilder.newBuilder()
            .maximumSize(plugin.getConfig().getInt("y0.cache-size", 1024))
            .expireAfterAccess(plugin.getConfig().getInt("y0.cache-expiration", 5), TimeUnit.MINUTES)
            .concurrencyLevel(Runtime.getRuntime().availableProcessors())
            .initialCapacity(256)
            .build();
            
        if (cp != null) {
            cp.shutdown();
            try {
                if (!cp.awaitTermination(5, TimeUnit.SECONDS)) {
                    cp.shutdownNow();
                }
            } catch (InterruptedException e) {
                cp.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        int ct = plugin.getConfig().getInt("y0.chunk-processor-threads", -1);
        int tc;
        if (ct <= 0) {
            tc = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
        } else {
            tc = ct;
        }
        
        cp = Executors.newFixedThreadPool(tc, r -> {
            Thread t = new Thread(r, "TuffX-Chunk-" + System.nanoTime());
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        }); 

        emissionCache.clear();

        plugin.getLogger().info("Y0 reloaded.");
    }

    public void onTuffXEnable() {
        PacketEvents.getAPI().init();

        plugin.saveDefaultConfig();
        d = plugin.getConfig().getBoolean("y0.debug-mode", false);
        ObjectArrayList<String> ewList = new ObjectArrayList<>(plugin.getConfig().getStringList("y0.enabled-worlds"));
        ew = new ObjectOpenHashSet<>(ewList.size());
        if (plugin.getConfig().getBoolean("y0.y0-enabled", false)) ew.addAll(ewList);

        this.cpl = new ChunkPacketListener(this);

        cc = CacheBuilder.newBuilder()
            .maximumSize(plugin.getConfig().getInt("y0.cache-size", 1024))
            .expireAfterAccess(plugin.getConfig().getInt("y0.cache-expiration", 5), TimeUnit.MINUTES)
            .concurrencyLevel(Runtime.getRuntime().availableProcessors())
            .initialCapacity(256)
            .build();

        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CH);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, CH, plugin);

        if (v == null) v = new ViaBlockIds(this.plugin);
        lfe();

        int ct = plugin.getConfig().getInt("y0.chunk-processor-threads", -1);
        int tc;
        if (ct <= 0) {
            tc = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
        } else {
            tc = ct;
        }
        
        cp = Executors.newFixedThreadPool(tc, r -> {
            Thread t = new Thread(r, "TuffX-Chunk-" + System.nanoTime());
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        });
    }

    public record CSC(int x, int y, int z) {}

    public void onTuffXDisable() {
        if (cp != null) {
            cp.shutdown();
            try {
                if (!cp.awaitTermination(10, TimeUnit.SECONDS)) {
                    cp.shutdownNow();
                    if (!cp.awaitTermination(5, TimeUnit.SECONDS)) {
                        plugin.getLogger().severe("Failed to shutdown chunk processor pool!");
                    }
                }
            } catch (InterruptedException e) {
                cp.shutdownNow();
                Thread.currentThread().interrupt();
            } finally {
                cp = null;
            }
        }

        if (cc != null) {
            cc.invalidateAll();
            cc = null;
        }

        aib.clear();
        
        if (v != null) {
            v = null;
        }
    }

    public boolean isPlayerReady(Player p) {
        if (p == null) return false;
        return aib.contains(p.getUniqueId());
    }

    public void setChunkInjector(tf.tuff.netty.ChunkInjector injector) {
        this.chunkInjector = injector;
    }

    public void handlePacket(Player p, byte[] m) {
        try (DataInputStream i = new DataInputStream(new ByteArrayInputStream(m))) {
            int x = i.readInt();
            int y = i.readInt();
            int z = i.readInt();
            int al = i.readUnsignedByte();
            byte[] ab = new byte[al];
            i.readFully(ab);
            String a = new String(ab, StandardCharsets.UTF_8);
            hip(p, new Location(p.getWorld(), x, y, z), a);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to parse plugin message from " + p.getName() + ": " + e.getMessage());
        }
    }

    private void hip(Player p, Location l, String a) {
        if (!ew.contains(p.getWorld().getName()) && !a.equalsIgnoreCase("ready")) {
            p.sendPluginMessage(plugin, CH, cby0sp(false));
            return;
        }

        switch (a.toLowerCase()) {
            case "ready2":
                ld("Player " + p.getName() + " is READY.");
                aib.add(p.getUniqueId());
                if (ew.contains(p.getWorld().getName())) {
                    aib.add(p.getUniqueId());
                    preCacheVisibleChunks(p);
                    if (chunkInjector != null) {
                        chunkInjector.inject(p);
                    }
                    p.sendPluginMessage(plugin, CH, cby0sp(true));
                    resendChunksInView(p);
                } else {
                    p.sendPluginMessage(plugin, CH, cby0sp(false));
                }
                break;
            case "use_on_block":
                break;
            case "ready":
                if (plugin.getConfig().getBoolean("y0.kick-outdated-clients", true)){
                    p.kickPlayer("§cYour client is not compatible with the version of §6TuffX §cthe server has installed!\n§7Please update your client.");
                }
        }
    }

    private void preCacheVisibleChunks(Player p) {
        World world = p.getWorld();
        int viewDistance = p.getClientViewDistance();
        int playerChunkX = p.getLocation().getChunk().getX();
        int playerChunkZ = p.getLocation().getChunk().getZ();

        Object2ObjectOpenHashMap<BlockData, int[]> cvt = new Object2ObjectOpenHashMap<>(256);

        for (int x = -viewDistance; x <= viewDistance; x++) {
            for (int z = -viewDistance; z <= viewDistance; z++) {
                int currentChunkX = playerChunkX + x;
                int currentChunkZ = playerChunkZ + z;

                if (world.isChunkLoaded(currentChunkX, currentChunkZ)) {
                    WCK k = new WCK(world.getName(), currentChunkX, currentChunkZ);
                    if (cc.getIfPresent(k) == null) {
                        try {
                            Chunk chunk = world.getChunkAt(currentChunkX, currentChunkZ);
                            ChunkSnapshot snapshot = chunk.getChunkSnapshot(false, false, false);
                            ObjectArrayList<byte[]> pp = new ObjectArrayList<>(4);
                            cvt.clear();

                            for (int sy = -4; sy < 0; sy++) {
                                byte[] sectionData = csp(snapshot, currentChunkX, currentChunkZ, sy, cvt);
                                if (sectionData != null) {
                                    pp.add(sectionData);
                                }
                            }

                            cc.put(k, pp);
                        } catch (Exception e) {
                        }
                    }
                }
            }
        }
    }

    private static final int Y0_CHUNKS_PER_TICK = 8;

    public void resendChunksInView(Player p) {
        World world = p.getWorld();
        int viewDistance = p.getClientViewDistance();
        int playerChunkX = p.getLocation().getChunk().getX();
        int playerChunkZ = p.getLocation().getChunk().getZ();

        List<int[]> chunks = new ArrayList<>();
        for (int x = -viewDistance; x <= viewDistance; x++) {
            for (int z = -viewDistance; z <= viewDistance; z++) {
                int currentChunkX = playerChunkX + x;
                int currentChunkZ = playerChunkZ + z;

                if (world.isChunkLoaded(currentChunkX, currentChunkZ)) {
                    chunks.add(new int[]{currentChunkX, currentChunkZ, x * x + z * z});
                }
            }
        }

        chunks.sort((a, b) -> Integer.compare(a[2], b[2]));

        sendY0ChunksBatched(p, world.getName(), chunks, 0);
    }

    private void sendY0ChunksBatched(Player p, String worldName, List<int[]> chunks, int startIndex) {
        if (!p.isOnline() || startIndex >= chunks.size()) return;

        int endIndex = Math.min(startIndex + Y0_CHUNKS_PER_TICK, chunks.size());
        for (int i = startIndex; i < endIndex; i++) {
            int[] chunk = chunks.get(i);
            WCK k = new WCK(worldName, chunk[0], chunk[1]);
            ObjectArrayList<byte[]> cachedData = cc.getIfPresent(k);
            if (cachedData != null && !cachedData.isEmpty()) {
                for (byte[] py : cachedData) {
                    p.sendPluginMessage(plugin, CH, py);
                }
            }
        }

        if (endIndex < chunks.size()) {
            final int nextStart = endIndex;
            plugin.getServer().getScheduler().runTaskLater(plugin, () ->
                sendY0ChunksBatched(p, worldName, chunks, nextStart), 1);
        }
    }

    private byte[] cby0sp(boolean s) {
        try (ByteArrayOutputStream b = new ByteArrayOutputStream(); DataOutputStream o = new DataOutputStream(b)) {
            o.writeUTF("y0_status");
            o.writeBoolean(s);
            return b.toByteArray();
        } catch (IOException e) { return null; }
    }

    private byte[] cdp() {
        try (ByteArrayOutputStream b = new ByteArrayOutputStream(); DataOutputStream o = new DataOutputStream(b)) {
            o.writeUTF("dimension_change");
            return b.toByteArray();
        } catch (IOException e) { return null; }
    }

    public void handlePlayerChangeWorld(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        p.sendPluginMessage(plugin, CH, cdp());
        boolean isEnabledWorld = ew.contains(p.getWorld().getName());
        p.sendPluginMessage(plugin, CH, cby0sp(isEnabledWorld));
        if (isPlayerReady(p) && isEnabledWorld) {
            resendChunksInView(p);
        }
    }

    public void handlePlayerJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        p.sendPluginMessage(plugin, CH, cdp());
        boolean isEnabledWorld = ew.contains(p.getWorld().getName());
        p.sendPluginMessage(plugin, CH, cby0sp(isEnabledWorld));
    }

    public void processAndSendChunk(final Player p, final Chunk c) {
        if (c == null || p == null || !p.isOnline()) {
            return;
        }

        if (ew != null && !ew.contains(c.getWorld().getName())) {
             return;
        }

        final WCK k = new WCK(c.getWorld().getName(), c.getX(), c.getZ());
        ObjectArrayList<byte[]> cachedData = cc.getIfPresent(k);
        if (cachedData != null) {
            if (p.isOnline()) {
                for (byte[] py : cachedData) {
                    p.sendPluginMessage(plugin, CH, py);
                }
            }
            return;
        }

        final ChunkSnapshot snapshot = c.getChunkSnapshot(false, false, false);
        processSnapshotAsync(p, snapshot, c.getX(), c.getZ());
    }

    private void processSnapshotAsync(final Player p, final ChunkSnapshot snapshot, final int chunkX, final int chunkZ) {
        ExecutorService executor = cp;
        if (executor == null || executor.isShutdown()) {
            return;
        }

        final WCK k = new WCK(snapshot.getWorldName(), chunkX, chunkZ);

        executor.submit(() -> {
            final ObjectArrayList<byte[]> pp = new ObjectArrayList<>(4);
            final Object2ObjectOpenHashMap<BlockData, int[]> cvt = tlcc.get();
            cvt.clear();
            for (int sy = -4; sy < 0; sy++) {
                if (!p.isOnline()) {
                    return;
                }
                try {
                    byte[] py = csp(snapshot, chunkX, chunkZ, sy, cvt);
                    if (py != null) {
                        pp.add(py);
                    }
                } catch (IOException e) {
                    plugin.getLogger().severe("Payload creation failed: " + e.getMessage());
                }
            }
            this.cc.put(k, pp);
            if (!pp.isEmpty()) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (p.isOnline()) {
                        for (byte[] py : pp) {
                            p.sendPluginMessage(plugin, CH, py);
                        }
                    }
                });
            }
        });
    }

    private void icc(World w, int x, int z) {
        WCK k = new WCK(w.getName(), x >> 4, z >> 4);
        cc.invalidate(k);
    }

    public byte[] getY0DataForChunk(Player p, int chunkX, int chunkZ) {
        if (!isPlayerReady(p)) return null;
        World world = p.getWorld();
        if (ew == null || !ew.contains(world.getName())) return null;

        WCK k = new WCK(world.getName(), chunkX, chunkZ);
        ObjectArrayList<byte[]> cachedData = cc.getIfPresent(k);

        if (cachedData != null) {
            if (cachedData.isEmpty()) return null;
            try {
                ByteArrayOutputStream combined = new ByteArrayOutputStream();
                for (byte[] section : cachedData) {
                    combined.write(section);
                }
                return combined.toByteArray();
            } catch (IOException e) {
                return null;
            }
        }

        return null;
    }

    public void preCacheY0Data(Player p, int chunkX, int chunkZ) {
        if (!isPlayerReady(p)) return;
        World world = p.getWorld();
        if (ew == null || !ew.contains(world.getName())) return;

        WCK k = new WCK(world.getName(), chunkX, chunkZ);
        if (cc.getIfPresent(k) != null) return;

        if (!world.isChunkLoaded(chunkX, chunkZ)) return;

        try {
            Chunk chunk = world.getChunkAt(chunkX, chunkZ);
            ChunkSnapshot snapshot = chunk.getChunkSnapshot(false, false, false);
            ObjectArrayList<byte[]> pp = new ObjectArrayList<>(4);
            Object2ObjectOpenHashMap<BlockData, int[]> cvt = new Object2ObjectOpenHashMap<>(256);

            for (int sy = -4; sy < 0; sy++) {
                byte[] sectionData = csp(snapshot, chunkX, chunkZ, sy, cvt);
                if (sectionData != null) {
                    pp.add(sectionData);
                }
            }

            cc.put(k, pp);
        } catch (Exception e) {
        }
    }

    public void cacheChunkWithCallback(Player p, int chunkX, int chunkZ, Consumer<byte[]> callback) {
        if (!isPlayerReady(p)) {
            callback.accept(null);
            return;
        }
        World world = p.getWorld();
        if (ew == null || !ew.contains(world.getName())) {
            callback.accept(null);
            return;
        }

        WCK k = new WCK(world.getName(), chunkX, chunkZ);
        ObjectArrayList<byte[]> existing = cc.getIfPresent(k);
        if (existing != null) {
            if (existing.isEmpty()) {
                callback.accept(null);
            } else {
                try {
                    ByteArrayOutputStream combined = new ByteArrayOutputStream();
                    for (byte[] section : existing) {
                        combined.write(section);
                    }
                    callback.accept(combined.toByteArray());
                } catch (IOException e) {
                    callback.accept(null);
                }
            }
            return;
        }

        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            callback.accept(null);
            return;
        }

        try {
            Chunk chunk = world.getChunkAt(chunkX, chunkZ);
            ChunkSnapshot snapshot = chunk.getChunkSnapshot(false, false, false);
            ObjectArrayList<byte[]> pp = new ObjectArrayList<>(4);
            Object2ObjectOpenHashMap<BlockData, int[]> cvt = new Object2ObjectOpenHashMap<>(256);

            for (int sy = -4; sy < 0; sy++) {
                byte[] sectionData = csp(snapshot, chunkX, chunkZ, sy, cvt);
                if (sectionData != null) {
                    pp.add(sectionData);
                }
            }

            cc.put(k, pp);

            if (pp.isEmpty()) {
                callback.accept(null);
            } else {
                ByteArrayOutputStream combined = new ByteArrayOutputStream();
                for (byte[] section : pp) {
                    combined.write(section);
                }
                callback.accept(combined.toByteArray());
            }
        } catch (Exception e) {
            callback.accept(null);
        }
    }
    
    private void cp(UUID id) {
        aib.remove(id);
    }

    public void handlePlayerQuit(PlayerQuitEvent e) {
        if (chunkInjector != null) {
            chunkInjector.eject(e.getPlayer());
        }
        cp(e.getPlayer().getUniqueId());
    }

    public void handleChunkLoad(org.bukkit.event.world.ChunkLoadEvent e) {
        if (ew == null || !ew.contains(e.getWorld().getName())) return;

        org.bukkit.Chunk chunk = e.getChunk();
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();

        WCK k = new WCK(e.getWorld().getName(), chunkX, chunkZ);
        if (cc.getIfPresent(k) != null) return;

        try {
            ChunkSnapshot snapshot = chunk.getChunkSnapshot(false, false, false);
            ObjectArrayList<byte[]> pp = new ObjectArrayList<>(4);
            Object2ObjectOpenHashMap<BlockData, int[]> cvt = new Object2ObjectOpenHashMap<>(256);

            for (int sy = -4; sy < 0; sy++) {
                byte[] sectionData = csp(snapshot, chunkX, chunkZ, sy, cvt);
                if (sectionData != null) {
                    pp.add(sectionData);
                }
            }

            cc.put(k, pp);
        } catch (Exception ex) {
        }
    }

    private byte[] csp(ChunkSnapshot s, int x, int z, int sy, Object2ObjectOpenHashMap<BlockData, int[]> c) throws IOException {
        ShortArrayList ba = tlba.get();
        ByteArrayList la = tlla.get();
        ba.clear();
        la.clear();
        ba.ensureCapacity(4096);
        la.ensureCapacity(4096);
        
        boolean h = false;
        int by = sy << 4;

        for (int y = 0; y < 16; y++) {
            int wy = by + y;
            for (int zz = 0; zz < 16; zz++) {
                for (int xx = 0; xx < 16; xx++) {
                    BlockData bd = s.getBlockData(xx, wy, zz);
                    int[] ld = c.getOrDefault(bd, EMPTY_LEGACY);
                    if (ld == EMPTY_LEGACY && v != null) {
                        ld = v.toLegacy(bd);
                        c.put(bd, ld);
                    }
                    
                    short lb = (short) ((ld[1] << 12) | (ld[0] & 0xFFF));
                    byte pl = (byte) ((s.getBlockSkyLight(xx, wy, zz) << 4) | s.getBlockEmittedLight(xx, wy, zz));

                    ba.add(lb);
                    la.add(pl);

                    if (lb != 0 || pl != 0) {
                        h = true;
                    }
                }
            }
        }

        if (!h) {
            return null;
        }

        ByteArrayOutputStream b = tlos.get();
        b.reset(); 
        
        try (DataOutputStream o = new DataOutputStream(b)) {
            o.writeUTF("chunk_data");
            o.writeInt(x);
            o.writeInt(z);
            o.writeInt(sy);

            int sz = ba.size();
            for (int j = 0; j < sz; j++) {
                o.writeShort(ba.getShort(j));
                o.writeByte(la.getByte(j));
            }
            return b.toByteArray();
        }
    }

    public void handleBlockBreak(BlockBreakEvent e) { 
        if (e.getBlock().getY() < 0) {
            hbc(e.getBlock().getLocation(), e.getBlock().getBlockData(), Material.AIR.createBlockData()); 
            icc(e.getBlock().getWorld(), e.getBlock().getX(), e.getBlock().getZ());
        }
    }

    public void handleBlockPlace(BlockPlaceEvent e) { 
        if (e.getBlock().getY() < 0) {
            hbc(e.getBlock().getLocation(), e.getBlockReplacedState().getBlockData(), e.getBlock().getBlockData()); 
            icc(e.getBlock().getWorld(), e.getBlock().getX(), e.getBlock().getZ());
        }
    }

    public void handleBlockPhysics(BlockPhysicsEvent e) {
        final Block b = e.getBlock();
        if (b.getY() < 0) {
            final Location l = b.getLocation();
            final World w = l.getWorld();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                BlockData ud = w.getBlockData(l);
                ssbu(l, ud);
                icc(w, l.getBlockX(), l.getBlockZ());
            });
        }
    }

    public void handleBlockExplode(BlockExplodeEvent e) {
        final ObjectOpenHashSet<WCK> ac = new ObjectOpenHashSet<>();
        final List<Block> btu = new ArrayList<>(e.blockList());
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (Block b : btu) {
                if (b.getY() < 0) {
                    ssbu(b.getLocation(), Material.AIR.createBlockData());
                    ac.add(new WCK(b.getWorld().getName(), b.getX() >> 4, b.getZ() >> 4));
                }
            }
            if (!ac.isEmpty()) {
                ac.forEach(cc::invalidate);
            }
        });
    }

    public void handleBlockFromTo(BlockFromToEvent e) {
        final Block b = e.getToBlock();
        if (b.getY() < 0) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                ssbu(b.getLocation(), b.getBlockData());
                icc(b.getWorld(), b.getX(), b.getZ());
            });
        }
    }

    private void ssbu(Location l, BlockData d) {
        try (ByteArrayOutputStream b = new ByteArrayOutputStream(64);
            DataOutputStream o = new DataOutputStream(b)) {

            o.writeUTF("block_update");
            o.writeInt(l.getBlockX());
            o.writeInt(l.getBlockY());
            o.writeInt(l.getBlockZ());

            int[] ld = v.toLegacy(d);
            o.writeShort((short) ((ld[1] << 12) | (ld[0] & 0xFFF)));
            byte[] py = b.toByteArray();

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                for (Player p : l.getWorld().getPlayers()) {
                    if (p.getLocation().distanceSquared(l) < 4096) {
                        p.sendPluginMessage(plugin, CH, py);
                    }
                }
            });

        } catch (IOException e) {
            plugin.getLogger().severe("Failed to create single block update payload: " + e.getMessage());
        }
    }

    public static int getEmission(BlockData data) {
        if (getLightEmissionMethod != null) {
            try {
                return (int) getLightEmissionMethod.invoke(data);
            } catch (Exception e) {
            }
        }
        return legacy_light_map.getOrDefault(data.getMaterial(), 0);
    }

    private void hbc(Location l, BlockData od, BlockData nd) {
        ssbu(l, nd);
        boolean oe = getEmission(od) > 0;
        boolean ne = getEmission(nd) > 0;
        boolean oo = od.getMaterial().isOccluding();
        boolean no = nd.getMaterial().isOccluding();
        if (oe != ne || oo != no) {
            slu(l);
        }
    }

    private void slu(Location l) {
        ObjectOpenHashSet<CSC> stu = new ObjectOpenHashSet<>();
        World w = l.getWorld();
        int bx = l.getBlockX();
        int by = l.getBlockY();
        int bz = l.getBlockZ();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int ny = by + dy;
                    if (ny < -64 || ny >= 0) continue;

                    stu.add(new CSC(
                        (bx + dx) >> 4,
                        ny >> 4,
                        (bz + dz) >> 4
                    ));
                }
            }
        }
        for (CSC sc : stu) {
            if (!w.isChunkLoaded(sc.x, sc.z)) continue;
            ChunkSnapshot s = w.getChunkAt(sc.x, sc.z).getChunkSnapshot(true, false, false);
            
            if (cp != null && !cp.isShutdown()) {
                cp.submit(() -> {
                    try {
                        byte[] py = clp(s, sc);
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            for (Player p : w.getPlayers()) {
                                if (p.isOnline() && p.getLocation().distanceSquared(l) < 4096) {
                                    p.sendPluginMessage(plugin, CH, py);
                                }
                            }
                        });
                    } catch (IOException e) {
                        plugin.getLogger().severe("Failed to create lighting payload: " + e.getMessage());
                    }
                });
            }
        }
    }

    private byte[] clp(ChunkSnapshot s, CSC sc) throws IOException {
        try (ByteArrayOutputStream b = new ByteArrayOutputStream(4120); 
            DataOutputStream o = new DataOutputStream(b)) {
            o.writeUTF("lighting_update");
            o.writeInt(sc.x);
            o.writeInt(sc.z);
            o.writeInt(sc.y);

            byte[] ld = new byte[4096];
            int by = sc.y * 16;
            int i = 0;

            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        int wy = by + y;
                        int bl = s.getBlockEmittedLight(x, wy, z);
                        int sl = s.getBlockSkyLight(x, wy, z);
                        ld[i++] = (byte) ((sl << 4) | bl);
                    }
                }
            }
            o.write(ld);
            return b.toByteArray();
        }
    }

    private void lfe() {
    }
}
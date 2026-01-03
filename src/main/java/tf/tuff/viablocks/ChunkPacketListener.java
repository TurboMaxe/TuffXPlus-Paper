package tf.tuff.viablocks;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.Player;
import java.util.UUID;

public class ChunkPacketListener extends PacketListenerAbstract {

    private static boolean initialized = false;
    private final ViaBlocksPlugin plugin;

    private ChunkPacketListener(ViaBlocksPlugin plugin) {
        this.plugin = plugin;
    }

    public static void initialize(ViaBlocksPlugin plugin) {
        if (initialized) {
            return;
        }

        PacketEvents.getAPI().getEventManager().registerListener(new ChunkPacketListener(plugin));
        plugin.getLogger().info("PacketEvents listener for chunks registered successfully.");
        initialized = true;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() != PacketType.Play.Server.CHUNK_DATA) {
            return;
        }

        Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        UUID playerId = player.getUniqueId();
        WrapperPlayServerChunkData wrapper = new WrapperPlayServerChunkData(event);
        int chunkX = wrapper.getColumn().getX();
        int chunkZ = wrapper.getColumn().getZ();

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

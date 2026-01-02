package tf.tuff;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitRunnable;
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

import tf.tuff.y0.Y0Plugin;

public class TuffX extends JavaPlugin implements Listener, PluginMessageListener {

    private ServerRegistry serverRegistry;

    private Y0Plugin y0Plugin;
    
    @Override
    public void onLoad() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().getSettings().reEncodeByDefault(false)
                .checkForUpdates(false)
                .bStats(true);
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {
        PacketEvents.getAPI().init();

        y0Plugin = new Y0Plugin(this);

        saveDefaultConfig();
        
        PacketEvents.getAPI().getEventManager().registerListener(
            new ChunkPacketListener(this), PacketListenerPriority.NORMAL
        );

        getServer().getMessenger().registerOutgoingPluginChannel(this, CH);
        getServer().getMessenger().registerIncomingPluginChannel(this, CH, this);
        getServer().getPluginManager().registerEvents(this, this);

        if (getConfig().getBoolean("registry.enabled", false)) {
            String url = getConfig().getString("registry.server-url");
            String ws = getConfig().getString("registry.server");

            if (!ws.isEmpty() && !ws.equals("wss://urserverip.net")) {
                serverRegistry = new ServerRegistry(this, url, ws);
                serverRegistry.connect();
            }
        }
    }

    @Override
    public void onDisable() {
        if (serverRegistry != null) {
            serverRegistry.disconnect();
            serverRegistry = null;
        }

        PacketEvents.getAPI().terminate();
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!player.isOnline()) return;
      
        if (channel.equals("eagler:below_y0") y0Plugin.handlePacket(player,message):
    }
  
    private void lfe() {
        getLogger().info("");
        getLogger().info("████████╗██╗   ██╗███████╗ ███████╗ ██╗  ██╗");
        getLogger().info("╚══██╔══╝██║   ██║██╔════╝ ██╔════╝ ╚██╗██╔╝");
        getLogger().info("   ██║   ██║   ██║██████╗  ██████╗   ╚███╔╝ ");
        getLogger().info("   ██║   ██║   ██║██╔═══╝  ██╔═══╝   ██╔██╗ ");
        getLogger().info("   ██║   ╚██████╔╝██║      ██║      ██╔╝╚██╗");
        getLogger().info("   ╚═╝    ╚═════╝ ╚═╝      ╚═╝      ╚═╝  ╚═╝");
        getLogger().info("");
        getLogger().info("Below y0 and TuffX programmed by Potato");
    }
}

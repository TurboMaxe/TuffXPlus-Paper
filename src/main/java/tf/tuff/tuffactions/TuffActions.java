package tf.tuff.tuffactions;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPluginMessage;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import tf.tuff.tuffactions.creative.CreativeMenu;
import tf.tuff.tuffactions.network.TuffPacketListener;
import tf.tuff.tuffactions.swimming.Swimming;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class TuffActions extends JavaPlugin implements Listener {

    public static final String CHANNEL = "eagler:tuffactions";

    private Swimming swimmingManager;
    private CreativeMenu creativeManager;
    private PacketListenerCommon packetListener;

    public static boolean swimmingEnabled = false;
    public static boolean creativeEnabled = false;

    public static final Set<UUID> tuffPlayers = ConcurrentHashMap.newKeySet();

    @Override
    public void onLoad() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().getSettings()
                .reEncodeByDefault(false)
                .checkForUpdates(false)
                .bStats(false);
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getLogger().info("TuffActions has been enabled");
        getLogger().info("Enabling features...");

        swimmingEnabled = getConfig().getBoolean("swimming.enabled", true);
        creativeEnabled = getConfig().getBoolean("creative-items.enabled", true);

        if (swimmingEnabled) getLogger().info("Swimming enabled.");
        if (creativeEnabled) getLogger().info("Creative items enabled.");

        PacketEvents.getAPI().init();
        packetListener = PacketEvents.getAPI().getEventManager().registerListener(new TuffPacketListener(this));

        this.swimmingManager = new Swimming(this);
        this.creativeManager = new CreativeMenu(this);

        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("Finished enabling features.");
        logEnable();
    }

    @Override
    public void onDisable() {
        if (packetListener != null) {
            PacketEvents.getAPI().getEventManager().unregisterListener(packetListener);
        }
        PacketEvents.getAPI().terminate();
    }

    public void handlePluginMessage(Player player, byte[] message) {
        if (player == null || message == null || message.length < 13) {
            return;
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            in.readInt();
            in.readInt();
            in.readInt();
            int actionLength = in.readUnsignedByte();
            if (actionLength == 0 || in.available() < actionLength) {
                return;
            }
            byte[] actionBytes = new byte[actionLength];
            in.readFully(actionBytes);
            String action = new String(actionBytes, StandardCharsets.UTF_8);

            tuffPlayers.add(player.getUniqueId());

            if ("swimming_state".equals(action) && swimmingEnabled) {
                swimmingManager.handleSwimState(player, in.readBoolean());
            } else if ("shield_ready".equals(action) && shieldEnabled){
                shieldManager.handleShieldReady(player);
            } else if ("creative_ready".equals(action) && creativeEnabled){
                creativeManager.handleCreativeReady(player);
            } else if ("swim_ready".equals(action) && swimmingEnabled){
                swimmingManager.handleSwimReady(player);
            //} else if ("trim_ready".equals(action) && trimEnabled){
                //trimManager.handleTrimReady(player);
            } else if ("give_creative_item".equals(action) && creativeEnabled){
                if (player.getGameMode() != GameMode.CREATIVE) {
                    return;
                }
                int itemLength = in.readUnsignedByte();
                if (in.available() < itemLength + Integer.BYTES) {
                    return;
                }
                byte[] itemBytes = new byte[itemLength];
                in.readFully(itemBytes);
                String item = new String(itemBytes, StandardCharsets.UTF_8);
                int amount = in.readInt();
                creativeManager.handlePlaceholderTaken(player, item, amount);
            }
        } catch (IOException e) {
            getLogger().log(Level.WARNING, "Failed to read a plugin message from " + player.getName(), e);
        }
    }

    public void sendPluginMessage(Player player, byte[] payload) {
        sendPluginMessage(player, CHANNEL, payload);
    }

    public void sendPluginMessage(Player player, String channel, byte[] payload) {
        if (player == null || payload == null || !player.isOnline() || !PacketEvents.getAPI().isInitialized()) {
            return;
        }
        WrapperPlayServerPluginMessage packet = new WrapperPlayServerPluginMessage(channel, payload);
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
    }

    //public Trims getTrimsManager() {
        //return trimManager;
    //}

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (swimmingEnabled) {
            swimmingManager.handleSwimQuit(event);
        }
        if (shieldEnabled) {
            shieldManager.handleShieldQuit(event);
        }
        //if (trimEnabled) {
            //trimManager.handleTrimQuit(event);
        //}
        tuffPlayers.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerInventoryClick(InventoryClickEvent event) {
        if (creativeEnabled) {
            creativeManager.onPlayerInventoryClick(event);
        }
    }

    public void logEnable(){
        getLogger().info("Selected features enabled.");
        getLogger().info("Credits:");
        getLogger().info("Swimming, shield mechanics, creative items - Potato (atypicalpotato)");
        getLogger().info("PacketEvents integration, shaded build, 1.14+ support - llucasandersen");
    }
}

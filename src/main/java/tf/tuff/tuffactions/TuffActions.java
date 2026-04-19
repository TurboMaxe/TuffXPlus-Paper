package tf.tuff.tuffactions;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPluginMessage;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.EntityToggleSwimEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import tf.tuff.TuffX;
import tf.tuff.tuffactions.creative.CreativeMenu;
import tf.tuff.tuffactions.restrictions.Restrictions;
import tf.tuff.tuffactions.swimming.Swimming;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class TuffActions {

    public static final String CHANNEL = "eagler:tuffactions";

    private Swimming swimmingManager;
    private CreativeMenu creativeManager;
    private Restrictions restrictions;

    public static boolean swimmingEnabled = false;
    public static boolean creativeEnabled = false;
    public static boolean restrictionsEnabled = false;

    public final TuffX plugin;

    public static final Set<UUID> tuffPlayers = ConcurrentHashMap.newKeySet();

    public TuffActions(TuffX plugin) {
        this.plugin = plugin;
    }

    private void loadConfig() {
        plugin.saveDefaultConfig();
        info("TuffActions has been enabled");
        info("Enabling features...");

        swimmingEnabled = plugin.getConfig().getBoolean("swimming.enabled", true);
        creativeEnabled = plugin.getConfig().getBoolean("creative-items.enabled", true);
        restrictionsEnabled = plugin.getConfig().getBoolean("restrictions.enabled", false);

        if (swimmingEnabled) info("Swimming enabled.");
        if (creativeEnabled) info("Creative items enabled.");
        if (restrictionsEnabled) info("Restrictions enabled.");
    }

    public void onTuffXReload() {
        loadConfig();
        restrictions.onTuffXReload();

        info("Misc features reloaded.");
    }

    public void load() {
        loadConfig();
        PacketEvents.getAPI().init();

        this.swimmingManager = new Swimming(this);
        this.creativeManager = new CreativeMenu(this);
        this.restrictions = new Restrictions(this);
        info("Finished enabling features.");
    }

    public boolean onTuffXCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("restrictions"))
            return this.restrictions.onTuffXCommand(sender, command, label, args);
        return true;
    }

    public void handlePacket(Player player, byte[] message) {
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

            // This may or may not work - turbo
            switch (action.toLowerCase()) {
                case "swimming_state":
                    if (swimmingEnabled) {
                        swimmingManager.handleSwimState(player, in.readBoolean());
                        break;
                    }
                case "elytra_state":
                    if (swimmingEnabled) {
                        swimmingManager.handleElytraState(player, in.readBoolean());
                        break;
                    }
                case "creative-ready":
                    if (creativeEnabled) {
                        creativeManager.handleCreativeReady(player);
                        break;
                    }
                case "swim_ready":
                    if (swimmingEnabled) {
                        swimmingManager.handleSwimReady(player);
                        break;
                    }
                case "give_creative_item":
                    if (creativeEnabled) {
                        if (player.getGameMode() != GameMode.CREATIVE) return;
                        int itemLength = in.readUnsignedByte();
                        if (in.available() < itemLength + Integer.BYTES) {
                            return;
                        }
                        byte[] itemBytes = new byte[itemLength];
                        in.readFully(itemBytes);
                        String item = new String(itemBytes, StandardCharsets.UTF_8);
                        int amount = in.readInt();
                        creativeManager.handlePlaceholderTaken(player, item, amount);
                        break;
                    }
                case "pick_viablock":
                    if (creativeEnabled) {
                        if (player.getGameMode() != GameMode.CREATIVE) return;
                        int blockLength = in.readUnsignedByte();
                        if (in.available() < blockLength + 1) return;
                        byte[] blockBytes = new byte[blockLength];
                        in.readFully(blockBytes);
                        String blockName = new String(blockBytes, StandardCharsets.UTF_8);
                        int hotbarSlot = in.readUnsignedByte();
                        creativeManager.handlePickViablock(player, blockName, hotbarSlot);
                        break;
                    }
                case "restrictions_ready":
                    restrictions.handleRestrictionsReady(player);
            }

        } catch (IOException e) {
            log(Level.WARNING, "Failed to read a plugin message from " + player.getName(), e);
        }
    }

    public void sendPluginMessage(Player player, byte[] payload) {
        sendPluginMessage(player, CHANNEL, payload);
    }

    public void sendPluginMessage(Player player, String channel, byte[] payload) {
        if (player == null || payload == null ||
            !player.isOnline() || !PacketEvents.getAPI().isInitialized()
        ) {
            return;
        }

        WrapperPlayServerPluginMessage packet = new WrapperPlayServerPluginMessage(channel, payload);
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, packet);
    }

    public void handlePlayerQuit(PlayerQuitEvent event) {
        if (swimmingEnabled) swimmingManager.handleSwimQuit(event);
        tuffPlayers.remove(event.getPlayer().getUniqueId());
    }

    public void handleToggleSwim(EntityToggleSwimEvent event) {
        if (swimmingEnabled) {
            swimmingManager.handleToggleSwim(event);
        }
    }

    public void handleToggleGlide(EntityToggleGlideEvent event) {
        if (swimmingEnabled) {
            swimmingManager.handleToggleGlide(event);
        }
    }

    public void handlePlayerInventoryClick(InventoryClickEvent event) {
        if (creativeEnabled) creativeManager.onPlayerInventoryClick(event);
    }

    public void log(Level level, String msg, Throwable e) {
        plugin.getLogger().log(level, "[TuffActions] ".formatted(msg), e);
    }

    public void log(Level level, String msg) {
        plugin.getLogger().log(level, "[TuffActions] %s".formatted(msg));
    }

    public void info(String msg) {
        log(Level.INFO, msg);
    }
}

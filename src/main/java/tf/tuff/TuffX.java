package tf.tuff;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;
import tf.tuff.listeners.BlockListener;
import tf.tuff.listeners.PlayerListener;
import tf.tuff.netty.ChunkInjector;
import tf.tuff.tuffactions.TuffActions;
import tf.tuff.viablocks.ViaBlocksPlugin;
import tf.tuff.viaentities.ViaEntitiesPlugin;
import tf.tuff.y0.Y0Plugin;

import java.util.List;

public final class TuffX extends JavaPlugin implements PluginMessageListener {

    private ServerRegistry serverRegistry;
    @Getter private static TuffX instance;
    @Getter private Y0Plugin y0Plugin;
    @Getter private ViaBlocksPlugin viaBlocksPlugin;
    @Getter private TuffActions tuffActions;
    @Getter private ViaEntitiesPlugin viaEntitiesPlugin;

    public TuffX() {
        instance = this;
    }

    @Override
    public void onLoad() {
        y0Plugin = new Y0Plugin(this);
        this.viaBlocksPlugin = new ViaBlocksPlugin(this);
        this.tuffActions = new TuffActions(this);
        this.viaEntitiesPlugin = new ViaEntitiesPlugin(this);

        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().getSettings().reEncodeByDefault(false)
                .checkForUpdates(false);
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {
        PacketEvents.getAPI().init();

        y0Plugin.onTuffXEnable();
        tuffActions.load();
        viaBlocksPlugin.onTuffXEnable();
        viaEntitiesPlugin.onTuffXEnable();

        ChunkInjector chunkInjector = new ChunkInjector(viaBlocksPlugin.blockListener, y0Plugin);
        viaBlocksPlugin.blockListener.setChunkInjector(chunkInjector);
        y0Plugin.setChunkInjector(chunkInjector);

        saveDefaultConfig();
        PacketEvents.getAPI().getEventManager().registerListener(
            new NetworkListener(this), PacketListenerPriority.NORMAL
        );

        Bukkit.getPluginManager().registerEvents(new BlockListener(), this);
        Bukkit.getPluginManager().registerEvents(new PlayerListener(), this);
        setupRegistry();
        lfe();
    }

    private void setupRegistry() {
        if (getConfig().getBoolean("registry.enabled", false)) {
            String url = getConfig().getString("registry.server-url");
            String ws = getConfig().getString("registry.server");

            if (ws != null && !ws.isEmpty() && !ws.equals("wss://urserverip.net")) {
                serverRegistry = new ServerRegistry(this, url, ws);
                serverRegistry.connect();
            }
        }
    }

    @Override
    public void onDisable() {
        y0Plugin.onTuffXDisable();
        viaBlocksPlugin.onTuffXDisable();
        viaEntitiesPlugin.onTuffXDisable();

        if (serverRegistry != null) {
            serverRegistry.disconnect();
            serverRegistry = null;
        }

        PacketEvents.getAPI().terminate();
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, Player player, byte[] message) {
        if (!player.isOnline()) return;

        switch (channel) {
            case "eagler:below_y0" -> y0Plugin.handlePacket(player, message);
            case "viablocks:handshake" -> viaBlocksPlugin.handlePacket(player, message);
            case "eagler:tuffactions" -> tuffActions.handlePacket(player, message);
            case "entities:handshake" -> viaEntitiesPlugin.handlePacket(player, message);
            default ->
               getLogger().warning("Received plugin message on unknown channel '%s' from %s".formatted(channel, player.getName()));
        }
    }

    public void reloadTuffX(){
        reloadConfig();
        saveDefaultConfig();

        if (serverRegistry != null) {
            serverRegistry.disconnect();
            serverRegistry = null;
        }

        setupRegistry();
        viaBlocksPlugin.onTuffXReload();
        y0Plugin.onTuffXReload();
        tuffActions.onTuffXReload();
        viaEntitiesPlugin.onTuffXReload();
        getLogger().info("TuffX reloaded.");
    }

    public boolean TuffXCommand(CommandSender sender, Command command, String label, String[] args){
        if (args.length > 0) {
            if (args[0].equalsIgnoreCase("reload")) {
                if (!(sender instanceof Player player)) {
                    reloadTuffX();
                } else {
                    if (!player.hasPermission("tuffx.reload")) {
                        player.sendMessage("§cYou do not have permission to use this command.");
                        return false;
                    }
                    reloadTuffX();
                    player.sendMessage("TuffX reloaded.");
                }
            }
        }
        return true;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, Command command, @NotNull String label, String @NotNull [] args) {
        if (command.getName().equalsIgnoreCase("tuffx")) return TuffXCommand(sender, command, label, args);
        if (command.getName().equalsIgnoreCase("viablocks")) return viaBlocksPlugin.onTuffXCommand(sender, command, label, args);
        if (command.getName().equalsIgnoreCase("restrictions")) return tuffActions.onTuffXCommand(sender, command, label, args);
        return true;
    }

    private void lfe() {
        List.of(
                "████████╗██╗   ██╗███████╗ ███████╗ ██╗  ██╗",
                "╚══██╔══╝██║   ██║██╔════╝ ██╔════╝ ╚██╗██╔╝",
                "   ██║   ██║   ██║██████╗  ██████╗   ╚███╔╝ ",
                "   ██║   ██║   ██║██╔═══╝  ██╔═══╝   ██╔██╗ ",
                "   ██║   ╚██████╔╝██║      ██║      ██╔╝╚██╗",
                "   ╚═╝    ╚═════╝ ╚═╝      ╚═╝      ╚═╝  ╚═╝",
                "",
                "CREDITS",
                "",
                "Y0 support:",
                "• Below y0 (client + plugin) programmed by Potato (@justatypicalpotato)",
                "• llucasandersen - plugin optimizations",
                "",
                "ViaBlocks:",
                "• ViaBlocks partial plugin and client rewrite by Potato",
                "• llucasandersen (Complex client models and texture fixes,",
                "      optimizations, PacketEvents migration and async safety fixes)",
                "• coleis1op, if ts is driving me crazy, im taking credit",
                "",
                "Other:",
                "• Swimming and creative items programmed by Potato (@justatypicalpotato)",
                "• shaded build, 1.14+ support (before merge) - llucasandersen",
                "• Restrictions - UplandJacob",
                "• Overall plugin merges by Potato"
        ).forEach(Bukkit.getConsoleSender()::sendMessage);
    }
}
